package fr.blueprint.core.compile;

import fr.blueprint.api.event.TriggerContext;
import fr.blueprint.api.node.BlueprintHandle;
import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.event.StandardEvents;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.VarNodes;
import fr.blueprint.core.graph.VarScope;
import fr.blueprint.core.graph.Variable;
import fr.blueprint.core.graph.Vec2d;
import fr.blueprint.core.registry.PluginLoader;
import fr.blueprint.core.vm.BlueprintVm;
import fr.blueprint.core.vm.ExecResult;
import fr.blueprint.core.vm.ExecutionEnvironment;
import fr.blueprint.core.vm.ExecutionState;
import fr.blueprint.core.vm.VarStore;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story 7.1b : le flux structuré abaissé (frames CallSub + Jmp/JmpIf/Yield + Calls
 * synthétisés) s'exécute headless de bout en bout — sequence, for (somme 1..3),
 * while, do_once (drapeau GRAPH persistant), wait_until (suspension/reprise), switch.
 */
class StructuredFlowTest {

    private static final PluginLoader.LoadedRegistries LOADED =
            PluginLoader.load(List.of(), true);

    private static final BlueprintHandle HANDLE = new BlueprintHandle() {
        @Override
        public Identifier id() {
            return Identifier.fromNamespaceAndPath("test", "flow");
        }

        @Override
        public boolean enabled() {
            return true;
        }
    };

    private static final TriggerContext TRIGGER = new TriggerContext() {
        @Override
        public Identifier eventId() {
            return StandardEvents.SERVER_TICK.id();
        }

        @Override
        public Object output(String name) {
            return null;
        }
    };

    private static ExecutionEnvironment env(VarStore vars) {
        return new ExecutionEnvironment(id -> LOADED.nodes().get(id).orElse(null),
                HANDLE, TRIGGER, vars, null, null, LoggerFactory.getLogger("blueprint-test"));
    }

    private static Identifier node(String path) {
        return Identifier.fromNamespaceAndPath("blueprint", path);
    }

