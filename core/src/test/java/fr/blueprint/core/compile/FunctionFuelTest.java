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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Une fonction n'est pas une façon d'échapper au budget</b> (story 20.1, AC5).
 *
 * <p>La question n'est pas rhétorique. Un mécanisme d'appel qui coûterait moins que le code
 * qu'il exécute serait une porte dérobée : il suffirait de ranger une boucle coûteuse dans
 * une fonction pour la faire passer sous le plafond de carburant, et le serveur qu'un
 * graphe glouton est censé ne pas faire ramer ramerait quand même.
 *
 * <p>Le banc compare donc <b>un appel et deux</b>, et non un chiffre absolu contre un
 * seuil : deux mesures d'une même exécution, comme le veut la doctrine du §7.1. Le
 * deuxième appel doit payer son corps aussi cher que le premier, sans quoi une fonction
 * deviendrait meilleur marché à mesure qu'on l'appelle.
 */
class FunctionFuelTest {

    private static final PluginLoader.LoadedRegistries LOADED = PluginLoader.load(List.of(), true);

    private static Identifier bpId(String path) {
        return Identifier.fromNamespaceAndPath("test", path);
    }

    private static Identifier type(String path) {
        return Identifier.fromNamespaceAndPath("blueprint", path);
    }

    private static void apply(Blueprint bp, EditOperation op) {
        var result = op.apply(bp, LOADED.nodes());
        assertTrue(result.applied(), () -> "opération refusée : " + result.refusal());
    }

    private static void declareResult(Blueprint bp) {
        GraphLoader.addVariable(bp, new Variable("r", PinTypes.DOUBLE,
                LiteralValue.of(PinTypes.DOUBLE, 0.0), VarScope.GRAPH, false));
    }

