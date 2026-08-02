package fr.blueprint.core.registry;

import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.pin.PinKind;
import fr.blueprint.api.registry.NodeRegistry;
import fr.blueprint.core.BlueprintMod;
import fr.blueprint.core.graph.NodeShape;
import fr.blueprint.core.graph.NodeTypeLookup;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implémentation du registre des nœuds. Sert aussi de {@link NodeTypeLookup} : les
 * nœuds enregistrés alimentent directement le validateur de graphes (story 2.2, AC6).
 */
public final class NodeRegistryImpl implements NodeRegistry, NodeTypeLookup {

    private final Map<Identifier, NodeType> byId = new LinkedHashMap<>();
    private final Map<Identifier, String> providerById = new HashMap<>();
    private final Map<Identifier, NodeShape> shapeCache = new ConcurrentHashMap<>();
    private String currentProvider = "blueprint";
    private boolean frozen;

    /** Nom du mod dont les enregistrements sont en cours (pour les diagnostics). */
    public void currentProvider(String modId) {
        this.currentProvider = modId;
    }

    @Override
    public void register(NodeType type) {
        if (frozen) {
            throw new IllegalStateException("Registre des nœuds gelé : « " + type.id()
                    + " » (mod « " + currentProvider + " ») arrive après la phase d'enregistrement");
        }
        String existing = providerById.get(type.id());
        if (existing != null) {
            throw new IllegalStateException("Nœud en doublon : « " + type.id()
                    + " » est déjà fourni par « " + existing
                    + " », refusé pour « " + currentProvider + " »");
        }
        // Convention : le namespace du nœud est le modid du fournisseur. Avertir, pas bloquer.
        if (!type.id().getNamespace().equals(currentProvider)) {
            BlueprintMod.LOGGER.warn(
                    "Le nœud « {} » du mod « {} » n'utilise pas son namespace — convention non respectée",
                    type.id(), currentProvider);
        }
        byId.put(type.id(), type);
        providerById.put(type.id(), currentProvider);
    }

    /** Retire tous les enregistrements d'un fournisseur (isolation d'un plugin en échec). */
    public void removeAllFrom(String provider) {
        List<Identifier> ids = new ArrayList<>();
        providerById.forEach((id, p) -> {
            if (p.equals(provider)) {
                ids.add(id);
            }
        });
        for (Identifier id : ids) {
            byId.remove(id);
            providerById.remove(id);
            shapeCache.remove(id);
        }
    }

    public void freeze() {
        frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public Optional<String> providerOf(Identifier id) {
        return Optional.ofNullable(providerById.get(id));
    }

    @Override
    public Optional<NodeType> get(Identifier id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Collection<NodeType> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    // ------------------------------------------------ NodeTypeLookup (validateur)

    @Override
    public @Nullable NodeShape shape(Identifier typeId) {
        NodeType type = byId.get(typeId);
        if (type == null) {
            return null;
        }
        return shapeCache.computeIfAbsent(typeId, k -> toShape(type));
    }

    /**
     * {@code required} : une entrée DATA sans défaut de pin ni défaut de type doit être
     * câblée — même sémantique que la palette de l'éditeur (story 2.1, Dev Notes).
     */
    private static NodeShape toShape(NodeType type) {
        List<NodeShape.PinDef> inputs = new ArrayList<>(type.inputs().size());
        for (NodeType.PinSpec spec : type.inputs()) {
            boolean required = spec.kind() == PinKind.DATA
                    && spec.defaultValue() == null
                    && spec.type().defaultValue() == null;
            inputs.add(new NodeShape.PinDef(spec.name(), spec.kind(), spec.type(), required));
        }
        List<NodeShape.PinDef> outputs = new ArrayList<>(type.outputs().size());
        for (NodeType.PinSpec spec : type.outputs()) {
            outputs.add(new NodeShape.PinDef(spec.name(), spec.kind(), spec.type(), false));
        }
        return new NodeShape(inputs, outputs, false, type.permission());
    }
}
