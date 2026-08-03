package fr.blueprint.client.editor.screen;

import fr.blueprint.core.graph.screen.Screen;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La transformation entre la surface de conception et le widget (story 10.2).
 * Le rendu et le clic la partagent : une divergence ici et l'auteur clique à côté de
 * ce qu'il voit.
 */
class DesignSurfaceTest {

    /** L'échelle tient compte de la marge : c'est le tout qui doit rentrer. */
    @Test
    void laSurfaceEstCentreeDansSaZoneMargeComprise() {
        DesignSurface surface = DesignSurface.fit(0, 0, 800, 500);
        assertEquals(2, surface.scale(), "368×2 = 736 tient dans 800 ; ×3 non");
        assertEquals(32 + DesignSurface.MARGIN * 2, surface.left(), "(800 − 736) / 2 + marge");
        assertEquals(22 + DesignSurface.MARGIN * 2, surface.top(), "(500 − 456) / 2 + marge");
        assertEquals(surface.left() - DesignSurface.MARGIN * 2, surface.outerLeft());
    }

    @Test
    void laZoneEstDecaleeParSonOrigine() {
        DesignSurface a = DesignSurface.fit(0, 0, 800, 500);
        DesignSurface b = DesignSurface.fit(100, 50, 800, 500);
        assertEquals(a.left() + 100, b.left());
        assertEquals(a.top() + 50, b.top());
    }

    /**
     * <b>Le test qui compte pour AC3b.</b> Un élément posé au-delà du canevas reste
     * dans la zone de travail : sans la marge, le concepteur laisserait poser ce qu'il
     * ne laisserait plus ni voir ni rattraper.
     */
    @Test
    void laMargeRendSaisissableCeQuiDeborde() {
        DesignSurface surface = DesignSurface.fit(0, 0, 800, 500);
        int justOutside = surface.toScreenX(Screen.SAFE_WIDTH + 4);

        assertFalse(surface.insideCanvas(justOutside, surface.top() + 10),
                "hors de la zone garantie");
        assertTrue(surface.contains(justOutside, surface.top() + 10),
                "mais toujours dans la zone de travail");
    }

    /** Une échelle fractionnaire donnerait des bords baveux et un texte flou. */
    @Test
    void lEchelleResteEntiere() {
        for (int width = 320; width <= 2000; width += 37) {
            DesignSurface surface = DesignSurface.fit(0, 0, width, width);
            assertEquals(surface.scale(), Math.round(surface.scale()), "échelle entière");
            assertTrue(surface.scale() >= 1);
            assertTrue(surface.scale() <= DesignSurface.MAX_SCALE);
        }
    }

    /** Une fenêtre minuscule ne doit pas produire une échelle nulle — ni une division. */
    @Test
    void uneZoneTropPetiteRetombeSurUnPourUn() {
        DesignSurface surface = DesignSurface.fit(0, 0, 100, 60);
        assertEquals(1, surface.scale());
        assertEquals(Screen.SAFE_WIDTH, surface.width());
    }

    /**
     * Une unité entière retombe exactement sur elle-même : l'échelle est entière, donc
     * {@code unité × échelle} l'est aussi. C'est ce qui garantit qu'un élément dessiné
     * à 40 se clique à 40.
     */
    @Test
    void uneUniteEntiereFaitUnAllerRetourExact() {
        DesignSurface surface = DesignSurface.fit(0, 0, 1200, 800);
        for (double unit : new double[]{0, 1, 40, 160, 319}) {
            assertEquals(unit, surface.toDesignX(surface.toScreenX(unit)), 1e-9);
            assertEquals(unit, surface.toDesignY(surface.toScreenY(unit)), 1e-9);
        }
    }

    /**
     * Une unité fractionnaire ne peut pas revenir exacte — l'écran n'a pas de demi-pixel.
     * Ce qui compte est que l'écart reste sous le demi-pixel : au-delà, l'élément se
     * dessinerait visiblement ailleurs qu'où il se clique.
     */
    @Test
    void uneUniteFractionnaireNeDeriveQueDUnDemiPixel() {
        DesignSurface surface = DesignSurface.fit(0, 0, 1200, 800);
        double tolerance = 0.5 / surface.scale() + 1e-9;
        for (double unit : new double[]{0.5, 40.5, 160.25, 318.75}) {
            assertEquals(unit, surface.toDesignX(surface.toScreenX(unit)), tolerance);
            assertEquals(unit, surface.toDesignY(surface.toScreenY(unit)), tolerance);
        }
    }

    @Test
    void lOrigineDeLaSurfaceEstLOrigineDesUnites() {
        DesignSurface surface = DesignSurface.fit(0, 0, 800, 500);
        assertEquals(0, surface.toDesignX(surface.left()), 1e-9);
        assertEquals(0, surface.toDesignY(surface.top()), 1e-9);
        assertEquals(Screen.SAFE_WIDTH, surface.toDesignX(surface.right()), 1e-9);
        assertEquals(Screen.SAFE_HEIGHT, surface.toDesignY(surface.bottom()), 1e-9);
    }

    /**
     * <b>Le canevas est CHOISI.</b> Il valait 320×180 en dur, donc on concevait
     * toujours dans le pire cas sans jamais voir ce que les ancres donnent ailleurs.
     */
    @Test
    void leCanevasPrendLaTailleDemandee() {
        DesignSurface grand = DesignSurface.fit(0, 0, 1400, 900, 640, 360);

        assertEquals(640, grand.unitsWidth());
        assertEquals(360, grand.unitsHeight());
        assertEquals(640, grand.toDesignX(grand.right()), 1e-9,
                "le bord droit vaut 640 unités, plus 320");
        assertEquals(2, grand.scale(), "688×408 tient deux fois dans 1400×900");
    }

    /** Un canevas plus grand que la fenêtre retombe sur 1 plutôt que sur 0. */
    @Test
    void unCanevasTropGrandNeDisparaitPas() {
        DesignSurface serre = DesignSurface.fit(0, 0, 400, 300, 960, 540);
        assertEquals(1, serre.scale());
        assertEquals(960, serre.width());
    }

    @Test
    void horsDeLaZoneDeTravailUnClicNeConcoitRien() {
        DesignSurface surface = DesignSurface.fit(0, 0, 800, 500);
        assertTrue(surface.contains(surface.outerLeft(), surface.outerTop()));
        assertTrue(surface.contains(surface.outerRight() - 1, surface.outerBottom() - 1));
        assertFalse(surface.contains(surface.outerRight(), surface.outerTop()),
                "le bord droit est dehors");
        assertFalse(surface.contains(surface.outerLeft() - 1, surface.outerTop()));
    }
}
