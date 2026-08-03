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

    public enum Action { COMPILE, TEST, DEBUG, SCRIPT, SAVE, CLOSE }

    private static final Action[] ORDER =
            {Action.CLOSE, Action.SAVE, Action.SCRIPT, Action.DEBUG, Action.TEST, Action.COMPILE};

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
            case SCRIPT -> I18n.get("blueprint.editor.toolbar.script");
            case SAVE -> I18n.get("blueprint.editor.toolbar.save");
            case DEBUG -> I18n.get("blueprint.editor.toolbar.debug");
            case CLOSE -> "✕";
        };
    }

    /**
     * Ce que fait le bouton et par quel raccourci. Les boutons sont des mots de sept
     * lettres sans icône : sans cette explication, « Script » ou « Tester » ne se
     * devinent pas, et les raccourcis ne se découvraient nulle part.
     */
    public static String hint(Action action) {
        return I18n.get(switch (action) {
            case COMPILE -> "blueprint.editor.tip.compile";
            case TEST -> "blueprint.editor.tip.test";
            case SCRIPT -> "blueprint.editor.tip.script";
            case SAVE -> "blueprint.editor.tip.save";
            case DEBUG -> "blueprint.editor.tip.debug";
            case CLOSE -> "blueprint.editor.tip.close";
        });
    }

    /** Le libellé affiché, pour le titre d'une infobulle. */
    public static String title(Action action) {
        return label(action);
    }

    /**
     * Largeur réservée au titre, avant les onglets.
     *
     * <p><b>Fixe</b>, et non déduite de la longueur du titre : le titre gagne un « ● »
     * dès la première modification, ce qui aurait décalé les onglets de huit pixels au
     * premier geste — on aurait cliqué à côté de l'onglet qu'on visait, et seulement
     * après avoir touché à quelque chose.
     */
    private static final int TITLE_ZONE = 160;

    /** Où commencent les onglets ; borné pour tenir dans une fenêtre étroite. */
    public static int tabsX(int width) {
        return Math.max(60, Math.min(TITLE_ZONE, width / 3));
    }

    public static void render(GuiGraphics g, Font font, String title, boolean dirty,
                              boolean canSave, boolean canTest, int width) {
        render(g, font, title, dirty, canSave, canTest, width, null);
    }

    /**
     * La barre complète, onglets compris (story 10.2 → refondue ici).
     *
     * <p>Les onglets « Graphe » / « Écrans » occupaient une <b>seconde bande</b> de treize
     * pixels sous celle-ci, sur toute la hauteur de l'éditeur. Or cette barre-ci est
     * vide entre le titre et les boutons : deux bandes pour ce qui tient dans une seule
     * coûtaient treize pixels de canevas à chaque image, sans rien apporter.
     */
    public static void render(GuiGraphics g, Font font, String title, boolean dirty,
                              boolean canSave, boolean canTest, int width,
                              fr.blueprint.client.editor.screen.ModeTabs.@Nullable Mode mode) {
        g.fill(0, 0, width, HEIGHT, BACKGROUND);
        g.fill(0, HEIGHT - 1, width, HEIGHT, BORDER);
        String head = (dirty ? "● " : "") + title;
        // Le titre s'arrête AVANT les onglets : il était borné à la moitié de la barre,
        // ce qui le laissait passer dessous dès qu'un identifiant était long.
        g.drawString(font, font.plainSubstrByWidth(head, tabsX(width) - 12), 6, 4,
                dirty ? DIRTY_COLOR : TITLE_COLOR, false);
        if (mode != null) {
            fr.blueprint.client.editor.screen.ModeTabs.render(g, font, mode, tabsX(width), HEIGHT);
        }

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
