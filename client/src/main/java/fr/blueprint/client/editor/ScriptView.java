package fr.blueprint.client.editor;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;

/**
 * Rendu de la vue script (moitié droite, story 5.11) : numéros de ligne, coloration
 * minimale, ligne du nœud sélectionné surlignée, boutons Copier / Exporter /
 * Importer. L'état vit dans {@link ScriptViewState}.
 */
public final class ScriptView {

    public static final int LINE_HEIGHT = 10;
    public static final int HEADER_HEIGHT = 14;
    /** Colonne des numéros de ligne. */
    public static final int GUTTER = 24;

    public enum Action { COPY, EXPORT, IMPORT }

    private static final Action[] ORDER = {Action.IMPORT, Action.EXPORT, Action.COPY};

    private static final int BACKGROUND = 0xF8121417;
    private static final int BORDER = 0xFF3A3D42;
    private static final int GUTTER_COLOR = 0xFF5A5F68;
    private static final int PLAIN = 0xFFD5D8DC;
    private static final int EVENT = 0xFF7DCFFF;
    private static final int VARIABLE = 0xFFBB9AF7;
    private static final int COMMENT = 0xFF6A7078;
    private static final int HIGHLIGHT = 0xFF2F3A55;
    private static final int BUTTON = 0xFFAFB6C0;
    private static final int WARNING = 0xFFE0AF68;

    private ScriptView() {
    }

    public static int panelLeft(int width) {
        return width / 2;
    }

    public static int visibleLines(int height) {
        return Math.max(1, (height - ToolbarWidget.HEIGHT - HEADER_HEIGHT
                - DiagnosticsPanel.BAR_HEIGHT - 4) / LINE_HEIGHT);
    }

    private static String label(Action action) {
        return I18n.get(switch (action) {
            case COPY -> "blueprint.editor.script.copy";
            case EXPORT -> "blueprint.editor.script.export";
            case IMPORT -> "blueprint.editor.script.import";
        });
    }

    public static void render(GuiGraphics g, Font font, ScriptViewState state,
                              int width, int height) {
        if (!state.visible()) {
            return;
        }
        int left = panelLeft(width);
        int top = ToolbarWidget.HEIGHT;
        int bottom = height - DiagnosticsPanel.BAR_HEIGHT;
        g.fill(left, top, width, bottom, BACKGROUND);
        g.fill(left, top, left + 1, bottom, BORDER);

        // Boutons du bandeau, à droite.
        int x = width - 4;
        for (Action action : ORDER) {
            String label = action == Action.IMPORT && state.importArmed()
                    ? I18n.get("blueprint.editor.script.import_confirm")
                    : label(action);
            int w = font.width(label) + 8;
            x -= w;
            g.drawString(font, label, x + 4, top + 3,
                    action == Action.IMPORT && state.importArmed() ? WARNING : BUTTON, false);
            x -= 4;
        }
        if (!state.issues().isEmpty()) {
            g.drawString(font, font.plainSubstrByWidth("⚠ " + state.issues().get(0),
                    x - left - 8), left + 4, top + 3, WARNING, false);
        }

        int lines = visibleLines(height);
        for (int i = 0; i < lines; i++) {
            int index = state.scroll() + i;
            if (index >= state.lines().size()) {
                break;
            }
            int y = top + HEADER_HEIGHT + i * LINE_HEIGHT;
            if (index == state.highlightedLine()) {
                g.fill(left + 1, y - 1, width, y + LINE_HEIGHT - 1, HIGHLIGHT);
            }
            g.drawString(font, String.valueOf(index + 1), left + 3, y, GUTTER_COLOR, false);
            String line = state.lines().get(index);
            int color = switch (ScriptViewState.kindOf(line)) {
                case EVENT -> EVENT;
                case VARIABLE -> VARIABLE;
                case COMMENT -> COMMENT;
                case PLAIN -> PLAIN;
            };
            g.drawString(font, font.plainSubstrByWidth(line, width - left - GUTTER - 6),
                    left + GUTTER, y, color, false);
        }
    }

    public static boolean contains(double mx, double my, int width, int height) {
        return mx >= panelLeft(width) && my >= ToolbarWidget.HEIGHT
                && my < height - DiagnosticsPanel.BAR_HEIGHT;
    }

    /** Le bouton du bandeau sous la souris, ou null. */
    public static @Nullable Action actionAt(Font font, ScriptViewState state,
                                            double mx, double my, int width) {
        if (my < ToolbarWidget.HEIGHT || my >= ToolbarWidget.HEIGHT + HEADER_HEIGHT) {
            return null;
        }
        int x = width - 4;
        for (Action action : ORDER) {
            String label = action == Action.IMPORT && state.importArmed()
                    ? I18n.get("blueprint.editor.script.import_confirm")
                    : label(action);
            int w = font.width(label) + 8;
            x -= w;
            if (mx >= x && mx < x + w) {
                return action;
            }
            x -= 4;
        }
        return null;
    }

    /** L'indice de ligne (absolu) sous la souris, ou −1. */
    public static int lineAt(ScriptViewState state, double my, int height) {
        int first = ToolbarWidget.HEIGHT + HEADER_HEIGHT;
        if (my < first || my >= height - DiagnosticsPanel.BAR_HEIGHT) {
            return -1;
        }
        int line = state.scroll() + (int) ((my - first) / LINE_HEIGHT);
        return line < state.lines().size() ? line : -1;
    }
}
