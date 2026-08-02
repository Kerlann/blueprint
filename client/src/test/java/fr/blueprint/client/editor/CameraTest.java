package fr.blueprint.client.editor;

import fr.blueprint.core.graph.Vec2d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraTest {

    private static final double EPS = 1e-9;

    @Test
    void huitCransBornesExactes() {
        assertEquals(8, Camera.ZOOM_LEVELS.length);
        assertEquals(0.25, Camera.ZOOM_LEVELS[0]);
        assertEquals(2.00, Camera.ZOOM_LEVELS[Camera.ZOOM_LEVELS.length - 1]);
        assertEquals(1.00, Camera.ZOOM_LEVELS[Camera.DEFAULT_ZOOM_INDEX]);

        Camera cam = new Camera();
        cam.zoomBy(-100, 0, 0);
        assertEquals(0.25, cam.zoom(), EPS);
        cam.zoomBy(100, 0, 0);
        assertEquals(2.00, cam.zoom(), EPS);
    }

    @Test
    void zoomSurPivotLaisseLePointDuMondeImmobile() {
        Camera cam = new Camera();
        cam.panByScreen(-137, 42);
        double pivotSx = 300;
        double pivotSy = 200;
        double wxAvant = cam.toWorldX(pivotSx);
        double wyAvant = cam.toWorldY(pivotSy);

        cam.zoomBy(1, pivotSx, pivotSy);
        assertEquals(wxAvant, cam.toWorldX(pivotSx), EPS);
        assertEquals(wyAvant, cam.toWorldY(pivotSy), EPS);

        cam.zoomBy(-2, pivotSx, pivotSy);
        assertEquals(wxAvant, cam.toWorldX(pivotSx), EPS);
        assertEquals(wyAvant, cam.toWorldY(pivotSy), EPS);
    }

    @Test
    void allerRetourMondeEcran() {
        Camera cam = new Camera();
        cam.panByScreen(87.5, -12.25);
        cam.zoomBy(-2, 100, 60);
        assertEquals(123.45, cam.toWorldX(cam.toScreenX(123.45)), EPS);
        assertEquals(-678.9, cam.toWorldY(cam.toScreenY(-678.9)), EPS);
    }

    @Test
    void panDeplaceLEcranDuDeltaDonne() {
        Camera cam = new Camera();
        cam.zoomBy(1, 0, 0);
        double sxAvant = cam.toScreenX(500);
        double syAvant = cam.toScreenY(300);
        cam.panByScreen(50, 30);
        assertEquals(sxAvant + 50, cam.toScreenX(500), EPS);
        assertEquals(syAvant + 30, cam.toScreenY(300), EPS);
    }

    @Test
    void rectangleVisibleCorrespondAuxTransformations() {
        Camera cam = new Camera();
        cam.panByScreen(-40, 15);
        cam.zoomBy(-1, 20, 20);
        Camera.Rect r = cam.visibleRect(854, 480);
        assertEquals(cam.toWorldX(0), r.left(), EPS);
        assertEquals(cam.toWorldY(0), r.top(), EPS);
        assertEquals(cam.toWorldX(854), r.right(), EPS);
        assertEquals(cam.toWorldY(480), r.bottom(), EPS);
    }

    @Test
    void intersectionDeBoites() {
        Camera.Rect r = new Camera.Rect(0, 0, 100, 100);
        assertTrue(r.intersects(50, 50, 10, 10));
        assertTrue(r.intersects(-5, -5, 10, 10));
        assertFalse(r.intersects(100, 0, 10, 10));
        assertFalse(r.intersects(0, -20, 10, 20));
    }

    @Test
    void accrocheAuPasDe16() {
        Camera cam = new Camera();
        assertFalse(cam.snapEnabled());
        assertEquals(23, cam.snap(23), EPS);

        cam.toggleSnap();
        assertTrue(cam.snapEnabled());
        assertEquals(16, cam.snap(23), EPS);
        assertEquals(-16, cam.snap(-9), EPS);
        assertEquals(new Vec2d(32, 0), cam.snap(new Vec2d(30, 7.9)));

        cam.toggleSnap();
        assertEquals(23, cam.snap(23), EPS);
    }

    @Test
    void recadrageChoisitLePlusGrandCranQuiContient() {
        Camera cam = new Camera();
        // 1000×500 monde dans 854×480 écran avec marge 40 : 0,70 passe (700×350),
        // 1,00 déborde (1000 > 774).
        cam.frameAll(new Camera.Rect(0, 0, 1000, 500), 854, 480);
        assertEquals(0.70, cam.zoom(), EPS);
        assertEquals(427, cam.toScreenX(500), EPS);
        assertEquals(240, cam.toScreenY(250), EPS);
    }

    @Test
    void recadrageSansContenuRevientAuDefaut() {
        Camera cam = new Camera();
        cam.zoomBy(3, 17, 4);
        cam.frameAll(new Camera.Rect(0, 0, 0, 0), 854, 480);
        assertEquals(1.00, cam.zoom(), EPS);
        assertEquals(427, cam.toScreenX(0), EPS);
        assertEquals(240, cam.toScreenY(0), EPS);
    }

    @Test
    void recadrageGeantTombeSurLeCranMinimum() {
        Camera cam = new Camera();
        cam.frameAll(new Camera.Rect(0, 0, 100_000, 100_000), 854, 480);
        assertEquals(0.25, cam.zoom(), EPS);
    }
}
