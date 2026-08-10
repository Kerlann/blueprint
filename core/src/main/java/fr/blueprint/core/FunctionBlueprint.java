package fr.blueprint.core;

import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.event.StandardEvents;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.BlueprintFunction;
import fr.blueprint.core.graph.BlueprintMeta;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.FuncNodes;
import fr.blueprint.core.graph.GraphLoader;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.Node;
import fr.blueprint.core.graph.NodeTypeLookup;
import fr.blueprint.core.graph.Vec2d;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * <b>Les fonctions</b> : définir une fois, appeler partout (story 20.1).
 *
 * <p>Lancement en jeu : <code>/blueprint fonctions</code> puis <code>/fonctions</code>.
 *
 * <p>Deux fonctions, dont l'une appelle l'autre :
 *
 * <ul>
 *   <li><code>carre(n) → (r)</code> — une multiplication ;</li>
 *   <li><code>hypotenuse_carree(a, b) → (d)</code> — <b>deux appels</b> à {@code carre},
 *       puis une addition.</li>
 * </ul>
 *
 * <p>Le graphe principal appelle {@code hypotenuse_carree(3, 4)} et annonce 25.
 *
 * <h2>Ce que cet exemple prouve, et qu'un seul appel ne prouverait pas</h2>
 *
 * <p><b>Deux appels de la même fonction ne se marchent pas dessus.</b> {@code carre(3)} et
 * {@code carre(4)} vivent dans la même exécution, et leurs résultats sont additionnés
 * <b>après</b> que les deux ont tourné. Un corps partagé entre les deux sites — ou une
 * mémoïsation qui ne les distingue pas — rendrait 18 ou 32, jamais 25.
 *
 * <p>C'est pourquoi le corps de {@code hypotenuse_carree} appelle {@code carre} plutôt que
 * de multiplier deux fois : un exemple qui se contenterait de deux multiplications
 * n'exercerait pas les appels imbriqués, et c'est là que tout se joue.
 */
public final class FunctionBlueprint {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("blueprint", "fonctions");

    /** Nom à taper : {@code /fonctions}. */
    public static final String COMMAND = "fonctions";

    private FunctionBlueprint() {
    }

    public static Blueprint build(NodeTypeLookup lookup) {
        Blueprint bp = new Blueprint(ID, new BlueprintMeta(
                "Blueprint", "Les fonctions : definir une fois, appeler partout",
                "1.0.0", Permission.GAMEPLAY));

        // Le résultat est aussi rangé dans une variable, et pas seulement annoncé au
        // joueur : c'est ce qui rend cet exemple VÉRIFIABLE sans partie. Un message dans
        // le chat ne se teste qu'avec un vrai joueur ; une variable se lit dans un test
        // headless, et le blueprint livré devient sa propre preuve.
        GraphLoader.addVariable(bp, new fr.blueprint.core.graph.Variable("resultat",
                PinTypes.DOUBLE, LiteralValue.of(PinTypes.DOUBLE, 0.0),
                fr.blueprint.core.graph.VarScope.GRAPH, false));

        GraphLoader.addFunction(bp, carre());
        GraphLoader.addFunction(bp, hypotenuseCarree());
        principal(bp, lookup);
        return bp;
    }

    // ------------------------------------------------------------------ les corps

    /** {@code carre(n) → (r)} : une multiplication, et rien d'autre. */
    private static BlueprintFunction carre() {
        Node param = node("carre-p", FuncNodes.PARAM, -300, 0);
        Node mul = node("carre-mul", type("math/mul"), -60, 120);
        Node result = node("carre-r", FuncNodes.RESULT, 180, 0);
        nameThem("carre", param, result);

        return BlueprintFunction.of("carre",
                        List.of(new BlueprintFunction.Param("n", PinTypes.DOUBLE)),
                        List.of(new BlueprintFunction.Param("r", PinTypes.DOUBLE)))
                .withBody(bodyOf(param, mul, result), links(
                        new Link(param.uuid(), "exec_out", result.uuid(), "exec_in"),
                        // Le MÊME paramètre sur les deux entrées : n × n.
                        new Link(param.uuid(), "n", mul.uuid(), "a"),
                        new Link(param.uuid(), "n", mul.uuid(), "b"),
                        new Link(mul.uuid(), "result", result.uuid(), "r")));
    }

