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

    private DetailsPanel() {
    }

    public static void render(GuiGraphics g, Font font, List<DetailsPanelState.Row> rows,
                              int width, int height) {
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
                default -> {
                    g.drawString(font, font.plainSubstrByWidth(row.label(), 44),
                            left + 4, y, LABEL_COLOR, false);
                    g.drawString(font, font.plainSubstrByWidth(row.value(), WIDTH - 54),
                            left + 50, y, VALUE_COLOR, false);
                }
            }
        }
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
