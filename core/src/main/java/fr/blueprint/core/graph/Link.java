package fr.blueprint.core.graph;

import java.util.UUID;

/** Lien orienté d'un pin de sortie vers un pin d'entrée. */
public record Link(UUID fromNode, String fromPin, UUID toNode, String toPin) {
}
