package fr.blueprint.client.editor.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;

/**
 * Les onglets « Graphe » / « Écrans » (story 10.2, AC1).
 *
 * <p>Un onglet dans le MÊME écran, pas une seconde fenêtre : on passe du graphe à
 * l'écran et retour sans fermer ni réenregistrer. Deux écrans distincts obligeraient à
 * choisir lequel détient la session, laquelle des deux piles d'annulation fait foi, et
 * ce qu'il advient d'un {@code Ctrl+S} lancé depuis l'autre.
 *
 * <p>Géométrie et rendu purs, comme {@code ToolbarWidget} : le hit-test et le dessin
 * lisent la même arithmétique, donc l'onglet se clique là où il se voit.
 */
public final class ModeTabs {

    /** Le mode d'édition courant de l'éditeur. */
    public enum Mode { GRAPH, SCREENS }

    public static final int HEIGHT = 13;

    private static final int BACKGROUND = 0xF01A1B1E;
    private static final int BORDER = 0xFF3A3D42;
    private static final int ACTIVE_BACKGROUND = 0xFF2B2D31;
    private static final int ACTIVE_TEXT = 0xFFE6E6E6;
    private static final int IDLE_TEXT = 0xFF8A909A;
    private static final int PADDING = 8;

    private ModeTabs() {
    }

    private static String label(Mode mode) {
        return I18n.get(mode == Mode.GRAPH
                ? "blueprint.editor.tab.graph" : "blueprint.editor.tab.screens");
    }

    /** Largeur d'un onglet — la même formule au rendu et au clic. */
    private static int widthOf(Font font, Mode mode) {
        return font.width(label(mode)) + PADDING * 2;
    }

    /** Largeur totale du bandeau — il s'arrête après le dernier onglet. */
    public static int totalWidth(Font font) {
        int total = 0;
        for (Mode mode : Mode.values()) {
            total += widthOf(font, mode);
        }
        return total;
    }

    public static void render(GuiGraphics g, Font font, Mode active, int top) {
        // Le bandeau ne barre PAS l'écran : en mode graphe il flotte au-dessus du
        // canevas, et une bande pleine largeur masquerait treize pixels de graphe sur
        // toute sa longueur, sans rien apporter.
        int width = totalWidth(font);
        g.fill(0, top, width, top + HEIGHT, BACKGROUND);
        g.fill(0, top + HEIGHT - 1, width, top + HEIGHT, BORDER);
        int x = 0;
        for (Mode mode : Mode.values()) {
            int w = widthOf(font, mode);
            if (mode == active) {
                g.fill(x, top, x + w, top + HEIGHT - 1, ACTIVE_BACKGROUND);
                g.fill(x, top, x + w, top + 1, fr.blueprint.client.theme.Theme.current()
                        .nodeSelected());
            }
            g.drawString(font, label(mode), x + PADDING, top + 3,
                    mode == active ? ACTIVE_TEXT : IDLE_TEXT, false);
            x += w;
        }
    }

    /** L'onglet sous la souris, ou {@code null}. */
    public static @Nullable Mode modeAt(Font font, double mx, double my, int top) {
        if (my < top || my >= top + HEIGHT) {
            return null;
        }
        int x = 0;
        for (Mode mode : Mode.values()) {
            int w = widthOf(font, mode);
            if (mx >= x && mx < x + w) {
                return mode;
            }
            x += w;
        }
        return null;
    }
}
