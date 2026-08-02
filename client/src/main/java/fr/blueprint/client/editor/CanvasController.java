package fr.blueprint.client.editor;

import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.Node;
import fr.blueprint.core.graph.NodeTypeLookup;
import fr.blueprint.core.graph.Vec2d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La logique d'interaction du canevas, en coordonnées monde et sans Minecraft :
 * hit-test, sélection (clic, Shift, rectangle élastique), déplacement de la sélection
 * avec accroche, suppression. Le widget ne fait que convertir écran→monde et dessiner.
 *
 * <p>Toute mutation du graphe passe par des {@link EditOperation} ; les inverses sont
 * collectés dans l'ordre d'application pour la pile d'annulation de la story 5.6.
 */
public final class CanvasController {

    public enum Gesture { NONE, MOVE, RUBBER }

    private final Blueprint blueprint;
    private final NodeTypeLookup lookup;
    private final Camera camera;
    private final NodeGeometry geometry = new NodeGeometry();
    private final SelectionModel selection = new SelectionModel();
    private final List<EditOperation> inverses = new ArrayList<>();
    /** Offsets « position du nœud − point de saisie », figés à la presse. */
    private final Map<UUID, Vec2d> dragOffsets = new HashMap<>();

    private Gesture gesture = Gesture.NONE;
    private double rubberStartX;
    private double rubberStartY;
    private double rubberEndX;
    private double rubberEndY;

    public CanvasController(Blueprint blueprint, NodeTypeLookup lookup, Camera camera) {
        this.blueprint = blueprint;
        this.lookup = lookup;
        this.camera = camera;
    }

    public Blueprint blueprint() {
        return blueprint;
    }

    public SelectionModel selection() {
        return selection;
    }

    public Gesture gesture() {
        return gesture;
    }

    /** Boîtes monde des nœuds (cache invalidé par la révision du graphe). */
    public List<NodeGeometry.Box> boxes() {
        return geometry.boxes(blueprint, lookup);
    }

    /**
     * Le nœud sous le point donné, ou null. Parcourt en sens inverse de l'ordre de
     * dessin pour attraper le nœud rendu au-dessus.
     */
    public @Nullable NodeGeometry.Box hitTest(double wx, double wy) {
        List<NodeGeometry.Box> boxes = boxes();
        for (int i = boxes.size() - 1; i >= 0; i--) {
            NodeGeometry.Box b = boxes.get(i);
            if (b.contains(wx, wy)) {
                return b;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------- gestes

    /** Presse du bouton gauche en coordonnées monde. */
    public void press(double wx, double wy, boolean additive) {
        NodeGeometry.Box hit = hitTest(wx, wy);
        if (hit == null) {
            if (!additive) {
                selection.clear();
            }
            gesture = Gesture.RUBBER;
            rubberStartX = rubberEndX = wx;
            rubberStartY = rubberEndY = wy;
            return;
        }
        UUID id = hit.node().uuid();
        selection.click(id, additive);
        dragOffsets.clear();
        for (UUID sel : selection.ids()) {
            Node node = blueprint.node(sel);
            if (node != null) {
                dragOffsets.put(sel, new Vec2d(node.position().x() - wx, node.position().y() - wy));
            }
        }
        gesture = dragOffsets.isEmpty() ? Gesture.NONE : Gesture.MOVE;
    }

    public void drag(double wx, double wy) {
        switch (gesture) {
            case RUBBER -> {
                rubberEndX = wx;
                rubberEndY = wy;
            }
            case MOVE -> {
                // Cibler « accroche(saisie + offset) » à chaque glissement : pas de
                // deltas incrémentaux, l'accroche accumulerait les erreurs d'arrondi.
                for (Map.Entry<UUID, Vec2d> e : dragOffsets.entrySet()) {
                    Node node = blueprint.node(e.getKey());
                    if (node == null) {
                        continue;
                    }
                    Vec2d target = camera.snap(new Vec2d(wx + e.getValue().x(), wy + e.getValue().y()));
                    if (!target.equals(node.position())) {
                        apply(new EditOperation.MoveNode(e.getKey(), target));
                    }
                }
            }
            case NONE -> {
            }
        }
    }

    /** Relâche du bouton gauche ; termine le rectangle élastique le cas échéant. */
    public void release(boolean additive) {
        if (gesture == Gesture.RUBBER) {
            Camera.Rect rect = rubberRect();
            List<UUID> hits = new ArrayList<>();
            List<NodeGeometry.Box> boxes = boxes();
            for (int i = 0; i < boxes.size(); i++) {
                NodeGeometry.Box b = boxes.get(i);
                if (rect != null && rect.intersects(b.x(), b.y(), b.width(), b.height())) {
                    hits.add(b.node().uuid());
                }
            }
            // La presse a déjà vidé la sélection si non additif : on ajoute toujours.
            selection.selectAll(hits, true);
        }
        gesture = Gesture.NONE;
    }

    /** Rectangle élastique courant (normalisé), ou null hors geste. */
    public @Nullable Camera.Rect rubberRect() {
        if (gesture != Gesture.RUBBER) {
            return null;
        }
        return new Camera.Rect(
                Math.min(rubberStartX, rubberEndX), Math.min(rubberStartY, rubberEndY),
                Math.max(rubberStartX, rubberEndX), Math.max(rubberStartY, rubberEndY));
    }

    /** Supprime la sélection ; l'opération retire aussi les liens touchés. */
    public void deleteSelection() {
        for (UUID id : List.copyOf(selection.ids())) {
            apply(new EditOperation.RemoveNode(id));
        }
        selection.clear();
    }

    /** Inverses collectés, dans l'ordre d'application — la 5.6 en fera la pile d'annulation. */
    public List<EditOperation> inverses() {
        return inverses;
    }

    private void apply(EditOperation op) {
        EditOperation.Result result = op.apply(blueprint, lookup);
        if (result.applied() && result.inverse() != null) {
            inverses.add(result.inverse());
        }
    }
}
