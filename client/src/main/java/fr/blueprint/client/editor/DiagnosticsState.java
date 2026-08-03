package fr.blueprint.client.editor;

import fr.blueprint.core.graph.Diagnostic;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Validation à la volée (story 5.6b) : chaque mutation invalide le rapport, la
 * validation ne se relance qu'après {@link #DEBOUNCE_MS} de calme — jamais dans la
 * frame de la frappe. Pur : l'horloge est injectée (testable sans attendre).
 */
public final class DiagnosticsState {

    public static final long DEBOUNCE_MS = 300;

    private final LongSupplier clock;
    private List<Diagnostic> report = List.of();
    private long dirtySince = -1;
    private boolean expanded;
    private int errors;
    private int warnings;

    public DiagnosticsState(LongSupplier clock) {
        this.clock = clock;
        this.dirtySince = clock.getAsLong(); // valider une première fois à l'ouverture
    }

    /** Une mutation vient d'avoir lieu : repartir le débouncé. */
    public void invalidate() {
        dirtySince = clock.getAsLong();
    }

    /** Le débouncé est-il échu ? (le widget lance alors la validation) */
    public boolean shouldValidate() {
        return dirtySince >= 0 && clock.getAsLong() - dirtySince >= DEBOUNCE_MS;
    }

    /** Force la prochaine validation sans attendre (bouton Compiler). */
    public void validateNow() {
        dirtySince = clock.getAsLong() - DEBOUNCE_MS;
    }

    public void accept(List<Diagnostic> diagnostics) {
        report = List.copyOf(diagnostics);
        dirtySince = -1;
        errors = 0;
        warnings = 0;
        for (int i = 0; i < report.size(); i++) {
            if (report.get(i).severity() == Diagnostic.Severity.ERROR) {
                errors++;
            } else {
                warnings++;
            }
        }
    }

    public List<Diagnostic> report() {
        return report;
    }

    public int errors() {
        return errors;
    }

    public int warnings() {
        return warnings;
    }

    /** Une erreur bloquante interdit le bouton Tester (UX §8). */
    public boolean blocking() {
        return errors > 0;
    }

    public boolean expanded() {
        return expanded;
    }

    public void toggleExpanded() {
        expanded = !expanded;
    }

    /** Le nœud visé par un diagnostic, ou null (lien, variable, graphe). */
    public static @Nullable UUID nodeOf(Diagnostic d) {
        if (d.target() instanceof Diagnostic.Target.NodeTarget(UUID node)) {
            return node;
        }
        if (d.target() instanceof Diagnostic.Target.LinkTarget(fr.blueprint.core.graph.Link link)) {
            return link.toNode(); // recentrer sur l'aval du lien fautif
        }
        return null;
    }

    /**
     * Couleur de liseré du nœud selon le pire diagnostic qui le vise, ou 0.
     * Parcours linéaire : le rapport est court et ne change qu'à la validation.
     */
    public int outlineColor(UUID node) {
        int color = 0;
        for (int i = 0; i < report.size(); i++) {
            Diagnostic d = report.get(i);
            if (node.equals(nodeOf(d))) {
                if (d.severity() == Diagnostic.Severity.ERROR) {
                    return fr.blueprint.client.theme.Theme.current().error();
                }
                color = fr.blueprint.client.theme.Theme.current().warning();
            }
        }
        return color;
    }
}
