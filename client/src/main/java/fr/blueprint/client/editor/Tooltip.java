package fr.blueprint.client.editor;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Infobulle de survol (story 5.12). Jusqu'ici l'éditeur ne réagissait pas au passage de
 * la souris : un pin ne disait pas son type, un nœud en faute ne disait pas son erreur,
 * un fantôme ne disait pas quel mod manquait. Tout était dans le modèle, rien n'était
 * montré.
 *
 * <p>Le <b>placement</b> est pur et testé : c'est la partie qui se trompe (une bulle qui
 * sort de l'écran ou qui se met sous le curseur, donc invisible).
 */
public final class Tooltip {

    private static final int BACKGROUND = 0xF01A1B1E;
    private static final int BORDER = 0xFF3A3D42;
    private static final int TEXT = 0xFFE6E6E6;
    private static final int HINT = 0xFF9AA0A8;

    /** Décalage sous le curseur : la bulle ne doit jamais être sous la pointe. */
    private static final int OFFSET_X = 10;
    private static final int OFFSET_Y = 12;
    private static final int PADDING = 3;
    private static final int LINE = 10;

    private Tooltip() {
    }

    /**
     * Coin haut-gauche de la bulle : à droite et sous le curseur, sauf si elle
     * déborderait — auquel cas elle bascule de l'autre côté plutôt que d'être tronquée.
     */
    public static int[] place(double mouseX, double mouseY, int boxWidth, int boxHeight,
                              int screenWidth, int screenHeight) {
        int x = (int) mouseX + OFFSET_X;
        int y = (int) mouseY + OFFSET_Y;
        if (x + boxWidth > screenWidth) {
            x = (int) mouseX - OFFSET_X - boxWidth;
        }
        if (y + boxHeight > screenHeight) {
            y = (int) mouseY - OFFSET_Y - boxHeight;
        }
        // Écran minuscule : coller au bord plutôt que sortir.
        x = Math.max(0, Math.min(x, Math.max(0, screenWidth - boxWidth)));
        y = Math.max(0, Math.min(y, Math.max(0, screenHeight - boxHeight)));
        return new int[]{x, y};
    }

    public static int width(Font font, List<String> lines) {
        int max = 0;
        for (String line : lines) {
            max = Math.max(max, font.width(line));
        }
        return max + 2 * PADDING;
    }

    public static int height(List<String> lines) {
        return lines.size() * LINE + 2 * PADDING - 2;
    }

    /**
     * Dessine la bulle. La première ligne est le titre, les suivantes sont grisées :
     * on lit le « quoi » d'un coup d'œil, le détail seulement si on s'arrête dessus.
     */
    public static void render(GuiGraphics g, Font font, List<String> lines,
                              double mouseX, double mouseY, int screenWidth, int screenHeight) {
        if (lines.isEmpty()) {
            return;
        }
        int w = width(font, lines);
        int h = height(lines);
        int[] at = place(mouseX, mouseY, w, h, screenWidth, screenHeight);
        int x = at[0];
        int y = at[1];

        g.fill(x, y, x + w, y + h, BACKGROUND);
        g.fill(x, y, x + w, y + 1, BORDER);
        g.fill(x, y + h - 1, x + w, y + h, BORDER);
        g.fill(x, y, x + 1, y + h, BORDER);
        g.fill(x + w - 1, y, x + w, y + h, BORDER);

        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), x + PADDING, y + PADDING + i * LINE,
                    i == 0 ? TEXT : HINT, false);
        }
    }
}
