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
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Blueprints d'exemple, prêts à charger et à lire.
 *
 * <p>La démo (4.4a) prouvait que le runtime marchait, avec quatre nœuds. Elle ne
 * montre rien de ce que la bibliothèque sait faire depuis : vecteurs, requêtes,
 * signaux, dictionnaires, retours privés. Un joueur qui ouvre l'éditeur pour la
 * première fois a besoin d'un graphe qui RESSEMBLE à ce qu'il voudra écrire.
 *
 * <p>Chacun est construit en Java plutôt qu'écrit à la main en BScript, et
 * {@code ExampleBlueprintsTest} les valide tous : <b>un exemple qui ne compile pas
 * est pire que pas d'exemple</b> — il apprend une erreur. Le test génère aussi les
 * fichiers {@code .bp} de {@code docs/examples/}, exactement comme la référence des
 * nœuds : ce qui est commité est ce que le registre produit.
 *
 * <p>Ils sont volontairement <b>courts</b> : au-delà d'une dizaine de nœuds, un
 * exemple cesse d'être un exemple et devient un projet.
 */
public final class ExampleBlueprints {

    /** Un exemple : son graphe, et ce qu'il apprend à qui le lit. */
    public record Example(Identifier id, String teaches,
                          BiFunction<NodeTypeLookup, Identifier, Blueprint> builder) {

        public Blueprint build(NodeTypeLookup lookup) {
            return builder.apply(lookup, id);
        }
    }

    private ExampleBlueprints() {
    }

    private static Identifier id(String name) {
        return Identifier.fromNamespaceAndPath("blueprint", "example/" + name);
    }

    private static Identifier node(String path) {
        return Identifier.fromNamespaceAndPath("blueprint", path);
    }

    /** Les exemples, dans l'ordre où les lire — du plus simple au plus complet. */
    public static List<Example> all() {
        return List.of(
                new Example(id("porte_secrete"),
                        "événement de bloc, position relative à la face cliquée, permission WORLD",
                        ExampleBlueprints::secretDoor),
                new Example(id("compteur_de_blocs"),
                        "variables persistantes, scoreboard partagé, barre d'action",
                        ExampleBlueprints::blockCounter),
                new Example(id("balise_de_soin"),
                        "requête d'entités, boucle sur une liste, retour privé au joueur",
                        ExampleBlueprints::healingBeacon),
                new Example(id("relais_de_signal"),
                        "signal entre blueprints : émettre ici, écouter là",
                        ExampleBlueprints::signalRelay),
                new Example(id("annonce_de_mort"),
                        "dégâts et mort, entité → joueur, texte riche avec infobulle",
                        ExampleBlueprints::deathAnnouncer),
                new Example(id("jour_et_nuit"),
                        "lecture du monde, comparaison, titre et sous-titre",
                        ExampleBlueprints::dayAndNight),
                new Example(id("guichet"),
                        "un écran complet : disposition en colonne, style nommé, "
                                + "étiquette liée à une variable, boutons câblés",
                        ExampleBlueprints::counter));
    }

    // ------------------------------------------------------------------ exemples

