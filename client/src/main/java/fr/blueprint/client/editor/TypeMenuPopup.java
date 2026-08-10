package fr.blueprint.client.editor;

import fr.blueprint.api.pin.PinType;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;

/**
 * Rendu du menu de type d'une variable. Toute la géométrie vit dans
 * {@link TypeMenuState} — ici, seulement du dessin.
 *
 * <p>Chaque ligne porte la <b>pastille de couleur</b> du type, la même que celle de la
 * ligne de variable et celle des pins sur le canevas. C'est ce qui rend le menu lisible
 * sans le lire : on reconnaît le vert du nombre ou le violet du vecteur avant d'avoir
 * décodé le mot.
 */
public final class TypeMenuPopup {

    public static final int WIDTH = 92;

    private static final int BACKGROUND = 0xF01A1B1E;
    private static final int BORDER = 0xFF3A3D42;
    private static final int HOVER = 0xFF2F3A55;
    private static final int LABEL = 0xFFE6E6E6;
    private static final int CURRENT = 0xFF8A8F98;

    private TypeMenuPopup() {
    }

    public static void render(GuiGraphics g, Font font, TypeMenuState menu) {
        if (!menu.isOpen()) {
            return;
        }
        int x = menu.x();
        int y = menu.y();
        int h = menu.height();
        g.fill(x - 1, y - 1, x + WIDTH + 1, y + h + 1, BORDER);
        g.fill(x, y, x + WIDTH, y + h, BACKGROUND);

        var types = menu.types();
        for (int i = 0; i < types.size(); i++) {
            PinType type = types.get(i);
            int top = menu.rowTop(i);
            if (i == menu.hovered()) {
                g.fill(x + 1, top, x + WIDTH - 1, top + TypeMenuState.ROW_HEIGHT, HOVER);
            }
            g.fill(x + 4, top + 3, x + 8, top + 7, type.color());
            boolean isCurrent = type.equals(menu.current());
            g.drawString(font, font.plainSubstrByWidth(I18n.get(type.translationKey()), WIDTH - 22),
                    x + 12, top + 2, isCurrent ? CURRENT : LABEL, false);
            // Le type courant est marqué plutôt que masqué : voir où l'on est placé dans
            // une liste vaut mieux qu'une liste dont un élément manque sans explication.
            if (isCurrent) {
                g.drawString(font, "•", x + WIDTH - 8, top + 2, CURRENT, false);
            }
        }
    }
}
