package fr.blueprint.client.editor;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.BlueprintFunction;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.FuncNodes;
import fr.blueprint.core.graph.FunctionOps;
import fr.blueprint.core.graph.Vec2d;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le panneau des fonctions (story 20.2, AC1, AC5, AC8).
 *
 * <p>Pur : ces tests n'ouvrent aucune fenêtre. Ce qu'ils protègent est ce qui se voit le
 * plus mal en jouant — une fonction créée sans ses bords, un renommage qui casse sans
 * prévenir, un compte d'appels qui oublie les corps.
 */
class FunctionPanelStateTest {

    private static final PluginLoader.LoadedRegistries LOADED = PluginLoader.load(List.of(), true);

    private Blueprint bp;
    private FunctionPanelState state;

    @BeforeEach
    void setUp() {
        bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "panel"));
        state = new FunctionPanelState(bp, op -> op.apply(bp, LOADED.nodes()).applied());
    }

    /**
     * <b>Une fonction neuve arrive avec ses deux bords.</b>
     *
     * <p>Sans {@code func/param}, aucun appel ne peut atteindre le corps ; sans
     * {@code func/result}, il ne rend rien. Un auteur devant une toile vide n'a aucun moyen
     * de deviner qu'il lui manque deux nœuds que la palette ne propose pas.
     */
    @Test
    void uneFonctionNeuveArriveAvecSesDeuxBords() {
        String name = state.create();

        assertNotNull(name);
        var function = bp.function(name);
        assertEquals(2, function.nodes().size());

        var types = function.nodes().values().stream().map(n -> n.typeId()).toList();
        assertTrue(types.contains(FuncNodes.PARAM), "l'entrée du corps manque");
        assertTrue(types.contains(FuncNodes.RESULT), "la sortie du corps manque");

        // Et les bords savent à quelle fonction ils appartiennent : sans ce littéral,
        // leur forme ne se résout pas et ils se dessineraient vides.
        function.nodes().values().forEach(node ->
                assertEquals(name, FuncNodes.boundName(node),
                        "un bord sans nom de fonction n'a pas de forme"));
    }

    /**
     * <b>Créer une fonction, c'est UN pas d'annulation.</b>
     *
     * <p>Trois opérations partent — la fonction, puis ses deux bords, chacun avec son
     * littéral. Découvrir qu'il faut appuyer cinq fois sur {@code Ctrl+Z} pour défaire ce
     * qu'on vient de faire en un clic serait une surprise, et laisserait entre-temps des
     * états intermédiaires — une fonction sans ses bords — qu'aucun geste ne sait produire.
     */
    @Test
    void creerUneFonctionEstUnSeulPasDAnnulation() {
        var history = new fr.blueprint.client.editor.history.UndoStack();
        var avecPile = new FunctionPanelState(bp, op -> {
            var result = op.apply(bp, LOADED.nodes());
            if (result.applied() && result.inverse() != null) {
                history.record(result.inverse());
            }
            return result.applied();
        }, history::beginGesture, history::endGesture, () -> null);

        String name = avecPile.create();
        assertNotNull(name);
        assertEquals(2, bp.function(name).nodes().size());

        assertTrue(history.undo(bp, LOADED.nodes()));

        assertNull(bp.function(name), "un seul Ctrl+Z doit tout défaire");
        assertFalse(history.canUndo(),
                "et il ne doit rien rester en attente : les bords partent AVEC la fonction");
    }

    /** Les noms ne se marchent pas dessus, et le panneau les rend triés. */
    @Test
    void lesNomsSEnchainentEtSeTrient() {
        state.create();
        state.create();

        assertEquals(List.of("fonction1", "fonction2"),
                state.rows().stream().map(BlueprintFunction::name).toList());
    }

    /**
     * <b>Renommer prévient avant de casser.</b>
     *
     * <p>Le premier appel arme l'avertissement, le second applique — la même mécanique que
     * le retypage d'une variable. Renommer ne réécrit pas les littéraux des appels : c'est
     * le précédent de {@code RenameVariable}, et le corriger en douce serait une mutation
     * cachée.
     */
    @Test
    void renommerPrevientAvantDeCasser() {
        String name = state.create();
        poserUnAppel(name);

        state.openRename(name);
        state.backspace();
        state.type("X");

        assertFalse(state.commitRename(), "le premier appel doit avertir, pas appliquer");
        assertEquals(1, state.pendingBreaks(), "l'avertissement doit compter les appels");
        assertNotNull(bp.function(name), "rien ne doit avoir bougé");

        assertTrue(state.commitRename(), "le second appel applique");
        assertNull(bp.function(name));
    }

    /** Sans appel à casser, un renommage passe du premier coup. */
    @Test
    void sansAppelLeRenommagePasseDuPremierCoup() {
        String name = state.create();
        state.openRename(name);
        state.backspace();
        state.type("X");

        assertTrue(state.commitRename(), "rien à casser, rien à demander");
    }

    /**
     * <b>Le compte d'appels regarde aussi les corps.</b>
     *
     * <p>Une fonction peut en appeler une autre. Ne compter que le graphe principal
     * annoncerait « aucun appel cassé » à un auteur dont le renommage va casser ceux d'un
     * corps — un avertissement faux est pire qu'aucun avertissement.
     */
    @Test
    void leCompteDAppelsRegardeAussiLesCorps() {
        String cible = state.create();
        String appelante = state.create();

        UUID call = UUID.randomUUID();
        assertTrue(new FunctionOps.AddNodeIn(appelante, call, FuncNodes.CALL, new Vec2d(0, 0))
                .apply(bp, LOADED.nodes()).applied());
        assertTrue(new FunctionOps.SetLiteralIn(appelante, call, FuncNodes.FUNCTION_PIN,
                LiteralValue.of(PinTypes.STRING, cible)).apply(bp, LOADED.nodes()).applied());

        assertEquals(1, state.callsTo(cible),
                "un appel posé dans un CORPS compte autant qu'un appel du graphe");
    }

    /** Les bords ne comptent pas comme des appels, bien qu'ils portent le même littéral. */
    @Test
    void lesBordsNeComptentPasCommeDesAppels() {
        String name = state.create();

        assertEquals(0, state.callsTo(name),
                "func/param et func/result nomment leur fonction sans l'appeler — les "
                        + "compter ferait avertir d'un renommage qui ne casse rien");
    }

    /**
     * <b>L'onglet Fonctions ouvre une fonction, jamais le graphe.</b>
     *
     * <p>Y arriver et voir le graphe qu'on vient de quitter est le pire des affichages :
     * rien ne distingue à l'œil « je n'ai pas encore ouvert de corps » de « ce corps
     * contient déjà tout ça », et les nœuds posés tombent dans le graphe sous une étiquette
     * qui annonce l'inverse.
     */
    @Test
    void lOngletOuvreUneFonctionJamaisLeGraphe() {
        assertNull(state.bodyToOpen(),
                "sans aucune fonction, il n'y a rien à ouvrir — et surtout pas le graphe");

        state.create();
        String second = state.create();
        assertEquals(second, state.bodyToOpen(),
                "la création sélectionne : c'est celle qu'on vient de faire qu'on veut voir");

        state.select(null);
        assertEquals("fonction1", state.bodyToOpen(),
                "sans sélection, la première de la liste");

        state.select(second);
        assertEquals(second, state.bodyToOpen());

        state.delete(second);
        assertEquals("fonction1", state.bodyToOpen(),
                "une sélection qui pointe vers une fonction supprimée ne doit pas laisser "
                        + "l'onglet sans rien à montrer");
    }

    /** Supprimer une fonction la retire, et désélectionne. */
    @Test
    void supprimerRetireEtDeselectionne() {
        String name = state.create();
        state.select(name);

        assertTrue(state.delete(name));
        assertNull(bp.function(name));
        assertNull(state.selected());
    }

    /**
     * <b>Une ligne montre la signature, pas le seul nom.</b>
     *
     * <p>Une liste de noms nus obligerait à ouvrir chaque corps pour savoir lequel prend
     * une entité — ce qui est précisément la question qu'on se pose en cherchant la
     * fonction à appeler.
     */
    @Test
    void uneLigneMontreLaSignature() {
        var soigner = BlueprintFunction.of("soigner",
                List.of(new BlueprintFunction.Param("cible", PinTypes.ENTITY),
                        new BlueprintFunction.Param("points", PinTypes.DOUBLE)),
                List.of(new BlueprintFunction.Param("soigne", PinTypes.BOOL)));

        assertEquals("soigner(cible, points) → soigne", FunctionPanelLayout.label(soigner));
    }

    /** Sans sortie, pas de flèche qui pendrait dans le vide. */
    @Test
    void uneFonctionSansSortieNAffichePasDeFleche() {
        assertEquals("agir()", FunctionPanelLayout.label(
                BlueprintFunction.of("agir", List.of(), List.of())));
    }

    /** Les trois actions d'une ligne se cliquent là où elles se dessinent. */
    @Test
    void lesActionsSeCliquentLaOuEllesSeDessinent() {
        int w = FunctionPanelLayout.WIDTH;
        assertEquals(FunctionPanelLayout.RowAction.OPEN, FunctionPanelLayout.actionAt(w - 32, true));
        assertEquals(FunctionPanelLayout.RowAction.RENAME, FunctionPanelLayout.actionAt(w - 22, true));
        assertEquals(FunctionPanelLayout.RowAction.DELETE, FunctionPanelLayout.actionAt(w - 12, true));
        assertNull(FunctionPanelLayout.actionAt(4, true),
                "le début de la ligne porte le nom, pas une action");
        assertNull(FunctionPanelLayout.actionAt(w - 12, false),
                "une ligne non sélectionnée n'offre aucune action : ses icônes ne sont pas "
                        + "dessinées, et un clic dessus viserait du vide");
    }

    /**
     * Le panneau s'arrête après sa dernière ligne.
     *
     * <p>Un blueprint sans fonction peignait sinon une colonne noire sur toute la hauteur
     * de l'écran pour deux mots — elle ampute le canevas d'autant, sans rien montrer.
     * C'est la leçon du panneau des variables, reprise ici plutôt que réapprise.
     */
    @Test
    void lePanneauSArreteApresSaDerniereLigne() {
        state.create();
        int uneLigne = FunctionPanelLayout.bottom(state, 300);
        state.create();
        state.create();
        int troisLignes = FunctionPanelLayout.bottom(state, 300);

        assertEquals(2 * FunctionPanelLayout.ROW_HEIGHT, troisLignes - uneLigne,
                "chaque ligne de plus rallonge le panneau d'exactement sa hauteur");
        assertTrue(troisLignes < 300 - DiagnosticsPanel.BAR_HEIGHT,
                "le panneau ne descend pas jusqu'à la barre du bas : il amputerait le "
                        + "canevas d'une colonne noire pour trois mots");
    }

    /** Un avertissement en attente prend sa place, sinon il déborderait du cadre. */
    @Test
    void lAvertissementSeReserveSaPlace() {
        String name = state.create();
        poserUnAppel(name);
        int sansAvertissement = FunctionPanelLayout.bottom(state, 300);

        state.openRename(name);
        state.backspace();
        state.type("X");
        state.commitRename();   // arme l'avertissement

        assertTrue(FunctionPanelLayout.bottom(state, 300) > sansAvertissement,
                "le panneau doit s'agrandir pour porter l'avertissement");
    }

    /** Renoncer à un renommage éteint aussi l'avertissement. */
    @Test
    void renoncerEteintLAvertissement() {
        String name = state.create();
        poserUnAppel(name);
        state.openRename(name);
        state.backspace();
        state.type("X");
        state.commitRename();
        assertTrue(state.pendingBreaks() > 0);

        state.cancelRename();

        assertEquals(0, state.pendingBreaks(),
                "un avertissement qui survit à l'abandon ferait appliquer au clic suivant "
                        + "un renommage auquel on avait renoncé");
        assertFalse(state.isRenaming());
    }

    /** Les lignes tombent l'une sous l'autre, et celle qu'on renomme montre la frappe. */
    @Test
    void laDispositionPlaceLesLignesEtMontreLaFrappe() {
        state.create();
        String second = state.create();
        state.select(second);

        var rows = FunctionPanelLayout.rows(state, 300, 0);
        assertEquals(2, rows.size());
        assertEquals(FunctionPanelLayout.ROW_HEIGHT, rows.get(1).y() - rows.get(0).y(),
                "deux lignes consécutives sont séparées d'une hauteur de ligne");
        assertTrue(rows.get(1).selected(), "la ligne sélectionnée doit se savoir telle");

        state.openRename(second);
        state.type("X");
        assertTrue(FunctionPanelLayout.rows(state, 300, 0).get(1).text().endsWith("X_"),
                "sans la frappe en cours, on taperait à l'aveugle");
    }

    /**
     * <b>On clique la ligne qu'on voit.</b>
     *
     * <p>Rendu et hit-test lisent la même arithmétique. Deux calculs séparés laisseraient
     * une bande où le clic tombe une ligne à côté — le défaut le plus pénible d'une liste,
     * parce qu'on supprime alors la fonction voisine de celle qu'on visait.
     */
    @Test
    void onCliqueLaLigneQuOnVoit() {
        state.create();
        state.create();

        var rows = FunctionPanelLayout.rows(state, 300, 0);
        for (int i = 0; i < rows.size(); i++) {
            assertEquals(i, FunctionPanelLayout.rowAt(state, 10, rows.get(i).y() + 1, 0, 300),
                    "la ligne " + i + " ne se clique pas là où elle se dessine");
        }
        assertEquals(-1, FunctionPanelLayout.rowAt(state, 10,
                        rows.get(rows.size() - 1).y() + FunctionPanelLayout.ROW_HEIGHT + 1, 0, 300),
                "sous la dernière ligne, aucune ligne");
        assertEquals(-1, FunctionPanelLayout.rowAt(state, FunctionPanelLayout.WIDTH + 5, rows.get(0).y() + 1,
                0, 300), "à droite du panneau, c'est le canevas");
    }

    /**
     * <b>Chaque clic demande ce qu'il a l'air de demander.</b>
     *
     * <p>Le double-clic ouvre le corps là où le panneau des variables renomme : ouvrir est
     * le geste qu'on fait vingt fois quand renommer arrive une fois. Les trois actions
     * n'existent que sur la ligne sélectionnée — cliquer à leur emplacement sur une autre
     * ligne la sélectionne, il ne la supprime pas.
     */
    @Test
    void chaqueClicDemandeCeQuIlALAirDeDemander() {
        String premier = state.create();
        String second = state.create();
        state.select(premier);
        int w = FunctionPanelLayout.WIDTH;
        var rows = FunctionPanelLayout.rows(state, 300, 0);
        double yPremier = rows.get(0).y() + 1;
        double ySecond = rows.get(1).y() + 1;

        assertEquals(new FunctionPanelLayout.Click(FunctionPanelLayout.Hit.CREATE, null),
                FunctionPanelLayout.clickAt(state, w - 8, ToolbarWidget.HEIGHT + 3, 0, 300, false));
        assertEquals(new FunctionPanelLayout.Click(FunctionPanelLayout.Hit.OPEN, premier),
                FunctionPanelLayout.clickAt(state, 10, yPremier, 0, 300, true),
                "le double-clic ouvre le corps");
        assertEquals(new FunctionPanelLayout.Click(FunctionPanelLayout.Hit.DELETE, premier),
                FunctionPanelLayout.clickAt(state, w - 12, yPremier, 0, 300, false));
        assertEquals(new FunctionPanelLayout.Click(FunctionPanelLayout.Hit.SELECT, second),
                FunctionPanelLayout.clickAt(state, w - 12, ySecond, 0, 300, false),
                "au même endroit sur une ligne NON sélectionnée, aucune action : les icônes "
                        + "n'y sont pas dessinées, et supprimer au premier clic serait brutal");
        assertEquals(new FunctionPanelLayout.Click(FunctionPanelLayout.Hit.DESELECT, null),
                FunctionPanelLayout.clickAt(state, 10,
                        rows.get(1).y() + FunctionPanelLayout.ROW_HEIGHT + 2, 0, 300, false));
    }

    /** Le « + » de l'en-tête, et rien d'autre dans l'en-tête. */
    @Test
    void lePlusEstDansLEnTeteADroite() {
        assertTrue(FunctionPanelLayout.plusAt(FunctionPanelLayout.WIDTH - 8, ToolbarWidget.HEIGHT + 3));
        assertFalse(FunctionPanelLayout.plusAt(4, ToolbarWidget.HEIGHT + 3),
                "le titre n'est pas un bouton");
        assertFalse(FunctionPanelLayout.plusAt(FunctionPanelLayout.WIDTH - 8,
                        ToolbarWidget.HEIGHT + FunctionPanelLayout.HEADER_HEIGHT + 1),
                "sous l'en-tête commence la première ligne : y créer une fonction au lieu "
                        + "de la sélectionner serait une surprise");
    }

    /** Le corps ouvert se marque dans la liste, même quand la sélection est ailleurs. */
    @Test
    void leCorpsOuvertSeMarqueDansLaListe() {
        String premier = state.create();
        String second = state.create();
        var ouvert = new java.util.concurrent.atomic.AtomicReference<String>(premier);
        var vue = new FunctionPanelState(bp, op -> op.apply(bp, LOADED.nodes()).applied(),
                () -> { }, () -> { }, ouvert::get);
        vue.select(second);

        var rows = FunctionPanelLayout.rows(vue, 300, 0);
        assertTrue(rows.get(0).open(), "le corps ouvert doit se voir");
        assertFalse(rows.get(0).selected());
        assertTrue(rows.get(1).selected(), "et la sélection reste indépendante");
        assertFalse(rows.get(1).open(),
                "sans quoi rien ne dirait quel graphe le canevas est en train de montrer");
    }

    /** Un panneau vide ne dispose rien — c'est le message d'accueil qui prend la place. */
    @Test
    void unPanneauVideNeDisposeRien() {
        assertTrue(FunctionPanelLayout.rows(state, 300, 0).isEmpty());
    }

    private void poserUnAppel(String function) {
        UUID call = UUID.randomUUID();
        assertTrue(new EditOperation.AddNode(call, FuncNodes.CALL, new Vec2d(0, 0))
                .apply(bp, LOADED.nodes()).applied());
        assertTrue(new EditOperation.SetLiteral(call, FuncNodes.FUNCTION_PIN,
                LiteralValue.of(PinTypes.STRING, function)).apply(bp, LOADED.nodes()).applied());
    }
}