    /**
     * Un <b>écran</b> complet, de bout en bout (story 10.6, AC5) : un signal l'ouvre, une
     * colonne range ses éléments, un style nommé habille les deux boutons, une étiquette
     * montre une variable, et cliquer la change puis rafraîchit.
     *
     * <p>C'est le seul exemple qui montre l'épic 10 en entier. Les six autres enseignent
     * le graphe ; celui-ci enseigne ce qu'on met devant le joueur.
     *
     * <p>Aucune coordonnée n'est écrite sur les enfants du cadre — c'est précisément ce
     * qu'il doit enseigner : la colonne les range elle-même.
     */
    private static Blueprint counter(NodeTypeLookup lookup, Identifier blueprintId) {
        Blueprint bp = start(blueprintId, "Un guichet : un écran, un compteur, deux boutons",
                Permission.GAMEPLAY);

        fr.blueprint.core.graph.GraphLoader.addVariable(bp,
                new fr.blueprint.core.graph.Variable("jetons", PinTypes.INT,
                        fr.blueprint.api.pin.LiteralValue.of(PinTypes.INT, 0),
                        fr.blueprint.core.graph.VarScope.PLAYER, false));

        var bouton = new fr.blueprint.core.graph.screen.ElementStyle(
                0xC01F2735, 0xFF6B7280, 1, 0xFFE6E6E6,
                0xC02F3A55, 0xC0141519, 0x60141519, 3,
                fr.blueprint.core.graph.screen.ElementStyle.TextAlign.CENTER);

        var elements = java.util.List.of(
                fr.blueprint.core.graph.screen.ScreenElement.of("cadre",
                                fr.blueprint.core.graph.screen.ElementKind.PANEL, 0, 0, 160, 90)
                        .withAnchor(fr.blueprint.core.graph.screen.Anchor.CENTER)
                        .withLayout(fr.blueprint.core.graph.screen.LayoutSpec.column(4)
                                .withCross(fr.blueprint.core.graph.screen.LayoutSpec
                                        .Cross.STRETCH)),
                fr.blueprint.core.graph.screen.ScreenElement.of("titre",
                                fr.blueprint.core.graph.screen.ElementKind.LABEL, 0, 0, 160, 12)
                        .withParent("cadre")
                        .resized(fr.blueprint.core.graph.screen.Extent.fill(),
                                fr.blueprint.core.graph.screen.Extent.of(12))
                        .withText(fr.blueprint.core.graph.screen.ScreenText.literal("Guichet")),
                // L'étiquette DÉCLARE ce qu'elle montre : un seul gui/refresh la met à
                // jour, au lieu d'un gui/set_text à chaque endroit où la valeur bouge.
                fr.blueprint.core.graph.screen.ScreenElement.of("solde",
                                fr.blueprint.core.graph.screen.ElementKind.LABEL, 0, 0, 160, 12)
                        .withParent("cadre")
                        .resized(fr.blueprint.core.graph.screen.Extent.fill(),
                                fr.blueprint.core.graph.screen.Extent.of(12))
                        .withBinding(fr.blueprint.core.graph.screen.ElementBinding
                                .text("jetons", "Jetons : %s")),
                fr.blueprint.core.graph.screen.ScreenElement.of("prendre",
                                fr.blueprint.core.graph.screen.ElementKind.BUTTON, 0, 0, 160, 20)
                        .withParent("cadre")
                        .resized(fr.blueprint.core.graph.screen.Extent.fill(),
                                fr.blueprint.core.graph.screen.Extent.of(20))
                        .withText(fr.blueprint.core.graph.screen.ScreenText
                                .literal("Prendre un jeton"))
                        .withStyleName("bouton").styled(bouton),
                fr.blueprint.core.graph.screen.ScreenElement.of("fermer",
                                fr.blueprint.core.graph.screen.ElementKind.BUTTON, 0, 0, 160, 20)
                        .withParent("cadre")
                        .resized(fr.blueprint.core.graph.screen.Extent.fill(),
                                fr.blueprint.core.graph.screen.Extent.of(20))
                        .withText(fr.blueprint.core.graph.screen.ScreenText.literal("Fermer"))
                        .withStyleName("bouton").styled(bouton));

        fr.blueprint.core.graph.GraphLoader.addScreen(bp,
                new fr.blueprint.core.graph.screen.Screen("guichet", false, elements,
                        java.util.Map.of("bouton", bouton)));

        // /blueprint run guichet : l'écran s'ouvre, puis on le remplit une fois.
        //
        // La COMMANDE et non le signal : un signal peut venir de n'importe où — d'un
        // autre blueprint, d'un bloc — et ne porte donc pas de joueur. Un écran, si :
        // il s'ouvre CHEZ quelqu'un.
        UUID signal = add(bp, lookup, "signal", StandardEvents.COMMAND.id(), -640, 0);
        literal(bp, lookup, signal, "name", PinTypes.STRING, "guichet");
        UUID open = add(bp, lookup, "open", node("gui/open"), -400, 0);
        literal(bp, lookup, open, "screen", PinTypes.STRING, "guichet");
        UUID firstRefresh = add(bp, lookup, "first", node("gui/refresh"), -160, 0);
        literal(bp, lookup, firstRefresh, "screen", PinTypes.STRING, "guichet");
        link(bp, lookup, signal, "exec_out", open, "exec_in");
        link(bp, lookup, signal, "player", open, "player");
        link(bp, lookup, open, "exec_out", firstRefresh, "exec_in");
        link(bp, lookup, signal, "player", firstRefresh, "player");

        // Clic sur « prendre » : +1 jeton, puis on redemande le rafraîchissement. Sans
        // ce dernier, l'écran garderait l'ancienne valeur — la liaison ne devine pas.
        UUID clicked = add(bp, lookup, "clicked",
                StandardEvents.GUI_ELEMENT_CLICKED.id(), -640, 260);
        literal(bp, lookup, clicked, "element", PinTypes.STRING, "prendre");
        UUID read = add(bp, lookup, "read", node("var/get"), -400, 460);
        literal(bp, lookup, read, "var", PinTypes.STRING, "jetons");
        UUID plus = add(bp, lookup, "plus", node("math/add"), -160, 460);
        literal(bp, lookup, plus, "b", PinTypes.DOUBLE, 1.0);
        UUID write = add(bp, lookup, "write", node("var/set"), -400, 260);
        literal(bp, lookup, write, "var", PinTypes.STRING, "jetons");
        UUID refresh = add(bp, lookup, "refresh", node("gui/refresh"), -160, 260);
        literal(bp, lookup, refresh, "screen", PinTypes.STRING, "guichet");
        link(bp, lookup, clicked, "exec_out", write, "exec_in");
        link(bp, lookup, read, "value", plus, "a");
        link(bp, lookup, plus, "result", write, "value");
        link(bp, lookup, write, "exec_out", refresh, "exec_in");
        link(bp, lookup, clicked, "player", refresh, "player");

        // Clic sur « fermer » : l'écran se referme.
        UUID closeClick = add(bp, lookup, "close_click",
                StandardEvents.GUI_ELEMENT_CLICKED.id(), -640, 560);
        literal(bp, lookup, closeClick, "element", PinTypes.STRING, "fermer");
        UUID close = add(bp, lookup, "close", node("gui/close"), -400, 560);
        link(bp, lookup, closeClick, "exec_out", close, "exec_in");
        link(bp, lookup, closeClick, "player", close, "player");
        return bp;
    }

