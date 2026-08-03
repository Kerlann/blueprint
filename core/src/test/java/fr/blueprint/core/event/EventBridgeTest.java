package fr.blueprint.core.event;

import fr.blueprint.api.BlueprintPlugin;
import fr.blueprint.api.event.EventRegistry;
import fr.blueprint.api.event.EventType;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.BlueprintManager;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.GraphValidator;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.Vec2d;
import fr.blueprint.core.registry.PluginLoader;
import fr.blueprint.core.vm.BlueprintScheduler;
import fr.blueprint.core.vm.ExecutionEnvironment;
import fr.blueprint.core.vm.VarStore;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le produit devient jouable (story 7.6, AC5-AC6) : un événement émis déclenche les
 * blueprints actifs qui portent son nœud, de bout en bout — dispatcher → pont →
 * compilation → ordonnanceur → nœuds standard.
 */
class EventBridgeTest {

    private static final List<String> RECORDS = new ArrayList<>();

    private static final EventType SCORE_EVENT = EventType.builder(
                    Identifier.fromNamespaceAndPath("mymod", "score_changed"))
            .out("points", PinTypes.INT).build();

    private static final BlueprintPlugin SPY = new BlueprintPlugin() {
        @Override
        public void registerNodes(fr.blueprint.api.registry.NodeRegistry registry) {
            registry.register(NodeType.builder(Identifier.fromNamespaceAndPath("spy", "record"))
                    .exec().in("tag", PinTypes.STRING, "?")
                    .action(ctx -> RECORDS.add(ctx.in("tag"))).build());
        }

        @Override
        public void registerEvents(EventRegistry registry) {
            registry.register(SCORE_EVENT);
        }
    };

    private static final PluginLoader.LoadedRegistries LOADED = PluginLoader.load(List.of(
            new PluginLoader.PluginEntry("spy", SPY)), true);

    private EventDispatcher dispatcher;
    private BlueprintScheduler scheduler;
    private BlueprintManager manager;
    private BlueprintEventBridge bridge;

    @BeforeEach
    void setup() {
        RECORDS.clear();
        dispatcher = new EventDispatcher(new EventDispatcher.ThreadGate() {
            @Override
            public boolean isOnThread() {
                return true;
            }

            @Override
            public void submit(Runnable task) {
                task.run();
            }
        });
        scheduler = new BlueprintScheduler(100, new BlueprintScheduler.Listener() {
            @Override
            public void disabled(Identifier blueprintId, int streakTicks) {
            }

            @Override
            public void faulted(Identifier blueprintId, UUID node, String message) {
                throw new AssertionError("faute inattendue : " + message);
            }
        });
        manager = new BlueprintManager();
        bridge = new BlueprintEventBridge(manager, LOADED.nodes(), scheduler,
                (bp, trigger) -> new ExecutionEnvironment(
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
                        LoggerFactory.getLogger("blueprint-test")));
        bridge.wire(dispatcher, LOADED.events().all());
    }

    private static void apply(Blueprint bp, EditOperation op) {
        var result = op.apply(bp, LOADED.nodes());
        if (!result.applied()) {
            throw new AssertionError("opération refusée : " + result.refusal());
        }
    }

