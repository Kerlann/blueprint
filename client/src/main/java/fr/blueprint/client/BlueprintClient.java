package fr.blueprint.client;

import fr.blueprint.core.BlueprintMod;
import net.fabricmc.api.ClientModInitializer;

public class BlueprintClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlueprintMod.LOGGER.info("Blueprint client initialisé");
    }
}
