package fr.blueprint.api.node;

/**
 * Niveau de permission d'un nœud. Un blueprint porte un plafond : le serveur refuse
 * de compiler ou d'exécuter un nœud dont le niveau dépasse ce plafond.
 * L'ordre des constantes définit la hiérarchie.
 */
public enum Permission {
    /** Aucun effet sur le monde (maths, chaînes, lecture d'état). */
    SAFE,
    /** Effets de jeu ordinaires (messages, sons, effets sur soi). */
    GAMEPLAY,
    /** Modifie le monde (poser des blocs, spawner, explosion). */
    WORLD,
    /** Réservé aux opérateurs (commandes arbitraires, gestion du serveur). */
    ADMIN;

    /** Vrai si ce niveau est autorisé sous le plafond donné. */
    public boolean allowedUnder(Permission cap) {
        return ordinal() <= cap.ordinal();
    }
}
