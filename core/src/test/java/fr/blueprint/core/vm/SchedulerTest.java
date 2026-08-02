package fr.blueprint.core.vm;

import fr.blueprint.api.BlueprintPlugin;
import fr.blueprint.api.event.TriggerContext;
import fr.blueprint.api.node.BlueprintHandle;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.compile.Compiler;
import fr.blueprint.core.compile.ir.Ir;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.Vec2d;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.nbt.CompoundTag;
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

/** Ordonnanceur (story 3.5) + traversée NBT du wait (story 3.4, AC4). */
class SchedulerTest {

    private static final List<String> RECORDS = new ArrayList<>();

    private static final BlueprintPlugin SPY = registry -> {
        registry.register(NodeType.builder(sid("start")).execOut("exec_out").action(ctx -> {
        }).build());
        registry.register(NodeType.builder(sid("record"))
                .exec().in("tag", PinTypes.STRING, "?")
                .action(ctx -> RECORDS.add(ctx.in("tag"))).build());
        registry.register(NodeType.builder(sid("wait20"))
                .exec().action(ctx -> ctx.suspend(20)).build());
        registry.register(NodeType.builder(sid("boom"))
                .exec().action(ctx -> {
                    throw new IllegalStateException("nœud cassé");
                }).build());
    };

    private static Identifier sid(String path) {
        return Identifier.fromNamespaceAndPath("sched", path);
    }

    private static final PluginLoader.LoadedRegistries LOADED = PluginLoader.load(List.of(
            new PluginLoader.PluginEntry("sched", SPY)));

    private record DisabledEvent(Identifier blueprint, int streak) {
    }

    private record FaultEvent(Identifier blueprint, UUID node, String message) {
    }

    private final List<DisabledEvent> disabledEvents = new ArrayList<>();
    private final List<FaultEvent> faultEvents = new ArrayList<>();

    private BlueprintScheduler scheduler(int maxStreak) {
        return new BlueprintScheduler(maxStreak, new BlueprintScheduler.Listener() {
            @Override
            public void disabled(Identifier blueprintId, int streakTicks) {
                disabledEvents.add(new DisabledEvent(blueprintId, streakTicks));
            }

            @Override
            public void faulted(Identifier blueprintId, UUID node, String message) {
                faultEvents.add(new FaultEvent(blueprintId, node, message));
            }
        });
    }

    @BeforeEach
    void reset() {
        RECORDS.clear();
        disabledEvents.clear();
        faultEvents.clear();
    }

    // ---------------------------------------------------------------- fixtures

    private static ExecutionEnvironment env(Identifier blueprintId) {
        BlueprintHandle handle = new BlueprintHandle() {
            @Override
            public Identifier id() {
                return blueprintId;
            }

            @Override
            public boolean enabled() {
                return true;
            }
        };
        TriggerContext trigger = new TriggerContext() {
            @Override
            public Identifier eventId() {
                return Identifier.fromNamespaceAndPath("sched", "manual");
            }

            @Override
            public Object output(String name) {
                return null;
            }
        };
        return new ExecutionEnvironment(typeId -> LOADED.nodes().get(typeId).orElse(null),
                handle, trigger, VarStore.inMemory(), null, null,
                LoggerFactory.getLogger("blueprint-test"));
    }

    private static void apply(Blueprint bp, EditOperation op) {
        var result = op.apply(bp, LOADED.nodes());
        if (!result.applied()) {
            throw new AssertionError("opération refusée : " + result.refusal());
        }
    }

