package fr.blueprint.client.editor;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;

import java.util.List;

/**
 * Rendu écran de la palette (5.4a + 5.4b, refondue en 12.1) : un champ de recherche,
 * la case « Contextuel », et un index de catégories repliables. Les nœuds bloqués y
 * restent visibles, grisés, avec leur raison.
 *
 * <p>La logique vit dans {@link PaletteState}.
 */
public final class PalettePopup {

    /**
     * Largeur. Élargie de 220 : sous une catégorie dépliée, le nom du nœud était
     * tronqué par la colonne de catégorie répétée à droite — laquelle a disparu, et
     * la place a servi au titre.
     */
    public static final int WIDTH = 262;
    public static final int ROW_HEIGHT = 13;
    /**
     * En-tête : titre, case « Context Sensitive », champ de recherche encadré.
     *
     * <p>Trois lignes au lieu de deux. La case n'a pas d'autre endroit où vivre, et
     * l'encadrement du champ dit qu'on peut y taper — l'invite {@code >} du début ne le
     * disait qu'à qui le savait déjà.
     */
    private static final int HEADER_HEIGHT = 40;
    /** Hauteur de la case à cocher, alignée sur le texte de la ligne de titre. */
    private static final int CHECKBOX = 7;

    private static final int BACKGROUND = 0xF01A1B1E;
    private static final int BORDER = 0xFF3A3D42;
    private static final int ROW_SELECTED = 0xFF2F3A55;
    private static final int TITLE_COLOR = 0xFF8A8F98;
    private static final int QUERY_COLOR = 0xFFE6E6E6;
    private static final int ENTRY_COLOR = 0xFFD5D8DC;
    private static final int BLOCKED_COLOR = 0xFF5A5F68;
    private static final int META_COLOR = 0xFF7A7F88;
    private static final int FILTER_COLOR = 0xFF7DCFFF;
    /** Sous-catégorie : même rôle, un cran plus discret et décalé. */
    private static final int SUBTITLE_COLOR = 0xFF6E737C;
    private static final int SUB_INDENT = 8;

    private static final int FIELD_BACKGROUND = 0xFF101114;
    private static final int FIELD_BORDER = 0xFF4A4F58;
    private static final int PLACEHOLDER_COLOR = 0xFF6A6F78;
    private static final int CHECK_COLOR = 0xFF7DCFFF;

    private PalettePopup() {
    }

    /**
     * La case « Context Sensitive » et son libellé, alignés à droite comme dans Unreal.
     *
     * <p>Elle dit ce que la liste montre : cochée, seulement ce qui se câble au fil
     * tiré ; décochée, tout. Sans elle, un auteur qui ne trouvait pas un nœud dans une
     * liste filtrée en concluait qu'il n'existait pas.
     */
    private static void renderCheckbox(GuiGraphics g, Font font, PaletteState state,
                                       int x, int y) {
        String label = I18n.get("blueprint.editor.palette.context");
        int labelW = font.width(label);
        int boxX = x + WIDTH - 6 - labelW - CHECKBOX - 3;
        int boxY = y + 4;
        g.fill(boxX, boxY, boxX + CHECKBOX, boxY + CHECKBOX, FIELD_BACKGROUND);
        g.fill(boxX, boxY, boxX + CHECKBOX, boxY + 1, FIELD_BORDER);
        g.fill(boxX, boxY + CHECKBOX - 1, boxX + CHECKBOX, boxY + CHECKBOX, FIELD_BORDER);
        g.fill(boxX, boxY, boxX + 1, boxY + CHECKBOX, FIELD_BORDER);
        g.fill(boxX + CHECKBOX - 1, boxY, boxX + CHECKBOX, boxY + CHECKBOX, FIELD_BORDER);
        if (state.contextSensitive()) {
            g.fill(boxX + 2, boxY + 2, boxX + CHECKBOX - 2, boxY + CHECKBOX - 2, CHECK_COLOR);
        }
        g.drawString(font, label, boxX + CHECKBOX + 3, y + 3,
                state.contextSensitive() ? FILTER_COLOR : META_COLOR, false);
    }

