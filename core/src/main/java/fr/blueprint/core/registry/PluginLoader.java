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

    /**
     * Classes porteuses de méthodes {@code @BlueprintNode} déclarées par un mod dans son
     * {@code fabric.mod.json} (story 8.1) : la voie sans plugin ni processeur.
     *
     * <pre>{@code "custom": { "blueprint:node_holders": ["com.example.MyNodes"] } }</pre>
     */
    public record NodeHolders(String modId, List<String> classNames) {
    }

    /** Adaptateur Fabric : ne contient aucune logique, tout est dans {@link #load}. */
    public static LoadedRegistries loadFromFabric() {
        List<PluginEntry> entries = new ArrayList<>();
        FabricLoader.getInstance()
                .getEntrypointContainers("blueprint", BlueprintPlugin.class)
                .forEach(container -> entries.add(new PluginEntry(
                        container.getProvider().getMetadata().getId(),
                        container.getEntrypoint())));
        return load(entries, true, holdersFromFabric());
    }

    /** Lit la clé {@code blueprint:node_holders} de chaque mod présent. */
    private static List<NodeHolders> holdersFromFabric() {
        List<NodeHolders> out = new ArrayList<>();
        for (var mod : FabricLoader.getInstance().getAllMods()) {
            var value = mod.getMetadata().getCustomValue(HOLDERS_KEY);
            if (value == null
                    || value.getType() != net.fabricmc.loader.api.metadata.CustomValue.CvType.ARRAY) {
                continue;
            }
            List<String> classNames = new ArrayList<>();
            for (var entry : value.getAsArray()) {
                if (entry.getType() == net.fabricmc.loader.api.metadata.CustomValue.CvType.STRING) {
                    classNames.add(entry.getAsString());
                }
            }
            if (!classNames.isEmpty()) {
                out.add(new NodeHolders(mod.getMetadata().getId(), classNames));
            }
        }
        return out;
    }

    public static final String HOLDERS_KEY = "blueprint:node_holders";

    /** Chargement nu (tests) : sans la bibliothèque standard. */
    public static LoadedRegistries load(List<PluginEntry> plugins) {
        return load(plugins, false);
    }

    public static LoadedRegistries load(List<PluginEntry> plugins, boolean includeStandard) {
        return load(plugins, includeStandard, List.of());
    }

    public static LoadedRegistries load(List<PluginEntry> plugins, boolean includeStandard,
                                        List<NodeHolders> holders) {
        PinTypeRegistryImpl pinTypes = new PinTypeRegistryImpl();
        NodeRegistryImpl nodes = new NodeRegistryImpl();
        EventRegistryImpl events = new EventRegistryImpl();
        pinTypes.registerBuiltins();
        if (includeStandard) {
            nodes.currentProvider("blueprint");
            events.currentProvider("blueprint");
            fr.blueprint.core.nodes.StandardNodes.register(nodes);
            fr.blueprint.core.nodes.GuiNodes.register(nodes);
            fr.blueprint.core.event.StandardEvents.register(events);
        }

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

        // Phase 2 bis : les classes annotées déclarées dans fabric.mod.json (8.1). Un
        // mod peut n'avoir QUE ça — aucun plugin, aucun processeur d'annotations.
        for (NodeHolders holder : holders) {
            if (failed.contains(holder.modId())) {
                continue;
            }
            nodes.currentProvider(holder.modId());
            for (String className : holder.classNames()) {
                try {
                    Class<?> type = Class.forName(className, true,
                            PluginLoader.class.getClassLoader());
                    fr.blueprint.api.annotation.AnnotatedNodes.register(nodes, type);
                } catch (ClassNotFoundException | LinkageError e) {
                    isolate(holder.modId(), "node_holders (" + className + " introuvable)",
                            new IllegalStateException(e), failed, pinTypes, nodes, events);
                } catch (Exception e) {
                    isolate(holder.modId(), "node_holders (" + className + ")",
                            e, failed, pinTypes, nodes, events);
                }
            }
        }

        // Synthèse des nœuds d'événement (7.6, AC5) : chaque EventType — standard OU
        // tiers — engendre son point d'entrée. L'action matérialise la charge utile du
        // déclencheur dans les slots du graphe.
        for (fr.blueprint.api.event.EventType event : events.all()) {
            if (nodes.get(event.id()).isPresent()) {
                BlueprintMod.LOGGER.warn(
                        "Nœud d'événement non synthétisé : l'identifiant « {} » est déjà un nœud", event.id());
                continue;
            }
            nodes.currentProvider(events.providerOf(event.id()).orElse("blueprint"));
            var builder = fr.blueprint.api.node.NodeType.builder(event.id())
                    .category(eventCategory(event))
                    .entryPoint()
                    .titleKey(event.titleKey())
                    .execOut("exec_out")
                    .action(ctx -> {
                        for (fr.blueprint.api.event.EventType.OutDef out : event.outputs()) {
                            Object value = ctx.trigger().output(out.name());
                            if (value != null) {
                                ctx.out(out.name(), value);
                            }
                        }
                    });
            for (fr.blueprint.api.event.EventType.OutDef out : event.outputs()) {
                builder.out(out.name(), out.type());
            }
            nodes.register(builder.build());
        }

        pinTypes.freeze();
        nodes.freeze();
        events.freeze();
        return new LoadedRegistries(pinTypes, nodes, events, List.copyOf(failed));
    }

    /**
     * La sous-catégorie d'un nœud d'événement, déduite de ce qu'il PRODUIT plutôt que
     * de son nom : un événement qui donne un joueur est un événement de joueur, un
     * événement qui donne une entité vient du monde, le reste vient du serveur.
     *
     * <p>Déduire du nom aurait marché pour les événements du projet et raté ceux des
     * mods tiers, qui n'ont aucune raison de suivre notre convention de nommage.
     */
    private static fr.blueprint.api.node.NodeCategory eventCategory(
            fr.blueprint.api.event.EventType event) {
        boolean player = false;
        boolean entity = false;
        boolean block = false;
        for (fr.blueprint.api.event.EventType.OutDef out : event.outputs()) {
            player |= out.type().equals(fr.blueprint.api.pin.PinTypes.PLAYER);
            entity |= out.type().equals(fr.blueprint.api.pin.PinTypes.ENTITY);
            block |= out.type().equals(fr.blueprint.api.pin.PinTypes.BLOCKPOS);
        }
        // Une POSITION l'emporte sur le joueur : « un joueur casse un bloc » se cherche
        // sous « monde », pas sous « joueur » — on y va pour réagir au bloc, le joueur
        // n'étant que celui qui passait par là. La règle a aussi rendu « événements du
        // joueur » lisible : elle atteignait treize entrées, la borne au-delà de laquelle
        // un repli de palette ne se lit plus d'un coup d'œil (11.5).
        if (block) {
            return fr.blueprint.api.node.NodeCategories.EVENT_WORLD;
        }
        if (player) {
            return fr.blueprint.api.node.NodeCategories.EVENT_PLAYER;
        }
        return entity ? fr.blueprint.api.node.NodeCategories.EVENT_WORLD
                : fr.blueprint.api.node.NodeCategories.EVENT_SERVER;
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
