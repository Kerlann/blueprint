package fr.blueprint.core.event;

import fr.blueprint.api.BlueprintPlugin;
import fr.blueprint.api.event.BlueprintEvents;
import fr.blueprint.api.event.Dispatch;
import fr.blueprint.api.event.EventRegistry;
import fr.blueprint.api.event.EventType;
import fr.blueprint.api.event.TriggerContext;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Système d'événements (story 2.5) : builder, dispatch, paresse, thread, registre. */
class EventSystemTest {

    /** Porte factice : simule le thread serveur et sa file de tâches. */
    private static final class FakeGate implements EventDispatcher.ThreadGate {
        boolean onThread = true;
        final Deque<Runnable> queue = new ArrayDeque<>();

        @Override
        public boolean isOnThread() {
            return onThread;
        }

        @Override
        public void submit(Runnable task) {
            queue.add(task);
        }

        void runPending() {
            onThread = true;
            while (!queue.isEmpty()) {
                queue.poll().run();
            }
        }
    }

    private static final EventType RITUAL = EventType.builder(
                    Identifier.fromNamespaceAndPath("mymod", "ritual_complete"))
            .out("power", PinTypes.DOUBLE)
            .out("chant", PinTypes.STRING)
            .dispatch(Dispatch.PER_PLAYER)
            .build();

    @AfterEach
    void uninstallFacade() {
        BlueprintEvents.uninstall();
    }

    @Test
    void builderValidatesDeclarations() {
        assertEquals(Dispatch.PER_PLAYER, RITUAL.dispatch());
        assertEquals(2, RITUAL.outputs().size());
        // Sortie dupliquée.
        assertThrows(IllegalStateException.class, () -> EventType.builder(
                        Identifier.fromNamespaceAndPath("bad", "dup"))
                .out("x", PinTypes.INT).out("x", PinTypes.INT).build());
        // Type exec interdit en sortie d'événement.
        assertThrows(IllegalStateException.class, () -> EventType.builder(
                        Identifier.fromNamespaceAndPath("bad", "exec_out"))
                .out("go", PinTypes.EXEC));
        // Dispatch par défaut : GLOBAL.
        assertEquals(Dispatch.GLOBAL, EventType.builder(
                Identifier.fromNamespaceAndPath("ok", "plain")).build().dispatch());
    }

    @Test
    void subscribersReceiveValidatedPayload() {
        var gate = new FakeGate();
        var dispatcher = new EventDispatcher(gate);
        List<TriggerContext> received = new ArrayList<>();
        dispatcher.subscribe(RITUAL.id(), received::add);

        dispatcher.fire(RITUAL, payload -> payload.set("power", 3.5).set("chant", "ooo"));

        assertEquals(1, received.size());
        assertEquals(3.5, received.get(0).output("power"));
        assertEquals("ooo", received.get(0).output("chant"));
        assertEquals(RITUAL.id(), received.get(0).eventId());
        // Lire une sortie non déclarée = erreur de développeur nommée.
        var ex = assertThrows(IllegalStateException.class, () -> received.get(0).output("absent"));
        assertTrue(ex.getMessage().contains("ritual_complete"));
    }

    @Test
    void noSubscriberMeansPayloadIsNeverBuilt() {
        // AC4 : la mesure de « coût négligeable » est la paresse — le constructeur
        // de charge utile ne doit jamais être invoqué sans abonné.
        var dispatcher = new EventDispatcher(new FakeGate());
        AtomicInteger built = new AtomicInteger();
        dispatcher.fire(RITUAL, payload -> built.incrementAndGet());
        assertEquals(0, built.get());
    }

    @Test
    void offThreadFireIsDeferredToServerThread() {
        var gate = new FakeGate();
        var dispatcher = new EventDispatcher(gate);
        List<TriggerContext> received = new ArrayList<>();
        dispatcher.subscribe(RITUAL.id(), received::add);

        gate.onThread = false;
        dispatcher.fire(RITUAL, payload -> payload.set("power", 1.0));
        assertTrue(received.isEmpty(), "rien ne se livre hors du thread serveur");
        assertEquals(1, gate.queue.size());

        gate.runPending();
        assertEquals(1, received.size());
        assertEquals(1.0, received.get(0).output("power"));
    }

