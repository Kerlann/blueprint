package fr.blueprint.client.editor;

import fr.blueprint.api.pin.PinKind;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.NodeShape;
import fr.blueprint.core.graph.Vec2d;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

/**
 * Rendu des liens : courbes de Bézier cubiques à tangentes horizontales, colorées par
 * le type du pin source, exec plus épais (UX §5). Les segments sont des rectangles
 * pivotés par la pose 2D, tracés en espace écran.
 */
public final class WireLayer {

    private static final double TENSION = 0.55;

    private static int execColor() {
        return fr.blueprint.client.theme.Theme.current().execWire();
    }
    private static final int SELECTED_HALO = 0xFFFFFFFF;
    private static final int DATA_WIDTH = 2;
    private static final int EXEC_WIDTH = 3;
    /** Marge monde ajoutée au champ visible pour le culling des liens. */
    private static final double CULL_MARGIN = 300;

    private WireLayer() {
    }

    /** Dessine tous les liens du graphe (sous les nœuds). */
    public static void renderLinks(GuiGraphics g, Camera camera, CanvasController controller,
                                   int width, int height) {
        Blueprint bp = controller.blueprint();
        Camera.Rect view = camera.visibleRect(width, height);
        Camera.Rect wide = new Camera.Rect(view.left() - CULL_MARGIN, view.top() - CULL_MARGIN,
                view.right() + CULL_MARGIN, view.bottom() + CULL_MARGIN);
        for (Link link : bp.links()) {
            Vec2d from = endpoint(controller, link.fromNode(), link.fromPin(), true);
            Vec2d to = endpoint(controller, link.toNode(), link.toPin(), false);
            if (from == null || to == null) {
                continue;
            }
            if (!wide.intersects(Math.min(from.x(), to.x()), Math.min(from.y(), to.y()),
                    Math.abs(to.x() - from.x()) + 1, Math.abs(to.y() - from.y()) + 1)) {
                continue;
            }
            NodeShape.PinDef def = controller.pinDef(link.fromNode(), link.fromPin());
            boolean exec = def != null && def.kind() == PinKind.EXEC;
            int color = exec ? execColor() : def != null ? def.type().color() : execColor();
            int worldWidth = exec ? EXEC_WIDTH : DATA_WIDTH;
            if (link.equals(controller.selectedLink())) {
                // Un halo dessous : un fil sélectionné doit se voir sans changer de
                // couleur, sinon on perd l'information de type qu'elle porte.
                drawCurve(g, camera, from.x(), from.y(), 1, to.x(), to.y(), -1,
                        SELECTED_HALO, worldWidth + 3);
            }
            drawCurve(g, camera, from.x(), from.y(), 1, to.x(), to.y(), -1,
                    color, worldWidth);
        }
    }

    /** Aperçu du lien en cours de tracé, du pin saisi au curseur (au-dessus des nœuds). */
    public static void renderPreview(GuiGraphics g, Camera camera, CanvasController controller) {
        CanvasController.PinRef from = controller.wireFrom();
        if (from == null) {
            return;
        }
        Vec2d start = controller.pinCenter(from.node(), from.pin());
        if (start == null) {
            return;
        }
        boolean exec = from.kind() == PinKind.EXEC;
        int dir = from.output() ? 1 : -1;
        drawCurve(g, camera, start.x(), start.y(), dir,
                controller.wireCursorX(), controller.wireCursorY(), -dir,
                exec ? execColor() : from.type().color(), exec ? EXEC_WIDTH : DATA_WIDTH);
    }

    /**
     * Décalage horizontal des points de contrôle, en pixels écran. Extrait pour que le
     * clic vise <b>exactement</b> la courbe dessinée : deux formules qui divergent, et
     * l'on clique à côté d'un fil sans jamais comprendre pourquoi.
     */
    static double controlOffset(double x0, double x3) {
        return Math.clamp(Math.abs(x3 - x0) * TENSION, 16, 160);
    }