    private final Blueprint bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "flow"));

    private UUID add(String path) {
        UUID id = UUID.randomUUID();
        assertTrue(new EditOperation.AddNode(id, path.contains(":")
                        ? Identifier.tryParse(path) : node(path),
                new Vec2d(bp.nodes().size() * 50, 0)).apply(bp, LOADED.nodes()).applied());
        return id;
    }

    private void apply(EditOperation op) {
        assertTrue(op.apply(bp, LOOKED()).applied(), op::toString);
    }

    private fr.blueprint.core.graph.NodeTypeLookup LOOKED() {
        return LOADED.nodes();
    }

    private UUID varSet(String variable, double value) {
        UUID set = add("var/set");
        apply(new EditOperation.SetLiteral(set, "var", LiteralValue.of(PinTypes.STRING, variable)));
        apply(new EditOperation.SetLiteral(set, "value", LiteralValue.of(PinTypes.DOUBLE, value)));
        return set;
    }

    private void declareVar(String name) {
        apply(new EditOperation.AddVariable(new Variable(name, PinTypes.DOUBLE,
                LiteralValue.of(PinTypes.DOUBLE, 0.0), VarScope.GRAPH, false)));
    }

    private ExecResult compileAndRun(UUID entry, VarStore vars) {
        Compiler.CompileResult result = Compiler.compile(bp, LOADED.nodes(), entry);
        assertTrue(result.success(), () -> "diagnostics : " + result.diagnostics());
        return BlueprintVm.run(result.ir(), ExecutionState.fresh(result.ir()), env(vars), 10_000);
    }

    @Test
    void sequenceExecuteSesBranchesDansLOrdre() {
        declareVar("a");
        declareVar("b");
        UUID tick = add(StandardEvents.SERVER_TICK.id().toString());
        UUID seq = add("flow/sequence");
        UUID setA = varSet("a", 1);
        UUID setB = varSet("b", 2);
        apply(new EditOperation.AddLink(new Link(tick, "exec_out", seq, "exec_in")));
        apply(new EditOperation.AddLink(new Link(seq, "then_1", setA, "exec_in")));
        apply(new EditOperation.AddLink(new Link(seq, "then_2", setB, "exec_in")));

        VarStore vars = VarStore.inMemory();
        assertInstanceOf(ExecResult.Done.class, compileAndRun(tick, vars));
        assertEquals(1.0, vars.get(VarScope.GRAPH, "a"));
        assertEquals(2.0, vars.get(VarScope.GRAPH, "b"));
    }

    @Test
    void forAccumuleLesIndex() {
        declareVar("somme");
        UUID tick = add(StandardEvents.SERVER_TICK.id().toString());
        UUID loop = add("flow/for");
        apply(new EditOperation.SetLiteral(loop, "first", LiteralValue.of(PinTypes.INT, 1)));
        apply(new EditOperation.SetLiteral(loop, "last", LiteralValue.of(PinTypes.INT, 3)));
        // corps : somme = somme + index
        UUID get = add("var/get");
        apply(new EditOperation.SetLiteral(get, "var", LiteralValue.of(PinTypes.STRING, "somme")));
        UUID sum = add("math/add");
        UUID set = add("var/set");
        apply(new EditOperation.SetLiteral(set, "var", LiteralValue.of(PinTypes.STRING, "somme")));
        UUID done = varSet("fin", 1);
        declareVar("fin");
        apply(new EditOperation.AddLink(new Link(tick, "exec_out", loop, "exec_in")));
        apply(new EditOperation.AddLink(new Link(loop, "body", set, "exec_in")));
        apply(new EditOperation.AddLink(new Link(loop, "completed", done, "exec_in")));
        apply(new EditOperation.AddLink(new Link(get, "value", sum, "a")));
        apply(new EditOperation.AddLink(new Link(loop, "index", sum, "b")));
        apply(new EditOperation.AddLink(new Link(sum, "result", set, "value")));

        VarStore vars = VarStore.inMemory();
        assertInstanceOf(ExecResult.Done.class, compileAndRun(tick, vars));
        assertEquals(6.0, vars.get(VarScope.GRAPH, "somme")); // 1+2+3
        assertEquals(1.0, vars.get(VarScope.GRAPH, "fin"));   // completed après la boucle
    }

    @Test
    void whileSArreteQuandLaConditionTombe() {
        declareVar("c");
        UUID tick = add(StandardEvents.SERVER_TICK.id().toString());
        UUID loop = add("flow/while");
        UUID get = add("var/get");
        apply(new EditOperation.SetLiteral(get, "var", LiteralValue.of(PinTypes.STRING, "c")));
        UUID less = add("logic/less");
        apply(new EditOperation.SetLiteral(less, "b", LiteralValue.of(PinTypes.DOUBLE, 3.0)));
        UUID get2 = add("var/get");
        apply(new EditOperation.SetLiteral(get2, "var", LiteralValue.of(PinTypes.STRING, "c")));
        UUID sum = add("math/add");
        apply(new EditOperation.SetLiteral(sum, "b", LiteralValue.of(PinTypes.DOUBLE, 1.0)));
        UUID set = add("var/set");
        apply(new EditOperation.SetLiteral(set, "var", LiteralValue.of(PinTypes.STRING, "c")));
        apply(new EditOperation.AddLink(new Link(tick, "exec_out", loop, "exec_in")));
        apply(new EditOperation.AddLink(new Link(get, "value", less, "a")));
        apply(new EditOperation.AddLink(new Link(less, "result", loop, "condition")));
        apply(new EditOperation.AddLink(new Link(loop, "body", set, "exec_in")));
        apply(new EditOperation.AddLink(new Link(get2, "value", sum, "a")));
        apply(new EditOperation.AddLink(new Link(sum, "result", set, "value")));

        VarStore vars = VarStore.inMemory();
        assertInstanceOf(ExecResult.Done.class, compileAndRun(tick, vars));
        assertEquals(3.0, vars.get(VarScope.GRAPH, "c"));
    }

    @Test
    void doOncePersisteEntreLesExecutions() {
        declareVar("n");
        UUID tick = add(StandardEvents.SERVER_TICK.id().toString());
        UUID once = add("flow/do_once");
        UUID get = add("var/get");
        apply(new EditOperation.SetLiteral(get, "var", LiteralValue.of(PinTypes.STRING, "n")));
        UUID sum = add("math/add");
        apply(new EditOperation.SetLiteral(sum, "b", LiteralValue.of(PinTypes.DOUBLE, 1.0)));
        UUID set = add("var/set");
        apply(new EditOperation.SetLiteral(set, "var", LiteralValue.of(PinTypes.STRING, "n")));
        apply(new EditOperation.AddLink(new Link(tick, "exec_out", once, "exec_in")));
        apply(new EditOperation.AddLink(new Link(once, "exec_out", set, "exec_in")));
        apply(new EditOperation.AddLink(new Link(get, "value", sum, "a")));
        apply(new EditOperation.AddLink(new Link(sum, "result", set, "value")));

        Compiler.CompileResult result = Compiler.compile(bp, LOADED.nodes(), tick);
        assertTrue(result.success());
        VarStore vars = VarStore.inMemory();
        // Deux exécutions, MÊME VarStore : la seconde ne doit rien faire.
        BlueprintVm.run(result.ir(), ExecutionState.fresh(result.ir()), env(vars), 10_000);
        BlueprintVm.run(result.ir(), ExecutionState.fresh(result.ir()), env(vars), 10_000);
        assertEquals(1.0, vars.get(VarScope.GRAPH, "n"));
    }

    @Test
    void waitUntilSuspendPuisReprend() {
        declareVar("pret");
        declareVar("fait");
        UUID tick = add(StandardEvents.SERVER_TICK.id().toString());
        UUID wait = add("flow/wait_until");
        UUID get = add("var/get");
        apply(new EditOperation.SetLiteral(get, "var", LiteralValue.of(PinTypes.STRING, "pret")));
        UUID eq = add("logic/greater");
        apply(new EditOperation.SetLiteral(eq, "b", LiteralValue.of(PinTypes.DOUBLE, 0.0)));
        UUID set = varSet("fait", 1);
        apply(new EditOperation.AddLink(new Link(tick, "exec_out", wait, "exec_in")));
        apply(new EditOperation.AddLink(new Link(get, "value", eq, "a")));
        apply(new EditOperation.AddLink(new Link(eq, "result", wait, "condition")));
        apply(new EditOperation.AddLink(new Link(wait, "exec_out", set, "exec_in")));

        Compiler.CompileResult result = Compiler.compile(bp, LOADED.nodes(), tick);
        assertTrue(result.success(), () -> String.valueOf(result.diagnostics()));
        VarStore vars = VarStore.inMemory();
        ExecutionState state = ExecutionState.fresh(result.ir());
        var env = env(vars);
        assertInstanceOf(ExecResult.Suspended.class,
                BlueprintVm.run(result.ir(), state, env, 10_000));
        assertNull(vars.get(VarScope.GRAPH, "fait"));

        vars.set(VarScope.GRAPH, "pret", 1.0);
        assertInstanceOf(ExecResult.Done.class,
                BlueprintVm.run(result.ir(), state, env, 10_000));
        assertEquals(1.0, vars.get(VarScope.GRAPH, "fait"));
    }

    @Test
    void unPurPartageAvantLaBoucleSeReEvalueDansLaCondition() {
        // Régression QA 7.1b (famille VM-COMP-001) : le MÊME var/get alimente un set
        // AVANT la boucle ET la condition du while — mémoïsé hors boucle, la
        // condition ne changerait jamais (OUT_OF_FUEL au lieu de Done).
        declareVar("c");
        declareVar("copie");
        UUID tick = add(StandardEvents.SERVER_TICK.id().toString());
        UUID seq = add("flow/sequence");
        UUID getC = add("var/get");
        apply(new EditOperation.SetLiteral(getC, "var", LiteralValue.of(PinTypes.STRING, "c")));
        // then_1 : copie = get c (émet le pur AVANT la boucle)
        UUID setCopy = add("var/set");
        apply(new EditOperation.SetLiteral(setCopy, "var", LiteralValue.of(PinTypes.STRING, "copie")));
        // then_2 : while (get c < 3) { c = get c + 1 }
        UUID loop = add("flow/while");
        UUID less = add("logic/less");
        apply(new EditOperation.SetLiteral(less, "b", LiteralValue.of(PinTypes.DOUBLE, 3.0)));
        UUID get2 = add("var/get");
        apply(new EditOperation.SetLiteral(get2, "var", LiteralValue.of(PinTypes.STRING, "c")));
        UUID sum = add("math/add");
        apply(new EditOperation.SetLiteral(sum, "b", LiteralValue.of(PinTypes.DOUBLE, 1.0)));
        UUID setC = add("var/set");
        apply(new EditOperation.SetLiteral(setC, "var", LiteralValue.of(PinTypes.STRING, "c")));
        apply(new EditOperation.AddLink(new Link(tick, "exec_out", seq, "exec_in")));
        apply(new EditOperation.AddLink(new Link(seq, "then_1", setCopy, "exec_in")));
        apply(new EditOperation.AddLink(new Link(getC, "value", setCopy, "value")));
        apply(new EditOperation.AddLink(new Link(seq, "then_2", loop, "exec_in")));
        apply(new EditOperation.AddLink(new Link(getC, "value", less, "a"))); // MÊME pur
        apply(new EditOperation.AddLink(new Link(less, "result", loop, "condition")));
        apply(new EditOperation.AddLink(new Link(loop, "body", setC, "exec_in")));
        apply(new EditOperation.AddLink(new Link(get2, "value", sum, "a")));
        apply(new EditOperation.AddLink(new Link(sum, "result", setC, "value")));

        VarStore vars = VarStore.inMemory();
        assertInstanceOf(ExecResult.Done.class, compileAndRun(tick, vars));
        assertEquals(3.0, vars.get(VarScope.GRAPH, "c"));
    }

    @Test
    void switchAiguilleVersLaBonneBranche() {
        declareVar("chemin");
        UUID tick = add(StandardEvents.SERVER_TICK.id().toString());
        UUID sw = add("flow/switch");
        apply(new EditOperation.SetLiteral(sw, "value", LiteralValue.of(PinTypes.INT, 2)));
        UUID two = varSet("chemin", 2);
        UUID other = varSet("chemin", 99);
        apply(new EditOperation.AddLink(new Link(tick, "exec_out", sw, "exec_in")));
        apply(new EditOperation.AddLink(new Link(sw, "case_2", two, "exec_in")));
        apply(new EditOperation.AddLink(new Link(sw, "default", other, "exec_in")));

        VarStore vars = VarStore.inMemory();
        assertInstanceOf(ExecResult.Done.class, compileAndRun(tick, vars));
        assertEquals(2.0, vars.get(VarScope.GRAPH, "chemin"));
    }
}
