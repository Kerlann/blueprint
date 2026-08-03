package fr.blueprint.client.editor;

import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.PinKind;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.registry.NodeDescriptor;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Recherche ({@link NodeSearch}) et état de palette ({@link PaletteState}) — purs. */
class PaletteTest {

    private static NodeSearch.Entry entry(String path, String title, String category) {
        return new NodeSearch.Entry(Identifier.fromNamespaceAndPath("blueprint", path),
                title, "desc " + title, category);
    }

    // ---------------------------------------------------------------- recherche

    @Test
    void classementPrefixePuisMotPuisContenu() {
        NodeSearch search = new NodeSearch(List.of(
                entry("a", "Addition", "math"),
                entry("b", "Grand adder", "math"),
                entry("c", "Radd", "math"),
                entry("d", "Sans rapport", "flow")));
        List<NodeSearch.Entry> r = search.search("add", e -> true, 10);
        assertEquals(3, r.size());
        assertEquals("Addition", r.get(0).title());   // préfixe
        assertEquals("Grand adder", r.get(1).title()); // début de mot
        assertEquals("Radd", r.get(2).title());        // contenu
    }

    @Test
    void rechercheSurFournisseurEtDescription() {
        NodeSearch search = new NodeSearch(List.of(
                new NodeSearch.Entry(Identifier.fromNamespaceAndPath("mymod", "x"),
                        "Soigner", "répare la santé", "entity"),
                entry("y", "Casser", "world")));
        assertEquals(1, search.search("mymod", e -> true, 10).size());
        assertEquals(1, search.search("santé", e -> true, 10).size());
    }

    @Test
    void requeteVideRetourneToutFiltre() {
        NodeSearch search = new NodeSearch(List.of(
                entry("a", "A", "math"), entry("b", "B", "flow")));
        assertEquals(2, search.search("", e -> true, 10).size());
        assertEquals(1, search.search("", e -> e.category().equals("math"), 10).size());
    }

    @Test
    void performance2000TypesSous5Ms() {
        List<NodeSearch.Entry> entries = new ArrayList<>();
        for (int i = 0; i < 2_000; i++) {
            entries.add(entry("n" + i, "Nœud numéro " + i + " alpha beta", "cat" + (i % 12)));
        }
        NodeSearch search = new NodeSearch(entries);
        search.search("alpha", e -> true, 8); // échauffement
        // Meilleure de 5 mesures : l'AC vise la capacité, pas la variance d'une
        // machine de CI chargée (flake observé à 6 ms sous deux builds parallèles).
        double best = Double.MAX_VALUE;
        for (int run = 0; run < 5; run++) {
            long start = System.nanoTime();
            List<NodeSearch.Entry> r = search.search("beta 42", e -> true, 8);
            best = Math.min(best, (System.nanoTime() - start) / 1e6);
            assertNotNull(r);
        }
        assertTrue(best < 5, "recherche en " + best + " ms (AC4 : ≤ 5 ms)");
    }

    // ------------------------------------------------------------------ palette

    private static final NodeDescriptor EXEC_NODE = descriptor("exec_node",
            List.of(pin("exec_in", PinKind.EXEC)), List.of(pin("exec_out", PinKind.EXEC)));
    private static final NodeDescriptor PURE_NODE = descriptor("pure_node",
            List.of(pinDouble("a")), List.of(pinDouble("out")));

    private static NodeDescriptor descriptor(String path, List<NodeDescriptor.PinDescriptor> in,
                                             List<NodeDescriptor.PinDescriptor> out) {
        return new NodeDescriptor(Identifier.fromNamespaceAndPath("blueprint", path),
                "math", "t." + path, "d." + path, in, out, true, Permission.SAFE, 1, true);
    }

    private static NodeDescriptor.PinDescriptor pin(String name, PinKind kind) {
        return new NodeDescriptor.PinDescriptor(name, kind, PinTypes.EXEC, null);
    }