    /** La MÊME multiplication, rangée dans une fonction et appelée une fois. */
    private static Blueprint parFonction() {
        Blueprint bp = new Blueprint(bpId("func"));
        declareResult(bp);

        Node param = new Node(UUID.randomUUID(), FuncNodes.PARAM, new Vec2d(0, 0));
        Node mul = new Node(UUID.randomUUID(), type("math/mul"), new Vec2d(0, 0));
        Node exit = new Node(UUID.randomUUID(), FuncNodes.RESULT, new Vec2d(0, 0));
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

    /** Ajoute un appel de {@code carre(3)} qui écrit dans {@code r}, et le rend. */
    private static UUID appel(Blueprint bp, UUID after) {
        UUID call = UUID.randomUUID();
        UUID set = UUID.randomUUID();
        apply(bp, new EditOperation.AddNode(call, FuncNodes.CALL, new Vec2d(0, 0)));
        apply(bp, new EditOperation.SetLiteral(call, FuncNodes.FUNCTION_PIN,
                LiteralValue.of(PinTypes.STRING, "carre")));
        apply(bp, new EditOperation.SetLiteral(call, "n", LiteralValue.of(PinTypes.DOUBLE, 3.0)));
        apply(bp, new EditOperation.AddNode(set, type("var/set"), new Vec2d(0, 0)));
        apply(bp, new EditOperation.SetLiteral(set, "var", LiteralValue.of(PinTypes.STRING, "r")));
        apply(bp, new EditOperation.AddLink(new Link(call, "exec_out", set, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(call, "r", set, "value")));
        if (after != null) {
            apply(bp, new EditOperation.AddLink(new Link(after, "exec_out", call, "exec_in")));
        }
        return set;
    }

    /** Le carburant d'un graphe qui appelle {@code carre} le nombre de fois demandé. */
    private static int fuelOf(Blueprint bp, int appels) {
        UUID first = null;
        UUID last = null;
        for (int i = 0; i < appels; i++) {
            UUID before = last;
            last = appel(bp, before);
            if (first == null) {
                first = bp.linksInto(last, "exec_in").get(0).fromNode();
            }
        }
        return fuelOf(bp, first);
    }

    /** Compile, exécute, et rend le carburant réellement consommé. */
    private static int fuelOf(Blueprint bp, UUID start) {
        var compiled = Compiler.compile(bp, LOADED.nodes(), start);
        assertTrue(compiled.success(), () -> "compilation échouée : " + compiled.diagnostics());
        VarStore vars = VarStore.inMemory();
        var owner = new VarOwner(bp.id(), null);
        var env = new ExecutionEnvironment(
                id -> LOADED.nodes().get(id).orElse(null),
                new fr.blueprint.api.node.BlueprintHandle() {
                    @Override
                    public Identifier id() {
                        return bp.id();
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
                vars, owner, null, null, LoggerFactory.getLogger("blueprint-test"));

        var outcome = BlueprintVm.runMeasured(compiled.ir(),
                ExecutionState.fresh(compiled.ir()), env, 10_000);
        assertEquals(9.0, vars.get(VarScope.GRAPH, owner, "r"),
                "le graphe doit avoir calculé 3 × 3 : une mesure de carburant sur une "
                        + "exécution qui n'a pas tourné ne mesurerait rien");
        return outcome.fuelSpent();
    }

    private static UUID firstOf(Blueprint bp, Identifier typeId) {
        return bp.nodes().values().stream()
                .filter(n -> n.typeId().equals(typeId)).findFirst().orElseThrow().uuid();
    }

    /**
     * <b>Le banc.</b> Chaque appel paie son corps — le deuxième autant que le premier.
     *
     * <h2>Ce que ce test ne compare pas, et pourquoi</h2>
     *
     * <p>La première version comparait le même calcul écrit en ligne et rangé dans une
     * fonction, et attendait l'égalité. Elle a mesuré 5 contre 4, et l'écart n'était pas
     * un défaut : la version en ligne porte <b>deux littéraux</b> — {@code a: 3} et
     * {@code b: 3} — là où la fonction n'en a qu'un, son paramètre, servi aux deux entrées.
     * Deux graphes différents, donc une comparaison qui ne comparait rien.
     *
     * <p>La question qui compte n'est pas « autant qu'en ligne » mais « <b>autant que ce
     * qui tourne</b> ». Un mécanisme d'appel qui partagerait son corps entre les sites
     * ferait payer le second moins que le premier, et il suffirait alors d'appeler en
     * boucle pour passer sous le plafond.
     *
     * <p>Le seuil vient du <b>registre</b>, pas d'un nombre écrit ici : retarifer
     * {@code math/mul} ne doit pas demander de revenir dans ce test.
     */
    @Test
    void chaqueAppelPaieSonCorps() {
        int unAppel = fuelOf(parFonction(), 1);
        int deuxAppels = fuelOf(parFonction(), 2);

        int cout = LOADED.nodes().get(type("math/mul")).orElseThrow().fuelCost();
        assertTrue(deuxAppels - unAppel >= cout,
                "le second appel n'a pas payé sa multiplication (" + unAppel + " → "
                        + deuxAppels + ", attendu au moins +" + cout + ") : les deux sites "
                        + "partagent un corps, ce qui rend une fonction moins chère à "
                        + "mesure qu'on l'appelle — une porte dérobée dans le budget");
    }

    /**
     * Et le budget coupe un corps glouton comme il couperait le même code en ligne.
     *
     * <p>Un carburant trop court doit rendre {@code OUT_OF_FUEL}, pas un résultat partiel
     * qui laisserait croire que l'appel a réussi.
     */
    @Test
    void unBudgetTropCourtCoupeAussiDansUnCorps() {
        Blueprint bp = parFonction();
        appel(bp, null);
        var compiled = Compiler.compile(bp, LOADED.nodes(), firstOf(bp, FuncNodes.CALL));
        assertTrue(compiled.success());

        var env = new ExecutionEnvironment(
                id -> LOADED.nodes().get(id).orElse(null),
                new fr.blueprint.api.node.BlueprintHandle() {
                    @Override
                    public Identifier id() {
                        return bp.id();
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
                VarStore.inMemory(), new VarOwner(bp.id(), null), null, null,
                LoggerFactory.getLogger("blueprint-test"));

        var result = BlueprintVm.run(compiled.ir(),
                ExecutionState.fresh(compiled.ir()), env, 1);

        assertEquals(fr.blueprint.core.vm.ExecResult.OUT_OF_FUEL, result,
                "un corps de fonction n'est pas hors budget");
    }
}
