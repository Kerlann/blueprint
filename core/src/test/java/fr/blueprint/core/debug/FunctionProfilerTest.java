package fr.blueprint.core.debug;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.compile.Compiler;
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
import fr.blueprint.core.vm.ExecutionEnvironment;
import fr.blueprint.core.vm.ExecutionState;
import fr.blueprint.core.vm.VarOwner;
import fr.blueprint.core.vm.VarStore;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ce que le profileur voit d'une fonction (story 20.1, AC9).
 *
 * <p>Un profileur qui compterait deux fois est pire qu'un profileur absent : il envoie
 * optimiser un nœud qui ne coûte pas ce qu'il annonce. Ces tests fixent ce qu'il rend
 * vraiment, y compris là où la réponse peut surprendre.
 */
class FunctionProfilerTest {

    private static final PluginLoader.LoadedRegistries LOADED = PluginLoader.load(List.of(), true);
    private static final Identifier BP = Identifier.fromNamespaceAndPath("test", "prof");

    private static Identifier type(String path) {
        return Identifier.fromNamespaceAndPath("blueprint", path);
    }

    private static void apply(Blueprint bp, EditOperation op) {
        var result = op.apply(bp, LOADED.nodes());
        assertTrue(result.applied(), () -> "opération refusée : " + result.refusal());
    }

    /** L'UUID du {@code math/mul} du corps — celui qu'on suit à la trace. */
    private UUID corpsMul;

    private Blueprint withFunction() {
        Blueprint bp = new Blueprint(BP);
        GraphLoader.addVariable(bp, new Variable("r", PinTypes.DOUBLE,
                LiteralValue.of(PinTypes.DOUBLE, 0.0), VarScope.GRAPH, false));

        Node param = new Node(UUID.randomUUID(), FuncNodes.PARAM, new Vec2d(0, 0));
        Node mul = new Node(UUID.randomUUID(), type("math/mul"), new Vec2d(0, 0));
        Node exit = new Node(UUID.randomUUID(), FuncNodes.RESULT, new Vec2d(0, 0));
        corpsMul = mul.uuid();
        for (Node n : List.of(param, exit)) {
            GraphLoader.setLiteral(n, FuncNodes.FUNCTION_PIN,
                    LiteralValue.of(PinTypes.STRING, "carre"));
        }
        Map<UUID, Node> nodes = new LinkedHashMap<>();
        for (Node n : List.of(param, mul, exit)) {
            nodes.put(n.uuid(), n);
        }
        Set<Link> links = new LinkedHashSet<>(List.of(
                new Link(param.uuid(), "exec_out", exit.uuid(), "exec_in"),
                new Link(param.uuid(), "n", mul.uuid(), "a"),
                new Link(param.uuid(), "n", mul.uuid(), "b"),
                new Link(mul.uuid(), "result", exit.uuid(), "r")));
        GraphLoader.addFunction(bp, BlueprintFunction.of("carre",
                        List.of(new BlueprintFunction.Param("n", PinTypes.DOUBLE)),
                        List.of(new BlueprintFunction.Param("r", PinTypes.DOUBLE)))
                .withBody(nodes, links));
        return bp;
    }

    /** Deux appels enchaînés, et l'UUID du premier — le point de départ. */
    private UUID deuxAppels(Blueprint bp) {
        UUID first = null;
        UUID previous = null;
        for (int i = 0; i < 2; i++) {
            UUID call = UUID.randomUUID();
            UUID set = UUID.randomUUID();
            apply(bp, new EditOperation.AddNode(call, FuncNodes.CALL, new Vec2d(0, 0)));
            apply(bp, new EditOperation.SetLiteral(call, FuncNodes.FUNCTION_PIN,
                    LiteralValue.of(PinTypes.STRING, "carre")));
            apply(bp, new EditOperation.SetLiteral(call, "n",
                    LiteralValue.of(PinTypes.DOUBLE, 3.0)));
            apply(bp, new EditOperation.AddNode(set, type("var/set"), new Vec2d(0, 0)));
            apply(bp, new EditOperation.SetLiteral(set, "var",
                    LiteralValue.of(PinTypes.STRING, "r")));
            apply(bp, new EditOperation.AddLink(new Link(call, "exec_out", set, "exec_in")));
            apply(bp, new EditOperation.AddLink(new Link(call, "r", set, "value")));
            if (previous != null) {
                apply(bp, new EditOperation.AddLink(new Link(previous, "exec_out", call, "exec_in")));
            }
            if (first == null) {
                first = call;
            }
            previous = set;
        }
        return first;
    }

