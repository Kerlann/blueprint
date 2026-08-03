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

    /** Depuis les descripteurs reçus du serveur (synchro 6.2). */
    public static ClientNodeRegistry of(java.util.Collection<NodeDescriptor> received) {
        Map<Identifier, NodeDescriptor> map = new HashMap<>();
        for (NodeDescriptor descriptor : received) {
            map.put(descriptor.id(), descriptor);
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

    /**
     * Vue validateur des descripteurs : l'éditeur câble et valide exactement comme le
     * serveur, même quand le nœud vient d'un mod que le client n'a pas (AC3). La règle
     * {@code required} reproduit celle de {@code NodeRegistryImpl.toShape} — une entrée
     * DATA sans défaut de pin ni défaut de type doit être câblée.
     */
    public fr.blueprint.core.graph.NodeTypeLookup lookup() {
        Map<Identifier, fr.blueprint.core.graph.NodeShape> cache = new HashMap<>();
        return typeId -> {
            NodeDescriptor descriptor = descriptors.get(typeId);
            if (descriptor == null) {
                return null;
            }
            return cache.computeIfAbsent(typeId, k -> toShape(descriptor));
        };
    }

    private static fr.blueprint.core.graph.NodeShape toShape(NodeDescriptor descriptor) {
        java.util.List<fr.blueprint.core.graph.NodeShape.PinDef> inputs =
                new java.util.ArrayList<>(descriptor.inputs().size());
        for (NodeDescriptor.PinDescriptor pin : descriptor.inputs()) {
            boolean required = pin.kind() == fr.blueprint.api.pin.PinKind.DATA
                    && pin.defaultValue() == null
                    && pin.type().defaultValue() == null;
            inputs.add(new fr.blueprint.core.graph.NodeShape.PinDef(
                    pin.name(), pin.kind(), pin.type(), required));
        }
        java.util.List<fr.blueprint.core.graph.NodeShape.PinDef> outputs =
                new java.util.ArrayList<>(descriptor.outputs().size());
        for (NodeDescriptor.PinDescriptor pin : descriptor.outputs()) {
            outputs.add(new fr.blueprint.core.graph.NodeShape.PinDef(
                    pin.name(), pin.kind(), pin.type(), false));
        }
        return new fr.blueprint.core.graph.NodeShape(inputs, outputs,
                descriptor.entryPoint(), descriptor.permission());
    }
}
