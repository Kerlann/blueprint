package fr.blueprint.core.registry;

import fr.blueprint.api.BlueprintPlugin;
import fr.blueprint.core.BlueprintMod;
import fr.blueprint.core.event.EventRegistryImpl;
import fr.blueprint.platform.Platform;
import fr.blueprint.platform.PlatformMods;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Chargement des plugins tiers, par service ou par entrypoint. Trois phases, tous plugins
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
     * Classes porteuses de méthodes {@code @BlueprintNode} déclarées par un mod dans ses
     * métadonnées (story 8.1) : la voie sans plugin ni processeur.
     *
     * <pre>{@code "custom": { "blueprint:node_holders": ["com.example.MyNodes"] } }</pre>
     *
     * <p>La <i>forme</i> de la déclaration appartient au chargeur — c'est
     * {@code PlatformMods} qui la lit. Ce qu'on en fait est ici, et ne change pas d'un
     * chargeur à l'autre.
     */
    public record NodeHolders(String modId, List<String> classNames) {
    }

    /**
     * Tous les plugins présents, par les <b>deux</b> voies, dédoublonnés.
     *
     * <p>La voie du chargeur (entrypoint {@code fabric.mod.json}) et la voie portable
     * ({@link ServiceLoader}) coexistent délibérément. La première est annoncée depuis la
     * story 8.1 et des mods tiers ont pu s'écrire contre elle ; la retirer casserait un
     * contrat publié pour économiser vingt lignes. La seconde est la seule qui marche
     * partout — c'est celle que {@code docs/extension-api.md} recommande désormais.
     *
     * <p>Le chargeur passe en premier : son modid vient de ses métadonnées, il fait
     * autorité sur ce que le plugin déclare de lui-même.
     */
    public static List<PluginEntry> discover() {
        List<BlueprintPlugin> services = new ArrayList<>();
        ServiceLoader.load(BlueprintPlugin.class, PluginLoader.class.getClassLoader())
                .forEach(services::add);
        return merge(Platform.mods().plugins(), services,
                refusal -> BlueprintMod.LOGGER.error("Plugin Blueprint refusé — {}", refusal));
    }

    /** Les classes annotées déclarées dans les métadonnées des mods (story 8.1). */
    public static List<NodeHolders> discoverHolders() {
        List<NodeHolders> holders = new ArrayList<>();
        for (var holder : Platform.mods().nodeHolders()) {
            holders.add(new NodeHolders(holder.modId(), holder.classNames()));
        }
        return holders;
    }

    /**
     * La fusion des deux voies — <b>pure</b>, donc vérifiable sans jeu ni chargeur.
     *
     * <p>Le dédoublonnage se fait sur la <b>classe</b> et non sur l'instance : les deux
     * voies construisent chacune la leur, et un mod qui se déclare des deux côtés — ce
     * qui est le cas normal d'un mod qui veut marcher partout — verrait sinon ses nœuds
     * enregistrés deux fois, donc refusés la seconde fois, donc son plugin isolé pour un
     * conflit avec lui-même.
     *
     * @param onRefusal reçoit une phrase par plugin écarté, à journaliser
     */
    static List<PluginEntry> merge(List<PlatformMods.ModPlugin> fromLoader,
                                   List<BlueprintPlugin> fromServices,
                                   java.util.function.Consumer<String> onRefusal) {
        List<PluginEntry> entries = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (var plugin : fromLoader) {
            if (seen.add(plugin.plugin().getClass().getName())) {
                entries.add(new PluginEntry(plugin.modId(), plugin.plugin()));
            }
        }
        for (BlueprintPlugin plugin : fromServices) {
            if (!seen.add(plugin.getClass().getName())) {
                continue;   // déjà vu par le chargeur, qui sait mieux d'où il vient
            }
            String modId = plugin.modId();
            if (modId == null || modId.isBlank()) {
                onRefusal.accept(plugin.getClass().getName() + " : déclaré par service mais "
                        + "sans modId() — cet identifiant est montré au joueur dans la "
                        + "palette, il ne peut pas être deviné (voir BlueprintPlugin#modId)");
                continue;
            }
            entries.add(new PluginEntry(modId, plugin));
        }
        return List.copyOf(entries);
    }

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

        // Phase 2 bis : les classes annotées déclarées dans les métadonnées du mod (8.1). Un
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
