package fr.blueprint.core.graph;

/**
 * Codes de diagnostic du modèle de graphe. Chaque code a sa clé de traduction
 * {@code blueprint.diag.<code_en_minuscules>} présente dans {@code en_us.json}
 * et {@code fr_fr.json} (AC7) — ajouter un code ici sans les deux clés est un bug.
 */
public enum DiagnosticCode {
    // Structure
    NODE_NOT_FOUND,
    DUPLICATE_NODE,
    LINK_NOT_FOUND,
    DUPLICATE_LINK,
    COMMENT_NOT_FOUND,
    DUPLICATE_COMMENT,
    PIN_NOT_FOUND,
    // Câblage
    TYPE_MISMATCH,
    EXEC_OUT_ALREADY_LINKED,
    DATA_IN_ALREADY_LINKED,
    DATA_CYCLE,
    GENERIC_CONFLICT,
    REQUIRED_PIN_UNLINKED,
    // Graphe
    UNKNOWN_NODE_TYPE,
    NO_ENTRY_POINT,
    NODE_LIMIT_EXCEEDED,
    PERMISSION_EXCEEDED,
    // Variables
    VARIABLE_NOT_FOUND,
    DUPLICATE_VARIABLE
}
