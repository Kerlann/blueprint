package fr.blueprint.client.editor;

import fr.blueprint.core.graph.Diagnostic;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;

import java.util.List;

/**
 * Barre d'état et liste des diagnostics (UX §8) : compteurs en bas, liste dépliable
 * au clic, chaque entrée recentre sur le nœud fautif (U3). Les messages viennent des
 * clés `blueprint.diag.*` du modèle — le client n'invente aucun texte d'erreur.
 */
public final class DiagnosticsPanel {

    public static final int BAR_HEIGHT = 12;
    public static final int ROW_HEIGHT = 11;
    public static final int MAX_ROWS = 8;

    private static final int BACKGROUND = 0xF0141519;
    private static final int BORDER = 0xFF3A3D42;
    private static final int OK_COLOR = 0xFF9ECE6A;
    private static final int ERROR_COLOR = 0xFFF7768E;
    private static final int WARNING_COLOR = 0xFFE0AF68;
    private static final int TEXT_COLOR = 0xFFD5D8DC;

    private DiagnosticsPanel() {
    }

    public static void render(GuiGraphics g, Font font, DiagnosticsState state,
                              int width, int height) {
        int barTop = height - BAR_HEIGHT;
        g.fill(0, barTop, width, height, BACKGROUND);
        g.fill(0, barTop, width, barTop + 1, BORDER);
        String summary = state.errors() + state.warnings() == 0
                ? I18n.get("blueprint.editor.diag.ok")
                : I18n.get("blueprint.editor.diag.summary", state.errors(), state.warnings());
        int color = state.errors() > 0 ? ERROR_COLOR
                : state.warnings() > 0 ? WARNING_COLOR : OK_COLOR;
        g.drawString(font, summary, 6, barTop + 2, color, false);

        if (!state.expanded() || state.report().isEmpty()) {
            return;
        }
        List<Diagnostic> report = state.report();
        int rows = Math.min(report.size(), MAX_ROWS);
        int top = barTop - rows * ROW_HEIGHT;
        g.fill(0, top - 1, width, barTop, BACKGROUND);
        g.fill(0, top - 1, width, top, BORDER);
        for (int i = 0; i < rows; i++) {
            Diagnostic d = report.get(i);
            boolean error = d.severity() == Diagnostic.Severity.ERROR;
            int y = top + i * ROW_HEIGHT + 2;
            g.drawString(font, error ? "[E]" : "[W]", 6, y,
                    error ? ERROR_COLOR : WARNING_COLOR, false);
            String message = I18n.get(d.translationKey(), d.args().toArray());
            g.drawString(font, font.plainSubstrByWidth(message, width - 34), 26, y,
                    TEXT_COLOR, false);
        }
    }

    /** Clic dans la barre d'état ? (déplie/replie la liste) */
    public static boolean barContains(double my, int height) {
        return my >= height - BAR_HEIGHT && my < height;
    }

    /** Indice du diagnostic sous la souris dans la liste dépliée, ou −1. */
    public static int rowAt(DiagnosticsState state, double my, int height) {
        if (!state.expanded() || state.report().isEmpty()) {
            return -1;
        }
        int rows = Math.min(state.report().size(), MAX_ROWS);
        int top = height - BAR_HEIGHT - rows * ROW_HEIGHT;
        if (my < top || my >= height - BAR_HEIGHT) {
            return -1;
        }
        return (int) ((my - top) / ROW_HEIGHT);
    }
}