    private static NodeDescriptor.PinDescriptor pinDouble(String name) {
        return new NodeDescriptor.PinDescriptor(name, PinKind.DATA, PinTypes.DOUBLE, null);
    }

    private static java.util.function.Function<Identifier, NodeDescriptor> descriptorsForTest() {
        Map<Identifier, NodeDescriptor> descs = new HashMap<>();
        descs.put(EXEC_NODE.id(), EXEC_NODE);
        descs.put(PURE_NODE.id(), PURE_NODE);
        return descs::get;
    }

    private static PaletteState palette() {
        NodeSearch search = new NodeSearch(List.of(
                new NodeSearch.Entry(EXEC_NODE.id(), "Exec node", "d", "flow"),
                new NodeSearch.Entry(PURE_NODE.id(), "Pure node", "d", "math")));
        return new PaletteState(search, descriptorsForTest(),
                new fr.blueprint.client.config.PalettePrefs(), () -> Permission.GAMEPLAY);
    }

    @Test
    void ouvertureSansFiltreMontreTout() {
        PaletteState p = palette();
        p.open(10, 10, 100, 100, null);
        assertTrue(p.isOpen());
        assertEquals(2, p.results().size());
    }

    @Test
    void filtreParTypeDepuisUnLien() {
        PaletteState p = palette();
        // Depuis une sortie exec : seuls les nœuds à entrée exec restent.
        CanvasController.PinRef from = new CanvasController.PinRef(
                UUID.randomUUID(), "exec_out", PinKind.EXEC, PinTypes.EXEC, true, 0);
        p.open(10, 10, 100, 100, from);
        assertEquals(1, p.results().size());
        assertEquals(EXEC_NODE.id(), p.results().get(0).id());
    }

    @Test
    void filtreDataRespecteLAssignabilite() {
        PaletteState p = palette();
        // Depuis une entrée double : il faut une sortie assignable à double.
        CanvasController.PinRef from = new CanvasController.PinRef(
                UUID.randomUUID(), "a", PinKind.DATA, PinTypes.DOUBLE, false, 0);
        p.open(10, 10, 100, 100, from);
        assertEquals(1, p.results().size());
        assertEquals(PURE_NODE.id(), p.results().get(0).id());
    }

    // -------------------------------------------------------- navigation (5.4b)

    @Test
    void sansRequeteFavorisPuisRecentsPuisCategories() {
        PaletteState p = palette();
        p.open(0, 0, 0, 0, null);
        // Sans favoris ni récents : catégories triées, dépliées par défaut.
        assertTrue(p.items().get(0) instanceof PaletteState.Item.Category(String n, int c, boolean x, int d)
                && n.equals("flow"));
        assertEquals(2, p.results().size());

        p.toggleFavorite(PURE_NODE.id());
        p.noteInserted(EXEC_NODE.id());
        p.open(0, 0, 0, 0, null);
        // ★ Favoris d'abord, puis Récents, puis les catégories.
        assertTrue(p.items().get(0) instanceof PaletteState.Item.Section(String key)
                && key.contains("favorites"));
        assertTrue(p.items().get(1) instanceof PaletteState.Item.EntryItem(var e, boolean fav, boolean b)
                && fav && e.id().equals(PURE_NODE.id()));
        assertTrue(p.items().get(2) instanceof PaletteState.Item.Section(String key)
                && key.contains("recents"));
    }

    @Test
    void categorieRepliableEtDefilement() {
        PaletteState p = palette();
        p.open(0, 0, 0, 0, null);
        int before = p.results().size();
        p.toggleCategory("flow");
        assertEquals(before - 1, p.results().size());
        p.toggleCategory("flow");
        assertEquals(before, p.results().size());

        p.scrollBy(100);
        assertEquals(Math.max(0, p.items().size() - PaletteState.VISIBLE_ROWS), p.scroll());
        p.scrollBy(-100);
        assertEquals(0, p.scroll());
    }

