package fr.blueprint.api.event;

/**
 * Mode de distribution d'un événement : à quels blueprints il s'adresse.
 * Exploité par l'ordonnanceur (story 3.5) — déclaré dès maintenant car il fait
 * partie du contrat public de l'événement.
 */
public enum Dispatch {
    /** Tous les blueprints abonnés du serveur. */
    GLOBAL,
    /** Les blueprints abonnés de la dimension concernée. */
    PER_LEVEL,
    /** Les blueprints abonnés du joueur concerné. */
    PER_PLAYER
}
