package fr.blueprint.client.editor;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Index de recherche de la palette. Pur et immuable : les titres et descriptions
 * arrivent déjà traduits (I18n au moment de l'ouverture). Recherche floue sur titre,
 * description, catégorie et mod fournisseur (namespace) — ≤ 5 ms pour 2 000 types
 * (AC4 de la 5.4, vérifié par test).
 */
public final class NodeSearch {

    /** Un type de nœud vu par la palette ; le fournisseur est le namespace. */
    public record Entry(Identifier id, String title, String description, String category) {

        public String provider() {
            return id.getNamespace();
        }
    }

    private final List<Entry> entries;
    private final List<String> titlesLower;
    private final List<String> descriptionsLower;

    public NodeSearch(List<Entry> entries) {
        this.entries = List.copyOf(entries);
        this.titlesLower = new ArrayList<>(entries.size());
        this.descriptionsLower = new ArrayList<>(entries.size());
        for (Entry e : this.entries) {
            titlesLower.add(e.title().toLowerCase(Locale.ROOT));
            descriptionsLower.add(e.description().toLowerCase(Locale.ROOT));
        }
    }

    /** Résultats classés ; requête vide → tout (filtré), par titre. */
    public List<Entry> search(String query, Predicate<Entry> filter, int limit) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (!filter.test(e)) {
                continue;
            }
            int score = q.isEmpty() ? 1 : score(q, i, e);
            if (score > 0) {
                scored.add(new Scored(e, score));
            }
        }
        scored.sort(Comparator.comparingInt((Scored s) -> -s.score)
                .thenComparing(s -> s.entry.title()));
        List<Entry> out = new ArrayList<>(Math.min(limit, scored.size()));
        for (int i = 0; i < scored.size() && i < limit; i++) {
            out.add(scored.get(i).entry);
        }
        return out;
    }

    private int score(String q, int index, Entry e) {
        String title = titlesLower.get(index);
        if (title.startsWith(q)) {
            return 100;
        }
        if (title.contains(" " + q)) {
            return 80;
        }
        if (title.contains(q)) {
            return 60;
        }
        if (isSubsequence(q, title)) {
            return 40;
        }
        if (e.category().contains(q) || e.provider().contains(q)) {
            return 30;
        }
        if (descriptionsLower.get(index).contains(q)) {
            return 20;
        }
        return 0;
    }

    private static boolean isSubsequence(String q, String s) {
        int qi = 0;
        for (int si = 0; si < s.length() && qi < q.length(); si++) {
            if (s.charAt(si) == q.charAt(qi)) {
                qi++;
            }
        }
        return qi == q.length();
    }

    private record Scored(Entry entry, int score) {
    }
}
