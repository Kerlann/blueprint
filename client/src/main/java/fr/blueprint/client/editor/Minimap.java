package fr.blueprint.client.editor;

import fr.blueprint.client.theme.Theme;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Minimap (story 5.7, UX §2) : vue d'ensemble en bas à droite, rectangle de la vue
 * courante, clic = téléportation de la caméra. La projection monde↔minimap est pure et
 * testée.
 *
 * <p>Les nœuds y sont des <b>rectangles à l'échelle reliés par leurs fils</b>, et non des
 * points isolés comme au premier jet. Un point ne dit ni la taille d'un nœud ni ce qui le
 * relie : la minimap montrait huit taches dispersées dans un cadre, où l'on ne
 * reconnaissait pas le graphe qu'on venait de dessiner. Or c'est exactement à cela qu'elle
 * sert — se repérer dans ce qu'on ne voit plus.
 */
public final class Minimap {

    public static final int W = 96;
    public static final int H = 64;
    public static final int MARGIN = 8;
    /** Marge monde autour des bornes pour que les points ne collent pas aux bords. */
    private static final double PAD = 80;

    private static final int NODE_COLOR = 0xFFAFB6C0;
    private static final int LINK_COLOR = 0x60AFB6C0;

    private Minimap() {
    }

    /**
     * Un segment, tracé point par point. Le moteur ne dessine que des rectangles ; une
     * ligne oblique se compose donc à la main, comme les fils du canevas.
     */
    private static void line(GuiGraphics g, double x1, double y1, double x2, double y2,
                             int color) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        int steps = (int) Math.max(1, Math.max(Math.abs(dx), Math.abs(dy)));
        for (int i = 0; i <= steps; i++) {
            int px = (int) Math.round(x1 + dx * i / steps);
            int py = (int) Math.round(y1 + dy * i / steps);
            g.fill(px, py, px + 1, py + 1, color);
        }
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

    /**
     * Au-dessous de ce nombre de nœuds, la minimap ne s'affiche pas.
     *
     * <p>Elle sert à se repérer dans ce qui dépasse de l'écran. Trois nœuds tiennent
     * toujours dans la vue : un cadre en bas à droite ne montrerait alors rien qu'on ne
     * voie déjà, tout en masquant un morceau de canevas.
     */
    public static final int MIN_NODES = 4;

    /** La minimap a-t-elle quelque chose à montrer ? Le rendu ET le clic s'y fient. */
    public static boolean useful(List<NodeGeometry.Box> boxes) {
        return boxes.size() >= MIN_NODES;
    }

    public static void render(GuiGraphics g, Camera camera, List<NodeGeometry.Box> boxes,
                              java.util.Collection<fr.blueprint.core.graph.Link> links,
                              java.util.Set<java.util.UUID> selected,
                              int left, int top, int canvasWidth, int canvasHeight) {
        if (!useful(boxes)) {
            return;
        }
        Theme theme = Theme.current();
        g.fill(left - 1, top - 1, left + W + 1, top + H + 1, theme.nodeBorder());
        g.fill(left, top, left + W, top + H, (theme.canvasBackground() & 0x00FFFFFF) | 0xE0000000);

        Camera.Rect bounds = NodeGeometry.boundsOf(boxes);
        // Les FILS d'abord, sous les nœuds : ce sont eux qui donnent sa forme au graphe,
        // et les dessiner par-dessus les rectangles brouillerait les deux.
        java.util.Map<java.util.UUID, NodeGeometry.Box> byId = new java.util.HashMap<>();
        for (NodeGeometry.Box box : boxes) {
            byId.put(box.node().uuid(), box);
        }
        for (var link : links) {
            NodeGeometry.Box from = byId.get(link.fromNode());
            NodeGeometry.Box to = byId.get(link.toNode());
            if (from == null || to == null) {
                continue;
            }
            double[] a = toMini(bounds, from.x() + from.width(), from.y() + from.height() / 2);
            double[] b = toMini(bounds, to.x(), to.y() + to.height() / 2);
            line(g, left + a[0], top + a[1], left + b[0], top + b[1], LINK_COLOR);
        }
        for (NodeGeometry.Box box : boxes) {
            double[] a = toMini(bounds, box.x(), box.y());
            double[] b = toMini(bounds, box.x() + box.width(), box.y() + box.height());
            int x1 = left + (int) a[0];
            int y1 = top + (int) a[1];
            // Au moins deux pixels : à l'échelle d'un grand graphe, un nœud tomberait
            // sous le pixel et disparaîtrait — c'est le repère qu'on cherchait.
            int x2 = Math.max(x1 + 2, left + (int) b[0]);
            int y2 = Math.max(y1 + 2, top + (int) b[1]);
            boolean isSelected = selected.contains(box.node().uuid());
            g.fill(x1, y1, x2, y2, isSelected ? theme.nodeSelected() : NODE_COLOR);
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

    /**
     * Le clic n'atteint la minimap que si elle est DESSINÉE. Sans cette condition, un
     * cadre invisible en bas à droite avalerait les clics du canevas — et rien à l'écran
     * n'expliquerait pourquoi ce coin ne répond pas.
     */
    public static boolean contains(double mx, double my, int left, int top,
                                   List<NodeGeometry.Box> boxes) {
        return useful(boxes) && mx >= left && mx < left + W && my >= top && my < top + H;
    }
}