    /**
     * La case est-elle sous ce point ? Le clic la bascule.
     *
     * <p>La zone cliquable est toute la <b>moitié droite</b> de la ligne de titre, et non
     * les sept pixels de la case. Deux raisons : viser sept pixels à la souris est une
     * épreuve, et la largeur du libellé change avec la langue — la mesurer ici obligerait
     * à passer la police, donc à rendre cette fonction invérifiable sans client.
     */
    public static boolean checkboxAt(PaletteState state, double mx, double my,
                                     int screenW, int screenH) {
        if (state.wireFrom() == null) {
            return false;
        }
        int x = left(state, screenW);
        int y = top(state, screenH);
        return mx >= x + WIDTH / 2 && mx < x + WIDTH && my >= y && my < y + 14;
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

        g.drawString(font, font.plainSubstrByWidth(
                        I18n.get("blueprint.editor.palette.title"), WIDTH - 8),
                x + 4, y + 3, TITLE_COLOR, false);

        // La case, seulement quand elle décide de quelque chose : sans fil tiré, elle
        // ne filtre rien, et l'afficher inerte serait proposer un bouton qui ne fait
        // rien. C'est aussi ce que fait Unreal, qui la grise hors contexte.
        if (state.wireFrom() != null) {
            renderCheckbox(g, font, state, x, y);
        }

        // Le champ de recherche, ENCADRÉ. Il s'annonce ainsi comme un endroit où taper ;
        // l'invite « > » ne le disait qu'à qui le savait déjà.
        int fieldY = y + 25;
        g.fill(x + 4, fieldY, x + WIDTH - 4, fieldY + 12, FIELD_BACKGROUND);
        g.fill(x + 4, fieldY, x + WIDTH - 4, fieldY + 1, FIELD_BORDER);
        g.fill(x + 4, fieldY + 11, x + WIDTH - 4, fieldY + 12, FIELD_BORDER);
        g.fill(x + 4, fieldY, x + 5, fieldY + 12, FIELD_BORDER);
        g.fill(x + WIDTH - 5, fieldY, x + WIDTH - 4, fieldY + 12, FIELD_BORDER);
        if (state.query().isEmpty()) {
            g.drawString(font, I18n.get("blueprint.editor.palette.search"),
                    x + 9, fieldY + 2, PLACEHOLDER_COLOR, false);
        } else {
            g.drawString(font, font.plainSubstrByWidth(state.query() + "_", WIDTH - 18),
                    x + 9, fieldY + 2, QUERY_COLOR, false);
        }

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
                case PaletteState.Item.Category(String n, int count, boolean open, int depth) ->
                        // L'indentation est la SEULE chose qui distingue une
                        // sous-catégorie de sa parente : sans elle, l'arbre se lit
                        // comme une liste plate deux fois trop longue.
                        g.drawString(font, (open ? "▾ " : "▸ ")
                                        + categoryLabel(n) + " (" + count + ")",
                                x + 4 + depth * SUB_INDENT, rowY + 3,
                                depth == 0 ? TITLE_COLOR : SUBTITLE_COLOR, false);
                case PaletteState.Item.EntryItem(var entry, boolean blocked) -> {
                    if (state.entryIndexOf(index) == state.selectedIndex()) {
                        g.fill(x + 1, rowY, x + WIDTH - 1, rowY + ROW_HEIGHT, ROW_SELECTED);
                    }
                    // La catégorie n'est répétée que là où elle APPREND quelque chose :
                    // en recherche, en favoris, en récents — c'est-à-dire quand la ligne
                    // ne vit pas déjà sous l'en-tête qui la nomme. La répéter partout
                    // volait un tiers de la largeur au titre du nœud pour redire ce que
                    // la ligne du dessus venait d'annoncer.
                    String meta = blocked ? I18n.get("blueprint.editor.palette.blocked")
                            : state.showsCategoryColumn() ? entry.category() : "";
                    int metaW = meta.isEmpty() ? 0 : font.width(meta) + 6;
                    // Les entrées d'une catégorie dépliée sont INDENTÉES sous elle :
                    // sans cela, l'arbre se relit comme une liste plate et le repère
                    // qu'on vient d'ouvrir se perd dès la première ligne.
                    int indent = state.showsCategoryColumn() ? 0 : SUB_INDENT;
                    g.drawString(font, font.plainSubstrByWidth(entry.title(),
                                    WIDTH - 16 - metaW - indent),
                            x + 6 + indent, rowY + 3,
                            blocked ? BLOCKED_COLOR : ENTRY_COLOR, false);
                    if (!meta.isEmpty()) {
                        g.drawString(font, meta, x + WIDTH - metaW, rowY + 3,
                                blocked ? BLOCKED_COLOR : META_COLOR, false);
                    }
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

    /** Le point est-il dans le popup (pour ne pas fermer sur un clic dedans) ? */
    public static boolean contains(PaletteState state, double mx, double my,
                                   int screenW, int screenH) {
        int x = left(state, screenW);
        int y = top(state, screenH);
        return mx >= x - 1 && mx < x + WIDTH + 1 && my >= y - 1 && my < y + height(state) + 1;
    }
}
