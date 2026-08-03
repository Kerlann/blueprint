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

    @Test
    void laSurfaceEstCentreeDansSaZone() {
        DesignSurface surface = DesignSurface.fit(0, 0, 700, 400);
        assertEquals(2, surface.scale(), "320×2 = 640 tient dans 700 ; ×3 non");
        assertEquals(30, surface.left(), "(700 − 640) / 2");
        assertEquals(20, surface.top(), "(400 − 360) / 2");
    }

    @Test
    void laZoneEstDecaleeParSonOrigine() {
        DesignSurface surface = DesignSurface.fit(100, 50, 700, 400);
        assertEquals(130, surface.left());
        assertEquals(70, surface.top());
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
        DesignSurface surface = DesignSurface.fit(0, 0, 1000, 600);
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
        DesignSurface surface = DesignSurface.fit(0, 0, 1000, 600);
        double tolerance = 0.5 / surface.scale() + 1e-9;
        for (double unit : new double[]{0.5, 40.5, 160.25, 318.75}) {
            assertEquals(unit, surface.toDesignX(surface.toScreenX(unit)), tolerance);
            assertEquals(unit, surface.toDesignY(surface.toScreenY(unit)), tolerance);
        }
    }

    @Test
    void lOrigineDeLaSurfaceEstLOrigineDesUnites() {
        DesignSurface surface = DesignSurface.fit(0, 0, 700, 400);
        assertEquals(0, surface.toDesignX(surface.left()), 1e-9);
        assertEquals(0, surface.toDesignY(surface.top()), 1e-9);
        assertEquals(Screen.SAFE_WIDTH, surface.toDesignX(surface.right()), 1e-9);
        assertEquals(Screen.SAFE_HEIGHT, surface.toDesignY(surface.bottom()), 1e-9);
    }

    @Test
    void horsDeLaSurfaceUnClicNeConcoitRien() {
        DesignSurface surface = DesignSurface.fit(0, 0, 700, 400);
        assertTrue(surface.contains(surface.left(), surface.top()));
        assertTrue(surface.contains(surface.right() - 1, surface.bottom() - 1));
        assertFalse(surface.contains(surface.right(), surface.top()), "le bord droit est dehors");
        assertFalse(surface.contains(surface.left() - 1, surface.top()));
    }
}
