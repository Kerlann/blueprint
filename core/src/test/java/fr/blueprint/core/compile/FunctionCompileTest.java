package fr.blueprint.core.compile;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.BlueprintFunction;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.FuncNodes;
import fr.blueprint.core.graph.GraphLoader;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.Node;
import fr.blueprint.core.graph.VarScope;
import fr.blueprint.core.graph.Variable;
import fr.blueprint.core.graph.Vec2d;
import fr.blueprint.core.registry.PluginLoader;
import fr.blueprint.core.vm.BlueprintVm;
import fr.blueprint.core.vm.ExecResult;
import fr.blueprint.core.vm.ExecutionEnvironment;
import fr.blueprint.core.vm.ExecutionState;
import fr.blueprint.core.vm.VarOwner;
import fr.blueprint.core.vm.VarStore;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Une fonction s'exécute (story 20.1, AC4).
 *
 * <p>Le test qui compte est {@link #deuxAppelsDeLaMemeFonctionNeSeMarchentPasDessus} : deux
 * appels <b>successifs</b> passeraient sur presque n'importe quelle implémentation. Ce sont
 * deux appels qui doivent rendre deux résultats différents, dans la même exécution, qui
 * décident — sans quoi la mémoïsation des purs ou le partage de slots relirait le premier.
 */
class FunctionCompileTest {

    private static final PluginLoader.LoadedRegistries LOADED = PluginLoader.load(List.of(), true);
    private static final Identifier BP = Identifier.fromNamespaceAndPath("test", "fn");

    private static final fr.blueprint.api.node.BlueprintHandle HANDLE =
            new fr.blueprint.api.node.BlueprintHandle() {
                @Override
                public Identifier id() {
                    return BP;
                }

                @Override
                public boolean enabled() {
                    return true;
                }
            };

    private static final fr.blueprint.api.event.TriggerContext TRIGGER =
            new fr.blueprint.api.event.TriggerContext() {
                @Override
                public Identifier eventId() {
                    return Identifier.fromNamespaceAndPath("blueprint", "event/server_tick");
                }

                @Override
                public Object output(String name) {
                    throw new IllegalArgumentException(name);
                }
            };

    private static Node node(String seed, String path) {
        return new Node(UUID.nameUUIDFromBytes(seed.getBytes()),
                Identifier.fromNamespaceAndPath("blueprint", path), new Vec2d(0, 0));
    }

    /**
     * Une fonction {@code doubler(n) -> (resultat)} dont le corps est {@code n + n}.
     *
     * <p>Une addition et non une constante : une fonction qui rendrait toujours la même
     * chose ne prouverait rien sur le passage des arguments.
     */
    private static BlueprintFunction doubler() {
        Node param = node("p", "func/param");
        Node add = node("add", "math/add");
        Node result = node("r", "func/result");
        for (Node n : List.of(param, result)) {
            GraphLoader.setLiteral(n, FuncNodes.FUNCTION_PIN,
                    LiteralValue.of(PinTypes.STRING, "doubler"));
        }
        Map<UUID, Node> nodes = new LinkedHashMap<>();
        for (Node n : List.of(param, add, result)) {
            nodes.put(n.uuid(), n);
        }
        Set<Link> links = new LinkedHashSet<>(List.of(
                new Link(param.uuid(), "exec_out", result.uuid(), "exec_in"),
                new Link(param.uuid(), "n", add.uuid(), "a"),
                new Link(param.uuid(), "n", add.uuid(), "b"),
                new Link(add.uuid(), "result", result.uuid(), "resultat")));
        return BlueprintFunction.of("doubler",
                        List.of(new BlueprintFunction.Param("n", PinTypes.DOUBLE)),
                        List.of(new BlueprintFunction.Param("resultat", PinTypes.DOUBLE)))
                .withBody(nodes, links);
    }

    private static ExecutionEnvironment env(VarStore vars) {
        return new ExecutionEnvironment(
                typeId -> LOADED.nodes().get(typeId).orElse(null), HANDLE, TRIGGER, vars,
                new VarOwner(BP, null), null, null, LoggerFactory.getLogger("blueprint-test"));
    }

    private static void apply(Blueprint bp, EditOperation op) {
        var result = op.apply(bp, LOADED.nodes());
        assertTrue(result.applied(), () -> "opération refusée : " + result.refusal());
    }

    /**
     * <b>Le test qui décide.</b> {@code doubler(3)} puis {@code doubler(10)} dans la même
     * exécution, chacun écrivant sa propre variable.
     *
     * <p>Un dépliage partagé — ou une mémoïsation des purs qui ne distingue pas les deux
     * sites — rendrait 6 deux fois.
     */
    @Test
    void deuxAppelsDeLaMemeFonctionNeSeMarchentPasDessus() {
        Blueprint bp = new Blueprint(BP);
        GraphLoader.addFunction(bp, doubler());
        for (String name : List.of("six", "vingt")) {
            GraphLoader.addVariable(bp, new Variable(name, PinTypes.DOUBLE,
                    LiteralValue.of(PinTypes.DOUBLE, 0.0), VarScope.GRAPH, false));
        }

        UUID start = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID setA = UUID.randomUUID();
        UUID setB = UUID.randomUUID();
        apply(bp, new EditOperation.AddNode(start, FuncNodes.CALL, new Vec2d(0, 0)));
        apply(bp, new EditOperation.AddNode(second, FuncNodes.CALL, new Vec2d(0, 0)));
        apply(bp, new EditOperation.AddNode(setA, VarNodesId("var/set"), new Vec2d(0, 0)));
        apply(bp, new EditOperation.AddNode(setB, VarNodesId("var/set"), new Vec2d(0, 0)));
        for (UUID call : List.of(start, second)) {
            apply(bp, new EditOperation.SetLiteral(call, FuncNodes.FUNCTION_PIN,
                    LiteralValue.of(PinTypes.STRING, "doubler")));
        }
        apply(bp, new EditOperation.SetLiteral(start, "n", LiteralValue.of(PinTypes.DOUBLE, 3.0)));
        apply(bp, new EditOperation.SetLiteral(second, "n", LiteralValue.of(PinTypes.DOUBLE, 10.0)));
        apply(bp, new EditOperation.SetLiteral(setA, "var", LiteralValue.of(PinTypes.STRING, "six")));
        apply(bp, new EditOperation.SetLiteral(setB, "var", LiteralValue.of(PinTypes.STRING, "vingt")));
        // LES DEUX APPELS D'ABORD, les deux écritures ensuite. L'ordre n'est pas
        // cosmétique : écrire « six » entre les deux appels le capturerait AVANT que le
        // second n'écrase le slot partagé, et le test passerait sur l'implémentation même
        // qu'il prétend refuser. Il l'a d'abord fait.
        apply(bp, new EditOperation.AddLink(new Link(start, "exec_out", second, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(second, "exec_out", setA, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(setA, "exec_out", setB, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(start, "resultat", setA, "value")));
        apply(bp, new EditOperation.AddLink(new Link(second, "resultat", setB, "value")));

        var compiled = Compiler.compile(bp, LOADED.nodes(), start);
        assertTrue(compiled.success(), () -> "compilation échouée : " + compiled.diagnostics());

        VarStore vars = VarStore.inMemory();
        var env = env(vars);
        assertInstanceOf(ExecResult.Done.class, BlueprintVm.run(compiled.ir(),
                ExecutionState.fresh(compiled.ir()), env, 10_000));

        assertEquals(6.0, vars.get(VarScope.GRAPH, new VarOwner(BP, null), "six"),
                "le premier résultat a été écrasé par le second — les deux dépliages "
                        + "partagent leurs slots");
        assertEquals(20.0, vars.get(VarScope.GRAPH, new VarOwner(BP, null), "vingt"));
    }

    private static Identifier VarNodesId(String path) {
        return Identifier.fromNamespaceAndPath("blueprint", path);
    }
}
