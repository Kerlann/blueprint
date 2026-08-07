package fr.blueprint.client.editor;

import fr.blueprint.core.graph.BlueprintFunction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Le panneau des fonctions <b>décidé</b> : où tombent les lignes, ce que demande un clic
 * (story 20.2). {@link FunctionPanel} ne fait plus que peindre ce qui est décidé ici.
 *
 * <p>La géométrie est celle de {@link VariablePanel} — même largeur, même hauteur de ligne,
 * même en-tête avec son « + », mêmes actions au même endroit. Deux panneaux qui font le même
 * travail et se manipulent différemment obligeraient à réapprendre au second ce qu'on sait
 * du premier.
 *
 * <p>Cette classe existe séparément du dessin, là où {@code VariablePanel} mêle les deux, et
 * pour une raison précise : le panneau des variables a fini <b>exclu du seuil de
 * couverture</b>, parce qu'on ne pouvait plus distinguer ce qui exige une fenêtre de ce qui
 * n'en exige pas. Ici, tout ce qui décide se vérifie sans jeu lancé, et l'exclusion ne porte
 * que sur les appels de dessin.
 */
public final class FunctionPanelLayout {

    public static final int WIDTH = VariablePanel.WIDTH;
    public static final int HEADER_HEIGHT = VariablePanel.HEADER_HEIGHT;
    public static final int ROW_HEIGHT = VariablePanel.ROW_HEIGHT;

    /** Ce qu'une ligne sélectionnée offre. {@code OPEN} est le geste principal. */
    public enum RowAction { OPEN, RENAME, DELETE }

    /** Ce qu'un clic dans le panneau demande. */
    public enum Hit { CREATE, DESELECT, SELECT, OPEN, RENAME, DELETE }

    /** Un clic <b>décidé</b> : ce qu'il demande, et sur quelle fonction. */
    public record Click(Hit hit, @Nullable String name) {
    }

    /**
     * Une ligne <b>décidée</b> : ce qu'elle porte et où elle est.
     *
     * <p>Décider et peindre sont séparés parce que les décisions se vérifient sans
     * fenêtre — quelle ligne est visible après un défilement, quel texte porte celle qu'on
     * renomme, à quelle hauteur elle tombe — là où les appels de dessin ne se vérifient
     * qu'à l'œil.
     */
    public record Row(String name, String text, int y, boolean selected, boolean open) {
    }

    private FunctionPanelLayout() {
    }

    /** {@code carre(n) → r} — la signature en une ligne, sans les types. */
    public static String label(BlueprintFunction function) {
        StringBuilder sb = new StringBuilder(function.name()).append('(');
        for (int i = 0; i < function.inputs().size(); i++) {
            sb.append(i == 0 ? "" : ", ").append(function.inputs().get(i).name());
        }
        sb.append(')');
        if (!function.outputs().isEmpty()) {
            sb.append(" → ");
            for (int i = 0; i < function.outputs().size(); i++) {
                sb.append(i == 0 ? "" : ", ").append(function.outputs().get(i).name());
            }
        }
        return sb.toString();
    }

    /** Les lignes à peindre, dans l'ordre, pour ce défilement et cette hauteur. */
    public static List<Row> rows(FunctionPanelState state, int height, int scroll) {
        List<BlueprintFunction> functions = state.rows();
        if (functions.isEmpty()) {
            return List.of();
        }
        int visible = visibleRows(height);
        int first = PanelScroll.clamp(scroll, functions.size(), visible);
        List<Row> out = new ArrayList<>();
        for (int i = first; i < functions.size() && i < first + visible; i++) {
            BlueprintFunction f = functions.get(i);
            // Celle qu'on renomme montre la frappe en cours, pas sa signature : sinon le
            // champ de saisie serait invisible et l'on taperait à l'aveugle.
            String text = f.name().equals(state.renamingFunction())
                    ? state.renameBuffer() + "_"
                    : label(f);
            out.add(new Row(f.name(), text,
                    ToolbarWidget.HEIGHT + HEADER_HEIGHT + (i - first) * ROW_HEIGHT,
                    f.name().equals(state.selected()), f.name().equals(state.openBody())));
        }
        return List.copyOf(out);
    }

