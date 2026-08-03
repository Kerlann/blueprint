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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
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

    // Couleurs du canevas : jetons du thème (5.7) ; le chrome des panneaux reste
    // en constantes locales (consigné).
    private static final int RUBBER_FILL = 0x337AA2F7;
    private static final int RUBBER_BORDER = 0xFF7AA2F7;

    private final ClientNodeRegistry descriptors;
    private final Camera camera = new Camera();
    private final CanvasController controller;
    private final PaletteState palette;
    private final EditorSession session;
    private final NodeTypeLookup lookup;
    private final fr.blueprint.core.registry.PluginLoader.LoadedRegistries registries;
    private final Runnable closeRequest;
    private final DiagnosticsState diagnostics = new DiagnosticsState(System::currentTimeMillis);
    private final ScriptViewState scriptView = new ScriptViewState(System::currentTimeMillis);
    /** Tampon réutilisé chaque image : pas d'allocation dans la passe de rendu. */
    private final List<NodeGeometry.Box> visible = new ArrayList<>();

    private final LiteralEditState literalEdit = new LiteralEditState();
    private final VariablePanelState varPanel;
    private final DetailsPanelState details;

    private final TapTracker spaceTap = new TapTracker();
    private final java.nio.file.Path configDir;
    /** Renommage d'une boîte de commentaire (double-clic sur son titre). */
    private @Nullable UUID commentRenaming;
    private String commentBuffer = "";
    private final GotoState gotoState = new GotoState();
    private int minimapLeft;
    private int minimapTop;
    private final PickerState picker = new PickerState();
    /** Items et blocs du jeu pour les sélecteurs riches — construits à la demande. */
    private final RegistryCatalog catalog = new RegistryCatalog();

    private int width;
    private int height;
    private boolean shiftDown;
    private boolean panning;
    private boolean panelVisible = true;
    /** Variable en cours de glisser depuis le panneau (déposée en Get/Set). */
    private @Nullable String dragVar;

    private final ClipboardCodec clipboard;
    private double lastMouseX;
    private double lastMouseY;

    /** Débogueur (9.1b) : ce que le serveur pousse, ce que le canevas dessine. */
    private final DebugView debug;

    // Défilement des trois panneaux (5.12) : au-delà d'une trentaine de lignes, le
    // contenu était purement inatteignable.
    private final HoverTracker hover = new HoverTracker();
    private final PanelScroll varScroll = new PanelScroll();
    private final PanelScroll detailsScroll = new PanelScroll();
    private final PanelScroll diagScroll = new PanelScroll();

    public DebugView debug() {
        return debug;
    }

    public CanvasWidget(EditorSession session, NodeTypeLookup lookup,
                        ClientNodeRegistry descriptors,
                        fr.blueprint.core.registry.PluginLoader.LoadedRegistries registries,
                        Runnable closeRequest) {
        this.session = session;
        this.lookup = lookup;
        this.registries = registries;
        this.descriptors = descriptors;
        this.clipboard = new ClipboardCodec(registries);
        this.closeRequest = closeRequest;
        this.debug = new DebugView(session.blueprint().id());
        this.controller = new CanvasController(session.blueprint(), lookup, camera);
        this.controller.setOnMutation(() -> {
            diagnostics.invalidate();
            scriptView.invalidate();
        });
        // Titres et descriptions traduits une fois à l'ouverture ; l'index reste pur.
        List<NodeSearch.Entry> entries = new ArrayList<>();
        for (NodeDescriptor d : descriptors.descriptors()) {
            entries.add(new NodeSearch.Entry(d.id(), I18n.get(d.titleKey()),
                    I18n.get(d.descKey()), d.category()));
        }
        entries.sort(Comparator.comparing(NodeSearch.Entry::title));
        this.configDir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();
        // Thème rechargé à chaque ouverture : modifiable sans recompiler (5.7).
        fr.blueprint.client.theme.Theme.set(
                fr.blueprint.client.theme.ThemeLoader.load(configDir));
        this.palette = new PaletteState(new NodeSearch(entries), descriptors::descriptor,
                fr.blueprint.client.config.PalettePrefs.load(configDir),
                () -> session.blueprint().meta().permissionCap());
        this.varPanel = new VariablePanelState(session.blueprint(), lookup, controller::applyOp,
                controller::beginGesture, controller::endGesture);
        this.details = new DetailsPanelState(session.blueprint(), descriptors::descriptor,
                controller::applyOp, I18n::get);
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

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        // Validation débouncée (5.6b) : jamais dans la frame d'une frappe.
        if (diagnostics.shouldValidate()) {
            diagnostics.accept(fr.blueprint.core.graph.GraphValidator
                    .validate(controller.blueprint(), lookup).diagnostics());
        }
        g.fill(0, 0, width, height, fr.blueprint.client.theme.Theme.current().canvasBackground());
        GridLayer.render(g, camera, width, height);
        renderComments(g, font);
        WireLayer.renderLinks(g, camera, controller, width, height);
        renderNodes(g, font);
        renderRubber(g);
        WireLayer.renderPreview(g, camera, controller);
        ToolbarWidget.render(g, font, controller.blueprint().id().toString(),
                session.dirty(), session.savable(),
                session.savable() && !diagnostics.blocking(), width);
        DiagnosticsPanel.render(g, font, diagnostics, width, height,
                diagScroll.offset(diagnostics.report().size(), DiagnosticsPanel.VISIBLE_ROWS));
        if (scriptView.shouldRegenerate()) {
            scriptView.regenerate(controller.blueprint(), lookup);
        }
        if (scriptView.visible()) {
            UUID first = controller.selection().ids().isEmpty() ? null
                    : controller.selection().ids().iterator().next();
            scriptView.syncSelection(first, ScriptView.visibleLines(height));
        }
        if (panelVisible) {
            VariablePanel.render(g, font, varPanel, height,
                    varScroll.offset(varPanel.rows().size(), VariablePanel.visibleRows(height)));
            if (!scriptView.visible()) {
                var detailRows = details.rows(controller.selection().ids());
                DetailsPanel.render(g, font, detailRows, width, height, literalEdit,
                        detailsScroll.offset(detailRows.size(), DetailsPanel.visibleRows(height)));
            }
        }
        ScriptView.render(g, font, scriptView, width, height);
        int rightPanel = scriptView.visible() ? width - ScriptView.panelLeft(width)
                : panelVisible ? DetailsPanel.WIDTH : 0;
        minimapLeft = Minimap.left(width, rightPanel);
        minimapTop = Minimap.top(height);
        Minimap.render(g, camera, controller.boxes(), minimapLeft, minimapTop,
                canvasWidth(), height);
        renderGoto(g, font);
        renderEnumOptions(g, font);
        PalettePopup.render(g, font, palette, width, height);
        RegistryPickerPopup.render(g, font, picker, this::iconOf, width, height, mouseX, mouseY);
        // En dernier : une infobulle passe par-dessus tout, sinon elle disparaît sous
        // le nœud voisin dès qu'on survole le bord d'un nœud. Et seulement souris
        // posée : pendant un geste ou un déplacement, elle ne ferait que gêner.
        boolean settled = hover.settled(mouseX, mouseY, System.currentTimeMillis());
        if (settled && controller.gesture() == CanvasController.Gesture.NONE && !panning) {
            Tooltip.render(g, font, tooltipAt(font, mouseX, mouseY),
                    mouseX, mouseY, width, height);
        }
    }

    /**
     * Ce que dit le survol, du plus précis au plus général : un pin, puis un champ
     * éditable, puis le nœud lui-même. Rien au-dessus d'un panneau ou d'une fenêtre —
     * une bulle par-dessus la palette gênerait plus qu'elle n'aiderait.
     */
    private List<String> tooltipAt(Font font, double mx, double my) {
        if (palette.isOpen() || picker.isOpen() || gotoState.isOpen()) {
            return List.of();
        }
        if (my < ToolbarWidget.HEIGHT) {
            ToolbarWidget.Action action =
                    ToolbarWidget.actionAt(font, mx, my, width);
            return action == null ? List.of()
                    : List.of(ToolbarWidget.title(action), ToolbarWidget.hint(action));
        }
        if (my >= height - DiagnosticsPanel.BAR_HEIGHT
                || (panelVisible && (mx < VariablePanel.WIDTH || mx >= width - DetailsPanel.WIDTH))) {
            return List.of();
        }
        double wx = camera.toWorldX(mx);
        double wy = camera.toWorldY(my);

        CanvasController.PinRef pin = controller.pinAt(wx, wy);
        if (pin != null) {
            List<String> lines = new ArrayList<>();
            lines.add(pin.pin());
            lines.add(I18n.get(pin.type().translationKey()));
            if (!pin.output() && pin.kind() == fr.blueprint.api.pin.PinKind.DATA
                    && !isWired(pin.node(), pin.pin())) {
                lines.add(I18n.get("blueprint.editor.tip.unwired"));
            }
            return lines;
        }

        CanvasController.LiteralRef literal = controller.literalAt(wx, wy);
        if (literal != null) {
            return List.of(literal.pin(), I18n.get("blueprint.editor.tip.click_to_edit"));
        }

        NodeGeometry.Box box = controller.hitTest(wx, wy);
        if (box == null) {
            Link wire = controller.linkAt(wx, wy);
            return wire == null ? List.of()
                    : List.of(wire.fromPin() + " → " + wire.toPin(),
                            I18n.get("blueprint.editor.tip.link"));
        }
        UUID uuid = box.node().uuid();
        NodeDescriptor desc = descriptors.descriptor(box.node().typeId());
        List<String> lines = new ArrayList<>();
        if (desc == null) {
            // Fantôme : dire QUEL mod manque, c'est toute l'information utile (FR41).
            lines.add(box.node().typeId().toString());
            lines.add(I18n.get("blueprint.editor.tip.ghost", box.node().typeId().getNamespace()));
            return lines;
        }
        lines.add(I18n.get(desc.titleKey()));
        String description = I18n.get(desc.descKey());
        if (!description.equals(desc.descKey()) && !description.isBlank()) {
            lines.add(description);
        }
        // La pastille de l'en-tête n'a de sens que si son niveau se lit quelque part.
        if (NodeWidget.permissionBadge(desc.permission()) != 0) {
            lines.add(I18n.get("blueprint.editor.tip.permission",
                    I18n.get("blueprint.permission." + desc.permission().name().toLowerCase())));
        }
        // Quel mod fournit ce nœud : invisible autrement, et c'est ce qui explique
        // qu'il devienne un fantôme sur un autre serveur.
        if (!"blueprint".equals(box.node().typeId().getNamespace())) {
            lines.add(I18n.get("blueprint.editor.tip.provider",
                    box.node().typeId().getNamespace()));
        }
        // L'erreur du nœud prime sur sa description : c'est ce qu'on cherche quand on
        // survole un nœud entouré de rouge.
        for (var diagnostic : diagnostics.report()) {
            if (uuid.equals(DiagnosticsState.nodeOf(diagnostic))) {
                lines.add(I18n.get(diagnostic.translationKey(), diagnostic.args().toArray()));
                break;
            }
        }
        if (debug.debugging() && !debug.valuesOf(uuid).isEmpty()) {
            lines.addAll(debug.valuesOf(uuid));
        }
        return lines;
    }

    private int canvasWidth() {
        return scriptView.visible() ? ScriptView.panelLeft(width) : width;
    }

    /** « Aller au nœud » (Ctrl+F, 5.7) : petite recherche centrée en haut. */
    private void renderGoto(GuiGraphics g, Font font) {
        if (!gotoState.isOpen()) {
            return;
        }
        int w = 200;
        int x = (width - w) / 2;
        int y = ToolbarWidget.HEIGHT + 8;
        int h = 16 + Math.max(1, gotoState.results().size()) * 11 + 3;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF3A3D42);
        g.fill(x, y, x + w, y + h, 0xF01A1B1E);
        g.drawString(font, I18n.get("blueprint.editor.goto") + " " + gotoState.query() + "_",
                x + 4, y + 3, 0xFFE6E6E6, false);
        for (int i = 0; i < gotoState.results().size(); i++) {
            int rowY = y + 16 + i * 11;
            if (i == gotoState.selectedIndex()) {
                g.fill(x + 1, rowY - 1, x + w - 1, rowY + 10, 0xFF2F3A55);
            }
            g.drawString(font, font.plainSubstrByWidth(
                    gotoState.results().get(i).title(), w - 8), x + 4, rowY, 0xFFD5D8DC, false);
        }
    }

    private void openGoto() {
        List<GotoState.Target> targets = new ArrayList<>();
        for (NodeGeometry.Box b : controller.boxes()) {
            NodeDescriptor desc = descriptors.descriptor(b.node().typeId());
            targets.add(new GotoState.Target(b.node().uuid(),
                    desc != null ? I18n.get(desc.titleKey()) : b.node().typeId().toString()));
        }
        gotoState.open(targets);
    }

    /** Liste déroulante d'une énumération en édition (direction, 5.2c). */
    private void renderEnumOptions(GuiGraphics g, Font font) {
        if (literalEdit.mode() != LiteralEditState.Mode.ENUM) {
            return;
        }
        NodeGeometry.Box box = controller.boxOf(literalEdit.node());
        if (box == null) {
            return;
        }
        Camera.Rect zone = NodeGeometry.literalZone(box, literalEdit.row());
        int x1 = (int) Math.round(camera.toScreenX(zone.left()));
        int x2 = (int) Math.round(camera.toScreenX(zone.right()));
        int y = (int) Math.round(camera.toScreenY(zone.bottom()));
        List<Direction> options = literalEdit.options();
        g.fill(x1 - 1, y - 1, x2 + 1, y + options.size() * 12 + 1, 0xFF3A3D42);
        g.fill(x1, y, x2, y + options.size() * 12, 0xF01A1B1E);
        for (int i = 0; i < options.size(); i++) {
            if (i == literalEdit.optionIndex()) {
                g.fill(x1, y + i * 12, x2, y + (i + 1) * 12, 0xFF2F3A55);
            }
            g.drawString(font, options.get(i).getName(), x1 + 3, y + i * 12 + 2,
                    0xFFD5D8DC, false);
        }
    }

    /** Indice d'option de l'énum sous la souris, ou −1. */
    private int enumOptionAt(double mx, double my) {
        if (literalEdit.mode() != LiteralEditState.Mode.ENUM) {
            return -1;
        }
        NodeGeometry.Box box = controller.boxOf(literalEdit.node());
        if (box == null) {
            return -1;
        }
        Camera.Rect zone = NodeGeometry.literalZone(box, literalEdit.row());
        double x1 = camera.toScreenX(zone.left());
        double x2 = camera.toScreenX(zone.right());
        double y = camera.toScreenY(zone.bottom());
        if (mx < x1 || mx >= x2 || my < y || my >= y + literalEdit.options().size() * 12) {
            return -1;
        }
        return (int) ((my - y) / 12);
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
            // Le nœud en pause prime sur le liseré de diagnostic : c'est LUI qu'on
            // cherche des yeux quand l'exécution est arrêtée.
            int outline = debug.isPaused(uuid) ? fr.blueprint.client.theme.Theme.current().debugActive()
                    : diagnostics.outlineColor(uuid);
            NodeWidget.render(g, font, b, desc,
                    controller.selection().isSelected(uuid), z,
                    camera.toScreenX(b.x()), camera.toScreenY(b.y()), dimmer,
                    literals, literalEdit, outline);
            if (debug.debugging()) {
                renderDebugOverlay(g, font, b, uuid);
            }
        }
    }

    /**
     * Pastille de point d'arrêt et valeurs vues, dessinées PAR-DESSUS le nœud, dans
     * l'espace écran : le texte reste lisible quel que soit le zoom, alors que le
     * contenu du nœud, lui, suit l'échelle.
     */
    private void renderDebugOverlay(GuiGraphics g, Font font, NodeGeometry.Box box, UUID uuid) {
        int x = (int) Math.round(camera.toScreenX(box.x()));
        int y = (int) Math.round(camera.toScreenY(box.y()));
        if (debug.hasBreakpoint(uuid)) {
            // Pastille rouge en haut à gauche, hors du cadre — jamais sur le titre.
            g.fill(x - 7, y + 1, x - 1, y + 7, 0xFFF7768E);
        }
        List<String> values = debug.valuesOf(uuid);
        if (values.isEmpty() || camera.zoom() < 0.5) {
            return;   // trop dézoomé : le texte serait illisible, on ne l'écrit pas
        }
        int lineY = y - 2 - values.size() * 9;
        for (String line : values) {
            int w = font.width(line);
            g.fill(x - 1, lineY - 1, x + w + 2, lineY + 8, 0xC0101318);
            g.drawString(font, line, x + 1, lineY, 0xFFFFD866, false);
            lineY += 9;
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

    /** Boîtes de commentaire (5.7) : sous les nœuds, translucides, titre + poignée. */
    private void renderComments(GuiGraphics g, Font font) {
        for (fr.blueprint.core.graph.CommentBox box : controller.blueprint().comments()) {
            int x1 = (int) Math.round(camera.toScreenX(box.position().x()));
            int y1 = (int) Math.round(camera.toScreenY(box.position().y()));
            int x2 = (int) Math.round(camera.toScreenX(box.position().x() + box.size().x()));
            int y2 = (int) Math.round(camera.toScreenY(box.position().y() + box.size().y()));
            int titleH = (int) Math.round(CanvasController.COMMENT_TITLE * camera.zoom());
            boolean selected = box.uuid().equals(controller.selectedComment());
            g.fill(x1, y1, x2, y2, box.color());
            g.fill(x1, y1, x2, y1 + titleH, (box.color() & 0x00FFFFFF) | 0x66000000);
            int border = selected ? 0xFF7AA2F7 : 0x887DCFFF;
            g.fill(x1, y1, x2, y1 + 1, border);
            g.fill(x1, y2 - 1, x2, y2, border);
            g.fill(x1, y1, x1 + 1, y2, border);
            g.fill(x2 - 1, y1, x2, y2, border);
            int grip = (int) Math.round(CanvasController.COMMENT_GRIP * camera.zoom());
            g.fill(x2 - grip, y2 - 2, x2, y2, border);
            g.fill(x2 - 2, y2 - grip, x2, y2, border);
            if (camera.zoom() >= 0.35) {
                String title = box.uuid().equals(commentRenaming)
                        ? commentBuffer + "_" : box.text();
                g.drawString(font, font.plainSubstrByWidth(title, Math.max(10, x2 - x1 - 8)),
                        x1 + 4, y1 + 2, 0xFFE6E6E6, false);
            }
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

    /**
     * Routage de la souris, du plus modal au plus général : un sélecteur ouvert prime
     * sur la palette, qui prime sur le chrome (barre d'outils, diagnostics), qui prime
     * sur les panneaux, qui priment sur le canevas. Cet ordre EST la règle — le reste
     * n'est que la mise en œuvre de chaque zone.
     */
    public boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        // Un clic change ce qu'il y a sous le curseur : réafficher l'ancienne infobulle
        // par-dessus le résultat serait un mensonge de plus.
        hover.reset();
        if (picker.isOpen()) {
            return clickInPicker(e);
        }
        if (gotoState.isOpen()) {
            gotoState.close();
            return true;
        }
        if (clickOnMinimap(e)) {
            return true;
        }
        // Une liste déroulante d'énumération avale le clic qui la vise ; sinon on
        // retombe sur le cas général « clic ailleurs = valider ».
        if (literalEdit.isOpen() && literalEdit.mode() == LiteralEditState.Mode.ENUM) {
            int option = enumOptionAt(e.x(), e.y());
            if (option >= 0) {
                literalEdit.selectOption(option);
                commitLiteral(true);
                return true;
            }
        }
        commitPendingEdits();
        if (palette.isOpen()) {
            return clickInPalette(e);
        }
        if (clickOnChrome(e)) {
            return true;
        }
        if (clickOnPanels(e, doubled)) {
            return true;
        }
        if (e.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            palette.open(e.x(), e.y(), camera.toWorldX(e.x()), camera.toWorldY(e.y()), null);
            return true;
        }
        if (e.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return clickOnCanvas(e, doubled);
        }
        return false;
    }

    /** Toute édition en cours se valide dès qu'on clique ailleurs (AC2, 5.2b/5.5/5.10). */
    private void commitPendingEdits() {
        if (literalEdit.isOpen()) {
            // Saisie invalide = abandonner plutôt que garder un champ rouge (AC3).
            commitLiteral(false);
        }
        if (varPanel.isRenaming()) {
            varPanel.commitRename();
        }
        if (details.isEditingMeta()) {
            details.commitMetaEdit();
        }
    }

    private boolean clickInPicker(MouseButtonEvent e) {
        if (e.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            int cell = RegistryPickerPopup.cellAt(picker, e.x(), e.y(), width, height);
            if (cell >= 0 && picker.at(cell) != null) {
                pickFromRegistry(picker.at(cell));
            } else if (!RegistryPickerPopup.contains(e.x(), e.y(), width, height)) {
                picker.close();
            }
        } else {
            picker.close();
        }
        return true;
    }

    private boolean clickOnMinimap(MouseButtonEvent e) {
        if (e.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT
                || !Minimap.contains(e.x(), e.y(), minimapLeft, minimapTop)
                || controller.boxes().isEmpty()) {
            return false;
        }
        double[] world = Minimap.toWorld(NodeGeometry.boundsOf(controller.boxes()),
                e.x() - minimapLeft, e.y() - minimapTop);
        camera.centerOn(world[0], world[1], canvasWidth(), height);
        return true;
    }

    private boolean clickInPalette(MouseButtonEvent e) {
        if (e.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            palette.close();
            return true;
        }
        int itemIndex = PalettePopup.rowAt(palette, e.x(), e.y(), width, height);
        if (itemIndex >= 0) {
            switch (palette.items().get(itemIndex)) {
                case PaletteState.Item.Category(String name, int c, boolean ex) ->
                        palette.toggleCategory(name);
                case PaletteState.Item.EntryItem(var entry, boolean fav, boolean blocked) -> {
                    if (PalettePopup.starAt(palette, e.x(), width)) {
                        palette.toggleFavorite(entry.id());
                        palette.prefs().save(configDir);
                    } else {
                        palette.select(palette.entryIndexOf(itemIndex));
                        insertFromPalette(!e.hasControlDown());
                    }
                }
                default -> {
                }
            }
            return true;
        }
        if (!PalettePopup.contains(palette, e.x(), e.y(), width, height)) {
            palette.close();
        }
        return true;
    }

    /** Barre d'outils et barre de diagnostics : le chrome au-dessus du canevas. */
    private boolean clickOnChrome(MouseButtonEvent e) {
        if (e.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (e.y() < ToolbarWidget.HEIGHT) {
            handleToolbar(ToolbarWidget.actionAt(minecraftFont(), e.x(), e.y(), width));
            return true;
        }
        if (DiagnosticsPanel.barContains(e.y(), height)) {
            diagnostics.toggleExpanded();
            return true;
        }
        if (diagnostics.expanded()) {
            int row = DiagnosticsPanel.rowAt(diagnostics, e.y(), height,
                    diagScroll.offset(diagnostics.report().size(), DiagnosticsPanel.VISIBLE_ROWS));
            if (row >= 0) {
                var node = DiagnosticsState.nodeOf(diagnostics.report().get(row));
                if (node != null) {
                    // Recentrer dans la zone canevas réelle (vue script comprise, QA 5.6b).
                    controller.focusNode(node, canvasWidth(), height);
                }
                return true;
            }
        }
        return false;
    }

    /** Pan, panneau des variables, vue script, panneau de détails. */
    private boolean clickOnPanels(MouseButtonEvent e, boolean doubled) {
        if (e.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE
                || (spaceTap.isDown() && e.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
            spaceTap.use(); // Espace sert au pan : ce n'était pas un tap-palette
            panning = true;
            return true;
        }
        if (e.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (panelVisible && VariablePanel.contains(e.x(), e.y(), height)) {
            handleVariablePanelClick(e, doubled);
            return true;
        }
        if (scriptView.visible() && ScriptView.contains(e.x(), e.y(), width, height)) {
            handleScriptViewClick(e);
            return true;
        }
        if (panelVisible && !scriptView.visible()
                && DetailsPanel.contains(e.x(), e.y(), width, height)) {
            handleDetailsPanelClick(e);
            return true;
        }
        return false;
    }

    /** Le canevas lui-même : renommage d'un commentaire, littéral, puis geste. */
    private boolean clickOnCanvas(MouseButtonEvent e, boolean doubled) {
        double wx = camera.toWorldX(e.x());
        double wy = camera.toWorldY(e.y());
        if (commentRenaming != null) {
            commitCommentRename();
        }
        if (doubled && controller.hitTest(wx, wy) == null) {
            var title = controller.commentTitleAt(wx, wy);
            if (title != null) {
                commentRenaming = title.uuid();
                commentBuffer = title.text();
                return true;
            }
        }
        if (openLiteralEdit(wx, wy)) {
            return true;
        }
        controller.press(wx, wy, e.hasShiftDown(), e.hasAltDown());
        return true;
    }

    private void commitCommentRename() {
        if (commentRenaming != null && !commentBuffer.isBlank()) {
            controller.renameComment(commentRenaming, commentBuffer.trim());
        }
        commentRenaming = null;
        commentBuffer = "";
    }

    /** Clic sur une zone littérale : bascule le bool, ouvre le champ ou l'énum. */
    /**
     * Flèches : sélectionne le nœud suivant dans cette direction et le recentre — le
     * recentrage compte autant que la sélection, un nœud choisi hors écran ne servirait
     * à rien (U5, story 9.4).
     */
    private boolean navigate(int dx, int dy) {
        java.util.UUID from = controller.selection().ids().size() == 1
                ? controller.selection().ids().iterator().next() : null;
        java.util.UUID target = KeyboardNav.next(controller.blueprint(), from, dx, dy);
        if (target == null) {
            return false;
        }
        controller.selection().click(target, false);
        controller.focusNode(target, canvasWidth(), height);
        return true;
    }

    /** Entrée : éditer le premier littéral du nœud sélectionné, sans souris (U5). */
    private boolean editFirstLiteralOfSelection() {
        if (controller.selection().ids().size() != 1) {
            return false;
        }
        java.util.UUID nodeId = controller.selection().ids().iterator().next();
        Node node = controller.blueprint().node(nodeId);
        NodeDescriptor desc = node == null ? null : descriptors.descriptor(node.typeId());
        if (desc == null) {
            return false;
        }
        for (int i = 0; i < desc.inputs().size(); i++) {
            NodeDescriptor.PinDescriptor pin = desc.inputs().get(i);
            if (pin.kind() != fr.blueprint.api.pin.PinKind.DATA
                    || !controller.blueprint().linksInto(nodeId, pin.name()).isEmpty()) {
                continue;   // un pin câblé n'a pas de littéral à éditer
            }
            if (beginLiteralEdit(nodeId, pin.name(), pin.type(), i)) {
                return true;
            }
        }
        return false;
    }

    private boolean openLiteralEdit(double wx, double wy) {
        CanvasController.LiteralRef ref = controller.literalAt(wx, wy);
        return ref != null && beginLiteralEdit(ref.node(), ref.pin(), ref.type(), ref.row());
    }

    /** Point d'entrée partagé canevas/panneau de détails (5.10) ; {@code row < 0} = à retrouver. */
    private boolean beginLiteralEdit(UUID nodeId, String pin,
                                     fr.blueprint.api.pin.PinType type, int row) {
        Node node = controller.blueprint().node(nodeId);
        NodeDescriptor desc = node == null ? null : descriptors.descriptor(node.typeId());
        if (row < 0 && desc != null) {
            // Appel du panneau de détails : retrouver la rangée réelle du pin, sinon
            // la liste déroulante direction se dessinerait sur la rangée 0 (QA 5.2c).
            for (int i = 0; i < desc.inputs().size(); i++) {
                if (desc.inputs().get(i).name().equals(pin)) {
                    row = i;
                    break;
                }
            }
        }
        row = Math.max(0, row);
        LiteralValue current = node == null || desc == null ? null
                : literalToShow(node, desc, pin);
        if (type == PinTypes.BOOL) {
            boolean on = current != null && current.value() instanceof Boolean b && b;
            controller.setLiteral(nodeId, pin, LiteralValue.of(PinTypes.BOOL, !on));
            return true;
        }
        if (type == PinTypes.DIRECTION) {
            literalEdit.openEnum(nodeId, pin, row,
                    current != null && current.value() instanceof Direction d ? d : null);
            return true;
        }
        if (type == PinTypes.ITEMSTACK) {
            picker.open(nodeId, pin, false, itemEntries());
            return true;
        }
        if (type == PinTypes.BLOCKSTATE) {
            picker.open(nodeId, pin, true, blockEntries());
            return true;
        }
        if (LiteralEditState.editableAsText(type)) {
            literalEdit.openText(nodeId, pin, row, type,
                    LiteralEditState.display(type, current));
            return true;
        }
        return false; // entity/player : pas de littéral, le clic retombe sur la sélection
    }

    // ------------------------------------------------- sélecteur item/bloc (5.2c)

    private List<PickerState.Entry> itemEntries() {
        return catalog.items();
    }

    private List<PickerState.Entry> blockEntries() {
        return catalog.blocks();
    }

    private ItemStack iconOf(Identifier id) {
        return catalog.icon(id, picker.isBlock());
    }

    private void pickFromRegistry(PickerState.Entry entry) {
        if (picker.isBlock()) {
            controller.setLiteral(picker.node(), picker.pin(), LiteralValue.of(PinTypes.BLOCKSTATE,
                    BuiltInRegistries.BLOCK.getValue(entry.id()).defaultBlockState()));
        } else {
            controller.setLiteral(picker.node(), picker.pin(), LiteralValue.of(PinTypes.ITEMSTACK,
                    new ItemStack(BuiltInRegistries.ITEM.getValue(entry.id()))));
        }
        picker.close();
    }

    private void handleDetailsPanelClick(MouseButtonEvent e) {
        var rows = details.rows(controller.selection().ids());
        int index = DetailsPanel.rowAt(rows, e.y(),
                detailsScroll.offset(rows.size(), DetailsPanel.visibleRows(height)), height);
        if (index < 0) {
            return;
        }
        DetailsPanelState.Row row = rows.get(index);
        switch (row.kind()) {
            case META_AUTHOR -> details.openMetaEdit(DetailsPanelState.MetaField.AUTHOR);
            case META_DESCRIPTION -> details.openMetaEdit(DetailsPanelState.MetaField.DESCRIPTION);
            case META_CAP -> details.cyclePermissionCap();
            case LITERAL -> beginLiteralEdit(row.node(), row.pin(), row.type(), -1);
            case WIRED -> controller.focusNode(row.node(), width, height);
            default -> {
            }
        }
    }

    public void mouseMoved(double mx, double my) {
        lastMouseX = mx;
        lastMouseY = my;
    }

    // ------------------------------------------------------- presse-papier (5.8)

    private void copySelection(boolean cut) {
        String text = clipboard.copy(controller.blueprint(), controller.selection().ids(), lookup);
        net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(text);
        int count = controller.selection().size();
        if (cut) {
            controller.deleteSelection();
        }
        actionBar(cut ? "blueprint.editor.clipboard.cut" : "blueprint.editor.clipboard.copied", count);
    }

    private void pasteClipboard() {
        String text = net.minecraft.client.Minecraft.getInstance().keyboardHandler.getClipboard();
        ClipboardCodec.PasteResult result = clipboard.paste(text);
        if (!result.success()) {
            actionBar("blueprint.editor.clipboard.invalid");
            return;
        }
        var pasted = controller.pasteFragment(result.fragment(),
                camera.toWorldX(lastMouseX), camera.toWorldY(lastMouseY));
        actionBar("blueprint.editor.clipboard.pasted", pasted.size());
    }

    private void duplicateSelection() {
        var fragment = ClipboardCodec.extract(controller.blueprint(),
                controller.selection().ids(), lookup);
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        for (Node node : fragment.nodes().values()) {
            minX = Math.min(minX, node.position().x());
            minY = Math.min(minY, node.position().y());
        }
        controller.pasteFragment(fragment, minX + 16, minY + 16);
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
                    actionBar(unsavableReason());
                }
            }
            case TEST -> {
                if (!session.savable()) {
                    actionBar(unsavableReason());
                } else if (diagnostics.blocking()) {
                    // Tester est grisé : la raison passe par la barre d'action (U2).
                    actionBar("blueprint.editor.toolbar.blocked", diagnostics.errors());
                } else if (session.test()) {
                    actionBar("blueprint.editor.toolbar.tested",
                            controller.blueprint().id().toString());
                }
            }
            case DEBUG -> {
                // Le débogueur s'ouvre sur un VRAI blueprint : la démo n'existe pas
                // côté serveur, il n'y aurait rien à déboguer.
                if (!session.savable()) {
                    actionBar(unsavableReason());
                } else if (debug.debugging()) {
                    fr.blueprint.client.net.DebugClient.stop(session.blueprint().id());
                    actionBar("blueprint.editor.toolbar.debug_off");
                } else {
                    fr.blueprint.client.net.DebugClient.start(session.blueprint().id());
                    actionBar("blueprint.editor.toolbar.debug_on");
                }
            }
            case SCRIPT -> scriptView.toggle();
            case CLOSE -> closeRequest.run();
        }
    }

    /**
     * Pourquoi l'enregistrement est impossible. Deux raisons très différentes se
     * cachaient derrière le même message : « c'est la démo » et « le serveur ne vous
     * laisse pas écrire » — le joueur doit savoir laquelle des deux le concerne.
     */
    private String unsavableReason() {
        return session.readOnly()
                ? "blueprint.editor.toolbar.readonly"
                : "blueprint.editor.toolbar.unsavable";
    }

    /** Clics dans la vue script (5.11) : boutons, ou ligne → recentrer le nœud. */
    private void handleScriptViewClick(MouseButtonEvent e) {
        ScriptView.Action action = ScriptView.actionAt(minecraftFont(), scriptView,
                e.x(), e.y(), width);
        if (action != null) {
            switch (action) {
                case COPY -> {
                    net.minecraft.client.Minecraft.getInstance().keyboardHandler
                            .setClipboard(scriptView.fullText());
                    actionBar("blueprint.editor.script.copied");
                }
                case EXPORT -> {
                    var path = fr.blueprint.core.BlueprintFiles.export(controller.blueprint(),
                            configDir.resolve("blueprint").resolve("exports"), registries);
                    actionBar(path != null ? "blueprint.editor.script.exported"
                            : "blueprint.editor.script.export_failed");
                }
                case IMPORT -> {
                    // Premier clic : arme la confirmation (U2) ; second : remplace tout.
                    if (!scriptView.armImport()) {
                        return;
                    }
                    String text = net.minecraft.client.Minecraft.getInstance()
                            .keyboardHandler.getClipboard();
                    ClipboardCodec.PasteResult parsed = clipboard.paste(text);
                    if (parsed.success() && !parsed.fragment().nodes().isEmpty()) {
                        controller.replaceAll(parsed.fragment());
                        actionBar("blueprint.editor.script.imported",
                                parsed.fragment().nodes().size());
                    } else {
                        actionBar("blueprint.editor.clipboard.invalid");
                    }
                }
            }
            return;
        }
        scriptView.disarmImport();
        int line = ScriptView.lineAt(scriptView, e.y(), height);
        UUID node = line < 0 ? null : scriptView.nodeAtLine(line);
        if (node != null) {
            // Recentrer dans la MOITIÉ canevas, pas l'écran entier.
            controller.focusNode(node, width / 2.0, height);
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

    private void handleVariablePanelClick(MouseButtonEvent e, boolean doubled) {
        if (VariablePanel.plusAt(e.x(), e.y())) {
            varPanel.create();
            return;
        }
        int row = VariablePanel.rowAt(varPanel, e.x(), e.y(),
                varScroll.offset(varPanel.rows().size(), VariablePanel.visibleRows(height)), height);
        if (row < 0) {
            varPanel.select(null);
            return;
        }
        String name = varPanel.rows().get(row).name();
        if (doubled) {
            varPanel.openRename(name);
            return;
        }
        if (name.equals(varPanel.selected())) {
            VariablePanel.RowAction action = VariablePanel.actionAt(e.x());
            if (action != null) {
                switch (action) {
                    case TYPE -> varPanel.cycleType(name);
                    case SCOPE -> varPanel.cycleScope(name);
                    case DELETE -> varPanel.delete(name);
                }
                return;
            }
        }
        varPanel.select(name);
        dragVar = name; // armer le glisser vers le canevas (Get ; Ctrl = Set)
    }

    public boolean mouseReleased(MouseButtonEvent e) {
        if (dragVar != null) {
            String name = dragVar;
            dragVar = null;
            if (e.x() >= VariablePanel.WIDTH) {
                controller.insertVariableNode(e.hasControlDown(), name,
                        camera.toWorldX(e.x()), camera.toWorldY(e.y()));
            }
            return true;
        }
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
        if (dragVar != null) {
            return true; // le dépôt se joue au relâchement
        }
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
        if (picker.isOpen()) {
            picker.scrollBy(vAmount > 0 ? -1 : 1);
            return true;
        }
        if (palette.isOpen()) {
            palette.scrollBy(vAmount > 0 ? -1 : 1);
            return true;
        }
        if (scriptView.visible() && mouseX >= ScriptView.panelLeft(width)) {
            scriptView.scrollBy(vAmount > 0 ? -3 : 3, ScriptView.visibleLines(height));
            return true;
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
        // Molette AU-DESSUS d'un panneau : elle le fait défiler, elle ne zoome pas.
        int step = vAmount > 0 ? -1 : 1;
        if (panelVisible && VariablePanel.contains(mouseX, mouseY, height)) {
            varScroll.scrollBy(step, varPanel.rows().size(), VariablePanel.visibleRows(height));
            return true;
        }
        if (panelVisible && !scriptView.visible()
                && DetailsPanel.contains(mouseX, mouseY, width, height)) {
            detailsScroll.scrollBy(step, details.rows(controller.selection().ids()).size(),
                    DetailsPanel.visibleRows(height));
            return true;
        }
        if (diagnostics.expanded() && mouseY >= height - DiagnosticsPanel.BAR_HEIGHT
                - DiagnosticsPanel.expandedHeight(diagnostics)) {
            diagScroll.scrollBy(step, diagnostics.report().size(),
                    DiagnosticsPanel.VISIBLE_ROWS);
            return true;
        }
        camera.zoomBy(vAmount > 0 ? 1 : -1, mouseX, mouseY);
        return true;
    }

    /**
     * Routage du clavier. Chaque mode ouvert (renommage, sélecteur, palette…) capte
     * TOUT le clavier tant qu'il est ouvert — sinon un « d » tapé dans un champ de
     * recherche dupliquerait la sélection. L'ordre des modes ci-dessous est leur ordre
     * de priorité, et c'est la seule chose que cette méthode décide.
     */
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == GLFW.GLFW_KEY_LEFT_SHIFT || e.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            shiftDown = true;
        }
        if (varPanel.isRenaming()) {
            return keyInVariableRename(e);
        }
        if (details.isEditingMeta()) {
            return keyInMetaEdit(e);
        }
        if (gotoState.isOpen()) {
            return keyInGoto(e);
        }
        if (commentRenaming != null) {
            return keyInCommentRename(e);
        }
        if (picker.isOpen()) {
            return keyInPicker(e);
        }
        if (literalEdit.isOpen()) {
            return keyInLiteralEdit(e);
        }
        if (palette.isOpen()) {
            return keyInPalette(e);
        }
        return keyOnCanvas(e);
    }

    private boolean keyInVariableRename(KeyEvent e) {
        switch (e.key()) {
            case GLFW.GLFW_KEY_ESCAPE -> varPanel.cancelRename();
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> varPanel.commitRename();
            case GLFW.GLFW_KEY_BACKSPACE -> varPanel.backspace();
            default -> {
            }
        }
        return true;
    }

    private boolean keyInMetaEdit(KeyEvent e) {
        switch (e.key()) {
            case GLFW.GLFW_KEY_ESCAPE -> details.cancelMetaEdit();
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> details.commitMetaEdit();
            case GLFW.GLFW_KEY_BACKSPACE -> details.backspace();
            default -> {
            }
        }
        return true;
    }

    private boolean keyInGoto(KeyEvent e) {
        switch (e.key()) {
            case GLFW.GLFW_KEY_ESCAPE -> gotoState.close();
            case GLFW.GLFW_KEY_UP -> gotoState.moveSelection(-1);
            case GLFW.GLFW_KEY_DOWN -> gotoState.moveSelection(1);
            case GLFW.GLFW_KEY_BACKSPACE -> gotoState.backspace();
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                GotoState.Target target = gotoState.selectedTarget();
                if (target != null) {
                    controller.focusNode(target.node(), canvasWidth(), height);
                }
                gotoState.close();
            }
            default -> {
            }
        }
        return true;
    }

    private boolean keyInCommentRename(KeyEvent e) {
        switch (e.key()) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                commentRenaming = null;
                commentBuffer = "";
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> commitCommentRename();
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!commentBuffer.isEmpty()) {
                    commentBuffer = commentBuffer.substring(0, commentBuffer.length() - 1);
                }
            }
            default -> {
            }
        }
        return true;
    }

    private boolean keyInPicker(KeyEvent e) {
        switch (e.key()) {
            case GLFW.GLFW_KEY_ESCAPE -> picker.close();
            case GLFW.GLFW_KEY_BACKSPACE -> picker.backspace();
            case GLFW.GLFW_KEY_PAGE_UP -> picker.scrollBy(-PickerState.ROWS);
            case GLFW.GLFW_KEY_PAGE_DOWN -> picker.scrollBy(PickerState.ROWS);
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (!picker.filtered().isEmpty()) {
                    pickFromRegistry(picker.filtered().get(0));
                }
            }
            default -> {
            }
        }
        return true;
    }

    /** Le clavier de l'éditeur est suspendu pendant l'édition d'un littéral (AC4, 5.2b). */
    private boolean keyInLiteralEdit(KeyEvent e) {
        switch (e.key()) {
            case GLFW.GLFW_KEY_ESCAPE -> literalEdit.close();
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> commitLiteral(true);
            case GLFW.GLFW_KEY_BACKSPACE -> literalEdit.backspace();
            case GLFW.GLFW_KEY_UP -> literalEdit.moveOption(-1);
            case GLFW.GLFW_KEY_DOWN -> literalEdit.moveOption(1);
            // Ctrl+P : « position du joueur » dans un champ vec3/blockpos (5.2c).
            case GLFW.GLFW_KEY_P -> {
                if (e.hasControlDown()) {
                    fillPlayerPosition();
                }
            }
            default -> {
            }
        }
        return true;
    }

    private boolean keyInPalette(KeyEvent e) {
        switch (e.key()) {
            case GLFW.GLFW_KEY_ESCAPE -> palette.close();
            case GLFW.GLFW_KEY_UP -> palette.moveSelection(-1);
            case GLFW.GLFW_KEY_DOWN -> palette.moveSelection(1);
            case GLFW.GLFW_KEY_PAGE_UP -> palette.scrollBy(-PaletteState.VISIBLE_ROWS);
            case GLFW.GLFW_KEY_PAGE_DOWN -> palette.scrollBy(PaletteState.VISIBLE_ROWS);
            // Ctrl+Entrée insère sans connecter (UX §6).
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER ->
                    insertFromPalette(!e.hasControlDown());
            case GLFW.GLFW_KEY_BACKSPACE -> palette.backspace();
            default -> {
            }
        }
        return true;
    }

    /** Raccourcis du canevas lui-même (UX §11) : aucun mode n'est ouvert. */
    private boolean keyOnCanvas(KeyEvent e) {
        switch (e.key()) {
            case GLFW.GLFW_KEY_SPACE -> {
                spaceTap.press();
                return true;
            }
            case GLFW.GLFW_KEY_F -> {
                if (e.hasControlDown()) {
                    openGoto(); // Ctrl+F : aller au nœud (UX §11)
                } else {
                    frameAll();
                }
                return true;
            }
            case GLFW.GLFW_KEY_TAB -> {
                panelVisible = !panelVisible;
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                controller.deleteSelection();
                return true;
            }
            // Flèches : navigation de nœud en nœud, sans souris (U5, story 9.4).
            case GLFW.GLFW_KEY_LEFT -> {
                return navigate(-1, 0);
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                return navigate(1, 0);
            }
            case GLFW.GLFW_KEY_UP -> {
                return navigate(0, -1);
            }
            case GLFW.GLFW_KEY_DOWN -> {
                return navigate(0, 1);
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                return editFirstLiteralOfSelection();
            }
            // Débogueur (9.1b) : B pose/retire un point d'arrêt, F5 reprend, F10 avance.
            case GLFW.GLFW_KEY_B -> {
                if (!e.hasControlDown() && controller.selection().ids().size() == 1) {
                    fr.blueprint.client.net.DebugClient.toggleBreakpoint(
                            session.blueprint().id(),
                            controller.selection().ids().iterator().next());
                    return true;
                }
            }
            case GLFW.GLFW_KEY_F5 -> {
                fr.blueprint.client.net.DebugClient.resume(session.blueprint().id());
                return true;
            }
            case GLFW.GLFW_KEY_F10 -> {
                fr.blueprint.client.net.DebugClient.step(session.blueprint().id());
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
            case GLFW.GLFW_KEY_C -> {
                if (e.hasControlDown() && !controller.selection().isEmpty()) {
                    copySelection(false);
                    return true;
                }
                if (!e.hasControlDown() && !controller.selection().isEmpty()) {
                    // C : boîte de commentaire autour de la sélection (UX §11).
                    controller.createCommentAroundSelection(
                            I18n.get("blueprint.editor.comment.default"));
                    return true;
                }
            }
            case GLFW.GLFW_KEY_Q -> {
                if (controller.alignSelection()) {
                    return true;
                }
            }
            case GLFW.GLFW_KEY_A -> {
                if (e.hasControlDown() && e.hasShiftDown()) {
                    controller.autoLayout();
                    return true;
                }
            }
            case GLFW.GLFW_KEY_X -> {
                if (e.hasControlDown() && !controller.selection().isEmpty()) {
                    copySelection(true);
                    return true;
                }
            }
            case GLFW.GLFW_KEY_V -> {
                if (e.hasControlDown()) {
                    pasteClipboard();
                    return true;
                }
            }
            case GLFW.GLFW_KEY_D -> {
                if (e.hasControlDown() && !controller.selection().isEmpty()) {
                    duplicateSelection();
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
            // Tap propre (pas servi au pan) → la palette s'ouvre au curseur (UX §6).
            // Jamais pendant un geste en cours (glisser, câblage…) — QA 5.4b.
            if (spaceTap.release() && !palette.isOpen() && !literalEdit.isOpen()
                    && !varPanel.isRenaming() && !details.isEditingMeta() && !panning
                    && controller.gesture() == CanvasController.Gesture.NONE) {
                palette.open(lastMouseX, lastMouseY,
                        camera.toWorldX(lastMouseX), camera.toWorldY(lastMouseY), null);
            }
            return true;
        }
        return false;
    }

    /** Ctrl+P : injecte la position du joueur dans le champ vec3/blockpos (5.2c). */
    private void fillPlayerPosition() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (literalEdit.type() == PinTypes.BLOCKPOS) {
            var pos = player.blockPosition();
            literalEdit.setText(pos.getX() + " " + pos.getY() + " " + pos.getZ());
        } else if (literalEdit.type() == PinTypes.VEC3) {
            var pos = player.position();
            literalEdit.setText(String.format(java.util.Locale.ROOT, "%.2f %.2f %.2f",
                    pos.x, pos.y, pos.z));
        }
    }

    public boolean charTyped(CharacterEvent e) {
        if (picker.isOpen() && e.isAllowedChatCharacter()) {
            picker.type(e.codepointAsString());
            return true;
        }
        if (varPanel.isRenaming() && e.isAllowedChatCharacter()) {
            varPanel.type(e.codepointAsString());
            return true;
        }
        if (details.isEditingMeta() && e.isAllowedChatCharacter()) {
            details.type(e.codepointAsString());
            return true;
        }
        if (commentRenaming != null && e.isAllowedChatCharacter()) {
            commentBuffer += e.codepointAsString();
            return true;
        }
        if (gotoState.isOpen() && e.isAllowedChatCharacter()) {
            gotoState.type(e.codepointAsString());
            return true;
        }
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

    private void insertFromPalette(boolean connect) {
        NodeSearch.Entry entry = palette.selectedEntry();
        if (entry != null) {
            controller.insertNode(entry.id(), palette.worldX(), palette.worldY(),
                    connect ? palette.wireFrom() : null);
            palette.noteInserted(entry.id());
            palette.prefs().save(configDir);
        }
        palette.close();
    }
}
