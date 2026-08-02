package fr.blueprint.core.graph;

import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static fr.blueprint.core.graph.TestNodes.LOOKUP;
import static fr.blueprint.core.graph.TestNodes.apply;
import static fr.blueprint.core.graph.TestNodes.newGraph;
import static fr.blueprint.core.graph.TestNodes.node;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validation globale (AC3, AC4, AC6 partiel) — chaque code du validateur a son cas. */
class GraphValidatorTest {

    private static boolean has(GraphValidator.ValidationResult r, DiagnosticCode code) {
        return r.diagnostics().stream().anyMatch(d -> d.code() == code);
    }

    @Test
    void cleanGraphIsExecutable() {
        var bp = newGraph();
        UUID start = node(bp, "s", TestNodes.START);
        UUID print = node(bp, "p", TestNodes.PRINT);
        apply(bp, new EditOperation.AddLink(new Link(start, "exec_out", print, "exec_in")));
        apply(bp, new EditOperation.SetLiteral(print, "text", LiteralValue.of(PinTypes.STRING, "bonjour")));
        var result = GraphValidator.validate(bp, LOOKUP);
        assertTrue(result.executable(), () -> "diagnostics inattendus : " + result.diagnostics());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void dataCycleBetweenTwoNodesIsDetected() {
        // Chaque lien est valide isolément ; le cycle n'apparaît qu'au niveau du graphe (FR7).
        var bp = newGraph();
        UUID a = node(bp, "a", TestNodes.ADD);
        UUID b = node(bp, "b", TestNodes.ADD);
        apply(bp, new EditOperation.AddLink(new Link(a, "sum", b, "a")));
        apply(bp, new EditOperation.AddLink(new Link(b, "sum", a, "a")));
        var result = GraphValidator.validate(bp, LOOKUP);
        assertTrue(has(result, DiagnosticCode.DATA_CYCLE));
        assertFalse(result.executable());
    }

    @Test
    void execCycleIsAllowed() {
        // Une boucle d'exécution est un while : jamais un diagnostic (FR7).
        var bp = newGraph();
        UUID p1 = node(bp, "p1", TestNodes.PRINT);
        UUID p2 = node(bp, "p2", TestNodes.PRINT);
        apply(bp, new EditOperation.SetLiteral(p1, "text", LiteralValue.of(PinTypes.STRING, "a")));
        apply(bp, new EditOperation.SetLiteral(p2, "text", LiteralValue.of(PinTypes.STRING, "b")));
        apply(bp, new EditOperation.AddLink(new Link(p1, "exec_out", p2, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(p2, "exec_out", p1, "exec_in")));
        var result = GraphValidator.validate(bp, LOOKUP);
        assertFalse(has(result, DiagnosticCode.DATA_CYCLE));
    }

    @Test
    void missingEntryPointIsAWarning() {
        var bp = newGraph();
        node(bp, "p", TestNodes.ADD);
        var result = GraphValidator.validate(bp, LOOKUP);
        assertTrue(has(result, DiagnosticCode.NO_ENTRY_POINT));
        // Avertissement, pas erreur : le graphe reste exécutable (il ne fera juste rien).
        assertTrue(result.diagnostics().stream()
                .filter(d -> d.code() == DiagnosticCode.NO_ENTRY_POINT)
                .allMatch(d -> d.severity() == Diagnostic.Severity.WARNING));
    }

    @Test
    void requiredPinMustBeLinkedOrSet() {
        var bp = newGraph();
        UUID print = node(bp, "p", TestNodes.PRINT);
        assertTrue(has(GraphValidator.validate(bp, LOOKUP), DiagnosticCode.REQUIRED_PIN_UNLINKED));
        apply(bp, new EditOperation.SetLiteral(print, "text", LiteralValue.of(PinTypes.STRING, "ok")));
        assertFalse(has(GraphValidator.validate(bp, LOOKUP), DiagnosticCode.REQUIRED_PIN_UNLINKED));
    }

    @Test
    void requiredReferencePinCannotBeSatisfiedByLiteral() {
        var bp = newGraph();
        node(bp, "n", TestNodes.NEEDS_ENTITY);
        assertTrue(has(GraphValidator.validate(bp, LOOKUP), DiagnosticCode.REQUIRED_PIN_UNLINKED));
    }

    @Test
    void permissionCapIsEnforced() {
        var bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "capped"),
                new BlueprintMeta("", "", "1.0.0", Permission.GAMEPLAY));
        node(bp, "boom", TestNodes.BOOM);
        var result = GraphValidator.validate(bp, LOOKUP);
        assertTrue(has(result, DiagnosticCode.PERMISSION_EXCEEDED));
        assertFalse(result.executable());

        var admin = new Blueprint(Identifier.fromNamespaceAndPath("test", "admin"),
                new BlueprintMeta("", "", "1.0.0", Permission.ADMIN));
        node(admin, "boom", TestNodes.BOOM);
        assertFalse(has(GraphValidator.validate(admin, LOOKUP), DiagnosticCode.PERMISSION_EXCEEDED));
    }

