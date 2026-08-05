package fr.blueprint.core;

import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.event.StandardEvents;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.BlueprintMeta;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.NodeTypeLookup;
import fr.blueprint.core.graph.VarScope;
import fr.blueprint.core.graph.Variable;
import fr.blueprint.core.graph.Vec2d;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/**
 * <b>Le banc de performance jouable</b> — le seul blueprint livré avec le mod.
 *
 * <p>Il ne cherche pas à enseigner : il cherche à <b>faire travailler la VM</b>, et à le
 * faire là où les bancs headless ne vont pas — dans une partie, sur un serveur, avec un
 * joueur qui tape une commande et lit le résultat.
 *
 * <p>Lancement en jeu : <code>/bpc bench</code>. Le graphe répond par un message donnant
 * ce qu'il a calculé, le fuel dépensé étant lisible juste après par
 * <code>/blueprint profile show</code>.
 *
 * <h2>Ce qu'il met sous tension</h2>
 *
 * <p>Les <b>trois formes de boucle</b> du langage, parce que chacune s'abaisse en un jeu
 * d'instructions différent et qu'aucune n'exerce les mêmes chemins de la VM :
 *
 * <ul>
 *   <li><b>{@code flow/for}</b> — bornes fixes. Compteur, comparaison et saut arrière :
 *       c'est le cas où le budget de fuel se consomme le plus régulièrement.</li>
 *   <li><b>{@code flow/while}</b> — condition recalculée à chaque tour. La condition est
 *       une chaîne de nœuds <i>purs</i> ré-évaluée à chaque itération : c'est ce qui
 *       distingue une boucle d'un déroulé, et ce que la mémoïsation des purs ne doit
 *       surtout pas court-circuiter.</li>
 *   <li><b>{@code flow/for_each}</b> — parcours d'une liste. Exerce les nœuds de
 *       collection et le passage d'un élément par tour.</li>
 * </ul>
 *
 * <p>Autour d'elles, ce qui coûte réellement dans un graphe réel : des <b>variables</b>
 * lues et écrites à chaque tour ({@code LoadVar}/{@code StoreVar}), de l'arithmétique, de
 * la concaténation bornée, et une conversion finale en texte.
 *
 * <h2>Pourquoi une commande et non le tick</h2>
 *
 * <p>Un banc branché sur {@code server_tick} tournerait en permanence et fausserait toute
 * autre mesure prise pendant ce temps — sans compter qu'il consommerait du budget chez
 * quelqu'un qui ne l'a pas demandé. Ici, rien ne tourne tant que personne ne tape la
 * commande.
 *
 * <p>Les bornes sont volontairement modestes ({@link #ITERATIONS}) : le but est de
 * mesurer, pas de déclencher la police du dépassement de budget. Pour éprouver
 * <i>celle-là</i>, il suffit de monter le littéral {@code last} du nœud {@code flow/for}
 * dans l'éditeur — c'est d'ailleurs une bonne façon de voir le mod se protéger.
 */
public final class BenchBlueprint {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("blueprint", "bench");

    /** Nom à taper : {@code /bpc bench}. */
    public static final String COMMAND = "bench";

    /**
     * Tours de la boucle {@code for}. Assez pour que la mesure sorte du bruit, assez peu
     * pour rester très en deçà du budget d'un tick — un banc qui se fait couper par la
     * police ne mesure plus rien.
     */
    public static final int ITERATIONS = 200;

    private BenchBlueprint() {
    }

