package fr.blueprint.client.editor;

import fr.blueprint.core.graph.Vec2d;

/**
 * Caméra du canevas : transformation monde↔écran, crans de zoom bornés, accroche.
 * Logique pure sans Minecraft — c'est ce qui permet de la tester headless et de
 * mesurer la passe CPU du rendu dans le banc (NFR1).
 *
 * <p>Convention : {@code origin} est le point du monde affiché au coin haut-gauche
 * de l'écran ; {@code écran = (monde − origin) × zoom}.
 */
public final class Camera {

    /** Pas de la grille en unités monde (UX §3). */
    public static final double GRID_STEP = 16;

    /** Une ligne majeure toutes les 4 mineures. */
    public static final double GRID_MAJOR_STEP = GRID_STEP * 4;

    /** Sous ce zoom, les lignes mineures disparaissent et les titres passent en LOD. */
    public static final double GRID_FADE_ZOOM = 0.5;

    /**
     * Les 8 crans exigés par le PRD (extrêmes exacts 0,25 et 2,00), progression
     * quasi géométrique passant par 1,00 — le cran par défaut.
     */
    public static final double[] ZOOM_LEVELS = {0.25, 0.35, 0.50, 0.70, 1.00, 1.25, 1.60, 2.00};

    public static final int DEFAULT_ZOOM_INDEX = 4;

    /** Marge écran conservée autour du graphe par {@link #frameAll}. */
    private static final double FRAME_MARGIN = 40;

    private double originX;
    private double originY;
    private int zoomIndex = DEFAULT_ZOOM_INDEX;
    private boolean snapEnabled;

    public double zoom() {
        return ZOOM_LEVELS[zoomIndex];
    }

    public int zoomIndex() {
        return zoomIndex;
    }

    // ------------------------------------------------------------ transformations

    public double toScreenX(double worldX) {
        return (worldX - originX) * zoom();
    }

    public double toScreenY(double worldY) {
        return (worldY - originY) * zoom();
    }

    public double toWorldX(double screenX) {
        return screenX / zoom() + originX;
    }

    public double toWorldY(double screenY) {
        return screenY / zoom() + originY;
    }

    /** Rectangle du monde couvert par un écran de {@code screenW}×{@code screenH}. */
    public Rect visibleRect(double screenW, double screenH) {
        return new Rect(originX, originY, toWorldX(screenW), toWorldY(screenH));
    }

    // ------------------------------------------------------------------ navigation

    /** Déplace la vue d'un delta exprimé en pixels écran (glisser de souris). */
    public void panByScreen(double dx, double dy) {
        originX -= dx / zoom();
        originY -= dy / zoom();
    }

    /**
     * Change de cran de zoom en gardant immobile le point du monde situé sous
     * {@code (pivotSx, pivotSy)} — le zoom « sur le curseur ».
     */
    public void zoomBy(int steps, double pivotSx, double pivotSy) {
        int next = Math.clamp(zoomIndex + steps, 0, ZOOM_LEVELS.length - 1);
        if (next == zoomIndex) {
            return;
        }
        double pivotWx = toWorldX(pivotSx);
        double pivotWy = toWorldY(pivotSy);
        zoomIndex = next;
        originX = pivotWx - pivotSx / zoom();
        originY = pivotWy - pivotSy / zoom();
    }

    /**
     * Recentre la vue sur la boîte englobante donnée (touche F) : choisit le plus
     * grand cran qui la contient avec marge, puis centre. Une boîte vide recentre
     * sur l'origine du monde au zoom par défaut.
     */
    public void frameAll(Rect bounds, double screenW, double screenH) {
        double w = bounds.right() - bounds.left();
        double h = bounds.bottom() - bounds.top();
        double usableW = Math.max(1, screenW - 2 * FRAME_MARGIN);
        double usableH = Math.max(1, screenH - 2 * FRAME_MARGIN);
        int best = 0;
        for (int i = ZOOM_LEVELS.length - 1; i >= 0; i--) {
            if (w * ZOOM_LEVELS[i] <= usableW && h * ZOOM_LEVELS[i] <= usableH) {
                best = i;
                break;
            }
        }
        zoomIndex = (w <= 0 && h <= 0) ? DEFAULT_ZOOM_INDEX : best;
        double cx = (bounds.left() + bounds.right()) / 2;
        double cy = (bounds.top() + bounds.bottom()) / 2;
        originX = cx - screenW / 2 / zoom();
        originY = cy - screenH / 2 / zoom();
    }

    /** Centre la vue sur un point monde sans changer de cran (clic sur diagnostic). */
    public void centerOn(double worldX, double worldY, double screenW, double screenH) {
        originX = worldX - screenW / 2 / zoom();
        originY = worldY - screenH / 2 / zoom();
    }

    // -------------------------------------------------------------------- accroche

    public boolean snapEnabled() {
        return snapEnabled;
    }

    public void toggleSnap() {
        snapEnabled = !snapEnabled;
    }

    /** Arrondit une coordonnée monde au pas de grille si l'accroche est active. */
    public double snap(double v) {
        return snapEnabled ? Math.round(v / GRID_STEP) * GRID_STEP : v;
    }

    public Vec2d snap(Vec2d p) {
        return snapEnabled ? new Vec2d(snap(p.x()), snap(p.y())) : p;
    }

    /** Rectangle monde, bords en coordonnées monde. */
    public record Rect(double left, double top, double right, double bottom) {

        /** Test d'intersection avec une boîte (x, y, largeur, hauteur) — le culling. */
        public boolean intersects(double x, double y, double w, double h) {
            return x < right && x + w > left && y < bottom && y + h > top;
        }
    }
}
