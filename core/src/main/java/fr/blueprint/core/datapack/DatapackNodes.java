package fr.blueprint.core.datapack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.core.BlueprintMod;
import fr.blueprint.core.registry.NodeRegistryImpl;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Nœuds composites livrés par datapack (story 8.2) : {@code data/<ns>/blueprint/nodes/*.json},
 * relus à chaque {@code /reload}.
 *
 * <p>Un fichier invalide est <b>signalé et ignoré</b> — les autres se chargent. Un
 * datapack cassé ne doit jamais coûter les nœuds de tous les autres, et surtout pas
 * empêcher le rechargement.
 */
public final class DatapackNodes {

    /** Dossier lu dans chaque datapack. */
    public static final String DIRECTORY = "blueprint/nodes";

    private DatapackNodes() {
    }

    /** Bilan d'un rechargement — journalisé, et rendu aux tests. */
    public record Report(List<NodeType> loaded, Map<Identifier, List<String>> rejected) {

        public int loadedCount() {
            return loaded.size();
        }
    }

    /**
     * Analyse un lot de fichiers déjà lus (pur, testable sans {@code ResourceManager}).
     *
     * @param files identifiant du fichier → contenu JSON
     */
    public static Report parseAll(Map<Identifier, String> files,
                                  PluginLoader.LoadedRegistries registries) {
        // Analyse : chaque fichier isolé de ses voisins, y compris contre une exception
        // inattendue (un « fuelCost »: "beaucoup" ne doit pas emporter le rechargement).
        Map<Identifier, CompositeDefinition> definitions = new LinkedHashMap<>();
        Map<Identifier, List<String>> rejected = new LinkedHashMap<>();
        files.forEach((file, text) -> {
            List<String> errors = new ArrayList<>();
            try {
                JsonElement element = JsonParser.parseString(text);
                if (!(element instanceof JsonObject json)) {
                    errors.add("objet JSON attendu");
                } else {
                    CompositeDefinition.Result parsed = CompositeDefinition.parse(json,
                            id -> registries.pinTypes().get(id).orElse(null));
                    if (parsed.ok()) {
                        definitions.put(file, parsed.definition());
                    } else {
                        errors.addAll(parsed.errors());
                    }
                }
            } catch (RuntimeException e) {
                errors.add("fichier illisible : " + e);
            }
            if (!errors.isEmpty()) {
                rejected.put(file, List.copyOf(errors));
            }
        });

        // Construction par vagues : un composite peut en appeler un autre du même lot,
        // à condition qu'il finisse par se résoudre. Tant qu'une vague en construit au
        // moins un, on recommence ; ce qui reste ne se résoudra jamais (référence
        // absente ou cycle) et part au rapport avec sa raison.
        Map<Identifier, NodeType> built = new LinkedHashMap<>();
        CompositeNode.Resolver resolver = id -> {
            NodeType fromLayer = built.get(id);
            return fromLayer != null ? fromLayer : registries.nodes().get(id).orElse(null);
        };
        Map<Identifier, List<String>> pending = new LinkedHashMap<>();
        boolean progress = true;
        while (progress && !definitions.isEmpty()) {
            progress = false;
            pending.clear();
            for (var iterator = definitions.entrySet().iterator(); iterator.hasNext(); ) {
                var entry = iterator.next();
                CompositeNode.Built attempt;
                try {
                    attempt = CompositeNode.build(entry.getValue(), resolver);
                } catch (RuntimeException e) {
                    attempt = new CompositeNode.Built(null, List.of("construction impossible : " + e));
                }
                if (attempt.type() != null) {
                    built.put(entry.getValue().id(), attempt.type());
                    iterator.remove();
                    progress = true;
                } else {
                    pending.put(entry.getKey(), attempt.errors());
                }
            }
        }
        rejected.putAll(pending);
        return new Report(List.copyOf(built.values()), Map.copyOf(rejected));
    }

    /** Lit les fichiers du gestionnaire de ressources, puis remplace la couche datapack. */
    public static Report reload(ResourceManager resources, PluginLoader.LoadedRegistries registries) {
        Map<Identifier, String> files = new LinkedHashMap<>();
        resources.listResources(DIRECTORY, path -> path.getPath().endsWith(".json"))
                .forEach((path, resource) -> {
                    String text = read(path, resource);
                    if (text != null) {
                        files.put(path, text);
                    }
                });
        Report report = parseAll(files, registries);
        if (registries.nodes() instanceof NodeRegistryImpl impl) {
            impl.replaceDatapackLayer(report.loaded());
        }
        report.rejected().forEach((file, errors) ->
                BlueprintMod.LOGGER.error("Nœud de datapack « {} » refusé : {}",
                        file, String.join(" ; ", errors)));
        BlueprintMod.LOGGER.info("Nœuds de datapack : {} chargé(s), {} refusé(s)",
                report.loadedCount(), report.rejected().size());
        return report;
    }

    private static String read(Identifier path, Resource resource) {
        try (BufferedReader reader = resource.openAsReader()) {
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line).append('\n');
            }
            return text.toString();
        } catch (IOException e) {
            BlueprintMod.LOGGER.error("Nœud de datapack « {} » illisible", path, e);
            return null;
        }
    }
}
