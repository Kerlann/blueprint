package fr.blueprint.core;

import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.event.StandardEvents;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.BlueprintMeta;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.GraphLoader;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.NodeTypeLookup;
import fr.blueprint.core.graph.VarScope;
import fr.blueprint.core.graph.Variable;
import fr.blueprint.core.graph.Vec2d;
import fr.blueprint.core.graph.screen.Anchor;
import fr.blueprint.core.graph.screen.ElementBinding;
import fr.blueprint.core.graph.screen.ElementKind;
import fr.blueprint.core.graph.screen.ElementOptions;
import fr.blueprint.core.graph.screen.ElementStyle;
import fr.blueprint.core.graph.screen.Extent;
import fr.blueprint.core.graph.screen.LayoutSpec;
import fr.blueprint.core.graph.screen.Screen;
import fr.blueprint.core.graph.screen.ScreenElement;
import fr.blueprint.core.graph.screen.ScreenText;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * <b>La vitrine</b> : les onze types d'éléments d'écran, tous <b>câblés</b>.
 *
 * <p>Montrer les widgets côte à côte serait une planche d'échantillons. Ici chacun est
 * relié à de la logique de graphe : les boutons changent une variable, la liste répond au
 * clic, le curseur et la case à cocher écrivent, le champ de saisie est relu, et le titre
 * comme la barre de progression <b>suivent</b> la variable sans qu'un seul nœud ne les
 * touche — c'est le travail des liaisons.
 *
 * <p>Lancement en jeu : <code>/blueprint showcase</code> puis <code>/vitrine</code>.
 *
 * <h2>Les onze types, et ce que chacun démontre</h2>
 *
 * <ul>
 *   <li><b>PANEL</b> — quatre imbriqués : colonne, lignes, et un panneau défilant ;</li>
 *   <li><b>LABEL</b> — le titre, <i>lié</i> à la variable {@code score} par un format ;</li>
 *   <li><b>BUTTON</b> — trois, qui ajoutent, retirent et ferment ;</li>
 *   <li><b>PROGRESS</b> — <i>liée</i> à {@code score}, bornée de 0 à 100 ;</li>
 *   <li><b>LIST</b> — remplie par le graphe, et qui rend l'indice cliqué ;</li>
 *   <li><b>INPUT</b> — numérique, relu à la validation ;</li>
 *   <li><b>TOGGLE</b> — active et désactive un bouton ;</li>
 *   <li><b>SLIDER</b> — {@code live} : écrit {@code score} à chaque cran, là où le
 *       défaut n'envoie qu'au relâchement ;</li>
 *   <li><b>SLOT</b> — un emplacement d'objet ;</li>
 *   <li><b>IMAGE</b> — une texture du jeu ;</li>
 *   <li><b>ENTITY_PREVIEW</b> — une créature qui tourne.</li>
 * </ul>
 *
 * <h2>Pourquoi les liaisons plutôt que des nœuds</h2>
 *
 * <p>Le titre et la barre pourraient être mis à jour par {@code gui/set_text} et
 * {@code gui/set_progress} à chaque changement. Ils ne le sont pas : ils <b>déclarent</b>
 * suivre {@code score}, et un seul {@code gui/refresh} les remet tous les deux d'accord.
 * C'est la différence entre un écran qu'on repeint et un écran qui se lit — et c'est ce
 * qu'un débutant a le plus de mal à voir sans exemple.
 */
public final class ShowcaseBlueprint {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("blueprint", "vitrine");

    /** Nom à taper : {@code /vitrine}. */
    public static final String COMMAND = "vitrine";

    /** Le nom de l'écran, tel que les nœuds gui le désignent. */
    public static final String SCREEN = "vitrine";

    private ShowcaseBlueprint() {
    }

    // ------------------------------------------------------------------- l'écran

