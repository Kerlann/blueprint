package fr.blueprint.client.editor;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Sélecteur d'item ou de bloc (story 5.2c) : grille paginée + recherche sur le nom
 * traduit et l'identifiant. Pur : les entrées (registre + titres) sont injectées à
 * l'ouverture, le rendu des icônes vit dans {@link RegistryPickerPopup}.
 */
public final class PickerState {

    public static final int COLS = 8;
    public static final int ROWS = 5;

    public record Entry(Identifier id, String title) {
    }

    private boolean open;
    private boolean block;
    private UUID node;
    private String pin = "";
    private List<Entry> all = List.of();
    private List<Entry> filtered = List.of();
    private String query = "";
    private int scrollRow;

    public void open(UUID node, String pin, boolean block, List<Entry> entries) {
        this.open = true;
        this.node = node;
        this.pin = pin;
        this.block = block;
        this.all = entries;
        this.query = "";
        this.scrollRow = 0;
        refresh();
    }

    public void close() {
        open = false;
    }

    public boolean isOpen() {
        return open;
    }

    public boolean isBlock() {
        return block;
    }

    public UUID node() {
        return node;
    }

    public String pin() {
        return pin;
    }

    public String query() {
        return query;
    }

    public List<Entry> filtered() {
        return filtered;
    }

    /** Fenêtre visible de la grille, à plat (COLS×ROWS au plus). */
    public List<Entry> window() {
        int from = scrollRow * COLS;
        int to = Math.min(filtered.size(), from + COLS * ROWS);
        return from >= filtered.size() ? List.of() : filtered.subList(from, to);
    }

    /** L'entrée à l'indice de cellule visible, ou null. */
    public @Nullable Entry at(int cellIndex) {
        List<Entry> window = window();
        return cellIndex >= 0 && cellIndex < window.size() ? window.get(cellIndex) : null;
    }

    public void type(String text) {
        query += text;
        refresh();
    }

    public void backspace() {
        if (!query.isEmpty()) {
            query = query.substring(0, query.length() - 1);
            refresh();
        }
    }

    public void scrollBy(int rows) {
        int maxRow = Math.max(0, (filtered.size() - 1) / COLS - ROWS + 1);
        scrollRow = Math.clamp(scrollRow + rows, 0, maxRow);
    }

    public int scrollRow() {
        return scrollRow;
    }

    private void refresh() {
        scrollRow = 0;
        if (query.isBlank()) {
            filtered = all;
            return;
        }
        String q = query.toLowerCase(Locale.ROOT);
        List<Entry> out = new ArrayList<>();
        for (Entry entry : all) {
            if (entry.title().toLowerCase(Locale.ROOT).contains(q)
                    || entry.id().toString().contains(q)) {
                out.add(entry);
            }
        }
        filtered = out;
    }
}