    private static UUID node(Blueprint bp, String seed, Identifier type) {
        UUID uuid = UUID.nameUUIDFromBytes(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        apply(bp, new EditOperation.AddNode(uuid, type, Vec2d.ZERO));
        return uuid;
    }

    @Test
    void eventNodesAreSynthesizedAsEntryPoints() {
        // AC5 : événements standard ET tiers deviennent des nœuds d'entrée.
        var tickNode = LOADED.nodes().get(StandardEvents.SERVER_TICK.id()).orElseThrow();
        assertTrue(tickNode.entryPoint());
        assertTrue(LOADED.nodes().shape(StandardEvents.SERVER_TICK.id()).entryPoint());
        var scoreNode = LOADED.nodes().get(SCORE_EVENT.id()).orElseThrow();
        assertTrue(scoreNode.entryPoint());
        assertEquals("points", scoreNode.outputs().get(1).name());   // exec_out puis points
        assertEquals("spy", LOADED.nodes().providerOf(SCORE_EVENT.id()).orElseThrow());
    }

    @Test
    void serverTickTriggersActiveBlueprints() {
        Blueprint bp = manager.create(Identifier.fromNamespaceAndPath("test", "on_tick")).orElseThrow();
        UUID tick = node(bp, "t", StandardEvents.SERVER_TICK.id());
        UUID record = node(bp, "r", Identifier.fromNamespaceAndPath("spy", "record"));
        apply(bp, new EditOperation.SetLiteral(record, "tag", LiteralValue.of(PinTypes.STRING, "tick !")));
        apply(bp, new EditOperation.AddLink(new Link(tick, "exec_out", record, "exec_in")));
        // Avec un nœud d'événement, plus d'avertissement NO_ENTRY_POINT.
        assertTrue(GraphValidator.validate(bp, LOADED.nodes()).diagnostics().isEmpty());

        fr.blueprint.api.event.BlueprintEvents.install(dispatcher);
        try {
            fr.blueprint.api.event.BlueprintEvents.fire(StandardEvents.SERVER_TICK, payload -> {
            });
        } finally {
            fr.blueprint.api.event.BlueprintEvents.uninstall();
        }
        scheduler.tick(10_000);
        assertEquals(List.of("tick !"), RECORDS);
    }

    /**
     * QA NET-003 : depuis que l'éditeur enregistre par le réseau (6.3), la révision
     * d'un blueprint monte des dizaines de fois par session. Le cache d'IR doit rester
     * borné par (blueprints × nœuds d'entrée), pas croître à chaque enregistrement.
     */
    @Test
    void theIrCacheDoesNotGrowWithEveryRevision() {
        Blueprint bp = manager.create(Identifier.fromNamespaceAndPath("test", "edited")).orElseThrow();
        UUID tick = node(bp, "t3", StandardEvents.SERVER_TICK.id());
        UUID record = node(bp, "r3", Identifier.fromNamespaceAndPath("spy", "record"));
        apply(bp, new EditOperation.SetLiteral(record, "tag", LiteralValue.of(PinTypes.STRING, "x")));
        apply(bp, new EditOperation.AddLink(new Link(tick, "exec_out", record, "exec_in")));

        for (int i = 0; i < 20; i++) {
            dispatcher.fire(StandardEvents.SERVER_TICK, payload -> {
            });
            scheduler.tick(10_000);
            apply(bp, new EditOperation.MoveNode(record, new Vec2d(i, 0)));
        }

        assertEquals(20, RECORDS.size(), "chaque tick a bien déclenché le graphe");
        assertEquals(1, bridge.cachedIrCount(),
                "une entrée par nœud d'événement — pas une par révision");
    }

    @Test
    void eventPayloadFlowsIntoTheGraphThroughStandardNodes() {
        // score_changed(points) → to_string → record : la charge utile traverse le
        // nœud d'événement synthétisé, la conversion standard, jusqu'à l'espion.
        Blueprint bp = manager.create(Identifier.fromNamespaceAndPath("test", "on_score")).orElseThrow();
        UUID event = node(bp, "e", SCORE_EVENT.id());
        UUID toString = node(bp, "ts", Identifier.fromNamespaceAndPath("blueprint", "convert/to_string"));
        UUID record = node(bp, "r2", Identifier.fromNamespaceAndPath("spy", "record"));
        apply(bp, new EditOperation.AddLink(new Link(event, "exec_out", record, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(event, "points", toString, "value")));
        apply(bp, new EditOperation.AddLink(new Link(toString, "result", record, "tag")));

        dispatcher.fire(SCORE_EVENT, payload -> payload.set("points", 1250));
        scheduler.tick(10_000);
        assertEquals(List.of("1250"), RECORDS);
    }

    @Test
    void disabledBlueprintsAreNotTriggered() {
        Blueprint bp = manager.create(Identifier.fromNamespaceAndPath("test", "sleepy")).orElseThrow();
        UUID tick = node(bp, "st", StandardEvents.SERVER_TICK.id());
        UUID record = node(bp, "sr", Identifier.fromNamespaceAndPath("spy", "record"));
        apply(bp, new EditOperation.AddLink(new Link(tick, "exec_out", record, "exec_in")));
        manager.setEnabled(bp.id(), false);

        dispatcher.fire(StandardEvents.SERVER_TICK, payload -> {
        });
        scheduler.tick(10_000);
        assertTrue(RECORDS.isEmpty(), "un blueprint désactivé ne se déclenche pas");
        assertEquals(0, scheduler.activeCount());
    }

    @Test
    void irCacheInvalidatesOnRevisionChange() {
        Blueprint bp = manager.create(Identifier.fromNamespaceAndPath("test", "evolving")).orElseThrow();
        UUID tick = node(bp, "ev-t", StandardEvents.SERVER_TICK.id());
        UUID record = node(bp, "ev-r", Identifier.fromNamespaceAndPath("spy", "record"));
        apply(bp, new EditOperation.SetLiteral(record, "tag", LiteralValue.of(PinTypes.STRING, "v1")));
        apply(bp, new EditOperation.AddLink(new Link(tick, "exec_out", record, "exec_in")));

        dispatcher.fire(StandardEvents.SERVER_TICK, payload -> {
        });
        scheduler.tick(10_000);
        // Édition (révision bouge) : le cache doit recompiler, pas rejouer l'ancienne IR.
        apply(bp, new EditOperation.SetLiteral(record, "tag", LiteralValue.of(PinTypes.STRING, "v2")));
        dispatcher.fire(StandardEvents.SERVER_TICK, payload -> {
        });
        scheduler.tick(10_000);
        assertEquals(List.of("v1", "v2"), RECORDS);
    }
}
