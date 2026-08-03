package fr.blueprint.client.editor;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Rendu du panneau de détails (droite, story 5.10). Le contenu vient de
 * {@link DetailsPanelState#rows} ; le widget route les clics par ligne.
 */
public final class DetailsPanel {

    public static final int WIDTH = 130;
    public static final int ROW_HEIGHT = 12;

    private static final int BACKGROUND = 0xF0141519;
    private static final int BORDER = 0xFF3A3D42;
    private static final int HEADER_COLOR = 0xFFE6E6E6;
    private static final int LABEL_COLOR = 0xFF8A8F98;
    private static final int VALUE_COLOR = 0xFFD5D8DC;
    private static final int LINK_COLOR = 0xFF7AA2F7;
    private static final int NOTE_COLOR = 0xFF9AA0A8;
    /** Champ éditable : fond creusé + liseré, pour qu'on VOIE qu'on peut cliquer. */
    private static final int FIELD_BG = 0xFF0F1218;
    private static final int FIELD_BORDER = 0xFF3A3D42;
    private static final int FIELD_EDIT_BORDER = 0xFF7AA2F7;
    private static final int FIELD_INVALID_BORDER = 0xFFF7768E;

    /** Bord gauche des champs de valeur, aligné pour toutes les lignes. */
    private static final int FIELD_LEFT = 48;

    private DetailsPanel() {
    }

    public static void render(GuiGraphics g, Font font, List<DetailsPanelState.Row> rows,
                              int width, int height) {
        render(g, font, rows, width, height, null);
    }

    /**
     * @param edit édition en cours, pour la dessiner <b>dans le panneau</b> : c'est là
     *             qu'on vient de cliquer, c'est donc là qu'on regarde. Avant, le champ
     *             ne s'ouvrait que sur le nœud — souvent hors écran, et l'on croyait
     *             que le clic n'avait rien fait.
     */
    public static void render(GuiGraphics g, Font font, List<DetailsPanelState.Row> rows,
                              int width, int height, @org.jetbrains.annotations.Nullable
                              LiteralEditState edit) {
        int left = width - WIDTH;
        int top = ToolbarWidget.HEIGHT;
        int bottom = height - DiagnosticsPanel.BAR_HEIGHT;
        g.fill(left, top, width, bottom, BACKGROUND);
        g.fill(left, top, left + 1, bottom, BORDER);

        for (int i = 0; i < rows.size(); i++) {
            int y = top + 3 + i * ROW_HEIGHT;
            if (y + ROW_HEIGHT > bottom) {
                break;
            }
            DetailsPanelState.Row row = rows.get(i);
            switch (row.kind()) {
                case HEADER -> {
                    g.drawString(font, font.plainSubstrByWidth(row.label(), WIDTH - 8),
                            left + 4, y, HEADER_COLOR, false);
                    if (!row.value().isEmpty()) {
                        // Identifiant technique sous le titre, même ligne raccourcie.
                    }
                }
                case NOTE -> g.drawString(font, font.plainSubstrByWidth(row.value(), WIDTH - 8),
                        left + 4, y, NOTE_COLOR, false);
                case WIRED -> {
                    g.drawString(font, font.plainSubstrByWidth(row.label(), 44),
                            left + 4, y, LABEL_COLOR, false);
                    g.drawString(font, font.plainSubstrByWidth(row.value(), WIDTH - 54),
                            left + 50, y, LINK_COLOR, false);
                }
                // Tout ce qui s'édite se dessine comme un champ : littéraux d'un nœud,
                // auteur, description et plafond du blueprint.
                case LITERAL, META_AUTHOR, META_DESCRIPTION, META_CAP ->
                        editableRow(g, font, row, left, y, edit);
                default -> {
                    g.drawString(font, font.plainSubstrByWidth(row.label(), 44),
                            left + 4, y, LABEL_COLOR, false);
                    g.drawString(font, font.plainSubstrByWidth(row.value(), WIDTH - 54),
                            left + 50, y, VALUE_COLOR, false);
                }
            }
        }
    }

    /** Une ligne éditable : libellé à gauche, champ à droite — en cours d'édition ou non. */
    private static void editableRow(GuiGraphics g, Font font, DetailsPanelState.Row row,
                                    int left, int y, @org.jetbrains.annotations.Nullable
                                    LiteralEditState edit) {
        g.drawString(font, font.plainSubstrByWidth(row.label(), 40),
                left + 4, y, LABEL_COLOR, false);

        int x1 = left + FIELD_LEFT;
        int x2 = left + WIDTH - 4;
        boolean editing = edit != null && edit.isOpen() && row.node() != null
                && row.node().equals(edit.node()) && row.pin() != null
                && row.pin().equals(edit.pin());

        g.fill(x1, y - 2, x2, y + 10, FIELD_BG);
        int border = !editing ? FIELD_BORDER
                : edit.isValid() ? FIELD_EDIT_BORDER : FIELD_INVALID_BORDER;
        g.fill(x1, y - 2, x2, y - 1, border);
        g.fill(x1, y + 9, x2, y + 10, border);
        g.fill(x1, y - 2, x1 + 1, y + 10, border);
        g.fill(x2 - 1, y - 2, x2, y + 10, border);

        String shown;
        if (editing) {
            shown = edit.mode() == LiteralEditState.Mode.ENUM
                    ? "‹ " + edit.options().get(edit.optionIndex()).getName() + " ›"
                    : edit.text() + "_";
            // Garder la FIN visible : c'est là qu'on tape.
            while (font.width(shown) > x2 - x1 - 6 && shown.length() > 1) {
                shown = shown.substring(1);
            }
        } else {
            shown = font.plainSubstrByWidth(row.value(), x2 - x1 - 6);
        }
        g.drawString(font, shown, x1 + 3, y, editing ? HEADER_COLOR : VALUE_COLOR, false);
    }

    public static boolean contains(double mx, double my, int width, int height) {
        return mx >= width - WIDTH && my >= ToolbarWidget.HEIGHT
                && my < height - DiagnosticsPanel.BAR_HEIGHT;
    }

    /** Indice de la ligne sous la souris, ou −1. */
    public static int rowAt(List<DetailsPanelState.Row> rows, double my) {
        int first = ToolbarWidget.HEIGHT + 3;
        if (my < first) {
            return -1;
        }
        int row = (int) ((my - first) / ROW_HEIGHT);
        return row < rows.size() ? row : -1;
    }
}
