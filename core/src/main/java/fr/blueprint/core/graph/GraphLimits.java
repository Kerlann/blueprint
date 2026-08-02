package fr.blueprint.core.graph;

/** Bornes structurelles d'un blueprint (principe P3 : rien n'est illimité). */
public record GraphLimits(int maxNodes) {

    /** Valeur par défaut ; deviendra configurable serveur (story 9.3). */
    public static final GraphLimits DEFAULT = new GraphLimits(1000);
}
