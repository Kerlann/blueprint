package fr.blueprint.client.editor;

import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.NodeTypeLookup;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Le canevas de l'éditeur : grille, boîtes de nœuds en niveau de détail, pan et zoom.
 * Le culling précède tout calcul (coding-standards §5) : un nœud hors champ n'est ni
 * transformé ni dessiné. Le rendu complet des nœuds (pins, littéraux) est la story 5.2.
 */
public final class CanvasWidget {

    // Couleurs de la spec UX §12 — le thème JSON rechargeable est la story 5.7.
    private static final int BACKGROUND = 0xFF1A1B1E;
    private static final int GRID = 0xFF242629;
    private static final int GRID_MAJOR = 0xFF2E3135;
    private static final int NODE_BACKGROUND = 0xFF2B2D31;
    private static final int NODE_BORDER = 0xFF3A3D42;
    private static final int GHOST_BORDER = 0xFFC74A5B;
    private static final int TITLE_COLOR = 0xFFE6E6E6;

    /** Sous ce zoom, plus de titre du tout (UX §3). */
    private static final double TITLE_FADE_ZOOM = 0.35;

    private final Blueprint blueprint;
    private final NodeTypeLookup lookup;
    private final Camera camera = new Camera();
    private final NodeGeometry geometry = new NodeGeometry();
    /** Tampon réutilisé chaque image : pas d'allocation dans la passe de rendu. */
    private final List<NodeGeometry.Box> visible = new ArrayList<>();

    private int width;
    private int height;
    private boolean spaceDown;
    private boolean panning;

    public CanvasWidget(Blueprint blueprint, NodeTypeLookup lookup) {
        this.blueprint = blueprint;
        this.lookup = lookup;
    }

    public Camera camera() {
        return camera;
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /** Recentre sur l'ensemble du graphe (ouverture et touche F). */
    public void frameAll() {
        camera.frameAll(NodeGeometry.boundsOf(geometry.boxes(blueprint, lookup)), width, height);
    }

    // ---------------------------------------------------------------------- rendu

    public void render(GuiGraphics g, Font font) {
        g.fill(0, 0, width, height, BACKGROUND);
        renderGrid(g);
        renderNodes(g, font);
    }

    private void renderGrid(GuiGraphics g) {
        // Les mineures s'effacent sous 0,5× ; les majeures restent (UX §3).
        if (camera.zoom() >= Camera.GRID_FADE_ZOOM) {
            renderGridLines(g, Camera.GRID_STEP, GRID);
        }
        renderGridLines(g, Camera.GRID_MAJOR_STEP, GRID_MAJOR);
    }

    private void renderGridLines(GuiGraphics g, double step, int color) {
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

    private void renderNodes(GuiGraphics g, Font font) {
        List<NodeGeometry.Box> boxes = geometry.boxes(blueprint, lookup);
        Camera.Rect view = camera.visibleRect(width, height);
        visible.clear();
        for (int i = 0; i < boxes.size(); i++) {
            NodeGeometry.Box b = boxes.get(i);
            if (view.intersects(b.x(), b.y(), b.width(), b.height())) {
                visible.add(b);
            }
        }
        double z = camera.zoom();
        for (int i = 0; i < visible.size(); i++) {
            NodeGeometry.Box b = visible.get(i);
            int x1 = (int) Math.round(camera.toScreenX(b.x()));
            int y1 = (int) Math.round(camera.toScreenY(b.y()));
            int x2 = (int) Math.round(camera.toScreenX(b.x() + b.width()));
            int y2 = (int) Math.round(camera.toScreenY(b.y() + b.height()));
            g.fill(x1, y1, x2, y2, b.ghost() ? GHOST_BORDER : NODE_BORDER);
            g.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, NODE_BACKGROUND);
            if (z >= TITLE_FADE_ZOOM) {
                renderTitle(g, font, b, x1, y1, x2, y2, (float) z);
            }
        }
    }

    private void renderTitle(GuiGraphics g, Font font, NodeGeometry.Box b,
                             int x1, int y1, int x2, int y2, float z) {
        Identifier typeId = b.node().typeId();
        // Un fantôme affiche l'identifiant brut ; le message complet arrive en 5.2.
        Component title = b.ghost()
                ? Component.literal(typeId.toString())
                : Component.translatable("blueprint.node." + typeId.getNamespace()
                        + "." + typeId.getPath() + ".name");
        g.enableScissor(x1 + 1, y1 + 1, x2 - 1, y2 - 1);
        g.pose().pushMatrix();
        g.pose().translate(x1 + 4 * z, y1 + 5 * z);
        g.pose().scale(z, z);
        g.drawString(font, title, 0, 0, TITLE_COLOR, false);
        g.pose().popMatrix();
        g.disableScissor();
    }

    // -------------------------------------------------------------------- entrées

    public boolean mouseClicked(MouseButtonEvent e) {
        if (e.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE
                || (spaceDown && e.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
            panning = true;
            return true;
        }
        return false;
    }

    public boolean mouseReleased(MouseButtonEvent e) {
        if (panning) {
            panning = false;
            return true;
        }
        return false;
    }

    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (panning) {
            camera.panByScreen(dx, dy);
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double vAmount) {
        if (vAmount == 0) {
            return false;
        }
        camera.zoomBy(vAmount > 0 ? 1 : -1, mouseX, mouseY);
        return true;
    }

    public boolean keyPressed(KeyEvent e) {
        switch (e.key()) {
            case GLFW.GLFW_KEY_SPACE -> {
                spaceDown = true;
                return true;
            }
            case GLFW.GLFW_KEY_F -> {
                frameAll();
                return true;
            }
            case GLFW.GLFW_KEY_G -> {
                if (e.hasControlDown()) {
                    camera.toggleSnap();
                    return true;
                }
            }
            case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> {
                if (e.hasControlDown()) {
                    camera.zoomBy(1, width / 2.0, height / 2.0);
                    return true;
                }
            }
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> {
                if (e.hasControlDown()) {
                    camera.zoomBy(-1, width / 2.0, height / 2.0);
                    return true;
                }
            }
            default -> {
            }
        }
        return false;
    }

    public boolean keyReleased(KeyEvent e) {
        if (e.key() == GLFW.GLFW_KEY_SPACE) {
            spaceDown = false;
            return true;
        }
        return false;
    }
}