    private static UUID node(Blueprint bp, String seed, String type) {
        UUID uuid = UUID.nameUUIDFromBytes(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        apply(bp, new EditOperation.AddNode(uuid, sid(type), Vec2d.ZERO));
        return uuid;
    }

    private record Compiled(Blueprint bp, UUID start, Ir ir) {
    }

    /** start → record(tag). */
    private static Compiled chainGraph(String name, String tag) {
        var bp = new Blueprint(Identifier.fromNamespaceAndPath("sched", name));
        UUID start = node(bp, name + "-s", "start");
        UUID record = node(bp, name + "-r", "record");
        apply(bp, new EditOperation.SetLiteral(record, "tag", LiteralValue.of(PinTypes.STRING, tag)));
        apply(bp, new EditOperation.AddLink(new Link(start, "exec_out", record, "exec_in")));
        return compiled(bp, start);
    }

    /** start → record(tag) → record (boucle infinie). */
    private static Compiled loopGraph(String name, String tag) {
        var bp = new Blueprint(Identifier.fromNamespaceAndPath("sched", name));
        UUID start = node(bp, name + "-s", "start");
        UUID record = node(bp, name + "-r", "record");
        apply(bp, new EditOperation.SetLiteral(record, "tag", LiteralValue.of(PinTypes.STRING, tag)));
        apply(bp, new EditOperation.AddLink(new Link(start, "exec_out", record, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(record, "exec_out", record, "exec_in")));
        return compiled(bp, start);
    }

    private static Compiled compiled(Blueprint bp, UUID start) {
        Compiler.CompileResult result = Compiler.compile(bp, LOADED.nodes(), start);
        assertTrue(result.success(), () -> "compilation : " + result.diagnostics());
        return new Compiled(bp, start, result.ir());
    }

    // ------------------------------------------------------------------- tests

    @Test
    void chainCompletesInOneTickWithStats() {
        var scheduler = scheduler(100);
        Compiled chain = chainGraph("chain", "ok");
        scheduler.launch(chain.bp().id(), chain.ir(), env(chain.bp().id()));

        scheduler.tick(10_000);

        assertEquals(List.of("ok"), RECORDS);
        assertEquals(0, scheduler.activeCount());
        var stats = scheduler.stats(chain.bp().id());
        assertEquals(1, stats.runs());
        assertEquals(1, stats.completed());
        assertTrue(stats.fuel() > 0);
        assertTrue(stats.peakNanos() > 0);
    }

    @Test
    void suspensionWaitsExactlyItsTicks() {
        var scheduler = scheduler(100);
        var bp = new Blueprint(Identifier.fromNamespaceAndPath("sched", "waiting"));
        UUID start = node(bp, "w-s", "start");
        UUID wait = node(bp, "w-w", "wait20");
        UUID after = node(bp, "w-a", "record");
        apply(bp, new EditOperation.SetLiteral(after, "tag", LiteralValue.of(PinTypes.STRING, "après")));
        apply(bp, new EditOperation.AddLink(new Link(start, "exec_out", wait, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(wait, "exec_out", after, "exec_in")));
        Compiled c = compiled(bp, start);
        scheduler.launch(bp.id(), c.ir(), env(bp.id()));

        scheduler.tick(10_000);   // exécute jusqu'à la suspension (20 ticks)
        assertTrue(RECORDS.isEmpty());
        for (int i = 0; i < 19; i++) {
            scheduler.tick(10_000);
        }
        assertTrue(RECORDS.isEmpty(), "19 ticks écoulés : toujours en attente");
        scheduler.tick(10_000);   // 20e tick : échéance atteinte, reprise
        assertEquals(List.of("après"), RECORDS);
        assertEquals(0, scheduler.activeCount());
    }

    @Test
    void waitSurvivesNbtRoundTripThroughRestart() {
        // AC4 (3.4) : la promesse fondatrice — wait 20t traverse une « sauvegarde/rechargement ».
        var scheduler = scheduler(100);
        var bp = new Blueprint(Identifier.fromNamespaceAndPath("sched", "restart"));
        UUID start = node(bp, "rs-s", "start");
        UUID wait = node(bp, "rs-w", "wait20");
        UUID after = node(bp, "rs-a", "record");
        apply(bp, new EditOperation.SetLiteral(after, "tag", LiteralValue.of(PinTypes.STRING, "revenu")));
        apply(bp, new EditOperation.AddLink(new Link(start, "exec_out", wait, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(wait, "exec_out", after, "exec_in")));
        Compiled c = compiled(bp, start);
        scheduler.launch(bp.id(), c.ir(), env(bp.id()));
        scheduler.tick(10_000);   // suspendu pour 20 ticks

        // « Arrêt du monde » : capture, sérialisation, extinction de l'ordonnanceur.
        List<SuspendedExecution> saved = scheduler.drainForSave();
        assertEquals(1, saved.size());
        assertEquals(0, scheduler.activeCount());
        CompoundTag nbt = ExecutionNbt.encode(saved.get(0));
        assertNotNull(nbt);

        // « Redémarrage » : nouvel ordonnanceur, décodage, recompilation, reprise.
        SuspendedExecution reloaded = ExecutionNbt.decode(nbt, RefResolver.NONE);
        assertNotNull(reloaded);
        assertEquals(c.ir().revision(), reloaded.revision(), "contrôle de révision du cache");
        var freshScheduler = scheduler(100);
        Ir recompiled = compiled(bp, start).ir();
        freshScheduler.resume(reloaded, recompiled, env(bp.id()));

        for (int i = 0; i < reloaded.remainingTicks(); i++) {
            assertTrue(RECORDS.isEmpty());
            freshScheduler.tick(10_000);
        }
        assertEquals(List.of("revenu"), RECORDS, "le wait a survécu au redémarrage");
    }

    @Test
    void overBudgetStreakDisablesTheBlueprint() {
        var scheduler = scheduler(3);
        Compiled loop = loopGraph("greedy", "spin");
        scheduler.launch(loop.bp().id(), loop.ir(), env(loop.bp().id()));

        scheduler.tick(30);
        scheduler.tick(30);
        assertTrue(disabledEvents.isEmpty(), "2 dépassements : pas encore de sanction");
        scheduler.tick(30);
        assertEquals(List.of(new DisabledEvent(loop.bp().id(), 3)), disabledEvents);
        assertEquals(0, scheduler.activeCount(), "les exécutions du glouton sont purgées");
        int recordsAtDisable = RECORDS.size();
        scheduler.tick(30);
        assertEquals(recordsAtDisable, RECORDS.size(), "plus rien ne tourne");
    }

    @Test
    void budgetIsSharedFairlyBetweenExecutions() {
        var scheduler = scheduler(100);
        Compiled a = loopGraph("loop-a", "a");
        Compiled b = loopGraph("loop-b", "b");
        scheduler.launch(a.bp().id(), a.ir(), env(a.bp().id()));
        scheduler.launch(b.bp().id(), b.ir(), env(b.bp().id()));

        scheduler.tick(60);
        assertTrue(RECORDS.contains("a") && RECORDS.contains("b"),
                "les deux exécutions progressent dans le même tick : " + RECORDS);
        long countA = RECORDS.stream().filter("a"::equals).count();
        long countB = RECORDS.stream().filter("b"::equals).count();
        assertTrue(Math.abs(countA - countB) <= 1, "répartition équitable : " + countA + "/" + countB);
    }

    @Test
    void faultNotifiesWithTheGuiltyNodeAndRemoves() {
        var scheduler = scheduler(100);
        var bp = new Blueprint(Identifier.fromNamespaceAndPath("sched", "faulty"));
        UUID start = node(bp, "f-s", "start");
        UUID boom = node(bp, "f-b", "boom");
        apply(bp, new EditOperation.AddLink(new Link(start, "exec_out", boom, "exec_in")));
        Compiled c = compiled(bp, start);
        scheduler.launch(bp.id(), c.ir(), env(bp.id()));

        scheduler.tick(10_000);

        assertEquals(1, faultEvents.size());
        assertEquals(bp.id(), faultEvents.get(0).blueprint());
        assertEquals(boom, faultEvents.get(0).node(), "la faute nomme le nœud fautif");
        assertTrue(faultEvents.get(0).message().contains("nœud cassé"));
        assertEquals(0, scheduler.activeCount());
        assertEquals(1, scheduler.stats(bp.id()).faults());
    }
}
