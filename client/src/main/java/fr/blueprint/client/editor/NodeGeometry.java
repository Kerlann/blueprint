package fr.blueprint.client.editor;

import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.Node;
import fr.blueprint.core.graph.NodeShape;
import fr.blueprint.core.graph.NodeTypeLookup;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Boîtes monde des nœuds d'un blueprint, dérivées de leur forme (les nœuds n'ont pas
 * de taille persistée). Suffisant pour le culling et les boîtes de niveau de détail
 * de la story 5.1 ; la 5.2 raffinera avec les littéraux inline.
 *
 * <p>Le calcul est mis en cache et invalidé par la révision du blueprint : la passe
 * de rendu ne reconstruit rien tant que le graphe ne change pas (coding-standards §5).
 */
public final class NodeGeometry {

    public static final double WIDTH = 140;
    public static final double TITLE_HEIGHT = 18;
    public static final double ROW_HEIGHT = 12;

    /** Rangées présumées d'un nœud fantôme (forme inconnue). */
    private static final int GHOST_ROWS = 3;

    private final List<Box> boxes = new ArrayList<>();
    private int revision = -1;

    /** La liste retournée est réutilisée d'un appel à l'autre : ne pas la conserver. */
    public List<Box> boxes(Blueprint bp, NodeTypeLookup lookup) {
        if (bp.revision() != revision) {
            revision = bp.revision();
            boxes.clear();
            for (Node node : bp.nodes().values()) {
                boxes.add(boxOf(node, lookup.shape(node.typeId())));
            }
        }
        return boxes;
    }

    public static Box boxOf(Node node, @Nullable NodeShape shape) {
        int rows = shape == null
                ? GHOST_ROWS
                : Math.max(1, Math.max(shape.inputs().size(), shape.outputs().size()));
        return new Box(node, node.position().x(), node.position().y(),
                WIDTH, TITLE_HEIGHT + rows * ROW_HEIGHT, shape == null);
    }

    /** Boîte englobante d'un ensemble de boîtes — vide → rectangle nul à l'origine. */
    public static Camera.Rect boundsOf(List<Box> boxes) {
        if (boxes.isEmpty()) {
            return new Camera.Rect(0, 0, 0, 0);
        }
        double left = Double.MAX_VALUE;
        double top = Double.MAX_VALUE;
        double right = -Double.MAX_VALUE;
        double bottom = -Double.MAX_VALUE;
        for (int i = 0; i < boxes.size(); i++) {
            Box b = boxes.get(i);
            left = Math.min(left, b.x());
            top = Math.min(top, b.y());
            right = Math.max(right, b.x() + b.width());
            bottom = Math.max(bottom, b.y() + b.height());
        }
        return new Camera.Rect(left, top, right, bottom);
    }

    /** Boîte monde d'un nœud ; {@code ghost} = type inconnu du registre. */
    public record Box(Node node, double x, double y, double width, double height, boolean ghost) {
    }
}
