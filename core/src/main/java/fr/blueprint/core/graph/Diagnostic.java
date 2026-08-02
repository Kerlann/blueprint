package fr.blueprint.core.graph;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Anomalie rattachée à une cible précise du graphe — jamais un message générique (U3).
 * Les {@code args} alimentent la traduction ({@code Component.translatable}) côté client ;
 * aucune chaîne visible n'est composée ici.
 */
public record Diagnostic(DiagnosticCode code, Severity severity, Target target, List<Object> args) {

    public enum Severity {
        ERROR, WARNING
    }

    /** Ce que le diagnostic désigne ; l'éditeur s'en sert pour recentrer et surligner. */
    public sealed interface Target {
        record NodeTarget(UUID node) implements Target {}

        record LinkTarget(Link link) implements Target {}

        record VariableTarget(String name) implements Target {}

        record GraphTarget() implements Target {}
    }

    public static Diagnostic error(DiagnosticCode code, Target target, Object... args) {
        return new Diagnostic(code, Severity.ERROR, target, List.of(args));
    }

    public static Diagnostic warning(DiagnosticCode code, Target target, Object... args) {
        return new Diagnostic(code, Severity.WARNING, target, List.of(args));
    }

    public static Target node(UUID uuid) {
        return new Target.NodeTarget(uuid);
    }

    public static Target link(Link link) {
        return new Target.LinkTarget(link);
    }

    public static Target variable(String name) {
        return new Target.VariableTarget(name);
    }

    public static Target graph() {
        return new Target.GraphTarget();
    }

    public String translationKey() {
        return "blueprint.diag." + code.name().toLowerCase(Locale.ROOT);
    }
}
