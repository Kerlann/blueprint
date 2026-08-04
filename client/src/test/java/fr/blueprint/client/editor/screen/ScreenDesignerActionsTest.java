package fr.blueprint.client.editor.screen;

import fr.blueprint.client.editor.history.UndoStack;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.NodeTypeLookup;
import fr.blueprint.core.graph.ScreenOps;
import fr.blueprint.core.graph.screen.ElementBinding;
import fr.blueprint.core.graph.screen.ElementKind;
import fr.blueprint.core.graph.screen.ElementOptions;
import fr.blueprint.core.graph.screen.LayoutSpec;
import fr.blueprint.core.graph.screen.Screen;
import fr.blueprint.core.graph.screen.ScreenElement;
import fr.blueprint.core.graph.screen.ScreenLayout;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Les actions du concepteur qui ne passent pas par la souris : flèches, alignement,
 * répartition, presse-papiers, gestion des écrans.
 *
 * <p>Ce sont elles qui font la différence entre « on peut poser des boutons » et « on
 * peut soigner un menu » — et elles sont toutes pures, donc toutes testables.
 */
class ScreenDesignerActionsTest {

    private static final NodeTypeLookup LOOKUP = typeId -> null;

    private Blueprint bp;
    private UndoStack history;
    private ScreenCanvasController controller;