    /**
     * Clic droit sur un bloc d'or → la face cliquée devient de la pierre.
     * Le plus court possible, et il montre déjà {@code pos/relative}, qui n'existe
     * que depuis le batch 2.
     */
    private static Blueprint secretDoor(NodeTypeLookup lookup, Identifier blueprintId) {
        Blueprint bp = start(blueprintId, "Clic droit sur un bloc → poser un bloc "
                + "sur la face touchée", Permission.WORLD);
        UUID use = add(bp, lookup, "use", StandardEvents.PLAYER_USE_BLOCK.id(), -520, 0);
        UUID state = add(bp, lookup, "state", node("world/block_state"), -260, 0);
        UUID relative = add(bp, lookup, "relative", node("pos/relative"), -260, 200);
        UUID place = add(bp, lookup, "place", node("world/set_block"), 40, 0);

        literal(bp, lookup, relative, "distance", PinTypes.INT, 1);
        literal(bp, lookup, state, "block", PinTypes.RESOURCE_LOCATION,
                Identifier.withDefaultNamespace("stone"));

        link(bp, lookup, use, "exec_out", state, "exec_in");
        link(bp, lookup, state, "exec_out", place, "exec_in");
        link(bp, lookup, use, "pos", relative, "pos");
        link(bp, lookup, use, "face", relative, "direction");
        link(bp, lookup, relative, "pos", place, "pos");
        link(bp, lookup, state, "state", place, "state");
        return bp;
    }

