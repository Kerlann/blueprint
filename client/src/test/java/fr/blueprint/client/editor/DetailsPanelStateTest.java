package fr.blueprint.client.editor;

import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.BlueprintMeta;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.Vec2d;
import fr.blueprint.core.registry.NodeDescriptor;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DetailsPanelStateTest {

    private static final PluginLoader.LoadedRegistries LOADED =
            PluginLoader.load(List.of(), true);

    private Blueprint bp;
    private DetailsPanelState state;

    @BeforeEach
    void setUp() {
        bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "details"));
        state = new DetailsPanelState(bp,
                id -> LOADED.nodes().get(id).map(NodeDescriptor::of).orElse(null),
                op -> op.apply(bp, LOADED.nodes()).applied(),
                key -> key);
    }

    private UUID add(String path, double x) {
        UUID id = UUID.randomUUID();
        assertTrue(new EditOperation.AddNode(id,
                Identifier.fromNamespaceAndPath("blueprint", path), new Vec2d(x, 0))
                .apply(bp, LOADED.nodes()).applied());
        return id;
    }

    private static List<DetailsPanelState.Row> ofKind(List<DetailsPanelState.Row> rows,
                                                      DetailsPanelState.Kind kind) {
        return rows.stream().filter(r -> r.kind() == kind).toList();
    }

    @Test
    void selectionVideMontreLeBlueprint() {
        var rows = state.rows(List.of());
        assertEquals(DetailsPanelState.Kind.HEADER, rows.get(0).kind());
        assertEquals("test:details", rows.get(0).value());
        assertEquals(1, ofKind(rows, DetailsPanelState.Kind.META_AUTHOR).size());
        assertEquals(1, ofKind(rows, DetailsPanelState.Kind.META_CAP).size());
    }

    @Test
    void noeudSeulLitterauxEtLienCliquable() {
        UUID sum = add("math/add", 0);
        UUID mul = add("math/mul", -200);
        assertTrue(new EditOperation.SetLiteral(sum, "b", LiteralValue.of(PinTypes.DOUBLE, 4.0))
                .apply(bp, LOADED.nodes()).applied());
        assertTrue(new EditOperation.AddLink(new Link(mul, "result", sum, "a"))
                .apply(bp, LOADED.nodes()).applied());

        var rows = state.rows(Set.of(sum));
        // « a » est câblé → ligne WIRED pointant la source ; « b » → LITERAL 4.
        var wired = ofKind(rows, DetailsPanelState.Kind.WIRED);
        assertEquals(1, wired.size());
        assertEquals(mul, wired.get(0).node());
        var literals = ofKind(rows, DetailsPanelState.Kind.LITERAL);
        assertEquals(1, literals.size());
        assertEquals("b", literals.get(0).pin());
        assertEquals("4", literals.get(0).value());
    }

    @Test
    void selectionMultipleEtFantome() {
        UUID a = add("math/add", 0);
        UUID b = add("math/mul", 200);
        assertEquals(DetailsPanelState.Kind.HEADER, state.rows(Set.of(a, b)).get(0).kind());
        assertEquals("2", state.rows(Set.of(a, b)).get(0).value());

        UUID ghost = UUID.randomUUID();
        assertTrue(new EditOperation.AddNode(ghost,
                Identifier.fromNamespaceAndPath("gonemod", "node"), new Vec2d(0, 0))
                .apply(bp, LOADED.nodes()).applied());
        var rows = state.rows(Set.of(ghost));
        assertEquals("gonemod:node", rows.get(0).label());
        assertEquals("gonemod", rows.get(1).value());
    }

    @Test
    void editionDesMetadonnees() {
        state.openMetaEdit(DetailsPanelState.MetaField.AUTHOR);
        state.type("Kerlann");
        assertTrue(state.commitMetaEdit());
        assertEquals("Kerlann", bp.meta().author());
        assertFalse(state.isEditingMeta());

        state.openMetaEdit(DetailsPanelState.MetaField.DESCRIPTION);
        state.type("x");
        state.cancelMetaEdit();
        assertEquals("", bp.meta().description());
    }

    @Test
    void cycleDuPlafondDePermission() {
        assertEquals(Permission.GAMEPLAY, bp.meta().permissionCap());
        assertTrue(state.cyclePermissionCap());
        assertEquals(Permission.WORLD, bp.meta().permissionCap());
        // L'inverse de SetMeta restaure l'ancien plafond.
        BlueprintMeta before = bp.meta();
        EditOperation.Result result = new EditOperation.SetMeta(new BlueprintMeta(
                "a", "b", "2.0.0", Permission.ADMIN)).apply(bp, LOADED.nodes());
        assertTrue(result.applied());
        assertTrue(result.inverse().apply(bp, LOADED.nodes()).applied());
        assertEquals(before, bp.meta());
    }

    // ----------------------------------------- signature d'une fonction (20.2, AC4)

    /** Ouvre un corps « carre » et branche le panneau dessus. */
    private GraphView ouvrirCarre() {
        assertTrue(new fr.blueprint.core.graph.FunctionOps.AddFunction(
                fr.blueprint.core.graph.BlueprintFunction.of("carre", List.of(), List.of()))
                .apply(bp, LOADED.nodes()).applied());
        GraphView view = new GraphView(bp);
        assertTrue(view.open("carre"));
        state.follow(view);
        return view;
    }

    private fr.blueprint.core.graph.BlueprintFunction carre() {
        return bp.function("carre");
    }

    /**
     * <b>Dans un corps, le panneau décrit la signature</b> (AC4).
     *
     * <p>C'est la seule chose d'une fonction qui ne se pose pas sur la toile. Sans un endroit
     * pour la changer, il faudrait écrire du BScript à la main — exactement le trou que
     * cette story ferme.
     */
    @Test
    void dansUnCorpsLePanneauDecritLaSignature() {
        assertEquals(DetailsPanelState.Kind.META_AUTHOR, state.rows(List.of()).get(1).kind(),
                "hors d'un corps, le panneau décrit le blueprint");

        ouvrirCarre();
        var rows = state.rows(List.of());

        assertEquals("carre", rows.get(0).value());
        assertEquals(1, ofKind(rows, DetailsPanelState.Kind.PARAM_ADD_IN).size());
        assertEquals(1, ofKind(rows, DetailsPanelState.Kind.PARAM_ADD_OUT).size());
        assertTrue(ofKind(rows, DetailsPanelState.Kind.META_AUTHOR).isEmpty(),
                "les métadonnées du blueprint n'ont rien à faire là : elles feraient croire "
                        + "qu'on édite le blueprint alors qu'on édite une fonction");
    }

    /** Ajouter, renommer, retyper, retirer — les quatre gestes de l'AC4. */
    @Test
    void lesQuatreGestesDeLaSignature() {
        ouvrirCarre();

        assertTrue(state.addParam(false));
        assertEquals(1, carre().inputs().size());
        assertEquals(PinTypes.DOUBLE, carre().inputs().get(0).type());

        assertTrue(state.cycleParamType(0, false));
        assertFalse(PinTypes.DOUBLE.equals(carre().inputs().get(0).type()),
                "le type doit avoir tourné");

        state.openParamEdit(0, false);
        assertTrue(state.isEditingParam());
        state.backspace();
        state.backspace();
        state.type("n");
        assertTrue(state.commitParamEdit());
        assertEquals("n", carre().inputs().get(0).name());

        assertTrue(state.addParam(true));
        assertEquals(1, carre().outputs().size());
        assertTrue(state.removeParam(0, false));
        assertTrue(carre().inputs().isEmpty());
        assertEquals(1, carre().outputs().size(),
                "retirer une entrée ne doit pas emporter la sortie : les deux côtés sont "
                        + "édités par la MÊME opération, et confondre les listes les écrase");
    }

    /**
     * <b>Deux paramètres homonymes sont refusés, des deux côtés.</b>
     *
     * <p>Un nom de paramètre est un nom de <b>pin</b> sur le nœud d'appel. Une entrée et une
     * sortie du même nom donneraient deux pins homonymes, dont l'un ne serait plus jamais
     * désigné par un lien — et le graphe se casserait sans qu'on sache où.
     */
    @Test
    void deuxParametresHomonymesSontRefusesDesDeuxCotes() {
        ouvrirCarre();
        assertTrue(state.addParam(false));
        assertTrue(state.addParam(true));
        renommer(0, true, "r");
        assertEquals("r", carre().outputs().get(0).name());

        // Le sens qui compte : renommer une ENTRÉE comme une SORTIE. Ne regarder que les
        // entrées attraperait l'autre sens et laisserait passer celui-ci.
        renommer(0, false, "r");

        assertFalse("r".equals(carre().inputs().get(0).name()),
                "une entrée et une sortie du même nom donneraient deux pins homonymes sur "
                        + "le nœud d'appel, dont l'un ne serait plus jamais désigné");
        assertFalse(state.isEditingParam(), "et le champ se referme au lieu de rester coincé");

        // Et l'autre sens tient aussi.
        renommer(0, true, carre().inputs().get(0).name());
        assertEquals("r", carre().outputs().get(0).name());
    }

    /** Vide le champ puis y tape {@code nom}, et valide. */
    private void renommer(int index, boolean output, String nom) {
        state.openParamEdit(index, output);
        for (int i = 0; i < 8; i++) {
            state.backspace();
        }
        state.type(nom);
        state.commitParamEdit();
    }

    /**
     * <b>Un nœud sélectionné dans un corps a bien ses détails.</b>
     *
     * <p>Le panneau interrogeait le graphe principal. Un nœud de corps n'y existe pas : le
     * panneau restait vide, et rien n'expliquait la disparition.
     */
    @Test
    void unNoeudDUnCorpsABienSesDetails() {
        ouvrirCarre();
        UUID dansLeCorps = UUID.randomUUID();
        assertTrue(new fr.blueprint.core.graph.FunctionOps.AddNodeIn("carre", dansLeCorps,
                Identifier.fromNamespaceAndPath("blueprint", "debug/log"), new Vec2d(0, 0))
                .apply(bp, LOADED.nodes()).applied());

        var rows = state.rows(List.of(dansLeCorps));

        assertFalse(rows.isEmpty(),
                "un panneau vide sur un nœud sélectionné ne se distingue pas d'un panneau "
                        + "cassé");
        assertEquals(DetailsPanelState.Kind.HEADER, rows.get(0).kind());
    }

    /** Hors d'un corps, les gestes de signature ne visent rien et refusent. */
    @Test
    void horsDUnCorpsLesGestesDeSignatureRefusent() {
        assertFalse(state.addParam(false));
        assertFalse(state.removeParam(0, false));
        assertFalse(state.cycleParamType(0, false));
        state.openParamEdit(0, false);
        assertFalse(state.isEditingParam());
        assertFalse(state.commitParamEdit());
    }
}
