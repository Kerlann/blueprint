package fr.blueprint.api.node;

/**
 * Côté d'exécution d'un nœud. La v1 n'exécute que côté serveur (principe P1 :
 * l'exécution est serveur, l'édition est client) ; d'autres valeurs pourront
 * apparaître dans une version ultérieure sans casser les déclarations existantes.
 */
public enum ExecSide {
    SERVER
}
