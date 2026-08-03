package fr.blueprint.core.compile;

import fr.blueprint.api.event.TriggerContext;
import fr.blueprint.api.node.BlueprintHandle;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.compile.ir.Instruction;
import fr.blueprint.core.event.StandardEvents;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.GraphValidator;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story 5.5 : var/get et var/set sont abaissés en LoadVar/StoreVar — le graphe
 * complet (événement → set → get → set) s'exécute headless et le VarStore porte
 * les valeurs. Le validateur refuse un nœud de variable non lié.
 */
class CompilerVarNodesTest {

    private static final PluginLoader.LoadedRegistries LOADED =
            PluginLoader.load(List.of(), true);

    private static final BlueprintHandle HANDLE = new BlueprintHandle() {
        @Override
        public Identifier id() {
            return Identifier.fromNamespaceAndPath("test", "vars");
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

    private static ExecutionEnvironment env() {
        return new ExecutionEnvironment(
                id -> LOADED.nodes().get(id).orElse(null),
                HANDLE, TRIGGER, VarStore.inMemory(), null, null,
                LoggerFactory.getLogger("blueprint-test"));
    }

    private static UUID add(Blueprint bp, Identifier typeId, double x) {
        UUID id = UUID.randomUUID();
        assertTrue(new EditOperation.AddNode(id, typeId, new Vec2d(x, 0))
                .apply(bp, LOADED.nodes()).applied());
        return id;
    }

    @Test
    void setPuisGetTraversentLeVarStore() {
        Blueprint bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "vars"));
        assertTrue(new EditOperation.AddVariable(new Variable("score", PinTypes.DOUBLE,
                LiteralValue.of(PinTypes.DOUBLE, 0.0), VarScope.GRAPH, false))
                .apply(bp, LOADED.nodes()).applied());
        assertTrue(new EditOperation.AddVariable(new Variable("copie", PinTypes.DOUBLE,
                null, VarScope.WORLD, false))
                .apply(bp, LOADED.nodes()).applied());

        UUID tick = add(bp, StandardEvents.SERVER_TICK.id(), -300);
        UUID set = add(bp, VarNodes.SET, 0);
        UUID get = add(bp, VarNodes.GET, 150);
        UUID copy = add(bp, VarNodes.SET, 300);

        apply(bp, new EditOperation.SetLiteral(set, "var", LiteralValue.of(PinTypes.STRING, "score")));
        apply(bp, new EditOperation.SetLiteral(set, "value", LiteralValue.of(PinTypes.DOUBLE, 2.5)));
        apply(bp, new EditOperation.SetLiteral(get, "var", LiteralValue.of(PinTypes.STRING, "score")));
        apply(bp, new EditOperation.SetLiteral(copy, "var", LiteralValue.of(PinTypes.STRING, "copie")));
        apply(bp, new EditOperation.AddLink(new Link(tick, "exec_out", set, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(set, "exec_out", copy, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(get, "value", copy, "value")));

        Compiler.CompileResult result = Compiler.compile(bp, LOADED.nodes(), tick);
        assertTrue(result.success(), () -> "diagnostics : " + result.diagnostics());
        // Le lowering a bien eu lieu : aucune instruction Call ne vise var/get ou var/set.
        for (Instruction instruction : result.ir().instructions()) {
            if (instruction instanceof Instruction.Call call) {
                assertFalse(VarNodes.isVarNode(call.type()),
                        "un nœud de variable ne doit jamais devenir un Call");
            }
        }

        var environment = env();
        assertInstanceOf(ExecResult.Done.class, BlueprintVm.run(
                result.ir(), ExecutionState.fresh(result.ir()), environment, 1_000));
        assertEquals(2.5, environment.vars().get(VarScope.GRAPH, "score"));
        assertEquals(2.5, environment.vars().get(VarScope.WORLD, "copie"));
    }

    @Test
    void leValidateurRefuseUnNoeudDeVariableNonLie() {
        Blueprint bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "unbound"));
        UUID get = add(bp, VarNodes.GET, 0);
        // Aucun littéral « var » : erreur ciblée sur le nœud.
        GraphValidator.ValidationResult result = GraphValidator.validate(bp, LOADED.nodes());
        assertTrue(result.diagnostics().stream().anyMatch(d ->
                d.code() == fr.blueprint.core.graph.DiagnosticCode.VARIABLE_NOT_FOUND));

        // Nom posé mais variable inexistante : refus aussi.
        apply(bp, new EditOperation.SetLiteral(get, "var", LiteralValue.of(PinTypes.STRING, "absente")));
        assertTrue(GraphValidator.validate(bp, LOADED.nodes()).diagnostics().stream()
                .anyMatch(d -> d.code() == fr.blueprint.core.graph.DiagnosticCode.VARIABLE_NOT_FOUND));

        // Variable déclarée : plus d'erreur de liaison.
        apply(bp, new EditOperation.AddVariable(new Variable("absente", PinTypes.DOUBLE,
                null, VarScope.GRAPH, false)));
        assertFalse(GraphValidator.validate(bp, LOADED.nodes()).diagnostics().stream()
                .anyMatch(d -> d.code() == fr.blueprint.core.graph.DiagnosticCode.VARIABLE_NOT_FOUND));
    }

    private static void apply(Blueprint bp, EditOperation op) {
        assertTrue(op.apply(bp, LOADED.nodes()).applied(), op::toString);
    }
}
