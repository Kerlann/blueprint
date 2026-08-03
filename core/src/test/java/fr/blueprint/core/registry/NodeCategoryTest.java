package fr.blueprint.core.registry;

import fr.blueprint.api.node.NodeCategories;
import fr.blueprint.api.node.NodeCategory;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sous-catégories (story 5.14). Le chemin vit dans l'identifiant de la catégorie ;
 * ce test tient la contrainte de profondeur et vérifie que la bibliothèque standard
 * range effectivement ses grosses catégories.
 */
class NodeCategoryTest {

    @Test
    void unCheminSeDecoupeEnParentEtFeuille() {
        NodeCategory sub = new NodeCategory("math/arithmetic");
        assertEquals("math", sub.parent());
        assertEquals("arithmetic", sub.leaf());
        assertTrue(sub.isSub());

        NodeCategory flat = new NodeCategory("flow");
        assertEquals("flow", flat.parent(), "une catégorie plate est sa propre parente");
        assertEquals("flow", flat.leaf());
        assertFalse(flat.isSub());
    }

    /**
     * Un seul niveau. Au-delà, la palette devient un explorateur de fichiers — et
     * l'arbre à deux niveaux du rendu afficherait n'importe quoi.
     */
    @Test
    void troisNiveauxSontRefuses() {
        assertThrows(IllegalArgumentException.class,
                () -> new NodeCategory("world/block/place"));
    }

    @Test
    void unCheminMalformeEstRefuse() {
        assertThrows(IllegalArgumentException.class, () -> new NodeCategory("/orphelin"));
        assertThrows(IllegalArgumentException.class, () -> new NodeCategory("orphelin/"));
        assertThrows(IllegalArgumentException.class, () -> new NodeCategory("  "));
    }

    /**
     * Aucune catégorie standard ne doit dépasser la douzaine de nœuds : c'est le
     * seuil au-delà duquel la liste dépliée ne se parcourt plus du regard, et la
     * raison d'être des sous-catégories. Le test échouera quand la bibliothèque
     * grossira sans qu'on range.
     */
    @Test
    void aucuneCategorieNeDevientIngerable() {
        var registries = PluginLoader.load(java.util.List.of(), true);
        Map<String, Integer> counts = new LinkedHashMap<>();
        registries.nodes().all().forEach(type ->
                counts.merge(type.category().id(), 1, Integer::sum));

        assertFalse(counts.isEmpty(), "aucun nœud chargé : le test ne prouve rien");
        counts.forEach((category, count) -> assertTrue(count <= 12,
                "« " + category + " » porte " + count + " nœuds — la subdiviser"));
    }

    /** Les nœuds rangés en sous-catégorie gardent un parent qui existe vraiment. */
    @Test
    void chaqueSousCategorieARattacheeAUneParenteConnue() {
        var registries = PluginLoader.load(java.util.List.of(), true);
        java.util.Set<String> parents = new java.util.HashSet<>();
        registries.nodes().all().forEach(type ->
                parents.add(NodeCategory.parentOf(type.category().id())));

        // Les parents doivent figurer parmi les catégories standard : un parent
        // inventé n'aurait ni couleur, ni pictogramme, ni traduction.
        java.util.Set<String> standard = new java.util.HashSet<>();
        for (java.lang.reflect.Field field : NodeCategories.class.getDeclaredFields()) {
            if (field.getType().equals(NodeCategory.class)) {
                try {
                    standard.add(((NodeCategory) field.get(null)).parent());
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(field.getName(), e);
                }
            }
        }
        parents.removeAll(standard);
        assertTrue(parents.isEmpty(), "parents inconnus : " + parents);
    }

    @Test
    void laBibliothequeStandardUtiliseBienSesSousCategories() {
        var registries = PluginLoader.load(java.util.List.of(), true);
        long subCategorised = registries.nodes().all().stream()
                .filter(type -> type.category().isSub())
                .count();
        assertTrue(subCategorised >= 40,
                "seulement " + subCategorised + " nœuds rangés en sous-catégorie");
    }
}
