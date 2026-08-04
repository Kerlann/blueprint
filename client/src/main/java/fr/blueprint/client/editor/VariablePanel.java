package fr.blueprint.client.editor;

import fr.blueprint.core.graph.Variable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Rendu du panneau des variables (UX §2, §7) : pastille de couleur du type, nom,
 * initiale de portée. La ligne sélectionnée porte les actions [T]ype, [S] portée,
 * [×] supprimer ; double-clic renomme. L'état vit dans {@link VariablePanelState}.
 */
public final class VariablePanel {

    public static final int WIDTH = 96;
    public static final int HEADER_HEIGHT = 14;
    public static final int ROW_HEIGHT = 12;

    public enum RowAction { TYPE, SCOPE, DELETE }

    private static final int BACKGROUND = 0xF0141519;
    private static final int BORDER = 0xFF3A3D42;
    private static final int TITLE_COLOR = 0xFF8A8F98;
    private static final int NAME_COLOR = 0xFFD5D8DC;
    private static final int SELECTED_BG = 0xFF2F3A55;
    private static final int ACTION_COLOR = 0xFFAFB6C0;
    private static final int WARNING_COLOR = 0xFFE0AF68;

    private VariablePanel() {
    }

    public static void render(GuiGraphics g, Font font, VariablePanelState state,
                              int height) {
        render(g, font, state, height, 0);
    }

    /** {@code scroll} : première ligne affichée (le panneau défile depuis la 5.12). */
    public static void render(GuiGraphics g, Font font, VariablePanelState state,
                              int height, int scroll) {
        int top = ToolbarWidget.HEIGHT;
        int bottom = bottom(state, height);
        g.fill(0, top, WIDTH, bottom, BACKGROUND);
        g.fill(WIDTH - 1, top, WIDTH, bottom, BORDER);
        // Un filet SOUS le panneau : rétracté, il n'atteint plus la barre du bas, et
        // sans ce trait son bord inférieur se confondrait avec le canevas.
        g.fill(0, bottom - 1, WIDTH, bottom, BORDER);
        g.drawString(font, I18n.get("blueprint.editor.vars.title"), 4, top + 3, TITLE_COLOR, false);
        g.drawString(font, "+", WIDTH - 10, top + 3, ACTION_COLOR, false);

        List<Variable> rows = state.rows();
        if (rows.isEmpty()) {
            // Dire quoi faire plutôt que de laisser un vide. Un panneau vide sans un mot
            // ne se distingue pas d'un panneau cassé, et le « + » de l'en-tête est trop
            // discret pour qu'on le cherche.
            g.drawString(font, font.plainSubstrByWidth(
                            I18n.get("blueprint.editor.vars.empty"), WIDTH - 8),
                    4, top + HEADER_HEIGHT + 2, TITLE_COLOR, false);
            return;
        }
        int visible = visibleRows(height);
        int first = PanelScroll.clamp(scroll, rows.size(), visible);
        for (int i = first; i < rows.size() && i < first + visible; i++) {
            Variable v = rows.get(i);
            int y = top + HEADER_HEIGHT + (i - first) * ROW_HEIGHT;
            boolean isSelected = v.name().equals(state.selected());
            if (isSelected) {
                g.fill(0, y, WIDTH - 1, y + ROW_HEIGHT, SELECTED_BG);
            }
            g.fill(3, y + 4, 7, y + 8, v.type().color());
            String label = v.name().equals(state.renamingVar())
                    ? state.renameBuffer() + "_"
                    : v.name();
            int nameWidth = isSelected ? WIDTH - 48 : WIDTH - 24;
            g.drawString(font, font.plainSubstrByWidth(label, nameWidth), 10, y + 2,
                    NAME_COLOR, false);
            if (isSelected) {
                g.drawString(font, "T", WIDTH - 34, y + 2, ACTION_COLOR, false);
                g.drawString(font, "S", WIDTH - 24, y + 2, ACTION_COLOR, false);
                g.drawString(font, "×", WIDTH - 14, y + 2, ACTION_COLOR, false);
            } else {
                g.drawString(font, scopeTag(v), WIDTH - 12, y + 2, TITLE_COLOR, false);
            }
        }

        // Curseur de défilement : sans lui, rien ne dit qu'il y a d'autres variables.
        if (PanelScroll.overflows(rows.size(), visible)) {
            int trackTop = top + HEADER_HEIGHT;
            int trackHeight = visible * ROW_HEIGHT;
            int[] thumb = PanelScroll.thumb(first, rows.size(), visible, trackHeight);
            g.fill(WIDTH - 3, trackTop, WIDTH - 1, trackTop + trackHeight, BACKGROUND);
            g.fill(WIDTH - 3, trackTop + thumb[0], WIDTH - 1, trackTop + thumb[0] + thumb[1],
                    ACTION_COLOR);
        }

        if (state.pendingBreaks() > 0) {
            String warn = I18n.get("blueprint.editor.vars.retype_warning", state.pendingBreaks());
            g.drawString(font, font.plainSubstrByWidth(warn, WIDTH - 6), 4, bottom - 10,
                    WARNING_COLOR, false);
        }
    }

