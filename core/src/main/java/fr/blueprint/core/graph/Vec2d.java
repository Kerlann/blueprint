package fr.blueprint.core.graph;

/**
 * Position 2D dans le canevas de l'éditeur. Record maison plutôt que
 * {@code net.minecraft.world.phys.Vec2} : il nous faut une égalité par valeur fiable
 * (réversibilité des opérations d'édition) et aucune dépendance au jeu dans le modèle.
 */
public record Vec2d(double x, double y) {
    public static final Vec2d ZERO = new Vec2d(0, 0);
}
