package fr.blueprint.core.graph;

import fr.blueprint.api.pin.GenericResolution;
import fr.blueprint.api.pin.ParameterizedPinType;
import fr.blueprint.api.pin.PinKind;
import fr.blueprint.api.pin.PinType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Validation structurelle d'un blueprint. Retourne des {@link Diagnostic} typés,
 * jamais d'exception : un graphe invalide est une erreur d'utilisateur, pas de
 * programme. {@link #canLink} est LA source de vérité du câblage — l'éditeur et les
 * opérations d'édition passent par elle, aucune divergence possible.
 */
public final class GraphValidator {

    private GraphValidator() {
    }

    /** Résultat : diagnostics + exécutabilité (aucune erreur, aucun fantôme). */
    public record ValidationResult(List<Diagnostic> diagnostics, boolean executable) {
        public List<Diagnostic> errors() {
            return diagnostics.stream().filter(d -> d.severity() == Diagnostic.Severity.ERROR).toList();
        }
    }

    public static ValidationResult validate(Blueprint bp, NodeTypeLookup lookup) {
        return validate(bp, lookup, GraphLimits.DEFAULT);
    }

    public static ValidationResult validate(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
        List<Diagnostic> out = new ArrayList<>();

        if (bp.nodes().size() > limits.maxNodes()) {
            out.add(Diagnostic.error(DiagnosticCode.NODE_LIMIT_EXCEEDED, Diagnostic.graph(),
                    bp.nodes().size(), limits.maxNodes()));
        }

        // Fantômes et permissions, nœud par nœud.
        boolean anyEntryPoint = false;
        Map<UUID, NodeShape> shapes = new HashMap<>();
        Set<UUID> ghosts = new HashSet<>();
        for (Node node : bp.nodes().values()) {
            NodeShape shape = lookup.shape(node.typeId());
            if (shape == null) {
                ghosts.add(node.uuid());
                shapes.put(node.uuid(), GhostNode.deduceShape(bp, lookup, node));
                out.add(Diagnostic.error(DiagnosticCode.UNKNOWN_NODE_TYPE, Diagnostic.node(node.uuid()),
                        node.typeId().toString(), node.typeId().getNamespace()));
                continue;
            }
            shapes.put(node.uuid(), shape);
            anyEntryPoint |= shape.entryPoint();
            if (!shape.permission().allowedUnder(bp.meta().permissionCap())) {
                out.add(Diagnostic.error(DiagnosticCode.PERMISSION_EXCEEDED, Diagnostic.node(node.uuid()),
                        shape.permission().name(), bp.meta().permissionCap().name()));
            }
            // Littéraux : contrôle profond (TYPE-001) + pins requis.
            node.literals().forEach((pin, literal) -> {
                NodeShape.PinDef def = shape.input(pin);
                if (def == null) {
                    out.add(Diagnostic.error(DiagnosticCode.PIN_NOT_FOUND, Diagnostic.node(node.uuid()), pin));
                } else if (!def.type().isAssignableFrom(literal.type()) || !Literals.matches(literal)) {
                    out.add(Diagnostic.error(DiagnosticCode.TYPE_MISMATCH, Diagnostic.node(node.uuid()),
                            pin, def.type().toString(), literal.type().toString()));
                }
            });
            for (NodeShape.PinDef def : shape.inputs()) {
                if (def.required() && node.literal(def.name()) == null
                        && bp.linksInto(node.uuid(), def.name()).isEmpty()) {
                    out.add(Diagnostic.error(DiagnosticCode.REQUIRED_PIN_UNLINKED,
                            Diagnostic.node(node.uuid()), def.name()));
                }
            }
        }

        if (!bp.nodes().isEmpty() && !anyEntryPoint) {
            out.add(Diagnostic.warning(DiagnosticCode.NO_ENTRY_POINT, Diagnostic.graph()));
        }

        // Liens : validation individuelle (défense en profondeur, les ops valident déjà).
        for (Link link : bp.links()) {
            Diagnostic d = checkLink(bp, shapes, link, false);
            if (d != null) {
                out.add(d);
            }
        }

        // Jokers : une résolution par nœud, tous les liens y contribuent (FR5).
        for (Node node : bp.nodes().values()) {
            NodeShape shape = shapes.get(node.uuid());
            GenericResolution resolution = new GenericResolution();
            for (Link link : bp.linksTouching(node.uuid())) {
                Diagnostic conflict = unifyLink(bp, shapes, resolution, node, shape, link);
                if (conflict != null) {
                    out.add(conflict);
                }
            }
        }

        // Cycles de données (FR7) : DFS tricolore sur les liens DATA uniquement.
        out.addAll(findDataCycles(bp, shapes, ghosts));

        boolean executable = out.stream().noneMatch(d -> d.severity() == Diagnostic.Severity.ERROR);
        return new ValidationResult(List.copyOf(out), executable);
    }

    /**
     * La règle de câblage unique (AC2, FR3, FR4). Retourne le diagnostic de refus,
     * ou null si le lien est acceptable. Utilisée par {@code EditOperation.AddLink}
     * et par {@link #validate}.
     */
    public static @Nullable Diagnostic canLink(Blueprint bp, NodeTypeLookup lookup, Link link) {
        Map<UUID, NodeShape> shapes = new HashMap<>();
        Node from = bp.node(link.fromNode());
        Node to = bp.node(link.toNode());
        if (from == null || to == null) {
            return Diagnostic.error(DiagnosticCode.NODE_NOT_FOUND, Diagnostic.link(link),
                    from == null ? link.fromNode().toString() : link.toNode().toString());
        }
        shapes.put(from.uuid(), shapeOrGhost(bp, lookup, from));
        shapes.put(to.uuid(), shapeOrGhost(bp, lookup, to));
        if (bp.links().contains(link)) {
            return Diagnostic.error(DiagnosticCode.DUPLICATE_LINK, Diagnostic.link(link));
        }
        Diagnostic structural = checkLink(bp, shapes, link, true);
        if (structural != null) {
            return structural;
        }
        // Conflit de jokers si on ajoutait ce lien (résolutions des deux extrémités).
        // Les liens EXISTANTS des deux nœuds participent : leurs contreparties doivent
        // donc aussi être résolues en forme, pas seulement les extrémités du candidat.
        for (Node endpoint : List.of(from, to)) {
            NodeShape shape = shapes.get(endpoint.uuid());
            GenericResolution resolution = new GenericResolution();
            List<Link> withCandidate = new ArrayList<>(bp.linksTouching(endpoint.uuid()));
            withCandidate.add(link);
            for (Link l : withCandidate) {
                ensureShape(bp, lookup, shapes, l.fromNode());
                ensureShape(bp, lookup, shapes, l.toNode());
                Diagnostic conflict = unifyLink(bp, shapes, resolution, endpoint, shape, l);
                if (conflict != null) {
                    return conflict;
                }
            }
        }
        return null;
    }

    private static void ensureShape(Blueprint bp, NodeTypeLookup lookup,
                                    Map<UUID, NodeShape> shapes, UUID uuid) {
        if (!shapes.containsKey(uuid)) {
            Node node = bp.node(uuid);
            if (node != null) {
                shapes.put(uuid, shapeOrGhost(bp, lookup, node));
            }
        }
    }

    private static NodeShape shapeOrGhost(Blueprint bp, NodeTypeLookup lookup, Node node) {
        NodeShape shape = lookup.shape(node.typeId());
        return shape != null ? shape : GhostNode.deduceShape(bp, lookup, node);
    }

    /** Contrôles pin à pin d'un lien. {@code adding} : le lien n'est pas encore posé. */
    private static @Nullable Diagnostic checkLink(Blueprint bp, Map<UUID, NodeShape> shapes,
                                                  Link link, boolean adding) {
        NodeShape fromShape = shapes.get(link.fromNode());
        NodeShape toShape = shapes.get(link.toNode());
        if (fromShape == null || toShape == null) {
            return Diagnostic.error(DiagnosticCode.NODE_NOT_FOUND, Diagnostic.link(link),
                    (fromShape == null ? link.fromNode() : link.toNode()).toString());
        }
        NodeShape.PinDef out = fromShape.output(link.fromPin());
        NodeShape.PinDef in = toShape.input(link.toPin());
        if (out == null) {
            return Diagnostic.error(DiagnosticCode.PIN_NOT_FOUND, Diagnostic.link(link), link.fromPin());
        }
        if (in == null) {
            return Diagnostic.error(DiagnosticCode.PIN_NOT_FOUND, Diagnostic.link(link), link.toPin());
        }
        if (out.kind() != in.kind()) {
            return Diagnostic.error(DiagnosticCode.TYPE_MISMATCH, Diagnostic.link(link),
                    link.toPin(), in.type().toString(), out.type().toString());
        }
        // Cardinalité (FR3) : exec sortant ≤ 1, data entrant ≤ 1.
        int tolerance = adding ? 0 : 1;
        if (out.kind() == PinKind.EXEC
                && bp.linksFrom(link.fromNode(), link.fromPin()).size() > tolerance) {
            return Diagnostic.error(DiagnosticCode.EXEC_OUT_ALREADY_LINKED, Diagnostic.link(link), link.fromPin());
        }
        if (in.kind() == PinKind.DATA
                && bp.linksInto(link.toNode(), link.toPin()).size() > tolerance) {
            return Diagnostic.error(DiagnosticCode.DATA_IN_ALREADY_LINKED, Diagnostic.link(link), link.toPin());
        }
        if (in.kind() == PinKind.DATA && !in.type().isAssignableFrom(out.type())) {
            return Diagnostic.error(DiagnosticCode.TYPE_MISMATCH, Diagnostic.link(link),
                    link.toPin(), in.type().toString(), out.type().toString());
        }
        // Auto-boucle de données : cycle trivial, refusé immédiatement.
        if (adding && in.kind() == PinKind.DATA && link.fromNode().equals(link.toNode())) {
            return Diagnostic.error(DiagnosticCode.DATA_CYCLE, Diagnostic.link(link));
        }
        return null;
    }

    /** Contribution d'un lien à la résolution des jokers d'un nœud (contrat « déclaré d'abord »). */
    private static @Nullable Diagnostic unifyLink(Blueprint bp, Map<UUID, NodeShape> shapes,
                                                   GenericResolution resolution,
                                                   Node node, NodeShape shape, Link link) {
        NodeShape.PinDef declared;
        NodeShape.PinDef concrete;
        if (link.toNode().equals(node.uuid())) {
            declared = shape.input(link.toPin());
            NodeShape other = shapes.get(link.fromNode());
            concrete = other == null ? null : other.output(link.fromPin());
        } else if (link.fromNode().equals(node.uuid())) {
            declared = shape.output(link.fromPin());
            NodeShape other = shapes.get(link.toNode());
            concrete = other == null ? null : other.input(link.toPin());
        } else {
            return null;
        }
        if (declared == null || concrete == null || declared.kind() != PinKind.DATA) {
            return null; // les pins manquants ont déjà leur diagnostic
        }
        if (!containsGeneric(declared.type()) ) {
            return null;
        }
        if (!resolution.unify(declared.type(), concrete.type())) {
            return Diagnostic.error(DiagnosticCode.GENERIC_CONFLICT, Diagnostic.link(link),
                    declared.type().toString(), concrete.type().toString());
        }
        return null;
    }

    private static boolean containsGeneric(PinType type) {
        if (type.isGeneric()) {
            return true;
        }
        if (type instanceof ParameterizedPinType p) {
            for (PinType arg : p.args()) {
                if (containsGeneric(arg)) {
                    return true;
                }
            }
        }
        return false;
    }

    // DFS tricolore : blanc (jamais vu), gris (en cours), noir (terminé).
    // Un arc DATA vers un nœud gris ferme un cycle.
    private static List<Diagnostic> findDataCycles(Blueprint bp, Map<UUID, NodeShape> shapes,
                                                    Set<UUID> ghosts) {
        Map<UUID, List<Link>> dataOut = new HashMap<>();
        for (Link link : bp.links()) {
            // GHOST-001 : entre deux fantômes, la nature du pin est indevinable (déduite
            // DATA par défaut) — un cycle exec y serait un faux positif ; on s'abstient,
            // le graphe est déjà non exécutable via UNKNOWN_NODE_TYPE.
            if (ghosts.contains(link.fromNode()) && ghosts.contains(link.toNode())) {
                continue;
            }
            NodeShape fromShape = shapes.get(link.fromNode());
            if (fromShape == null) {
                continue;
            }
            NodeShape.PinDef out = fromShape.output(link.fromPin());
            if (out != null && out.kind() == PinKind.DATA) {
                dataOut.computeIfAbsent(link.fromNode(), k -> new ArrayList<>()).add(link);
            }
        }
        List<Diagnostic> cycles = new ArrayList<>();
        Set<UUID> done = new HashSet<>();
        Set<UUID> inProgress = new HashSet<>();
        for (UUID start : bp.nodes().keySet()) {
            dfs(start, dataOut, inProgress, done, cycles);
        }
        return cycles;
    }

    private static void dfs(UUID node, Map<UUID, List<Link>> dataOut,
                            Set<UUID> inProgress, Set<UUID> done, List<Diagnostic> cycles) {
        if (done.contains(node) || !inProgress.add(node)) {
            return;
        }
        for (Link link : dataOut.getOrDefault(node, List.of())) {
            if (inProgress.contains(link.toNode())) {
                cycles.add(Diagnostic.error(DiagnosticCode.DATA_CYCLE, Diagnostic.link(link)));
            } else {
                dfs(link.toNode(), dataOut, inProgress, done, cycles);
            }
        }
        inProgress.remove(node);
        done.add(node);
    }
}