    private void run(Blueprint bp, UUID start) {
        var compiled = Compiler.compile(bp, LOADED.nodes(), start);
        assertTrue(compiled.success(), () -> "compilation échouée : " + compiled.diagnostics());
        var env = new ExecutionEnvironment(
                id -> LOADED.nodes().get(id).orElse(null),
                new fr.blueprint.api.node.BlueprintHandle() {
                    @Override
                    public Identifier id() {
                        return BP;
                    }

                    @Override
                    public boolean enabled() {
                        return true;
                    }
                },
                new fr.blueprint.api.event.TriggerContext() {
                    @Override
                    public Identifier eventId() {
                        return type("event/server_tick");
                    }

                    @Override
                    public Object output(String name) {
                        return null;
                    }
                },
                VarStore.inMemory(), new VarOwner(BP, null), null, null,
                LoggerFactory.getLogger("blueprint-test"));
        BlueprintVm.run(compiled.ir(), ExecutionState.fresh(compiled.ir()), env, 10_000);
    }

    @AfterEach
    void tearDown() {
        // Le profileur est un état GLOBAL. Le laisser allumé ferait mesurer les tests
        // suivants — et un test qui en abîme un autre est plus coûteux à trouver que
        // celui qu'il cachait.
        Profiler.disableAll();
    }

    /**
     * <b>Le temps du corps est attribué au corps</b>, une entrée par nœud, deux appels.
     *
     * <p>Le dépliage duplique le code, pas l'identité : les deux sites d'appel exécutent
     * des instructions différentes, qui portent le <b>même</b> UUID source. Le profileur
     * voit donc un nœud appelé deux fois, et non deux nœuds appelés une fois — ce qui est
     * ce qu'un auteur veut lire, puisqu'il n'a écrit qu'un nœud.
     */
    @Test
    void leCorpsEstAttribueAuCorps() {
        Blueprint bp = withFunction();
        UUID start = deuxAppels(bp);
        Profiler profiler = Profiler.enable(BP);

        run(bp, start);

        var cout = profiler.top(20).stream()
                .filter(c -> c.node().equals(corpsMul)).toList();

        assertEquals(1, cout.size(),
                "le nœud du corps doit avoir UNE entrée, pas une par site d'appel");
        assertEquals(2, cout.get(0).calls(),
                "deux appels, deux exécutions du même nœud écrit une seule fois");
    }

    /**
     * <b>Le nœud d'appel n'apparaît pas, et c'est la vérité.</b>
     *
     * <p>Un dépliage ne produit aucune instruction pour l'appel lui-même : le corps prend
     * sa place. Lui inventer une ligne dans le rapport ferait compter <b>deux fois</b> le
     * temps du corps — une fois sous l'appel, une fois sous ses nœuds — et le total
     * cesserait d'être un total.
     *
     * <p>C'est la même chose que pour une boucle abaissée, à ceci près qu'une boucle garde
     * l'identifiant de son nœud sur les instructions synthétisées, là où un appel n'en
     * synthétise aucune.
     */
    @Test
    void leNoeudDAppelNApparaitPas() {
        Blueprint bp = withFunction();
        UUID start = deuxAppels(bp);
        Profiler profiler = Profiler.enable(BP);

        run(bp, start);

        assertTrue(profiler.top(20).stream().noneMatch(c -> c.node().equals(start)),
                "un appel qui n'exécute rien lui-même ne doit pas porter de temps : "
                        + "le compter ferait apparaître le corps deux fois dans le total");
    }

    /** Et le total est la somme de ce qui a vraiment tourné, sans rien en double. */
    @Test
    void leTotalNeCompteRienDeuxFois() {
        Blueprint bp = withFunction();
        UUID start = deuxAppels(bp);
        Profiler profiler = Profiler.enable(BP);

        run(bp, start);

        long parNoeud = profiler.top(50).stream().mapToLong(c -> c.calls()).sum();
        assertEquals(parNoeud, profiler.totalCalls(),
                "le total doit être exactement la somme des lignes du rapport");
    }
}
