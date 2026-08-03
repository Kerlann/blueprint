package fr.blueprint.client.editor;

import net.minecraft.client.gui.GuiGraphics;

/**
 * La grille du canevas (UX §3), extraite du canevas : elle ne dépend que de la caméra
 * et de la taille de l'écran.
 *
 * <p>Les lignes mineures s'effacent sous 0,5× de zoom — à cette échelle elles se
 * touchent et forment un aplat qui masque les nœuds. Les majeures restent, elles
 * donnent l'échelle.
 */
final class GridLayer {

    private GridLayer() {
    }

    static void render(GuiGraphics g, Camera camera, int width, int height) {
        var theme = fr.blueprint.client.theme.Theme.current();
        if (camera.zoom() >= Camera.GRID_FADE_ZOOM) {
            lines(g, camera, width, height, Camera.GRID_STEP, theme.grid());
        }
        lines(g, camera, width, height, Camera.GRID_MAJOR_STEP, theme.gridMajor());
    }

    private static void lines(GuiGraphics g, Camera camera, int width, int height,
                              double step, int color) {
        Camera.Rect r = camera.visibleRect(width, height);
        // Partir du premier multiple du pas ≤ bord gauche : aucune ligne manquante ni
        // dédoublée aux bords, quel que soit le cran de zoom.
        for (double x = Math.floor(r.left() / step) * step; x <= r.right(); x += step) {
            int sx = (int) Math.round(camera.toScreenX(x));
            g.fill(sx, 0, sx + 1, height, color);
        }
        for (double y = Math.floor(r.top() / step) * step; y <= r.bottom(); y += step) {
            int sy = (int) Math.round(camera.toScreenY(y));
            g.fill(0, sy, width, sy + 1, color);
        }
    }
}
