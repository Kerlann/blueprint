package fr.blueprint.core.graph;

import fr.blueprint.core.graph.screen.Screen;
import fr.blueprint.core.graph.screen.ScreenElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Les opérations d'édition d'écran (story 10.1, AC4).
 *
 * <p>Un fichier à part plutôt que huit records de plus dans {@link EditOperation}, qui
 * en porte déjà dix-huit et frôle les quatre cents lignes. Elles implémentent la même
 * interface, passent par le même {@code applyTracked} et alimentent la même pile
 * d'annulation — seul leur rangement diffère.
 *
 * <p>Toutes suivent la règle du modèle : refuser proprement plutôt qu'appliquer à
 * moitié, porter son inverse, incrémenter la révision.
 */
public final class ScreenOps {

    private ScreenOps() {
    }

    public record AddScreen(Screen screen) implements EditOperation {
        @Override
        public Result apply(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
            if (bp.screen(screen.name()) != null) {
                return Result.refused(Diagnostic.error(DiagnosticCode.DUPLICATE_SCREEN,
                        Diagnostic.screen(screen.name()), screen.name()));
            }
            if (bp.screens().size() >= limits.maxScreens()) {
                return Result.refused(Diagnostic.error(DiagnosticCode.SCREEN_LIMIT_EXCEEDED,
                        Diagnostic.graph(), bp.screens().size(), limits.maxScreens()));
            }
            bp.putScreen(screen);
            bp.bumpRevision();
            return Result.ok(new RemoveScreen(screen.name()));
        }
    }

    public record RemoveScreen(String name) implements EditOperation {
        @Override
        public Result apply(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
            Screen before = bp.screen(name);
            if (before == null) {
                return Result.refused(Diagnostic.error(DiagnosticCode.SCREEN_NOT_FOUND,
                        Diagnostic.screen(name), name));
            }
            bp.dropScreen(name);
            bp.bumpRevision();
            return Result.ok(new AddScreen(before));
        }
    }

    /**
     * Remplace un écran entier. C'est l'inverse naturel de plusieurs des autres : un
     * écran est immuable, donc « défaire une suppression en cascade » revient à
     * reposer l'écran d'avant — bien plus sûr que de reconstituer les enfants un à un.
     */
    public record SetScreen(Screen screen) implements EditOperation {
        @Override
        public Result apply(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
            Screen before = bp.screen(screen.name());
            if (before == null) {
                return Result.refused(Diagnostic.error(DiagnosticCode.SCREEN_NOT_FOUND,
                        Diagnostic.screen(screen.name()), screen.name()));
            }
            if (screen.size() > limits.maxElementsPerScreen()) {
                return Result.refused(Diagnostic.error(DiagnosticCode.ELEMENT_LIMIT_EXCEEDED,
                        Diagnostic.screen(screen.name()),
                        screen.size(), limits.maxElementsPerScreen()));
            }
            bp.putScreen(screen);
            bp.bumpRevision();
            return Result.ok(new SetScreen(before));
        }
    }

    public record AddElement(String screen, ScreenElement element) implements EditOperation {
        @Override
        public Result apply(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
            Screen target = bp.screen(screen);
            if (target == null) {
                return Result.refused(Diagnostic.error(DiagnosticCode.SCREEN_NOT_FOUND,
                        Diagnostic.screen(screen), screen));
            }
            if (target.element(element.name()) != null) {
                return Result.refused(Diagnostic.error(DiagnosticCode.DUPLICATE_ELEMENT,
                        Diagnostic.element(screen, element.name()), element.name()));
            }
            if (target.size() >= limits.maxElementsPerScreen()) {
                return Result.refused(Diagnostic.error(DiagnosticCode.ELEMENT_LIMIT_EXCEEDED,
                        Diagnostic.screen(screen),
                        target.size(), limits.maxElementsPerScreen()));
            }
            Diagnostic refusal = ScreenRules.checkPlacement(screen, target, element, limits);
            if (refusal != null) {
                return Result.refused(refusal);
            }
            bp.putScreen(target.with(element));
            bp.bumpRevision();
            return Result.ok(new RemoveElement(screen, element.name()));
        }
    }