    private static Screen screen() {
        List<ScreenElement> elements = new ArrayList<>();

        // Le cadre : une colonne centrée qui occupe l'essentiel de la fenêtre.
        elements.add(ScreenElement.of("racine", ElementKind.PANEL, 0, 0, 300, 170)
                .withAnchor(Anchor.CENTER)
                .resized(Extent.percent(0.9, 300, 1600), Extent.percent(0.9, 170, 900))
                .withLayout(LayoutSpec.column(4).withCross(LayoutSpec.Cross.STRETCH)));

        // LABEL lié : son texte vient de « score », par le format. Aucun nœud ne l'écrit.
        elements.add(ScreenElement.of("titre", ElementKind.LABEL, 0, 0, 280, 14)
                .withParent("racine")
                .resized(Extent.fill(), Extent.of(14))
                .withText(ScreenText.literal("Vitrine"))
                .withBinding(ElementBinding.text("score", "Score : %s"))
                .withTooltip(ScreenText.literal("Ce texte suit la variable « score »")));

        // PROGRESS liée à la MÊME variable : deux éléments, une source, un refresh.
        elements.add(ScreenElement.of("barre", ElementKind.PROGRESS, 0, 0, 280, 8)
                .withParent("racine")
                .resized(Extent.fill(), Extent.of(8))
                .withBinding(ElementBinding.progress("score", 0, 100))
                .withTooltip(ScreenText.literal("Remplissage de 0 à 100, lié à « score »")));

        elements.add(ScreenElement.of("corps", ElementKind.PANEL, 0, 0, 280, 110)
                .withParent("racine")
                .resized(Extent.fill(), Extent.fill())
                .withLayout(LayoutSpec.row(4).withCross(LayoutSpec.Cross.STRETCH)));

        // --- colonne de gauche : ce qui se regarde ---
        elements.add(ScreenElement.of("visuel", ElementKind.PANEL, 0, 0, 90, 110)
                .withParent("corps")
                .resized(Extent.of(90), Extent.fill())
                .withLayout(LayoutSpec.column(4).withCross(LayoutSpec.Cross.STRETCH)));

        elements.add(ScreenElement.of("image", ElementKind.IMAGE, 0, 0, 80, 30)
                .withParent("visuel")
                .resized(Extent.fill(), Extent.of(30))
                .withTexture(Identifier.withDefaultNamespace("textures/block/oak_planks.png"))
                .withTooltip(ScreenText.literal("Une texture du jeu")));

        elements.add(ScreenElement.of("creature", ElementKind.ENTITY_PREVIEW, 0, 0, 80, 45)
                .withParent("visuel")
                .resized(Extent.fill(), Extent.of(45))
                .withOptions(ElementOptions.entity(Identifier.withDefaultNamespace("cow")))
                .withTooltip(ScreenText.literal("Un aperçu d'entité")));

        elements.add(ScreenElement.of("objet", ElementKind.SLOT, 0, 0, 80, 20)
                .withParent("visuel")
                .resized(Extent.fill(), Extent.of(20))
                .withTooltip(ScreenText.literal(
                        "Autant d'émeraudes que le score — posées par gui/set_item")));

        // --- colonne de droite : ce qui se manipule, dans un panneau défilant ---
        elements.add(ScreenElement.of("reglages", ElementKind.PANEL, 0, 0, 180, 110)
                .withParent("corps")
                .resized(Extent.fill(), Extent.fill())
                .withLayout(LayoutSpec.column(4).withCross(LayoutSpec.Cross.STRETCH)
                        .withScroll(LayoutSpec.Scroll.VERTICAL))
                .withTooltip(ScreenText.literal("Défile à la molette")));

        elements.add(ScreenElement.of("liste", ElementKind.LIST, 0, 0, 170, 40)
                .withParent("reglages")
                .resized(Extent.fill(), Extent.of(40))
                .withOptions(ElementOptions.list(10))
                .withTooltip(ScreenText.literal("Cliquez une ligne : elle devient le score")));

        elements.add(ScreenElement.of("saisie", ElementKind.INPUT, 0, 0, 170, 12)
                .withParent("reglages")
                .resized(Extent.fill(), Extent.of(12))
                .withOptions(ElementOptions.input("Un nombre, puis Entrée", 4,
                        ElementOptions.InputFilter.INTEGER))
                .withTooltip(ScreenText.literal("Validez : le score prend cette valeur")));

        elements.add(ScreenElement.of("curseur", ElementKind.SLIDER, 0, 0, 170, 12)
                .withParent("reglages")
                .resized(Extent.fill(), Extent.of(12))
                // Le placeholder d'un curseur est son UNITÉ : il n'a aucun autre sens
                // pour ce type, et le peintre l'écrit derrière la valeur.
                // « live » : ce curseur rapporte à CHAQUE cran, contrairement au défaut. Une
                // vitrine doit montrer les deux comportements, et celui-ci est le cas qui
                // le justifie — une valeur que le graphe suit en direct. Ailleurs, un
                // curseur muet jusqu'au relâchement épargne au serveur soixante-dix
                // paquets par glissement.
                .withOptions(ElementOptions.slider(0, 100, 5)
                        .withPlaceholder(" pts").withLive(true))
                .withTooltip(ScreenText.literal(
                        "Glissez : le score suit en direct — c'est un curseur « live »")));

        // La liste déroulante : ses choix sont ses LIGNES, les mêmes qu'une liste, posées
        // par le même gui/set_lines. Son texte sert d'invite tant que rien n'est choisi.
        elements.add(ScreenElement.of("choix", ElementKind.DROPDOWN, 0, 0, 170, 12)
                .withParent("reglages")
                .resized(Extent.fill(), Extent.of(12))
                .withText(ScreenText.literal("Choisir un palier…"))
                // rowHeight : le MÊME réglage que pour une liste, puisque les choix sont
                // des lignes. Ici plus aérées que la valeur par défaut.
                .withOptions(ElementOptions.list(14))
                .withTooltip(ScreenText.literal(
                        "Se déplie par-dessus ; flèches, lettre pour chercher, Échap referme")));

        elements.add(ScreenElement.of("bascule", ElementKind.TOGGLE, 0, 0, 170, 12)
                .withParent("reglages")
                .resized(Extent.fill(), Extent.of(12))
                .withText(ScreenText.literal("Autoriser « +10 »"))
                .withTooltip(ScreenText.literal("Décochez : le bouton « +10 » se grise")));

        // --- la barre d'actions ---
        elements.add(ScreenElement.of("actions", ElementKind.PANEL, 0, 0, 280, 16)
                .withParent("racine")
                .resized(Extent.fill(), Extent.of(16))
                .withLayout(LayoutSpec.row(4).withCross(LayoutSpec.Cross.STRETCH)));

        elements.add(ScreenElement.of("plus", ElementKind.BUTTON, 0, 0, 90, 16)
                .withParent("actions")
                .resized(Extent.fill(), Extent.fill())
                .withText(ScreenText.literal("+10")));

        elements.add(ScreenElement.of("moins", ElementKind.BUTTON, 0, 0, 90, 16)
                .withParent("actions")
                .resized(Extent.fill(), Extent.fill())
                .withText(ScreenText.literal("-10")));

        elements.add(ScreenElement.of("fermer", ElementKind.BUTTON, 0, 0, 90, 16)
                .withParent("actions")
                .resized(Extent.fill(), Extent.fill())
                .withText(ScreenText.literal("Fermer")));

        return new Screen(SCREEN, false, elements);
    }

