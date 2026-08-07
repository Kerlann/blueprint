package fr.blueprint.core.graph;

/**
 * Portée d'une variable de blueprint : <b>qui la possède</b>, et donc qui la voit.
 *
 * <p>Deux questions se posent séparément, et les confondre était le défaut d'origine :
 * <i>par joueur ou pour tout le monde ?</i> et <i>propre à ce graphe ou partagée entre
 * graphes ?</i> {@code PLAYER} répondait à la première et laissait la seconde ouverte —
 * deux blueprints qui déclaraient chacun un {@code prenom} écrivaient au même endroit,
 * avec deux types possiblement différents et aucun moyen de s'en apercevoir.
 *
 * <table border="1">
 *   <caption>Ce que chaque portée possède</caption>
 *   <tr><th></th><th>par joueur</th><th>partagée entre blueprints</th></tr>
 *   <tr><td>{@code LOCAL}</td><td>—</td><td>—</td></tr>
 *   <tr><td>{@code GRAPH}</td><td>non</td><td>non</td></tr>
 *   <tr><td>{@code PLAYER}</td><td>oui</td><td>non</td></tr>
 *   <tr><td>{@code PLAYER_SHARED}</td><td>oui</td><td><b>oui</b></td></tr>
 *   <tr><td>{@code WORLD}</td><td>non</td><td><b>oui</b></td></tr>
 * </table>
 */
public enum VarScope {
    /** Durée d'une exécution ; jamais persistée. */
    LOCAL,
    /** Persistante par blueprint. */
    GRAPH,
    /**
     * Persistante <b>par joueur et par blueprint</b> (≤ 64 Ko par joueur, NFR14).
     *
     * <p>Le défaut sûr : un blueprint ne peut pas marcher sur les variables d'un autre,
     * même en choisissant le même nom. C'est la même isolation que {@code GRAPH}, avec le
     * joueur en plus.
     */
    PLAYER,
    /**
     * Persistante par joueur, et <b>commune à tous les blueprints</b>.
     *
     * <p>Le canal explicite entre scripts d'un même serveur : un prénom de jeu de rôle
     * écrit par le script de création, lu par celui des métiers et par celui de la
     * banque. Rien de tel n'existait, et {@code PLAYER} en tenait lieu <b>sans le dire</b>
     * — donc sans que personne ne puisse choisir le contraire.
     *
     * <p>Le partage se déclare des deux côtés : deux graphes qui se rencontrent ici l'ont
     * tous les deux voulu, et un type qui ne correspond pas est une erreur d'auteur, non
     * une collision subie.
     */
    PLAYER_SHARED,
    /** Globale au monde, et commune à tous les blueprints. */
    WORLD
}