    private static String scopeTag(Variable v) {
        return v.scope().name().substring(0, 1).toUpperCase(Locale.ROOT);
    }

    /**
     * Bas du panneau : il s'arrête après sa dernière ligne au lieu de descendre jusqu'à
     * la barre du bas.
     *
     * <p>Un blueprint sans variable peignait une colonne noire sur toute la hauteur de
     * l'écran pour deux mots — elle amputait le canevas d'autant, sans rien montrer. Le
     * panneau prend maintenant la place qu'il occupe réellement.
     *
     * <p>Rendu ET hit-test lisent cette méthode : deux calculs séparés auraient laissé
     * une bande invisible avalant les clics sous le panneau rétracté.
     */
    public static int bottom(VariablePanelState state, int height) {
        int full = height - DiagnosticsPanel.BAR_HEIGHT;
        int used = ToolbarWidget.HEIGHT + HEADER_HEIGHT
                + Math.max(1, state.rows().size()) * ROW_HEIGHT + 4;
        return Math.min(full, used);
    }

    public static boolean contains(double mx, double my, VariablePanelState state, int height) {
        return mx < WIDTH && my >= ToolbarWidget.HEIGHT && my < bottom(state, height);
    }

    public static boolean plusAt(double mx, double my) {
        return mx >= WIDTH - 14 && my >= ToolbarWidget.HEIGHT
                && my < ToolbarWidget.HEIGHT + HEADER_HEIGHT;
    }

    /** Nombre de lignes qui tiennent dans le panneau. */
    public static int visibleRows(int height) {
        int usable = height - DiagnosticsPanel.BAR_HEIGHT - 12 - ToolbarWidget.HEIGHT - HEADER_HEIGHT;
        return Math.max(1, usable / ROW_HEIGHT);
    }

    /** Indice de la ligne sous la souris, ou −1. */
    public static int rowAt(VariablePanelState state, double mx, double my) {
        return rowAt(state, mx, my, 0, Integer.MAX_VALUE);
    }

    /** Variante défilée : {@code scroll} lignes masquées en haut. */
    public static int rowAt(VariablePanelState state, double mx, double my, int scroll, int height) {
        if (mx >= WIDTH) {
            return -1;
        }
        int first = ToolbarWidget.HEIGHT + HEADER_HEIGHT;
        if (my < first) {
            return -1;
        }
        int visible = height == Integer.MAX_VALUE ? Integer.MAX_VALUE : visibleRows(height);
        int shown = (int) ((my - first) / ROW_HEIGHT);
        if (visible != Integer.MAX_VALUE && shown >= visible) {
            return -1;
        }
        int row = shown + PanelScroll.clamp(scroll, state.rows().size(),
                visible == Integer.MAX_VALUE ? state.rows().size() : visible);
        return row < state.rows().size() ? row : -1;
    }

    /** Action de la ligne SÉLECTIONNÉE sous la souris, ou null. */
    public static @Nullable RowAction actionAt(double mx) {
        if (mx >= WIDTH - 34 && mx < WIDTH - 24) {
            return RowAction.TYPE;
        }
        if (mx >= WIDTH - 24 && mx < WIDTH - 14) {
            return RowAction.SCOPE;
        }
        if (mx >= WIDTH - 14 && mx < WIDTH - 4) {
            return RowAction.DELETE;
        }
        return null;
    }
}