    // ------------------------------------------------------------------ le graphe

    public static Blueprint build(NodeTypeLookup lookup) {
        Blueprint bp = new Blueprint(ID, new BlueprintMeta(
                "Blueprint", "Vitrine : les onze types d'éléments d'écran, tous câblés",
                "1.0.0", Permission.GAMEPLAY));

        // Portée PLAYER : deux joueurs ouvrant la vitrine ont chacun leur score, ce qui
        // est le comportement attendu d'un menu personnel et ce qu'un GRAPH ne donnerait
        // pas — le second verrait le score du premier bouger sous ses yeux.
        GraphLoader.addVariable(bp, new Variable("score", PinTypes.DOUBLE,
                LiteralValue.of(PinTypes.DOUBLE, 50.0), VarScope.PLAYER, true));
        GraphLoader.addScreen(bp, screen());

        openOnCommand(bp, lookup);
        button(bp, lookup, "plus", 10.0, 0);
        button(bp, lookup, "moins", -10.0, 500);
        closeButton(bp, lookup);
        listClick(bp, lookup);
        dropdownPick(bp, lookup);
        inputSubmit(bp, lookup);
        sliderMove(bp, lookup);
        toggleFlip(bp, lookup);
        return bp;
    }

    /** {@code /vitrine} : ouvrir l'écran, puis remplir la liste. */
    private static void openOnCommand(Blueprint bp, NodeTypeLookup lookup) {
        UUID command = add(bp, lookup, "cmd", StandardEvents.COMMAND.id(), -600, -400);
        literal(bp, lookup, command, "name", LiteralValue.of(PinTypes.STRING, COMMAND));

        UUID open = add(bp, lookup, "open", node("gui/open"), -300, -400);
        literal(bp, lookup, open, "screen", LiteralValue.of(PinTypes.STRING, SCREEN));
        link(bp, lookup, command, "exec_out", open, "exec_in");
        link(bp, lookup, command, "player", open, "player");

        // Le contenu de la liste vient du GRAPHE, pas de l'écran : c'est ce qui permet de
        // le calculer. Une découpe de chaîne suffit ici et reste éditable d'un champ.
        UUID lines = add(bp, lookup, "lines", node("string/split"), -600, -250);
        literal(bp, lookup, lines, "text",
                LiteralValue.of(PinTypes.STRING, "0,25,50,75,100"));
        literal(bp, lookup, lines, "separator", LiteralValue.of(PinTypes.STRING, ","));

        UUID fill = add(bp, lookup, "fill", node("gui/set_lines"), -40, -400);
        literal(bp, lookup, fill, "screen", LiteralValue.of(PinTypes.STRING, SCREEN));
        literal(bp, lookup, fill, "element", LiteralValue.of(PinTypes.STRING, "liste"));
        link(bp, lookup, lines, "parts", fill, "lines");
        link(bp, lookup, command, "player", fill, "player");
        link(bp, lookup, open, "exec_out", fill, "exec_in");

        // Le dropdown reçoit ses choix par le MÊME nœud que la liste, et depuis la même
        // découpe : deux widgets, une source, un seul endroit à changer.
        UUID choices = add(bp, lookup, "choices", node("gui/set_lines"), 240, -400);
        literal(bp, lookup, choices, "screen", LiteralValue.of(PinTypes.STRING, SCREEN));
        literal(bp, lookup, choices, "element", LiteralValue.of(PinTypes.STRING, "choix"));
        link(bp, lookup, lines, "parts", choices, "lines");
        link(bp, lookup, command, "player", choices, "player");
        link(bp, lookup, fill, "exec_out", choices, "exec_in");

        refreshAfter(bp, lookup, "cmd", choices, command, 480, -400);
    }

