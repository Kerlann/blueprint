package fr.blueprint.client;

import fr.blueprint.client.editor.BlueprintEditorScreen;
import fr.blueprint.core.BlueprintMod;
import fr.blueprint.core.DemoBlueprint;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class BlueprintClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Raccourci de dev (story 5.1) : ouvre l'éditeur sur le blueprint de démo.
        // L'ouverture réelle depuis le serveur (synchro registre) est l'épic 6.
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(BlueprintMod.MOD_ID, "main"));
        KeyMapping openEditor = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.blueprint.open_editor", GLFW.GLFW_KEY_F6, category));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openEditor.consumeClick()) {
                var nodes = BlueprintMod.registries().nodes();
                mc.setScreen(new BlueprintEditorScreen(DemoBlueprint.build(nodes), nodes));
            }
        });

        BlueprintMod.LOGGER.info("Blueprint client initialisé");
    }
}