    @Test
    void nodeLimitIsEnforcedAtValidation() {
        var bp = newGraph();
        node(bp, "a", TestNodes.ADD);
        node(bp, "b", TestNodes.ADD);
        var result = GraphValidator.validate(bp, LOOKUP, new GraphLimits(1));
        assertTrue(has(result, DiagnosticCode.NODE_LIMIT_EXCEEDED));
    }

    @Test
    void forcedIllegalStatesAreCaughtInDepth() {
        // Défense en profondeur : on force des états que les opérations refuseraient
        // (mutations package-private), le validateur doit quand même les voir.
        var bp = newGraph();
        UUID start = node(bp, "s", TestNodes.START);
        UUID p1 = node(bp, "p1", TestNodes.PRINT);
        UUID p2 = node(bp, "p2", TestNodes.PRINT);
        bp.putLink(new Link(start, "exec_out", p1, "exec_in"));
        bp.putLink(new Link(start, "exec_out", p2, "exec_in"));   // exec out dupliqué
        var result = GraphValidator.validate(bp, LOOKUP);
        assertTrue(has(result, DiagnosticCode.EXEC_OUT_ALREADY_LINKED));

        var bp2 = newGraph();
        UUID a1 = node(bp2, "a1", TestNodes.ADD);
        UUID a2 = node(bp2, "a2", TestNodes.ADD);
        UUID sink = node(bp2, "sink", TestNodes.ADD);
        bp2.putLink(new Link(a1, "sum", sink, "a"));
        bp2.putLink(new Link(a2, "sum", sink, "a"));              // data in dupliqué
        assertTrue(has(GraphValidator.validate(bp2, LOOKUP), DiagnosticCode.DATA_IN_ALREADY_LINKED));

        var bp3 = newGraph();
        UUID ml = node(bp3, "ml", TestNodes.MAKE_LIST);
        UUID first = node(bp3, "f", TestNodes.FIRST);
        UUID print = node(bp3, "p", TestNodes.PRINT);
        bp3.putLink(new Link(ml, "list", first, "list"));
        bp3.putLink(new Link(first, "elem", print, "text"));      // T=int contre string
        assertTrue(has(GraphValidator.validate(bp3, LOOKUP), DiagnosticCode.GENERIC_CONFLICT));
    }

    @Test
    void deepLiteralMismatchIsCaughtByValidator() {
        // Reprise QA TYPE-001, côté validateur : littéral forcé sans passer par l'op.
        var bp = newGraph();
        UUID first = node(bp, "f", TestNodes.FIRST);
        bp.node(first).setLiteral("list",
                LiteralValue.of(PinTypes.listOf(PinTypes.INT), List.of("oops")));
        assertTrue(has(GraphValidator.validate(bp, LOOKUP), DiagnosticCode.TYPE_MISMATCH));
    }
}
