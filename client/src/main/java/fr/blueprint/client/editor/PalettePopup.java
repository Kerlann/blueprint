package fr.blueprint.client.editor;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;

import java.util.List;

/**
 * Rendu écran de la palette et correspondance clic→ligne. La logique (requête,
 * filtre, sélection) vit dans {@link PaletteState}.
 */
public final class PalettePopup {

    public static final int WIDTH = 220;
    public static final int ROW_HEIGHT = 13;
    /** En-tête : titre + champ de recherche (et rappel du filtre au besoin). */
    private static final int HEADER_HEIGHT = 28;

    private static final int BACKGROUND = 0xF01A1B1E;
    private static final int BORDER = 0xFF3A3D42;
    private static final int ROW_SELECTED = 0xFF2F3A55;
    private static final int TITLE_COLOR = 0xFF8A8F98;
    private static final int QUERY_COLOR = 0xFFE6E6E6;
    private static final int ENTRY_COLOR = 0xFFD5D8DC;
    private static final int META_COLOR = 0xFF7A7F88;
    private static final int FILTER_COLOR = 0xFF7DCFFF;

    private PalettePopup() {
    }

    public static int height(PaletteState state) {
        return HEADER_HEIGHT + Math.max(1, state.results().size()) * ROW_HEIGHT + 4;
    }

    /** Coin haut-gauche effectif, ancré au curseur mais maintenu dans l'écran. */
    public static int left(PaletteState state, int screenW) {
        return (int) Math.clamp(state.anchorX(), 4, Math.max(4, screenW - WIDTH - 4));
    }

    public static int top(PaletteState state, int screenH) {
        return (int) Math.clamp(state.anchorY(), 4, Math.max(4, screenH - height(state) - 4));
    }

    public static void render(GuiGraphics g, Font font, PaletteState state,
                              int screenW, int screenH) {
        if (!state.isOpen()) {
            return;
        }
        int x = left(state, screenW);
        int y = top(state, screenH);
        int h = height(state);
        g.fill(x - 1, y - 1, x + WIDTH + 1, y + h + 1, BORDER);
        g.fill(x, y, x + WIDTH, y + h, BACKGROUND);

        String header = I18n.get("blueprint.editor.palette.title");
        if (state.wireFrom() != null) {
            header += " — " + I18n.get("blueprint.editor.palette.filter",
                    I18n.get(state.wireFrom().type().translationKey()));
        }
        g.drawString(font, font.plainSubstrByWidth(header, WIDTH - 8), x + 4, y + 3,
                state.wireFrom() != null ? FILTER_COLOR : TITLE_COLOR, false);
        g.drawString(font, "🔍 " + state.query() + "_", x + 4, y + 15, QUERY_COLOR, false);

        List<NodeSearch.Entry> results = state.results();
        if (results.isEmpty()) {
            g.drawString(font, I18n.get("blueprint.editor.palette.empty"),
                    x + 4, y + HEADER_HEIGHT + 3, META_COLOR, false);
            return;
        }
        for (int i = 0; i < results.size(); i++) {
            int rowY = y + HEADER_HEIGHT + i * ROW_HEIGHT;
            if (i == state.selectedIndex()) {
                g.fill(x + 1, rowY, x + WIDTH - 1, rowY + ROW_HEIGHT, ROW_SELECTED);
            }
            NodeSearch.Entry e = results.get(i);
            String meta = e.category();
            int metaW = font.width(meta);
            g.drawString(font, font.plainSubstrByWidth(e.title(), WIDTH - 12 - metaW),
                    x + 4, rowY + 3, ENTRY_COLOR, false);
            g.drawString(font, meta, x + WIDTH - 6 - metaW, rowY + 3, META_COLOR, false);
        }
    }

    /** Indice de la ligne sous la souris, ou −1 (aussi −1 hors du popup). */
    public static int rowAt(PaletteState state, double mx, double my, int screenW, int screenH) {
        int x = left(state, screenW);
        int y = top(state, screenH);
        if (mx < x || mx >= x + WIDTH || my < y + HEADER_HEIGHT || my >= y + height(state)) {
            return -1;
        }
        int row = (int) ((my - y - HEADER_HEIGHT) / ROW_HEIGHT);
        return row < state.results().size() ? row : -1;
    }

    /** Le point est-il dans le popup (pour ne pas fermer sur un clic dedans) ? */
    public static boolean contains(PaletteState state, double mx, double my,
                                   int screenW, int screenH) {
        int x = left(state, screenW);
        int y = top(state, screenH);
        return mx >= x - 1 && mx < x + WIDTH + 1 && my >= y - 1 && my < y + height(state) + 1;
    }
}
