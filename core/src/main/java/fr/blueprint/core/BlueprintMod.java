package fr.blueprint.core;

import fr.blueprint.api.BlueprintPlugin;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlueprintMod implements ModInitializer {
    public static final String MOD_ID = "blueprint";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Blueprint initialisé");

        // Le chargement réel des plugins (registres, gel, hash) arrive en story 2.2 ;
        // ici on prouve seulement que l'entrypoint "blueprint" est câblé.
        int plugins = FabricLoader.getInstance()
                .getEntrypointContainers("blueprint", BlueprintPlugin.class)
                .size();
        LOGGER.info("{} plugin(s) Blueprint détecté(s)", plugins);
    }
}
