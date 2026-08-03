package fr.blueprint.client.editor;

import fr.blueprint.api.pin.PinKind;
import fr.blueprint.api.pin.PinType;
import fr.blueprint.client.editor.history.UndoStack;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.GraphValidator;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.Node;
import fr.blueprint.core.graph.NodeShape;
import fr.blueprint.core.graph.NodeTypeLookup;
import fr.blueprint.core.graph.Vec2d;
import net.minecraft.resources.Identifier;
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

    public enum Gesture { NONE, MOVE, RUBBER, WIRE }

    /** Rayon de saisie d'un pin, en unités monde. */
    public static final double PIN_HIT_RADIUS = 6;

    /** Un pin identifié sur un nœud : côté, rangée, et son type pour la compatibilité. */
    public record PinRef(UUID node, String pin, PinKind kind, PinType type,
                         boolean output, int row) {
    }

    /** Relâche d'un lien dans le vide : la palette s'ouvre filtrée (5.4a). */
    public record WireDrop(double worldX, double worldY, PinRef from) {
    }

    /** Zone littérale d'un pin d'entrée data non câblé (5.2b). */
    public record LiteralRef(UUID node, String pin, PinType type, int row) {
    }

    private final Blueprint blueprint;
    private final NodeTypeLookup lookup;
    private final Camera camera;
    private final NodeGeometry geometry = new NodeGeometry();
    private final SelectionModel selection = new SelectionModel();
    private final UndoStack history = new UndoStack();
    /** Offsets « position du nœud − point de saisie », figés à la presse. */
    private final Map<UUID, Vec2d> dragOffsets = new HashMap<>();

    /** Notifié après chaque mutation appliquée (undo/redo compris) — débouncé 5.6b. */
    private @Nullable Runnable onMutation;

    private Gesture gesture = Gesture.NONE;
    private double rubberStartX;
    private double rubberStartY;
    private double rubberEndX;
    private double rubberEndY;
    private @Nullable PinRef wireFrom;
    private double wireX;
    private double wireY;
    /** Index boîte par nœud, reconstruit avec le cache de géométrie. */
    private final Map<UUID, NodeGeometry.Box> boxIndex = new HashMap<>();
    private int boxIndexRevision = -1;

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

    /** Boîte d'un nœud par identifiant (index reconstruit à la révision). */
    public @Nullable NodeGeometry.Box boxOf(UUID node) {
        List<NodeGeometry.Box> boxes = boxes();
        if (blueprint.revision() != boxIndexRevision) {
            boxIndexRevision = blueprint.revision();
            boxIndex.clear();
            for (int i = 0; i < boxes.size(); i++) {
                boxIndex.put(boxes.get(i).node().uuid(), boxes.get(i));
            }
        }
        return boxIndex.get(node);
    }

    // --------------------------------------------------------------------- pins

    /** Le pin sous le point donné (rayon {@link #PIN_HIT_RADIUS}), ou null. */
    public @Nullable PinRef pinAt(double wx, double wy) {
        List<NodeGeometry.Box> boxes = boxes();
        double r2 = PIN_HIT_RADIUS * PIN_HIT_RADIUS;
        for (int i = boxes.size() - 1; i >= 0; i--) {
            NodeGeometry.Box b = boxes.get(i);
            NodeShape shape = lookup.shape(b.node().typeId());
            if (shape == null) {
                continue; // fantôme : pas de câblage tant que la forme est inconnue
            }
            for (int row = 0; row < shape.inputs().size(); row++) {
                if (dist2(NodeGeometry.inputPinCenter(b, row), wx, wy) <= r2) {
                    NodeShape.PinDef def = shape.inputs().get(row);
                    return new PinRef(b.node().uuid(), def.name(), def.kind(), def.type(), false, row);
                }
            }
            for (int row = 0; row < shape.outputs().size(); row++) {
                if (dist2(NodeGeometry.outputPinCenter(b, row), wx, wy) <= r2) {
                    NodeShape.PinDef def = shape.outputs().get(row);
                    return new PinRef(b.node().uuid(), def.name(), def.kind(), def.type(), true, row);
                }
            }
        }
        return null;
    }

    /** Centre monde d'un pin nommé, ou null (nœud absent, fantôme, pin inconnu). */
    public @Nullable Vec2d pinCenter(UUID node, String pin) {
        NodeGeometry.Box box = boxOf(node);
        Node n = blueprint.node(node);
        if (box == null || n == null) {
            return null;
        }
        NodeShape shape = lookup.shape(n.typeId());
        if (shape == null) {
            return null;
        }
        for (int row = 0; row < shape.inputs().size(); row++) {
            if (shape.inputs().get(row).name().equals(pin)) {
                return NodeGeometry.inputPinCenter(box, row);
            }
        }
        for (int row = 0; row < shape.outputs().size(); row++) {
            if (shape.outputs().get(row).name().equals(pin)) {
                return NodeGeometry.outputPinCenter(box, row);
            }
        }
        return null;
    }

    /** Définition d'un pin nommé (pour la couleur des liens), ou null. */
    public @Nullable NodeShape.PinDef pinDef(UUID node, String pin) {
        Node n = blueprint.node(node);
        NodeShape shape = n == null ? null : lookup.shape(n.typeId());
        if (shape == null) {
            return null;
        }
        NodeShape.PinDef def = shape.output(pin);
        return def != null ? def : shape.input(pin);
    }

    /**
     * Le lien hypothétique depuis {@code from} vers ce pin passerait-il ?
     * Délégué à {@code GraphValidator.canLink} — la source de vérité du câblage.
     */
    public boolean canConnect(PinRef from, UUID node, String pin, boolean pinIsOutput) {
        if (from.output() == pinIsOutput) {
            return false;
        }
        return GraphValidator.canLink(blueprint, lookup, buildLink(from, node, pin)) == null;
    }

    private static Link buildLink(PinRef from, UUID node, String pin) {
        return from.output()
                ? new Link(from.node(), from.pin(), node, pin)
                : new Link(node, pin, from.node(), from.pin());
    }

    private static double dist2(Vec2d p, double wx, double wy) {
        double dx = p.x() - wx;
        double dy = p.y() - wy;
        return dx * dx + dy * dy;
    }

    // ----------------------------------------------------------------- littéraux

    /**
     * La zone littérale sous le point donné : pin d'entrée **data non câblé** du
     * nœud au-dessus, ou null. Un pin câblé masque son littéral (le lien prime).
     */
    public @Nullable LiteralRef literalAt(double wx, double wy) {
        NodeGeometry.Box b = hitTest(wx, wy);
        if (b == null) {
            return null;
        }
        NodeShape shape = lookup.shape(b.node().typeId());
        if (shape == null) {
            return null;
        }
        for (int row = 0; row < shape.inputs().size(); row++) {
            NodeShape.PinDef def = shape.inputs().get(row);
            if (def.kind() != PinKind.DATA) {
                continue;
            }
            Camera.Rect zone = NodeGeometry.literalZone(b, row);
            if (wx >= zone.left() && wx < zone.right() && wy >= zone.top() && wy < zone.bottom()
                    && blueprint.linksInto(b.node().uuid(), def.name()).isEmpty()) {
                return new LiteralRef(b.node().uuid(), def.name(), def.type(), row);
            }
        }
        return null;
    }

    /** Applique un littéral via {@code SetLiteral} (revalidé côté modèle, inverse collecté). */
    public boolean setLiteral(UUID node, String pin, @Nullable fr.blueprint.api.pin.LiteralValue value) {
        return applyTracked(new EditOperation.SetLiteral(node, pin, value));
    }

    // ------------------------------------------------------------------- gestes

    /** Presse du bouton gauche en coordonnées monde. */
    public void press(double wx, double wy, boolean additive) {
        press(wx, wy, additive, false);
    }

    /** Presse du bouton gauche ; Alt+clic sur un pin câblé détache ses liens. */
    public void press(double wx, double wy, boolean additive, boolean alt) {
        // Tout ce qui se passe entre presse et relâche = un geste = une annulation.
        history.beginGesture();
        PinRef pin = pinAt(wx, wy);
        if (pin != null) {
            if (alt) {
                detach(pin);
                gesture = Gesture.NONE;
                return;
            }
            wireFrom = pin;
            wireX = wx;
            wireY = wy;
            gesture = Gesture.WIRE;
            return;
        }
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
            case WIRE -> {
                wireX = wx;
                wireY = wy;
            }
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

    /**
     * Relâche du bouton gauche. Retourne un {@link WireDrop} si un lien a été lâché
     * dans le vide — le widget y ouvre la palette filtrée (5.4a).
     */
    public @Nullable WireDrop release(boolean additive) {
        try {
            return doRelease(additive);
        } finally {
            history.endGesture();
        }
    }

    private @Nullable WireDrop doRelease(boolean additive) {
        if (gesture == Gesture.WIRE && wireFrom != null) {
            PinRef from = wireFrom;
            wireFrom = null;
            gesture = Gesture.NONE;
            PinRef target = pinAt(wireX, wireY);
            if (target == null) {
                return new WireDrop(wireX, wireY, from);
            }
            if (from.output() != target.output()) {
                // canLink refuse types, cardinalité, cycles : rien à dupliquer ici.
                apply(new EditOperation.AddLink(buildLink(from, target.node(), target.pin())));
            }
            return null;
        }
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
        return null;
    }

    /** Pin d'origine du lien en cours de tracé, ou null hors geste. */
    public @Nullable PinRef wireFrom() {
        return gesture == Gesture.WIRE ? wireFrom : null;
    }

    public double wireCursorX() {
        return wireX;
    }

    public double wireCursorY() {
        return wireY;
    }

    /** Détache tous les liens touchant ce pin (Alt+clic). */
    private void detach(PinRef pin) {
        List<Link> links = pin.output()
                ? blueprint.linksFrom(pin.node(), pin.pin())
                : blueprint.linksInto(pin.node(), pin.pin());
        for (Link link : List.copyOf(links)) {
            apply(new EditOperation.RemoveLink(link));
        }
    }

    /**
     * Insère un nœud (palette) à la position monde donnée, accrochée ; si un pin
     * source est fourni, connecte le premier pin compatible — validé par
     * {@code canLink} maintenant que le nœud existe.
     */
    public @Nullable UUID insertNode(Identifier typeId, double wx, double wy, @Nullable PinRef from) {
        history.beginGesture();
        try {
            UUID id = UUID.randomUUID();
            Vec2d pos = camera.snap(new Vec2d(wx, wy));
            if (!applyTracked(new EditOperation.AddNode(id, typeId, pos))) {
                return null;
            }
            if (from != null) {
                NodeShape shape = lookup.shape(typeId);
                if (shape != null) {
                    List<NodeShape.PinDef> candidates = from.output() ? shape.inputs() : shape.outputs();
                    for (int i = 0; i < candidates.size(); i++) {
                        if (applyTracked(new EditOperation.AddLink(
                                buildLink(from, id, candidates.get(i).name())))) {
                            break;
                        }
                    }
                }
            }
            return id;
        } finally {
            history.endGesture();
        }
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

    /** Supprime la sélection (une entrée d'annulation) ; les liens touchés partent avec. */
    public void deleteSelection() {
        history.beginGesture();
        try {
            for (UUID id : List.copyOf(selection.ids())) {
                apply(new EditOperation.RemoveNode(id));
            }
        } finally {
            history.endGesture();
        }
        selection.clear();
    }

    /** La pile d'annulation (5.6a) : toutes les mutations y naissent réversibles. */
    public UndoStack history() {
        return history;
    }

    /** Le canal de mutation unique pour les panneaux (variables, détails…). */
    public boolean applyOp(EditOperation op) {
        return applyTracked(op);
    }

    /**
     * Colle un fragment (5.8) : variables manquantes créées, nœuds remappés vers des
     * UUID neufs, positions décalées en bloc (coin haut-gauche sous le curseur,
     * accroché), liens internes reconstruits. La nouvelle sélection = les nœuds
     * collés. Un seul geste d'annulation.
     */
    public List<UUID> pasteFragment(Blueprint fragment, double wx, double wy) {
        if (fragment.nodes().isEmpty() && fragment.variables().isEmpty()) {
            return List.of();
        }
        history.beginGesture();
        try {
            for (fr.blueprint.core.graph.Variable variable : fragment.variables().values()) {
                if (!blueprint.variables().containsKey(variable.name())) {
                    applyTracked(new EditOperation.AddVariable(variable));
                }
            }
            if (fragment.nodes().isEmpty()) {
                return List.of();
            }
            double minX = Double.MAX_VALUE;
            double minY = Double.MAX_VALUE;
            for (Node node : fragment.nodes().values()) {
                minX = Math.min(minX, node.position().x());
                minY = Math.min(minY, node.position().y());
            }
            double ox = camera.snap(wx) - minX;
            double oy = camera.snap(wy) - minY;

            Map<UUID, UUID> remap = new HashMap<>();
            List<UUID> pasted = new ArrayList<>();
            for (Node node : fragment.nodes().values()) {
                UUID fresh = UUID.randomUUID();
                remap.put(node.uuid(), fresh);
                if (!applyTracked(new EditOperation.AddNode(fresh, node.typeId(),
                        new Vec2d(node.position().x() + ox, node.position().y() + oy)))) {
                    continue;
                }
                pasted.add(fresh);
                node.literals().forEach((pin, value) ->
                        applyTracked(new EditOperation.SetLiteral(fresh, pin, value)));
            }
            for (Link link : fragment.links()) {
                UUID from = remap.get(link.fromNode());
                UUID to = remap.get(link.toNode());
                if (from != null && to != null) {
                    applyTracked(new EditOperation.AddLink(
                            new Link(from, link.fromPin(), to, link.toPin())));
                }
            }
            selection.selectAll(pasted, false);
            return pasted;
        } finally {
            history.endGesture();
        }
    }

    /** Dépose un nœud Get (ou Set) lié à la variable, accroché — un seul geste. */
    public @Nullable UUID insertVariableNode(boolean set, String name, double wx, double wy) {
        history.beginGesture();
        try {
            UUID id = UUID.randomUUID();
            Identifier type = set ? fr.blueprint.core.graph.VarNodes.SET
                    : fr.blueprint.core.graph.VarNodes.GET;
            if (!applyTracked(new EditOperation.AddNode(id, type, camera.snap(new Vec2d(wx, wy))))) {
                return null;
            }
            applyTracked(new EditOperation.SetLiteral(id, fr.blueprint.core.graph.VarNodes.VAR_PIN,
                    fr.blueprint.api.pin.LiteralValue.of(fr.blueprint.api.pin.PinTypes.STRING, name)));
            return id;
        } finally {
            history.endGesture();
        }
    }

    public void setOnMutation(@Nullable Runnable onMutation) {
        this.onMutation = onMutation;
    }

    public boolean undo() {
        boolean done = history.undo(blueprint, lookup);
        if (done) {
            notifyMutation();
        }
        return done;
    }

    public boolean redo() {
        boolean done = history.redo(blueprint, lookup);
        if (done) {
            notifyMutation();
        }
        return done;
    }

    /** Recentre la caméra sur un nœud et le sélectionne (clic sur un diagnostic). */
    public boolean focusNode(UUID node, double screenW, double screenH) {
        NodeGeometry.Box box = boxOf(node);
        if (box == null) {
            return false;
        }
        selection.selectAll(List.of(node), false);
        camera.centerOn(box.x() + box.width() / 2, box.y() + box.height() / 2, screenW, screenH);
        return true;
    }

    private void notifyMutation() {
        if (onMutation != null) {
            onMutation.run();
        }
    }

    private void apply(EditOperation op) {
        applyTracked(op);
    }

    private boolean applyTracked(EditOperation op) {
        EditOperation.Result result = op.apply(blueprint, lookup);
        if (result.applied()) {
            if (result.inverse() != null) {
                history.record(result.inverse());
            }
            notifyMutation();
        }
        return result.applied();
    }
}
