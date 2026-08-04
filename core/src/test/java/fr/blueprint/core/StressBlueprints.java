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
import fr.blueprint.core.graph.ScreenOps;
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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * Deux blueprints <b>volumineux</b>, construits pour être éprouvés — pas pour être lus.
 *
 * <p>Les huit exemples de {@link ExampleBlueprints} enseignent : ils sont courts par
 * dessein, et c'est ce qui les rend impropres à répondre à « est-ce que ça tient à
 * l'échelle ? ». Tous les tests du projet travaillent sur cinq à soixante-quatre éléments,
 * alors que le modèle en autorise <b>cent vingt-huit par écran</b> et <b>mille nœuds</b>.
 * Entre les deux, personne ne regardait.
 *
 * <p>Ce que ces deux-là mettent sous tension, précisément :
 * <ul>
 *   <li>le <b>paquet réseau</b> d'un écran, plafonné à 64 Kio — un écran réellement
 *       chargé y tient-il, ou l'ouverture échoue-t-elle chez le joueur ?</li>
 *   <li>l'aller-retour <b>BScript</b> sur un graphe où tout coexiste : c'est là que le
 *       projet a déjà perdu un filtre d'événement sans que rien ne le voie ;</li>
 *   <li>la <b>passe de disposition</b> sur un écran profond, à onze types d'éléments et
 *       trois panneaux défilants imbriqués ;</li>
 *   <li>la <b>compilation</b> d'un graphe long, où les branches se rejoignent.</li>
 * </ul>
 *
 * <p>Fixtures de test et non exemples livrés : ils n'ont rien à enseigner et
 * encombreraient {@code /blueprint examples}. Ils sont écrits dans
 * {@code run/blueprint/exports/} pour la session en jeu, où ils remplacent la corvée de
 * construire un cas lourd à la main.
 */
public final class StressBlueprints {

    /** Sous le plafond de 128, et assez près pour que le plafond compte. */
    public static final int ELEMENTS = 110;

    /** Sous le plafond de 1000, et assez long pour qu'un défilement de vue serve. */
    public static final int CHAIN_LENGTH = 120;

    private StressBlueprints() {
    }

    private static Identifier id(String name) {
        return Identifier.fromNamespaceAndPath("blueprint", "banc/" + name);
    }

    private static Identifier node(String path) {
        return Identifier.fromNamespaceAndPath("blueprint", path);
    }

    // ------------------------------------------------------------------ l'écran

