package fr.blueprint.client.editor.screen;

import fr.blueprint.client.editor.history.UndoStack;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.NodeTypeLookup;
import fr.blueprint.core.graph.ScreenOps;
import fr.blueprint.core.graph.screen.ElementKind;
import fr.blueprint.core.graph.screen.Extent;
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
 * Le concepteur d'écrans (story 10.2), testé sans client : poser, saisir, déplacer,
 * redimensionner, ordonner. Tout est en unités d'interface dans la surface 320×180.
 */
class ScreenCanvasControllerTest {

    private static final NodeTypeLookup LOOKUP = typeId -> null;

    private Blueprint bp;
    private UndoStack history;
    private ScreenCanvasController controller;

    @BeforeEach
    void setUp() {
        bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "concepteur"));
        history = new UndoStack();
        new ScreenOps.AddScreen(Screen.empty("menu")).apply(bp, LOOKUP);
        controller = new ScreenCanvasController(bp, LOOKUP, history, "menu");
    }

    private void put(ScreenElement element) {
        assertTrue(new ScreenOps.AddElement("menu", element).apply(bp, LOOKUP).applied(),
                () -> "mise en place refusée : " + element.name());
    }

    private ScreenElement element(String name) {
        return bp.screen("menu").element(name);
    }

    private void dragFrom(double x, double y, double toX, double toY) {
        controller.press(x, y, false);
        controller.drag(toX, toY);
        controller.release();
    }

    // ------------------------------------------------------------------ hit-test

    @Test
    void leClicAttrapeLElementDuDessus() {
        put(ScreenElement.of("dessous", ElementKind.LABEL, 10, 10, 40, 20));
        put(ScreenElement.of("dessus", ElementKind.LABEL, 20, 15, 40, 20));

        assertEquals("dessus", controller.hitTest(30, 20), "le dernier dessiné gagne");
        assertEquals("dessous", controller.hitTest(12, 12));
        assertNull(controller.hitTest(300, 170));
    }

    @Test
    void leClicDansLeVideVideLaSelection() {
        put(ScreenElement.of("a", ElementKind.LABEL, 10, 10, 40, 20));
        controller.press(20, 20, false);
        controller.release();
        assertEquals(1, controller.selection().size());

        controller.press(300, 170, false);
        controller.release();
        assertTrue(controller.selection().isEmpty());
    }

    // --------------------------------------------------------------- déplacement

    @Test
    void glisserDeplaceLElementSaisi() {
        put(ScreenElement.of("a", ElementKind.LABEL, 10, 10, 40, 20));
        dragFrom(20, 20, 60, 50);

        assertEquals(50, element("a").x(), 1e-9, "10 + (60 − 20), accroché à la grille");
        assertEquals(40, element("a").y(), 1e-9);
    }

    /**
     * <b>Le test qui compte.</b> Un enfant est positionné DANS son parent : déplacer
     * les deux appliquerait le décalage deux fois et l'enfant s'échapperait de son
     * cadre, un peu plus à chaque geste répété.
     */
    @Test
    void deplacerUnGroupeNAppliquePasDeuxFoisLeDecalageAuxEnfants() {
        put(ScreenElement.of("cadre", ElementKind.PANEL, 10, 10, 120, 80));
        put(ScreenElement.of("bouton", ElementKind.BUTTON, 5, 5, 60, 20).withParent("cadre"));

        controller.selection().selectAll(List.of("cadre", "bouton"), false);
        controller.press(20, 20, true);   // additif : la sélection est conservée
        controller.drag(40, 20);
        controller.release();

        assertEquals(30, element("cadre").x(), 1e-9, "le cadre suit la souris");
        assertEquals(5, element("bouton").x(), 1e-9,
                "l'enfant garde son décalage : il a déjà suivi son parent");
    }

    @Test
    void unElementNeSortPasDeSonParent() {
        put(ScreenElement.of("cadre", ElementKind.PANEL, 10, 10, 100, 60));
        put(ScreenElement.of("bouton", ElementKind.BUTTON, 4, 4, 40, 20).withParent("cadre"));

        dragFrom(20, 20, 300, 160);

        ScreenLayout.Rect rect = controller.rectOf("bouton");
        assertTrue(rect.right() <= 110 + 1e-9, "bord droit dans le cadre : " + rect);
        assertTrue(rect.bottom() <= 70 + 1e-9, "bord bas dans le cadre : " + rect);
    }

    /** Les guides accrochent aux bords des voisins ; l'écart résiduel disparaît. */
    @Test
    void lesGuidesAccrochentAuxVoisins() {
        put(ScreenElement.of("repere", ElementKind.LABEL, 100, 40, 40, 20));
        put(ScreenElement.of("mobile", ElementKind.LABEL, 10, 100, 40, 20));

        controller.press(20, 110, false);
        controller.drag(111, 110);      // viserait x = 101 : à une unité du repère
        assertEquals(100, controller.rectOf("mobile").x(), 1e-9, "accroché sur le bord");
        assertFalse(controller.guides().isEmpty(), "et la ligne est montrée");
        controller.release();
        assertTrue(controller.guides().isEmpty(), "la ligne disparaît au relâchement");
    }

    // ---------------------------------------------------------- redimensionnement

    @Test
    void laPoigneeSudEstAgrandit() {
        put(ScreenElement.of("a", ElementKind.LABEL, 10, 10, 40, 20));
        controller.press(20, 20, false);
        controller.release();

        assertEquals(ScreenCanvasController.Handle.SE, controller.handleAt(50, 30));
        controller.press(50, 30, false);
        controller.drag(90, 60);
        controller.release();

        ScreenLayout.Rect rect = controller.rectOf("a");
        assertEquals(80, rect.width(), 1e-9);
        assertEquals(50, rect.height(), 1e-9);
        assertEquals(10, rect.x(), 1e-9, "le coin opposé ne bouge pas");
    }

    @Test
    void laPoigneeNordOuestDeplaceLeCoinSansBougerLOppose() {
        put(ScreenElement.of("a", ElementKind.LABEL, 20, 20, 60, 40));
        controller.press(40, 40, false);
        controller.release();

        controller.press(20, 20, false);
        controller.drag(40, 30);
        controller.release();

        ScreenLayout.Rect rect = controller.rectOf("a");
        assertEquals(40, rect.x(), 1e-9);
        assertEquals(30, rect.y(), 1e-9);
        assertEquals(80, rect.right(), 1e-9, "le bord droit est resté");
        assertEquals(60, rect.bottom(), 1e-9, "le bord bas aussi");
    }

    @Test
    void uneArreteNeChangeQuUnAxe() {
        put(ScreenElement.of("a", ElementKind.LABEL, 20, 20, 60, 40));
        controller.press(40, 40, false);
        controller.release();

        assertEquals(ScreenCanvasController.Handle.E, controller.handleAt(80, 40));
        controller.press(80, 40, false);
        controller.drag(120, 100);
        controller.release();

        ScreenLayout.Rect rect = controller.rectOf("a");
        assertEquals(100, rect.width(), 1e-9);
        assertEquals(40, rect.height(), 1e-9, "la hauteur n'a pas bougé");
    }

    @Test
    void onNeDescendPasSousLaTailleMinimale() {
        put(ScreenElement.of("a", ElementKind.LABEL, 20, 20, 60, 40));
        controller.press(40, 40, false);
        controller.release();

        controller.press(80, 60, false);   // poignée SE
        controller.drag(0, 0);
        controller.release();

        ScreenLayout.Rect rect = controller.rectOf("a");
        assertTrue(rect.width() >= ScreenElement.MIN_SIZE, "largeur : " + rect.width());
        assertTrue(rect.height() >= ScreenElement.MIN_SIZE, "hauteur : " + rect.height());
    }

    /**
     * Une taille en pourcentage le reste après redimensionnement. La convertir en
     * unités fixes détruirait sans le dire l'adaptation choisie par l'auteur, et son
     * menu cesserait de suivre la fenêtre.
     */
    @Test
    void unRedimensionnementPreserveLaNatureRelativeDeLaTaille() {
        put(new ScreenElement("a", ElementKind.PANEL, null,
                fr.blueprint.core.graph.screen.Anchor.TOP_LEFT, 0, 0,
                Extent.percent(0.5, 0, 0), Extent.of(40),
                fr.blueprint.core.graph.screen.ScreenText.EMPTY, null,
                fr.blueprint.core.graph.screen.ElementStyle.DEFAULT, true, true));
        controller.press(80, 20, false);
        controller.release();

        controller.press(160, 40, false);   // poignée SE
        controller.drag(80, 40);
        controller.release();

        assertTrue(element("a").width().relative(), "toujours un pourcentage");
        assertEquals(0.25, element("a").width().value(), 1e-9, "80 / 320");
    }

    // ------------------------------------------------------------------- actions

    @Test
    void poserDansUnPanneauYRattacheLElement() {
        put(ScreenElement.of("cadre", ElementKind.PANEL, 10, 10, 150, 100));
        String name = controller.addElement(ElementKind.BUTTON, 40, 40);

        assertNotNull(name);
        assertEquals("cadre", element(name).parent());
        assertEquals(name, controller.selection().ids().iterator().next(),
                "et l'élément posé est sélectionné : on l'ajuste dans la foulée");
    }

    @Test
    void lesNomsPosesNeSeMarchentPasDessus() {
        assertEquals("button", controller.addElement(ElementKind.BUTTON, 10, 10));
        assertEquals("button_2", controller.addElement(ElementKind.BUTTON, 60, 10));
        assertEquals("button_3", controller.addElement(ElementKind.BUTTON, 120, 10));
    }

    @Test
    void supprimerEmporteLesDescendants() {
        put(ScreenElement.of("cadre", ElementKind.PANEL, 10, 10, 150, 100));
        put(ScreenElement.of("bouton", ElementKind.BUTTON, 5, 5, 60, 20).withParent("cadre"));

        controller.selection().selectAll(List.of("cadre"), false);
        assertTrue(controller.deleteSelection());
        assertEquals(0, bp.screen("menu").size());
        assertTrue(controller.selection().isEmpty());
    }

    /**
     * Dupliquer un cadre AVEC son bouton doit produire un cadre copié qui contient sa
     * propre copie du bouton. Rattaché à l'original, on obtiendrait deux boutons dans
     * le cadre de départ et un cadre copié vide.
     */
    @Test
    void dupliquerUnGroupeRattacheLesEnfantsALaCopie() {
        put(ScreenElement.of("cadre", ElementKind.PANEL, 10, 10, 150, 100));
        put(ScreenElement.of("bouton", ElementKind.BUTTON, 5, 5, 60, 20).withParent("cadre"));

        controller.selection().selectAll(List.of("bouton", "cadre"), false);
        assertTrue(controller.duplicateSelection());

        Screen screen = bp.screen("menu");
        assertEquals(4, screen.size());
        assertEquals("cadre_2", screen.element("bouton_2").parent(),
                "l'enfant copié suit le cadre copié");
        assertEquals(1, screen.childrenOf("cadre").size(), "et l'original n'a pas grossi");
    }

    @Test
    void reordonnerChangeLOrdreDeDessin() {
        put(ScreenElement.of("a", ElementKind.LABEL, 10, 10, 40, 20));
        put(ScreenElement.of("b", ElementKind.LABEL, 10, 40, 40, 20));

        controller.selection().selectAll(List.of("a"), false);
        assertTrue(controller.reorderSelection(1));
        assertEquals(List.of("b", "a"),
                List.copyOf(bp.screen("menu").elements().keySet()));
    }

    @Test
    void leNomEstVerifieEnDirect() {
        put(ScreenElement.of("a", ElementKind.LABEL, 10, 10, 40, 20));
        put(ScreenElement.of("b", ElementKind.LABEL, 10, 40, 40, 20));

        assertFalse(controller.nameAvailable("b", "a"), "déjà pris");
        assertTrue(controller.nameAvailable("a", "a"), "son propre nom reste valide");
        assertTrue(controller.nameAvailable("c", "a"));
        assertFalse(controller.nameAvailable("  ", "a"), "un nom vide n'est pas un nom");
    }

    @Test
    void renommerSuitDansLaSelection() {
        put(ScreenElement.of("a", ElementKind.LABEL, 10, 10, 40, 20));
        controller.selection().selectAll(List.of("a"), false);

        assertTrue(controller.rename("a", "titre"));
        assertTrue(controller.selection().isSelected("titre"));
        assertFalse(controller.selection().isSelected("a"),
                "l'ancien nom ne reste pas sélectionné : les actions porteraient dans le vide");
    }

    // ------------------------------------------------------- annulation partagée

    /**
     * AC6 : un seul {@code Ctrl+Z}. La pile est celle du canevas de nœuds, et un
     * glisser complet — quarante images, quarante {@code SetElement} — ne fait
     * qu'une entrée.
     */
    @Test
    void unGlisserNeFaitQuUneEntreeDAnnulation() {
        put(ScreenElement.of("a", ElementKind.LABEL, 10, 10, 40, 20));

        controller.press(20, 20, false);
        for (int i = 0; i < 20; i++) {
            controller.drag(20 + i * 2, 20 + i);
        }
        controller.release();
        assertEquals(48, element("a").x(), 1e-9, "10 + (58 − 20)");

        assertTrue(history.undo(bp, LOOKUP));
        assertEquals(10, element("a").x(), 1e-9, "un seul undo ramène au départ");
        assertFalse(history.canUndo(), "et il n'y avait bien qu'une entrée");
    }

    /**
     * Un clic qui ne modifie rien ne doit pas laisser le geste ouvert : les éditions
     * suivantes seraient avalées dans la même entrée d'annulation. C'est le défaut
     * exact trouvé en story 5.12 sur le clic d'un fil.
     */
    @Test
    void selectionnerNeFusionnePasLesAnnulationsSuivantes() {
        put(ScreenElement.of("a", ElementKind.LABEL, 10, 10, 40, 20));
        put(ScreenElement.of("b", ElementKind.LABEL, 10, 60, 40, 20));

        controller.press(300, 170, false);   // clic dans le vide
        controller.release();

        dragFrom(20, 20, 40, 20);
        dragFrom(20, 70, 40, 70);

        assertTrue(history.undo(bp, LOOKUP));
        assertEquals(10, element("b").x(), 1e-9, "le second glisser est défait seul");
        assertEquals(30, element("a").x(), 1e-9, "le premier tient toujours");
    }

    @Test
    void changerDEcranAbandonneLeGesteEnCours() {
        new ScreenOps.AddScreen(Screen.empty("autre")).apply(bp, LOOKUP);
        put(ScreenElement.of("a", ElementKind.LABEL, 10, 10, 40, 20));

        controller.press(20, 20, false);
        controller.setScreenName("autre");

        assertEquals(ScreenCanvasController.Gesture.NONE, controller.gesture());
        assertTrue(controller.selection().isEmpty());
        assertNull(controller.screen().element("a"), "l'autre écran est vide");
    }

    /** Un écran supprimé sous les pieds du concepteur ne le fait pas tomber. */
    @Test
    void unEcranDisparuNeCassePas() {
        put(ScreenElement.of("a", ElementKind.LABEL, 10, 10, 40, 20));
        new ScreenOps.RemoveScreen("menu").apply(bp, LOOKUP);

        assertNull(controller.screen());
        assertNull(controller.hitTest(20, 20));
        assertNull(controller.rectOf("a"));
        controller.press(20, 20, false);
        controller.drag(40, 40);
        controller.release();
        assertFalse(controller.deleteSelection());
        assertNull(controller.addElement(ElementKind.BUTTON, 10, 10));
    }
}
