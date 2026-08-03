package fr.blueprint.client.editor;

import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.PinKind;
import fr.blueprint.client.config.PalettePrefs;
import fr.blueprint.core.registry.NodeDescriptor;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * État de la palette (5.4a + 5.4b) : requête et résultats classés, ou — sans
 * requête — navigation ★ favoris / récents / catégories repliables. Les nœuds
 * au-dessus du plafond de permission restent visibles mais marqués bloqués (U2).
 * Pur — le rendu et les entrées vivent dans {@link PalettePopup} et
 * {@code CanvasWidget}.
 *
 * <p>Le filtre de compatibilité est structurel (kind + assignabilité sur les
 * descripteurs) : le nœud n'existe pas encore, {@code canLink} ne peut pas
 * trancher. L'auto-connexion après insertion, elle, repasse par {@code canLink}.
 */
public final class PaletteState {

    /** Lignes visibles simultanément (le reste défile). */
    public static final int VISIBLE_ROWS = 10;
    private static final int SEARCH_LIMIT = 200;

    /** Une ligne de la palette : section, catégorie repliable, ou entrée insérable. */
    public sealed interface Item {
        record Section(String labelKey) implements Item {
        }

        record Category(String name, int count, boolean expanded) implements Item {
        }

        record EntryItem(NodeSearch.Entry entry, boolean favorite, boolean blocked) implements Item {
        }
    }

    private final NodeSearch search;
    private final Function<Identifier, NodeDescriptor> descriptors;
    private final PalettePrefs prefs;
    private final Supplier<Permission> permissionCap;

    private boolean open;
    private String query = "";
    private List<NodeSearch.Entry> entries = List.of();
    /** Correspondance ligne ↔ entrée par POSITION (voir {@link #indexRows()}). */
    private int[] itemToEntry = new int[0];
    private int[] entryToItem = new int[0];
    private List<Item> items = List.of();
    private int selected;
    private int scroll;
    private final Set<String> collapsed = new LinkedHashSet<>();
    private double anchorX;
    private double anchorY;
    private double worldX;
    private double worldY;
    private @Nullable CanvasController.PinRef wireFrom;

    public PaletteState(NodeSearch search, Function<Identifier, NodeDescriptor> descriptors,
                        PalettePrefs prefs, Supplier<Permission> permissionCap) {
        this.search = search;
        this.descriptors = descriptors;
        this.prefs = prefs;
        this.permissionCap = permissionCap;
    }

    public PalettePrefs prefs() {
        return prefs;
    }

    /** Ouvre à l'ancre écran donnée ; {@code wireFrom} non nul = filtre par type. */
    public void open(double anchorX, double anchorY, double worldX, double worldY,
                     @Nullable CanvasController.PinRef wireFrom) {
        this.open = true;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.worldX = worldX;
        this.worldY = worldY;
        this.wireFrom = wireFrom;
        this.query = "";
        refresh();
    }

    public void close() {
        open = false;
        wireFrom = null;
    }

    public boolean isOpen() {
        return open;
    }

    public String query() {
        return query;
    }

    /** Les entrées insérables, dans l'ordre d'affichage (la sélection les parcourt). */
    public List<NodeSearch.Entry> results() {
        return entries;
    }

    /** Les lignes affichées (sections, catégories, entrées). */
    public List<Item> items() {
        return items;
    }

    public int scroll() {
        return scroll;
    }

    public void scrollBy(int delta) {
        scroll = Math.clamp(scroll + delta, 0, Math.max(0, items.size() - VISIBLE_ROWS));
    }

    public int selectedIndex() {
        return selected;
    }

    public @Nullable NodeSearch.Entry selectedEntry() {
        return selected >= 0 && selected < entries.size() ? entries.get(selected) : null;
    }

    public double anchorX() {
        return anchorX;
    }

    public double anchorY() {
        return anchorY;
    }

    public double worldX() {
        return worldX;
    }

    public double worldY() {
        return worldY;
    }

    public @Nullable CanvasController.PinRef wireFrom() {
        return wireFrom;
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
        if (!entries.isEmpty()) {
            selected = Math.clamp(selected + delta, 0, entries.size() - 1);
            ensureSelectedVisible();
        }
    }

    public void select(int entryIndex) {
        if (entryIndex >= 0 && entryIndex < entries.size()) {
            selected = entryIndex;
        }
    }

    /**
     * L'indice d'entrée correspondant à une ligne, ou −1 (section, catégorie).
     *
     * <p>Par POSITION, pas par identité : un nœud mis en favori apparaît DEUX fois,
     * une fois dans « Favoris » et une fois dans sa catégorie. Chercher son entrée
     * dans la liste plate renvoyait toujours la première — cliquer la ligne de la
     * catégorie surlignait celle des favoris, tout en haut.
     */
    public int entryIndexOf(int itemIndex) {
        return itemIndex >= 0 && itemIndex < itemToEntry.length ? itemToEntry[itemIndex] : -1;
    }

    public void toggleCategory(String name) {
        if (!collapsed.remove(name)) {
            collapsed.add(name);
        }
        refresh();
    }

