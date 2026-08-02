package fr.blueprint.api.node;

import net.minecraft.resources.Identifier;

/**
 * Vue publique du blueprint en cours d'exécution, exposée aux actions via
 * {@link NodeContext#blueprint()}. Lecture seule : un nœud n'édite jamais son
 * propre graphe.
 */
public interface BlueprintHandle {

    Identifier id();

    boolean enabled();
}
