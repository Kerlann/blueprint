package fr.blueprint.core.graph;

/** Portée d'une variable de blueprint. */
public enum VarScope {
    /** Durée d'une exécution ; jamais persistée. */
    LOCAL,
    /** Persistante par blueprint. */
    GRAPH,
    /** Globale au monde. */
    WORLD,
    /** Persistante par joueur (≤ 64 Ko par joueur, NFR14). */
    PLAYER
}
