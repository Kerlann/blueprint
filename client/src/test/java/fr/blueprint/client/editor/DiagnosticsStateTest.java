package fr.blueprint.client.editor;

import fr.blueprint.core.graph.Diagnostic;
import fr.blueprint.core.graph.DiagnosticCode;
import fr.blueprint.core.graph.Link;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticsStateTest {

    /** Horloge injectée : pas d'attente réelle dans les tests. */
    private final long[] now = {1_000};
    private final DiagnosticsState state = new DiagnosticsState(() -> now[0]);

    private static Diagnostic error(UUID node) {
        return Diagnostic.error(DiagnosticCode.REQUIRED_PIN_UNLINKED,
                Diagnostic.node(node), "amount");
    }

    private static Diagnostic warning(UUID node) {
        return Diagnostic.warning(DiagnosticCode.NO_ENTRY_POINT, Diagnostic.node(node));
    }

    @Test
    void debounceDe300Ms() {
        // À l'ouverture : une première validation part.
        now[0] += DiagnosticsState.DEBOUNCE_MS;
        assertTrue(state.shouldValidate());
        state.accept(List.of());
        assertFalse(state.shouldValidate());

        state.invalidate();
        now[0] += DiagnosticsState.DEBOUNCE_MS - 1;
        assertFalse(state.shouldValidate());
        now[0] += 1;
        assertTrue(state.shouldValidate());

        // Une frappe pendant l'attente repart le compteur.
        state.invalidate();
        now[0] += 100;
        state.invalidate();
        now[0] += DiagnosticsState.DEBOUNCE_MS - 100;
        assertFalse(state.shouldValidate());
    }

    @Test
    void compteursEtBlocage() {
        UUID a = UUID.randomUUID();
        state.accept(List.of(error(a), warning(a), warning(UUID.randomUUID())));
        assertEquals(1, state.errors());
        assertEquals(2, state.warnings());
        assertTrue(state.blocking());

        state.accept(List.of(warning(a)));
        assertFalse(state.blocking());
    }

    @Test
    void couleurDeLisereLErreurGagne() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        state.accept(List.of(warning(a), error(a), warning(b)));
        assertEquals(0xFFF7768E, state.outlineColor(a));
        assertEquals(0xFFE0AF68, state.outlineColor(b));
        assertEquals(0, state.outlineColor(UUID.randomUUID()));
    }

    @Test
    void cibleDuDiagnostic() {
        UUID node = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        assertEquals(node, DiagnosticsState.nodeOf(error(node)));
        // Un lien fautif recentre sur son aval.
        Diagnostic link = Diagnostic.error(DiagnosticCode.TYPE_MISMATCH,
                Diagnostic.link(new Link(node, "out", to, "in")), "in", "int", "string");
        assertEquals(to, DiagnosticsState.nodeOf(link));
        assertNull(DiagnosticsState.nodeOf(Diagnostic.error(
                DiagnosticCode.NO_ENTRY_POINT, Diagnostic.graph())));
    }

    @Test
    void validateNowCourtCircuiteLeDebounce() {
        state.accept(List.of());
        state.validateNow();
        assertTrue(state.shouldValidate());
    }
}