    /**
     * Un écran <b>chargé</b> : les onze types d'éléments, trois panneaux défilants
     * imbriqués sur les deux axes, des paragraphes qui reviennent à la ligne, des
     * infobulles partout, deux styles nommés et quatre liaisons de variables.
     *
     * <p>Il n'est pas beau et n'a pas à l'être. Il est <b>complet</b> : ouvrir cet écran,
     * c'est voir d'un coup tout ce que l'épic 10 sait faire, et mesurer ce que cela coûte.
     */
    public static Blueprint bigScreen(NodeTypeLookup lookup) {
        Blueprint bp = new Blueprint(id("ecran"), new BlueprintMeta("Blueprint",
                "Banc d'essai : un écran chargé, tous les types d'éléments",
                "1.0.0", Permission.GAMEPLAY));

        for (String name : List.of("or", "pv", "niveau", "nom")) {
            GraphLoader.addVariable(bp, new Variable(name,
                    name.equals("nom") ? PinTypes.STRING : PinTypes.INT,
                    name.equals("nom") ? LiteralValue.of(PinTypes.STRING, "Sans nom")
                            : LiteralValue.of(PinTypes.INT, 7),
                    VarScope.PLAYER, false));
        }

        var paragraphe = ElementStyle.DEFAULT.withWrap(true);
        var actif = new ElementStyle(0xC01F2735, 0xFF7AA2F7, 1, 0xFFE6E6E6,
                0xC02F3A55, 0xC0141519, 0x60141519, 3, ElementStyle.TextAlign.CENTER, false);
        var styles = new LinkedHashMap<String, ElementStyle>();
        styles.put("paragraphe", paragraphe);
        styles.put("actif", actif);

        List<ScreenElement> elements = new ArrayList<>();

        // La racine : une colonne qui tient toute la fenêtre.
        elements.add(ScreenElement.of("racine", ElementKind.PANEL, 0, 0, 300, 170)
                .withAnchor(Anchor.CENTER)
                .resized(Extent.percent(0.94, 300, 1800), Extent.percent(0.94, 170, 1000))
                .withLayout(LayoutSpec.column(3).withCross(LayoutSpec.Cross.STRETCH))
                .withTooltip(ScreenText.literal("Le cadre de tout le banc")));

        elements.add(ScreenElement.of("titre", ElementKind.LABEL, 0, 0, 200, 12)
                .withParent("racine")
                .resized(Extent.fill(), Extent.of(12))
                .withText(ScreenText.literal("Banc d'essai — tous les types"))
                .withStyleName("actif").styled(actif));

        // Un panneau défilant VERTICAL : la longue page de texte.
        elements.add(ScreenElement.of("lecture", ElementKind.PANEL, 0, 0, 200, 60)
                .withParent("racine")
                .resized(Extent.fill(), Extent.of(60))
                .withLayout(LayoutSpec.column(2).withCross(LayoutSpec.Cross.STRETCH)
                        .withScroll(LayoutSpec.Scroll.VERTICAL))
                .withTooltip(ScreenText.literal("Défile verticalement")));
        for (int i = 0; i < 18; i++) {
            elements.add(ScreenElement.of("para" + i, ElementKind.LABEL, 0, 0, 180, 24)
                    .withParent("lecture")
                    .resized(Extent.fill(), Extent.of(24))
                    .withText(ScreenText.literal("Paragraphe " + (i + 1)
                            + " : un texte assez long pour revenir à la ligne au moins une "
                            + "fois, et vérifier que ce qui dépasse en hauteur est coupé "
                            + "net plutôt que débordé sur ce qui suit."))
                    .withStyleName("paragraphe").styled(paragraphe));
        }

        // Un panneau défilant HORIZONTAL : une rangée trop large.
        elements.add(ScreenElement.of("rangee", ElementKind.PANEL, 0, 0, 200, 26)
                .withParent("racine")
                .resized(Extent.fill(), Extent.of(26))
                .withLayout(LayoutSpec.row(2).withScroll(LayoutSpec.Scroll.HORIZONTAL))
                .withTooltip(ScreenText.literal("Défile horizontalement")));
        for (int i = 0; i < 14; i++) {
            elements.add(ScreenElement.of("vignette" + i, ElementKind.BUTTON, 0, 0, 60, 20)
                    .withParent("rangee")
                    .withText(ScreenText.literal("Onglet " + (i + 1)))
                    .withTooltip(ScreenText.literal("Onglet numéro " + (i + 1)))
                    .withStyleName(i == 0 ? "actif" : "").styled(i == 0 ? actif
                            : ElementStyle.DEFAULT));
        }

        // Un panneau défilant sur LES DEUX axes, contenant une grille.
        elements.add(ScreenElement.of("plan", ElementKind.PANEL, 0, 0, 200, 44)
                .withParent("racine")
                .resized(Extent.fill(), Extent.of(44))
                .withLayout(LayoutSpec.grid(6, 2, 2).withScroll(LayoutSpec.Scroll.BOTH))
                .withTooltip(ScreenText.literal("Défile dans les deux sens")));
        for (int i = 0; i < 30; i++) {
            elements.add(ScreenElement.of("case" + i, ElementKind.SLOT, 0, 0, 18, 18)
                    .withParent("plan")
                    .withTooltip(ScreenText.literal("Emplacement " + (i + 1))));
        }

        // Les types restants, une fois chacun : c'est ce qui rend l'écran COMPLET.
        elements.add(ScreenElement.of("reglages", ElementKind.PANEL, 0, 0, 200, 40)
                .withParent("racine")
                .resized(Extent.fill(), Extent.of(40))
                .withLayout(LayoutSpec.column(2).withCross(LayoutSpec.Cross.STRETCH)));
        elements.add(ScreenElement.of("or", ElementKind.LABEL, 0, 0, 180, 10)
                .withParent("reglages").resized(Extent.fill(), Extent.of(10))
                .withBinding(ElementBinding.text("or", "Or : %s"))
                .withTooltip(ScreenText.literal("Lu depuis la variable « or »")));
        elements.add(ScreenElement.of("barre", ElementKind.PROGRESS, 0, 0, 180, 8)
                .withParent("reglages").resized(Extent.fill(), Extent.of(8))
                .withBinding(new ElementBinding("pv", ElementBinding.Target.PROGRESS,
                        "%s", 0, 20, 0))
                .withTooltip(ScreenText.literal("Points de vie, de 0 à 20")));
        elements.add(ScreenElement.of("saisie", ElementKind.INPUT, 0, 0, 180, 14)
                .withParent("reglages").resized(Extent.fill(), Extent.of(14))
                .withOptions(new ElementOptions("Votre nom…", 24,
                        ElementOptions.InputFilter.TEXT, 0, 0, 0, 0, null))
                .withTooltip(ScreenText.literal("Vingt-quatre caractères au plus")));
        elements.add(ScreenElement.of("case_a_cocher", ElementKind.TOGGLE, 0, 0, 180, 12)
                .withParent("reglages").resized(Extent.fill(), Extent.of(12))
                .withText(ScreenText.literal("Recevoir les annonces"))
                .withTooltip(ScreenText.literal("Se souvient de votre choix")));
        elements.add(ScreenElement.of("curseur", ElementKind.SLIDER, 0, 0, 180, 12)
                .withParent("reglages").resized(Extent.fill(), Extent.of(12))
                .withOptions(new ElementOptions("", 0, ElementOptions.InputFilter.TEXT,
                        0, 100, 5, 0, null))
                .withTooltip(ScreenText.literal("De 0 à 100, par pas de 5")));
        elements.add(ScreenElement.of("liste", ElementKind.LIST, 0, 0, 180, 30)
                .withParent("reglages").resized(Extent.fill(), Extent.of(30))
                .withOptions(new ElementOptions("", 0, ElementOptions.InputFilter.TEXT,
                        0, 0, 0, 10, null))
                .withTooltip(ScreenText.literal("Remplie par le graphe")));
        elements.add(ScreenElement.of("image", ElementKind.IMAGE, 0, 0, 32, 32)
                .withParent("reglages")
                .withTexture(Identifier.withDefaultNamespace("textures/block/stone.png"))
                .withTooltip(ScreenText.literal("Une texture du jeu")));
        elements.add(ScreenElement.of("creature", ElementKind.ENTITY_PREVIEW, 0, 0, 40, 40)
                .withParent("reglages")
                .withOptions(new ElementOptions("", 0, ElementOptions.InputFilter.TEXT,
                        0, 0, 0, 0, Identifier.withDefaultNamespace("pig")))
                .withTooltip(ScreenText.literal("Un aperçu d'entité, jamais une vraie")));

        // Le compte est complété par des étiquettes ordinaires : ce qu'on veut mesurer
        // est le PLAFOND, et il ne se mesure qu'en s'en approchant.
        for (int i = elements.size(); i < ELEMENTS; i++) {
            elements.add(ScreenElement.of("garniture" + i, ElementKind.LABEL, 0, 0, 60, 8)
                    .withParent("lecture")
                    .resized(Extent.fill(), Extent.of(8))
                    .withText(ScreenText.literal("Ligne de garniture " + i)));
        }

        GraphLoader.addScreen(bp, new Screen("banc", false, elements, styles));

        // Un HUD à côté : il ne capte pas la souris, donc aucun élément interactif.
        GraphLoader.addScreen(bp, new Screen("bandeau", true, List.of(
                ScreenElement.of("cadre_hud", ElementKind.PANEL, -4, 4, 90, 30)
                        .withAnchor(Anchor.TOP_RIGHT)
                        .withLayout(LayoutSpec.column(1).withCross(LayoutSpec.Cross.STRETCH)),
                ScreenElement.of("hud_or", ElementKind.LABEL, 0, 0, 80, 10)
                        .withParent("cadre_hud").resized(Extent.fill(), Extent.of(10))
                        .withBinding(ElementBinding.text("or", "Or : %s")),
                ScreenElement.of("hud_pv", ElementKind.PROGRESS, 0, 0, 80, 6)
                        .withParent("cadre_hud").resized(Extent.fill(), Extent.of(6))
                        .withBinding(new ElementBinding("pv",
                                ElementBinding.Target.PROGRESS, "%s", 0, 20, 0)))));

        // Le graphe minimal qui l'ouvre : /blueprint run banc_ecran.
        UUID command = add(bp, lookup, "cmd", StandardEvents.COMMAND.id(), -640, 0);
        literal(bp, lookup, command, "name", PinTypes.STRING, "banc_ecran");
        UUID open = add(bp, lookup, "open", node("gui/open"), -400, 0);
        literal(bp, lookup, open, "screen", PinTypes.STRING, "banc");
        UUID refresh = add(bp, lookup, "refresh", node("gui/refresh"), -160, 0);
        literal(bp, lookup, refresh, "screen", PinTypes.STRING, "banc");
        link(bp, lookup, command, "exec_out", open, "exec_in");
        link(bp, lookup, command, "player", open, "player");
        link(bp, lookup, open, "exec_out", refresh, "exec_in");
        link(bp, lookup, command, "player", refresh, "player");
        return bp;
    }