    @Test
    void plafondDePermissionGriseSansMasquer() {
        NodeDescriptor admin = new NodeDescriptor(
                Identifier.fromNamespaceAndPath("blueprint", "admin_node"), "world",
                "t.admin", "d.admin", List.of(), List.of(pin("exec_out", PinKind.EXEC)),
                false, Permission.ADMIN, 1, true);
        Map<Identifier, NodeDescriptor> descs = new HashMap<>();
        descs.put(admin.id(), admin);
        PaletteState p = new PaletteState(
                new NodeSearch(List.of(new NodeSearch.Entry(admin.id(), "Admin", "d", "world"))),
                descs::get, new fr.blueprint.client.config.PalettePrefs(),
                () -> Permission.GAMEPLAY);
        p.open(0, 0, 0, 0, null);
        // Visible (jamais masqué, U2) mais marqué bloqué.
        assertEquals(1, p.results().size());
        assertTrue(p.blocked(p.results().get(0)));
    }

    @Test
    void frappeNavigationEtFermeture() {
        PaletteState p = palette();
        p.open(10, 10, 100, 100, null);
        p.type("pure");
        assertEquals(1, p.results().size());
        assertEquals("Pure node", p.results().get(0).title());
        p.backspace();
        p.backspace();
        p.backspace();
        p.backspace();
        assertEquals(2, p.results().size());
        p.moveSelection(1);
        assertEquals(1, p.selectedIndex());
        assertNotNull(p.selectedEntry());
        p.close();
        assertFalse(p.isOpen());
    }

    /**
     * Un nœud en favori apparaît DEUX fois : dans « Favoris » et dans sa catégorie.
     * Le clic doit sélectionner la ligne cliquée, pas sa jumelle — sinon le
     * surlignage saute tout en haut de la liste sans rien expliquer.
     */
    @Test
    void unFavoriApparaitDeuxFoisEtChaqueLigneEstDistincte() {
        PaletteState p = palette();
        p.toggleFavorite(PURE_NODE.id());
        p.open(0, 0, 0, 0, null);

        // Les deux lignes qui portent le même nœud.
        List<Integer> rows = new java.util.ArrayList<>();
        for (int i = 0; i < p.items().size(); i++) {
            if (p.items().get(i) instanceof PaletteState.Item.EntryItem(var e, var f, var b)
                    && e.id().equals(PURE_NODE.id())) {
                rows.add(i);
            }
        }
        assertEquals(2, rows.size(), "une fois en favori, une fois dans sa catégorie");

        int premiere = p.entryIndexOf(rows.get(0));
        int seconde = p.entryIndexOf(rows.get(1));
        assertTrue(premiere >= 0 && seconde >= 0);
        assertFalse(premiere == seconde, "deux lignes, deux indices d'entrée");

        // Et la correspondance revient bien sur la ligne cliquée, dans les deux sens.
        p.select(seconde);
        assertEquals(seconde, p.selectedIndex());
        assertEquals(rows.get(1).intValue(), p.itemRowOf(seconde));
        assertEquals(rows.get(0).intValue(), p.itemRowOf(premiere));
    }

    // ------------------------------------------- tri du menu d'ajout (5.13, UE5)

    /**
     * On commence un graphe par un <b>événement</b>, on le nourrit de
     * <b>variables</b>, le reste vient après. Le tri alphabétique mettait « debug »
     * en tête et « event » au milieu : l'ordre d'une table des matières, pas celui
     * dans lequel on travaille.
     */
    @Test
    void lesCategoriesSontTrieesParUsagePasParAlphabet() {
        List<String> ordre = new ArrayList<>(List.of(
                "world", "debug", "flow", PaletteState.VARIABLES, "event", "math"));
        ordre.sort(PaletteState.CATEGORY_ORDER);

        assertEquals(List.of("event", PaletteState.VARIABLES, "flow",
                "debug", "math", "world"), ordre);
    }