    @Test
    void payloadWritesAreValidated() {
        var dispatcher = new EventDispatcher(new FakeGate());
        dispatcher.subscribe(RITUAL.id(), trigger -> {
        });
        // Sortie inconnue.
        var unknown = assertThrows(IllegalStateException.class,
                () -> dispatcher.fire(RITUAL, payload -> payload.set("absent", 1)));
        assertTrue(unknown.getMessage().contains("absent"));
        // Valeur mal typée.
        var badType = assertThrows(IllegalStateException.class,
                () -> dispatcher.fire(RITUAL, payload -> payload.set("power", "pas un double")));
        assertTrue(badType.getMessage().contains("power"));
    }

    @Test
    void faultySubscriberDoesNotStarveOthers() {
        var dispatcher = new EventDispatcher(new FakeGate());
        AtomicInteger served = new AtomicInteger();
        dispatcher.subscribe(RITUAL.id(), trigger -> {
            throw new RuntimeException("abonné cassé");
        });
        dispatcher.subscribe(RITUAL.id(), trigger -> served.incrementAndGet());
        dispatcher.fire(RITUAL, payload -> payload.set("power", 2.0));
        assertEquals(1, served.get());
    }

    @Test
    void facadeIsSafeBeforeInstallAndRoutesAfter() {
        // Avant installation : no-op silencieux, le consumer n'est pas invoqué.
        AtomicInteger built = new AtomicInteger();
        BlueprintEvents.fire(RITUAL, payload -> built.incrementAndGet());
        assertEquals(0, built.get());

        // Après installation : routage réel.
        var dispatcher = new EventDispatcher(new FakeGate());
        List<TriggerContext> received = new ArrayList<>();
        dispatcher.subscribe(RITUAL.id(), received::add);
        BlueprintEvents.install(dispatcher);
        BlueprintEvents.fire(RITUAL, payload -> payload.set("power", 9.0));
        assertEquals(1, received.size());

        // Après désinstallation : de nouveau no-op.
        BlueprintEvents.uninstall();
        BlueprintEvents.fire(RITUAL, payload -> payload.set("power", 1.0));
        assertEquals(1, received.size());
    }

    @Test
    void pluginsRegisterEventsInPhaseThree() {
        EventType custom = EventType.builder(Identifier.fromNamespaceAndPath("mod_e", "boom"))
                .out("radius", PinTypes.DOUBLE).build();
        BlueprintPlugin plugin = new BlueprintPlugin() {
            @Override
            public void registerNodes(fr.blueprint.api.registry.NodeRegistry registry) {
            }

            @Override
            public void registerEvents(EventRegistry registry) {
                registry.register(custom);
            }
        };
        var loaded = PluginLoader.load(List.of(new PluginLoader.PluginEntry("mod_e", plugin)));
        assertTrue(loaded.failedMods().isEmpty());
        assertEquals(custom, loaded.events().get(custom.id()).orElseThrow());
        assertEquals("mod_e", loaded.events().providerOf(custom.id()).orElseThrow());
        // Gelé après chargement.
        assertTrue(loaded.events().isFrozen());
        assertThrows(IllegalStateException.class, () -> loaded.events().register(custom));
    }

    @Test
    void duplicateEventAcrossModsIsolatesSecond() {
        EventType shared = EventType.builder(Identifier.fromNamespaceAndPath("shared", "event")).build();
        BlueprintPlugin first = new BlueprintPlugin() {
            @Override
            public void registerNodes(fr.blueprint.api.registry.NodeRegistry registry) {
            }

            @Override
            public void registerEvents(EventRegistry registry) {
                registry.register(shared);
            }
        };
        BlueprintPlugin second = new BlueprintPlugin() {
            @Override
            public void registerNodes(fr.blueprint.api.registry.NodeRegistry registry) {
            }

            @Override
            public void registerEvents(EventRegistry registry) {
                registry.register(EventType.builder(Identifier.fromNamespaceAndPath("shared", "event")).build());
            }
        };
        var loaded = PluginLoader.load(List.of(
                new PluginLoader.PluginEntry("mod_one", first),
                new PluginLoader.PluginEntry("mod_two", second)));
        assertEquals(List.of("mod_two"), loaded.failedMods());
        assertEquals("mod_one", loaded.events().providerOf(shared.id()).orElseThrow());
    }
}