    /**
     * Chaque bloc cassé incrémente un score, et le joueur le voit en barre d'action.
     * Le score passe par le SCOREBOARD et non par une variable : ainsi
     * {@code /scoreboard} et l'affichage latéral le voient aussi.
     */
    private static Blueprint blockCounter(NodeTypeLookup lookup, Identifier blueprintId) {
        Blueprint bp = start(blueprintId, "Compter les blocs cassés, dans le scoreboard",
                Permission.GAMEPLAY);
        variable(bp, lookup, "dernier_total", PinTypes.DOUBLE, 0.0);

        UUID broke = add(bp, lookup, "broke", StandardEvents.PLAYER_BREAK_BLOCK.id(), -520, 0);
        UUID score = add(bp, lookup, "score", node("score/add"), -240, 0);
        UUID text = add(bp, lookup, "text", node("convert/to_string"), 0, 160);
        UUID concat = add(bp, lookup, "concat", node("string/concat"), 240, 160);
        UUID bar = add(bp, lookup, "bar", node("player/action_bar"), 480, 0);

        literal(bp, lookup, score, "objective", PinTypes.STRING, "blocs");
        literal(bp, lookup, score, "amount", PinTypes.INT, 1);
        literal(bp, lookup, concat, "a", PinTypes.STRING, "Blocs cassés : ");

        link(bp, lookup, broke, "exec_out", score, "exec_in");
        link(bp, lookup, broke, "player", score, "entity");
        link(bp, lookup, score, "exec_out", bar, "exec_in");
        link(bp, lookup, broke, "player", bar, "player");
        link(bp, lookup, score, "score", text, "value");
        link(bp, lookup, text, "result", concat, "b");
        link(bp, lookup, concat, "result", bar, "text");
        return bp;
    }

    /**
     * Toutes les cinq secondes, soigne les joueurs à moins de huit blocs d'une
     * position et le leur dit — en privé. Montre le trio requête → boucle → action,
     * qui est le squelette de la plupart des scripts de serveur.
     */
    private static Blueprint healingBeacon(NodeTypeLookup lookup, Identifier blueprintId) {
        Blueprint bp = start(blueprintId, "Soigner les joueurs proches, toutes les 5 s",
                Permission.GAMEPLAY);

        UUID tick = add(bp, lookup, "tick", StandardEvents.SERVER_TICK.id(), -640, 0);
        UUID wait = add(bp, lookup, "wait", node("flow/wait"), -400, 0);
        UUID center = add(bp, lookup, "center", node("vec/make"), -400, 200);
        UUID nearest = add(bp, lookup, "nearest", node("query/nearest_player"), -160, 0);
        UUID branch = add(bp, lookup, "branch", node("flow/branch"), 80, 0);
        UUID heal = add(bp, lookup, "heal", node("entity/heal"), 320, -60);
        UUID sound = add(bp, lookup, "sound", node("player/play_sound"), 560, -60);

        literal(bp, lookup, wait, "ticks", PinTypes.INT, 100);
        literal(bp, lookup, center, "x", PinTypes.DOUBLE, 0.0);
        literal(bp, lookup, center, "y", PinTypes.DOUBLE, 64.0);
        literal(bp, lookup, center, "z", PinTypes.DOUBLE, 0.0);
        literal(bp, lookup, nearest, "radius", PinTypes.DOUBLE, 8.0);
        literal(bp, lookup, heal, "amount", PinTypes.DOUBLE, 2.0);
        literal(bp, lookup, sound, "sound", PinTypes.RESOURCE_LOCATION,
                Identifier.withDefaultNamespace("block.beacon.activate"));

        link(bp, lookup, tick, "exec_out", wait, "exec_in");
        link(bp, lookup, wait, "exec_out", nearest, "exec_in");
        link(bp, lookup, center, "vec", nearest, "pos");
        link(bp, lookup, nearest, "exec_out", branch, "exec_in");
        link(bp, lookup, nearest, "found", branch, "condition");
        link(bp, lookup, branch, "true", heal, "exec_in");
        link(bp, lookup, nearest, "player", heal, "entity");
        link(bp, lookup, heal, "exec_out", sound, "exec_in");
        link(bp, lookup, nearest, "player", sound, "player");
        return bp;
    }