    /**
     * Les variables du blueprint apparaissent dans le menu d'ajout, en « Obtenir » et
     * « Définir ». Avant, le menu ignorait qu'elles existaient : il fallait les faire
     * glisser depuis le panneau, un geste que rien n'annonce.
     */
    @Test
    void lesVariablesDuBlueprintApparaissentDansLeMenu() {
        NodeSearch.Entry get = new NodeSearch.Entry(
                fr.blueprint.core.graph.VarNodes.GET, "Obtenir score", "Entier",
                PaletteState.VARIABLES, "score");
        NodeSearch.Entry set = new NodeSearch.Entry(
                fr.blueprint.core.graph.VarNodes.SET, "Définir score", "Entier",
                PaletteState.VARIABLES, "score");

        PaletteState p = new PaletteState(
                new NodeSearch(List.of(
                        new NodeSearch.Entry(EXEC_NODE.id(), "Exec node", "d", "flow"))),
                descriptorsForTest(), new fr.blueprint.client.config.PalettePrefs(),
                () -> Permission.ADMIN,
                () -> List.of(get, set));
        p.open(0, 0, 0, 0, null);

        assertTrue(p.results().contains(get));
        assertTrue(p.results().contains(set));
        assertTrue(get.isVariable(), "et l'insertion saura QUELLE variable poser");
        assertEquals("score", get.variable());

        // La catégorie Variables est placée avant les catégories de nœuds.
        int variables = indexOfCategory(p, PaletteState.VARIABLES);
        int flow = indexOfCategory(p, "flow");
        assertTrue(variables >= 0 && flow >= 0);
        assertTrue(variables < flow, "Variables avant Contrôle du flux");
    }

    /** Sans variable déclarée, aucune catégorie Variables vide ne s'affiche. */
    @Test
    void aucuneCategorieVariablesQuandLeBlueprintNenAPas() {
        PaletteState p = palette();
        p.open(0, 0, 0, 0, null);
        assertEquals(-1, indexOfCategory(p, PaletteState.VARIABLES));
    }

    /** Un vrai nœud du registre n'est pas une variable : l'insertion ne doit pas dévier. */
    @Test
    void unNoeudOrdinaireNestPasUneVariable() {
        PaletteState p = palette();
        p.open(0, 0, 0, 0, null);
        assertFalse(p.results().get(0).isVariable());
        assertNull(p.results().get(0).variable());
    }

    // ---------------------------------------------- sous-catégories (5.14, UE5)

    /** Palette à deux catégories dont une subdivisée : math, math/arithmetic, flow. */
    private static PaletteState arborescente() {
        NodeSearch search = new NodeSearch(List.of(
                new NodeSearch.Entry(EXEC_NODE.id(), "Exec node", "d", "flow"),
                new NodeSearch.Entry(PURE_NODE.id(), "Pure node", "d", "math/arithmetic"),
                new NodeSearch.Entry(Identifier.fromNamespaceAndPath("blueprint", "z"),
                        "Direct", "d", "math")));
        Map<Identifier, NodeDescriptor> descs = new HashMap<>();
        descs.put(EXEC_NODE.id(), EXEC_NODE);
        descs.put(PURE_NODE.id(), PURE_NODE);
        return new PaletteState(search, descs::get,
                new fr.blueprint.client.config.PalettePrefs(), () -> Permission.ADMIN);
    }

    @Test
    void uneSousCategorieSAfficheSousSaParenteEtIndentee() {
        PaletteState p = arborescente();
        p.open(0, 0, 0, 0, null);

        int math = indexOfCategory(p, "math");
        int arithmetic = indexOfCategory(p, "math/arithmetic");
        assertTrue(math >= 0 && arithmetic > math, "la sous-catégorie suit sa parente");
        assertEquals(0, depthOf(p, math));
        assertEquals(1, depthOf(p, arithmetic), "indentée d'un cran");
    }

