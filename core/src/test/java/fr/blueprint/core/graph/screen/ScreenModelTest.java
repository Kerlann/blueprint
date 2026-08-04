package fr.blueprint.core.graph.screen;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le modèle d'écran (story 10.1). Ce qui se teste ici décide de ce qui sera
 * rattrapable plus tard : un champ oublié dans le modèle se paie en migration de tous
 * les écrans déjà créés.
 */
class ScreenModelTest {

    private static ScreenElement button(String name) {
        return ScreenElement.of(name, ElementKind.BUTTON, 0, 0, 80, 20);
    }

    // ------------------------------------------------------------ l'adaptation

    /**
     * <b>Le test qui porte tout l'épic.</b> Une taille fixe ne suit pas la fenêtre ;
     * un pourcentage seul devient illisible en 320×180 et démesuré en 960×540. Les
     * trois mécanismes ensemble, ou rien.
     */
    @Test
    void unePartRelativeBorneeTientDeLaPlusPetiteALaPlusGrandeFenetre() {
        // « la moitié de la largeur, jamais moins de 100, jamais plus de 300 »
        Extent half = Extent.percent(0.5, 100, 300);

        assertEquals(160, half.resolve(Screen.SAFE_WIDTH), 1e-9,
                "en 320 de large : la moitié fait 160, dans les bornes");
        assertEquals(300, half.resolve(960), 1e-9,
                "en 960 : la moitié ferait 480, la borne haute la retient");
        assertEquals(100, half.resolve(120), 1e-9,
                "en 120 : la moitié ferait 60, la borne basse la relève");
    }

    @Test
    void uneTailleFixeIgnoreLaPlaceDisponible() {
        Extent fixed = Extent.of(80);
        assertEquals(80, fixed.resolve(320), 1e-9);
        assertEquals(80, fixed.resolve(960), 1e-9);
    }

    @Test
    void unePartSansBorneHauteNestPasPlafonnee() {
        Extent free = Extent.percent(0.5, 0, 0);
        assertEquals(480, free.resolve(960), 1e-9);
    }

