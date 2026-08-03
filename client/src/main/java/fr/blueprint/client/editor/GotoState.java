package fr.blueprint.client.editor;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * « Aller au nœud » (Ctrl+F, story 5.7) : recherche parmi les nœuds DU graphe
 * courant, Entrée recentre. Pur — les titres traduits sont injectés à l'ouverture.
 */
public final class GotoState {

    public static final int MAX_RESULTS = 8;

    public record Target(UUID node, String title) {
    }

    private boolean open;
    private List<Target> all = List.of();
    private List<Target> results = List.of();
    private String query = "";
    private int selected;

    public void open(List<Target> targets) {
        open = true;
        all = List.copyOf(targets);
        query = "";
        refresh();
    }

    public void close() {
        open = false;
    }

    public boolean isOpen() {
        return open;
    }

    public String query() {
        return query;
    }

    public List<Target> results() {
        return results;
    }

    public int selectedIndex() {
        return selected;
    }

    public @Nullable Target selectedTarget() {
        return selected >= 0 && selected < results.size() ? results.get(selected) : null;
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

    public void moveSelection(int delta) {
        if (!results.isEmpty()) {
            selected = Math.clamp(selected + delta, 0, results.size() - 1);
        }
    }

    private void refresh() {
        selected = 0;
        String q = query.toLowerCase(Locale.ROOT);
        List<Target> out = new ArrayList<>();
        for (Target target : all) {
            if (q.isBlank() || target.title().toLowerCase(Locale.ROOT).contains(q)) {
                out.add(target);
                if (out.size() >= MAX_RESULTS) {
                    break;
                }
            }
        }
        results = List.copyOf(out);
    }
}
