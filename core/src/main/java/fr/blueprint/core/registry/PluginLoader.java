package fr.blueprint.core.registry;

import fr.blueprint.api.BlueprintPlugin;
import fr.blueprint.core.BlueprintMod;
import fr.blueprint.core.event.EventRegistryImpl;
import net.fabricmc.loader.api.FabricLoader;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Chargement des plugins de l'entrypoint {@code blueprint}. Trois phases, tous plugins
 * confondus : types → nœuds → événements (un plugin peut utiliser les types d'un autre).
 * Un plugin qui lève est isolé : journalisé avec le nom du mod, ses enregistrements
 * partiels retirés — ses nœuds deviendront fantômes, jamais un crash au démarrage.
 */
public final class PluginLoader {

    /** Un plugin et le modid qui le fournit (les diagnostics nomment toujours le mod). */
    public record PluginEntry(String modId, BlueprintPlugin plugin) {
    }

    /** Registres chargés et gelés, plus la liste des mods dont le plugin a échoué. */
    public record LoadedRegistries(PinTypeRegistryImpl pinTypes, NodeRegistryImpl nodes,
                                   EventRegistryImpl events, List<String> failedMods) {
    }

    private PluginLoader() {
    }

    /** Adaptateur Fabric : ne contient aucune logique, tout est dans {@link #load}. */
    public static LoadedRegistries loadFromFabric() {
        List<PluginEntry> entries = new ArrayList<>();
        FabricLoader.getInstance()
                .getEntrypointContainers("blueprint", BlueprintPlugin.class)
                .forEach(container -> entries.add(new PluginEntry(
                        container.getProvider().getMetadata().getId(),
                        container.getEntrypoint())));
        return load(entries);
    }

    public static LoadedRegistries load(List<PluginEntry> plugins) {
        PinTypeRegistryImpl pinTypes = new PinTypeRegistryImpl();
        NodeRegistryImpl nodes = new NodeRegistryImpl();
        EventRegistryImpl events = new EventRegistryImpl();
        pinTypes.registerBuiltins();

        Set<String> failed = new LinkedHashSet<>();

        // Phase 1 : les types d'abord — un plugin peut typer avec les pins d'un autre.
        for (PluginEntry entry : plugins) {
            pinTypes.currentProvider(entry.modId());
            try {
                entry.plugin().registerTypes(pinTypes);
            } catch (Exception e) {
                isolate(entry.modId(), "registerTypes", e, failed, pinTypes, nodes, events);
            }
        }
        // Phase 2 : les nœuds.
        for (PluginEntry entry : plugins) {
            if (failed.contains(entry.modId())) {
                continue;
            }
            pinTypes.currentProvider(entry.modId());
            nodes.currentProvider(entry.modId());
            try {
                entry.plugin().registerNodes(nodes);
            } catch (Exception e) {
                isolate(entry.modId(), "registerNodes", e, failed, pinTypes, nodes, events);
            }
        }
        // Phase 3 : les événements.
        for (PluginEntry entry : plugins) {
            if (failed.contains(entry.modId())) {
                continue;
            }
            events.currentProvider(entry.modId());
            try {
                entry.plugin().registerEvents(events);
            } catch (Exception e) {
                isolate(entry.modId(), "registerEvents", e, failed, pinTypes, nodes, events);
            }
        }

        pinTypes.freeze();
        nodes.freeze();
        events.freeze();
        return new LoadedRegistries(pinTypes, nodes, events, List.copyOf(failed));
    }

    private static void isolate(String modId, String phase, Exception e, Set<String> failed,
                                PinTypeRegistryImpl pinTypes, NodeRegistryImpl nodes,
                                EventRegistryImpl events) {
        failed.add(modId);
        pinTypes.removeAllFrom(modId);
        nodes.removeAllFrom(modId);
        events.removeAllFrom(modId);
        BlueprintMod.LOGGER.error(
                "Le plugin Blueprint du mod « {} » a échoué pendant {} — il est désactivé, "
                        + "ses nœuds apparaîtront en fantômes", modId, phase, e);
    }
}
