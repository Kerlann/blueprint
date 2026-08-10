package fr.blueprint.client.editor;

import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.PinKind;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.NodeShape;
import fr.blueprint.core.graph.NodeTypeLookup;
import fr.blueprint.core.graph.VarNodes;
import fr.blueprint.core.graph.VarScope;
import fr.blueprint.core.graph.Vec2d;
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

class VariablePanelStateTest {

    /** Forme minimale des nœuds var (comme la vraie : var string, value any). */
    private static final NodeShape VAR_GET_SHAPE = new NodeShape(
            List.of(new NodeShape.PinDef("var", PinKind.DATA, PinTypes.STRING, false)),
            List.of(new NodeShape.PinDef("value", PinKind.DATA, PinTypes.ANY, false)),
            false, Permission.SAFE);

    /** Consommateur typé double strict — c'est lui qui casse au retypage. */
    private static final Identifier SINK = Identifier.fromNamespaceAndPath("test", "sink");
    private static final NodeShape SINK_SHAPE = new NodeShape(
            List.of(new NodeShape.PinDef("in", PinKind.DATA, PinTypes.DOUBLE, false)),
            List.of(), false, Permission.SAFE);

    private static final NodeTypeLookup LOOKUP = typeId ->
            VarNodes.GET.equals(typeId) ? VAR_GET_SHAPE
                    : SINK.equals(typeId) ? SINK_SHAPE : null;

    private Blueprint bp;
    private VariablePanelState state;

