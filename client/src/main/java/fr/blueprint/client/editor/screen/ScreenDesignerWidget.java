package fr.blueprint.client.editor.screen;

import fr.blueprint.client.editor.EditorSession;
import fr.blueprint.client.editor.history.UndoStack;
import fr.blueprint.client.screen.ScreenPainter;
import fr.blueprint.core.graph.Diagnostic;
import fr.blueprint.core.graph.NodeTypeLookup;
import fr.blueprint.core.graph.ScreenOps;
import fr.blueprint.core.graph.screen.ElementKind;
import fr.blueprint.core.graph.screen.Screen;
import fr.blueprint.core.graph.screen.ScreenElement;
import fr.blueprint.core.graph.screen.ScreenLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Le concepteur d'écrans (story 10.2) : la palette à gauche, la surface de conception au
 * centre, les propriétés à droite.
 *
 * <p>Le widget ne décide rien. Il convertit pixels ↔ unités par {@link DesignSurface},
 * délègue tous les gestes à {@link ScreenCanvasController} et toute la peinture à
 * {@link ScreenPainter} — celui-là même que le rendu en jeu utilisera (10.3). Ce qui
 * reste ici est ce qui ne se teste pas sans client : le chrome.
 */
public final class ScreenDesignerWidget {

    public static final int PALETTE_WIDTH = 76;
    public static final int PROPERTIES_WIDTH = 128;
    private static final int ROW = 12;

    private static final int PANEL_BACKGROUND = 0xF01A1B1E;
    private static final int PANEL_BORDER = 0xFF3A3D42;
    private static final int TEXT = 0xFFD5D8DC;
    private static final int DIM_TEXT = 0xFF8A909A;
    private static final int SELECTED = 0xFF7AA2F7;
    private static final int INVALID = 0xFFF7768E;
    private static final int SURFACE_BACKGROUND = 0xFF101114;
    private static final int SAFE_BORDER = 0xFF4A4F58;
    private static final int GUIDE = 0xFFE0AF68;
    private static final int OVERFLOW = 0xFFE0AF68;
    private static final int HANDLE = 0xFFE6E6E6;
    private static final int RUBBER_FILL = 0x337AA2F7;

    private final EditorSession session;
    private final NodeTypeLookup lookup;
    private final ScreenCanvasController controller;
    private final ElementPropertiesState properties = new ElementPropertiesState();

    private int top;
    private int width;
    private int height;
    private DesignSurface surface = new DesignSurface(0, 0, 1);
    private @Nullable String message;

    public ScreenDesignerWidget(EditorSession session, NodeTypeLookup lookup, UndoStack history) {
        this.session = session;
        this.lookup = lookup;
        String first = session.blueprint().screens().keySet().stream().findFirst().orElse("");
        this.controller = new ScreenCanvasController(session.blueprint(), lookup, history, first);
    }

    public ScreenCanvasController controller() {
        return controller;
    }

    public void setBounds(int top, int width, int height) {
        this.top = top;
        this.width = width;
        this.height = height;
        this.surface = DesignSurface.fit(PALETTE_WIDTH, top + ROW,
                Math.max(1, width - PALETTE_WIDTH - PROPERTIES_WIDTH),
                Math.max(1, height - top - ROW * 2));
    }

    // ------------------------------------------------------------------- rendu

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        Screen screen = controller.screen();
        properties.select(screen == null || controller.selection().size() != 1 ? null
                : screen.element(controller.selection().ids().iterator().next()));