    /**
     * Distance en pixels écran d'un point à la courbe, par la même polyligne que le
     * rendu. Pur, donc testable : c'est la géométrie qui se trompe, pas le dessin.
     */
    public static double distanceToCurve(double x0, double y0, int dir0,
                                         double x3, double y3, int dir3,
                                         double px, double py) {
        double d = controlOffset(x0, x3);
        double x1 = x0 + dir0 * d;
        double x2 = x3 + dir3 * d;
        int n = (int) Math.clamp(Math.hypot(x3 - x0, y3 - y0) / 8, 8, 32);

        double best = Double.MAX_VALUE;
        double ax = x0;
        double ay = y0;
        for (int i = 1; i <= n; i++) {
            double t = (double) i / n;
            double u = 1 - t;
            double bx = u * u * u * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x3;
            double by = u * u * u * y0 + 3 * u * u * t * y0 + 3 * u * t * t * y3 + t * t * t * y3;
            best = Math.min(best, distanceToSegment(ax, ay, bx, by, px, py));
            ax = bx;
            ay = by;
        }
        return best;
    }

    private static double distanceToSegment(double x1, double y1, double x2, double y2,
                                            double px, double py) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len2 = dx * dx + dy * dy;
        if (len2 < 1e-9) {
            return Math.hypot(px - x1, py - y1);
        }
        double t = Math.clamp(((px - x1) * dx + (py - y1) * dy) / len2, 0, 1);
        return Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
    }

    /** Centre du pin, ou secours au bord de la boîte (fantôme sans forme connue). */
    static @Nullable Vec2d endpoint(CanvasController controller, java.util.UUID node,
                                    String pin, boolean output) {
        Vec2d center = controller.pinCenter(node, pin);
        if (center != null) {
            return center;
        }
        NodeGeometry.Box box = controller.boxOf(node);
        if (box == null) {
            return null;
        }
        return new Vec2d(output ? box.x() + box.width() : box.x(), box.y() + box.height() / 2);
    }

    /**
     * Bézier cubique en espace écran ; {@code dir0}/{@code dir3} orientent les
     * tangentes horizontales (+1 vers la droite). Épaisseur en pixels monde,
     * convertie à l'écran par le zoom.
     */
    private static void drawCurve(GuiGraphics g, Camera camera,
                                  double wx0, double wy0, int dir0,
                                  double wx3, double wy3, int dir3,
                                  int color, int worldWidth) {
        double x0 = camera.toScreenX(wx0);
        double y0 = camera.toScreenY(wy0);
        double x3 = camera.toScreenX(wx3);
        double y3 = camera.toScreenY(wy3);
        double d = Math.clamp(Math.abs(x3 - x0) * TENSION, 16, 160);
        double x1 = x0 + dir0 * d;
        double x2 = x3 + dir3 * d;

        double dist = Math.hypot(x3 - x0, y3 - y0);
        int n = (int) Math.clamp(dist / 8, 8, 32);
        int thickness = Math.max(1, (int) Math.round(worldWidth * camera.zoom()));

        double px = x0;
        double py = y0;
        for (int i = 1; i <= n; i++) {
            double t = (double) i / n;
            double u = 1 - t;
            double cx = u * u * u * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x3;
            double cy = u * u * u * y0 + 3 * u * u * t * y0 + 3 * u * t * t * y3 + t * t * t * y3;
            drawSegment(g, px, py, cx, cy, thickness, color);
            px = cx;
            py = cy;
        }
    }

    private static void drawSegment(GuiGraphics g, double x1, double y1, double x2, double y2,
                                    int thickness, int color) {
        double len = Math.hypot(x2 - x1, y2 - y1);
        if (len < 0.5) {
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate((float) x1, (float) y1);
        g.pose().rotate((float) Math.atan2(y2 - y1, x2 - x1));
        int half = thickness / 2;
        g.fill(0, -half, (int) Math.ceil(len) + 1, thickness - half, color);
        g.pose().popMatrix();
    }
}
