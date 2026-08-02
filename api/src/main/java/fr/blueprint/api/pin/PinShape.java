package fr.blueprint.api.pin;

/**
 * Forme de rendu d'un pin. La forme double la couleur pour rester lisible
 * en cas de daltonisme (NFR11).
 */
public enum PinShape {
    EXEC,
    CIRCLE,
    DIAMOND,
    ARRAY,
    MAP
}
