package fr.blueprint.core.compile;

import fr.blueprint.api.BlueprintPlugin;
import fr.blueprint.api.event.TriggerContext;
import fr.blueprint.api.node.BlueprintHandle;
import fr.blueprint.api.node.NodeCategories;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.compile.ir.Ir;
import fr.blueprint.core.compile.ir.IrNbt;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.Vec2d;
import fr.blueprint.core.registry.PluginLoader;
import fr.blueprint.core.vm.BlueprintVm;
import fr.blueprint.core.vm.ExecResult;
import fr.blueprint.core.vm.ExecutionEnvironment;
import fr.blueprint.core.vm.ExecutionState;
import fr.blueprint.core.vm.VarStore;
import fr.blueprint.testmod.TestPlugin;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bout en bout compilé (stories 3.2 + 3.3) : des graphes réels s'exécutent. */
class CompilerVmTest {

    private static final List<String> RECORDS = new ArrayList<>();
    private static final AtomicInteger CONCATS = new AtomicInteger();
    private static final AtomicInteger PROBES = new AtomicInteger();

    /** Nœuds espions : ils tracent l'exécution pour que les tests l'observent. */
    private static final BlueprintPlugin SPY = registry -> {
        registry.register(NodeType.builder(id("start"))
                .category(NodeCategories.EVENT).execOut("exec_out")
                .action(ctx -> {
                }).build());
        registry.register(NodeType.builder(id("record"))
                .exec().in("tag", PinTypes.STRING, "?")
                .action(ctx -> RECORDS.add(ctx.in("tag"))).build());
        registry.register(NodeType.builder(id("concat"))
                .pure().in("a", PinTypes.STRING, "a").in("b", PinTypes.STRING, "b")
                .out("text", PinTypes.STRING)
                .action(ctx -> {
                    CONCATS.incrementAndGet();
                    ctx.out("text", ctx.<String>in("a") + ctx.<String>in("b"));
                }).build());
        registry.register(NodeType.builder(id("wait"))
                .exec().action(ctx -> ctx.suspend(20)).build());
        // Un nœud À EXÉCUTION qui porte une sortie de donnée — la forme de
        // « entity/position ». C'est le cas que home.bp câblait sans le savoir.
        registry.register(NodeType.builder(id("probe"))
                .exec().out("value", PinTypes.STRING)
                .action(ctx -> {
                    PROBES.incrementAndGet();
                    ctx.out("value", "sonde");
                }).build());
    };

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("spy", path);
    }

    private static final PluginLoader.LoadedRegistries LOADED = PluginLoader.load(List.of(
            new PluginLoader.PluginEntry("spy", SPY),
            new PluginLoader.PluginEntry("blueprint_testmod", new TestPlugin())));

    private static final BlueprintHandle HANDLE = new BlueprintHandle() {
        @Override
        public Identifier id() {
            return Identifier.fromNamespaceAndPath("test", "graph");
        }

        @Override
        public boolean enabled() {
            return true;
        }
    };
    private static final TriggerContext TRIGGER = new TriggerContext() {
        @Override
        public Identifier eventId() {
            return Identifier.fromNamespaceAndPath("test", "manual");
        }

        @Override
        public Object output(String name) {
            return null;
        }
    };

    @BeforeEach
    void reset() {
        RECORDS.clear();
        CONCATS.set(0);
        PROBES.set(0);
    }

    /**
     * <b>Un nœud à exécution utilisé comme valeur ne s'exécute pas.</b>
     *
     * <p>C'est ce qui cassait {@code home.bp} : il écrivait
     * {@code var/set(value: entity/position(entity: $player))}, ce qui donne un lien de
     * donnée depuis un nœud à exécution que rien ne place dans la chaîne. Le compilateur
     * n'émet un producteur que s'il est <b>pur</b> ({@code Compiler.prepareInput}) ; celui-ci
     * ne l'est pas, donc sa case de sortie n'est jamais écrite, et le consommateur lit ce
     * qui traîne. Le joueur atterrissait à une position qui n'était celle de personne.
     *
     * <p>Le test fige la règle telle qu'elle est <b>aujourd'hui</b> : la sonde ne tourne pas
     * et rien n'arrive au consommateur. Il ne dit pas que c'est bien — il dit que tant que
     * ça reste ainsi, l'éditeur et BScript doivent refuser ce câblage plutôt que produire
     * un graphe qui ment. Le jour où le compilateur hissera ces nœuds, ce test tombera, et
     * c'est exactement le moment où il faut relire les deux refus.
     */
    @Test
    void unNoeudAExecutionUtiliseCommeValeurNeSExecutePas() {
        var bp = graph();
        UUID start = node(bp, "s", id("start"));
        UUID probe = node(bp, "p", id("probe"));
        UUID rec = node(bp, "r", id("record"));
        apply(bp, new EditOperation.AddLink(new Link(start, "exec_out", rec, "exec_in")));
        // La sonde alimente « tag » SANS être dans la chaîne d'exécution.
        apply(bp, new EditOperation.AddLink(new Link(probe, "value", rec, "tag")));

        Ir ir = compileOk(bp, start);
        assertInstanceOf(ExecResult.Done.class,
                BlueprintVm.run(ir, ExecutionState.fresh(ir), env(), 1000));

        assertEquals(0, PROBES.get(),
                "la sonde n'est pas dans la chaîne : le compilateur ne l'émet pas");
        // « ? » est le défaut déclaré du pin « tag ». C'est le pire des cas : la valeur
        // n'est ni juste ni absente, elle est plausible. Rien dans le jeu ne distingue un
        // défaut d'une vraie lecture — d'où un point de retour qui n'était celui de
        // personne, sans une ligne dans le journal.
        assertEquals(List.of("?"), RECORDS,
                "le consommateur retombe sur le défaut du pin, silencieusement");
    }

    private static ExecutionEnvironment env() {
        return new ExecutionEnvironment(typeId -> LOADED.nodes().get(typeId).orElse(null),
                HANDLE, TRIGGER, VarStore.inMemory(), null, null,
                LoggerFactory.getLogger("blueprint-test"));
    }

    private static Blueprint graph() {
        return new Blueprint(Identifier.fromNamespaceAndPath("test", "graph"));
    }

    private static UUID node(Blueprint bp, String seed, Identifier type) {
        UUID uuid = UUID.nameUUIDFromBytes(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        apply(bp, new EditOperation.AddNode(uuid, type, Vec2d.ZERO));
        return uuid;
    }

    private static void apply(Blueprint bp, EditOperation op) {
        var result = op.apply(bp, LOADED.nodes());
        if (!result.applied()) {
            throw new AssertionError("opération refusée : " + result.refusal());
        }
    }

    private static Ir compileOk(Blueprint bp, UUID start) {
        Compiler.CompileResult result = Compiler.compile(bp, LOADED.nodes(), start);
        assertTrue(result.success(), () -> "compilation échouée : " + result.diagnostics());
        return result.ir();
    }

    @Test
    void linearChainRunsInOrder() {
        var bp = graph();
        UUID start = node(bp, "s", id("start"));
        UUID r1 = node(bp, "r1", id("record"));
        UUID r2 = node(bp, "r2", id("record"));
        apply(bp, new EditOperation.SetLiteral(r1, "tag", LiteralValue.of(PinTypes.STRING, "un")));
        apply(bp, new EditOperation.SetLiteral(r2, "tag", LiteralValue.of(PinTypes.STRING, "deux")));
        apply(bp, new EditOperation.AddLink(new Link(start, "exec_out", r1, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(r1, "exec_out", r2, "exec_in")));

        Ir ir = compileOk(bp, start);
        assertInstanceOf(ExecResult.Done.class,
                BlueprintVm.run(ir, ExecutionState.fresh(ir), env(), 1000));
        assertEquals(List.of("un", "deux"), RECORDS);
    }

    @Test
    void branchesFollowTheActionsChoice() {
        for (int value : new int[]{4, 7}) {
            RECORDS.clear();
            var bp = graph();
            UUID start = node(bp, "s", id("start"));
            UUID branch = node(bp, "b",
                    Identifier.fromNamespaceAndPath("blueprint_testmod", "odd_or_even"));
            UUID even = node(bp, "even", id("record"));
            UUID odd = node(bp, "odd", id("record"));
            apply(bp, new EditOperation.SetLiteral(branch, "value", LiteralValue.of(PinTypes.INT, value)));
            apply(bp, new EditOperation.SetLiteral(even, "tag", LiteralValue.of(PinTypes.STRING, "pair")));
            apply(bp, new EditOperation.SetLiteral(odd, "tag", LiteralValue.of(PinTypes.STRING, "impair")));
            apply(bp, new EditOperation.AddLink(new Link(start, "exec_out", branch, "exec_in")));
            apply(bp, new EditOperation.AddLink(new Link(branch, "even", even, "exec_in")));
            apply(bp, new EditOperation.AddLink(new Link(branch, "odd", odd, "exec_in")));

            Ir ir = compileOk(bp, start);
            BlueprintVm.run(ir, ExecutionState.fresh(ir), env(), 1000);
            assertEquals(List.of(value % 2 == 0 ? "pair" : "impair"), RECORDS);
        }
    }

    @Test
    void pureNodeIsMemoizedAcrossConsumers() {
        // concat alimente DEUX records : émis une fois, invoqué une fois (FR13).
        var bp = graph();
        UUID start = node(bp, "s", id("start"));
        UUID concat = node(bp, "c", id("concat"));
        UUID r1 = node(bp, "r1", id("record"));
        UUID r2 = node(bp, "r2", id("record"));
        apply(bp, new EditOperation.AddLink(new Link(start, "exec_out", r1, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(r1, "exec_out", r2, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(concat, "text", r1, "tag")));
        apply(bp, new EditOperation.AddLink(new Link(concat, "text", r2, "tag")));

        Ir ir = compileOk(bp, start);
        BlueprintVm.run(ir, ExecutionState.fresh(ir), env(), 1000);
        assertEquals(List.of("ab", "ab"), RECORDS);
        assertEquals(1, CONCATS.get(), "le pur est mémoïsé par étape");
    }

    @Test
    void pureFeedingTwoBranchesIsCorrectOnBothPaths() {
        // Audit QA : concat alimente les DEUX branches d'un odd_or_even. La branche
        // émise en second ne doit PAS lire un slot jamais écrit (défaut silencieux).
        for (int value : new int[]{4, 7}) {
            RECORDS.clear();
            CONCATS.set(0);
            var bp = graph();
            UUID start = node(bp, "pb-s", id("start"));
            UUID branch = node(bp, "pb-b",
                    Identifier.fromNamespaceAndPath("blueprint_testmod", "odd_or_even"));
            UUID concat = node(bp, "pb-c", id("concat"));
            UUID even = node(bp, "pb-e", id("record"));
            UUID odd = node(bp, "pb-o", id("record"));
            apply(bp, new EditOperation.SetLiteral(branch, "value", LiteralValue.of(PinTypes.INT, value)));
            apply(bp, new EditOperation.AddLink(new Link(start, "exec_out", branch, "exec_in")));
            apply(bp, new EditOperation.AddLink(new Link(branch, "even", even, "exec_in")));
            apply(bp, new EditOperation.AddLink(new Link(branch, "odd", odd, "exec_in")));
            apply(bp, new EditOperation.AddLink(new Link(concat, "text", even, "tag")));
            apply(bp, new EditOperation.AddLink(new Link(concat, "text", odd, "tag")));

            Ir ir = compileOk(bp, start);
            BlueprintVm.run(ir, ExecutionState.fresh(ir), env(), 1000);
            assertEquals(List.of("ab"), RECORDS,
                    "valeur " + value + " : la branche prise doit lire la vraie valeur du pur");
            assertEquals(1, CONCATS.get(), "le pur s'exécute une fois sur le chemin pris");
        }
    }

    @Test
    void execLoopExhaustsFuelInsteadOfHanging() {
        var bp = graph();
        UUID start = node(bp, "s", id("start"));
        UUID r1 = node(bp, "r1", id("record"));
        UUID r2 = node(bp, "r2", id("record"));
        apply(bp, new EditOperation.AddLink(new Link(start, "exec_out", r1, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(r1, "exec_out", r2, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(r2, "exec_out", r1, "exec_in")));   // boucle

        Ir ir = compileOk(bp, start);
        var state = ExecutionState.fresh(ir);
        assertInstanceOf(ExecResult.OutOfFuel.class, BlueprintVm.run(ir, state, env(), 30));
        int firstBatch = RECORDS.size();
        assertTrue(firstBatch > 2, "la boucle a tourné plusieurs fois avant l'épuisement");
        // Reprise : l'état est préservé, la boucle continue.
        assertInstanceOf(ExecResult.OutOfFuel.class, BlueprintVm.run(ir, state, env(), 30));
        assertTrue(RECORDS.size() > firstBatch);
    }

    @Test
    void suspensionPausesThenResumesToTheNextNode() {
        var bp = graph();
        UUID start = node(bp, "s", id("start"));
        UUID wait = node(bp, "w", id("wait"));
        UUID after = node(bp, "a", id("record"));
        apply(bp, new EditOperation.SetLiteral(after, "tag", LiteralValue.of(PinTypes.STRING, "après")));
        apply(bp, new EditOperation.AddLink(new Link(start, "exec_out", wait, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(wait, "exec_out", after, "exec_in")));

        Ir ir = compileOk(bp, start);
        var state = ExecutionState.fresh(ir);
        assertEquals(new ExecResult.Suspended(20), BlueprintVm.run(ir, state, env(), 1000));
        assertTrue(RECORDS.isEmpty(), "rien après la suspension au premier run");
        assertInstanceOf(ExecResult.Done.class, BlueprintVm.run(ir, state, env(), 1000));
        assertEquals(List.of("après"), RECORDS);
    }

    @Test
    void ghostGraphFailsCompilationWithDiagnostics() {
        var bp = graph();
        UUID ghost = node(bp, "g", Identifier.fromNamespaceAndPath("absent", "node"));
        Compiler.CompileResult result = Compiler.compile(bp, LOADED.nodes(), ghost);
        assertFalse(result.success());
        assertNull(result.ir());
        assertTrue(result.diagnostics().stream()
                .anyMatch(d -> d.code() == fr.blueprint.core.graph.DiagnosticCode.UNKNOWN_NODE_TYPE));
    }

    @Test
    void compiledIrSurvivesNbtRoundTrip() {
        var bp = graph();
        UUID start = node(bp, "s", id("start"));
        UUID r1 = node(bp, "r1", id("record"));
        apply(bp, new EditOperation.SetLiteral(r1, "tag", LiteralValue.of(PinTypes.STRING, "cache")));
        apply(bp, new EditOperation.AddLink(new Link(start, "exec_out", r1, "exec_in")));

        Ir original = compileOk(bp, start);
        Ir decoded = IrNbt.decode(IrNbt.encode(original),
                typeId -> LOADED.pinTypes().get(typeId).orElse(null));
        assertEquals(original, decoded, "round-trip NBT de l'IR (cache entre démarrages)");

        // Et l'IR rechargée s'exécute à l'identique.
        BlueprintVm.run(decoded, ExecutionState.fresh(decoded), env(), 1000);
        assertEquals(List.of("cache"), RECORDS);
    }
}
