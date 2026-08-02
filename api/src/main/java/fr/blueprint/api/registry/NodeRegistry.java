package fr.blueprint.api.registry;

import fr.blueprint.api.node.NodeType;
import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.Optional;

/**
 * Registre des types de nœuds. Les mods tiers y enregistrent leurs nœuds depuis
 * {@code BlueprintPlugin#registerNodes} ; le registre est ensuite gelé.
 */
public interface NodeRegistry {

    /**
     * @throws IllegalStateException si le registre est gelé ou si l'identifiant est
     *         déjà pris (le message nomme les deux mods fournisseurs).
     */
    void register(NodeType type);

    Optional<NodeType> get(Identifier id);

    Collection<NodeType> all();
}