    /** Un bouton qui ajoute {@code delta} au score, puis rafraîchit les liaisons. */
    private static void button(Blueprint bp, NodeTypeLookup lookup, String element,
                               double delta, double y) {
        UUID clicked = add(bp, lookup, element + "-evt",
                StandardEvents.GUI_ELEMENT_CLICKED.id(), -600, y);
        literal(bp, lookup, clicked, "element", LiteralValue.of(PinTypes.STRING, element));

        UUID read = add(bp, lookup, element + "-read", node("var/get"), -600, y + 120);
        literal(bp, lookup, read, "var", LiteralValue.of(PinTypes.STRING, "score"));
        UUID compute = add(bp, lookup, element + "-add", node("math/add"), -380, y + 120);
        link(bp, lookup, read, "value", compute, "a");
        literal(bp, lookup, compute, "b", LiteralValue.of(PinTypes.DOUBLE, delta));
        // Borné : une barre de progression liée à 0..100 n'a rien à afficher au-delà, et
        // un score qui file à l'infini ne se voit que par le titre.
        UUID clamp = add(bp, lookup, element + "-clamp", node("math/clamp"), -160, y + 120);
        link(bp, lookup, compute, "result", clamp, "value");
        literal(bp, lookup, clamp, "min", LiteralValue.of(PinTypes.DOUBLE, 0.0));
        literal(bp, lookup, clamp, "max", LiteralValue.of(PinTypes.DOUBLE, 100.0));

        UUID write = setScore(bp, lookup, element, clamp, -300, y);
        link(bp, lookup, clicked, "exec_out", write, "exec_in");
        refreshAfter(bp, lookup, element, write, clicked, 0, y);
    }