    /**
     * Le panneau s'arrête après sa dernière ligne, comme celui des variables.
     *
     * <p>Rendu <b>et</b> hit-test lisent cette méthode : deux calculs séparés laisseraient
     * une bande invisible avalant les clics sous le panneau rétracté.
     */
    public static int bottom(FunctionPanelState state, int height) {
        int full = height - DiagnosticsPanel.BAR_HEIGHT;
        int used = ToolbarWidget.HEIGHT + HEADER_HEIGHT
                + Math.max(1, Math.min(state.rows().size(), visibleRows(height))) * ROW_HEIGHT + 4;
        if (state.pendingBreaks() > 0) {
            used += 10;
        }
        return Math.min(full, used);
    }

    /** Nombre de lignes qui tiennent dans le panneau. */
    public static int visibleRows(int height) {
        int usable = height - DiagnosticsPanel.BAR_HEIGHT - 12
                - ToolbarWidget.HEIGHT - HEADER_HEIGHT;
        return Math.max(1, usable / ROW_HEIGHT);
    }

    public static boolean contains(double mx, double my, FunctionPanelState state, int height) {
        return mx < WIDTH && my >= ToolbarWidget.HEIGHT && my < bottom(state, height);
    }

    public static boolean plusAt(double mx, double my) {
        return mx >= WIDTH - 14 && my >= ToolbarWidget.HEIGHT
                && my < ToolbarWidget.HEIGHT + HEADER_HEIGHT;
    }

    /** Indice de la ligne sous la souris, ou −1. */
    public static int rowAt(FunctionPanelState state, double mx, double my,
                            int scroll, int height) {
        if (mx >= WIDTH) {
            return -1;
        }
        int first = ToolbarWidget.HEIGHT + HEADER_HEIGHT;
        if (my < first) {
            return -1;
        }
        int visible = visibleRows(height);
        int shown = (int) ((my - first) / ROW_HEIGHT);
        if (shown >= visible) {
            return -1;
        }
        int row = shown + PanelScroll.clamp(scroll, state.rows().size(), visible);
        return row < state.rows().size() ? row : -1;
    }

    /** L'action sous le curseur sur la ligne sélectionnée, ou {@code null}. */
    public static @Nullable RowAction actionAt(double mx, boolean selected) {
        if (!selected) {
            return null;
        }
        if (mx >= WIDTH - 34 && mx < WIDTH - 24) {
            return RowAction.OPEN;
        }
        if (mx >= WIDTH - 24 && mx < WIDTH - 14) {
            return RowAction.RENAME;
        }
        return mx >= WIDTH - 14 && mx < WIDTH - 4 ? RowAction.DELETE : null;
    }

    /**
     * Ce que ce clic demande, sans rien appliquer.
     *
     * <p>Décider et agir sont séparés parce que la décision est là où sont les pièges — le
     * double-clic qui ouvre plutôt que de renommer, les trois actions qui n'apparaissent que
     * sur la ligne sélectionnée, le clic sous la dernière ligne qui désélectionne — et
     * qu'aucun de ces pièges ne se vérifie derrière une fenêtre.
     *
     * <p>Le double-clic <b>ouvre le corps</b>, contre l'usage du panneau des variables où il
     * renomme : ouvrir est ici le geste qu'on fait vingt fois quand renommer arrive une
     * fois, et l'action « ✎ » reste là pour l'autre.
     */
    public static Click clickAt(FunctionPanelState state, double mx, double my,
                                int scroll, int height, boolean doubled) {
        if (plusAt(mx, my)) {
            return new Click(Hit.CREATE, null);
        }
        int row = rowAt(state, mx, my, scroll, height);
        if (row < 0) {
            return new Click(Hit.DESELECT, null);
        }
        String name = state.rows().get(row).name();
        if (doubled) {
            return new Click(Hit.OPEN, name);
        }
        RowAction action = actionAt(mx, name.equals(state.selected()));
        if (action == null) {
            return new Click(Hit.SELECT, name);
        }
        return new Click(switch (action) {
            case OPEN -> Hit.OPEN;
            case RENAME -> Hit.RENAME;
            case DELETE -> Hit.DELETE;
        }, name);
    }
}
