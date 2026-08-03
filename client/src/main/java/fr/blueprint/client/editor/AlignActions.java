package fr.blueprint.client.editor;

import fr.blueprint.core.graph.Vec2d;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Alignement et distribution de la sélection (story 5.7, touche Q) : l'axe dominant
 * de la boîte englobante décide — plus haute que large → colonne alignée à gauche et
 * distribuée verticalement ; plus large que haute → rangée alignée en haut. Pur.
 */
public final class AlignActions {

    /** Espacement minimal entre deux nœuds distribués. */
    public static final double GAP = 24;

    private AlignActions() {
    }

    /** Positions cibles (l'ordre relatif des nœuds est préservé). */
    public static Map<UUID, Vec2d> align(List<NodeGeometry.Box> selection) {
        if (selection.size() < 2) {
            return Map.of();
        }
        Camera.Rect bounds = NodeGeometry.boundsOf(selection);
        boolean vertical = bounds.bottom() - bounds.top() >= bounds.right() - bounds.left();

        Map<UUID, Vec2d> out = new LinkedHashMap<>();
        if (vertical) {
            List<NodeGeometry.Box> ordered = selection.stream()
                    .sorted(Comparator.comparingDouble(NodeGeometry.Box::y)
                            .thenComparing(b -> b.node().uuid().toString()))
                    .toList();
            double y = bounds.top();
            for (NodeGeometry.Box box : ordered) {
                out.put(box.node().uuid(), new Vec2d(bounds.left(), y));
                y += box.height() + GAP;
            }
        } else {
            List<NodeGeometry.Box> ordered = selection.stream()
                    .sorted(Comparator.comparingDouble(NodeGeometry.Box::x)
                            .thenComparing(b -> b.node().uuid().toString()))
                    .toList();
            double x = bounds.left();
            for (NodeGeometry.Box box : ordered) {
                out.put(box.node().uuid(), new Vec2d(x, bounds.top()));
                x += box.width() + GAP;
            }
        }
        return out;
    }
}
