package fr.blueprint.client.editor;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.client.registry.ClientNodeRegistry;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.Node;
import fr.blueprint.core.graph.NodeTypeLookup;
import fr.blueprint.core.registry.NodeDescriptor;
import net.minecraft.core.Direction;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

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
    private final PaletteState palette;
    private final EditorSession session;
    private final NodeTypeLookup lookup;
    private final Runnable closeRequest;
    private final DiagnosticsState diagnostics = new DiagnosticsState(System::currentTimeMillis);
    /** Tampon réutilisé chaque image : pas d'allocation dans la passe de rendu. */
    private final List<NodeGeometry.Box> visible = new ArrayList<>();

    private final LiteralEditState literalEdit = new LiteralEditState();

    private int width;
    private int height;
    private boolean spaceDown;
    private boolean shiftDown;
    private boolean panning;

    public CanvasWidget(EditorSession session, NodeTypeLookup lookup,
                        ClientNodeRegistry descriptors, Runnable closeRequest) {
        this.session = session;
        this.lookup = lookup;
        this.descriptors = descriptors;
        this.closeRequest = closeRequest;
        this.controller = new CanvasController(session.blueprint(), lookup, camera);
        this.controller.setOnMutation(diagnostics::invalidate);
        // Titres et descriptions traduits une fois à l'ouverture ; l'index reste pur.
        List<NodeSearch.Entry> entries = new ArrayList<>();
        for (NodeDescriptor d : descriptors.descriptors()) {
            entries.add(new NodeSearch.Entry(d.id(), I18n.get(d.titleKey()),
                    I18n.get(d.descKey()), d.category()));
        }
        entries.sort(Comparator.comparing(NodeSearch.Entry::title));
        this.palette = new PaletteState(new NodeSearch(entries), descriptors::descriptor);
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
        // Validation débouncée (5.6b) : jamais dans la frame d'une frappe.
        if (diagnostics.shouldValidate()) {
            diagnostics.accept(fr.blueprint.core.graph.GraphValidator
                    .validate(controller.blueprint(), lookup).diagnostics());
        }
        g.fill(0, 0, width, height, BACKGROUND);
        renderGrid(g);
        WireLayer.renderLinks(g, camera, controller, width, height);
        renderNodes(g, font);
        renderRubber(g);
        WireLayer.renderPreview(g, camera, controller);
        ToolbarWidget.render(g, font, controller.blueprint().id().toString(),
                session.dirty(), session.savable(),
                session.savable() && !diagnostics.blocking(), width);
        DiagnosticsPanel.render(g, font, diagnostics, width, height);
        PalettePopup.render(g, font, palette, width, height);
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
        CanvasController.PinRef wireFrom = controller.wireFrom();
        for (int i = 0; i < visible.size(); i++) {
            NodeGeometry.Box b = visible.get(i);
            UUID uuid = b.node().uuid();
            NodeWidget.PinDimmer dimmer = wireFrom == null ? null : (pin, output) -> {
                if (uuid.equals(wireFrom.node()) && pin.equals(wireFrom.pin())
                        && output == wireFrom.output()) {
                    return false; // le pin saisi reste net
                }
                return !controller.canConnect(wireFrom, uuid, pin, output);
            };
            NodeDescriptor desc = descriptors.descriptor(b.node().typeId());
            NodeWidget.LiteralProvider literals = desc == null ? null
                    : pin -> literalToShow(b.node(), desc, pin);
            NodeWidget.render(g, font, b, desc,
                    controller.selection().isSelected(uuid), z,
                    camera.toScreenX(b.x()), camera.toScreenY(b.y()), dimmer,
                    literals, literalEdit, diagnostics.outlineColor(uuid));
        }
    }

    /** Valeur affichée pour un pin d'entrée : littéral posé, sinon défaut ; null si câblé. */
    private @Nullable LiteralValue literalToShow(Node node, NodeDescriptor desc, String pin) {
        if (isWired(node.uuid(), pin)) {
            return null;
        }
        LiteralValue set = node.literal(pin);
        if (set != null) {
            return set;
        }
        for (int i = 0; i < desc.inputs().size(); i++) {
            if (desc.inputs().get(i).name().equals(pin)) {
                return desc.inputs().get(i).defaultValue();
            }
        }
        return null;
    }

    /** Sans allocation (appelé dans la passe de rendu), contrairement à linksInto. */
    private boolean isWired(UUID node, String pin) {
        for (Link link : controller.blueprint().links()) {
            if (link.toNode().equals(node) && link.toPin().equals(pin)) {
                return true;
            }
        }
        return false;
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
        if (literalEdit.isOpen()) {
            // Clic ailleurs = valider (AC2) ; saisie invalide = abandonner (AC3).
            commitLiteral(false);
        }
        if (palette.isOpen()) {
            if (e.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                int row = PalettePopup.rowAt(palette, e.x(), e.y(), width, height);
                if (row >= 0) {
                    palette.select(row);
                    insertFromPalette();
                    return true;
                }
                if (!PalettePopup.contains(palette, e.x(), e.y(), width, height)) {
                    palette.close();
                }
                return true;
            }
            palette.close();
            return true;
        }
        if (e.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && e.y() < ToolbarWidget.HEIGHT) {
            handleToolbar(ToolbarWidget.actionAt(minecraftFont(), e.x(), e.y(), width));
            return true;
        }
        if (e.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && DiagnosticsPanel.barContains(e.y(), height)) {
            diagnostics.toggleExpanded();
            return true;
        }
        if (e.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && diagnostics.expanded()) {
            int row = DiagnosticsPanel.rowAt(diagnostics, e.y(), height);
            if (row >= 0) {
                var node = DiagnosticsState.nodeOf(diagnostics.report().get(row));
                if (node != null) {
                    controller.focusNode(node, width, height);
                }
                return true;
            }
        }
        if (e.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE
                || (spaceDown && e.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
            panning = true;
            return true;
        }
        if (e.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            palette.open(e.x(), e.y(), camera.toWorldX(e.x()), camera.toWorldY(e.y()), null);
            return true;
        }
        if (e.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            double wx = camera.toWorldX(e.x());
            double wy = camera.toWorldY(e.y());
            if (openLiteralEdit(wx, wy)) {
                return true;
            }
            controller.press(wx, wy, e.hasShiftDown(), e.hasAltDown());
            return true;
        }
        return false;
    }

    /** Clic sur une zone littérale : bascule le bool, ouvre le champ ou l'énum. */
    private boolean openLiteralEdit(double wx, double wy) {
        CanvasController.LiteralRef ref = controller.literalAt(wx, wy);
        if (ref == null) {
            return false;
        }
        Node node = controller.blueprint().node(ref.node());
        NodeDescriptor desc = node == null ? null : descriptors.descriptor(node.typeId());
        LiteralValue current = node == null || desc == null ? null
                : literalToShow(node, desc, ref.pin());
        if (ref.type() == PinTypes.BOOL) {
            boolean on = current != null && current.value() instanceof Boolean b && b;
            controller.setLiteral(ref.node(), ref.pin(), LiteralValue.of(PinTypes.BOOL, !on));
            return true;
        }
        if (ref.type() == PinTypes.DIRECTION) {
            literalEdit.openEnum(ref.node(), ref.pin(), ref.row(),
                    current != null && current.value() instanceof Direction d ? d : null);
            return true;
        }
        if (LiteralEditState.editableAsText(ref.type())) {
            literalEdit.openText(ref.node(), ref.pin(), ref.row(), ref.type(),
                    LiteralEditState.display(ref.type(), current));
            return true;
        }
        return false; // type 5.2c : le clic retombe sur la sélection du nœud
    }

    private static Font minecraftFont() {
        return net.minecraft.client.Minecraft.getInstance().font;
    }

    private void actionBar(String key, Object... args) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable(key, args), true);
        }
    }

    private void handleToolbar(@Nullable ToolbarWidget.Action action) {
        if (action == null) {
            return;
        }
        switch (action) {
            case COMPILE -> {
                // Validation immédiate, sans attendre le débouncé.
                diagnostics.accept(fr.blueprint.core.graph.GraphValidator
                        .validate(controller.blueprint(), lookup).diagnostics());
                actionBar("blueprint.editor.diag.summary", diagnostics.errors(),
                        diagnostics.warnings());
            }
            case SAVE -> {
                if (session.save()) {
                    actionBar("blueprint.editor.saved", controller.blueprint().id().toString());
                } else {
                    actionBar("blueprint.editor.toolbar.unsavable");
                }
            }
            case TEST -> {
                if (!session.savable()) {
                    actionBar("blueprint.editor.toolbar.unsavable");
                } else if (diagnostics.blocking()) {
                    // Tester est grisé : la raison passe par la barre d'action (U2).
                    actionBar("blueprint.editor.toolbar.blocked", diagnostics.errors());
                } else if (session.test()) {
                    actionBar("blueprint.editor.toolbar.tested",
                            controller.blueprint().id().toString());
                }
            }
            case CLOSE -> closeRequest.run();
        }
    }

    /** Valide l'édition courante ; {@code keepOnInvalid} garde le champ rouge ouvert. */
    private void commitLiteral(boolean keepOnInvalid) {
        LiteralValue value = literalEdit.parse();
        if (value != null) {
            controller.setLiteral(literalEdit.node(), literalEdit.pin(), value);
            literalEdit.close();
        } else if (!keepOnInvalid) {
            literalEdit.close();
        }
    }

    public boolean mouseReleased(MouseButtonEvent e) {
        if (panning) {
            panning = false;
            return true;
        }
        if (controller.gesture() != CanvasController.Gesture.NONE) {
            CanvasController.WireDrop drop = controller.release(e.hasShiftDown());
            if (drop != null) {
                // FR29 : lien lâché dans le vide → palette filtrée par le type transporté.
                palette.open(camera.toScreenX(drop.worldX()), camera.toScreenY(drop.worldY()),
                        drop.worldX(), drop.worldY(), drop.from());
            }
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
        if (literalEdit.isOpen()) {
            // Molette : ±1 sur un champ numérique (Shift = ±10), cycle sur une énum.
            if (literalEdit.mode() == LiteralEditState.Mode.ENUM) {
                literalEdit.moveOption(vAmount > 0 ? -1 : 1);
            } else {
                literalEdit.adjustNumber((vAmount > 0 ? 1 : -1) * (shiftDown ? 10L : 1L));
            }
            return true;
        }
        camera.zoomBy(vAmount > 0 ? 1 : -1, mouseX, mouseY);
        return true;
    }

    public boolean keyPressed(KeyEvent e) {
        if (e.key() == GLFW.GLFW_KEY_LEFT_SHIFT || e.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            shiftDown = true;
        }
        if (literalEdit.isOpen()) {
            switch (e.key()) {
                case GLFW.GLFW_KEY_ESCAPE -> literalEdit.close();
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> commitLiteral(true);
                case GLFW.GLFW_KEY_BACKSPACE -> literalEdit.backspace();
                case GLFW.GLFW_KEY_UP -> literalEdit.moveOption(-1);
                case GLFW.GLFW_KEY_DOWN -> literalEdit.moveOption(1);
                default -> {
                }
            }
            return true; // le clavier de l'éditeur est suspendu pendant l'édition (AC4)
        }
        if (palette.isOpen()) {
            switch (e.key()) {
                case GLFW.GLFW_KEY_ESCAPE -> palette.close();
                case GLFW.GLFW_KEY_UP -> palette.moveSelection(-1);
                case GLFW.GLFW_KEY_DOWN -> palette.moveSelection(1);
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> insertFromPalette();
                case GLFW.GLFW_KEY_BACKSPACE -> palette.backspace();
                default -> {
                }
            }
            return true; // la palette capte tout le clavier tant qu'elle est ouverte
        }
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
            case GLFW.GLFW_KEY_Z -> {
                if (e.hasControlDown()) {
                    if (e.hasShiftDown()) {
                        controller.redo();
                    } else {
                        controller.undo();
                    }
                    return true;
                }
            }
            case GLFW.GLFW_KEY_Y -> {
                if (e.hasControlDown()) {
                    controller.redo();
                    return true;
                }
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
        if (e.key() == GLFW.GLFW_KEY_LEFT_SHIFT || e.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            shiftDown = false;
        }
        if (e.key() == GLFW.GLFW_KEY_SPACE) {
            spaceDown = false;
            return true;
        }
        return false;
    }

    public boolean charTyped(CharacterEvent e) {
        if (literalEdit.isOpen() && e.isAllowedChatCharacter()) {
            literalEdit.type(e.codepointAsString());
            return true;
        }
        if (palette.isOpen() && e.isAllowedChatCharacter()) {
            palette.type(e.codepointAsString());
            return true;
        }
        return false;
    }

    private void insertFromPalette() {
        NodeSearch.Entry entry = palette.selectedEntry();
        if (entry != null) {
            controller.insertNode(entry.id(), palette.worldX(), palette.worldY(),
                    palette.wireFrom());
        }
        palette.close();
    }
}
