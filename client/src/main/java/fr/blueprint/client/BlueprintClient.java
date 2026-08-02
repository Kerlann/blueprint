package fr.blueprint.client;

import com.mojang.brigadier.Command;
import fr.blueprint.client.editor.BlueprintEditorScreen;
import fr.blueprint.client.registry.ClientNodeRegistry;
import fr.blueprint.core.BlueprintMod;
import fr.blueprint.core.DemoBlueprint;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class BlueprintClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Ouvertures de dev (story 5.1) : F6 et /blueprint-edit, sur le blueprint de
        // démo. L'ouverture réelle depuis le serveur (synchro registre) est l'épic 6.
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(BlueprintMod.MOD_ID, "main"));
        KeyMapping openEditor = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.blueprint.open_editor", GLFW.GLFW_KEY_F6, category));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openEditor.consumeClick()) {
                openDemoEditor(mc);
            }
        });

        // Commande cliente (ne touche pas au /blueprint serveur). L'ouverture est
        // différée d'une tâche : la fermeture du chat écraserait un setScreen immédiat.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("blueprint-edit")
                        .executes(context -> {
                            Minecraft mc = Minecraft.getInstance();
                            mc.schedule(() -> openDemoEditor(mc));
                            return Command.SINGLE_SUCCESS;
                        })));

        BlueprintMod.LOGGER.info("Blueprint client initialisé");
    }

    private static void openDemoEditor(Minecraft mc) {
        var registries = BlueprintMod.registries();
        mc.setScreen(new BlueprintEditorScreen(
                DemoBlueprint.build(registries.nodes()), registries.nodes(),
                ClientNodeRegistry.fromLocal(registries)));
    }
}