    private static void closeButton(Blueprint bp, NodeTypeLookup lookup) {
        UUID clicked = add(bp, lookup, "fermer-evt",
                StandardEvents.GUI_ELEMENT_CLICKED.id(), -600, 1000);
        literal(bp, lookup, clicked, "element", LiteralValue.of(PinTypes.STRING, "fermer"));
        UUID close = add(bp, lookup, "fermer-do", node("gui/close"), -300, 1000);
        link(bp, lookup, clicked, "exec_out", close, "exec_in");
        link(bp, lookup, clicked, "player", close, "player");
    }

    /** Cliquer une ligne de la liste : sa valeur devient le score. */
    private static void listClick(Blueprint bp, NodeTypeLookup lookup) {
        UUID event = add(bp, lookup, "liste-evt",
                StandardEvents.GUI_LIST_CLICKED.id(), -600, 1300);
        literal(bp, lookup, event, "element", LiteralValue.of(PinTypes.STRING, "liste"));

        // La ligne est du TEXTE : il faut la convertir. « valide » dit si elle en était un,
        // ce qui rend le graphe robuste à une liste qu'on remplirait autrement.
        UUID number = add(bp, lookup, "liste-num", node("convert/to_number"), -600, 1420);
        link(bp, lookup, event, "line", number, "text");

        UUID write = setScore(bp, lookup, "liste", number, -300, 1300);
        link(bp, lookup, event, "exec_out", write, "exec_in");
        refreshAfter(bp, lookup, "liste", write, event, 0, 1300);
    }

    /**
     * Choisir dans la liste déroulante : le palier retenu devient le score.
     *
     * <p>Le même événement que la liste, {@code gui_list_clicked}, et la même validation
     * côté serveur — un dropdown ne pose pas une autre question qu'une liste, il la pose
     * replié. C'est aussi ce qui fait qu'un client modifié annonçant le choix numéro neuf
     * d'une liste qui en compte trois est écarté par le chemin déjà éprouvé.
     */
    private static void dropdownPick(Blueprint bp, NodeTypeLookup lookup) {
        UUID event = add(bp, lookup, "choix-evt",
                StandardEvents.GUI_LIST_CLICKED.id(), -600, 2500);
        literal(bp, lookup, event, "element", LiteralValue.of(PinTypes.STRING, "choix"));

        UUID number = add(bp, lookup, "choix-num", node("convert/to_number"), -600, 2620);
        link(bp, lookup, event, "line", number, "text");

        UUID write = setScore(bp, lookup, "choix", number, -300, 2500);
        link(bp, lookup, event, "exec_out", write, "exec_in");
        refreshAfter(bp, lookup, "choix", write, event, 0, 2500);
    }

