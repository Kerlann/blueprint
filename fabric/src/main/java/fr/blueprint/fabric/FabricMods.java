package fr.blueprint.fabric;

import fr.blueprint.api.BlueprintPlugin;
import fr.blueprint.platform.PlatformMods;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.CustomValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Ce que Fabric sait des mods présents : les entrypoints et les valeurs personnalisées
 * du {@code fabric.mod.json}.
 *
 * <p>Aucune logique ici — la lecture est brute, et tout le traitement reste dans
 * {@code PluginLoader}. C'était déjà la forme de {@code loadFromFabric()} avant le
 * déplacement, et c'est ce qui rend l'équivalent NeoForge écrivable sans le relire.
 */
public final class FabricMods implements PlatformMods {

    /** La clé du {@code fabric.mod.json} où un mod déclare ses classes annotées (8.1). */
    private static final String HOLDERS_KEY = "blueprint:node_holders";

    @Override
    public boolean isLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public List<ModPlugin> plugins() {
        List<ModPlugin> out = new ArrayList<>();
        FabricLoader.getInstance()
                .getEntrypointContainers("blueprint", BlueprintPlugin.class)
                .forEach(container -> out.add(new ModPlugin(
                        container.getProvider().getMetadata().getId(),
                        container.getEntrypoint())));
        return out;
    }

    @Override
    public List<ModNodeHolders> nodeHolders() {
        List<ModNodeHolders> out = new ArrayList<>();
        for (var mod : FabricLoader.getInstance().getAllMods()) {
            var value = mod.getMetadata().getCustomValue(HOLDERS_KEY);
            if (value == null || value.getType() != CustomValue.CvType.ARRAY) {
                continue;
            }
            List<String> classNames = new ArrayList<>();
            for (var entry : value.getAsArray()) {
                if (entry.getType() == CustomValue.CvType.STRING) {
                    classNames.add(entry.getAsString());
                }
            }
            if (!classNames.isEmpty()) {
                out.add(new ModNodeHolders(mod.getMetadata().getId(), classNames));
            }
        }
        return out;
    }
}
