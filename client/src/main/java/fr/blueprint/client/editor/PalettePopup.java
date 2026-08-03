package fr.blueprint.client.editor;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;

import java.util.List;

/**
 * Rendu écran de la palette (5.4a + 5.4b) : recherche, ou navigation favoris /
 * récents / catégories ; ★ cliquable, défilement, nœuds bloqués grisés avec raison.
 * La logique vit dans {@link PaletteState}.
 */
public final class PalettePopup {

    public static final int WIDTH = 220;
    public static final int ROW_HEIGHT = 13;
    /** En-tête : titre + champ de recherche (et rappel du filtre au besoin). */
    private static final int HEADER_HEIGHT = 28;
    /** Zone ★ au bord gauche d'une ligne d'entrée. */
    public static final int STAR_WIDTH = 12;

    private static final int BACKGROUND = 0xF01A1B1E;
    private static final int BORDER = 0xFF3A3D42;
    private static final int ROW_SELECTED = 0xFF2F3A55;
    private static final int TITLE_COLOR = 0xFF8A8F98;
    private static final int QUERY_COLOR = 0xFFE6E6E6;
    private static final int ENTRY_COLOR = 0xFFD5D8DC;
    private static final int BLOCKED_COLOR = 0xFF5A5F68;
    private static final int META_COLOR = 0xFF7A7F88;
    private static final int FILTER_COLOR = 0xFF7DCFFF;
    private static final int STAR_ON = 0xFFE5C07B;
    private static final int STAR_OFF = 0xFF4A4F58;
    private static final int SECTION_COLOR = 0xFF7DCFFF;

    private PalettePopup() {
    }

    /**
     * Nom lisible d'une catégorie. Les catégories s'affichaient BRUTES — « flow »,
     * « event » — alors que ce sont les seuls repères du menu d'ajout.
     *
     * <p>Repli sur l'identifiant si la clé n'existe pas : un mod tiers déclare la
     * catégorie qu'il veut, et le projet n'a évidemment pas sa traduction. Mieux vaut
     * « mymod_magie » qu'une clé brute à rallonge.
     */
    public static String categoryLabel(String category) {
        String key = "blueprint.category." + category;
        return I18n.exists(key) ? I18n.get(key) : category;
    }

    private static int visibleRows(PaletteState state) {
        return Math.min(state.items().size(), PaletteState.VISIBLE_ROWS);
    }

    public static int height(PaletteState state) {
        return HEADER_HEIGHT + Math.max(1, visibleRows(state)) * ROW_HEIGHT + 4;
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
        g.drawString(font, "> " + state.query() + "_", x + 4, y + 15, QUERY_COLOR, false);

        List<PaletteState.Item> items = state.items();
        if (items.isEmpty()) {
            g.drawString(font, I18n.get("blueprint.editor.palette.empty"),
                    x + 4, y + HEADER_HEIGHT + 3, META_COLOR, false);
            return;
        }
        int rows = visibleRows(state);
        for (int r = 0; r < rows; r++) {
            int index = state.scroll() + r;
            if (index >= items.size()) {
                break;
            }
            int rowY = y + HEADER_HEIGHT + r * ROW_HEIGHT;
            switch (items.get(index)) {
                case PaletteState.Item.Section(String labelKey) ->
                        g.drawString(font, I18n.get(labelKey), x + 4, rowY + 3, SECTION_COLOR, false);
                case PaletteState.Item.Category(String name, int count, boolean expanded) ->
                        g.drawString(font, (expanded ? "▾ " : "▸ ")
                                        + categoryLabel(name) + " (" + count + ")",
                                x + 4, rowY + 3, TITLE_COLOR, false);
                case PaletteState.Item.EntryItem(var entry, boolean favorite, boolean blocked) -> {
                    if (state.entryIndexOf(index) == state.selectedIndex()) {
                        g.fill(x + 1, rowY, x + WIDTH - 1, rowY + ROW_HEIGHT, ROW_SELECTED);
                    }
                    g.drawString(font, "★", x + 3, rowY + 3, favorite ? STAR_ON : STAR_OFF, false);
                    String meta = blocked
                            ? I18n.get("blueprint.editor.palette.blocked")
                            : entry.category();
                    int metaW = font.width(meta);
                    g.drawString(font, font.plainSubstrByWidth(entry.title(),
                                    WIDTH - 20 - STAR_WIDTH - metaW),
                            x + 4 + STAR_WIDTH, rowY + 3,
                            blocked ? BLOCKED_COLOR : ENTRY_COLOR, false);
                    g.drawString(font, meta, x + WIDTH - 6 - metaW, rowY + 3,
                            blocked ? BLOCKED_COLOR : META_COLOR, false);
                }
            }
        }
        // Témoin de défilement : position dans la liste.
        if (items.size() > rows) {
            int track = rows * ROW_HEIGHT;
            int thumb = Math.max(6, track * rows / items.size());
            int offset = (track - thumb) * state.scroll() / Math.max(1, items.size() - rows);
            g.fill(x + WIDTH - 2, y + HEADER_HEIGHT + offset,
                    x + WIDTH - 1, y + HEADER_HEIGHT + offset + thumb, TITLE_COLOR);
        }
    }

    /** Indice de LIGNE (dans items) sous la souris, ou −1 (aussi −1 hors du popup). */
    public static int rowAt(PaletteState state, double mx, double my, int screenW, int screenH) {
        int x = left(state, screenW);
        int y = top(state, screenH);
        if (mx < x || mx >= x + WIDTH || my < y + HEADER_HEIGHT || my >= y + height(state)) {
            return -1;
        }
        int row = (int) ((my - y - HEADER_HEIGHT) / ROW_HEIGHT);
        int index = state.scroll() + row;
        return index < state.items().size() ? index : -1;
    }

    /** Le clic est-il dans la zone ★ d'une ligne ? */
    public static boolean starAt(PaletteState state, double mx, int screenW) {
        int x = left(state, screenW);
        return mx >= x && mx < x + 4 + STAR_WIDTH;
    }

    /** Le point est-il dans le popup (pour ne pas fermer sur un clic dedans) ? */
    public static boolean contains(PaletteState state, double mx, double my,
                                   int screenW, int screenH) {
        int x = left(state, screenW);
        int y = top(state, screenH);
        return mx >= x - 1 && mx < x + WIDTH + 1 && my >= y - 1 && my < y + height(state) + 1;
    }
}