    /** Valider le champ de saisie : son contenu devient le score. */
    private static void inputSubmit(Blueprint bp, NodeTypeLookup lookup) {
        UUID event = add(bp, lookup, "saisie-evt",
                StandardEvents.GUI_INPUT_CHANGED.id(), -600, 1600);
        literal(bp, lookup, event, "element", LiteralValue.of(PinTypes.STRING, "saisie"));

        // Le champ émet à CHAQUE frappe ; « soumis » distingue la validation. Sans ce
        // branchement, taper « 100 » écrirait successivement 1, puis 10, puis 100.
        UUID branch = add(bp, lookup, "saisie-if", node("flow/branch"), -380, 1600);
        link(bp, lookup, event, "exec_out", branch, "exec_in");
        link(bp, lookup, event, "submitted", branch, "condition");

        UUID number = add(bp, lookup, "saisie-num", node("convert/to_number"), -600, 1720);
        link(bp, lookup, event, "text", number, "text");

        UUID write = setScore(bp, lookup, "saisie", number, -160, 1600);
        link(bp, lookup, branch, "true", write, "exec_in");
        refreshAfter(bp, lookup, "saisie", write, event, 120, 1600);
    }

    /** Glisser le curseur : le score suit en continu. */
    private static void sliderMove(Blueprint bp, NodeTypeLookup lookup) {
        UUID event = add(bp, lookup, "curseur-evt",
                StandardEvents.GUI_VALUE_CHANGED.id(), -600, 1900);
        literal(bp, lookup, event, "element", LiteralValue.of(PinTypes.STRING, "curseur"));

        UUID write = add(bp, lookup, "curseur-set", node("var/set"), -300, 1900);
        literal(bp, lookup, write, "var", LiteralValue.of(PinTypes.STRING, "score"));
        link(bp, lookup, event, "value", write, "value");
        link(bp, lookup, event, "exec_out", write, "exec_in");
        refreshAfter(bp, lookup, "curseur", write, event, 0, 1900);
    }

    /** Décocher la case grise le bouton « +10 » — un widget qui en pilote un autre. */
    private static void toggleFlip(Blueprint bp, NodeTypeLookup lookup) {
        UUID event = add(bp, lookup, "bascule-evt",
                StandardEvents.GUI_VALUE_CHANGED.id(), -600, 2200);
        literal(bp, lookup, event, "element", LiteralValue.of(PinTypes.STRING, "bascule"));

        UUID enable = add(bp, lookup, "bascule-do", node("gui/set_enabled"), -300, 2200);
        literal(bp, lookup, enable, "screen", LiteralValue.of(PinTypes.STRING, SCREEN));
        literal(bp, lookup, enable, "element", LiteralValue.of(PinTypes.STRING, "plus"));
        link(bp, lookup, event, "checked", enable, "enabled");
        link(bp, lookup, event, "player", enable, "player");
        link(bp, lookup, event, "exec_out", enable, "exec_in");
    }

    // ------------------------------------------------------------------ outillage

    /** Un {@code var/set} sur « score », alimenté par la sortie d'un nœud de calcul. */
    private static UUID setScore(Blueprint bp, NodeTypeLookup lookup, String seed,
                                 UUID source, double x, double y) {
        UUID write = add(bp, lookup, seed + "-set", node("var/set"), x, y);
        literal(bp, lookup, write, "var", LiteralValue.of(PinTypes.STRING, "score"));
        // « result » pour math/*, « value » pour convert/to_number : le nom du pin de
        // sortie dépend du nœud, et se lit dans la palette.
        String pin = bp.node(source).typeId().getPath().startsWith("convert/") ? "value" : "result";
        link(bp, lookup, source, pin, write, "value");
        return write;
    }

