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
    PROGRESS(false, false),

    // --------------------------------------------------- éléments riches (10.8)

    /**
     * Liste défilante. Ses lignes sont un <b>gabarit répété</b>, pas des éléments
     * empilés : décrire une ligne et laisser la liste la répéter garde l'écran de la
     * taille de sa description, alors qu'un élément par ligne le ferait grossir avec les
     * données — un paquet d'ouverture proportionnel au nombre d'articles, et le plafond
     * d'éléments (10.6) atteint pour une liste banale.
     */
    LIST(false, true),
    /** Champ de saisie. Ce qu'il accepte est <b>revérifié côté serveur</b> (FR52). */
    INPUT(false, true),
    /**
     * Affiche un objet : icône, nombre, reflet d'enchantement, infobulle au survol.
     *
     * <p><b>Affichage seul.</b> Un emplacement où l'on pourrait déposer un objet ne
     * serait plus un affichage mais un conteneur, avec tout ce que Minecraft y attache —
     * synchronisation d'inventaire, transactions, et duplication en cas de
     * désynchronisation. Pour échanger un objet, on passe par un bouton et le graphe le
     * donne ou le retire lui-même.
     */
    SLOT(false, false),
    /** Case à cocher : un choix binaire, sans les cinq éléments qu'il fallait pour le simuler. */
    TOGGLE(false, true),
    /** Curseur de valeur, entre deux bornes et sur un pas éventuel. */
    SLIDER(false, true),
    /**
     * Aperçu d'une entité vivante, tournant dans son cadre.
     *
     * <p>Il <b>dessine un modèle</b>, il ne fait pas exister de créature : en faire
     * apparaître une pour l'afficher la ferait vivre — elle prendrait des dégâts,
     * déclencherait des événements, compterait dans les quotas du monde, et resterait là
     * si l'écran se fermait mal.
     */
    ENTITY_PREVIEW(false, false);

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
