package fr.blueprint.core.script;

import fr.blueprint.api.pin.PinKind;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.Node;
import fr.blueprint.core.graph.NodeShape;
import fr.blueprint.core.graph.NodeTypeLookup;
import fr.blueprint.core.graph.Vec2d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mise en page automatique (story 5.7, réutilisée par l'import BScript sans
 * {@code @pos}) : couches par parcours du flux exec de gauche à droite, purs décalés
 * entre les colonnes de leurs consommateurs. Déterministe : même graphe → mêmes
 * positions (ordre par UUID).
 */
public final class AutoLayout {

    /** Espacement horizontal entre couches exec. */
    public static final double COLUMN = 240;
    /** Espacement vertical entre nœuds d'une même couche. */
    public static final double ROW = 140;
    /** Décalage des purs, entre leur colonne et la précédente. */
    public static final double PURE_SHIFT = 170;

    private AutoLayout() {
    }

    /** Les positions cibles de tous les nœuds du graphe. */
    public static Map<UUID, Vec2d> compute(Blueprint bp, NodeTypeLookup lookup) {
        Map<UUID, Integer> layer = new HashMap<>();
        Map<UUID, Boolean> pure = new HashMap<>();
        for (Node node : bp.nodes().values()) {
            NodeShape shape = lookup.shape(node.typeId());
            boolean hasExec = false;
            if (shape != null) {
                for (NodeShape.PinDef def : shape.inputs()) {
                    hasExec |= def.kind() == PinKind.EXEC;
                }
                for (NodeShape.PinDef def : shape.outputs()) {
                    hasExec |= def.kind() == PinKind.EXEC;
                }
            }
            pure.put(node.uuid(), shape != null && !hasExec);
        }

        // BFS sur les liens exec depuis les racines (pas de lien exec entrant).
        List<UUID> execNodes = bp.nodes().values().stream()
                .filter(n -> !pure.get(n.uuid()))
                .map(Node::uuid)
                .sorted(Comparator.comparing(UUID::toString))
                .toList();
        Deque<UUID> queue = new ArrayDeque<>();
        for (UUID id : execNodes) {
            boolean hasIncomingExec = false;
            for (Link link : bp.links()) {
                if (link.toNode().equals(id) && isExecLink(bp, lookup, link)) {
                    hasIncomingExec = true;
                    break;
                }
            }
            if (!hasIncomingExec) {
                layer.put(id, 0);
                queue.add(id);
            }
        }
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            int next = layer.get(current) + 1;
            for (Link link : bp.links()) {
                if (link.fromNode().equals(current) && isExecLink(bp, lookup, link)
                        && layer.getOrDefault(link.toNode(), -1) < next) {
                    layer.put(link.toNode(), next);
                    queue.add(link.toNode());
                }
            }
        }
        // Nœuds exec jamais atteints (cycles isolés) : couche 0.
        for (UUID id : execNodes) {
            layer.putIfAbsent(id, 0);
        }
        // Purs : couche du premier consommateur (le décalage les met entre colonnes).
        for (Node node : bp.nodes().values()) {
            if (!pure.get(node.uuid())) {
                continue;
            }
            int best = 0;
            for (Link link : bp.links()) {
                if (link.fromNode().equals(node.uuid())) {
                    best = Math.max(best, layer.getOrDefault(link.toNode(), 0));
                }
            }
            layer.put(node.uuid(), best);
        }

        // Empilement vertical par colonne, ordre déterministe (UUID).
        Map<Integer, List<UUID>> columns = new LinkedHashMap<>();
        bp.nodes().keySet().stream()
                .sorted(Comparator.comparing(UUID::toString))
                .forEach(id -> columns.computeIfAbsent(layer.get(id), k -> new ArrayList<>()).add(id));

        Map<UUID, Vec2d> out = new HashMap<>();
        for (Map.Entry<Integer, List<UUID>> column : columns.entrySet()) {
            int execIndex = 0;
            int pureIndex = 0;
            for (UUID id : column.getValue()) {
                if (pure.get(id)) {
                    out.put(id, new Vec2d(column.getKey() * COLUMN - PURE_SHIFT,
                            pureIndex++ * ROW + ROW / 2));
                } else {
                    out.put(id, new Vec2d(column.getKey() * COLUMN, execIndex++ * ROW));
                }
            }
        }
        return out;
    }

    private static boolean isExecLink(Blueprint bp, NodeTypeLookup lookup, Link link) {
        Node from = bp.node(link.fromNode());
        NodeShape shape = from == null ? null : lookup.shape(from.typeId());
        if (shape == null) {
            return false;
        }
        NodeShape.PinDef def = shape.output(link.fromPin());
        return def != null && def.kind() == PinKind.EXEC;
    }
}
