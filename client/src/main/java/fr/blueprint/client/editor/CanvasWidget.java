package fr.blueprint.client.editor;

import fr.blueprint.client.registry.ClientNodeRegistry;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.NodeTypeLookup;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Le canevas de l'éditeur : grille, nœuds rendus depuis leurs descripteurs, pan/zoom,
 * sélection et déplacement. Le culling précède tout calcul (coding-standards §5) ; la
 * logique d'interaction vit dans {@link CanvasController} (pur, testé headless) — ce
 * widget convertit écran→monde et dessine.
 */
public final class CanvasWidget {

    // Couleurs de la spec UX §12 — le thème JSON rechargeable est la story 5.7.
    private static final int BACKGROUND = 0xFF1A1B1E;
    private static final int GRID = 0xFF242629;
    private static final int GRID_MAJOR = 0xFF2E3135;
    private static final int RUBBER_FILL = 0x337AA2F7;
    private static final int RUBBER_BORDER = 0xFF7AA2F7;

    private final ClientNodeRegistry descriptors;
    private final Camera camera = new Camera();
    private final CanvasController controller;
    /** Tampon réutilisé chaque image : pas d'allocation dans la passe de rendu. */
    private final List<NodeGeometry.Box> visible = new ArrayList<>();

    private int width;
    private int height;
    private boolean spaceDown;
    private boolean panning;

    public CanvasWidget(Blueprint blueprint, NodeTypeLookup lookup, ClientNodeRegistry descriptors) {
        this.descriptors = descriptors;
        this.controller = new CanvasController(blueprint, lookup, camera);
    }

    public Camera camera() {
        return camera;
    }

    public CanvasController controller() {
        return controller;
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /** Recentre sur l'ensemble du graphe (ouverture et touche F). */
    public void frameAll() {
        camera.frameAll(NodeGeometry.boundsOf(controller.boxes()), width, height);
    }

    // ---------------------------------------------------------------------- rendu

    public void render(GuiGraphics g, Font font) {
        g.fill(0, 0, width, height, BACKGROUND);
        renderGrid(g);
        renderNodes(g, font);
        renderRubber(g);
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
        List<NodeGeometry.Box> boxes = controller.boxes();
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
            NodeWidget.render(g, font, b, descriptors.descriptor(b.node().typeId()),
                    controller.selection().isSelected(b.node().uuid()), z,
                    camera.toScreenX(b.x()), camera.toScreenY(b.y()));
        }
    }

    private void renderRubber(GuiGraphics g) {
        Camera.Rect r = controller.rubberRect();
        if (r == null) {
            return;
        }
        int x1 = (int) Math.round(camera.toScreenX(r.left()));
        int y1 = (int) Math.round(camera.toScreenY(r.top()));
        int x2 = (int) Math.round(camera.toScreenX(r.right()));
        int y2 = (int) Math.round(camera.toScreenY(r.bottom()));
        g.fill(x1, y1, x2, y2, RUBBER_FILL);
        g.fill(x1, y1, x2, y1 + 1, RUBBER_BORDER);
        g.fill(x1, y2 - 1, x2, y2, RUBBER_BORDER);
        g.fill(x1, y1, x1 + 1, y2, RUBBER_BORDER);
        g.fill(x2 - 1, y1, x2, y2, RUBBER_BORDER);
    }

    // -------------------------------------------------------------------- entrées

    public boolean mouseClicked(MouseButtonEvent e) {
        if (e.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE
                || (spaceDown && e.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
            panning = true;
            return true;
        }
        if (e.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            controller.press(camera.toWorldX(e.x()), camera.toWorldY(e.y()), e.hasShiftDown());
            return true;
        }
        return false;
    }

    public boolean mouseReleased(MouseButtonEvent e) {
        if (panning) {
            panning = false;
            return true;
        }
        if (controller.gesture() != CanvasController.Gesture.NONE) {
            controller.release(e.hasShiftDown());
            return true;
        }
        return false;
    }

    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (panning) {
            camera.panByScreen(dx, dy);
            return true;
        }
        if (controller.gesture() != CanvasController.Gesture.NONE) {
            controller.drag(camera.toWorldX(e.x()), camera.toWorldY(e.y()));
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
            case GLFW.GLFW_KEY_DELETE -> {
                controller.deleteSelection();
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