    @Test
    void uneLongueurIncoherenteEstRefuseeALaConstruction() {
        assertThrows(IllegalArgumentException.class,
                () -> new Extent(Extent.Mode.FIXED, Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Extent(Extent.Mode.PERCENT, 1, -1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Extent(Extent.Mode.PERCENT, 1, 100, 50),
                "borne haute sous la basse : la taille n'aurait aucune solution");
    }

    @Test
    void lAncreDonneLaFractionDuParent() {
        assertEquals(0, Anchor.TOP_LEFT.fractionX(), 1e-9);
        assertEquals(0.5, Anchor.CENTER.fractionX(), 1e-9);
        assertEquals(1, Anchor.BOTTOM_RIGHT.fractionY(), 1e-9);
    }

    // ------------------------------------------------------------- l'identité

    @Test
    void unElementSansNomEstRefuse() {
        assertThrows(IllegalArgumentException.class,
                () -> ScreenElement.of("", ElementKind.LABEL, 0, 0, 10, 10));
        assertThrows(IllegalArgumentException.class,
                () -> ScreenElement.of("   ", ElementKind.LABEL, 0, 0, 10, 10));
    }

    @Test
    void leNomEstLIdentiteDansLEcran() {
        Screen screen = Screen.empty("menu")
                .with(button("acheter"))
                .with(button("vendre"));
        assertEquals(2, screen.size());
        assertNotNull(screen.element("acheter"));
        assertNull(screen.element("inconnu"));

        // Le même nom REMPLACE : deux éléments ne peuvent pas le porter.
        Screen again = screen.with(button("acheter"));
        assertEquals(2, again.size());
    }

    // ------------------------------------------------------------ l'imbrication

    /**
     * Masquer le parent masque la page : c'est ce qui rend un menu à onglets
     * praticable, au lieu de douze appels de visibilité.
     */
    @Test
    void lesEnfantsSeRetrouventParLeurParent() {
        Screen screen = Screen.empty("menu")
                .with(ScreenElement.of("page1", ElementKind.PANEL, 0, 0, 200, 100))
                .with(button("ok").withParent("page1"))
                .with(button("annuler").withParent("page1"))
                .with(button("dehors"));

        assertEquals(2, screen.childrenOf("page1").size());
        assertEquals(List.of("page1", "dehors"),
                screen.childrenOf(null).stream().map(ScreenElement::name).toList(),
                "la racine porte le panneau lui-même ET l'élément hors panneau");
    }

    /**
     * Retirer un conteneur retire ses descendants. Les laisser orphelins produirait
     * des éléments dont le parent n'existe plus : invisibles, indélogeables, et
     * comptés dans le plafond.
     */
    @Test
    void retirerUnConteneurEmporteToutSonContenu() {
        Screen screen = Screen.empty("menu")
                .with(ScreenElement.of("page", ElementKind.PANEL, 0, 0, 200, 100))
                .with(ScreenElement.of("bloc", ElementKind.PANEL, 0, 0, 100, 50)
                        .withParent("page"))
                .with(button("profond").withParent("bloc"))
                .with(button("survivant"));

        Screen after = screen.without("page");
        assertEquals(1, after.size());
        assertNotNull(after.element("survivant"));
        assertNull(after.element("profond"), "le petit-enfant part aussi");
    }

    /** Une parenté circulaire ne doit pas faire boucler la suppression à l'infini. */
    @Test
    void uneParenteCirculaireNeBouclePas() {
        Screen screen = Screen.empty("menu")
                .with(ScreenElement.of("a", ElementKind.PANEL, 0, 0, 10, 10).withParent("b"))
                .with(ScreenElement.of("b", ElementKind.PANEL, 0, 0, 10, 10).withParent("a"));

        Screen after = screen.without("a");
        assertEquals(0, after.size(), "le cycle est coupé, les deux partent");
    }

    // ------------------------------------------------------- ordre de dessin

    @Test
    void lOrdreDeDessinSeReordonne() {
        Screen screen = Screen.empty("menu")
                .with(button("bas")).with(button("milieu")).with(button("haut"));
        assertEquals(List.of("bas", "milieu", "haut"),
                List.copyOf(screen.elements().keySet()));

        Screen raised = screen.reordered("bas", 2);
        assertEquals(List.of("milieu", "haut", "bas"),
                List.copyOf(raised.elements().keySet()));
    }

    @Test
    void reordonnerAuDelaDesBornesSArreteAuBord() {
        Screen screen = Screen.empty("menu").with(button("a")).with(button("b"));
        assertEquals(List.of("b", "a"),
                List.copyOf(screen.reordered("a", 99).elements().keySet()));
        assertEquals(List.of("a", "b"),
                List.copyOf(screen.reordered("a", -99).elements().keySet()));
        assertEquals(screen, screen.reordered("inconnu", 1), "un nom absent ne change rien");
    }

    /** Remplacer garde la place : modifier un élément ne le fait pas passer devant. */
    @Test
    void remplacerNeChangePasLOrdre() {
        Screen screen = Screen.empty("menu")
                .with(button("a")).with(button("b")).with(button("c"));
        Screen after = screen.replacing("a", button("a").movedTo(50, 50));

        assertEquals(List.of("a", "b", "c"), List.copyOf(after.elements().keySet()));
        assertEquals(50, after.element("a").x(), 1e-9);
    }

    // ------------------------------------------------------------------ états

    /**
     * Un bouton qui ne change pas au survol passe pour cassé. Un état à zéro retombe
     * sur le normal — un élément inerte se décrit alors sans rien écrire.
     */
    @Test
    void leStyleRepondAuSurvolEtALActivation() {
        ElementStyle style = ElementStyle.DEFAULT;
        int normal = style.backgroundFor(false, false, true);

        assertFalse(normal == style.backgroundFor(true, false, true), "le survol change");
        assertFalse(normal == style.backgroundFor(true, true, true), "la pression aussi");
        assertFalse(normal == style.backgroundFor(false, false, false), "et le grisé");

        ElementStyle inert = new ElementStyle(0xFF102030, 0, 0, 0xFFFFFFFF,
                0, 0, 0, 0, ElementStyle.TextAlign.LEFT, false);
        assertEquals(0xFF102030, inert.backgroundFor(true, true, true),
                "sans état déclaré, tout retombe sur le fond normal");
    }

    @Test
    void unePermissionDeStyleNegativeEstRefusee() {
        assertThrows(IllegalArgumentException.class, () -> new ElementStyle(
                0, 0, -1, 0, 0, 0, 0, 0, ElementStyle.TextAlign.LEFT, false));
    }

    // --------------------------------------------------------------- traduction

    @Test
    void unTexteEstLitteralOuTraduisible() {
        assertFalse(ScreenText.literal("Acheter").translate());
        assertTrue(ScreenText.key("menu.acheter").translate());
        assertTrue(ScreenText.EMPTY.isEmpty());
        assertEquals("", new ScreenText(null, false).value(), "jamais null");
    }

    // ------------------------------------------------------------- types

    @Test
    void seulsLesConteneursOntDesEnfantsEtSeulsCertainsSeCliquent() {
        assertTrue(ElementKind.PANEL.container());
        assertFalse(ElementKind.LABEL.container());
        assertTrue(ElementKind.BUTTON.interactive());
        assertFalse(ElementKind.IMAGE.interactive(),
                "une image ne se clique pas : sinon un HUD pourrait en contenir");
    }

    // ------------------------------------------------------------------ écran

    @Test
    void unEcranEstModalOuPermanent() {
        Screen modal = Screen.empty("menu");
        assertFalse(modal.hud());
        assertTrue(modal.withHud(true).hud());
    }

    @Test
    void unEcranSansNomEstRefuse() {
        assertThrows(IllegalArgumentException.class, () -> Screen.empty(""));
        assertThrows(IllegalArgumentException.class, () -> new Screen(null, false, List.of()));
    }

    @Test
    void lEgaliteCompareLeContenuEtLOrdre() {
        Screen a = Screen.empty("m").with(button("x")).with(button("y"));
        Screen b = Screen.empty("m").with(button("x")).with(button("y"));
        Screen c = Screen.empty("m").with(button("y")).with(button("x"));

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertFalse(a.equals(c), "l'ordre de dessin fait partie de l'écran");
    }

    @Test
    void lesModificationsRendentUnNouvelEcran() {
        Screen original = Screen.empty("menu").with(button("a"));
        Screen modified = original.with(button("b"));

        assertEquals(1, original.size(), "l'original est intact");
        assertEquals(2, modified.size());
    }

    @Test
    void unElementSeModifieChampParChamp() {
        ScreenElement element = button("b")
                .movedTo(10, 20)
                .resized(Extent.of(50), Extent.percent(0.25, 10, 40))
                .renamed("bouton")
                .withText(ScreenText.key("menu.ok"))
                .withTexture(Identifier.withDefaultNamespace("textures/gui/x.png"))
                .withVisible(false)
                .withEnabled(false)
                .withParent("page");

        assertEquals("bouton", element.name());
        assertEquals(10, element.x(), 1e-9);
        assertEquals("page", element.parent());
        assertEquals("menu.ok", element.text().value());
        assertNotNull(element.texture());
        assertFalse(element.visible());
        assertFalse(element.enabled());
        assertEquals(ElementKind.BUTTON, element.kind(), "le type ne change jamais");
    }
}