    public static Blueprint build(NodeTypeLookup lookup) {
        Blueprint bp = new Blueprint(ID, new BlueprintMeta(
                "Blueprint",
                "Banc de performance : trois formes de boucle, variables et collections",
                "1.0.0", Permission.GAMEPLAY));

        declare(bp, lookup, "somme", PinTypes.DOUBLE, LiteralValue.of(PinTypes.DOUBLE, 0.0));
        declare(bp, lookup, "reste", PinTypes.DOUBLE, LiteralValue.of(PinTypes.DOUBLE, 0.0));
        declare(bp, lookup, "texte", PinTypes.STRING, LiteralValue.of(PinTypes.STRING, ""));

        // ---------------------------------------------------------------- départ
        UUID command = add(bp, lookup, "command", StandardEvents.COMMAND.id(), -700, 0);
        literal(bp, lookup, command, "name", LiteralValue.of(PinTypes.STRING, COMMAND));

        // Remise à zéro : un banc doit partir du même état à chaque lancement, sinon la
        // deuxième mesure part de ce qu'a laissé la première.
        UUID resetSum = setVar(bp, lookup, "resetSum", "somme", -450, -120);
        UUID zero = add(bp, lookup, "zero", node("math/add"), -700, -200);
        literal(bp, lookup, zero, "a", LiteralValue.of(PinTypes.DOUBLE, 0.0));
        literal(bp, lookup, zero, "b", LiteralValue.of(PinTypes.DOUBLE, 0.0));
        link(bp, lookup, zero, "result", resetSum, "value");
        link(bp, lookup, command, "exec_out", resetSum, "exec_in");

        UUID resetRest = setVar(bp, lookup, "resetRest", "reste", -250, -120);
        UUID iterations = add(bp, lookup, "iterations", node("math/add"), -450, -220);
        literal(bp, lookup, iterations, "a", LiteralValue.of(PinTypes.DOUBLE, (double) ITERATIONS));
        literal(bp, lookup, iterations, "b", LiteralValue.of(PinTypes.DOUBLE, 0.0));
        link(bp, lookup, iterations, "result", resetRest, "value");
        link(bp, lookup, resetSum, "exec_out", resetRest, "exec_in");

        // « texte » AUSSI, et l'oubli s'est vu du premier coup en jeu : la liste sortait
        // « alphabetagammaalphabetagamma » au deuxième lancement, puis trois fois au
        // troisième. Une variable de portée GRAPH survit à l'exécution — c'est tout
        // l'intérêt d'une variable, et c'est ce qui rend la remise à zéro obligatoire
        // pour un banc. Deux des trois étaient remises à zéro ; celle-là ne l'était pas.
        UUID resetText = setVar(bp, lookup, "resetText", "texte", -50, -120);
        UUID emptyText = add(bp, lookup, "emptyText", node("string/concat"), -250, -220);
        literal(bp, lookup, emptyText, "a", LiteralValue.of(PinTypes.STRING, ""));
        literal(bp, lookup, emptyText, "b", LiteralValue.of(PinTypes.STRING, ""));
        link(bp, lookup, emptyText, "result", resetText, "value");
        link(bp, lookup, resetRest, "exec_out", resetText, "exec_in");

        // -------------------------------------------------- boucle 1 : bornes fixes
        UUID forLoop = add(bp, lookup, "for", node("flow/for"), 0, 0);
        literal(bp, lookup, forLoop, "first", LiteralValue.of(PinTypes.INT, 1));
        literal(bp, lookup, forLoop, "last", LiteralValue.of(PinTypes.INT, ITERATIONS));
        link(bp, lookup, resetText, "exec_out", forLoop, "exec_in");

        // Corps : somme = somme + index. Deux nœuds purs et une écriture de variable,
        // ré-évalués à chaque tour — c'est le cœur de ce que la boucle coûte.
        UUID readSum = add(bp, lookup, "readSum", node("var/get"), 200, 160);
        literal(bp, lookup, readSum, "var", LiteralValue.of(PinTypes.STRING, "somme"));
        UUID accumulate = add(bp, lookup, "accumulate", node("math/add"), 400, 160);
        link(bp, lookup, readSum, "value", accumulate, "a");
        link(bp, lookup, forLoop, "index", accumulate, "b");
        UUID writeSum = setVar(bp, lookup, "writeSum", "somme", 620, 90);
        link(bp, lookup, accumulate, "result", writeSum, "value");
        link(bp, lookup, forLoop, "body", writeSum, "exec_in");

        // ------------------------------------------ boucle 2 : condition recalculée
        UUID whileLoop = add(bp, lookup, "while", node("flow/while"), 0, 320);
        link(bp, lookup, forLoop, "completed", whileLoop, "exec_in");

        // La condition est une chaîne de purs RÉ-ÉVALUÉE à chaque tour : « reste > 0 ».
        // Si la mémoïsation des purs la figeait, la boucle ne s'arrêterait jamais — ce
        // graphe est donc aussi un test de non-régression du compilateur.
        UUID readRest = add(bp, lookup, "readRest", node("var/get"), -450, 420);
        literal(bp, lookup, readRest, "var", LiteralValue.of(PinTypes.STRING, "reste"));
        UUID stillPositive = add(bp, lookup, "stillPositive", node("logic/greater"), -220, 420);
        link(bp, lookup, readRest, "value", stillPositive, "a");
        literal(bp, lookup, stillPositive, "b", LiteralValue.of(PinTypes.DOUBLE, 0.0));
        link(bp, lookup, stillPositive, "result", whileLoop, "condition");

        UUID readRestBody = add(bp, lookup, "readRestBody", node("var/get"), 200, 470);
        literal(bp, lookup, readRestBody, "var", LiteralValue.of(PinTypes.STRING, "reste"));
        UUID decrement = add(bp, lookup, "decrement", node("math/sub"), 400, 470);
        link(bp, lookup, readRestBody, "value", decrement, "a");
        literal(bp, lookup, decrement, "b", LiteralValue.of(PinTypes.DOUBLE, 1.0));
        UUID writeRest = setVar(bp, lookup, "writeRest", "reste", 620, 400);
        link(bp, lookup, decrement, "result", writeRest, "value");
        link(bp, lookup, whileLoop, "body", writeRest, "exec_in");

        // --------------------------------------------- boucle 3 : parcours de liste
        // La liste vient d'une DÉCOUPE et non de « list/of » : les pins de ce dernier sont
        // génériques, et un type générique ne porte pas de littéral — on ne peut donc pas
        // lui taper ses éléments dans l'éditeur. La découpe donne une vraie liste de
        // chaînes, éditable d'un seul champ, et exerce un nœud de plus au passage.
        UUID list = add(bp, lookup, "list", node("string/split"), -450, 700);
        literal(bp, lookup, list, "text", LiteralValue.of(PinTypes.STRING, "alpha,beta,gamma"));
        literal(bp, lookup, list, "separator", LiteralValue.of(PinTypes.STRING, ","));

        UUID forEach = add(bp, lookup, "forEach", node("flow/for_each"), 0, 640);
        link(bp, lookup, list, "parts", forEach, "list");
        link(bp, lookup, whileLoop, "completed", forEach, "exec_in");

        UUID readText = add(bp, lookup, "readText", node("var/get"), 200, 800);
        literal(bp, lookup, readText, "var", LiteralValue.of(PinTypes.STRING, "texte"));
        UUID asText = add(bp, lookup, "asText", node("convert/to_string"), 200, 880);
        link(bp, lookup, forEach, "element", asText, "value");
        UUID append = add(bp, lookup, "append", node("string/concat"), 420, 820);
        link(bp, lookup, readText, "value", append, "a");
        link(bp, lookup, asText, "result", append, "b");
        UUID writeText = setVar(bp, lookup, "writeText", "texte", 640, 740);
        link(bp, lookup, append, "result", writeText, "value");
        link(bp, lookup, forEach, "body", writeText, "exec_in");

        // ------------------------------------------------------------- le verdict
        // Le message est CALCULÉ, pas écrit en dur, et il rend compte des TROIS boucles.
        //
        // La deuxième version de ce graphe n'affichait que la somme et la liste : le
        // « while » tournait sans laisser de trace, et rien dans la sortie ne distinguait
        // « il a décrémenté deux cents fois » de « il n'est jamais entré ». Un banc dont
        // un tiers du travail est invisible ne dit pas s'il a tourné.
        //
        // « reste » doit valoir 0 à l'arrivée : c'est la preuve que la condition a bien
        // été RÉ-ÉVALUÉE à chaque tour. Figée, la boucle ne se serait jamais arrêtée.
        UUID message = report(bp, lookup, "somme", "Banc terminé — somme ", 900, 900, null);
        message = report(bp, lookup, "reste", ", reste ", 900, 1060, message);
        message = report(bp, lookup, "texte", ", liste ", 900, 1220, message);

        UUID report = add(bp, lookup, "report", node("player/send_message"), 1700, 640);
        link(bp, lookup, forEach, "completed", report, "exec_in");
        link(bp, lookup, command, "player", report, "player");
        link(bp, lookup, message, "result", report, "text");

        return bp;
    }