    @BeforeEach
    void setUp() {
        bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "vars"));
        state = new VariablePanelState(bp, LOOKUP, op -> op.apply(bp, LOOKUP).applied());
    }

    @Test
    void creationNomsUniquesEtSelection() {
        assertEquals("var1", state.create());
        assertEquals("var2", state.create());
        assertEquals("var2", state.selected());
        assertEquals(2, state.rows().size());
        assertEquals(PinTypes.DOUBLE, bp.variables().get("var1").type());
        assertEquals(VarScope.GRAPH, bp.variables().get("var1").scope());
    }

    @Test
    void renommageCommitAnnulationEtDoublon() {
        state.create();
        state.openRename("var1");
        state.backspace();
        state.backspace();
        state.backspace();
        state.backspace();
        state.type("score");
        assertTrue(state.commitRename());
        assertNotNull(bp.variables().get("score"));
        assertNull(bp.variables().get("var1"));
        assertEquals("score", state.selected());

        // Doublon refusé : le champ reste ouvert.
        state.create();
        state.openRename("var1");
        state.backspace();
        state.backspace();
        state.backspace();
        state.backspace();
        state.type("score");
        assertFalse(state.commitRename());
        assertTrue(state.isRenaming());
        state.cancelRename();
        assertFalse(state.isRenaming());
    }

    @Test
    void renommageRepointeLesNoeudsLies() {
        state.create();
        UUID get = UUID.randomUUID();
        assertTrue(new EditOperation.AddNode(get, VarNodes.GET, new Vec2d(0, 0))
                .apply(bp, LOOKUP).applied());
        assertTrue(new EditOperation.SetLiteral(get, "var",
                fr.blueprint.api.pin.LiteralValue.of(PinTypes.STRING, "var1"))
                .apply(bp, LOOKUP).applied());

        state.openRename("var1");
        state.type("bis"); // var1bis
        assertTrue(state.commitRename());
        assertEquals("var1bis", VarNodes.boundName(bp.node(get)));
    }

    @Test
    void renommageEstUnSeulGesteDAnnulation() {
        // Régression QA 5.5 : RenameVariable + repointage des nœuds = UNE entrée —
        // sans geste, un seul Ctrl+Z laissait un nœud pointant l'ancien nom.
        CanvasController controller = new CanvasController(bp, LOOKUP, new Camera());
        VariablePanelState panel = new VariablePanelState(bp, LOOKUP, controller::applyOp,
                controller::beginGesture, controller::endGesture);
        panel.create();
        UUID get = UUID.randomUUID();
        apply(new EditOperation.AddNode(get, VarNodes.GET, new Vec2d(0, 0)));
        apply(new EditOperation.SetLiteral(get, "var",
                fr.blueprint.api.pin.LiteralValue.of(PinTypes.STRING, "var1")));

        int before = controller.history().undoDepth();
        panel.openRename("var1");
        panel.type("bis");
        assertTrue(panel.commitRename());
        assertEquals(before + 1, controller.history().undoDepth());

        assertTrue(controller.undo());
        assertNotNull(bp.variables().get("var1"));
        assertEquals("var1", VarNodes.boundName(bp.node(get)));
    }

    @Test
    void cycleDeTypeSansLienPasseDirect() {
        state.create(); // var1 double
        assertTrue(state.cycleType("var1"));
        assertEquals(PinTypes.INT, bp.variables().get("var1").type());
        assertEquals(0, state.pendingBreaks());
    }

    @Test
    void retypageQuiCasseDesLiensExigeConfirmation() {
        state.create(); // var1 double
        UUID get = UUID.randomUUID();
        UUID sink = UUID.randomUUID();
        apply(new EditOperation.AddNode(get, VarNodes.GET, new Vec2d(0, 0)));
        apply(new EditOperation.SetLiteral(get, "var",
                fr.blueprint.api.pin.LiteralValue.of(PinTypes.STRING, "var1")));
        apply(new EditOperation.AddNode(sink, SINK, new Vec2d(300, 0)));
        apply(new EditOperation.AddLink(new Link(get, "value", sink, "in")));

        // double → int : int est assignable à double, rien ne casse.
        assertTrue(state.cycleType("var1"));
        // int → long : toujours assignable à double.
        assertTrue(state.cycleType("var1"));
        // long → bool : le lien vers « in » (double) casse → avertissement d'abord.
        assertFalse(state.cycleType("var1"));
        assertEquals(1, state.pendingBreaks());
        assertEquals(PinTypes.LONG, bp.variables().get("var1").type());
        // Second clic : appliqué.
        assertTrue(state.cycleType("var1"));
        assertEquals(PinTypes.BOOL, bp.variables().get("var1").type());
        assertEquals(0, state.pendingBreaks());
    }

    /**
     * Choisir dans le menu atteint un type <b>en un geste</b>.
     *
     * <p>C'est la raison d'être du menu : {@code vec3} est au sixième cran du cycle, donc
     * six clics — et rien, avant, ne disait qu'il existait.
     */
    @Test
    void leChoixDirectAtteintUnTypeEloigne() {
        state.create(); // var1 double
        assertTrue(state.retypeTo("var1", PinTypes.VEC3));
        assertEquals(PinTypes.VEC3, bp.variables().get("var1").type());
        assertEquals(0, state.pendingBreaks());
    }

    /**
     * Le choix direct ne contourne pas la confirmation.
     *
     * <p>Ce serait la faille évidente d'un second chemin vers le retypage : la garde
     * vivait dans {@code cycleType}, et un menu qui appellerait l'opération sans elle
     * casserait des liens sans un mot. Les deux chemins passent maintenant par le même
     * corps, et ce test le tient.
     */
    @Test
    void leChoixDirectExigeAussiLaConfirmation() {
        state.create(); // var1 double
        UUID get = UUID.randomUUID();
        UUID sink = UUID.randomUUID();
        apply(new EditOperation.AddNode(get, VarNodes.GET, new Vec2d(0, 0)));
        apply(new EditOperation.SetLiteral(get, "var",
                fr.blueprint.api.pin.LiteralValue.of(PinTypes.STRING, "var1")));
        apply(new EditOperation.AddNode(sink, SINK, new Vec2d(300, 0)));
        apply(new EditOperation.AddLink(new Link(get, "value", sink, "in")));

        assertFalse(state.retypeTo("var1", PinTypes.VEC3),
                "un vecteur ne nourrit pas un pin double : il faut avertir avant");
        assertEquals(1, state.pendingBreaks());
        assertEquals(PinTypes.DOUBLE, bp.variables().get("var1").type());

        assertTrue(state.retypeTo("var1", PinTypes.VEC3));
        assertEquals(PinTypes.VEC3, bp.variables().get("var1").type());
    }

    /**
     * Reposer le type courant n'applique <b>aucune</b> opération.
     *
     * <p>Un cycle ne pouvait pas produire ce cas ; un menu, si — on l'ouvre, on hésite, on
     * reclique sur la ligne marquée. Laisser passer l'opération empilerait un pas
     * d'annulation qui ne change rien, et l'utilisateur devrait annuler deux fois pour
     * défaire une seule modification réelle.
     */
    @Test
    void reposerLeMemeTypeNAppliqueRien() {
        int[] appliquees = {0};
        VariablePanelState compte = new VariablePanelState(bp, LOOKUP, op -> {
            appliquees[0]++;
            return op.apply(bp, LOOKUP).applied();
        });
        compte.create(); // var1 double
        int apresCreation = appliquees[0];

        assertFalse(compte.retypeTo("var1", PinTypes.DOUBLE));
        assertEquals(apresCreation, appliquees[0],
                "un aller-retour dans le menu ne doit rien empiler dans l'annulation");
    }

    /**
     * Le cycle va du plus étroit au plus large : graphe → joueur → joueur partagé →
     * monde. Cliquer élargit, ce qui est le sens dans lequel on hésite.
     */
    @Test
    void cycleDePortee() {
        state.create();
        assertTrue(state.cycleScope("var1"));
        assertEquals(VarScope.PLAYER, bp.variables().get("var1").scope(),
                "un graphe qui s'ouvre s'ouvre d'abord au joueur, pas au monde entier");
        assertTrue(state.cycleScope("var1"));
        assertEquals(VarScope.PLAYER_SHARED, bp.variables().get("var1").scope());
        assertTrue(state.cycleScope("var1"));
        assertEquals(VarScope.WORLD, bp.variables().get("var1").scope());
    }

    @Test
    void suppression() {
        state.create();
        assertTrue(state.delete("var1"));
        assertTrue(bp.variables().isEmpty());
        assertNull(state.selected());
    }

    private void apply(EditOperation op) {
        assertTrue(op.apply(bp, LOOKUP).applied());
    }
}
