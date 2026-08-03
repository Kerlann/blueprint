package fr.blueprint.core.graph;

import fr.blueprint.api.pin.LiteralValue;
import org.jetbrains.annotations.Nullable;

/**
 * Porte de chargement du modèle, réservée aux désérialiseurs (NBT dans le paquet,
 * BScript dans {@code core.script}) : le chargement ne refuse rien (P4) et ne passe
 * donc pas par les {@link EditOperation}. Toute ÉDITION passe par les opérations —
 * cette classe n'est jamais un raccourci d'édition.
 */
public final class GraphLoader {

    private GraphLoader() {
    }

    public static void addNode(Blueprint bp, Node node) {
        bp.putNode(node);
    }

    public static void addLink(Blueprint bp, Link link) {
        bp.putLink(link);
    }

    public static void addVariable(Blueprint bp, Variable variable) {
        bp.putVariable(variable);
    }

    public static void addComment(Blueprint bp, CommentBox comment) {
        bp.putComment(comment);
    }

    public static void addScreen(Blueprint bp, fr.blueprint.core.graph.screen.Screen screen) {
        bp.putScreen(screen);
    }

    public static void setLiteral(Node node, String pin, @Nullable LiteralValue value) {
        node.setLiteral(pin, value);
    }
}
