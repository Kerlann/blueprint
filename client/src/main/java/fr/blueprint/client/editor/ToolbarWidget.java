package fr.blueprint.client.editor;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;

/**
 * Barre d'outils de l'éditeur (UX §2) : identifiant + ● non-enregistré à gauche,
 * boutons Compiler / Tester / Enregistrer / × à droite. Rendu et hit-test purs de
 * tout état : tout vient des paramètres.
 */
public final class ToolbarWidget {

    public static final int HEIGHT = 16;

    public enum Action { COMPILE, TEST, SAVE, CLOSE }

    private static final Action[] ORDER = {Action.CLOSE, Action.SAVE, Action.TEST, Action.COMPILE};

    private static final int BACKGROUND = 0xF0141519;
    private static final int BORDER = 0xFF3A3D42;
    private static final int TITLE_COLOR = 0xFFD5D8DC;
    private static final int DIRTY_COLOR = 0xFFE0AF68;
    private static final int BUTTON_COLOR = 0xFFAFB6C0;
    private static final int DISABLED_COLOR = 0xFF5A5F68;

    private ToolbarWidget() {
    }

    private static String label(Action action) {
        return switch (action) {
            case COMPILE -> I18n.get("blueprint.editor.toolbar.compile");
            case TEST -> I18n.get("blueprint.editor.toolbar.test");
            case SAVE -> I18n.get("blueprint.editor.toolbar.save");
            case CLOSE -> "✕";
        };
    }

    public static void render(GuiGraphics g, Font font, String title, boolean dirty,
                              boolean canSave, boolean canTest, int width) {
        g.fill(0, 0, width, HEIGHT, BACKGROUND);
        g.fill(0, HEIGHT - 1, width, HEIGHT, BORDER);
        String head = (dirty ? "● " : "") + title;
        g.drawString(font, font.plainSubstrByWidth(head, width / 2), 6, 4,
                dirty ? DIRTY_COLOR : TITLE_COLOR, false);

        int x = width - 4;
        for (Action action : ORDER) {
            String label = label(action);
            int w = font.width(label) + 10;
            x -= w;
            boolean enabled = switch (action) {
                case SAVE -> canSave;
                case TEST -> canTest;
                default -> true;
            };
            g.drawString(font, label, x + 5, 4, enabled ? BUTTON_COLOR : DISABLED_COLOR, false);
            x -= 4;
        }
    }

    /** Le bouton sous la souris (même géométrie que le rendu), ou null. */
    public static @Nullable Action actionAt(Font font, double mx, double my, int width) {
        if (my < 0 || my >= HEIGHT) {
            return null;
        }
        int x = width - 4;
        for (Action action : ORDER) {
            String label = label(action);
            int w = font.width(label) + 10;
            x -= w;
            if (mx >= x && mx < x + w) {
                return action;
            }
            x -= 4;
        }
        return null;
    }
}
