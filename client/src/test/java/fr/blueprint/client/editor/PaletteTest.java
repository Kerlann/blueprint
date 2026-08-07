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

    /** Assez de recherches pour dépasser la granularité de l'horloge processeur. */
    private static final int SEARCHES = 200;

    @Test
    void performance2000TypesSous5Ms() {
        List<NodeSearch.Entry> entries = new ArrayList<>();
        for (int i = 0; i < 2_000; i++) {
            entries.add(entry("n" + i, "Nœud numéro " + i + " alpha beta", "cat" + (i % 12)));
        }
        NodeSearch search = new NodeSearch(entries);
        search.search("alpha", e -> true, 8); // échauffement

        // AGRÉGÉE sur deux cents recherches, en temps PROCESSEUR DU FIL.
        //
        // Ce banc a été la première cause de constructions rouges du projet : douze, sans
        // qu'aucun code n'ait changé. Une mesure murale d'UNE recherche de 0,4 ms voit un
        // à-coup de l'ordonnanceur comme une régression, et le meilleur de cinq n'y
        // suffisait pas — un meilleur de cinq mesures murales reste une mesure de la
        // machine.
        //
        // Deux corrections, et il faut les deux :
        //
        // — le temps processeur du fil ne compte que les instants où ce fil a réellement
        //   tourné, si bien qu'une préemption ne s'y voit pas ;
        // — l'AGRÉGATION est indispensable avec lui. Cette horloge a une granularité
        //   grossière (≈ 15 ms sur Windows) : mesurer une seule recherche de 0,4 ms rend
        //   ZÉRO, et le test passerait alors à vide — sans rien vérifier, ce qui est pire
        //   que rougir. Deux cents recherches font ≈ 90 ms, très au-dessus.
        //
        // Le seuil de 5 ms, lui, ne bouge pas : c'est l'AC4, une exigence du produit. Le
        // relever aurait affaibli l'exigence pour un problème qui n'est pas dedans.
        var threads = java.lang.management.ManagementFactory.getThreadMXBean();
        boolean cpuTime = threads.isCurrentThreadCpuTimeSupported();
        long start = cpuTime ? threads.getCurrentThreadCpuTime() : System.nanoTime();
        for (int i = 0; i < SEARCHES; i++) {
            assertNotNull(search.search("beta 42", e -> true, 8));
        }
        long end = cpuTime ? threads.getCurrentThreadCpuTime() : System.nanoTime();
        double perSearch = (end - start) / (double) SEARCHES / 1e6;

        assertTrue(perSearch > 0, "mesure nulle : l'horloge ne résout pas "
                + SEARCHES + " recherches — en agréger davantage");
        assertTrue(perSearch < 5, "recherche en " + perSearch + " ms (AC4 : ≤ 5 ms)");
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
                () -> Permission.GAMEPLAY);
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

    /**
     * La liste <b>commence</b> par ses catégories, dans l'ordre de travail.
     *
     * <p>Elle commençait par « Favoris » puis « Récents », retirés en 12.2 : deux
     * sections en tête qui repoussaient l'index vers le bas et faisaient apparaître le
     * même nœud à deux endroits.
     */
    @Test
    void sansRequeteLaListeCommenceParSesCategories() {
        PaletteState p = palette();
        p.open(0, 0, 0, 0, null);
        assertTrue(p.items().get(0) instanceof PaletteState.Item.Category(String n, int c, boolean x, int d)
                && n.equals("flow"),
                "la première ligne est une catégorie, pas une section : " + p.items());
        assertEquals(2, p.results().size());
    }

    /**
     * <b>Le test qui compte.</b> Un nœud n'apparaît qu'<b>une fois</b>.
     *
     * <p>C'était le prix des favoris : le même nœud figurait dans sa section et dans sa
     * catégorie, et il a fallu indexer les lignes par POSITION pour que cliquer la
     * seconde ne surligne pas la première. Le doublon disparu, cette classe de confusion
     * disparaît avec lui — et ce test garde qu'elle ne revienne pas.
     */
    @Test
    void chaqueNoeudNApparaitQuUneFois() {
        PaletteState p = palette();
        p.open(0, 0, 0, 0, null);

        Map<Identifier, Integer> counts = new HashMap<>();
        for (PaletteState.Item item : p.items()) {
            if (item instanceof PaletteState.Item.EntryItem(var e, var blocked)) {
                counts.merge(e.id(), 1, Integer::sum);
            }
        }
        assertTrue(counts.values().stream().allMatch(n -> n == 1),
                "un nœud à deux endroits : " + counts);
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
                descs::get, () -> Permission.GAMEPLAY);
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
     * La correspondance ligne ↔ entrée reste juste dans les deux sens.
     *
     * <p>Elle avait été construite par POSITION parce qu'un favori apparaissait deux
     * fois et que cliquer la seconde ligne surlignait la première. Les favoris ont
     * disparu, la contrainte non : l'index reste ce sur quoi la sélection et le
     * défilement s'appuient, et une erreur d'un cran s'y voit à peine.
     */
    @Test
    void laCorrespondanceLigneEntreeTientDansLesDeuxSens() {
        PaletteState p = palette();
        p.open(0, 0, 0, 0, null);

        List<Integer> rows = new java.util.ArrayList<>();
        for (int i = 0; i < p.items().size(); i++) {
            if (p.items().get(i) instanceof PaletteState.Item.EntryItem) {
                rows.add(i);
            }
        }
        assertEquals(2, rows.size());

        for (int row : rows) {
            int entry = p.entryIndexOf(row);
            assertTrue(entry >= 0, "toute ligne d'entrée a son indice");
            assertEquals(row, p.itemRowOf(entry), "et le chemin inverse y revient");
            p.select(entry);
            assertEquals(entry, p.selectedIndex());
        }
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
                "world", "debug", "flow", PaletteState.VARIABLES, "event", "math",
                PaletteState.FUNCTIONS));
        ordre.sort(PaletteState.CATEGORY_ORDER);

        assertEquals(List.of("event", PaletteState.VARIABLES, PaletteState.FUNCTIONS, "flow",
                "debug", "math", "world"), ordre,
                "les membres du blueprint — variables puis fonctions — viennent juste après "
                        + "l'événement : ce sont eux qu'on a écrits et qu'on cherche");
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
                descriptorsForTest(), () -> Permission.ADMIN,
                () -> List.of(get, set));
        p.open(0, 0, 0, 0, null);

        assertTrue(p.results().contains(get));
        assertTrue(p.results().contains(set));
        assertTrue(get.isVariable(), "et l'insertion saura QUELLE variable poser");
        assertEquals("score", get.bound());

        // La catégorie Variables est placée avant les catégories de nœuds.
        int variables = indexOfCategory(p, PaletteState.VARIABLES);
        int flow = indexOfCategory(p, "flow");
        assertTrue(variables >= 0 && flow >= 0);
        assertTrue(variables < flow, "Variables avant Contrôle du flux");
    }

    /**
     * <b>Les fonctions du blueprint s'appellent depuis le menu d'ajout</b> (story 20.2,
     * AC6).
     *
     * <p>Elles arrivent dans <b>leur</b> catégorie et non parmi les variables : « Appeler
     * carre » n'est pas une variable, et les mélanger rendrait la liste illisible dès qu'un
     * blueprint a les deux.
     */
    @Test
    void lesFonctionsDuBlueprintSAppellentDepuisLeMenu() {
        NodeSearch.Entry appel = new NodeSearch.Entry(
                fr.blueprint.core.graph.FuncNodes.CALL, "Appeler carre", "carre(n) → r",
                PaletteState.FUNCTIONS, "carre");
        PaletteState p = new PaletteState(
                new NodeSearch(List.of(
                        new NodeSearch.Entry(EXEC_NODE.id(), "Exec node", "d", "flow"))),
                descriptorsForTest(), () -> Permission.ADMIN, () -> List.of(appel));
        p.open(0, 0, 0, 0, null);

        assertTrue(p.results().contains(appel));
        assertTrue(appel.isCall(), "l'insertion doit poser le littéral avec le nœud");
        assertFalse(appel.isVariable(),
                "un appel n'est pas une variable : le dévier vers insertVariableNode "
                        + "poserait un var/get nommé « carre »");
        assertEquals("carre", appel.bound());

        int fonctions = indexOfCategory(p, PaletteState.FUNCTIONS);
        assertTrue(fonctions >= 0, "les fonctions ont leur catégorie");
        assertEquals(-1, indexOfCategory(p, PaletteState.VARIABLES),
                "et elles ne sont pas versées dans celle des variables");
    }

    /**
     * <b>Un membre du blueprint se cherche comme le reste</b> (AC6).
     *
     * <p>La recherche ne traversait que le registre : dès la première lettre tapée, les
     * variables et les fonctions du blueprint disparaissaient de la liste. C'est pourtant le
     * geste que fait quelqu'un qui sait exactement quoi poser.
     */
    @Test
    void unMembreDuBlueprintSeChercheCommeLeReste() {
        NodeSearch.Entry appel = new NodeSearch.Entry(
                fr.blueprint.core.graph.FuncNodes.CALL, "Appeler carre", "carre(n) → r",
                PaletteState.FUNCTIONS, "carre");
        PaletteState p = new PaletteState(
                new NodeSearch(List.of(
                        new NodeSearch.Entry(EXEC_NODE.id(), "Exec node", "d", "flow"))),
                descriptorsForTest(), () -> Permission.ADMIN, () -> List.of(appel));
        p.open(0, 0, 0, 0, null);
        p.type("carre");

        assertTrue(p.results().contains(appel),
                "une fonction introuvable dès qu'on tape son nom est une fonction "
                        + "introuvable");
        assertEquals(appel, p.results().get(0),
                "et elle passe devant : c'est elle qu'on vient d'écrire");
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
        assertNull(p.results().get(0).bound());
    }

    // ---------------------------------------------- sous-catégories (5.14, UE5)

    /** Palette à deux catégories dont une subdivisée : math, math/arithmetic, flow. */
    /**
     * Une palette assez <b>grande</b> pour que le repli ait un sens : au-delà de
     * {@link PaletteState#VISIBLE_ROWS}, la palette cesse de tout déplier d'office.
     *
     * <p>Les jeux d'essai à deux ou trois nœuds tiennent tous à l'écran, et la palette
     * les montre donc entièrement — ce qui est le bon comportement, mais rend ces
     * fixtures aveugles au repli.
     */
    private static PaletteState grande() {
        return grande(2);
    }

    /**
     * Idem, réparti sur {@code categories} catégories.
     *
     * <p>Le nombre importe : avec deux catégories, l'index replié fait deux lignes et
     * <b>rien ne défile</b> — un test de défilement y passerait sans rien prouver. Avec
     * plus de catégories que de lignes visibles, l'index lui-même déborde, ce qui est le
     * cas réel (le produit en a une trentaine).
     */
    private static PaletteState grande(int categories) {
        List<NodeSearch.Entry> many = new ArrayList<>();
        Map<Identifier, NodeDescriptor> descs = new HashMap<>();
        for (int i = 0; i < PaletteState.VISIBLE_ROWS + 6; i++) {
            Identifier id = Identifier.fromNamespaceAndPath("blueprint", "n" + i);
            String category = categories == 2
                    ? (i % 2 == 0 ? "flow" : "math")
                    : "cat" + (i % categories);
            many.add(new NodeSearch.Entry(id, "Nœud " + i, "d", category));
            descs.put(id, new NodeDescriptor(id, category, "t." + i, "d." + i,
                    List.of(), List.of(pin("exec_out", PinKind.EXEC)),
                    false, Permission.SAFE, 1, true));
        }
        return new PaletteState(new NodeSearch(many), descs::get,
                () -> Permission.ADMIN);
    }

    /**
     * <b>Le test qui compte.</b> Une grande palette s'ouvre <b>repliée</b> : on voit
     * l'index des catégories, pas les nœuds.
     *
     * <p>C'était l'inverse, et « je n'arrive pas à me repérer » décrivait exactement le
     * résultat — deux cents lignes déversées d'un coup.
     */
    @Test
    void unePaletteFournieSOuvreRepliee() {
        PaletteState p = grande();
        p.open(0, 0, 0, 0, null);

        assertEquals(0, p.results().size(), "aucun nœud visible tant qu'on n'a pas déplié");
        assertTrue(p.items().stream().allMatch(i -> i instanceof PaletteState.Item.Category),
                "seules des catégories : " + p.items());
        assertTrue(p.items().size() <= PaletteState.VISIBLE_ROWS,
                "l'index doit tenir à l'écran, sinon il n'est pas un index");

        p.toggleCategory("flow");
        assertTrue(p.results().size() > 0, "déplier montre le contenu");
    }

    /**
     * <b>Le test qui compte.</b> Une palette qui tient à l'écran s'ouvre <b>dépliée</b>.
     *
     * <p>Faire cliquer sur trois en-têtes pour révéler quatre nœuds qu'on aurait pu
     * montrer d'emblée serait un rangement pour le rangement. C'est le cas courant en
     * tirant un fil, où le filtre ne laisse qu'une poignée de candidats — et c'est
     * précisément là qu'on veut les voir tout de suite.
     */
    @Test
    void unePaletteCourteSOuvreDepliee() {
        PaletteState p = palette();
        p.open(0, 0, 0, 0, null);
        assertEquals(2, p.results().size(), "deux nœuds tiennent à l'écran : on les montre");
    }

    /**
     * <b>Le test qui compte.</b> L'auto-dépliage est un <b>défaut</b>, pas un verrou.
     *
     * <p>Première version de la règle : « si tout tient à l'écran, tout est déplié ».
     * Elle écrasait le choix de l'auteur — replier une petite palette redevenait dépliée
     * à l'instant même, et le triangle ne faisait rien. Une aide qui reprend la main à
     * celui qu'elle aide n'est pas une aide.
     */
    @Test
    void replierResteFaisableSurUnePaletteCourte() {
        PaletteState p = palette();
        p.open(0, 0, 0, 0, null);
        assertEquals(2, p.results().size(), "déplié d'office : tout tient à l'écran");

        p.toggleCategory("flow");
        assertEquals(1, p.results().size(), "et l'auteur peut quand même replier");
        p.toggleCategory("flow");
        assertEquals(2, p.results().size());
    }

    /**
     * <b>Le test qui compte.</b> La case « Contextuel » décochée rend la palette entière.
     *
     * <p>Sans elle, un fil tiré filtrait sans recours : l'auteur qui ne trouvait pas un
     * nœud dans la liste réduite en concluait qu'il n'existait pas. Le filtre reste le
     * bon défaut ; c'est l'absence d'issue qui ne l'était pas.
     */
    @Test
    void laCaseContextuelDecocheeMontreTout() {
        PaletteState p = palette();
        CanvasController.PinRef from = new CanvasController.PinRef(
                UUID.randomUUID(), "exec_out", PinKind.EXEC, PinTypes.EXEC, true, 0);
        p.open(0, 0, 0, 0, from);
        assertTrue(p.contextSensitive(), "cochée par défaut : le filtre est le bon défaut");
        assertEquals(1, p.results().size(), "seul le nœud compatible");

        p.toggleContextSensitive();
        assertFalse(p.contextSensitive());
        assertEquals(2, p.results().size(), "décochée, la palette entière");

        p.toggleContextSensitive();
        assertEquals(1, p.results().size(), "et le filtre revient");
    }

    /**
     * Sans fil tiré, la case ne décide de rien — et le rendu ne l'affiche donc pas.
     * Basculer ne doit rien changer, plutôt que de changer quelque chose d'invisible.
     */
    @Test
    void sansFilTireLaCaseNeChangeRien() {
        PaletteState p = palette();
        p.open(0, 0, 0, 0, null);
        int before = p.results().size();
        p.toggleContextSensitive();
        assertEquals(before, p.results().size());
    }

    /**
     * La colonne de catégorie n'est répétée que là où elle apprend quelque chose : en
     * recherche, où les résultats viennent de partout. Sous l'en-tête qui les nomme, elle
     * volait un tiers de la largeur pour redire la ligne du dessus.
     */
    @Test
    void laColonneDeCategorieNApparaitQuEnRecherche() {
        PaletteState p = palette();
        p.open(0, 0, 0, 0, null);
        assertFalse(p.showsCategoryColumn(), "sous l'arbre, l'en-tête suffit");

        p.type("node");
        assertTrue(p.showsCategoryColumn(), "en recherche, elle situe le résultat");
    }

    /**
     * <b>Le test qui compte.</b> Déplier une catégorie ne renvoie pas la vue en haut.
     *
     * <p>Sur un index d'une trentaine de catégories, cela voulait dire que déplier la
     * vingtième la faisait disparaître de l'écran <b>au moment même où on l'ouvrait</b> :
     * il fallait redescendre pour retrouver ce qu'on venait de demander à voir.
     *
     * <p>La position se garde <i>exactement</i> : la ligne qu'on vient de cliquer est
     * forcément visible, et un dépli n'insère des lignes qu'au-dessous d'elle.
     */
    @Test
    void deplierUneCategorieGardeLaPosition() {
        PaletteState p = grande(PaletteState.VISIBLE_ROWS + 4);
        p.open(0, 0, 0, 0, null);
        p.scrollBy(3);
        int avant = p.scroll();
        assertTrue(avant > 0, "il faut avoir défilé pour que le test prouve quelque chose");

        p.toggleCategory("cat0");

        assertEquals(avant, p.scroll(), "la vue est repartie du haut");
    }

    /**
     * Replier garde aussi la position — mais la liste raccourcit, donc le défilement se
     * borne à ce qui reste. Sans ce recadrage, la vue montrerait du vide.
     */
    @Test
    void replierGardeLaPositionEtLaBorne() {
        PaletteState p = grande(PaletteState.VISIBLE_ROWS + 4);
        p.open(0, 0, 0, 0, null);
        p.toggleCategory("cat0");
        p.scrollBy(100);
        assertTrue(p.scroll() > 0);

        p.toggleCategory("cat0");

        assertTrue(p.scroll() <= Math.max(0, p.items().size() - PaletteState.VISIBLE_ROWS),
                "le défilement doit se borner à ce qui reste, sinon la vue montre du vide");
    }

    /**
     * La sélection est retrouvée par son <b>entrée</b>, pas par son indice : les indices
     * se décalent de tout ce qu'on vient d'ouvrir, et suivre l'indice ferait sauter le
     * surlignage sur un nœud voisin.
     */
    @Test
    void deplierGardeLeNoeudSelectionne() {
        PaletteState p = grande(PaletteState.VISIBLE_ROWS + 4);
        p.open(0, 0, 0, 0, null);
        p.toggleCategory("cat5");
        var choisi = p.results().get(0);
        p.select(0);

        p.toggleCategory("cat1");   // insère des lignes AVANT celles de cat5

        assertEquals(choisi, p.selectedEntry(), "le surlignage a changé de nœud");
    }

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
                () -> Permission.ADMIN);
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
