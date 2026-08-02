package fr.blueprint.core.storage;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.BlueprintManager;
import fr.blueprint.core.DemoBlueprint;
import fr.blueprint.core.event.BlueprintEventBridge;
import fr.blueprint.core.event.EventDispatcher;
import fr.blueprint.core.event.StandardEvents;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.Vec2d;
import fr.blueprint.core.registry.PluginLoader;
import fr.blueprint.core.vm.BlueprintScheduler;
import fr.blueprint.core.vm.ExecutionEnvironment;
import fr.blueprint.core.vm.RefResolver;
import fr.blueprint.core.vm.VarStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le cycle complet de la persistance (story 6.1), simulé headless : monde qui tourne →
 * capture vivante → codec SavedData → « redémarrage » → restauration + rapport →
 * l'exécution suspendue reprend et finit.
 */
class PersistenceTest {

    private static final List<String> RECORDS = new ArrayList<>();

    private static final PluginLoader.LoadedRegistries LOADED = PluginLoader.load(List.of(
            new PluginLoader.PluginEntry("spy", registry ->
                    registry.register(fr.blueprint.api.node.NodeType.builder(
                                    Identifier.fromNamespaceAndPath("spy", "record"))
                            .exec().in("tag", PinTypes.STRING, "?")
                            .action(ctx -> RECORDS.add(ctx.in("tag"))).build()))), true);

    private BlueprintManager manager;
    private BlueprintScheduler scheduler;
    private BlueprintEventBridge.EnvFactory envFactory;

    @BeforeEach
    void setup() {
        RECORDS.clear();
        manager = new BlueprintManager();
        scheduler = newScheduler();
        envFactory = (bp, trigger) -> new ExecutionEnvironment(
                typeId -> LOADED.nodes().get(typeId).orElse(null),
                new fr.blueprint.api.node.BlueprintHandle() {
                    @Override
                    public Identifier id() {
                        return bp.id();
                    }

                    @Override
                    public boolean enabled() {
                        return bp.enabled();
                    }
                },
                trigger, VarStore.inMemory(), null, null,
                LoggerFactory.getLogger("blueprint-test"));
    }

    private static BlueprintScheduler newScheduler() {
        return new BlueprintScheduler(100, new BlueprintScheduler.Listener() {
            @Override
            public void disabled(Identifier blueprintId, int streakTicks) {
            }

            @Override
            public void faulted(Identifier blueprintId, UUID node, String message) {
                throw new AssertionError("faute inattendue : " + message);
            }
        });
    }

    private static void apply(Blueprint bp, EditOperation op) {
        var result = op.apply(bp, LOADED.nodes());
        if (!result.applied()) {
            throw new AssertionError("opération refusée : " + result.refusal());
        }
    }

