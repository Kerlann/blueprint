package fr.blueprint.core.graph;

import fr.blueprint.core.graph.screen.Screen;
import fr.blueprint.core.graph.screen.ScreenElement;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Les règles de placement d'un élément d'écran — <b>la source de vérité unique</b>,
 * comme {@code GraphValidator.canLink} l'est pour le câblage (story 10.1, AC5).
 *
 * <p>Les opérations d'édition et le validateur l'appellent tous deux. Deux jeux de
 * règles divergeraient : l'éditeur laisserait poser ce que le validateur refuse
 * ensuite, ou l'inverse — et l'auteur ne saurait pas lequel croire.
 */
public final class ScreenRules {

    private ScreenRules() {
    }

    /**
     * Vérifie qu'un élément peut être posé ou modifié dans cet écran. Rend le
     * diagnostic qui l'interdit, ou {@code null} si tout va bien.
     */
    public static @Nullable Diagnostic checkPlacement(String screenName, Screen screen,
                                                      ScreenElement element,
                                                      GraphLimits limits) {
        // Taille : sous le minimum, un élément ne se clique plus et ne se rattrape
        // plus dans le concepteur — il devient un piège.
        if (element.width().resolve(Screen.SAFE_WIDTH) < ScreenElement.MIN_SIZE
                || element.height().resolve(Screen.SAFE_HEIGHT) < ScreenElement.MIN_SIZE) {
            return Diagnostic.error(DiagnosticCode.ELEMENT_TOO_SMALL,
                    Diagnostic.element(screenName, element.name()),
                    element.name(), (int) ScreenElement.MIN_SIZE);
        }

        // Un HUD ne capte pas la souris (story 10.9) : un bouton y serait un leurre.
        if (screen.hud() && element.kind().interactive()) {
            return Diagnostic.error(DiagnosticCode.INTERACTIVE_IN_HUD,
                    Diagnostic.element(screenName, element.name()),
                    element.name(), element.kind().name());
        }

        String parent = element.parent();
        if (parent == null) {
            return null;
        }
        if (parent.equals(element.name())) {
            return Diagnostic.error(DiagnosticCode.ELEMENT_PARENT_CYCLE,
                    Diagnostic.element(screenName, element.name()), element.name());
        }
        ScreenElement container = screen.element(parent);
        if (container == null) {
            return Diagnostic.error(DiagnosticCode.ELEMENT_PARENT_NOT_FOUND,
                    Diagnostic.element(screenName, element.name()), element.name(), parent);
        }
        if (!container.kind().container()) {
            return Diagnostic.error(DiagnosticCode.ELEMENT_NOT_CONTAINER,
                    Diagnostic.element(screenName, element.name()), parent,
                    container.kind().name());
        }
        // Un cycle de parenté rendrait toute la branche invisible et impossible à
        // atteindre : ni le rendu ni le concepteur ne sauraient par où commencer.
        if (createsCycle(screen, element)) {
            return Diagnostic.error(DiagnosticCode.ELEMENT_PARENT_CYCLE,
                    Diagnostic.element(screenName, element.name()), element.name());
        }
        return null;
    }

    /** Remonter la chaîne de parenté depuis l'élément : y revenir = cycle. */
    private static boolean createsCycle(Screen screen, ScreenElement element) {
        Set<String> seen = new HashSet<>();
        seen.add(element.name());
        String cursor = element.parent();
        while (cursor != null) {
            if (!seen.add(cursor)) {
                return true;
            }
            ScreenElement up = screen.element(cursor);
            cursor = up == null ? null : up.parent();
        }
        return false;
    }

    /**
     * Un élément déborde-t-il de la zone garantie ? <b>Avertissement</b>, jamais
     * erreur : un menu conçu large reste valide, mais son auteur doit apprendre à la
     * conception qu'une partie de ses joueurs ne le verra pas entier — plutôt que par
     * un rapport de bug.
     */
    public static boolean outsideSafeArea(ScreenElement element) {
        double right = element.x() + element.width().resolve(Screen.SAFE_WIDTH);
        double bottom = element.y() + element.height().resolve(Screen.SAFE_HEIGHT);
        return right > Screen.SAFE_WIDTH || bottom > Screen.SAFE_HEIGHT
                || element.x() < 0 || element.y() < 0;
    }
}
