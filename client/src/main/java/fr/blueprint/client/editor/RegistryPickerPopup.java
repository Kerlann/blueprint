package fr.blueprint.client.editor;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.function.Function;

/**
 * Grille d'icônes du sélecteur d'item/bloc (story 5.2c), centrée à l'écran :
 * recherche en tête, {@link PickerState#COLS}×{@link PickerState#ROWS} cases de
 * 18 px. Les icônes sont de vrais items rendus par {@code renderItem}.
 */
public final class RegistryPickerPopup {

    public static final int CELL = 18;
    public static final int WIDTH = PickerState.COLS * CELL + 8;
    public static final int HEADER = 26;
    public static final int HEIGHT = HEADER + PickerState.ROWS * CELL + 6;

    private static final int BACKGROUND = 0xF01A1B1E;
    private static final int BORDER = 0xFF3A3D42;
    private static final int TITLE_COLOR = 0xFF8A8F98;
    private static final int QUERY_COLOR = 0xFFE6E6E6;
    private static final int CELL_HOVER = 0xFF2F3A55;

    private RegistryPickerPopup() {
    }

    public static int left(int screenW) {
        return (screenW - WIDTH) / 2;
    }

    public static int top(int screenH) {
        return (screenH - HEIGHT) / 2;
    }

    public static void render(GuiGraphics g, Font font, PickerState state,
                              Function<Identifier, ItemStack> icons,
                              int screenW, int screenH, double mouseX, double mouseY) {
        if (!state.isOpen()) {
            return;
        }
        int x = left(screenW);
        int y = top(screenH);
        g.fill(x - 1, y - 1, x + WIDTH + 1, y + HEIGHT + 1, BORDER);
        g.fill(x, y, x + WIDTH, y + HEIGHT, BACKGROUND);
        g.drawString(font, I18n.get(state.isBlock()
                        ? "blueprint.editor.picker.block" : "blueprint.editor.picker.item"),
                x + 4, y + 3, TITLE_COLOR, false);
        g.drawString(font, "> " + state.query() + "_", x + 4, y + 14, QUERY_COLOR, false);

        List<PickerState.Entry> window = state.window();
        int hovered = cellAt(state, mouseX, mouseY, screenW, screenH);
        for (int i = 0; i < window.size(); i++) {
            int cx = x + 4 + (i % PickerState.COLS) * CELL;
            int cy = y + HEADER + (i / PickerState.COLS) * CELL;
            if (i == hovered) {
                g.fill(cx, cy, cx + CELL, cy + CELL, CELL_HOVER);
            }
            g.renderItem(icons.apply(window.get(i).id()), cx + 1, cy + 1);
        }
        if (hovered >= 0 && hovered < window.size()) {
            g.drawString(font, font.plainSubstrByWidth(window.get(hovered).title(), WIDTH - 8),
                    x + 4, y + HEIGHT - 11, QUERY_COLOR, false);
        }
    }

    /** Indice de cellule visible sous la souris, ou −1. */
    public static int cellAt(PickerState state, double mx, double my, int screenW, int screenH) {
        int x = left(screenW) + 4;
        int y = top(screenH) + HEADER;
        if (mx < x || mx >= x + PickerState.COLS * CELL || my < y
                || my >= y + PickerState.ROWS * CELL) {
            return -1;
        }
        int col = (int) ((mx - x) / CELL);
        int row = (int) ((my - y) / CELL);
        int index = row * PickerState.COLS + col;
        return index < state.window().size() ? index : -1;
    }

    public static boolean contains(double mx, double my, int screenW, int screenH) {
        int x = left(screenW);
        int y = top(screenH);
        return mx >= x - 1 && mx < x + WIDTH + 1 && my >= y - 1 && my < y + HEIGHT + 1;
    }
}