    /**
     * Le {@code gui/refresh} qui suit une écriture.
     *
     * <p>C'est LUI qui fait bouger le titre et la barre : les liaisons déclarent quoi
     * suivre, mais rien ne part vers le client tant que personne ne le demande. L'oublier
     * donne un écran qui se fige alors que la variable, elle, a bien changé — la panne la
     * plus déroutante de tout l'épic des interfaces.
     */
    private static void refreshAfter(Blueprint bp, NodeTypeLookup lookup, String seed,
                                     UUID after, UUID playerSource, double x, double y) {
        UUID put = fillSlot(bp, lookup, seed, after, playerSource, x, y);
        UUID refresh = add(bp, lookup, seed + "-refresh", node("gui/refresh"), x + 520, y);
        literal(bp, lookup, refresh, "screen", LiteralValue.of(PinTypes.STRING, SCREEN));
        link(bp, lookup, playerSource, "player", refresh, "player");
        link(bp, lookup, put, "exec_out", refresh, "exec_in");
    }

    /**
     * Remplir l'emplacement d'objet — le seul élément que rien n'écrivait.
     *
     * <p>Le nombre d'émeraudes suit {@code score}. Le poser <b>ici</b>, sur le chemin que
     * toutes les manipulations empruntent déjà, plutôt qu'à chaque site d'écriture :
     * câblé à un seul endroit, le SLOT ne bougerait qu'avec un bouton sur deux, ce qui
     * est pire pour un exemple qu'un emplacement franchement décoratif.
     *
     * <p>Un SLOT ne se lie pas comme le titre ou la barre : une liaison suit une valeur,
     * et un objet n'en est pas une. Il faut un nœud, et c'est justement ce que cet
     * exemple doit montrer — {@code gui/set_item} est le seul chemin.
     *
     * @return le {@code gui/set_item}, à qui enchaîner la suite.
     */
    private static UUID fillSlot(Blueprint bp, NodeTypeLookup lookup, String seed,
                                 UUID after, UUID playerSource, double x, double y) {
        UUID read = add(bp, lookup, seed + "-slot-read", node("var/get"), x + 240, y + 140);
        literal(bp, lookup, read, "var", LiteralValue.of(PinTypes.STRING, "score"));
        // Le compte est un ENTIER : item/create refuse un nombre à virgule, et le score
        // en est un dès que le curseur y passe.
        UUID count = add(bp, lookup, seed + "-slot-count", node("convert/to_int"),
                x + 380, y + 140);
        link(bp, lookup, read, "value", count, "value");

        UUID stack = add(bp, lookup, seed + "-slot-item", node("item/create"), x + 520, y + 140);
        literal(bp, lookup, stack, "item", LiteralValue.of(PinTypes.RESOURCE_LOCATION,
                Identifier.withDefaultNamespace("emerald")));
        link(bp, lookup, count, "result", stack, "count");

        UUID put = add(bp, lookup, seed + "-slot-set", node("gui/set_item"), x + 240, y);
        literal(bp, lookup, put, "screen", LiteralValue.of(PinTypes.STRING, SCREEN));
        literal(bp, lookup, put, "element", LiteralValue.of(PinTypes.STRING, "objet"));
        link(bp, lookup, stack, "stack", put, "item");
        link(bp, lookup, playerSource, "player", put, "player");
        link(bp, lookup, after, "exec_out", put, "exec_in");
        return put;
    }

    private static Identifier node(String path) {
        return Identifier.fromNamespaceAndPath("blueprint", path);
    }

    private static UUID add(Blueprint bp, NodeTypeLookup lookup, String seed,
                            Identifier type, double x, double y) {
        UUID uuid = UUID.nameUUIDFromBytes(("vitrine-" + seed)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        apply(bp, lookup, new EditOperation.AddNode(uuid, type, new Vec2d(x, y)));
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
            throw new IllegalStateException("Vitrine incohérente : " + result.refusal());
        }
    }
}
