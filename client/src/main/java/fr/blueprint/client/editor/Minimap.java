package fr.blueprint.client.editor;

import fr.blueprint.client.theme.Theme;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Minimap (story 5.7, UX §2) : vue d'ensemble en bas à droite, nœuds en points,
 * rectangle de la vue courante, clic = téléportation de la caméra. La projection
 * monde↔minimap est pure et testée.
 */
public final class Minimap {

    public static final int W = 96;
    public static final int H = 64;
    public static final int MARGIN = 8;
    /** Marge monde autour des bornes pour que les points ne collent pas aux bords. */
    private static final double PAD = 80;

    private Minimap() {
    }

    public static int left(int width, int rightPanel) {
        return width - W - MARGIN - rightPanel;
    }

    public static int top(int height) {
        return height - DiagnosticsPanel.BAR_HEIGHT - H - MARGIN;
    }

    /** Échelle uniforme monde→minimap pour des bornes données. */
    public static double scale(Camera.Rect bounds) {
        double w = bounds.right() - bounds.left() + 2 * PAD;
        double h = bounds.bottom() - bounds.top() + 2 * PAD;
        return Math.min(W / Math.max(1, w), H / Math.max(1, h));
    }

    /** Point minimap (relatif au coin de la minimap) d'un point monde. */
    public static double[] toMini(Camera.Rect bounds, double wx, double wy) {
        double s = scale(bounds);
        double cx = (bounds.left() + bounds.right()) / 2;
        double cy = (bounds.top() + bounds.bottom()) / 2;
        return new double[]{W / 2.0 + (wx - cx) * s, H / 2.0 + (wy - cy) * s};
    }

    /** Point monde d'un clic minimap — l'inverse exact de {@link #toMini}. */
    public static double[] toWorld(Camera.Rect bounds, double mx, double my) {
        double s = scale(bounds);
        double cx = (bounds.left() + bounds.right()) / 2;
        double cy = (bounds.top() + bounds.bottom()) / 2;
        return new double[]{cx + (mx - W / 2.0) / s, cy + (my - H / 2.0) / s};
    }

    public static void render(GuiGraphics g, Camera camera, List<NodeGeometry.Box> boxes,
                              int left, int top, int canvasWidth, int canvasHeight) {
        Theme theme = Theme.current();
        g.fill(left - 1, top - 1, left + W + 1, top + H + 1, theme.nodeBorder());
        g.fill(left, top, left + W, top + H, (theme.canvasBackground() & 0x00FFFFFF) | 0xE0000000);
        if (boxes.isEmpty()) {
            return;
        }
        Camera.Rect bounds = NodeGeometry.boundsOf(boxes);
        for (int i = 0; i < boxes.size(); i++) {
            NodeGeometry.Box b = boxes.get(i);
            double[] p = toMini(bounds, b.x() + b.width() / 2, b.y() + b.height() / 2);
            int px = left + (int) p[0];
            int py = top + (int) p[1];
            g.fill(px - 1, py - 1, px + 1, py + 1, 0xFFAFB6C0);
        }
        // Rectangle de la vue courante, borné à la minimap.
        Camera.Rect view = camera.visibleRect(canvasWidth, canvasHeight);
        double[] a = toMini(bounds, view.left(), view.top());
        double[] b = toMini(bounds, view.right(), view.bottom());
        int x1 = left + (int) Math.clamp(a[0], 0, W);
        int y1 = top + (int) Math.clamp(a[1], 0, H);
        int x2 = left + (int) Math.clamp(b[0], 0, W);
        int y2 = top + (int) Math.clamp(b[1], 0, H);
        g.fill(x1, y1, x2, y1 + 1, theme.nodeSelected());
        g.fill(x1, y2 - 1, x2, y2, theme.nodeSelected());
        g.fill(x1, y1, x1 + 1, y2, theme.nodeSelected());
        g.fill(x2 - 1, y1, x2, y2, theme.nodeSelected());
    }

    public static boolean contains(double mx, double my, int left, int top) {
        return mx >= left && mx < left + W && my >= top && my < top + H;
    }
}
