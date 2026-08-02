package fr.blueprint.api.node;

/**
 * Catégorie d'un nœud : regroupement dans la palette de l'éditeur et teinte de
 * l'en-tête. Les catégories standard sont dans {@link NodeCategories} ; un mod peut
 * créer la sienne (elle apparaîtra comme groupe supplémentaire dans la palette).
 */
public record NodeCategory(String id) {
}
