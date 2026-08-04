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
 *
 * <p>Le zoom était un <b>entier</b> par souci de netteté. L'argument tenait tant que le
 * peintre multipliait des entiers ; il ne tient plus depuis que le dessin passe par une
 * matrice — et il ne tenait de toute façon pas devant le besoin : aucun facteur entier ne
 * montre 1920 unités dans les ~420 pixels que laissent les panneaux. Ce que ces tests
 * verrouillent n'est donc plus « l'échelle est entière » mais ce qui compte vraiment :
 * <b>à 1:1 une unité vaut un pixel</b>, et l'aller-retour ne dérive jamais de plus d'un
 * demi-pixel.
 */
class DesignSurfaceTest {

    /** L'échelle tient compte de la marge : c'est le tout qui doit rentrer. */
    @Test
    void laSurfaceEstCentreeDansSaZoneMargeComprise() {
        DesignSurface surface = DesignSurface.fit(0, 0, 800, 500);

        // 368 unités de large (320 + 2×24) contre 228 de haut : c'est la largeur qui
        // borne, donc la zone est remplie exactement d'un bord à l'autre.
        assertEquals(0, surface.outerLeft(), "le canevas commence au bord gauche de la zone");
        assertEquals(800, surface.outerRight(), "et finit au bord droit");

        int above = surface.outerTop();
        int below = 500 - surface.outerBottom();
        assertTrue(Math.abs(above - below) <= 1,
                () -> "verticalement il doit être centré : " + above + " au-dessus, "
                        + below + " au-dessous");
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

    /**
     * À 1:1, une unité vaut <b>exactement</b> un pixel — c'est la taille du jeu, et le
     * seul cran où l'aperçu est au pixel près ce que le joueur verra.
     */
    @Test
    void aUnPourUnUneUniteVautUnPixel() {
        DesignCamera camera = new DesignCamera();
        camera.zoomTo(DesignCamera.ONE_TO_ONE, 0, 0);
        DesignSurface surface = DesignSurface.of(camera, 0, 0,
                Screen.SAFE_WIDTH, Screen.SAFE_HEIGHT);

        assertEquals(Screen.SAFE_WIDTH, surface.width());
        assertEquals(Screen.SAFE_HEIGHT, surface.height());
        assertEquals(40, surface.toScreenX(40) - surface.left());
    }

    /**
     * Une zone minuscule ne produit ni échelle nulle ni division : le canevas se voit
     * entier, simplement plus petit. Avant, il était dessiné au facteur 1 et débordait —
     * on ne pouvait ni tout voir ni rien y faire.
     */
    @Test
    void uneZoneTropPetiteMontreQuandMemeToutLeCanevas() {
        DesignSurface surface = DesignSurface.fit(0, 0, 100, 60);

        assertTrue(surface.zoom() >= DesignCamera.MIN_ZOOM);
        assertTrue(surface.outerLeft() >= -1 && surface.outerRight() <= 101,
                "le canevas entier tient dans les 100 pixels de large");
        assertTrue(surface.width() > 0, "et il n'a pas disparu");
    }

    /**
     * L'aller-retour ne peut plus être exact — l'écran n'a pas de fraction de pixel. Ce
     * qui compte est que l'écart reste sous le demi-pixel : au-delà, l'élément se
     * dessinerait visiblement ailleurs qu'où il se clique.
     */
    @Test
    void lAllerRetourNeDeriveJamaisDePlusDUnDemiPixel() {
        DesignSurface surface = DesignSurface.fit(0, 0, 1200, 800);
        double tolerance = 0.5 / surface.zoom() + 1e-9;

        for (double unit : new double[]{0, 1, 40, 40.5, 160.25, 318.75, 319}) {
            assertEquals(unit, surface.toDesignX(surface.toScreenX(unit)), tolerance);
            assertEquals(unit, surface.toDesignY(surface.toScreenY(unit)), tolerance);
        }
    }

    @Test
    void lOrigineDeLaSurfaceEstLOrigineDesUnites() {
        DesignSurface surface = DesignSurface.fit(0, 0, 800, 500);
        double tolerance = 0.5 / surface.zoom() + 1e-9;

        assertEquals(0, surface.toDesignX(surface.left()), tolerance);
        assertEquals(0, surface.toDesignY(surface.top()), tolerance);
        assertEquals(Screen.SAFE_WIDTH, surface.toDesignX(surface.right()), tolerance);
        assertEquals(Screen.SAFE_HEIGHT, surface.toDesignY(surface.bottom()), tolerance);
    }

    /**
     * Les bords dérivent tous de la MÊME conversion. {@code right()} vaut
     * {@code toScreenX(largeur)} et non {@code left() + largeur × zoom} : deux arrondis
     * séparés peuvent différer d'un pixel, et le cadre ne coïnciderait plus avec ce qu'il
     * encadre — un écart d'un pixel qu'on passerait une soirée à chercher.
     */
    @Test
    void lesBordsEtLesElementsSontArrondisEnsemble() {
        for (double zoom : DesignCamera.LADDER) {
            DesignCamera camera = new DesignCamera();
            camera.zoomTo(zoom, 0, 0);
            camera.panByScreen(7, 3);   // origine volontairement entre deux pixels
            DesignSurface surface = DesignSurface.of(camera, 5, 5, 640, 360);

            assertEquals(surface.toScreenX(640), surface.right());
            assertEquals(surface.toScreenY(360), surface.bottom());
            assertEquals(surface.toScreenX(-DesignSurface.MARGIN), surface.outerLeft());
        }
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
        assertEquals(640, grand.toDesignX(grand.right()), 0.5 / grand.zoom() + 1e-9,
                "le bord droit vaut 640 unités, plus 320");
    }

    /** Un canevas plus grand que la fenêtre se voit entier — c'est tout l'objet du zoom. */
    @Test
    void unCanevasTropGrandTientQuandMeme() {
        DesignSurface plein = DesignSurface.fit(0, 0, 420, 300, 1920, 1080);

        assertTrue(plein.zoom() < 1, "1920 unités dans 420 pixels : il faut réduire");
        assertTrue(plein.outerRight() <= 421 && plein.outerBottom() <= 301,
                "et le tout doit rentrer");
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
