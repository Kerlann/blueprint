package fr.blueprint.core.graph.screen;

import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.Diagnostic;
import fr.blueprint.core.graph.DiagnosticCode;
import fr.blueprint.core.graph.GraphValidator;
import fr.blueprint.core.graph.NodeTypeLookup;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Les styles nommés et les règles qui les accompagnent (story 10.10).
 *
 * <p>L'irritant qu'ils règlent : neuf couleurs par élément, à retaper sur chaque bouton.
 * Ce qu'ils doivent prouver ici, c'est qu'un style se change <b>une fois</b> — et que
 * l'oublier ou le renommer ne casse rien.
 */
class ScreenStyleTest {

    private static final NodeTypeLookup LOOKUP = typeId -> null;

    private static final ElementStyle ROUGE = new ElementStyle(
            0xFFAA0000, 0xFF550000, 1, 0xFFFFFFFF,
            0xFFCC0000, 0xFF880000, 0x40555555, 2, ElementStyle.TextAlign.CENTER);
    private static final ElementStyle BLEU = new ElementStyle(
            0xFF0000AA, 0xFF000055, 1, 0xFFFFFFFF,
            0xFF0000CC, 0xFF000088, 0x40555555, 2, ElementStyle.TextAlign.LEFT);

    private static ScreenElement bouton(String name) {
        return ScreenElement.of(name, ElementKind.BUTTON, 0, 0, 60, 20)
                .withStyleName("principal");
    }

