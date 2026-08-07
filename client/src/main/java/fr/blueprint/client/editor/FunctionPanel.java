package fr.blueprint.client.editor;

import fr.blueprint.core.graph.BlueprintFunction;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Rendu du panneau des fonctions (story 20.2). L'état vit dans
 * {@link FunctionPanelState}.
 *
 * <p>La <b>même géométrie</b> que {@link VariablePanel} : même largeur, même hauteur de
 * ligne, même en-tête avec son « + », même curseur de défilement. Deux panneaux qui font
 * le même travail et se manipulent différemment obligeraient à réapprendre au second ce
 * qu'on sait du premier.
 *
 * <p>Une ligne montre la <b>signature</b> et non le seul nom : {@code carre(n) → r}. Une
 * liste de noms nus obligerait à ouvrir chaque corps pour savoir lequel prend une entité,
 * ce qui est précisément la question qu'on se pose en cherchant la fonction à appeler.
 */
public final class FunctionPanel {

    public static final int WIDTH = VariablePanel.WIDTH;
    public static final int HEADER_HEIGHT = VariablePanel.HEADER_HEIGHT;
    public static final int ROW_HEIGHT = VariablePanel.ROW_HEIGHT;

    /** Ce qu'une ligne sélectionnée offre. {@code OPEN} est le geste principal. */
    public enum RowAction { OPEN, RENAME, DELETE }

    private FunctionPanel() {
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

    /**
     * Une ligne <b>décidée</b> : ce qu'elle porte et où elle est.
     *
     * <p>Décider et peindre sont séparés parce que les décisions se vérifient sans
     * fenêtre — quelle ligne est visible après un défilement, quel texte porte celle qu'on
     * renomme, à quelle hauteur elle tombe — là où les appels de dessin ne se vérifient
     * qu'à l'œil.
     */
    public record Row(String name, String text, int y, boolean selected) {
    }

    /** Les lignes à peindre, dans l'ordre, pour ce défilement et cette hauteur. */
    public static List<Row> layout(FunctionPanelState state, int height, int scroll) {
        List<BlueprintFunction> functions = state.rows();
        if (functions.isEmpty()) {
            return List.of();
        }
        int visible = visibleRows(height);
        int first = PanelScroll.clamp(scroll, functions.size(), visible);
        List<Row> out = new java.util.ArrayList<>();
        for (int i = first; i < functions.size() && i < first + visible; i++) {
            BlueprintFunction f = functions.get(i);
            boolean selected = f.name().equals(state.selected());
            // Celle qu'on renomme montre la frappe en cours, pas sa signature : sinon le
            // champ de saisie serait invisible et l'on taperait à l'aveugle.
            String text = f.name().equals(state.renamingFunction())
                    ? state.renameBuffer() + "_"
                    : label(f);
            out.add(new Row(f.name(), text,
                    ToolbarWidget.HEIGHT + HEADER_HEIGHT + (i - first) * ROW_HEIGHT, selected));
        }
        return List.copyOf(out);
    }

    // Le RENDU arrive avec le câblage (tâche 4). L'écrire d'avance donnerait du code que
    // rien n'appelle et que rien n'exerce — et la barrière de couverture aurait raison de
    // le refuser : un dessin qu'aucun test ne regarde et qu'aucun écran ne montre n'est pas
    // du travail fait, c'est du travail à vérifier plus tard.

    /** Le panneau s'arrête après sa dernière ligne, comme celui des variables. */
    public static int bottom(FunctionPanelState state, int height) {
        int rows = Math.min(state.rows().size(), visibleRows(height));
        int used = ToolbarWidget.HEIGHT + HEADER_HEIGHT + rows * ROW_HEIGHT + 2;
        if (state.pendingBreaks() > 0) {
            used += 10;
        }
        return Math.min(used, height);
    }

    private static int visibleRows(int height) {
        return Math.max(1, (height - ToolbarWidget.HEIGHT - HEADER_HEIGHT - 12) / ROW_HEIGHT);
    }

    /** L'action sous le curseur sur la ligne sélectionnée, ou {@code null}. */
    public static @Nullable RowAction actionAt(double mx, int row, boolean selected) {
        if (!selected) {
            return null;
        }
        if (mx >= WIDTH - 36 && mx < WIDTH - 26) {
            return RowAction.OPEN;
        }
        if (mx >= WIDTH - 26 && mx < WIDTH - 16) {
            return RowAction.RENAME;
        }
        return mx >= WIDTH - 16 && mx < WIDTH - 4 ? RowAction.DELETE : null;
    }
}
