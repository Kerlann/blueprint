package fr.blueprint.api.pin;

/**
 * Forme de rendu d'un pin. La forme double la couleur pour rester lisible
 * en cas de daltonisme (NFR11).
 */
public enum PinShape {
    EXEC,
    CIRCLE,
    DIAMOND,
    /** Triangle plein — ajouté en 9.4 : deux formes ne suffisaient pas à séparer 16 types. */
    TRIANGLE,
    /** Anneau (cercle évidé), même raison. */
    RING,
    /** Croix — la cinquième forme, imposée par le graphe de confusion (9.4). */
    CROSS,
    ARRAY,
    MAP
}