    /**
     * {@code hypotenuse_carree(a, b) → (d)} : {@code carre(a) + carre(b)}.
     *
     * <p>Deux appels de la même fonction, dont les résultats se rencontrent après coup.
     * C'est le cas qui distingue un vrai appel d'un raccourci.
     */
    private static BlueprintFunction hypotenuseCarree() {
        Node param = node("hyp-p", FuncNodes.PARAM, -600, 0);
        Node premier = node("hyp-c1", FuncNodes.CALL, -340, 0);
        Node second = node("hyp-c2", FuncNodes.CALL, -80, 0);
        Node somme = node("hyp-add", type("math/add"), 180, 140);
        Node result = node("hyp-r", FuncNodes.RESULT, 180, 0);
        nameThem("hypotenuse_carree", param, result);
        nameThem("carre", premier, second);

        return BlueprintFunction.of("hypotenuse_carree",
                        List.of(new BlueprintFunction.Param("a", PinTypes.DOUBLE),
                                new BlueprintFunction.Param("b", PinTypes.DOUBLE)),
                        List.of(new BlueprintFunction.Param("d", PinTypes.DOUBLE)))
                .withBody(bodyOf(param, premier, second, somme, result), links(
                        new Link(param.uuid(), "exec_out", premier.uuid(), "exec_in"),
                        new Link(premier.uuid(), "exec_out", second.uuid(), "exec_in"),
                        new Link(second.uuid(), "exec_out", result.uuid(), "exec_in"),
                        new Link(param.uuid(), "a", premier.uuid(), "n"),
                        new Link(param.uuid(), "b", second.uuid(), "n"),
                        // Les DEUX résultats se rencontrent ici, après que les deux appels
                        // ont tourné : c'est ce que ce blueprint est là pour vérifier.
                        new Link(premier.uuid(), "r", somme.uuid(), "a"),
                        new Link(second.uuid(), "r", somme.uuid(), "b"),
                        new Link(somme.uuid(), "result", result.uuid(), "d")));
    }

    // --------------------------------------------------------------- le graphe

    private static void principal(Blueprint bp, NodeTypeLookup lookup) {
        UUID commande = add(bp, lookup, "cmd", StandardEvents.COMMAND.id(), -600, 0);
        literal(bp, lookup, commande, "name", LiteralValue.of(PinTypes.STRING, COMMAND));

        UUID appel = add(bp, lookup, "appel", FuncNodes.CALL, -300, 0);
        literal(bp, lookup, appel, FuncNodes.FUNCTION_PIN,
                LiteralValue.of(PinTypes.STRING, "hypotenuse_carree"));
        literal(bp, lookup, appel, "a", LiteralValue.of(PinTypes.DOUBLE, 3.0));
        literal(bp, lookup, appel, "b", LiteralValue.of(PinTypes.DOUBLE, 4.0));
        link(bp, lookup, commande, "exec_out", appel, "exec_in");

        UUID garde = add(bp, lookup, "garde", type("var/set"), -60, 0);
        literal(bp, lookup, garde, "var", LiteralValue.of(PinTypes.STRING, "resultat"));
        link(bp, lookup, appel, "exec_out", garde, "exec_in");
        link(bp, lookup, appel, "d", garde, "value");

        UUID texte = add(bp, lookup, "texte", type("convert/to_string"), -300, 200);
        link(bp, lookup, appel, "d", texte, "value");

        UUID prefixe = add(bp, lookup, "prefixe", type("string/concat"), -60, 200);
        literal(bp, lookup, prefixe, "a", LiteralValue.of(PinTypes.STRING, "3x3 + 4x4 = "));
        link(bp, lookup, texte, "result", prefixe, "b");

        UUID message = add(bp, lookup, "message", type("player/send_message"), 240, 0);
        link(bp, lookup, garde, "exec_out", message, "exec_in");
        link(bp, lookup, commande, "player", message, "player");
        link(bp, lookup, prefixe, "result", message, "text");
    }

    // ------------------------------------------------------------------ outillage

    private static Identifier type(String path) {
        return Identifier.fromNamespaceAndPath("blueprint", path);
    }

    private static Node node(String seed, Identifier typeId, double x, double y) {
        return new Node(uuidOf(seed), typeId, new Vec2d(x, y));
    }

    private static UUID uuidOf(String seed) {
        return UUID.nameUUIDFromBytes(("fn-" + seed)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Les nœuds de fonction portent le nom de leur fonction en littéral. */
    private static void nameThem(String function, Node... nodes) {
        for (Node node : nodes) {
            GraphLoader.setLiteral(node, FuncNodes.FUNCTION_PIN,
                    LiteralValue.of(PinTypes.STRING, function));
        }
    }

    private static Map<UUID, Node> bodyOf(Node... nodes) {
        Map<UUID, Node> map = new LinkedHashMap<>();
        for (Node node : nodes) {
            map.put(node.uuid(), node);
        }
        return map;
    }

    private static Set<Link> links(Link... links) {
        return new LinkedHashSet<>(List.of(links));
    }

    private static UUID add(Blueprint bp, NodeTypeLookup lookup, String seed,
                            Identifier typeId, double x, double y) {
        UUID uuid = uuidOf(seed);
        apply(bp, lookup, new EditOperation.AddNode(uuid, typeId, new Vec2d(x, y)));
        return uuid;
    }

    private static void literal(Blueprint bp, NodeTypeLookup lookup, UUID target,
                                String pin, LiteralValue value) {
        apply(bp, lookup, new EditOperation.SetLiteral(target, pin, value));
    }

    private static void link(Blueprint bp, NodeTypeLookup lookup, UUID from, String fromPin,
                             UUID to, String toPin) {
        apply(bp, lookup, new EditOperation.AddLink(new Link(from, fromPin, to, toPin)));
    }

    private static void apply(Blueprint bp, NodeTypeLookup lookup, EditOperation op) {
        EditOperation.Result result = op.apply(bp, lookup);
        if (!result.applied()) {
            throw new IllegalStateException("Blueprint des fonctions incoherent : "
                    + result.refusal());
        }
    }
}
