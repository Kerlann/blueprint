package fr.blueprint.core.graph;

import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.PinKind;
import fr.blueprint.api.pin.PinTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * Nœud fantôme (principe P4) : un nœud dont le {@code typeId} n'est résolu par aucun
 * registre. Rien n'est supprimé — identifiant, position, littéraux, config et liens
 * sont intégralement conservés ; cette classe ne fait que <b>déduire une forme</b>
 * depuis les liens existants pour que l'éditeur puisse l'afficher et que le validateur
 * ne noie pas le graphe sous des faux diagnostics de pins.
 */
public final class GhostNode {

    private GhostNode() {
    }

    /**
     * Forme déduite : un pin par nom de pin référencé par un lien, typé {@code any}
     * (ou {@code exec} si le pin d'en face est un pin d'exécution connu).
     */
    public static NodeShape deduceShape(Blueprint blueprint, NodeTypeLookup lookup, Node ghost) {
        List<NodeShape.PinDef> inputs = new ArrayList<>();
        List<NodeShape.PinDef> outputs = new ArrayList<>();
        for (Link link : blueprint.linksTouching(ghost.uuid())) {
            if (link.toNode().equals(ghost.uuid())) {
                addUnique(inputs, deduce(link.toPin(), counterpartKind(blueprint, lookup, link, true)));
            }
            if (link.fromNode().equals(ghost.uuid())) {
                addUnique(outputs, deduce(link.fromPin(), counterpartKind(blueprint, lookup, link, false)));
            }
        }
        // Les littéraux posés sur le nœud gardent aussi leur pin visible.
        for (String pin : ghost.literals().keySet()) {
            addUnique(inputs, new NodeShape.PinDef(pin, PinKind.DATA, PinTypes.ANY, false));
        }
        return new NodeShape(inputs, outputs, false, Permission.SAFE);
    }

    private static PinKind counterpartKind(Blueprint bp, NodeTypeLookup lookup, Link link, boolean incoming) {
        Node counterpart = bp.node(incoming ? link.fromNode() : link.toNode());
        if (counterpart == null) {
            return PinKind.DATA;
        }
        NodeShape shape = lookup.shape(counterpart.typeId());
        if (shape == null) {
            return PinKind.DATA;
        }
        NodeShape.PinDef pin = incoming ? shape.output(link.fromPin()) : shape.input(link.toPin());
        return pin == null ? PinKind.DATA : pin.kind();
    }

    private static NodeShape.PinDef deduce(String name, PinKind kind) {
        return new NodeShape.PinDef(name, kind, kind == PinKind.EXEC ? PinTypes.EXEC : PinTypes.ANY, false);
    }

    private static void addUnique(List<NodeShape.PinDef> list, NodeShape.PinDef pin) {
        for (NodeShape.PinDef existing : list) {
            if (existing.name().equals(pin.name())) {
                return;
            }
        }
        list.add(pin);
    }
}
