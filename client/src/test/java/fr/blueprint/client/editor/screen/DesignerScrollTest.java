package fr.blueprint.client.editor.screen;

import fr.blueprint.client.editor.history.UndoStack;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.NodeTypeLookup;
import fr.blueprint.core.graph.ScreenOps;
import fr.blueprint.core.graph.screen.ElementKind;
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
 * Le panneau défilant vu du <b>concepteur</b> (story 10.13).
 *
 * <p>Découper en conception pose un problème que le jeu n'a pas : un enfant sorti du cadre
 * n'est dessiné nulle part, donc introuvable à la souris, donc <b>impossible à régler ni à
 * déplacer</b>. Le concepteur doit donc savoir le ramener sous les yeux — sans quoi cocher
 * une case ferait perdre l'accès à la moitié d'un panneau.
 */
class DesignerScrollTest {

    private static final NodeTypeLookup LOOKUP = typeId -> null;

    private Blueprint bp;
    private ScreenCanvasController controller;

    @BeforeEach
    void setUp() {
        bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "defile"));
        new ScreenOps.AddScreen(Screen.empty("menu")).apply(bp, LOOKUP);
        controller = new ScreenCanvasController(bp, LOOKUP, new UndoStack(), "menu");
        controller.setViewport(ScreenCanvasController.Viewport.SMALL);
    }

    private void put(ScreenElement element) {
        assertTrue(new ScreenOps.AddElement("menu", element).apply(bp, LOOKUP).applied(),
                () -> "mise en place refusée : " + element.name());
    }

    /** Un cadre de 100 de haut, six boutons de 20 : il en dépasse 20. */
    private void panneau() {
        put(ScreenElement.of("cadre", ElementKind.PANEL, 0, 0, 200, 100)
                .withLayout(LayoutSpec.column(0).withScroll(true)));
        for (int i = 0; i < 6; i++) {
            put(ScreenElement.of("b" + i, ElementKind.BUTTON, 0, 0, 100, 20)
                    .withParent("cadre"));
        }
    }

    // ------------------------------------------------------------- le défilement

    @Test
    void unPanneauNeufEstEnHautDeSaPage() {
        panneau();
        assertEquals(0, controller.scrollOf("cadre"), 1e-9);
    }

    @Test
    void faireDefilerBougeLesEnfantsEtPasLeCadre() {
        panneau();
        assertTrue(controller.scrollBy("cadre", 20));

        assertEquals(-20, controller.rects().get("b0").y(), 1e-9);
        assertEquals(0, controller.rects().get("cadre").y(), 1e-9);
    }

    /**
     * Le défilement est <b>borné</b> à ce qui dépasse. Sans borne, la molette emporterait
     * la page dans le vide, et il n'y aurait plus rien à l'écran ni aucun indice de la
     * direction où revenir.
     */
    @Test
    void leDefilementEstBorneAuContenu() {
        panneau();

        assertFalse(controller.scrollBy("cadre", -10), "on ne remonte pas avant le début");
        assertEquals(0, controller.scrollOf("cadre"), 1e-9);

        assertTrue(controller.scrollBy("cadre", 500));
        assertEquals(20, controller.scrollOf("cadre"), 1e-9,
                "six boutons de 20 dans un cadre de 100 : 20 dépassent, pas davantage");
        assertFalse(controller.scrollBy("cadre", 500), "et on ne va pas plus loin");
    }

    @Test
    void unPanneauQuiNeDefilePasRefuseDeDefiler() {
        put(ScreenElement.of("cadre", ElementKind.PANEL, 0, 0, 200, 100));
        put(ScreenElement.of("b", ElementKind.BUTTON, 0, 0, 100, 400).withParent("cadre"));

        assertFalse(controller.scrollBy("cadre", 20),
                "cocher la case est le seul moyen de faire défiler quoi que ce soit");
    }

    // ---------------------------------------------------------------- la molette

    @Test
    void laMoletteViseLePanneauLePlusInterieur() {
        put(ScreenElement.of("dehors", ElementKind.PANEL, 0, 0, 200, 150)
                .withLayout(LayoutSpec.ABSOLUTE.withScroll(true)));
        put(ScreenElement.of("dedans", ElementKind.PANEL, 0, 0, 100, 60)
                .withParent("dehors").withLayout(LayoutSpec.ABSOLUTE.withScroll(true)));
        put(ScreenElement.of("feuille", ElementKind.LABEL, 0, 0, 50, 200)
                .withParent("dedans"));

        assertEquals("dedans", controller.scrollableAt(10, 10),
                "c'est celui qu'on regarde qui doit bouger, comme dans toute page imbriquée");
        assertEquals("dehors", controller.scrollableAt(150, 120),
                "hors du panneau intérieur, c'est l'extérieur qui répond");
    }

    @Test
    void horsDeToutPanneauDefilantLaMoletteNaRienAViser() {
        panneau();
        assertNull(controller.scrollableAt(300, 170), "à côté du cadre : rien");
    }

    // ------------------------------------------------------- révéler la sélection

    /**
     * <b>Le test qui compte pour le concepteur.</b> Sélectionner un enfant sorti du cadre
     * le ramène dans le cadre.
     *
     * <p>C'est ce qui rend le découpage supportable en conception. Sans cela, cocher
     * « défilant » ferait disparaître la moitié des enfants d'un panneau : ils seraient
     * encore dans la liste des calques, mais plus nulle part à l'écran, et l'on ne pourrait
     * ni les déplacer, ni les redimensionner, ni voir ce qu'on règle.
     */
    @Test
    void selectionnerUnEnfantSortiDuCadreLeRamene() {
        panneau();
        // Défilé à fond : le premier bouton est sorti par le haut.
        assertTrue(controller.scrollBy("cadre", 20));
        ScreenLayout.Rect avant = controller.rects().get("b0");
        ScreenLayout.Rect cadre = controller.rects().get("cadre");
        assertTrue(avant.y() < cadre.y(), "le voilà bien hors du cadre");

        controller.selection().selectAll(List.of("b0"), false);
        assertTrue(controller.revealSelection(), "il faut le ramener");

        ScreenLayout.Rect apres = controller.rects().get("b0");
        assertTrue(apres.y() >= cadre.y() && apres.bottom() <= cadre.bottom(),
                () -> "toujours hors du cadre après révélation : " + apres);
    }

    @Test
    void selectionnerUnEnfantDejaVisibleNeBougeRien() {
        panneau();
        controller.selection().selectAll(List.of("b1"), false);

        assertFalse(controller.revealSelection(),
                "faire défiler pour rien ferait sauter la page à chaque clic");
        assertEquals(0, controller.scrollOf("cadre"), 1e-9);
    }

    @Test
    void unElementHorsDeToutPanneauDefilantNaRienAReveler() {
        put(ScreenElement.of("libre", ElementKind.LABEL, 10, 10, 40, 10));
        controller.selection().selectAll(List.of("libre"), false);

        assertFalse(controller.revealSelection());
    }

    /** Le hit-test du concepteur lit la MÊME table que le dessin : elle porte le décalage. */
    @Test
    void leHitTestSuitLeDecalage() {
        panneau();
        assertEquals("b1", controller.hitTest(10, 30), "sans défilement, b1 est à 20..40");

        assertTrue(controller.scrollBy("cadre", 20));
        assertEquals("b2", controller.hitTest(10, 30),
                "défilé de 20, c'est b2 qui occupe cette bande");
        assertNotNull(controller.rects().get("b0"));
    }
}
