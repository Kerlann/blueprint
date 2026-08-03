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

    public enum Gesture { NONE, MOVE, RUBBER, WIRE, MOVE_COMMENT, RESIZE_COMMENT }

    /** Hauteur de la barre de titre d'un commentaire, en unités monde. */
    public static final double COMMENT_TITLE = 14;
    /** Poignée de redimensionnement (coin bas-droit). */
    public static final double COMMENT_GRIP = 12;

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
    /** Lien sélectionné (Suppr le retire) : cliquer un fil était le geste sans effet. */
    private @Nullable Link selectedLink;
    /** Dernier refus non encore montré au joueur (voir {@link #takeRefusal()}). */
    private fr.blueprint.core.graph.@Nullable Diagnostic lastRefusal;
    /** Commentaire sélectionné (Suppr le retire) et geste en cours sur lui. */
    private @Nullable UUID selectedComment;
    private @Nullable UUID activeComment;
    private double commentGrabX;
    private double commentGrabY;
    private final Map<UUID, Vec2d> commentNodeOffsets = new HashMap<>();
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
            // Ce nœud est le plus haut sous le point et n'y a pas de pin : ceux des
            // nœuds DESSOUS ne se saisissent pas à travers lui. (Le rayon de saisie
            // déborde des boîtes, donc hors de toute boîte on continue de chercher.)
            if (b.contains(wx, wy)) {
                return null;
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
            // Champ large quand la rangée n'a pas de sortie en face (voir literalZone).
            Camera.Rect zone = NodeGeometry.literalZone(b, row, row < shape.outputs().size());
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
        // Toute presse défait la sélection de fil ; seule la branche « fil » la repose.
        selectedLink = null;
        PinRef pin = pinAt(wx, wy);
        if (pin != null) {
            if (alt) {
                detach(pin);
                gesture = Gesture.NONE;
                // Le widget ne rappellera pas release() (geste NONE) : fermer ici,
                // sinon le détachement fusionnerait avec le geste suivant (QA 5.3).
                history.endGesture();
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
            // Les commentaires vivent SOUS les nœuds : testés après eux (5.7).
            fr.blueprint.core.graph.CommentBox grip = commentGripAt(wx, wy);
            if (grip != null) {
                selectedComment = grip.uuid();
                activeComment = grip.uuid();
                selection.clear();
                gesture = Gesture.RESIZE_COMMENT;
                return;
            }
            fr.blueprint.core.graph.CommentBox title = commentTitleAt(wx, wy);
            if (title != null) {
                selectedComment = title.uuid();
                activeComment = title.uuid();
                selection.clear();
                commentGrabX = title.position().x() - wx;
                commentGrabY = title.position().y() - wy;
                commentNodeOffsets.clear();
                // Déplacer la boîte déplace les nœuds qu'elle contient (centre dedans).
                for (NodeGeometry.Box b : boxes()) {
                    double cx = b.x() + b.width() / 2;
                    double cy = b.y() + b.height() / 2;
                    if (cx >= title.position().x() && cx < title.position().x() + title.size().x()
                            && cy >= title.position().y() && cy < title.position().y() + title.size().y()) {
                        commentNodeOffsets.put(b.node().uuid(),
                                new Vec2d(b.x() - wx, b.y() - wy));
                    }
                }
                gesture = Gesture.MOVE_COMMENT;
                return;
            }
            selectedComment = null;
            // Après les nœuds et les poignées de commentaire, avant l'élastique : un
            // clic sur un fil sélectionnait le vide et ne faisait rien.
            Link wire = linkAt(wx, wy);
            if (wire != null) {
                selectedLink = wire;
                selection.clear();
                gesture = Gesture.NONE;
                // Geste NONE : le widget ne rappellera pas release(). Refermer ici,
                // sinon toutes les modifications suivantes fusionneraient en une
                // seule annulation (même piège qu'Alt+clic, QA 5.3).
                history.endGesture();
                return;
            }
            selectedLink = null;
            if (!additive) {
                selection.clear();
            }
            gesture = Gesture.RUBBER;
            rubberStartX = rubberEndX = wx;
            rubberStartY = rubberEndY = wy;
            return;
        }
        selectedComment = null;
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
            case MOVE_COMMENT -> {
                fr.blueprint.core.graph.CommentBox box = activeComment == null ? null
                        : blueprint.comment(activeComment);
                if (box == null) {
                    return;
                }
                Vec2d target = camera.snap(new Vec2d(wx + commentGrabX, wy + commentGrabY));
                if (!target.equals(box.position())) {
                    apply(new EditOperation.EditComment(new fr.blueprint.core.graph.CommentBox(
                            box.uuid(), box.text(), target, box.size(), box.color())));
                }
                for (Map.Entry<UUID, Vec2d> e : commentNodeOffsets.entrySet()) {
                    Node node = blueprint.node(e.getKey());
                    if (node == null) {
                        continue;
                    }
                    Vec2d nodeTarget = camera.snap(new Vec2d(wx + e.getValue().x(), wy + e.getValue().y()));
                    if (!nodeTarget.equals(node.position())) {
                        apply(new EditOperation.MoveNode(e.getKey(), nodeTarget));
                    }
                }
            }
            case RESIZE_COMMENT -> {
                fr.blueprint.core.graph.CommentBox box = activeComment == null ? null
                        : blueprint.comment(activeComment);
                if (box == null) {
                    return;
                }
                Vec2d size = new Vec2d(Math.max(60, wx - box.position().x()),
                        Math.max(40, wy - box.position().y()));
                if (!size.equals(box.size())) {
                    apply(new EditOperation.EditComment(new fr.blueprint.core.graph.CommentBox(
                            box.uuid(), box.text(), box.position(), size, box.color())));
                }
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
                updateSpliceCandidate();
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
        if (gesture == Gesture.MOVE && spliceCandidate != null) {
            // Toujours dans le geste du déplacement : couper le fil et se recâbler
            // font partie du même Ctrl+Z que le déplacement qui l'a provoqué.
            spliceOn(spliceCandidate, selection.ids().iterator().next());
            spliceCandidate = null;
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

    // ------------------------------------------------------------- liens (5.12)

    /** Tolérance de clic sur un fil, en pixels écran : plus fin ne s'attrape pas. */
    private static final double LINK_HIT_PX = 5;

    /**
     * Le lien sous le point donné, ou null. Le test se fait en <b>espace écran</b> :
     * la courbe elle-même y est définie (ses tangentes sont bornées en pixels), et la
     * tolérance reste constante quel que soit le zoom.
     */
    public @Nullable Link linkAt(double wx, double wy) {
        double px = camera.toScreenX(wx);
        double py = camera.toScreenY(wy);
        Link best = null;
        double bestDist = LINK_HIT_PX;
        for (Link link : blueprint.links()) {
            Vec2d from = WireLayer.endpoint(this, link.fromNode(), link.fromPin(), true);
            Vec2d to = WireLayer.endpoint(this, link.toNode(), link.toPin(), false);
            if (from == null || to == null) {
                continue;
            }
            double dist = WireLayer.distanceToCurve(
                    camera.toScreenX(from.x()), camera.toScreenY(from.y()), 1,
                    camera.toScreenX(to.x()), camera.toScreenY(to.y()), -1, px, py);
            if (dist < bestDist) {
                bestDist = dist;
                best = link;
            }
        }
        return best;
    }

    public @Nullable Link selectedLink() {
        return selectedLink;
    }

    // ------------------------------------------ insertion sur un fil (5.13, UE5)

    /** Le fil que le nœud déplacé viendrait couper, ou null. Mis à jour au glissement. */
    private @Nullable Link spliceCandidate;

    public @Nullable Link spliceCandidate() {
        return spliceCandidate;
    }

    /**
     * Le nœud traîné recouvre-t-il un fil qu'il pourrait couper ? Recalculé à chaque
     * glissement, mais le test est borné : un seul nœud sélectionné, un seul test de
     * fil au centre de sa boîte, et un appariement de pins sur des listes courtes.
     */
    private void updateSpliceCandidate() {
        spliceCandidate = null;
        if (selection.size() != 1) {
            return; // insérer une sélection entière n'aurait pas de sens
        }
        UUID id = selection.ids().iterator().next();
        NodeGeometry.Box box = boxOf(id);
        if (box == null) {
            return;
        }
        Link hit = linkAt(box.x() + box.width() / 2, box.y() + box.height() / 2);
        if (hit == null || hit.fromNode().equals(id) || hit.toNode().equals(id)) {
            return; // un nœud ne se réinsère pas sur son propre fil
        }
        if (spliceTargets(hit, id) != null) {
            spliceCandidate = hit;
        }
    }

    /**
     * Les deux pins par lesquels {@code node} traverserait ce fil : {in, out}, ou
     * null s'il ne peut pas le porter.
     *
     * <p>Le test est structurel (genre + assignabilité) plutôt qu'un appel à
     * {@code canLink} : la moitié aval serait refusée pour cardinalité tant que le
     * fil d'origine existe. {@link #spliceOn} repasse ensuite par {@code canLink},
     * qui reste la source de vérité — ceci n'en est que la présélection.
     */
    private String @Nullable [] spliceTargets(Link link, UUID node) {
        Node n = blueprint.node(node);
        NodeShape shape = n == null ? null : lookup.shape(n.typeId());
        NodeShape.PinDef source = pinDef(link.fromNode(), link.fromPin());
        NodeShape.PinDef sink = pinDef(link.toNode(), link.toPin());
        if (shape == null || source == null || sink == null) {
            return null;
        }
        String in = null;
        for (NodeShape.PinDef def : shape.inputs()) {
            if (def.kind() == source.kind() && def.type().isAssignableFrom(source.type())
                    && !isWired(node, def.name(), false)) {
                in = def.name();
                break;
            }
        }
        String out = null;
        for (NodeShape.PinDef def : shape.outputs()) {
            if (def.kind() == sink.kind() && sink.type().isAssignableFrom(def.type())
                    // Une sortie EXEC ne porte qu'un lien : déjà prise, elle ne peut
                    // pas relayer le fil coupé.
                    && !(def.kind() == fr.blueprint.api.pin.PinKind.EXEC
                            && isWired(node, def.name(), true))) {
                out = def.name();
                break;
            }
        }
        return in != null && out != null ? new String[]{in, out} : null;
    }

    private boolean isWired(UUID node, String pin, boolean output) {
        for (Link link : blueprint.links()) {
            if (output ? link.fromNode().equals(node) && link.fromPin().equals(pin)
                    : link.toNode().equals(node) && link.toPin().equals(pin)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Coupe le fil et fait passer le nœud au milieu. Le fil part <b>d'abord</b> :
     * tant qu'il est là, la cardinalité du pin d'arrivée refuserait la moitié aval.
     * Si l'un des deux nouveaux liens est malgré tout refusé, le fil d'origine est
     * remis — mieux vaut ne rien faire qu'un demi-câblage.
     */
    private boolean spliceOn(Link link, UUID node) {
        String[] pins = spliceTargets(link, node);
        if (pins == null || !applyTracked(new EditOperation.RemoveLink(link))) {
            return false;
        }
        boolean upstream = applyTracked(new EditOperation.AddLink(
                new Link(link.fromNode(), link.fromPin(), node, pins[0])));
        boolean downstream = applyTracked(new EditOperation.AddLink(
                new Link(node, pins[1], link.toNode(), link.toPin())));
        if (upstream && downstream) {
            return true;
        }
        if (upstream) {
            applyTracked(new EditOperation.RemoveLink(
                    new Link(link.fromNode(), link.fromPin(), node, pins[0])));
        }
        applyTracked(new EditOperation.AddLink(link));
        return false;
    }

    // ------------------------------------------------- actions du menu contextuel

    /** Détache tous les liens d'un pin — même chemin qu'Alt+clic, un seul geste. */
    public boolean breakPinLinks(PinRef pin) {
        history.beginGesture();
        try {
            int before = blueprint.links().size();
            detach(pin);
            return blueprint.links().size() != before;
        } finally {
            history.endGesture();
        }
    }

    /** Détache tous les liens d'un nœud, entrées et sorties confondues. */
    public boolean breakNodeLinks(UUID node) {
        history.beginGesture();
        try {
            List<Link> links = List.copyOf(blueprint.linksTouching(node));
            boolean any = false;
            for (Link link : links) {
                any |= applyTracked(new EditOperation.RemoveLink(link));
            }
            return any;
        } finally {
            history.endGesture();
        }
    }

    /** Remet un pin à sa valeur par défaut, en retirant son littéral explicite. */
    public boolean resetLiteral(UUID node, String pin) {
        return applyTracked(new EditOperation.SetLiteral(node, pin, null));
    }

    /**
     * Promeut un pin data en variable (geste d'Unreal) : crée une variable du type du
     * pin, avec sa valeur courante pour défaut, dépose un nœud Get/Set à côté et le
     * câble. Un seul geste d'annulation pour les quatre opérations — sinon quatre
     * Ctrl+Z pour défaire une seule intention.
     *
     * @return le nom de la variable créée, ou null si le pin ne s'y prête pas
     */
    public @Nullable String promoteToVariable(PinRef pin, String baseName) {
        if (pin.kind() != fr.blueprint.api.pin.PinKind.DATA || pin.type().isGeneric()) {
            return null;
        }
        NodeGeometry.Box box = boxOf(pin.node());
        if (box == null) {
            return null;
        }
        history.beginGesture();
        try {
            String name = uniqueVariableName(baseName);
            Node node = blueprint.node(pin.node());
            fr.blueprint.api.pin.LiteralValue current =
                    node == null ? null : node.literal(pin.pin());
            // La valeur du pin devient le défaut de la variable : promouvoir ne doit
            // pas perdre ce qui était déjà saisi.
            fr.blueprint.api.pin.LiteralValue initial =
                    current != null && current.type().equals(pin.type()) ? current : null;
            if (!applyTracked(new EditOperation.AddVariable(new fr.blueprint.core.graph.Variable(
                    name, pin.type(), initial,
                    fr.blueprint.core.graph.VarScope.GRAPH, false)))) {
                return null;
            }
            // Un pin d'ENTRÉE se nourrit d'un Get posé à sa gauche ; un pin de SORTIE
            // alimente un Set posé à sa droite.
            boolean set = pin.output();
            double x = set ? box.x() + box.width() + 48 : box.x() - NodeGeometry.WIDTH - 48;
            double y = box.y() + pin.row() * NodeGeometry.ROW_HEIGHT;
            UUID varNode = addVariableNode(set, name, x, y);
            if (varNode == null) {
                return null;
            }
            NodeShape shape = lookup.shape(set
                    ? fr.blueprint.core.graph.VarNodes.SET : fr.blueprint.core.graph.VarNodes.GET);
            if (shape != null) {
                List<NodeShape.PinDef> candidates = set ? shape.inputs() : shape.outputs();
                for (NodeShape.PinDef def : candidates) {
                    if (def.kind() == fr.blueprint.api.pin.PinKind.DATA
                            && applyTracked(new EditOperation.AddLink(
                                    buildLink(pin, varNode, def.name())))) {
                        break;
                    }
                }
            }
            return name;
        } finally {
            history.endGesture();
        }
    }

    private String uniqueVariableName(String base) {
        String clean = base.replaceAll("[^A-Za-z0-9_]", "");
        if (clean.isEmpty()) {
            clean = "var";
        }
        if (!blueprint.variables().containsKey(clean)) {
            return clean;
        }
        int n = 2;
        while (blueprint.variables().containsKey(clean + n)) {
            n++;
        }
        return clean + n;
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
            if (selection.isEmpty() && selectedLink != null) {
                apply(new EditOperation.RemoveLink(selectedLink));
                selectedLink = null;
            }
            if (selection.isEmpty() && selectedComment != null) {
                apply(new EditOperation.RemoveComment(selectedComment));
                selectedComment = null;
            }
        } finally {
            history.endGesture();
        }
        selection.clear();
    }

    // ------------------------------------------------------- commentaires (5.7)

    public @Nullable UUID selectedComment() {
        return selectedComment;
    }

    public fr.blueprint.core.graph.@Nullable CommentBox commentTitleAt(double wx, double wy) {
        for (fr.blueprint.core.graph.CommentBox box : blueprint.comments()) {
            if (wx >= box.position().x() && wx < box.position().x() + box.size().x()
                    && wy >= box.position().y() && wy < box.position().y() + COMMENT_TITLE) {
                return box;
            }
        }
        return null;
    }

    public fr.blueprint.core.graph.@Nullable CommentBox commentGripAt(double wx, double wy) {
        for (fr.blueprint.core.graph.CommentBox box : blueprint.comments()) {
            double x2 = box.position().x() + box.size().x();
            double y2 = box.position().y() + box.size().y();
            if (wx >= x2 - COMMENT_GRIP && wx < x2 && wy >= y2 - COMMENT_GRIP && wy < y2) {
                return box;
            }
        }
        return null;
    }

    /** Entoure la sélection d'une boîte de commentaire (touche C, UX §11). */
    public @Nullable UUID createCommentAroundSelection(String title) {
        List<NodeGeometry.Box> selected = new ArrayList<>();
        for (NodeGeometry.Box b : boxes()) {
            if (selection.isSelected(b.node().uuid())) {
                selected.add(b);
            }
        }
        if (selected.isEmpty()) {
            return null;
        }
        Camera.Rect bounds = NodeGeometry.boundsOf(selected);
        UUID id = UUID.randomUUID();
        boolean ok = applyTracked(new EditOperation.AddComment(new fr.blueprint.core.graph.CommentBox(
                id, title,
                new Vec2d(bounds.left() - 12, bounds.top() - COMMENT_TITLE - 8),
                new Vec2d(bounds.right() - bounds.left() + 24,
                        bounds.bottom() - bounds.top() + COMMENT_TITLE + 20),
                0x337DCFFF)));
        if (ok) {
            selectedComment = id;
        }
        return ok ? id : null;
    }

    public boolean renameComment(UUID id, String text) {
        fr.blueprint.core.graph.CommentBox box = blueprint.comment(id);
        return box != null && applyTracked(new EditOperation.EditComment(
                new fr.blueprint.core.graph.CommentBox(id, text, box.position(),
                        box.size(), box.color())));
    }

    // -------------------------------------------------- alignement, mise en page

    /** Q : aligne et distribue la sélection selon l'axe dominant (une entrée d'annulation). */
    public boolean alignSelection() {
        List<NodeGeometry.Box> selected = new ArrayList<>();
        for (NodeGeometry.Box b : boxes()) {
            if (selection.isSelected(b.node().uuid())) {
                selected.add(b);
            }
        }
        Map<UUID, Vec2d> targets = AlignActions.align(selected);
        return applyMoves(targets);
    }

    /** Ctrl+Shift+A : mise en page automatique de tout le graphe (AutoLayout core). */
    public boolean autoLayout() {
        return applyMoves(fr.blueprint.core.script.AutoLayout.compute(blueprint, lookup));
    }

    private boolean applyMoves(Map<UUID, Vec2d> targets) {
        if (targets.isEmpty()) {
            return false;
        }
        history.beginGesture();
        try {
            for (Map.Entry<UUID, Vec2d> e : targets.entrySet()) {
                Node node = blueprint.node(e.getKey());
                if (node != null && !node.position().equals(e.getValue())) {
                    applyTracked(new EditOperation.MoveNode(e.getKey(), e.getValue()));
                }
            }
        } finally {
            history.endGesture();
        }
        return true;
    }

    /** La pile d'annulation (5.6a) : toutes les mutations y naissent réversibles. */
    public UndoStack history() {
        return history;
    }

    /** Le canal de mutation unique pour les panneaux (variables, détails…). */
    public boolean applyOp(EditOperation op) {
        return applyTracked(op);
    }

    /** Bornes de geste exposées aux panneaux (renommage de variable = un undo, QA 5.5). */
    public void beginGesture() {
        history.beginGesture();
    }

    public void endGesture() {
        history.endGesture();
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

    /**
     * Remplace tout le graphe par le contenu du fragment (import de script, 5.11) —
     * UUID et positions CONSERVÉS, un seul geste d'annulation. Les métadonnées et
     * l'identifiant du blueprint restent ceux de la session.
     */
    public void replaceAll(Blueprint fragment) {
        history.beginGesture();
        try {
            for (UUID id : List.copyOf(blueprint.nodes().keySet())) {
                applyTracked(new EditOperation.RemoveNode(id));
            }
            for (String name : List.copyOf(blueprint.variables().keySet())) {
                applyTracked(new EditOperation.RemoveVariable(name));
            }
            // Les commentaires suivent le même sort que le graphe (QA 5.11) : les
            // anciens partent, ceux du script arrivent.
            for (fr.blueprint.core.graph.CommentBox comment : List.copyOf(blueprint.comments())) {
                applyTracked(new EditOperation.RemoveComment(comment.uuid()));
            }
            for (fr.blueprint.core.graph.CommentBox comment : fragment.comments()) {
                applyTracked(new EditOperation.AddComment(comment));
            }
            for (fr.blueprint.core.graph.Variable variable : fragment.variables().values()) {
                applyTracked(new EditOperation.AddVariable(variable));
            }
            for (Node node : fragment.nodes().values()) {
                applyTracked(new EditOperation.AddNode(node.uuid(), node.typeId(), node.position()));
                node.literals().forEach((pin, value) ->
                        applyTracked(new EditOperation.SetLiteral(node.uuid(), pin, value)));
            }
            for (Link link : fragment.links()) {
                applyTracked(new EditOperation.AddLink(link));
            }
            selection.clear();
        } finally {
            history.endGesture();
        }
    }

    /** Dépose un nœud Get (ou Set) lié à la variable, accroché — un seul geste. */
    public @Nullable UUID insertVariableNode(boolean set, String name, double wx, double wy) {
        history.beginGesture();
        try {
            return addVariableNode(set, name, wx, wy);
        } finally {
            history.endGesture();
        }
    }

    /**
     * Le corps de {@link #insertVariableNode}, <b>sans</b> ouvrir de geste.
     * {@code beginGesture} est idempotent : un appelant qui a déjà ouvert le sien
     * verrait son geste refermé au milieu par le {@code endGesture} imbriqué, et son
     * intention unique se scinderait en deux annulations.
     */
    private @Nullable UUID addVariableNode(boolean set, String name, double wx, double wy) {
        UUID id = UUID.randomUUID();
        Identifier type = set ? fr.blueprint.core.graph.VarNodes.SET
                : fr.blueprint.core.graph.VarNodes.GET;
        if (!applyTracked(new EditOperation.AddNode(id, type, camera.snap(new Vec2d(wx, wy))))) {
            return null;
        }
        applyTracked(new EditOperation.SetLiteral(id, fr.blueprint.core.graph.VarNodes.VAR_PIN,
                fr.blueprint.api.pin.LiteralValue.of(fr.blueprint.api.pin.PinTypes.STRING, name)));
        return id;
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
        } else {
            lastRefusal = result.refusal();
        }
        return result.applied();
    }

    /**
     * Diagnostic du dernier refus, consommé une fois. Un câblage refusé était un
     * no-op SILENCIEUX : le lien ne se faisait pas, rien ne disait pourquoi, et
     * l'explication existait pourtant déjà — le validateur la produit à chaque fois.
     */
    public fr.blueprint.core.graph.@Nullable Diagnostic takeRefusal() {
        fr.blueprint.core.graph.Diagnostic refusal = lastRefusal;
        lastRefusal = null;
        return refusal;
    }
}