    private static Blueprint blueprintOf(Screen screen) {
        Blueprint bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "styles"));
        fr.blueprint.core.graph.GraphLoader.addScreen(bp, screen);
        return bp;
    }

    private static List<DiagnosticCode> codes(Blueprint bp) {
        return GraphValidator.validate(bp, LOOKUP).diagnostics().stream()
                .map(Diagnostic::code).toList();
    }

    /**
     * <b>Le test qui compte.</b> Trois boutons suivent le même style ; le changer une
     * fois les change tous les trois. C'est exactement ce qu'on ne pouvait pas faire.
     */
    @Test
    void changerUnStyleNommeChangeTousLesElementsQuiLeSuivent() {
        Screen screen = new Screen("menu", false,
                List.of(bouton("a"), bouton("b"), bouton("c")),
                Map.of("principal", ROUGE));

        for (String name : List.of("a", "b", "c")) {
            assertEquals(ROUGE, screen.styleOf(screen.element(name)), name);
        }

        Screen repeint = screen.withStyle("principal", BLEU);
        for (String name : List.of("a", "b", "c")) {
            assertEquals(BLEU, repeint.styleOf(repeint.element(name)),
                    name + " : un seul changement, trois éléments repeints");
        }
        assertEquals(ROUGE, screen.styleOf(screen.element("a")),
                "l'écran d'origine est intact : le modèle reste immuable");
    }

    /** Sans nom, l'élément porte son style : c'est le comportement d'avant, inchangé. */
    @Test
    void unElementSansNomGardeSonStyleEnLigne() {
        ScreenElement libre = ScreenElement.of("libre", ElementKind.BUTTON, 0, 0, 60, 20)
                .styled(BLEU);
        Screen screen = new Screen("menu", false, List.of(libre), Map.of("principal", ROUGE));

        assertFalse(libre.followsNamedStyle());
        assertEquals(BLEU, screen.styleOf(libre));
    }

    /**
     * Un style renommé ou supprimé ne doit pas rendre l'écran invalide : l'élément
     * retombe sur son style en ligne et reste dessiné. Un avertissement le signale —
     * une erreur, elle, aurait bloqué l'exécution d'un menu qui s'affiche très bien.
     */
    @Test
    void unStyleIntrouvableAvertitEtRetombeSurLeStyleEnLigne() {
        ScreenElement orphelin = bouton("a").styled(BLEU);
        Screen screen = new Screen("menu", false, List.of(orphelin), Map.of());

        assertEquals(BLEU, screen.styleOf(orphelin), "le style en ligne reprend la main");

        Blueprint bp = blueprintOf(screen);
        var result = GraphValidator.validate(bp, LOOKUP);
        assertTrue(result.diagnostics().stream()
                        .anyMatch(d -> d.code() == DiagnosticCode.SCREEN_STYLE_NOT_FOUND
                                && d.severity() == Diagnostic.Severity.WARNING),
                "un avertissement");
        assertTrue(result.errors().isEmpty(), "et rien qui bloque");
    }

    /** Détacher rend à l'élément son style en ligne, sans y recopier le style nommé. */
    @Test
    void detacherUnElementLuiRendSonStyleEnLigne() {
        ScreenElement suiveur = bouton("a").styled(BLEU);
        Screen screen = new Screen("menu", false, List.of(suiveur), Map.of("principal", ROUGE));
        assertEquals(ROUGE, screen.styleOf(suiveur));

        ScreenElement detache = suiveur.withStyleName("");
        assertFalse(detache.followsNamedStyle());
        assertEquals(BLEU, screen.styleOf(detache));
        assertNotEquals(ROUGE, screen.styleOf(detache));
    }

    /**
     * Les six méthodes de copie de {@code Screen} construisaient l'écran sans sa table
     * de styles : ajouter un élément à un écran stylé l'aurait dépouillé de tous ses
     * styles d'un coup, et l'auteur l'aurait découvert par un menu redevenu gris.
     */
    @Test
    void toutesLesCopiesDEcranEmportentLaTableDeStyles() {
        Screen screen = new Screen("menu", false, List.of(bouton("a"), bouton("b")),
                Map.of("principal", ROUGE));

        List<Screen> copies = List.of(
                screen.with(bouton("c")),
                screen.replacing("a", bouton("a").withText(ScreenText.literal("ok"))),
                screen.without("b"),
                screen.reordered("a", 1),
                screen.renamed("autre_menu"),
                screen.withHud(true));

        for (Screen copy : copies) {
            assertEquals(Map.of("principal", ROUGE), copy.styles(),
                    "la table de styles doit survivre à chaque copie");
        }
    }

    // ------------------------------------------------------- règles de disposition

    /**
     * « S'ajuster au contenu » sur un libellé exigerait de mesurer la police, une mesure
     * qui n'existe que côté client — alors que cette règle tourne aussi côté serveur.
     * Deux mesures divergentes reproduiraient le défaut même que la passe de disposition
     * existe pour empêcher.
     */
    @Test
    void sAjusterAuContenuEstRefuseHorsDunConteneur() {
        Screen screen = new Screen("menu", false, List.of(
                ScreenElement.of("texte", ElementKind.LABEL, 0, 0, 40, 12)
                        .resized(Extent.hug(), Extent.of(12))));

        assertTrue(codes(blueprintOf(screen)).contains(DiagnosticCode.ELEMENT_HUG_NOT_CONTAINER));

        Screen conteneur = new Screen("menu", false, List.of(
                ScreenElement.of("cadre", ElementKind.PANEL, 0, 0, 40, 40)
                        .resized(Extent.hug(), Extent.hug())));
        assertFalse(codes(blueprintOf(conteneur))
                .contains(DiagnosticCode.ELEMENT_HUG_NOT_CONTAINER), "un conteneur, lui, peut");
    }

    /**
     * Une part {@code fill} ne prend que ce qui reste. Quand rien ne reste, elle tombe à
     * zéro : l'élément est toujours là, toujours valide, et invisible. C'est le genre de
     * panne qui fait chercher l'erreur dans le graphe alors qu'elle est dans le cadre.
     */
    @Test
    void unElementEcraseParSaDispositionEstSignale() {
        Screen screen = new Screen("menu", false, List.of(
                ScreenElement.of("colonne", ElementKind.PANEL, 0, 0, 100, 40)
                        .withLayout(LayoutSpec.column(0)),
                ScreenElement.of("gros", ElementKind.LABEL, 0, 0, 100, 40).withParent("colonne"),
                ScreenElement.of("ecrase", ElementKind.LABEL, 0, 0, 100, 10)
                        .withParent("colonne")
                        .resized(Extent.of(100), Extent.fill())));

        var result = GraphValidator.validate(blueprintOf(screen), LOOKUP);
        assertTrue(result.diagnostics().stream()
                        .anyMatch(d -> d.code() == DiagnosticCode.ELEMENT_SQUEEZED
                                && d.severity() == Diagnostic.Severity.WARNING),
                "la place est prise en entier par le premier enfant");
        assertTrue(result.errors().isEmpty(), "un avertissement : la fenêtre du joueur "
                + "est le plus souvent plus grande que la zone garantie");
    }

    /**
     * Une liaison morte est une ERREUR, pas un avertissement (10.7, AC4). Un élément lié
     * à une variable renommée n'affichera jamais rien ; se taire ici reviendrait à
     * laisser l'auteur découvrir en jeu un menu qui reste vide — la panne exacte que la
     * liaison existe pour éviter.
     */
    @Test
    void uneVariableLieeQuiDisparaitEstUneErreurALEdition() {
        Screen screen = new Screen("menu", false, List.of(
                ScreenElement.of("or", ElementKind.LABEL, 0, 0, 60, 20)
                        .withBinding(ElementBinding.text("argent", "Or : %s"))));

        Blueprint sans = blueprintOf(screen);
        var result = GraphValidator.validate(sans, LOOKUP);
        assertTrue(result.errors().stream()
                        .anyMatch(d -> d.code() == DiagnosticCode.SCREEN_BINDING_NOT_FOUND),
                "la variable n'existe pas : " + result.diagnostics());

        // Déclarée, la même liaison ne produit plus rien.
        Blueprint avec = blueprintOf(screen);
        fr.blueprint.core.graph.GraphLoader.addVariable(avec,
                new fr.blueprint.core.graph.Variable("argent",
                        fr.blueprint.api.pin.PinTypes.INT, null,
                        fr.blueprint.core.graph.VarScope.GRAPH, false));
        assertFalse(codes(avec).contains(DiagnosticCode.SCREEN_BINDING_NOT_FOUND));
    }

    /**
     * La taille minimale d'un enfant RANGÉ se décide entre frères — la remontée par
     * élément, qui ne connaît que le parent, en donnerait une valeur fictive. La juger
     * là-dessus rendrait impossible de poser le deuxième enfant d'une colonne serrée.
     */
    @Test
    void unEnfantRangeNestPasJugeSurUneTailleQuIlNAuraPas() {
        Screen screen = new Screen("menu", false, List.of(
                ScreenElement.of("colonne", ElementKind.PANEL, 0, 0, 100, 90)
                        .withLayout(LayoutSpec.column(4)),
                ScreenElement.of("a", ElementKind.BUTTON, 0, 0, 100, 20).withParent("colonne")
                        .resized(Extent.fill(), Extent.fill()),
                ScreenElement.of("b", ElementKind.BUTTON, 0, 0, 100, 20).withParent("colonne")
                        .resized(Extent.fill(), Extent.fill()),
                ScreenElement.of("c", ElementKind.BUTTON, 0, 0, 100, 20).withParent("colonne")
                        .resized(Extent.fill(), Extent.fill())));

        var result = GraphValidator.validate(blueprintOf(screen), LOOKUP);
        assertTrue(result.errors().isEmpty(),
                () -> "trois boutons qui se partagent une colonne : rien à refuser, "
                        + result.errors());
    }
}
