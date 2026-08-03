package fr.blueprint.client;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import fr.blueprint.client.editor.BlueprintEditorScreen;
import fr.blueprint.client.editor.EditorSession;
import fr.blueprint.core.BlueprintMod;
import fr.blueprint.core.DemoBlueprint;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public class BlueprintClient implements ClientModInitializer {

    /** Dernier blueprint réel édité : F6 le rouvre (sinon la démo). */
    private static @Nullable Identifier lastEdited;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(BlueprintMod.MOD_ID, "main"));
        KeyMapping openEditor = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.blueprint.open_editor", GLFW.GLFW_KEY_F6, category));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openEditor.consumeClick()) {
                if (lastEdited != null && fr.blueprint.client.net.BlueprintNet.connected()) {
                    fr.blueprint.client.net.BlueprintNet.requestOpen(lastEdited);
                } else {
                    openDemoEditor(mc);
                }
            }
        });

        // /blueprint-edit : liste | <id> | demo | create <id>. Tout passe par le
        // serveur depuis la 6.3 (paquets, verrou optimiste) ; l'ouverture est différée
        // d'une tâche : la fermeture du chat écraserait un setScreen immédiat.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("blueprint-edit")
                        .executes(context -> {
                            list(context.getSource());
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(ClientCommandManager.literal("demo").executes(context -> {
                            Minecraft mc = Minecraft.getInstance();
                            mc.schedule(() -> openDemoEditor(mc));
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(ClientCommandManager.literal("create")
                                .then(ClientCommandManager.argument("id", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            createAndEdit(context.getSource(),
                                                    StringArgumentType.getString(context, "id"));
                                            return Command.SINGLE_SUCCESS;
                                        })))
                        .then(ClientCommandManager.argument("id", StringArgumentType.greedyString())
                                .suggests((context, builder) -> {
                                    for (Identifier id
                                            : fr.blueprint.client.net.BlueprintNet.known()) {
                                        builder.suggest(id.toString());
                                    }
                                    builder.suggest("demo");
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    String raw = StringArgumentType.getString(context, "id");
                                    Identifier id = parseId(raw);
                                    if (id == null) {
                                        context.getSource().sendFeedback(Component.translatable(
                                                "blueprint.editor.cmd.unknown", raw, raw));
                                    } else {
                                        // Le verdict (ouverture ou « inconnu ») revient du
                                        // serveur : lui seul sait ce qui existe (6.3).
                                        lastEdited = id;
                                        fr.blueprint.client.net.BlueprintNet.requestOpen(id);
                                    }
                                    return Command.SINGLE_SUCCESS;
                                }))));

        // Synchro du registre serveur (6.2) puis ouverture/enregistrement réseau (6.3).
        fr.blueprint.client.net.RegistrySync.register();
        fr.blueprint.client.net.BlueprintNet.register();

        BlueprintMod.LOGGER.info("Blueprint client initialisé");
    }

    /** Sans namespace, un identifiant nu vit sous {@code blueprint:} (comme /blueprint). */
    private static @Nullable Identifier parseId(String raw) {
        return raw.contains(":") ? Identifier.tryParse(raw)
                : Identifier.tryParse(BlueprintMod.MOD_ID + ":" + raw);
    }

    /** La liste vient du serveur (6.3) : elle s'affiche à l'arrivée de la réponse. */
    private static void list(FabricClientCommandSource source) {
        if (!fr.blueprint.client.net.BlueprintNet.connected()) {
            source.sendFeedback(Component.translatable("blueprint.editor.cmd.multiplayer"));
            return;
        }
        fr.blueprint.client.net.BlueprintNet.requestList(true);
    }

    private static void createAndEdit(FabricClientCommandSource source, String raw) {
        if (!fr.blueprint.client.net.BlueprintNet.connected()) {
            source.sendFeedback(Component.translatable("blueprint.editor.cmd.multiplayer"));
            return;
        }
        Identifier id = parseId(raw);
        if (id == null) {
            source.sendFeedback(Component.translatable("blueprint.editor.cmd.unknown", raw, raw));
            return;
        }
        lastEdited = id;
        fr.blueprint.client.net.BlueprintNet.requestCreate(id);
    }

    private static void openDemoEditor(Minecraft mc) {
        var registries = BlueprintMod.registries();
        mc.setScreen(new BlueprintEditorScreen(
                EditorSession.scratch(DemoBlueprint.build(registries.nodes())),
                registries, fr.blueprint.client.net.RegistrySync.descriptors(),
                fr.blueprint.client.net.RegistrySync.lookup()));
    }
}
