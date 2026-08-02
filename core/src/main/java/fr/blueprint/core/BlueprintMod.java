package fr.blueprint.core;

import fr.blueprint.api.BlueprintPlugin;
import fr.blueprint.core.command.BlueprintCommand;
import fr.blueprint.core.config.BlueprintConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlueprintMod implements ModInitializer {
    public static final String MOD_ID = "blueprint";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static BlueprintConfig config = BlueprintConfig.DEFAULT;
    private static fr.blueprint.core.registry.PluginLoader.LoadedRegistries registries;

    public static BlueprintConfig config() {
        return config;
    }

    /** Registres gelés du serveur (types de pins + nœuds), disponibles après l'init. */
    public static fr.blueprint.core.registry.PluginLoader.LoadedRegistries registries() {
        return registries;
    }

    @Override
    public void onInitialize() {
        LOGGER.info("Blueprint initialisé");

        config = BlueprintConfig.load(FabricLoader.getInstance().getConfigDir());
        BlueprintCommand.register(config);

        int declared = FabricLoader.getInstance()
                .getEntrypointContainers("blueprint", BlueprintPlugin.class)
                .size();
        registries = fr.blueprint.core.registry.PluginLoader.loadFromFabric();
        LOGGER.info("{} plugin(s) Blueprint détecté(s) — {} type(s) de pins, {} nœud(s), {} en échec",
                declared, registries.pinTypes().all().size(), registries.nodes().all().size(),
                registries.failedMods().size());
    }
}
