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
                        ExampleBlueprints::counter),
                new Example(id("reglement"),
                        "une page qui se lit : panneau défilant, texte qui revient à la "
                                + "ligne, infobulles, retour en haut par le graphe",
                        ExampleBlueprints::rules));
    }

    /**
     * Les <b>démonstrations</b> : des projets complets, pas des exemples.
     *
     * <p>La distinction n'est pas cosmétique. Un exemple enseigne <i>une</i> chose et tient
     * en une douzaine de nœuds — c'est une règle du projet, gardée par un test, et la
     * relâcher pour caser un gros graphe l'aurait vidée de son sens. Une démonstration
     * assemble ce que plusieurs exemples ont enseigné et montre à quoi ressemble quelque
     * chose de <b>fini</b>.
     *
     * <p>Elles subissent toutes les autres gardes : elles se valident, se compilent, font
     * leur aller-retour BScript, et leur fichier commité est comparé à ce que le registre
     * produit. Seule la borne de taille ne s'y applique pas.
     */
    public static List<Example> showcases() {
        return List.of(
                new Example(id("banque"),
                        "le contenu déclaré au travail : un bloc qu'on clique ouvre un "
                                + "distributeur, une saisie dicte le montant, et l'argent "
                                + "va et vient entre un compte et l'inventaire",
                        ExampleBlueprints::bank));
    }

    /** Exemples et démonstrations : tout ce qui se charge par {@code /blueprint examples}. */
    public static List<Example> allAndShowcases() {
        List<Example> out = new ArrayList<>(all());
        out.addAll(showcases());
        return out;
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
                fr.blueprint.core.graph.screen.ElementStyle.TextAlign.CENTER, false);

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
     * Une page qu'on <b>lit</b> : le règlement du serveur.
     *
     * <p>Le guichet montre un écran qu'on manipule ; celui-ci montre un écran qu'on
     * parcourt, et c'est un besoin différent. Il enseigne les trois choses qu'aucun autre
     * exemple ne montrait : un <b>panneau défilant</b>, du texte qui <b>revient à la
     * ligne</b>, et des <b>infobulles</b>. Sans elles, une page de règles se découpait en
     * plusieurs écrans reliés par des boutons « suivant » — une pagination, pas une page.
     *
     * <p>Le retour à la ligne est porté par un <b>style nommé</b> et non par chaque
     * étiquette : c'est ce qui fait qu'ajouter un paragraphe ne demande rien d'autre que
     * d'écrire son texte.
     *
     * <p>Et le bouton « Haut de page » appelle {@code gui/set_scroll} : sans lui, un
     * lecteur arrivé au bout n'aurait que la molette pour remonter — et un joueur au
     * clavier, rien du tout.
     */
    private static Blueprint rules(NodeTypeLookup lookup, Identifier blueprintId) {
        Blueprint bp = start(blueprintId, "Une page de règles qui défile et se lit",
                Permission.GAMEPLAY);

        // Le style porte le retour à la ligne : les cinq paragraphes le suivent, et un
        // sixième n'aura qu'à le nommer.
        var paragraphe = fr.blueprint.core.graph.screen.ElementStyle.DEFAULT.withWrap(true);

        var elements = new java.util.ArrayList<fr.blueprint.core.graph.screen.ScreenElement>();
        elements.add(fr.blueprint.core.graph.screen.ScreenElement.of("page",
                        fr.blueprint.core.graph.screen.ElementKind.PANEL, 0, -12, 200, 120)
                .withAnchor(fr.blueprint.core.graph.screen.Anchor.CENTER)
                .resized(fr.blueprint.core.graph.screen.Extent.percent(0.7, 160, 400),
                        fr.blueprint.core.graph.screen.Extent.percent(0.6, 90, 260))
                // Le cadre a une hauteur À LUI : un « ajuster » grandirait avec le
                // contenu, donc rien ne dépasserait, donc il ne défilerait jamais.
                .withLayout(fr.blueprint.core.graph.screen.LayoutSpec.column(4)
                        .withCross(fr.blueprint.core.graph.screen.LayoutSpec.Cross.STRETCH)
                        .withScroll(fr.blueprint.core.graph.screen.LayoutSpec
                                .Scroll.VERTICAL)));
        elements.add(fr.blueprint.core.graph.screen.ScreenElement.of("titre",
                        fr.blueprint.core.graph.screen.ElementKind.LABEL, 0, 0, 160, 12)
                .withParent("page")
                .resized(fr.blueprint.core.graph.screen.Extent.fill(),
                        fr.blueprint.core.graph.screen.Extent.of(12))
                .withText(fr.blueprint.core.graph.screen.ScreenText
                        .literal("Règlement du serveur")));

        String[] regles = {
            "1. Restez courtois. Un désaccord se règle par la parole, pas par la pioche.",
            "2. Ne construisez pas à moins de cinquante blocs d'une base habitée sans "
                    + "l'accord de ses occupants.",
            "3. Les fermes automatiques sont autorisées tant qu'elles ne font pas chuter "
                    + "le nombre d'images par seconde des autres joueurs.",
            "4. Le contenu d'un coffre appartient à qui l'a posé, même sans cadenas.",
            "5. Signalez les défauts plutôt que de les exploiter : ils seront corrigés, "
                    + "et vous serez remercié.",
        };
        for (int i = 0; i < regles.length; i++) {
            elements.add(fr.blueprint.core.graph.screen.ScreenElement.of("regle" + (i + 1),
                            fr.blueprint.core.graph.screen.ElementKind.LABEL, 0, 0, 160, 30)
                    .withParent("page")
                    .resized(fr.blueprint.core.graph.screen.Extent.fill(),
                            fr.blueprint.core.graph.screen.Extent.of(30))
                    .withText(fr.blueprint.core.graph.screen.ScreenText.literal(regles[i]))
                    .withStyleName("paragraphe").styled(paragraphe));
        }

        // Les deux boutons vivent HORS du panneau : ils doivent rester atteignables quelle
        // que soit la position de lecture. Dedans, ils défileraient avec le texte.
        elements.add(fr.blueprint.core.graph.screen.ScreenElement.of("haut",
                        fr.blueprint.core.graph.screen.ElementKind.BUTTON, -34, -8, 60, 16)
                .withAnchor(fr.blueprint.core.graph.screen.Anchor.BOTTOM_CENTER)
                .withText(fr.blueprint.core.graph.screen.ScreenText.literal("Haut de page"))
                .withTooltip(fr.blueprint.core.graph.screen.ScreenText
                        .literal("Revenir au début du règlement")));
        elements.add(fr.blueprint.core.graph.screen.ScreenElement.of("fermer",
                        fr.blueprint.core.graph.screen.ElementKind.BUTTON, 34, -8, 60, 16)
                .withAnchor(fr.blueprint.core.graph.screen.Anchor.BOTTOM_CENTER)
                .withText(fr.blueprint.core.graph.screen.ScreenText.literal("Fermer"))
                .withTooltip(fr.blueprint.core.graph.screen.ScreenText
                        .literal("Ferme la page. Échap fait la même chose.")));

        fr.blueprint.core.graph.GraphLoader.addScreen(bp,
                new fr.blueprint.core.graph.screen.Screen("reglement", false, elements,
                        java.util.Map.of("paragraphe", paragraphe)));

        // /blueprint run reglement : la page s'ouvre. Rien à rafraîchir — elle ne montre
        // aucune variable, et c'est ce qui la rend si courte côté graphe.
        UUID command = add(bp, lookup, "commande", StandardEvents.COMMAND.id(), -640, 0);
        literal(bp, lookup, command, "name", PinTypes.STRING, "reglement");
        UUID open = add(bp, lookup, "ouvrir", node("gui/open"), -400, 0);
        literal(bp, lookup, open, "screen", PinTypes.STRING, "reglement");
        link(bp, lookup, command, "exec_out", open, "exec_in");
        link(bp, lookup, command, "player", open, "player");

        // « Haut de page » : sans ce nœud, un lecteur arrivé au bout n'aurait que la
        // molette pour remonter — et un joueur au clavier, rien du tout.
        UUID topClick = add(bp, lookup, "clic_haut",
                StandardEvents.GUI_ELEMENT_CLICKED.id(), -640, 260);
        literal(bp, lookup, topClick, "element", PinTypes.STRING, "haut");
        UUID toTop = add(bp, lookup, "remonter", node("gui/set_scroll"), -400, 260);
        literal(bp, lookup, toTop, "screen", PinTypes.STRING, "reglement");
        literal(bp, lookup, toTop, "element", PinTypes.STRING, "page");
        literal(bp, lookup, toTop, "offset", PinTypes.DOUBLE, 0.0);
        link(bp, lookup, topClick, "exec_out", toTop, "exec_in");
        link(bp, lookup, topClick, "player", toTop, "player");

        UUID closeClick = add(bp, lookup, "clic_fermer",
                StandardEvents.GUI_ELEMENT_CLICKED.id(), -640, 520);
        literal(bp, lookup, closeClick, "element", PinTypes.STRING, "fermer");
        UUID close = add(bp, lookup, "fermer", node("gui/close"), -400, 520);
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

    /**
     * La <b>banque</b> : le contenu déclaré de l'épic 11 mis au travail.
     *
     * <p>Les six premiers exemples enseignent le graphe, les deux suivants l'écran. Celui-ci
     * montre ce qu'aucun ne montrait : un <b>bloc déclaré</b> qu'on pose et qu'on clique, des
     * <b>items déclarés</b> qui servent de monnaie, et un graphe qui fait circuler la valeur
     * entre un compte et un inventaire. Il exige le contenu de
     * {@code docs/examples/content/} — {@code distributeur}, {@code piece}, {@code lingot}.
     *
     * <h2>Deux coupures, et pourquoi</h2>
     * <p>Une monnaie à une seule pièce ne demande aucun calcul : retirer 250 rend 250 pièces.
     * Avec le lingot à cent, le distributeur doit <b>faire l'appoint</b> — deux lingots et
     * cinquante pièces — ce qui est le premier vrai calcul qu'un exemple d'écran ait à
     * faire, et ce qui rend la division entière et le reste utiles plutôt que décoratifs.
     *
     * <h2>Le montant passe par une variable, et ce n'est pas un détour</h2>
     * <p>Rien ne permet de <i>lire</i> un champ de saisie à la demande : l'événement
     * {@code gui_input_changed} est la seule façon d'en connaître le contenu. Le montant est
     * donc rangé dans une variable à chaque frappe, et les boutons la lisent. C'est le
     * modèle que tout formulaire suivra, et le montrer ici évite à chacun de le redécouvrir.
     *
     * <h2>Ce que le dépôt fait de mieux qu'une soustraction</h2>
     * <p>{@code player/remove_item} rend le nombre <b>réellement</b> retiré. Créditer cela
     * plutôt que le montant demandé est ce qui empêche de créer de l'argent : demander mille
     * pièces qu'on n'a pas retire ce qu'on a, et crédite exactement autant.
     */
    private static Blueprint bank(NodeTypeLookup lookup, Identifier blueprintId) {
        Blueprint bp = start(blueprintId,
                "Une banque : un distributeur qu'on clique, un compte, des pièces et des lingots",
                Permission.GAMEPLAY);

        // Le compte est PAR JOUEUR et persistant : un solde partagé par tout le serveur
        // n'est pas une banque, c'est une cagnotte.
        fr.blueprint.core.graph.GraphLoader.addVariable(bp,
                new fr.blueprint.core.graph.Variable("compte", PinTypes.INT,
                        LiteralValue.of(PinTypes.INT, 0), VarScope.PLAYER, false));
        // Le montant saisi. GRAPH et non PLAYER : il ne vit que le temps d'un écran
        // ouvert, et le persister par joueur serait le garder pour rien.
        variable(bp, lookup, "montant", PinTypes.INT, 0);

        var bouton = new fr.blueprint.core.graph.screen.ElementStyle(
                0xC01F2735, 0xFF6B7280, 1, 0xFFE6E6E6,
                0xC02F3A55, 0xC0141519, 0x60141519, 3,
                fr.blueprint.core.graph.screen.ElementStyle.TextAlign.CENTER, false);

        var pleine = fr.blueprint.core.graph.screen.Extent.fill();
        var elements = java.util.List.of(
                fr.blueprint.core.graph.screen.ScreenElement.of("cadre",
                                fr.blueprint.core.graph.screen.ElementKind.PANEL, 0, 0, 176, 124)
                        .withAnchor(fr.blueprint.core.graph.screen.Anchor.CENTER)
                        .withLayout(fr.blueprint.core.graph.screen.LayoutSpec.column(4)
                                .withCross(fr.blueprint.core.graph.screen.LayoutSpec
                                        .Cross.STRETCH)),
                fr.blueprint.core.graph.screen.ScreenElement.of("titre",
                                fr.blueprint.core.graph.screen.ElementKind.LABEL, 0, 0, 176, 12)
                        .withParent("cadre")
                        .resized(pleine, fr.blueprint.core.graph.screen.Extent.of(12))
                        .withText(fr.blueprint.core.graph.screen.ScreenText
                                .literal("Distributeur")),
                fr.blueprint.core.graph.screen.ScreenElement.of("solde",
                                fr.blueprint.core.graph.screen.ElementKind.LABEL, 0, 0, 176, 12)
                        .withParent("cadre")
                        .resized(pleine, fr.blueprint.core.graph.screen.Extent.of(12))
                        .withBinding(fr.blueprint.core.graph.screen.ElementBinding
                                .text("compte", "Compte : %s pièces")),
                // Filtre ENTIER : un montant n'a pas de décimales, et refuser la frappe
                // vaut mieux que corriger la valeur après coup — le joueur voit
                // immédiatement ce que le champ accepte.
                fr.blueprint.core.graph.screen.ScreenElement.of("montant",
                                fr.blueprint.core.graph.screen.ElementKind.INPUT, 0, 0, 176, 16)
                        .withParent("cadre")
                        .resized(pleine, fr.blueprint.core.graph.screen.Extent.of(16))
                        .withOptions(fr.blueprint.core.graph.screen.ElementOptions.input(
                                "Montant", 6, fr.blueprint.core.graph.screen.ElementOptions
                                        .InputFilter.INTEGER))
                        .withTooltip(fr.blueprint.core.graph.screen.ScreenText
                                .literal("La somme à déposer ou à retirer")),
                fr.blueprint.core.graph.screen.ScreenElement.of("deposer",
                                fr.blueprint.core.graph.screen.ElementKind.BUTTON, 0, 0, 176, 20)
                        .withParent("cadre")
                        .resized(pleine, fr.blueprint.core.graph.screen.Extent.of(20))
                        .withText(fr.blueprint.core.graph.screen.ScreenText
                                .literal("Déposer des pièces"))
                        .withTooltip(fr.blueprint.core.graph.screen.ScreenText
                                .literal("Prend les pièces de votre inventaire"))
                        .withStyleName("bouton").styled(bouton),
                fr.blueprint.core.graph.screen.ScreenElement.of("retirer",
                                fr.blueprint.core.graph.screen.ElementKind.BUTTON, 0, 0, 176, 20)
                        .withParent("cadre")
                        .resized(pleine, fr.blueprint.core.graph.screen.Extent.of(20))
                        .withText(fr.blueprint.core.graph.screen.ScreenText
                                .literal("Retirer"))
                        .withTooltip(fr.blueprint.core.graph.screen.ScreenText
                                .literal("Rend des lingots de 100 et l'appoint en pièces"))
                        .withStyleName("bouton").styled(bouton),
                fr.blueprint.core.graph.screen.ScreenElement.of("fermer",
                                fr.blueprint.core.graph.screen.ElementKind.BUTTON, 0, 0, 176, 20)
                        .withParent("cadre")
                        .resized(pleine, fr.blueprint.core.graph.screen.Extent.of(20))
                        .withText(fr.blueprint.core.graph.screen.ScreenText.literal("Fermer"))
                        .withStyleName("bouton").styled(bouton));

        fr.blueprint.core.graph.GraphLoader.addScreen(bp,
                new fr.blueprint.core.graph.screen.Screen("banque", false, elements,
                        java.util.Map.of("bouton", bouton)));

        bankOpening(bp, lookup);
        bankAmount(bp, lookup);
        bankDeposit(bp, lookup);
        bankWithdraw(bp, lookup);

        UUID closeClick = add(bp, lookup, "close_click",
                StandardEvents.GUI_ELEMENT_CLICKED.id(), -700, 1500);
        literal(bp, lookup, closeClick, "element", PinTypes.STRING, "fermer");
        UUID close = add(bp, lookup, "close", node("gui/close"), -420, 1500);
        link(bp, lookup, closeClick, "exec_out", close, "exec_in");
        link(bp, lookup, closeClick, "player", close, "player");
        return bp;
    }

    /**
     * Clic droit sur un bloc → si c'est le distributeur, l'écran s'ouvre.
     *
     * <p>Le contrôle du bloc n'est pas une formalité : {@code player_use_block} part à
     * <b>chaque</b> clic droit sur <b>n'importe quel</b> bloc du monde. Sans lui, ouvrir
     * une porte ouvrirait la banque.
     */
    private static void bankOpening(Blueprint bp, NodeTypeLookup lookup) {
        UUID used = add(bp, lookup, "used", StandardEvents.PLAYER_USE_BLOCK.id(), -700, 0);
        UUID isBank = add(bp, lookup, "is_bank", node("world/is_block"), -420, 200);
        literal(bp, lookup, isBank, "block", PinTypes.RESOURCE_LOCATION,
                Identifier.fromNamespaceAndPath("blueprint", "distributeur"));
        UUID branch = add(bp, lookup, "open_branch", node("flow/branch"), -420, 0);
        UUID open = add(bp, lookup, "open", node("gui/open"), -140, 0);
        literal(bp, lookup, open, "screen", PinTypes.STRING, "banque");
        UUID firstRefresh = add(bp, lookup, "first", node("gui/refresh"), 140, 0);
        literal(bp, lookup, firstRefresh, "screen", PinTypes.STRING, "banque");

        link(bp, lookup, used, "exec_out", isBank, "exec_in");
        link(bp, lookup, used, "pos", isBank, "pos");
        link(bp, lookup, isBank, "exec_out", branch, "exec_in");
        link(bp, lookup, isBank, "matches", branch, "condition");
        link(bp, lookup, branch, "true", open, "exec_in");
        link(bp, lookup, used, "player", open, "player");
        link(bp, lookup, open, "exec_out", firstRefresh, "exec_in");
        link(bp, lookup, used, "player", firstRefresh, "player");
    }

    /** Chaque frappe dans le champ range le montant : c'est la seule façon de le lire. */
    private static void bankAmount(Blueprint bp, NodeTypeLookup lookup) {
        UUID changed = add(bp, lookup, "changed",
                StandardEvents.GUI_INPUT_CHANGED.id(), -700, 400);
        literal(bp, lookup, changed, "element", PinTypes.STRING, "montant");
        UUID toNumber = add(bp, lookup, "to_number", node("convert/to_number"), -420, 600);
        UUID toInt = add(bp, lookup, "to_int", node("convert/to_int"), -140, 600);
        UUID setAmount = add(bp, lookup, "set_amount", node("var/set"), -420, 400);
        literal(bp, lookup, setAmount, "var", PinTypes.STRING, "montant");

        link(bp, lookup, changed, "exec_out", setAmount, "exec_in");
        link(bp, lookup, changed, "text", toNumber, "text");
        link(bp, lookup, toNumber, "value", toInt, "value");
        link(bp, lookup, toInt, "result", setAmount, "value");
    }

    /**
     * Dépôt : on retire les pièces, et on crédite <b>ce qui a réellement été retiré</b>.
     *
     * <p>C'est toute la différence entre une banque et une imprimerie. Créditer le montant
     * demandé permettrait de déposer mille pièces qu'on n'a pas.
     */
    private static void bankDeposit(Blueprint bp, NodeTypeLookup lookup) {
        UUID click = add(bp, lookup, "deposit_click",
                StandardEvents.GUI_ELEMENT_CLICKED.id(), -700, 800);
        literal(bp, lookup, click, "element", PinTypes.STRING, "deposer");
        UUID amount = add(bp, lookup, "deposit_amount", node("var/get"), -700, 1000);
        literal(bp, lookup, amount, "var", PinTypes.STRING, "montant");
        UUID remove = add(bp, lookup, "remove", node("player/remove_item"), -420, 800);
        literal(bp, lookup, remove, "item", PinTypes.RESOURCE_LOCATION,
                Identifier.fromNamespaceAndPath("blueprint", "piece"));
        UUID balance = add(bp, lookup, "deposit_balance", node("var/get"), -140, 1000);
        literal(bp, lookup, balance, "var", PinTypes.STRING, "compte");
        UUID plus = add(bp, lookup, "deposit_plus", node("math/add"), 140, 1000);
        UUID write = add(bp, lookup, "deposit_write", node("var/set"), -140, 800);
        literal(bp, lookup, write, "var", PinTypes.STRING, "compte");
        UUID refresh = add(bp, lookup, "deposit_refresh", node("gui/refresh"), 140, 800);
        literal(bp, lookup, refresh, "screen", PinTypes.STRING, "banque");

        link(bp, lookup, click, "exec_out", remove, "exec_in");
        link(bp, lookup, click, "player", remove, "player");
        link(bp, lookup, amount, "value", remove, "count");
        link(bp, lookup, remove, "exec_out", write, "exec_in");
        link(bp, lookup, balance, "value", plus, "a");
        link(bp, lookup, remove, "removed", plus, "b");
        link(bp, lookup, plus, "result", write, "value");
        link(bp, lookup, write, "exec_out", refresh, "exec_in");
        link(bp, lookup, click, "player", refresh, "player");
    }

    /**
     * Retrait : on vérifie le solde, puis on rend <b>l'appoint</b> — un lingot par
     * centaine, le reste en pièces.
     *
     * <p>La vérification n'est pas décorative : sans elle, le compte passerait en négatif
     * et le distributeur donnerait de l'argent qui n'existe pas.
     */
    private static void bankWithdraw(Blueprint bp, NodeTypeLookup lookup) {
        UUID click = add(bp, lookup, "withdraw_click",
                StandardEvents.GUI_ELEMENT_CLICKED.id(), -700, 1200);
        literal(bp, lookup, click, "element", PinTypes.STRING, "retirer");
        UUID balance = add(bp, lookup, "withdraw_balance", node("var/get"), -700, 1340);
        literal(bp, lookup, balance, "var", PinTypes.STRING, "compte");
        UUID amount = add(bp, lookup, "withdraw_amount", node("var/get"), -700, 1400);
        literal(bp, lookup, amount, "var", PinTypes.STRING, "montant");
        UUID enough = add(bp, lookup, "enough", node("logic/greater_eq"), -560, 1340);
        UUID branch = add(bp, lookup, "withdraw_branch", node("flow/branch"), -420, 1200);

        // Le solde d'abord : débiter avant de donner, pour qu'une faute plus loin ne
        // laisse jamais le joueur avec l'argent ET le compte intact.
        UUID minus = add(bp, lookup, "withdraw_minus", node("math/sub"), -280, 1400);
        UUID write = add(bp, lookup, "withdraw_write", node("var/set"), -280, 1200);
        literal(bp, lookup, write, "var", PinTypes.STRING, "compte");

        UUID bars = add(bp, lookup, "bars", node("math/div"), -140, 1400);
        literal(bp, lookup, bars, "b", PinTypes.DOUBLE, 100.0);
        UUID barsInt = add(bp, lookup, "bars_int", node("convert/to_int"), 0, 1400);
        UUID coins = add(bp, lookup, "coins", node("math/mod"), -140, 1460);
        literal(bp, lookup, coins, "b", PinTypes.DOUBLE, 100.0);
        UUID coinsInt = add(bp, lookup, "coins_int", node("convert/to_int"), 0, 1460);

        UUID barStack = add(bp, lookup, "bar_stack", node("item/create"), 140, 1400);
        literal(bp, lookup, barStack, "item", PinTypes.RESOURCE_LOCATION,
                Identifier.fromNamespaceAndPath("blueprint", "lingot"));
        UUID giveBars = add(bp, lookup, "give_bars", node("player/give_item"), -140, 1200);
        UUID coinStack = add(bp, lookup, "coin_stack", node("item/create"), 140, 1460);
        literal(bp, lookup, coinStack, "item", PinTypes.RESOURCE_LOCATION,
                Identifier.fromNamespaceAndPath("blueprint", "piece"));
        UUID giveCoins = add(bp, lookup, "give_coins", node("player/give_item"), 0, 1200);
        UUID refresh = add(bp, lookup, "withdraw_refresh", node("gui/refresh"), 140, 1200);
        literal(bp, lookup, refresh, "screen", PinTypes.STRING, "banque");
        UUID refuse = add(bp, lookup, "refuse", node("player/send_message"), -280, 1100);
        literal(bp, lookup, refuse, "text", PinTypes.STRING,
                "Solde insuffisant pour ce retrait.");

        link(bp, lookup, balance, "value", enough, "a");
        link(bp, lookup, amount, "value", enough, "b");
        link(bp, lookup, click, "exec_out", branch, "exec_in");
        link(bp, lookup, enough, "result", branch, "condition");
        link(bp, lookup, branch, "false", refuse, "exec_in");
        link(bp, lookup, click, "player", refuse, "player");

        link(bp, lookup, branch, "true", write, "exec_in");
        link(bp, lookup, balance, "value", minus, "a");
        link(bp, lookup, amount, "value", minus, "b");
        link(bp, lookup, minus, "result", write, "value");

        link(bp, lookup, amount, "value", bars, "a");
        link(bp, lookup, bars, "result", barsInt, "value");
        link(bp, lookup, barsInt, "result", barStack, "count");
        link(bp, lookup, amount, "value", coins, "a");
        link(bp, lookup, coins, "result", coinsInt, "value");
        link(bp, lookup, coinsInt, "result", coinStack, "count");

        link(bp, lookup, write, "exec_out", giveBars, "exec_in");
        link(bp, lookup, click, "player", giveBars, "player");
        link(bp, lookup, barStack, "stack", giveBars, "item");
        link(bp, lookup, giveBars, "exec_out", giveCoins, "exec_in");
        link(bp, lookup, click, "player", giveCoins, "player");
        link(bp, lookup, coinStack, "stack", giveCoins, "item");
        link(bp, lookup, giveCoins, "exec_out", refresh, "exec_in");
        link(bp, lookup, click, "player", refresh, "player");
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
        for (Example example : allAndShowcases()) {
            out.add(example.build(lookup));
        }
        return out;
    }
}