    /** tick-event → wait20 → record("réveillé"). */
    private Blueprint waitingBlueprint() {
        Blueprint bp = manager.create(Identifier.fromNamespaceAndPath("test", "sleeper")).orElseThrow();
        UUID tick = UUID.nameUUIDFromBytes("p-t".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        UUID wait = UUID.nameUUIDFromBytes("p-w".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        UUID record = UUID.nameUUIDFromBytes("p-r".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        apply(bp, new EditOperation.AddNode(tick, StandardEvents.SERVER_TICK.id(), Vec2d.ZERO));
        apply(bp, new EditOperation.AddNode(wait,
                Identifier.fromNamespaceAndPath("blueprint", "flow/wait"), Vec2d.ZERO));
        apply(bp, new EditOperation.AddNode(record,
                Identifier.fromNamespaceAndPath("spy", "record"), Vec2d.ZERO));
        apply(bp, new EditOperation.SetLiteral(wait, "ticks", LiteralValue.of(PinTypes.INT, 20)));
        apply(bp, new EditOperation.SetLiteral(record, "tag", LiteralValue.of(PinTypes.STRING, "réveillé")));
        apply(bp, new EditOperation.AddLink(new Link(tick, "exec_out", wait, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(wait, "exec_out", record, "exec_in")));
        return bp;
    }

    /** Encode via le codec SavedData réel (NbtOps), comme Minecraft le ferait. */
    private static CompoundTag saveCycle(BlueprintStorage storage) {
        var encoded = BlueprintStorage.TYPE.codec().encodeStart(NbtOps.INSTANCE, storage).getOrThrow();
        return (CompoundTag) encoded;
    }

    private static BlueprintStorage loadCycle(CompoundTag tag) {
        return BlueprintStorage.TYPE.codec().parse(NbtOps.INSTANCE, tag).getOrThrow();
    }

    @Test
    void fullWorldRestartCycle() {
        // 1. Le monde tourne : démo + un blueprint en attente au milieu d'un wait.
        manager.adopt(DemoBlueprint.build(LOADED.nodes()));
        Blueprint sleeper = waitingBlueprint();
        var dispatcher = new EventDispatcher(new EventDispatcher.ThreadGate() {
            @Override
            public boolean isOnThread() {
                return true;
            }

            @Override
            public void submit(Runnable task) {
                task.run();
            }
        });
        new BlueprintEventBridge(manager, LOADED.nodes(), scheduler, envFactory)
                .wire(dispatcher, LOADED.events().all());
        dispatcher.fire(StandardEvents.SERVER_TICK, payload -> {
        });
        scheduler.tick(10_000);
        assertTrue(RECORDS.isEmpty(), "suspendu au milieu du wait");
        assertEquals(1, scheduler.activeCount());

        // 2. Sauvegarde du monde : capture vivante via le codec — non destructive.
        var storage = new BlueprintStorage();
        storage.bindLive(manager, scheduler);
        CompoundTag saved = saveCycle(storage);
        assertEquals(1, scheduler.activeCount(), "sauvegarder n'arrête pas les exécutions");

        // 3. « Redémarrage » : tout est neuf.
        var manager2 = new BlueprintManager();
        var scheduler2 = newScheduler();
        var restored = loadCycle(saved);
        var report = PersistenceHooks.restore(restored, manager2, scheduler2, LOADED,
                RefResolver.NONE, envFactory);

        assertEquals(2, report.blueprintsLoaded());
        assertEquals(0, report.blueprintsCorrupt());
        assertEquals(1, report.executionsResumed());
        assertEquals(0, report.executionsCancelled());
        assertTrue(manager2.get(DemoBlueprint.ID).isPresent());

        // 4. L'attente reprend là où elle en était, et finit.
        for (int i = 0; i < 25; i++) {
            scheduler2.tick(10_000);
        }
        assertEquals(List.of("réveillé"), RECORDS, "le wait a traversé le redémarrage du monde");
    }

    @Test
    void corruptBlueprintIsPreservedRawNeverLost() {
        var storage = new BlueprintStorage();
        CompoundTag garbage = new CompoundTag();
        garbage.putString("id", "pas un identifiant valide §§");
        storage.blueprintTags().add(garbage);

        var report = PersistenceHooks.restore(storage, manager, scheduler, LOADED,
                RefResolver.NONE, envFactory);
        assertEquals(0, report.blueprintsLoaded());
        assertEquals(1, report.blueprintsCorrupt());

        // Le brut est ré-émis à la sauvegarde suivante (P4) — même sans état vivant lié.
        CompoundTag saved = saveCycle(storage);
        var reloaded = loadCycle(saved);
        assertEquals(1, reloaded.corruptTags().size());
        assertEquals(garbage, reloaded.corruptTags().get(0));
    }

    @Test
    void staleRevisionCancelsResumeCleanly() {
        Blueprint sleeper = waitingBlueprint();
        var dispatcher = new EventDispatcher(new EventDispatcher.ThreadGate() {
            @Override
            public boolean isOnThread() {
                return true;
            }

            @Override
            public void submit(Runnable task) {
                task.run();
            }
        });
        new BlueprintEventBridge(manager, LOADED.nodes(), scheduler, envFactory)
                .wire(dispatcher, LOADED.events().all());
        dispatcher.fire(StandardEvents.SERVER_TICK, payload -> {
        });
        scheduler.tick(10_000);

        var storage = new BlueprintStorage();
        storage.bindLive(manager, scheduler);
        CompoundTag saved = saveCycle(storage);

        // Le blueprint est édité APRÈS la sauvegarde : la reprise doit s'annuler.
        apply(sleeper, new EditOperation.MoveNode(
                UUID.nameUUIDFromBytes("p-w".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                new Vec2d(9, 9)));
        var restored = loadCycle(saved);
        var manager2 = new BlueprintManager();
        // On réinjecte la version ÉDITÉE (révision plus récente que la capture).
        manager2.adopt(sleeper);
        var storage2 = new BlueprintStorage();
        storage2.suspendedTags().addAll(restored.suspendedTags());
        var report = PersistenceHooks.restore(storage2, manager2, newScheduler(), LOADED,
                RefResolver.NONE, envFactory);
        assertEquals(0, report.executionsResumed());
        assertEquals(1, report.executionsCancelled());
    }

    @Test
    void corruptBlueprintRevivesWhenDecodableAgain() {
        // Régression QA PERSIST-001 : un tag préservé brut est RETENTÉ au démarrage
        // suivant — si le mod manquant est revenu, le blueprint revit (P4).
        var storage = new BlueprintStorage();
        storage.corruptTags().add(
                fr.blueprint.core.graph.GraphNbt.encode(DemoBlueprint.build(LOADED.nodes())));
        var report = PersistenceHooks.restore(storage, manager, scheduler, LOADED,
                RefResolver.NONE, envFactory);
        assertEquals(1, report.blueprintsLoaded(), "le préservé redevenu décodable revit");
        assertEquals(0, report.blueprintsCorrupt());
        assertTrue(storage.corruptTags().isEmpty(), "plus rien à préserver");
        assertTrue(manager.get(DemoBlueprint.ID).isPresent());
    }

    @Test
    void malformedSuspendedExecutionNeverCrashesRestore() {
        // Régression QA PERSIST-002 : UUID malformé = annulation comptée, jamais un crash.
        var storage = new BlueprintStorage();
        CompoundTag bad = new CompoundTag();
        bad.putString("blueprint", "test:x");
        bad.putString("event", "test:e");
        bad.putString("entry", "pas-un-uuid");
        storage.suspendedTags().add(bad);
        var report = PersistenceHooks.restore(storage, manager, scheduler, LOADED,
                RefResolver.NONE, envFactory);
        assertEquals(1, report.executionsCancelled());
        assertEquals(0, report.executionsResumed());
    }

    @Test
    void storageCodecRoundTripsRawLists() {
        var storage = new BlueprintStorage();
        CompoundTag a = new CompoundTag();
        a.putString("k", "v");
        storage.blueprintTags().add(a);
        storage.suspendedTags().add(a.copy());
        storage.corruptTags().add(a.copy());

        var reloaded = loadCycle(saveCycle(storage));
        assertNotNull(reloaded);
        assertEquals(1, reloaded.blueprintTags().size());
        assertEquals(1, reloaded.suspendedTags().size());
        assertEquals(1, reloaded.corruptTags().size());
        assertEquals(a, reloaded.blueprintTags().get(0));
    }
}