    @BeforeEach
    void setUp() {
        bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "ui"));
        history = new UndoStack();
        new ScreenOps.AddScreen(Screen.empty("menu")).apply(bp, LOOKUP);
        controller = new ScreenCanvasController(bp, LOOKUP, history, "menu");
    }

    private void put(ScreenElement element) {
        assertTrue(new ScreenOps.AddElement("menu", element).apply(bp, LOOKUP).applied(),
                () -> "refusé : " + element.name());
    }

    private ScreenElement element(String name) {
        return bp.screen("menu").element(name);
    }

    // ------------------------------------------------------------------ flèches

    /**
     * La souris ne descend pas sous le pixel de la surface ; les flèches, si. C'est le
     * seul moyen de poser un élément EXACTEMENT à trois unités du bord.
     */
    @Test
    void lesFlechesDeplacentALUniteSansAccroche() {
        put(ScreenElement.of("a", ElementKind.LABEL, 10, 10, 40, 20));
        controller.selection().selectAll(List.of("a"), false);

        assertTrue(controller.nudgeSelection(1, 0));
        assertEquals(11, element("a").x(), 1e-9, "une unité, pas un pas de grille");
        controller.nudgeSelection(0, -1);
        assertEquals(9, element("a").y(), 1e-9);
    }

    @Test
    void unGroupeSeDecaleEnsembleSansDoublerLesEnfants() {
        put(ScreenElement.of("cadre", ElementKind.PANEL, 10, 10, 120, 80));
        put(ScreenElement.of("bouton", ElementKind.BUTTON, 5, 5, 60, 20).withParent("cadre"));
        controller.selection().selectAll(List.of("cadre", "bouton"), false);

        controller.nudgeSelection(4, 0);
        assertEquals(14, element("cadre").x(), 1e-9);
        assertEquals(5, element("bouton").x(), 1e-9, "l'enfant a déjà suivi son parent");
    }

    @Test
    void sansSelectionLesFlechesNeFontRien() {
        put(ScreenElement.of("a", ElementKind.LABEL, 10, 10, 40, 20));
        assertFalse(controller.nudgeSelection(1, 0));
        assertEquals(10, element("a").x(), 1e-9);
    }

    // --------------------------------------------------------------- alignement

    @Test
    void alignerAGaucheColleAuPlusAGauche() {
        put(ScreenElement.of("a", ElementKind.LABEL, 40, 10, 40, 10));
        put(ScreenElement.of("b", ElementKind.LABEL, 12, 30, 60, 10));
        put(ScreenElement.of("c", ElementKind.LABEL, 80, 50, 20, 10));
        controller.selection().selectAll(List.of("a", "b", "c"), false);

        assertTrue(controller.alignSelection(ScreenCanvasController.Align.LEFT));
        for (String name : List.of("a", "b", "c")) {
            assertEquals(12, controller.rectOf(name).x(), 1e-9, name);
        }
    }

    @Test
    void alignerADroiteAligneLesBordsDroits() {
        put(ScreenElement.of("a", ElementKind.LABEL, 10, 10, 40, 10));
        put(ScreenElement.of("b", ElementKind.LABEL, 10, 30, 100, 10));
        controller.selection().selectAll(List.of("a", "b"), false);

        assertTrue(controller.alignSelection(ScreenCanvasController.Align.RIGHT));
        assertEquals(110, controller.rectOf("a").right(), 1e-9);
        assertEquals(110, controller.rectOf("b").right(), 1e-9);
    }

    @Test
    void centrerAligneLesCentresEtNonLesBords() {
        put(ScreenElement.of("large", ElementKind.LABEL, 10, 10, 100, 10));
        put(ScreenElement.of("etroit", ElementKind.LABEL, 10, 30, 20, 10));
        controller.selection().selectAll(List.of("large", "etroit"), false);

        assertTrue(controller.alignSelection(ScreenCanvasController.Align.CENTER_X));
        assertEquals(controller.rectOf("large").x() + 50,
                controller.rectOf("etroit").x() + 10, 1e-9);
    }

    /**
     * Un SEUL élément s'aligne sur son <b>parent</b> — l'écran s'il n'en a pas.
     *
     * <p>Les six raccourcis d'alignement étaient muets tant qu'on n'avait pas sélectionné
     * deux éléments : aligner un élément sur lui-même ne fait rien, et c'est ce qu'ils
     * calculaient. Or « centre ce bouton dans son cadre » est le geste le plus courant
     * d'une mise en page, et il fallait le poser à la coordonnée près.
     */
    @Test
    void alignerUnSeulElementLeCadreSurSonParent() {
        put(ScreenElement.of("cadre", ElementKind.PANEL, 40, 20, 200, 100));
        put(ScreenElement.of("bouton", ElementKind.BUTTON, 5, 5, 60, 20)
                .withParent("cadre"));
        controller.selection().selectAll(List.of("bouton"), false);

        assertTrue(controller.alignSelection(ScreenCanvasController.Align.CENTER_X));
        assertEquals(40 + (200 - 60) / 2.0, controller.rectOf("bouton").x(), 1e-9,
                "centré dans son CADRE, pas dans l'écran");

        assertTrue(controller.alignSelection(ScreenCanvasController.Align.BOTTOM));
        assertEquals(20 + 100, controller.rectOf("bouton").bottom(), 1e-9,
                "collé au bas de son cadre");
    }

    /** Sans parent, la référence est l'écran simulé : « centrer » veut encore dire quelque chose. */
    @Test
    void alignerUnElementRacineLeCadreSurLEcran() {
        put(ScreenElement.of("a", ElementKind.LABEL, 10, 10, 40, 10));
        controller.selection().selectAll(List.of("a"), false);

        assertTrue(controller.alignSelection(ScreenCanvasController.Align.RIGHT));
        assertEquals(controller.viewportWidth(), controller.rectOf("a").right(), 1e-9);
    }

    /** L'axe non concerné ne bouge pas : aligner à gauche ne remonte personne. */
    @Test
    void alignerNeToucheQuUnAxe() {
        put(ScreenElement.of("a", ElementKind.LABEL, 40, 10, 40, 10));
        put(ScreenElement.of("b", ElementKind.LABEL, 12, 90, 40, 10));
        controller.selection().selectAll(List.of("a", "b"), false);

        controller.alignSelection(ScreenCanvasController.Align.LEFT);
        assertEquals(10, controller.rectOf("a").y(), 1e-9);
        assertEquals(90, controller.rectOf("b").y(), 1e-9);
    }

    // -------------------------------------------------------------- répartition

    @Test
    void repartirEgaliseLesIntervalles() {
        put(ScreenElement.of("a", ElementKind.LABEL, 10, 0, 40, 10));
        put(ScreenElement.of("b", ElementKind.LABEL, 10, 12, 40, 10));
        put(ScreenElement.of("c", ElementKind.LABEL, 10, 100, 40, 10));
        controller.selection().selectAll(List.of("a", "b", "c"), false);

        assertTrue(controller.distributeSelection(true));
        assertEquals(0, controller.rectOf("a").y(), 1e-9, "l'extrême ne bouge pas");
        assertEquals(50, controller.rectOf("b").y(), 1e-9, "au milieu des deux");
        assertEquals(100, controller.rectOf("c").y(), 1e-9, "l'autre extrême non plus");
    }

    /** L'ordre de SÉLECTION n'a pas d'importance : c'est la position qui décide. */
    @Test
    void repartirNeDependPasDeLOrdreDeSelection() {
        put(ScreenElement.of("a", ElementKind.LABEL, 0, 0, 20, 10));
        put(ScreenElement.of("b", ElementKind.LABEL, 90, 0, 20, 10));
        put(ScreenElement.of("c", ElementKind.LABEL, 30, 0, 20, 10));
        controller.selection().selectAll(List.of("b", "c", "a"), false);

        assertTrue(controller.distributeSelection(false));
        assertEquals(45, controller.rectOf("c").x(), 1e-9, "le milieu géométrique");
    }

    @Test
    void repartirExigeTroisElements() {
        put(ScreenElement.of("a", ElementKind.LABEL, 0, 0, 20, 10));
        put(ScreenElement.of("b", ElementKind.LABEL, 90, 0, 20, 10));
        controller.selection().selectAll(List.of("a", "b"), false);
        assertFalse(controller.distributeSelection(false), "deux points sont déjà répartis");
    }

    // ------------------------------------------------------ visibilité groupée

    /**
     * Une sélection mixte bascule vers une cible COMMUNE. Sans cela, chacun basculerait
     * de son côté et l'écran ne changerait pas — le geste paraîtrait sans effet.
     */
    @Test
    void basculerLaVisibiliteChoisitUneCibleCommune() {
        put(ScreenElement.of("a", ElementKind.LABEL, 0, 0, 20, 10));
        put(ScreenElement.of("b", ElementKind.LABEL, 0, 20, 20, 10).withVisible(false));
        controller.selection().selectAll(List.of("a", "b"), false);

        assertTrue(controller.toggleSelection(true));
        assertFalse(element("a").visible(), "« a » était visible : tout passe à masqué");
        assertFalse(element("b").visible());

        controller.toggleSelection(true);
        assertTrue(element("a").visible());
        assertTrue(element("b").visible());
    }

    @Test
    void basculerLActivationSuitLaMemeRegle() {
        put(ScreenElement.of("ok", ElementKind.BUTTON, 0, 0, 40, 20));
        controller.selection().selectAll(List.of("ok"), false);

        assertTrue(controller.toggleSelection(false));
        assertFalse(element("ok").enabled());
    }

    // ----------------------------------------------------------- presse-papiers

    @Test
    void copierPuisCollerCreeUneCopieDecalee() {
        put(ScreenElement.of("a", ElementKind.LABEL, 10, 10, 40, 10));
        controller.selection().selectAll(List.of("a"), false);

        assertTrue(controller.copySelection());
        assertTrue(controller.paste());
        assertEquals(2, bp.screen("menu").size());
        assertNotNull(element("a_2"));
        assertEquals(12, element("a_2").x(), 1e-9, "décalée, pour ne pas se superposer");
        assertTrue(controller.selection().isSelected("a_2"), "et sélectionnée");
    }

    /**
     * <b>Le test qui compte.</b> Coller dans un AUTRE écran est tout l'intérêt :
     * recomposer une page à partir d'une autre sans tout redessiner.
     */
    @Test
    void collerDansUnAutreEcranFonctionne() {
        put(ScreenElement.of("cadre", ElementKind.PANEL, 10, 10, 100, 60));
        put(ScreenElement.of("ok", ElementKind.BUTTON, 4, 4, 40, 20).withParent("cadre"));
        controller.selection().selectAll(List.of("cadre", "ok"), false);
        controller.copySelection();

        assertNotNull(controller.addScreen("page2"));
        assertTrue(controller.paste());

        Screen page2 = bp.screen("page2");
        assertEquals(2, page2.size());
        assertEquals("cadre", page2.element("ok").parent(),
                "l'enfant est rattaché à la copie du cadre, pas à l'original");
    }

    /**
     * Un parent absent de l'écran d'arrivée : l'élément est collé à la racine plutôt
     * que refusé. Perdre le geste vaudrait moins que d'avoir à le rattacher soi-même.
     */
    @Test
    void unParentAbsentColleALaRacine() {
        put(ScreenElement.of("cadre", ElementKind.PANEL, 10, 10, 100, 60));
        put(ScreenElement.of("ok", ElementKind.BUTTON, 4, 4, 40, 20).withParent("cadre"));
        controller.selection().selectAll(List.of("ok"), false);
        controller.copySelection();

        controller.addScreen("page2");
        assertTrue(controller.paste());
        assertNull(bp.screen("page2").element("ok").parent());
    }

    /**
     * Copier une sélection vide ne <b>vide pas</b> le presse-papiers : c'est ce que
     * fait tout éditeur, et l'inverse ferait perdre ce qu'on venait de copier sur un
     * clic malheureux dans le vide.
     */
    @Test
    void copierRienNEffacePasCeQuOnAvaitCopie() {
        put(ScreenElement.of("a", ElementKind.LABEL, 10, 10, 40, 10));
        controller.selection().selectAll(List.of("a"), false);
        assertTrue(controller.copySelection());

        controller.selection().clear();
        assertFalse(controller.copySelection(), "rien à copier");

        assertTrue(controller.paste(), "et « a » est toujours dans le presse-papiers");
        assertNotNull(element("a_2"));
    }

    // ------------------------------------------------------- gestion des écrans

    @Test
    void ajouterUnEcranBasculeDessusEtEviteLesDoublons() {
        assertEquals("page", controller.addScreen("page"));
        assertEquals("page", controller.screenName());
        assertEquals("page_2", controller.addScreen("page"));
    }

    @Test
    void supprimerLEcranCourantBasculeSurCeQuiReste() {
        controller.addScreen("page");
        assertTrue(controller.removeCurrentScreen());
        assertNull(bp.screen("page"));
        assertEquals("menu", controller.screenName());
    }

    @Test
    void renommerUnEcranEmporteSonContenu() {
        put(ScreenElement.of("a", ElementKind.LABEL, 10, 10, 40, 10));

        assertTrue(controller.renameCurrentScreen("boutique"));
        assertNull(bp.screen("menu"));
        assertEquals("boutique", controller.screenName());
        assertNotNull(bp.screen("boutique").element("a"));
    }

    @Test
    void renommerVersUnNomPrisEstRefuse() {
        controller.addScreen("page");
        controller.setScreenName("menu");
        assertFalse(controller.renameCurrentScreen("page"));
        assertFalse(controller.renameCurrentScreen("  "));
        assertNotNull(bp.screen("menu"), "et l'écran d'origine est intact");
    }

    /**
     * Un HUD ne capte pas la souris : un écran qui contient un bouton ne peut pas le
     * devenir. Le refus vient de `ScreenRules`, pas d'une règle recopiée ici.
     */
    @Test
    void passerEnHudEstRefuseSiUnBoutonExiste() {
        put(ScreenElement.of("ok", ElementKind.BUTTON, 10, 10, 40, 20));
        assertFalse(controller.toggleHud());
        assertFalse(bp.screen("menu").hud());
        assertNotNull(controller.takeRefusal(), "et le refus est expliqué");
    }

    @Test
    void passerEnHudMarcheSansElementInteractif() {
        put(ScreenElement.of("texte", ElementKind.LABEL, 10, 10, 40, 10));
        assertTrue(controller.toggleHud());
        assertTrue(bp.screen("menu").hud());
        assertTrue(controller.toggleHud());
        assertFalse(bp.screen("menu").hud(), "et se défait");
    }

    // ------------------------------------------------- taille de fenêtre simulée

    /**
     * <b>Le test qui compte.</b> Une ancre ne veut rien dire tant qu'on ne la voit pas
     * bouger. Concevoir toujours à 320×180 revenait à écrire une mise en page adaptative
     * sans jamais redimensionner la fenêtre : on découvrait le résultat en jeu.
     */
    @Test
    void changerDeFenetreDeplaceCeQuiEstAncreADroite() {
        put(new ScreenElement("coin", ElementKind.LABEL, null,
                fr.blueprint.core.graph.screen.Anchor.TOP_RIGHT, -4, 4,
                fr.blueprint.core.graph.screen.Extent.of(60),
                fr.blueprint.core.graph.screen.Extent.of(10),
                fr.blueprint.core.graph.screen.ScreenText.EMPTY,
                fr.blueprint.core.graph.screen.ScreenText.EMPTY, null,
                fr.blueprint.core.graph.screen.ElementStyle.DEFAULT, "", LayoutSpec.ABSOLUTE, ElementBinding.NONE, ElementOptions.NONE, true, true));

        assertEquals(316, controller.rectOf("coin").right(), 1e-9, "320 − 4");

        controller.setViewport(ScreenCanvasController.Viewport.LARGE);
        assertEquals(636, controller.rectOf("coin").right(), 1e-9,
                "640 − 4 : il suit le bord, ce qui est TOUT l'intérêt de l'ancre");
        assertEquals(-4, element("coin").x(), 1e-9,
                "et le décalage ÉCRIT n'a pas bougé : c'est une simulation");
    }

    /** Une taille en pourcentage suit la fenêtre, une taille fixe non. */
    @Test
    void unPourcentageSuitLaFenetreEtPasUneTailleFixe() {
        put(ScreenElement.of("fixe", ElementKind.LABEL, 0, 0, 100, 10));
        put(ScreenElement.of("relatif", ElementKind.LABEL, 0, 20, 10, 10)
                .resized(fr.blueprint.core.graph.screen.Extent.percent(0.5, 0, 0),
                        fr.blueprint.core.graph.screen.Extent.of(10)));

        assertEquals(160, controller.rectOf("relatif").width(), 1e-9);
        controller.setViewport(ScreenCanvasController.Viewport.HUGE);
        assertEquals(480, controller.rectOf("relatif").width(), 1e-9, "la moitié de 960");
        assertEquals(100, controller.rectOf("fixe").width(), 1e-9, "le fixe ne bouge pas");
    }

    /** Le canevas ne descend jamais sous la zone garantie : elle est le plancher. */
    @Test
    void leCanevasNeDescendPasSousLaZoneGarantie() {
        controller.setViewport(100, 50);
        assertEquals(Screen.SAFE_WIDTH, controller.viewportWidth(), 1e-9);
        assertEquals(Screen.SAFE_HEIGHT, controller.viewportHeight(), 1e-9);
    }

    /**
     * Le hit-test suit le canevas. Sans cela, on cliquerait à côté de ce qu'on voit dès
     * qu'on change de taille — le défaut le plus déroutant possible.
     */
    @Test
    void leClicSuitLaTailleSimulee() {
        put(new ScreenElement("coin", ElementKind.BUTTON, null,
                fr.blueprint.core.graph.screen.Anchor.TOP_RIGHT, -4, 4,
                fr.blueprint.core.graph.screen.Extent.of(60),
                fr.blueprint.core.graph.screen.Extent.of(20),
                fr.blueprint.core.graph.screen.ScreenText.EMPTY,
                fr.blueprint.core.graph.screen.ScreenText.EMPTY, null,
                fr.blueprint.core.graph.screen.ElementStyle.DEFAULT, "", LayoutSpec.ABSOLUTE, ElementBinding.NONE, ElementOptions.NONE, true, true));
        controller.setViewport(ScreenCanvasController.Viewport.LARGE);

        assertEquals("coin", controller.hitTest(600, 10));
        assertNull(controller.hitTest(300, 10), "plus là où il était en 320 de large");
    }

    // ------------------------------------------------------- annulation partagée

    /** Chaque action est UNE entrée d'annulation, pas une par élément touché. */
    @Test
    void chaqueActionNeFaitQuUneEntreeDAnnulation() {
        put(ScreenElement.of("a", ElementKind.LABEL, 40, 10, 40, 10));
        put(ScreenElement.of("b", ElementKind.LABEL, 12, 30, 40, 10));
        put(ScreenElement.of("c", ElementKind.LABEL, 80, 50, 40, 10));
        controller.selection().selectAll(List.of("a", "b", "c"), false);

        controller.alignSelection(ScreenCanvasController.Align.LEFT);
        assertTrue(history.undo(bp, LOOKUP));
        assertEquals(40, controller.rectOf("a").x(), 1e-9, "un seul Ctrl+Z rend tout");
        assertEquals(80, controller.rectOf("c").x(), 1e-9);
    }
}
