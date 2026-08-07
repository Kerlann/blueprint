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
    DUPLICATE_VARIABLE,
    // Écrans (épic 10)
    SCREEN_NOT_FOUND,
    DUPLICATE_SCREEN,
    ELEMENT_NOT_FOUND,
    DUPLICATE_ELEMENT,
    ELEMENT_NAME_INVALID,
    ELEMENT_TOO_SMALL,
    ELEMENT_PARENT_NOT_FOUND,
    ELEMENT_PARENT_CYCLE,
    ELEMENT_NOT_CONTAINER,
    INTERACTIVE_IN_HUD,
    SCREEN_LIMIT_EXCEEDED,
    ELEMENT_LIMIT_EXCEEDED,
    ELEMENT_OUTSIDE_SAFE_AREA,
    // Dispositions et styles nommés (story 10.10)
    ELEMENT_HUG_NOT_CONTAINER,
    ELEMENT_SQUEEZED,
    SCREEN_STYLE_NOT_FOUND,
    /**
     * Un conteneur défilant dont la hauteur s'ajuste à ses enfants (story 10.13).
     *
     * <p>Il grandit avec son contenu, donc rien ne dépasse jamais, donc il ne défile
     * <b>jamais</b>. La case est cochée, le panneau a l'air correct, et la molette ne fait
     * rien — sans ce mot, l'auteur en conclurait que le défilement est cassé. C'est la
     * même famille que les poignées inopérantes de la 10.10 : un contrôle qui ne peut rien
     * faire doit le dire.
     */
    SCREEN_SCROLL_HUGS,
    // Liaisons de données (story 10.7)
    SCREEN_BINDING_NOT_FOUND,
    // Fonctions (story 20.1)
    FUNCTION_NOT_FOUND,
    DUPLICATE_FUNCTION,
    /** Un événement dans un corps de fonction : elle s'appelle, elle ne se déclenche pas. */
    EVENT_IN_FUNCTION,
    /**
     * Un cycle dans le graphe des appels — récursion directe ou mutuelle.
     *
     * <p>Refusée et non bornée : la pile de cadres n'a pas de plafond, et le budget de
     * carburant ne couperait que tard, en nommant une cause qui n'est pas la bonne.
     */
    FUNCTION_RECURSION,
    /**
     * Un geste que le corps d'une fonction ne sait pas porter — un commentaire.
     *
     * <p>Un corps ne stocke que des nœuds et des liens. Laisser passer le geste le poserait
     * dans le graphe principal, où l'auteur ne le chercherait jamais ; le refuser en le
     * disant vaut mieux qu'un clic sans effet ou qu'un commentaire égaré.
     */
    UNSUPPORTED_IN_FUNCTION
}