        g.fill(0, top, width, height, SURFACE_BACKGROUND);
        renderSurface(g, font, screen);
        renderPalette(g, font);
        renderProperties(g, font);
        if (message != null) {
            g.drawString(font, message, PALETTE_WIDTH + 4, height - ROW, INVALID, false);
        }
    }

    private void renderSurface(GuiGraphics g, Font font, @Nullable Screen screen) {
        // La marge est plus sombre que la zone garantie : on voit d'un coup d'œil ce qui
        // déborde, sans avoir à lire le cadre.
        g.fill(surface.outerLeft(), surface.outerTop(), surface.outerRight(),
                surface.outerBottom(), 0xFF16171A);
        g.fill(surface.left(), surface.top(), surface.right(), surface.bottom(), 0xFF202227);
        // La bordure marque les 320×180 garantis : ce qui déborde ne sera pas vu par
        // tout le monde, et l'auteur doit le savoir en le dessinant, pas en jeu.
        g.fill(surface.left() - 1, surface.top() - 1, surface.right() + 1, surface.top(),
                SAFE_BORDER);
        g.fill(surface.left() - 1, surface.bottom(), surface.right() + 1,
                surface.bottom() + 1, SAFE_BORDER);
        g.fill(surface.left() - 1, surface.top(), surface.left(), surface.bottom(), SAFE_BORDER);
        g.fill(surface.right(), surface.top(), surface.right() + 1, surface.bottom(), SAFE_BORDER);

        if (screen == null) {
            g.drawString(font, I18n.get("blueprint.designer.no_screen"),
                    surface.left() + 8, surface.top() + 8, DIM_TEXT, false);
            return;
        }

        g.enableScissor(surface.outerLeft(), surface.outerTop(),
                surface.outerRight(), surface.outerBottom());
        // forceVisible : en conception, un élément masqué doit rester manipulable —
        // sinon le rendre invisible reviendrait à le perdre.
        ScreenPainter.paint(g, font, screen, surface.left(), surface.top(), surface.scale(),
                Screen.SAFE_WIDTH, Screen.SAFE_HEIGHT, new ScreenPainter.Visuals() {
                    @Override
                    public boolean forceVisible(String element) {
                        return true;
                    }
                });
        renderOverflow(g, screen);
        renderSelection(g, screen);
        renderGuides(g);
        if (controller.gesture() == ScreenCanvasController.Gesture.RUBBER) {
            ScreenLayout.Rect band = controller.rubberBand();
            g.fill(surface.toScreenX(band.x()), surface.toScreenY(band.y()),
                    surface.toScreenX(band.right()), surface.toScreenY(band.bottom()),
                    RUBBER_FILL);
        }
        g.disableScissor();
    }

    /**
     * Cerne d'orange ce qui sort des 320×180 garantis (AC3b).
     *
     * <p>Le validateur produit bien l'avertissement, mais il ne s'affiche que dans le
     * panneau de diagnostics de l'onglet Graphe : l'auteur qui dessine un menu ne le
     * verrait jamais. Or c'est ici, au moment du geste, que l'information sert — après
     * coup, elle arrive sous forme de rapport de bug d'un joueur en <i>GUI scale</i> 4.
     */
    private void renderOverflow(GuiGraphics g, Screen screen) {
        for (ScreenElement element : screen.elements().values()) {
            if (!fr.blueprint.core.graph.ScreenRules.outsideSafeArea(screen, element)) {
                continue;
            }
            ScreenLayout.Rect rect = ScreenLayout.resolve(screen, element,
                    Screen.SAFE_WIDTH, Screen.SAFE_HEIGHT);
            int left = surface.toScreenX(rect.x());
            int topPx = surface.toScreenY(rect.y());
            int right = surface.toScreenX(rect.right());
            int bottom = surface.toScreenY(rect.bottom());
            g.fill(left, topPx, right, topPx + 1, OVERFLOW);
            g.fill(left, bottom - 1, right, bottom, OVERFLOW);
            g.fill(left, topPx, left + 1, bottom, OVERFLOW);
            g.fill(right - 1, topPx, right, bottom, OVERFLOW);
        }
    }

    private void renderSelection(GuiGraphics g, Screen screen) {
        for (String name : controller.selection().ids()) {
            ScreenLayout.Rect rect = controller.rectOf(name);
            if (rect == null) {
                continue;
            }
            int left = surface.toScreenX(rect.x());
            int topPx = surface.toScreenY(rect.y());
            int right = surface.toScreenX(rect.right());
            int bottom = surface.toScreenY(rect.bottom());
            g.fill(left, topPx, right, topPx + 1, SELECTED);
            g.fill(left, bottom - 1, right, bottom, SELECTED);
            g.fill(left, topPx, left + 1, bottom, SELECTED);
            g.fill(right - 1, topPx, right, bottom, SELECTED);
        }
        if (controller.selection().size() != 1) {
            return;
        }
        ScreenLayout.Rect rect = controller.rectOf(controller.selection().ids().iterator().next());
        if (rect == null) {
            return;
        }
        for (ScreenCanvasController.Handle handle : ScreenCanvasController.Handle.values()) {
            int hx = surface.toScreenX(rect.x() + rect.width() * handle.fractionX());
            int hy = surface.toScreenY(rect.y() + rect.height() * handle.fractionY());
            g.fill(hx - 2, hy - 2, hx + 2, hy + 2, HANDLE);
        }
    }

    private void renderGuides(GuiGraphics g) {
        for (AlignmentGuides.Guide guide : controller.guides()) {
            if (guide.vertical()) {
                int x = surface.toScreenX(guide.position());
                g.fill(x, surface.toScreenY(guide.from()), x + 1,
                        surface.toScreenY(guide.to()), GUIDE);
            } else {
                int y = surface.toScreenY(guide.position());
                g.fill(surface.toScreenX(guide.from()), y,
                        surface.toScreenX(guide.to()), y + 1, GUIDE);
            }
        }
    }

    private void renderPalette(GuiGraphics g, Font font) {
        g.fill(0, top, PALETTE_WIDTH, height, PANEL_BACKGROUND);
        g.fill(PALETTE_WIDTH - 1, top, PALETTE_WIDTH, height, PANEL_BORDER);

        int y = top + 3;
        g.drawString(font, I18n.get("blueprint.designer.screens"), 4, y, DIM_TEXT, false);
        y += ROW;
        for (String name : session.blueprint().screens().keySet()) {
            boolean active = name.equals(controller.screenName());
            g.drawString(font, font.plainSubstrByWidth(name, PALETTE_WIDTH - 8), 6, y,
                    active ? SELECTED : TEXT, false);
            y += ROW;
        }
        g.drawString(font, I18n.get("blueprint.designer.add_screen"), 6, y, DIM_TEXT, false);
        y += ROW;
        // Les actions de l'écran COURANT, sous sa liste : renommer, supprimer, et le
        // passage modal ↔ HUD, qui n'existaient nulle part — on pouvait créer un écran
        // et plus jamais le défaire.
        Screen current = controller.screen();
        boolean hud = current != null && current.hud();
        String mode = hud ? I18n.get("blueprint.designer.screen_hud")
                : I18n.get("blueprint.designer.screen_modal");
        g.drawString(font, mode, 6, y, hud ? SELECTED : DIM_TEXT, false);
        y += ROW;
        g.drawString(font, I18n.get("blueprint.designer.rename_screen"), 6, y, DIM_TEXT, false);
        y += ROW;
        g.drawString(font, I18n.get("blueprint.designer.remove_screen"), 6, y, DIM_TEXT, false);
        y += ROW + 4;

        g.drawString(font, I18n.get("blueprint.designer.elements"), 4, y, DIM_TEXT, false);
        y += ROW;
        for (ElementKind kind : ElementKind.values()) {
            g.drawString(font, I18n.get(kindKey(kind)), 6, y, TEXT, false);
            y += ROW;
        }
        y += 4;
        renderLayers(g, font, y);
    }

    /**
     * La liste des éléments, du <b>dessus vers le dessous</b> — l'ordre de dessin lu à
     * l'envers, comme dans tout éditeur graphique.
     *
     * <p>C'est la seule façon d'atteindre un parent entièrement recouvert par ses
     * enfants : sur le canevas, cliquer dessus attrape l'enfant, toujours. Sans cette
     * liste, un panneau de fond devenait injoignable dès qu'on avait posé quelque chose
     * par-dessus — et il n'y avait plus aucun moyen de le déplacer ni de le supprimer.
     */
    private void renderLayers(GuiGraphics g, Font font, int startY) {
        Screen screen = controller.screen();
        if (screen == null) {
            return;
        }
        g.drawString(font, I18n.get("blueprint.designer.layers"), 4, startY, DIM_TEXT, false);
        int y = startY + ROW;
        for (String name : layerOrder(screen)) {
            if (y + ROW > height) {
                break;   // le panneau ne déborde pas : le reste s'atteint au canevas
            }
            ScreenElement element = screen.element(name);
            boolean selected = controller.selection().isSelected(name);
            int depth = depthOf(screen, name);
            // L'œil dit d'un coup ce qui est masqué — sans lui, un élément invisible
            // à la conception se confond avec un élément absent.
            g.drawString(font, element.visible() ? "◉" : "○", 4, y,
                    element.visible() ? TEXT : DIM_TEXT, false);
            g.drawString(font, font.plainSubstrByWidth(name, PALETTE_WIDTH - 18 - depth * 4),
                    14 + depth * 4, y, selected ? SELECTED : TEXT, false);
            y += ROW;
        }
    }

    /** Du dessus vers le dessous, chaque enfant sous son parent. */
    private static List<String> layerOrder(Screen screen) {
        List<String> out = new java.util.ArrayList<>(screen.elements().keySet());
        java.util.Collections.reverse(out);
        return out;
    }

    private static int depthOf(Screen screen, String name) {
        int depth = 0;
        java.util.Set<String> seen = new java.util.HashSet<>();
        seen.add(name);
        ScreenElement element = screen.element(name);
        String cursor = element == null ? null : element.parent();
        while (cursor != null && seen.add(cursor) && depth < 4) {
            depth++;
            ScreenElement up = screen.element(cursor);
            cursor = up == null ? null : up.parent();
        }
        return depth;
    }

    private static String kindKey(ElementKind kind) {
        return "blueprint.designer.kind." + kind.name().toLowerCase(java.util.Locale.ROOT);
    }

    private void renderProperties(GuiGraphics g, Font font) {
        int left = width - PROPERTIES_WIDTH;
        g.fill(left, top, width, height, PANEL_BACKGROUND);
        g.fill(left, top, left + 1, height, PANEL_BORDER);

        ScreenElement element = properties.element();
        if (element == null) {
            g.drawString(font, I18n.get("blueprint.designer.no_selection"), left + 4, top + 4,
                    DIM_TEXT, false);
            return;
        }
        int y = top + 3;
        g.drawString(font, I18n.get(kindKey(element.kind())), left + 4, y, DIM_TEXT, false);
        y += ROW;
        g.drawString(font, I18n.get("blueprint.designer.anchor",
                element.anchor().name().toLowerCase(java.util.Locale.ROOT)), left + 4, y,
                DIM_TEXT, false);
        y += ROW;

        for (ElementPropertiesState.Field field : ElementPropertiesState.Field.values()) {
            boolean editing = properties.isEditing(field);
            String value = editing ? properties.buffer() + "_" : properties.valueOf(field);
            int color = editing
                    ? (properties.valid(this::nameFree) ? SELECTED : INVALID) : TEXT;
            g.drawString(font, I18n.get(fieldKey(field)), left + 4, y, DIM_TEXT, false);
            g.drawString(font, font.plainSubstrByWidth(value, PROPERTIES_WIDTH - 56),
                    left + 52, y, color, false);
            y += ROW;
        }
    }

    private static String fieldKey(ElementPropertiesState.Field field) {
        return "blueprint.designer.field." + field.name().toLowerCase(java.util.Locale.ROOT);
    }

    private boolean nameFree(String candidate) {
        ScreenElement element = properties.element();
        return controller.nameAvailable(candidate, element == null ? null : element.name());
    }

    // ------------------------------------------------------------------ souris

    public boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        double mx = e.x();
        double my = e.y();
        if (my < top) {
            return false;
        }
        message = null;
        if (mx < PALETTE_WIDTH) {
            return clickPalette(my);
        }
        if (mx >= width - PROPERTIES_WIDTH) {
            return clickProperties(my);
        }
        if (!surface.contains(mx, my)) {
            return false;
        }
        properties.cancel();
        controller.press(surface.toDesignX(mx), surface.toDesignY(my),
                e.hasShiftDown());
        return true;
    }

    private boolean clickPalette(double my) {
        int y = top + 3 + ROW;
        for (String name : List.copyOf(session.blueprint().screens().keySet())) {
            if (my >= y && my < y + ROW) {
                controller.setScreenName(name);
                return true;
            }
            y += ROW;
        }
        if (my >= y && my < y + ROW) {
            addScreen();
            return true;
        }
        y += ROW;
        if (my >= y && my < y + ROW) {
            if (!controller.toggleHud()) {
                reportRefusal();
            }
            return true;
        }
        y += ROW;
        if (my >= y && my < y + ROW) {
            // Le renommage se fait dans le champ du panneau de droite, réutilisé : un
            // second éditeur de texte donnerait deux comportements de frappe à retenir.
            renamingScreen = controller.screenName();
            screenBuffer = controller.screenName();
            return true;
        }
        y += ROW;
        if (my >= y && my < y + ROW) {
            controller.removeCurrentScreen();
            return true;
        }
        y += ROW + 4 + ROW;
        for (ElementKind kind : ElementKind.values()) {
            if (my >= y && my < y + ROW) {
                // Posé au centre de la surface : l'auteur le traîne ensuite où il veut,
                // plutôt que de deviner un point de dépôt qu'il n'a pas indiqué.
                if (controller.addElement(kind, Screen.SAFE_WIDTH / 2.0,
                        Screen.SAFE_HEIGHT / 2.0) == null) {
                    reportRefusal();
                }
                return true;
            }
            y += ROW;
        }
        y += 4 + ROW;
        // La liste des calques : sélectionner, et basculer la visibilité par l'œil.
        Screen screen = controller.screen();
        if (screen != null) {
            for (String name : layerOrder(screen)) {
                if (my >= y && my < y + ROW) {
                    controller.selection().selectAll(List.of(name), false);
                    return true;
                }
                y += ROW;
            }
        }
        return true;
    }

    /** L'écran en cours de renommage, et ce qui est tapé. */
    private @Nullable String renamingScreen;
    private String screenBuffer = "";

    private void addScreen() {
        String base = I18n.get("blueprint.designer.new_screen_name");
        String name = base;
        for (int i = 2; session.blueprint().screen(name) != null; i++) {
            name = base + "_" + i;
        }
        var result = new ScreenOps.AddScreen(Screen.empty(name))
                .apply(session.blueprint(), lookup);
        if (result.applied()) {
            if (result.inverse() != null) {
                controller.historyRecord(result.inverse());
            }
            controller.setScreenName(name);
        } else if (result.refusal() != null) {
            message = I18n.get(result.refusal().translationKey(),
                    result.refusal().args().toArray());
        }
    }

    private boolean clickProperties(double my) {
        if (properties.element() == null) {
            return true;
        }
        int y = top + 3 + ROW;
        if (my >= y && my < y + ROW) {
            apply(properties.cycleAnchor(1));
            return true;
        }
        y += ROW;
        for (ElementPropertiesState.Field field : ElementPropertiesState.Field.values()) {
            if (my >= y && my < y + ROW) {
                properties.beginEdit(field);
                return true;
            }
            y += ROW;
        }
        return true;
    }

    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (controller.gesture() == ScreenCanvasController.Gesture.NONE) {
            return false;
        }
        controller.drag(surface.toDesignX(e.x()), surface.toDesignY(e.y()));
        return true;
    }

    public boolean mouseReleased(MouseButtonEvent e) {
        if (controller.gesture() == ScreenCanvasController.Gesture.NONE) {
            return false;
        }
        controller.release();
        reportRefusal();
        return true;
    }

    private void reportRefusal() {
        Diagnostic refusal = controller.takeRefusal();
        message = refusal == null ? null
                : I18n.get(refusal.translationKey(), refusal.args().toArray());
    }

    // ------------------------------------------------------------------ clavier

    public boolean keyPressed(KeyEvent e) {
        if (renamingScreen != null) {
            return screenNameKey(e);
        }
        if (properties.editing() != null) {
            return editKey(e);
        }
        // Les flèches : le seul moyen de poser un élément à l'unité près. Shift passe
        // à dix — un cran de grille visible, pour traverser sans marteler la touche.
        double step = e.hasShiftDown() ? 10 : 1;
        return switch (e.key()) {
            case GLFW.GLFW_KEY_LEFT -> nudge(-step, 0);
            case GLFW.GLFW_KEY_RIGHT -> nudge(step, 0);
            case GLFW.GLFW_KEY_UP -> nudge(0, -step);
            case GLFW.GLFW_KEY_DOWN -> nudge(0, step);
            case GLFW.GLFW_KEY_DELETE -> {
                controller.deleteSelection();
                yield true;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                // Échap désélectionne AVANT de fermer l'éditeur : sinon on perdrait sa
                // fenêtre pour avoir voulu annuler une sélection.
                if (controller.selection().isEmpty()) {
                    yield false;
                }
                controller.selection().clear();
                yield true;
            }
            case GLFW.GLFW_KEY_D -> control(e, controller::duplicateSelection);
            case GLFW.GLFW_KEY_C -> control(e, controller::copySelection);
            case GLFW.GLFW_KEY_V -> control(e, controller::paste);
            case GLFW.GLFW_KEY_A -> control(e, () -> {
                Screen screen = controller.screen();
                if (screen == null) {
                    return false;
                }
                controller.selection().selectAll(List.copyOf(screen.elements().keySet()), false);
                return true;
            });
            case GLFW.GLFW_KEY_H -> control(e, () -> controller.toggleSelection(true));
            case GLFW.GLFW_KEY_G -> {
                controller.toggleSnap();
                yield true;
            }
            case GLFW.GLFW_KEY_PAGE_UP -> {
                controller.reorderSelection(1);
                yield true;
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                controller.reorderSelection(-1);
                yield true;
            }
            // Alignement au pavé numérique : la disposition des touches DESSINE
            // l'alignement obtenu, ce qu'aucun raccourci alphabétique ne fait.
            case GLFW.GLFW_KEY_KP_4 -> align(ScreenCanvasController.Align.LEFT);
            case GLFW.GLFW_KEY_KP_5 -> align(ScreenCanvasController.Align.CENTER_X);
            case GLFW.GLFW_KEY_KP_6 -> align(ScreenCanvasController.Align.RIGHT);
            case GLFW.GLFW_KEY_KP_8 -> align(ScreenCanvasController.Align.TOP);
            case GLFW.GLFW_KEY_KP_2 -> align(ScreenCanvasController.Align.BOTTOM);
            case GLFW.GLFW_KEY_KP_0 -> align(ScreenCanvasController.Align.CENTER_Y);
            case GLFW.GLFW_KEY_KP_ADD -> {
                controller.distributeSelection(true);
                yield true;
            }
            case GLFW.GLFW_KEY_KP_SUBTRACT -> {
                controller.distributeSelection(false);
                yield true;
            }
            default -> false;
        };
    }

    private boolean nudge(double dx, double dy) {
        if (controller.selection().isEmpty()) {
            return false;
        }
        controller.nudgeSelection(dx, dy);
        reportRefusal();
        return true;
    }

    private boolean align(ScreenCanvasController.Align align) {
        controller.alignSelection(align);
        return true;
    }

    /** N'agit que si Ctrl est enfoncé — sinon la touche reste libre pour le jeu. */
    private boolean control(KeyEvent e, java.util.function.BooleanSupplier action) {
        if (!e.hasControlDown()) {
            return false;
        }
        action.getAsBoolean();
        reportRefusal();
        return true;
    }

    private boolean editKey(KeyEvent e) {
        switch (e.key()) {
            case GLFW.GLFW_KEY_ESCAPE -> properties.cancel();
            case GLFW.GLFW_KEY_BACKSPACE -> properties.backspace();
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                String rename = properties.pendingName();
                ScreenElement element = properties.element();
                ScreenElement edited = properties.commit(this::nameFree);
                if (rename != null && element != null) {
                    if (!controller.rename(element.name(), rename)) {
                        reportRefusal();
                    }
                } else {
                    apply(edited);
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void apply(@Nullable ScreenElement edited) {
        if (edited != null && !controller.setElement(edited)) {
            reportRefusal();
        }
    }

    public boolean charTyped(CharacterEvent e) {
        if (renamingScreen != null) {
            screenBuffer += (char) e.codepoint();
            return true;
        }
        if (properties.editing() == null) {
            return false;
        }
        properties.type((char) e.codepoint());
        return true;
    }

    /** La frappe du nom d'écran : mêmes touches que le panneau de propriétés. */
    private boolean screenNameKey(KeyEvent e) {
        switch (e.key()) {
            case GLFW.GLFW_KEY_ESCAPE -> renamingScreen = null;
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!screenBuffer.isEmpty()) {
                    screenBuffer = screenBuffer.substring(0, screenBuffer.length() - 1);
                }
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (!controller.renameCurrentScreen(screenBuffer.trim())) {
                    message = I18n.get("blueprint.designer.bad_screen_name", screenBuffer);
                }
                renamingScreen = null;
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