    /**
     * Le compte d'une parente inclut sa descendance : c'est le nombre de nœuds qu'on
     * s'attend à voir en la dépliant, pas celui de ses enfants directs.
     */
    @Test
    void leCompteDuneParenteInclutSaDescendance() {
        PaletteState p = arborescente();
        p.open(0, 0, 0, 0, null);
        assertEquals(2, countOf(p, "math"), "un nœud direct + un dans la sous-catégorie");
        assertEquals(1, countOf(p, "math/arithmetic"));
    }

    /**
     * Replier une parente replie tout ce qu'elle contient. Sinon « Opérations »
     * resterait orpheline à l'écran, sous une « Mathématiques » fermée.
     */
    @Test
    void replierUneParenteReplieSesSousCategories() {
        PaletteState p = arborescente();
        p.open(0, 0, 0, 0, null);
        assertEquals(3, p.results().size());

        p.toggleCategory("math");
        assertEquals(-1, indexOfCategory(p, "math/arithmetic"),
                "la sous-catégorie disparaît avec sa parente");
        assertEquals(1, p.results().size(), "et ses nœuds avec elle : reste flow");

        p.toggleCategory("math");
        assertEquals(3, p.results().size());
    }

    /** Une sous-catégorie se replie seule, sans emporter sa parente. */
    @Test
    void replierUneSousCategorieNeToucheQuElle() {
        PaletteState p = arborescente();
        p.open(0, 0, 0, 0, null);

        p.toggleCategory("math/arithmetic");
        assertTrue(indexOfCategory(p, "math") >= 0, "la parente reste");
        assertTrue(indexOfCategory(p, "math/arithmetic") >= 0, "la sous-catégorie aussi");
        assertEquals(2, p.results().size(), "seuls ses nœuds disparaissent");
    }

    /** Le découpage du chemin, là où tout le reste s'appuie. */
    @Test
    void leCheminDeCategorieSeDecoupe() {
        assertEquals("math", fr.blueprint.api.node.NodeCategory.parentOf("math/arithmetic"));
        assertEquals("arithmetic", fr.blueprint.api.node.NodeCategory.leafOf("math/arithmetic"));
        assertTrue(fr.blueprint.api.node.NodeCategory.isSub("math/arithmetic"));

        assertEquals("flow", fr.blueprint.api.node.NodeCategory.parentOf("flow"));
        assertEquals("flow", fr.blueprint.api.node.NodeCategory.leafOf("flow"));
        assertFalse(fr.blueprint.api.node.NodeCategory.isSub("flow"));
    }

    private static int depthOf(PaletteState p, int index) {
        return p.items().get(index) instanceof PaletteState.Item.Category(var n, var c, var e, int d)
                ? d : -1;
    }

    private static int countOf(PaletteState p, String name) {
        int index = indexOfCategory(p, name);
        return index < 0 ? -1
                : p.items().get(index) instanceof PaletteState.Item.Category(var n, int c, var e, var d)
                        ? c : -1;
    }

    private static int indexOfCategory(PaletteState p, String name) {
        for (int i = 0; i < p.items().size(); i++) {
            if (p.items().get(i) instanceof PaletteState.Item.Category(String n, var c, var e, var d)
                    && n.equals(name)) {
                return i;
            }
        }
        return -1;
    }

    /** Les sections et les catégories ne sont pas des entrées : −1, jamais un indice. */
    @Test
    void uneEnteteNestPasUneEntree() {
        PaletteState p = palette();
        p.open(0, 0, 0, 0, null);
        assertEquals(-1, p.entryIndexOf(0), "la première ligne est une catégorie");
        assertEquals(-1, p.entryIndexOf(-1));
        assertEquals(-1, p.entryIndexOf(9999));
        assertEquals(-1, p.itemRowOf(9999));
    }
}
