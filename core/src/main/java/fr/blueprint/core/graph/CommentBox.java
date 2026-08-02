package fr.blueprint.core.graph;

import java.util.UUID;

/** Boîte de commentaire du canevas (texte libre, jamais exécutée). */
public record CommentBox(UUID uuid, String text, Vec2d position, Vec2d size, int color) {
}