    /**
     * Un blueprint qui ÉMET, un nœud qui ÉCOUTE — dans le même graphe pour tenir en
     * un fichier, mais rien ne les relie sinon la chaîne « alarme ». C'est tout
     * l'intérêt : le second peut vivre dans un autre blueprint, écrit par quelqu'un
     * d'autre.
     */
    private static Blueprint signalRelay(NodeTypeLookup lookup, Identifier blueprintId) {
        Blueprint bp = start(blueprintId, "Émettre un signal ici, l'écouter là",
                Permission.GAMEPLAY);

        UUID chat = add(bp, lookup, "chat", StandardEvents.PLAYER_CHAT.id(), -520, -160);
        UUID contains = add(bp, lookup, "contains", node("string/contains"), -280, -60);
        UUID branch = add(bp, lookup, "branch", node("flow/branch"), -40, -160);
        UUID emit = add(bp, lookup, "emit", node("signal/emit"), 200, -160);

        literal(bp, lookup, contains, "search", PinTypes.STRING, "alerte");
        literal(bp, lookup, emit, "name", PinTypes.STRING, "alarme");

        link(bp, lookup, chat, "exec_out", branch, "exec_in");
        link(bp, lookup, chat, "message", contains, "value");
        link(bp, lookup, contains, "result", branch, "condition");
        link(bp, lookup, branch, "true", emit, "exec_in");
        link(bp, lookup, chat, "message", emit, "payload");

        // L'écouteur — l'autre moitié, indépendante de la première.
        UUID listen = add(bp, lookup, "listen", StandardEvents.SIGNAL.id(), -520, 200);
        UUID players = add(bp, lookup, "players", node("query/players"), -280, 200);
        UUID each = add(bp, lookup, "each", node("flow/for_each"), -40, 200);
        UUID title = add(bp, lookup, "title", node("player/title"), 200, 200);

        literal(bp, lookup, listen, "name", PinTypes.STRING, "alarme");
        literal(bp, lookup, title, "text", PinTypes.STRING, "ALERTE");

        link(bp, lookup, listen, "exec_out", players, "exec_in");
        link(bp, lookup, players, "exec_out", each, "exec_in");
        link(bp, lookup, players, "players", each, "list");
        link(bp, lookup, each, "body", title, "exec_in");
        link(bp, lookup, each, "element", title, "player");
        return bp;
    }

    /**
     * Quand une entité meurt, dire qui c'était — et si c'était un joueur, le nommer.
     * Montre {@code entity/as_player}, le pont qui manquait : l'événement rend une
     * entité, et un joueur mort restait anonyme.
     */
    private static Blueprint deathAnnouncer(NodeTypeLookup lookup, Identifier blueprintId) {
        Blueprint bp = start(blueprintId, "Annoncer la mort d'un joueur, avec infobulle",
                Permission.GAMEPLAY);

        UUID death = add(bp, lookup, "death", StandardEvents.ENTITY_DEATH.id(), -640, 0);
        UUID asPlayer = add(bp, lookup, "as_player", node("entity/as_player"), -400, 0);
        UUID branch = add(bp, lookup, "branch", node("flow/branch"), -160, 0);
        UUID name = add(bp, lookup, "name", node("entity/name"), -160, 200);
        UUID literal = add(bp, lookup, "literal", node("text/literal"), 80, 200);
        UUID hover = add(bp, lookup, "hover", node("text/hover"), 320, 200);
        UUID tooltip = add(bp, lookup, "tooltip", node("text/literal"), 80, 340);
        UUID send = add(bp, lookup, "send", node("player/send_text"), 560, 0);

        literal(bp, lookup, tooltip, "value", PinTypes.STRING, "Repose en paix");

        // Les nœuds de requête sont EXEC (leur résultat change à chaque tick, la
        // mémoïsation des purs le figerait) : « nom de l'entité » vit donc DANS la
        // chaîne d'exécution, pas seulement au bout d'un fil de données.
        link(bp, lookup, death, "exec_out", asPlayer, "exec_in");
        link(bp, lookup, death, "entity", asPlayer, "entity");
        link(bp, lookup, asPlayer, "exec_out", branch, "exec_in");
        link(bp, lookup, asPlayer, "is_player", branch, "condition");
        link(bp, lookup, branch, "true", name, "exec_in");
        link(bp, lookup, death, "entity", name, "entity");
        link(bp, lookup, name, "exec_out", send, "exec_in");
        link(bp, lookup, name, "name", literal, "value");
        link(bp, lookup, literal, "text", hover, "text");
        link(bp, lookup, tooltip, "text", hover, "tooltip");
        link(bp, lookup, asPlayer, "player", send, "player");
        link(bp, lookup, hover, "text", send, "text");
        return bp;
    }