    /** Bascule le favori d'une entrée ; l'appelant persiste les préférences. */
    public void toggleFavorite(Identifier id) {
        prefs.toggleFavorite(id);
        refresh();
    }

    public void noteInserted(Identifier id) {
        prefs.addRecent(id);
    }

    // -------------------------------------------------------------------- contenu

    private void refresh() {
        selected = 0;
        scroll = 0;
        List<Item> out = new ArrayList<>();
        List<NodeSearch.Entry> flat = new ArrayList<>();
        if (!query.isBlank()) {
            for (NodeSearch.Entry entry : search.search(query, this::compatible, SEARCH_LIMIT)) {
                out.add(wrap(entry));
                flat.add(entry);
            }
        } else {
            List<NodeSearch.Entry> all = search.search("", this::compatible, SEARCH_LIMIT);
            Map<Identifier, NodeSearch.Entry> byId = new LinkedHashMap<>();
            all.forEach(e -> byId.put(e.id(), e));

            section(out, flat, "blueprint.editor.palette.favorites",
                    prefs.favorites().stream().map(byId::get).filter(e -> e != null).toList());
            section(out, flat, "blueprint.editor.palette.recents",
                    prefs.recents().stream().map(byId::get).filter(e -> e != null).toList());

            Map<String, List<NodeSearch.Entry>> byCategory = new LinkedHashMap<>();
            all.forEach(e -> byCategory.computeIfAbsent(e.category(), k -> new ArrayList<>()).add(e));
            byCategory.keySet().stream().sorted(Comparator.naturalOrder()).forEach(category -> {
                List<NodeSearch.Entry> members = byCategory.get(category);
                boolean expanded = !collapsed.contains(category);
                out.add(new Item.Category(category, members.size(), expanded));
                if (expanded) {
                    for (NodeSearch.Entry entry : members) {
                        out.add(wrap(entry));
                        flat.add(entry);
                    }
                }
            });
        }
        items = List.copyOf(out);
        entries = List.copyOf(flat);
        indexRows();
    }

    /**
     * Correspondance ligne ↔ entrée, dans les deux sens. Construite ici et pas
     * cherchée à la demande : une entrée peut apparaître à DEUX lignes (favoris et
     * catégorie), et seule la position les distingue.
     */
    private void indexRows() {
        itemToEntry = new int[items.size()];
        entryToItem = new int[entries.size()];
        java.util.Arrays.fill(itemToEntry, -1);
        java.util.Arrays.fill(entryToItem, -1);
        int entry = 0;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) instanceof Item.EntryItem && entry < entryToItem.length) {
                itemToEntry[i] = entry;
                entryToItem[entry] = i;
                entry++;
            }
        }
    }

    private void section(List<Item> out, List<NodeSearch.Entry> flat, String labelKey,
                         List<NodeSearch.Entry> members) {
        if (members.isEmpty()) {
            return;
        }
        out.add(new Item.Section(labelKey));
        for (NodeSearch.Entry entry : members) {
            out.add(wrap(entry));
            flat.add(entry);
        }
    }

    private Item.EntryItem wrap(NodeSearch.Entry entry) {
        return new Item.EntryItem(entry, prefs.isFavorite(entry.id()), blocked(entry));
    }

    /** Au-dessus du plafond de permission : visible mais marqué (jamais masqué, U2). */
    public boolean blocked(NodeSearch.Entry entry) {
        NodeDescriptor desc = descriptors.apply(entry.id());
        return desc != null && !desc.permission().allowedUnder(permissionCap.get());
    }

    private void ensureSelectedVisible() {
        // Même piège que entryIndexOf : chercher la ligne par identité d'entrée
        // ramenait celle des favoris et faisait remonter la liste tout en haut.
        int i = itemRowOf(selected);
        if (i < 0) {
            return;
        }
        int maxScroll = Math.max(0, items.size() - VISIBLE_ROWS);
        scroll = Math.clamp(scroll,
                Math.min(Math.max(0, i - VISIBLE_ROWS + 1), maxScroll),
                Math.min(i, maxScroll));
    }

    /** La ligne affichée d'une entrée, ou −1. Inverse de {@link #entryIndexOf}. */
    public int itemRowOf(int entryIndex) {
        return entryIndex >= 0 && entryIndex < entryToItem.length
                ? entryToItem[entryIndex] : -1;
    }

    /** Sans lien source : tout passe. Sinon : au moins un pin compatible. */
    private boolean compatible(NodeSearch.Entry entry) {
        CanvasController.PinRef from = wireFrom;
        if (from == null) {
            return true;
        }
        NodeDescriptor desc = descriptors.apply(entry.id());
        if (desc == null) {
            return false;
        }
        List<NodeDescriptor.PinDescriptor> candidates = from.output() ? desc.inputs() : desc.outputs();
        for (int i = 0; i < candidates.size(); i++) {
            NodeDescriptor.PinDescriptor pin = candidates.get(i);
            if (pin.kind() != from.kind()) {
                continue;
            }
            if (pin.kind() == PinKind.EXEC) {
                return true;
            }
            boolean assignable = from.output()
                    ? pin.type().isAssignableFrom(from.type())
                    : from.type().isAssignableFrom(pin.type());
            if (assignable) {
                return true;
            }
        }
        return false;
    }
}
