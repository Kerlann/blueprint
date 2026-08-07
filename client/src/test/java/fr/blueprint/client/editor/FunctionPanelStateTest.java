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

        assertEquals("soigner(cible, points) → soigne", FunctionPanel.label(soigner));
    }

    /** Sans sortie, pas de flèche qui pendrait dans le vide. */
    @Test
    void uneFonctionSansSortieNAffichePasDeFleche() {
        assertEquals("agir()", FunctionPanel.label(
                BlueprintFunction.of("agir", List.of(), List.of())));
    }

    /** Les trois actions d'une ligne se cliquent là où elles se dessinent. */
    @Test
    void lesActionsSeCliquentLaOuEllesSeDessinent() {
        int w = FunctionPanel.WIDTH;
        assertEquals(FunctionPanel.RowAction.OPEN, FunctionPanel.actionAt(w - 34, 0, true));
        assertEquals(FunctionPanel.RowAction.RENAME, FunctionPanel.actionAt(w - 24, 0, true));
        assertEquals(FunctionPanel.RowAction.DELETE, FunctionPanel.actionAt(w - 14, 0, true));
        assertNull(FunctionPanel.actionAt(4, 0, true),
                "le début de la ligne porte le nom, pas une action");
        assertNull(FunctionPanel.actionAt(w - 14, 0, false),
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
        int vide = FunctionPanel.bottom(state, 300);
        state.create();
        int uneLigne = FunctionPanel.bottom(state, 300);

        assertTrue(uneLigne > vide, "une ligne de plus, un panneau plus haut");
        assertTrue(uneLigne < 300, "le panneau ne descend pas jusqu'en bas de l'écran");
    }

    /** Un avertissement en attente prend sa place, sinon il déborderait du cadre. */
    @Test
    void lAvertissementSeReserveSaPlace() {
        String name = state.create();
        poserUnAppel(name);
        int sansAvertissement = FunctionPanel.bottom(state, 300);

        state.openRename(name);
        state.backspace();
        state.type("X");
        state.commitRename();   // arme l'avertissement

        assertTrue(FunctionPanel.bottom(state, 300) > sansAvertissement,
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

        var rows = FunctionPanel.layout(state, 300, 0);
        assertEquals(2, rows.size());
        assertEquals(FunctionPanel.ROW_HEIGHT, rows.get(1).y() - rows.get(0).y(),
                "deux lignes consécutives sont séparées d'une hauteur de ligne");
        assertTrue(rows.get(1).selected(), "la ligne sélectionnée doit se savoir telle");

        state.openRename(second);
        state.type("X");
        assertTrue(FunctionPanel.layout(state, 300, 0).get(1).text().endsWith("X_"),
                "sans la frappe en cours, on taperait à l'aveugle");
    }

    /** Un panneau vide ne dispose rien — c'est le message d'accueil qui prend la place. */
    @Test
    void unPanneauVideNeDisposeRien() {
        assertTrue(FunctionPanel.layout(state, 300, 0).isEmpty());
    }

    private void poserUnAppel(String function) {
        UUID call = UUID.randomUUID();
        assertTrue(new EditOperation.AddNode(call, FuncNodes.CALL, new Vec2d(0, 0))
                .apply(bp, LOADED.nodes()).applied());
        assertTrue(new EditOperation.SetLiteral(call, FuncNodes.FUNCTION_PIN,
                LiteralValue.of(PinTypes.STRING, function)).apply(bp, LOADED.nodes()).applied());
    }
}
