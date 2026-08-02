package fr.blueprint.client.registry;

import fr.blueprint.api.node.NodeType;
import fr.blueprint.core.registry.NodeDescriptor;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Descripteurs de nœuds vus du client : tout ce qu'il faut pour afficher et câbler,
 * rien du mod fournisseur (principe P1). En v1.0 finale, cette table sera remplie par
 * la synchro réseau (épic 6) ; le raccord {@link #fromLocal} la construit depuis les
 * registres locaux — correct en solo et en dev, où client et serveur partagent la JVM.
 */
public final class ClientNodeRegistry {

    private final Map<Identifier, NodeDescriptor> descriptors;

    public ClientNodeRegistry(Map<Identifier, NodeDescriptor> descriptors) {
        this.descriptors = Map.copyOf(descriptors);
    }

    public static ClientNodeRegistry fromLocal(PluginLoader.LoadedRegistries registries) {
        Map<Identifier, NodeDescriptor> map = new HashMap<>();
        for (NodeType type : registries.nodes().all()) {
            map.put(type.id(), NodeDescriptor.of(type));
        }
        return new ClientNodeRegistry(map);
    }

    /** Le descripteur du type, ou null : le nœud se rend alors en fantôme. */
    public @Nullable NodeDescriptor descriptor(Identifier typeId) {
        return descriptors.get(typeId);
    }

    /** Tous les descripteurs connus (alimente la palette). */
    public java.util.Collection<NodeDescriptor> descriptors() {
        return descriptors.values();
    }
}
