package fr.blueprint.client.editor;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;

/**
 * Rendu du menu contextuel (story 5.13). Toute la géométrie vit dans
 * {@link ContextMenuState} — ici, seulement du dessin.
 */
public final class ContextMenuPopup {

    public static final int WIDTH = 148;

    private static final int BACKGROUND = 0xF01A1B1E;
    private static final int BORDER = 0xFF3A3D42;
    private static final int HOVER = 0xFF2F3A55;
    private static final int LABEL = 0xFFE6E6E6;
    private static final int DISABLED = 0xFF5A5F68;
    private static final int SHORTCUT = 0xFF8A8F98;
    private static final int SEPARATOR = 0xFF2A2D32;

    private ContextMenuPopup() {
    }

    public static void render(GuiGraphics g, Font font, ContextMenuState menu) {
        if (!menu.isOpen()) {
            return;
        }
        int x = (int) menu.x();
        int y = (int) menu.y();
        int h = menu.height();
        g.fill(x - 1, y - 1, x + WIDTH + 1, y + h + 1, BORDER);
        g.fill(x, y, x + WIDTH, y + h, BACKGROUND);

        var items = menu.items();
        for (int i = 0; i < items.size(); i++) {
            ContextMenuState.Item item = items.get(i);
            int top = menu.rowTop(i);
            if (item.separatorBefore()) {
                int line = top - ContextMenuState.SEPARATOR_HEIGHT / 2;
                g.fill(x + 4, line, x + WIDTH - 4, line + 1, SEPARATOR);
            }
            // Une ligne grisée ne se surligne pas : le survol promet un clic utile.
            if (i == menu.hovered() && item.enabled()) {
                g.fill(x + 1, top, x + WIDTH - 1, top + ContextMenuState.ROW_HEIGHT, HOVER);
            }
            int color = item.enabled() ? LABEL : DISABLED;
            g.drawString(font, font.plainSubstrByWidth(I18n.get(item.labelKey()), WIDTH - 50),
                    x + 6, top + 2, color, false);
            if (item.shortcut() != null) {
                int width = font.width(item.shortcut());
                g.drawString(font, item.shortcut(), x + WIDTH - 6 - width, top + 2,
                        item.enabled() ? SHORTCUT : DISABLED, false);
            }
        }
    }
}