    /**
     * Retire un élément <b>et ses descendants</b>. L'inverse est donc l'écran entier :
     * rendre le seul élément laisserait ses enfants perdus.
     */
    public record RemoveElement(String screen, String element) implements EditOperation {
        @Override
        public Result apply(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
            Screen target = bp.screen(screen);
            if (target == null) {
                return Result.refused(Diagnostic.error(DiagnosticCode.SCREEN_NOT_FOUND,
                        Diagnostic.screen(screen), screen));
            }
            if (target.element(element) == null) {
                return Result.refused(Diagnostic.error(DiagnosticCode.ELEMENT_NOT_FOUND,
                        Diagnostic.element(screen, element), element));
            }
            bp.putScreen(target.without(element));
            bp.bumpRevision();
            return Result.ok(new SetScreen(target));
        }
    }

    /** Modifie un élément en place : position, taille, style, texte, visibilité. */
    public record SetElement(String screen, ScreenElement element) implements EditOperation {
        @Override
        public Result apply(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
            Screen target = bp.screen(screen);
            if (target == null) {
                return Result.refused(Diagnostic.error(DiagnosticCode.SCREEN_NOT_FOUND,
                        Diagnostic.screen(screen), screen));
            }
            ScreenElement before = target.element(element.name());
            if (before == null) {
                return Result.refused(Diagnostic.error(DiagnosticCode.ELEMENT_NOT_FOUND,
                        Diagnostic.element(screen, element.name()), element.name()));
            }
            Diagnostic refusal = ScreenRules.checkPlacement(screen, target, element, limits);
            if (refusal != null) {
                return Result.refused(refusal);
            }
            bp.putScreen(target.replacing(element.name(), element));
            bp.bumpRevision();
            return Result.ok(new SetElement(screen, before));
        }
    }

    /**
     * Renomme un élément <b>et rattache ses enfants</b> au nouveau nom. Les laisser
     * pointer vers l'ancien les rendrait orphelins — invisibles et indélogeables.
     */
    public record RenameElement(String screen, String from, String to) implements EditOperation {
        @Override
        public Result apply(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
            Screen target = bp.screen(screen);
            if (target == null) {
                return Result.refused(Diagnostic.error(DiagnosticCode.SCREEN_NOT_FOUND,
                        Diagnostic.screen(screen), screen));
            }
            if (target.element(from) == null) {
                return Result.refused(Diagnostic.error(DiagnosticCode.ELEMENT_NOT_FOUND,
                        Diagnostic.element(screen, from), from));
            }
            if (to == null || to.isBlank()) {
                return Result.refused(Diagnostic.error(DiagnosticCode.DUPLICATE_ELEMENT,
                        Diagnostic.element(screen, from), String.valueOf(to)));
            }
            if (!from.equals(to) && target.element(to) != null) {
                return Result.refused(Diagnostic.error(DiagnosticCode.DUPLICATE_ELEMENT,
                        Diagnostic.element(screen, to), to));
            }
            List<ScreenElement> out = new ArrayList<>();
            for (ScreenElement existing : target.elements().values()) {
                ScreenElement reparented =
                        from.equals(existing.parent()) ? existing.withParent(to) : existing;
                out.add(existing.name().equals(from) ? reparented.renamed(to) : reparented);
            }
            bp.putScreen(new Screen(target.name(), target.hud(), out));
            bp.bumpRevision();
            return Result.ok(new SetScreen(target));
        }
    }

    /** Monte ou descend un élément dans l'ordre de dessin. */
    public record ReorderElement(String screen, String element, int delta)
            implements EditOperation {
        @Override
        public Result apply(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
            Screen target = bp.screen(screen);
            if (target == null) {
                return Result.refused(Diagnostic.error(DiagnosticCode.SCREEN_NOT_FOUND,
                        Diagnostic.screen(screen), screen));
            }
            if (target.element(element) == null) {
                return Result.refused(Diagnostic.error(DiagnosticCode.ELEMENT_NOT_FOUND,
                        Diagnostic.element(screen, element), element));
            }
            bp.putScreen(target.reordered(element, delta));
            bp.bumpRevision();
            return Result.ok(new SetScreen(target));
        }
    }
}
