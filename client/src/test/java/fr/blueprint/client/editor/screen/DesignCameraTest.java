package fr.blueprint.client.editor.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La vue du concepteur : zoom, déplacement, cadrage.
 *
 * <p>Le concepteur n'avait ni l'un ni l'autre — on dessinait un menu au facteur entier
 * que la place disponible imposait, soit 1 dans une fenêtre de jeu ordinaire. Ce fichier
 * verrouille ce qui rend un zoom utilisable plutôt que simplement présent.
 */
class DesignCameraTest {

    /** Une zone de travail plausible : ce qu'il reste entre les deux panneaux. */
    private static final int AREA_WIDTH = 420;
    private static final int AREA_HEIGHT = 300;

    @Test
    void lesCransSontCroissantsEtPassentParUnPourUn() {
        for (int i = 1; i < DesignCamera.LADDER.length; i++) {
            assertTrue(DesignCamera.LADDER[i] > DesignCamera.LADDER[i - 1],
                    "les crans doivent croître strictement");
        }
        boolean hasOne = false;
        for (double step : DesignCamera.LADDER) {
            hasOne |= step == DesignCamera.ONE_TO_ONE;
        }
        assertTrue(hasOne, "1:1 doit être un cran exact — c'est la taille réelle du jeu");
    }

    /**
     * <b>Le test qui compte.</b> Le point visé ne bouge pas d'un cran à l'autre.
     *
     * <p>C'est ce qui distingue un zoom d'un saut de vue. Sans ce pivot, on perd à chaque
     * cran l'élément qu'on regardait, et il faut le retrouver au déplacement de vue avant
     * de pouvoir zoomer encore — au point qu'il vaut mieux ne pas zoomer du tout.
     */
    @Test
    void leZoomGardeImmobileLePointSousLeCurseur() {
        DesignCamera camera = new DesignCamera();
        camera.fit(AREA_WIDTH, AREA_HEIGHT, 1920, 1080);

        double localX = 137;
        double localY = 88;
        double aimedX = camera.toUnitX(localX);
        double aimedY = camera.toUnitY(localY);

        for (int step : new int[]{1, 1, 1, -1, 1, 1, -1, -1}) {
            camera.zoomBy(step, localX, localY);
            assertEquals(aimedX, camera.toUnitX(localX), 1e-9,
                    "le point visé a glissé horizontalement en changeant de cran");
            assertEquals(aimedY, camera.toUnitY(localY), 1e-9,
                    "le point visé a glissé verticalement en changeant de cran");
        }
    }

    /** La molette part de la valeur COURANTE, donc elle marche aussi après un cadrage. */
    @Test
    void laMoletteRepartDuZoomContinuLaisseParLeCadrage() {
        DesignCamera camera = new DesignCamera();
        camera.fit(AREA_WIDTH, AREA_HEIGHT, 1920, 1080);
        double fitted = camera.zoom();

        assertTrue(fitted < 0.5, () -> "1920 unités dans 420 pixels : " + fitted);
        camera.zoomBy(1, 0, 0);
        assertTrue(camera.zoom() > fitted, "un cran vers l'avant grossit toujours");
        assertTrue(camera.zoom() <= 0.5,
                "et il s'arrête au premier cran AU-DESSUS, pas à un index mémorisé");
    }

    @Test
    void lesCransSarretentAuxExtremites() {
        DesignCamera camera = new DesignCamera();
        camera.zoomBy(50, 0, 0);
        assertEquals(DesignCamera.MAX_ZOOM, camera.zoom());
        camera.zoomBy(-50, 0, 0);
        assertEquals(DesignCamera.MIN_ZOOM, camera.zoom());
    }