    /**
     * À la connexion, dire au joueur s'il fait jour ou nuit, en titre + sous-titre.
     * Le plus lisible des six : un événement, une lecture du monde, un branchement.
     */
    private static Blueprint dayAndNight(NodeTypeLookup lookup, Identifier blueprintId) {
        Blueprint bp = start(blueprintId, "Accueil selon l'heure du monde",
                Permission.GAMEPLAY);

        UUID join = add(bp, lookup, "join", StandardEvents.PLAYER_JOIN.id(), -640, 0);
        UUID isDay = add(bp, lookup, "is_day", node("world/is_day"), -400, 0);
        UUID select = add(bp, lookup, "select", node("flow/select"), -160, 180);
        UUID title = add(bp, lookup, "title", node("player/title"), 80, 0);
        UUID subtitle = add(bp, lookup, "subtitle", node("player/subtitle"), 320, 0);

        literal(bp, lookup, select, "if_true", PinTypes.STRING, "Bonjour");
        literal(bp, lookup, select, "if_false", PinTypes.STRING, "Bonne nuit");
        literal(bp, lookup, subtitle, "text", PinTypes.STRING, "Bienvenue sur le serveur");

        link(bp, lookup, join, "exec_out", isDay, "exec_in");
        link(bp, lookup, isDay, "exec_out", title, "exec_in");
        link(bp, lookup, isDay, "is_day", select, "condition");
        link(bp, lookup, select, "value", title, "text");
        link(bp, lookup, join, "player", title, "player");
        link(bp, lookup, title, "exec_out", subtitle, "exec_in");
        link(bp, lookup, join, "player", subtitle, "player");
        return bp;
    }

    // ------------------------------------------------------------------ outillage

    private static Blueprint start(Identifier blueprintId, String description, Permission cap) {
        return new Blueprint(blueprintId,
                new BlueprintMeta("Blueprint", description, "1.0.0", cap));
    }

    /**
     * UUID <b>dérivé du nom</b> : deux générations du même exemple donnent le même
     * graphe, octet pour octet. Sans cela, le fichier {@code .bp} commité changerait à
     * chaque exécution et sa garde de régénération n'aurait aucun sens.
     */
    private static UUID add(Blueprint bp, NodeTypeLookup lookup, String seed,
                            Identifier type, double x, double y) {
        UUID uuid = UUID.nameUUIDFromBytes(
                (bp.id() + "-" + seed).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        apply(bp, lookup, new EditOperation.AddNode(uuid, type, new Vec2d(x, y)));
        return uuid;
    }

    private static void literal(Blueprint bp, NodeTypeLookup lookup, UUID node,
                                String pin, fr.blueprint.api.pin.PinType type, Object value) {
        apply(bp, lookup, new EditOperation.SetLiteral(node, pin, LiteralValue.of(type, value)));
    }

    private static void link(Blueprint bp, NodeTypeLookup lookup,
                             UUID from, String fromPin, UUID to, String toPin) {
        apply(bp, lookup, new EditOperation.AddLink(new Link(from, fromPin, to, toPin)));
    }

    private static void variable(Blueprint bp, NodeTypeLookup lookup, String name,
                                 fr.blueprint.api.pin.PinType type, Object initial) {
        apply(bp, lookup, new EditOperation.AddVariable(new Variable(name, type,
                LiteralValue.of(type, initial), VarScope.GRAPH, false)));
    }

    /**
     * Un exemple incohérent doit exploser À LA CONSTRUCTION, pas s'installer à moitié
     * dans le monde d'un joueur : le message nomme l'opération refusée.
     */
    private static void apply(Blueprint bp, NodeTypeLookup lookup, EditOperation op) {
        EditOperation.Result result = op.apply(bp, lookup);
        if (!result.applied()) {
            throw new IllegalStateException(
                    "Exemple « " + bp.id() + " » incohérent : " + result.refusal()
                            + " (opération : " + op + ")");
        }
    }

    /** Tous les exemples construits — pour la commande et pour le test. */
    public static List<Blueprint> buildAll(NodeTypeLookup lookup) {
        List<Blueprint> out = new ArrayList<>();
        for (Example example : all()) {
            out.add(example.build(lookup));
        }
        return out;
    }
}
