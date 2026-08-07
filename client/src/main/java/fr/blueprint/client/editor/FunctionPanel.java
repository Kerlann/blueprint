package fr.blueprint.client.editor;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;

import java.util.List;

/**
 * Dessin du panneau des fonctions (story 20.2). <b>Rien d'autre.</b>
 *
 * <p>Où tombent les lignes, ce que porte chacune et ce que demande un clic sont décidés par
 * {@link FunctionPanelLayout}, et l'état vit dans {@link FunctionPanelState}. Cette classe
 * reçoit des lignes déjà placées et les peint.
 *
 * <p>C'est le seul morceau du panneau qui exige un jeu lancé, et c'est pour cela qu'il est
 * seul dans son fichier : {@code VariablePanel}, qui mêle le dessin et la géométrie, a fini
 * exclu du seuil de couverture en emportant ses décisions avec lui.
 */
public final class FunctionPanel {

    private static final int BACKGROUND = 0xF0141519;
    private static final int BORDER = 0xFF3A3D42;
    private static final int TITLE_COLOR = 0xFF8A8F98;
    private static final int NAME_COLOR = 0xFFD5D8DC;
    private static final int SELECTED_BG = 0xFF2F3A55;
    /** Le corps ouvert : un vert sourd, distinct du bleu de la sélection. */
    private static final int OPEN_BG = 0xFF3D5A3A;
    private static final int ACTION_COLOR = 0xFFAFB6C0;
    private static final int WARNING_COLOR = 0xFFE0AF68;

    private FunctionPanel() {
    }

    /** {@code scroll} : première ligne affichée. */
    public static void render(GuiGraphics g, Font font, FunctionPanelState state,
                              int height, int scroll) {
        int width = FunctionPanelLayout.WIDTH;
        int top = ToolbarWidget.HEIGHT;
        int bottom = FunctionPanelLayout.bottom(state, height);
        g.fill(0, top, width, bottom, BACKGROUND);
        g.fill(width - 1, top, width, bottom, BORDER);
        g.fill(0, bottom - 1, width, bottom, BORDER);
        g.drawString(font, I18n.get("blueprint.editor.functions.title"), 4, top + 3,
                TITLE_COLOR, false);
        g.drawString(font, "+", width - 10, top + 3, ACTION_COLOR, false);

        List<FunctionPanelLayout.Row> rows = FunctionPanelLayout.rows(state, height, scroll);
        if (rows.isEmpty()) {
            // Dire quoi faire plutôt que de laisser un vide : un panneau vide sans un mot
            // ne se distingue pas d'un panneau cassé, et le « + » est trop discret.
            g.drawString(font, font.plainSubstrByWidth(
                            I18n.get("blueprint.editor.functions.empty"), width - 8),
                    4, top + FunctionPanelLayout.HEADER_HEIGHT + 2, TITLE_COLOR, false);
            return;
        }
        for (FunctionPanelLayout.Row row : rows) {
            // Le corps ouvert se signale même quand la sélection est ailleurs : sans quoi
            // rien dans le panneau ne dirait quel graphe le canevas est en train de montrer.
            if (row.open()) {
                g.fill(0, row.y(), width - 1, row.y() + FunctionPanelLayout.ROW_HEIGHT, OPEN_BG);
            } else if (row.selected()) {
                g.fill(0, row.y(), width - 1, row.y() + FunctionPanelLayout.ROW_HEIGHT,
                        SELECTED_BG);
            }
            int nameWidth = row.selected() ? width - 40 : width - 8;
            g.drawString(font, font.plainSubstrByWidth(row.text(), nameWidth), 4, row.y() + 2,
                    NAME_COLOR, false);
            if (row.selected()) {
                g.drawString(font, "▸", width - 34, row.y() + 2, ACTION_COLOR, false);
                g.drawString(font, "✎", width - 24, row.y() + 2, ACTION_COLOR, false);
                g.drawString(font, "×", width - 14, row.y() + 2, ACTION_COLOR, false);
            }
        }

        int visible = FunctionPanelLayout.visibleRows(height);
        int count = state.rows().size();
        if (PanelScroll.overflows(count, visible)) {
            int trackTop = top + FunctionPanelLayout.HEADER_HEIGHT;
            int trackHeight = visible * FunctionPanelLayout.ROW_HEIGHT;
            int[] thumb = PanelScroll.thumb(PanelScroll.clamp(scroll, count, visible),
                    count, visible, trackHeight);
            g.fill(width - 3, trackTop, width - 1, trackTop + trackHeight, BACKGROUND);
            g.fill(width - 3, trackTop + thumb[0], width - 1, trackTop + thumb[0] + thumb[1],
                    ACTION_COLOR);
        }

        if (state.pendingBreaks() > 0) {
            String warn = I18n.get("blueprint.editor.functions.rename_warning",
                    state.pendingBreaks());
            g.drawString(font, font.plainSubstrByWidth(warn, width - 6), 4, bottom - 10,
                    WARNING_COLOR, false);
        }
    }
}