    /** Cadrer montre le canevas ENTIER, marge comprise — petit comme grand. */
    @Test
    void leCadrageMontreToutLeCanevas() {
        for (int[] canvas : new int[][]{{320, 180}, {640, 360}, {1920, 1080}}) {
            DesignCamera camera = new DesignCamera();
            camera.fit(AREA_WIDTH, AREA_HEIGHT, canvas[0], canvas[1]);

            assertTrue(camera.toLocalX(-DesignSurface.MARGIN) >= -0.5,
                    () -> "le bord gauche du canevas " + canvas[0] + " sort de la zone");
            assertTrue(camera.toLocalX(canvas[0] + DesignSurface.MARGIN) <= AREA_WIDTH + 0.5,
                    () -> "le bord droit du canevas " + canvas[0] + " sort de la zone");
            assertTrue(camera.toLocalY(-DesignSurface.MARGIN) >= -0.5,
                    () -> "le bord haut du canevas " + canvas[1] + " sort de la zone");
            assertTrue(camera.toLocalY(canvas[1] + DesignSurface.MARGIN) <= AREA_HEIGHT + 0.5,
                    () -> "le bord bas du canevas " + canvas[1] + " sort de la zone");
        }
    }

    /**
     * <b>Le second test qui compte.</b> On ne peut pas perdre le canevas.
     *
     * <p>Sans borne, un déplacement de vue un peu vif le sort de la zone : il ne reste
     * plus ni élément, ni cadre, ni indice de la direction où pousser pour le rattraper.
     * L'auteur croit avoir effacé son écran.
     */
    @Test
    void aucunDeplacementDeVueNePerdLeCanevas() {
        DesignCamera camera = new DesignCamera();
        camera.fit(AREA_WIDTH, AREA_HEIGHT, 640, 360);
        camera.zoomBy(4, 0, 0);   // zoomé : la vue est plus petite que le canevas

        for (double[] push : new double[][]{{9000, 0}, {-9000, 0}, {0, 9000}, {0, -9000},
                {50000, 50000}, {-50000, -50000}}) {
            camera.panByScreen(push[0], push[1]);
            camera.clampInto(AREA_WIDTH, AREA_HEIGHT, 640, 360);

            assertTrue(camera.toLocalX(640 + DesignSurface.MARGIN) > 0
                            && camera.toLocalX(-DesignSurface.MARGIN) < AREA_WIDTH,
                    "le canevas a quitté la zone horizontalement");
            assertTrue(camera.toLocalY(360 + DesignSurface.MARGIN) > 0
                            && camera.toLocalY(-DesignSurface.MARGIN) < AREA_HEIGHT,
                    "le canevas a quitté la zone verticalement");
        }
    }

    /** Quand la vue est plus large que le canevas, elle le CENTRE au lieu de le coller. */
    @Test
    void unCanevasPlusPetitQueLaVueResteCentre() {
        DesignCamera camera = new DesignCamera();
        camera.fit(AREA_WIDTH, AREA_HEIGHT, 320, 180);
        camera.panByScreen(300, 300);
        camera.clampInto(AREA_WIDTH, AREA_HEIGHT, 320, 180);

        double leftGap = camera.toLocalX(0);
        double rightGap = AREA_WIDTH - camera.toLocalX(320);
        assertEquals(leftGap, rightGap, 1e-6, "le canevas doit rester au milieu");
    }

    /** L'aller-retour unité → pixel → unité reste exact à CHAQUE cran. */
    @Test
    void laConversionResteReciproqueATousLesCrans() {
        DesignCamera camera = new DesignCamera();
        camera.fit(AREA_WIDTH, AREA_HEIGHT, 960, 540);
        for (int i = 0; i < DesignCamera.LADDER.length; i++) {
            camera.zoomTo(DesignCamera.LADDER[i], AREA_WIDTH / 2.0, AREA_HEIGHT / 2.0);
            for (double unit : new double[]{0, 1, 40.5, 320, 959}) {
                assertEquals(unit, camera.toUnitX(camera.toLocalX(unit)), 1e-6,
                        () -> "aller-retour faux au cran " + camera.zoom());
                assertEquals(unit, camera.toUnitY(camera.toLocalY(unit)), 1e-6);
            }
        }
    }

    /** Un déplacement de vue rend exactement les pixels demandés, quel que soit le zoom. */
    @Test
    void leDeplacementDeVueSuitLaSourisAuPixel() {
        DesignCamera camera = new DesignCamera();
        camera.zoomTo(4, 0, 0);
        double before = camera.toLocalX(100);
        camera.panByScreen(30, 0);
        assertEquals(before + 30, camera.toLocalX(100), 1e-9,
                "la vue doit suivre la souris au pixel, pas à l'unité");
    }
}