    // ------------------------------------------------------------------ le graphe

    /**
     * Un graphe <b>long</b> : une chaîne de {@value #CHAIN_LENGTH} opérations qui se
     * suivent, plus autant de calculs purs qui l'alimentent.
     *
     * <p>Une chaîne, et non un tas de nœuds indépendants : ce qu'on veut éprouver est la
     * <b>compilation</b> et le parcours, pas la simple présence de beaucoup d'objets. Un
     * graphe large mais plat se compile en un clin d'œil et ne prouverait rien.
     */
    public static Blueprint longGraph(NodeTypeLookup lookup) {
        Blueprint bp = new Blueprint(id("graphe"), new BlueprintMeta("Blueprint",
                "Banc d'essai : une longue chaîne de nœuds", "1.0.0", Permission.GAMEPLAY));
        GraphLoader.addVariable(bp, new Variable("compteur", PinTypes.INT,
                LiteralValue.of(PinTypes.INT, 0), VarScope.GRAPH, false));

        UUID event = add(bp, lookup, "event", StandardEvents.COMMAND.id(), -900, 0);
        literal(bp, lookup, event, "name", PinTypes.STRING, "banc_graphe");

        UUID previous = event;
        String previousPin = "exec_out";
        for (int i = 0; i < CHAIN_LENGTH; i++) {
            // Une lecture, une addition, une écriture : trois nœuds par maillon, dont
            // deux PURS. C'est le mélange réel d'un graphe qui fait quelque chose.
            UUID read = add(bp, lookup, "read" + i, node("var/get"), -640, i * 120.0);
            literal(bp, lookup, read, "var", PinTypes.STRING, "compteur");
            UUID plus = add(bp, lookup, "plus" + i, node("math/add"), -400, i * 120.0 + 40);
            literal(bp, lookup, plus, "b", PinTypes.DOUBLE, 1.0);
            UUID write = add(bp, lookup, "write" + i, node("var/set"), -160, i * 120.0);
            literal(bp, lookup, write, "var", PinTypes.STRING, "compteur");

            link(bp, lookup, read, "value", plus, "a");
            link(bp, lookup, plus, "result", write, "value");
            link(bp, lookup, previous, previousPin, write, "exec_in");
            previous = write;
            previousPin = "exec_out";
        }
        return bp;
    }

    // ------------------------------------------------------------------- outillage

    private static UUID add(Blueprint bp, NodeTypeLookup lookup, String seed,
                            Identifier type, double x, double y) {
        UUID uuid = UUID.nameUUIDFromBytes(
                (bp.id() + "-" + seed).getBytes(StandardCharsets.UTF_8));
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

    private static void apply(Blueprint bp, NodeTypeLookup lookup, EditOperation op) {
        var result = op.apply(bp, lookup);
        if (!result.applied()) {
            throw new AssertionError("banc d'essai refusé : " + result.refusal());
        }
    }

    /** Utilisé par le test pour poser un écran sans passer par le chargeur. */
    static void putScreen(Blueprint bp, NodeTypeLookup lookup, Screen screen) {
        var result = new ScreenOps.AddScreen(screen).apply(bp, lookup);
        if (!result.applied()) {
            throw new AssertionError("écran refusé : " + result.refusal());
        }
    }
}
