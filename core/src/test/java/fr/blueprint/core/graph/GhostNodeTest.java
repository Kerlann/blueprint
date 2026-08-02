package fr.blueprint.core.graph;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static fr.blueprint.core.graph.TestNodes.LOOKUP;
import static fr.blueprint.core.graph.TestNodes.apply;
import static fr.blueprint.core.graph.TestNodes.newGraph;
import static fr.blueprint.core.graph.TestNodes.node;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nœuds fantômes (AC6, principe P4) : le scénario complet mod présent → mod retiré →
 * mod réinstallé, sans aucune perte.
 */
class GhostNodeTest {

    /** Le même graphe vu avec un lookup où le type « test:print » a disparu. */
    private static final NodeTypeLookup WITHOUT_PRINT =
            typeId -> typeId.equals(TestNodes.PRINT) ? null : LOOKUP.shape(typeId);

    private Blueprint buildGraph() {
        var bp = newGraph();
        UUID start = node(bp, "s", TestNodes.START);
        UUID print = node(bp, "p", TestNodes.PRINT);
        apply(bp, new EditOperation.AddLink(new Link(start, "exec_out", print, "exec_in")));
        apply(bp, new EditOperation.SetLiteral(print, "text", LiteralValue.of(PinTypes.STRING, "salut")));
        return bp;
    }

    @Test
    void missingModMakesGraphNonExecutableButLosesNothing() {
        var bp = buildGraph();
        var result = GraphValidator.validate(bp, WITHOUT_PRINT);

        assertFalse(result.executable(), "un fantôme interdit l'exécution (FR41)");
        var diag = result.diagnostics().stream()
                .filter(d -> d.code() == DiagnosticCode.UNKNOWN_NODE_TYPE)
                .findFirst().orElseThrow();
        assertTrue(diag.args().contains("test"), "le diagnostic nomme le mod manquant : " + diag.args());

        // Rien n'a été supprimé : nœud, lien, littéral, tout est encore là.
        assertEquals(2, bp.nodes().size());
        assertEquals(1, bp.links().size());
        UUID print = TestNodes.uuid("p");
        assertEquals("salut", bp.node(print).literal("text").value());
    }

    @Test
    void ghostShapeIsDeducedFromLinksAndLiterals() {
        var bp = buildGraph();
        Node ghost = bp.node(TestNodes.uuid("p"));
        NodeShape shape = GhostNode.deduceShape(bp, WITHOUT_PRINT, ghost);

        NodeShape.PinDef execIn = shape.input("exec_in");
        assertNotNull(execIn, "le pin lié doit être déduit");
        assertEquals(fr.blueprint.api.pin.PinKind.EXEC, execIn.kind(),
                "la nature exec vient du pin d'en face");
        assertNotNull(shape.input("text"), "le pin porteur d'un littéral doit être déduit");
        assertFalse(shape.entryPoint());
    }

    @Test
    void reinstallingTheModRestoresEverything() {
        var bp = buildGraph();
        Blueprint before = bp.copy();

        // Mod retiré : valide en fantôme — puis mod réinstallé : le graphe est identique.
        GraphValidator.validate(bp, WITHOUT_PRINT);
        var restored = GraphValidator.validate(bp, LOOKUP);

        assertTrue(bp.contentEquals(before), "la validation ne mute jamais le graphe");
        assertTrue(restored.executable());
    }

    @Test
    void unknownTypeCanStillBeAddedByOperation() {
        // Import d'un graphe référençant un mod absent : l'ajout n'est pas bloqué (P4).
        var bp = newGraph();
        apply(bp, new EditOperation.AddNode(TestNodes.uuid("g"), TestNodes.MISSING, Vec2d.ZERO));
        var result = GraphValidator.validate(bp, LOOKUP);
        assertFalse(result.executable());
        assertTrue(result.diagnostics().stream()
                .anyMatch(d -> d.code() == DiagnosticCode.UNKNOWN_NODE_TYPE
                        && d.args().contains("absentmod")));
    }
}
