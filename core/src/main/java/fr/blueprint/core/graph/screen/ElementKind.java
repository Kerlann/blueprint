package fr.blueprint.core.graph.screen;

/**
 * Les types d'éléments d'un écran (story 10.1, AC1 ; élargi en 10.8).
 *
 * <p>Un type inconnu à la lecture ne casse rien : il est préservé brut, comme un nœud
 * fantôme préserve sa configuration quand son mod manque (FR40). C'est ce qui permet
 * d'ajouter des types plus tard sans migrer les écrans déjà créés.
 */
public enum ElementKind {
    /** Conteneur : un fond, et des enfants positionnés RELATIVEMENT à lui. */
    PANEL(true, false),
    /** Texte. */
    LABEL(false, false),
    /** Cliquable — déclenche {@code gui/element_clicked}. */
    BUTTON(false, true),
    /** Texture, depuis le jeu ou un pack (story 10.5). */
    IMAGE(false, false),
    /** Barre de progression, de 0 à 1. */
    PROGRESS(false, false);

    private final boolean container;
    private final boolean interactive;

    ElementKind(boolean container, boolean interactive) {
        this.container = container;
        this.interactive = interactive;
    }

    /** Vrai si le type accepte des enfants. Masquer un conteneur masque sa page. */
    public boolean container() {
        return container;
    }

    /**
     * Vrai si le type attend le curseur. Un élément interactif n'a aucun sens dans un
     * HUD, où la souris appartient au jeu (story 10.9) : le validateur le refuse.
     */
    public boolean interactive() {
        return interactive;
    }
}
