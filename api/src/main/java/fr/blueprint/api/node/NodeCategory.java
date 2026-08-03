package fr.blueprint.api.node;

/**
 * Catégorie d'un nœud : regroupement dans la palette de l'éditeur et teinte de
 * l'en-tête. Les catégories standard sont dans {@link NodeCategories} ; un mod peut
 * créer la sienne (elle apparaîtra comme groupe supplémentaire dans la palette).
 *
 * <p><b>Sous-catégories</b> : un identifiant peut porter un chemin à deux niveaux
 * séparé par {@code /} — {@code "math/arithmetic"}, {@code "flow/loop"}. La palette
 * les affiche en arbre repliable, et un nœud de sous-catégorie hérite de la couleur
 * et du pictogramme de son parent : c'est le parent qui identifie, la sous-catégorie
 * qui range.
 *
 * <p>Le chemin vit dans l'identifiant plutôt que dans un champ à part : la catégorie
 * traverse déjà le NBT, le réseau et BScript sous forme de chaîne, et une chaîne
 * hiérarchique n'y change rien. <b>Un seul niveau</b> de profondeur est reconnu —
 * au-delà, une palette devient un explorateur de fichiers.
 */
public record NodeCategory(String id) {

    public static final char SEPARATOR = '/';

    public NodeCategory {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("catégorie vide");
        }
        if (id.indexOf(SEPARATOR) != id.lastIndexOf(SEPARATOR)) {
            throw new IllegalArgumentException(
                    "catégorie à plus de deux niveaux : « " + id + " »");
        }
        if (id.charAt(0) == SEPARATOR || id.charAt(id.length() - 1) == SEPARATOR) {
            throw new IllegalArgumentException("chemin de catégorie malformé : « " + id + " »");
        }
    }

    /** La catégorie parente ({@code "math"} pour {@code "math/arithmetic"}), ou soi-même. */
    public String parent() {
        return parentOf(id);
    }

    /** Le dernier segment ({@code "arithmetic"}), ou l'identifiant entier s'il est plat. */
    public String leaf() {
        return leafOf(id);
    }

    public boolean isSub() {
        return isSub(id);
    }

    // Les mêmes découpages sur une catégorie déjà réduite à sa chaîne : c'est sous
    // cette forme qu'elle voyage dans NodeDescriptor, le NBT et le réseau.

    public static String parentOf(String category) {
        int slash = category.indexOf(SEPARATOR);
        return slash < 0 ? category : category.substring(0, slash);
    }

    public static String leafOf(String category) {
        int slash = category.indexOf(SEPARATOR);
        return slash < 0 ? category : category.substring(slash + 1);
    }

    public static boolean isSub(String category) {
        return category.indexOf(SEPARATOR) >= 0;
    }
}
