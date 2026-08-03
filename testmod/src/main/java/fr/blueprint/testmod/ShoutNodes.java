package fr.blueprint.testmod;

import fr.blueprint.api.annotation.BlueprintNode;
import fr.blueprint.api.annotation.In;
import fr.blueprint.api.annotation.Out;

/**
 * Nœuds déclarés par annotation (story 8.1) : des méthodes statiques ordinaires, aucune
 * ligne de builder. C'est la preuve vivante que le chemin annoté vaut le chemin manuel —
 * {@code TestPlugin} les enregistre par {@code AnnotatedNodes.register}.
 */
public final class ShoutNodes {

    private ShoutNodes() {
    }

    @BlueprintNode(value = "blueprint_testmod:shout", category = "string", pure = true)
    @Out("shouted")
    public static String shout(@In(def = "salut") String message, @In(def = "1") int times) {
        return message.toUpperCase(java.util.Locale.ROOT).repeat(Math.max(0, times)) + " !";
    }
}