    // ------------------------------------------------------------------ outillage

    private static Identifier node(String path) {
        return Identifier.fromNamespaceAndPath("blueprint", path);
    }

    private static void declare(Blueprint bp, NodeTypeLookup lookup, String name,
                                fr.blueprint.api.pin.PinType type, LiteralValue defaultValue) {
        apply(bp, lookup, new EditOperation.AddVariable(
                new Variable(name, type, defaultValue, VarScope.GRAPH, false)));
    }

    private static UUID add(Blueprint bp, NodeTypeLookup lookup, String seed,
                            Identifier type, double x, double y) {
        UUID uuid = UUID.nameUUIDFromBytes(("bench-" + seed)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        apply(bp, lookup, new EditOperation.AddNode(uuid, type, new Vec2d(x, y)));
        return uuid;
    }

    /**
     * Ajoute « {@code <étiquette><valeur de la variable>} » au message en construction.
     *
     * <p>Rend le nœud de concaténation à chaîner au suivant, ou le premier morceau si
     * {@code previous} est nul. Écrire les trois à la main donnait douze nœuds quasi
     * identiques — c'est là que les divergences se glissent.
     */
    private static UUID report(Blueprint bp, NodeTypeLookup lookup, String variable,
                               String label, double x, double y,
                               @org.jetbrains.annotations.Nullable UUID previous) {
        UUID read = add(bp, lookup, "read-" + variable, node("var/get"), x, y);
        literal(bp, lookup, read, "var", LiteralValue.of(PinTypes.STRING, variable));
        UUID asText = add(bp, lookup, "text-" + variable, node("convert/to_string"), x + 200, y);
        link(bp, lookup, read, "value", asText, "value");

        UUID labelled = add(bp, lookup, "label-" + variable, node("string/concat"), x + 400, y);
        literal(bp, lookup, labelled, "a", LiteralValue.of(PinTypes.STRING, label));
        link(bp, lookup, asText, "result", labelled, "b");
        if (previous == null) {
            return labelled;
        }
        UUID joined = add(bp, lookup, "join-" + variable, node("string/concat"), x + 600, y);
        link(bp, lookup, previous, "result", joined, "a");
        link(bp, lookup, labelled, "result", joined, "b");
        return joined;
    }

    /** Un {@code var/set} déjà pointé sur sa variable — le geste le plus répété ici. */
    private static UUID setVar(Blueprint bp, NodeTypeLookup lookup, String seed,
                               String variable, double x, double y) {
        UUID uuid = add(bp, lookup, seed, node("var/set"), x, y);
        literal(bp, lookup, uuid, "var", LiteralValue.of(PinTypes.STRING, variable));
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
            throw new IllegalStateException("Banc incohérent : " + result.refusal());
        }
    }
}
