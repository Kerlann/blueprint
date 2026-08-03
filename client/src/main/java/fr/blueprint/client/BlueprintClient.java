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

        // F6 ouvre le NAVIGATEUR, pas une démo. Reprendre le dernier blueprint édité
        // paraissait pratique et ne l'était pas : on ne pouvait plus en atteindre un
        // autre sans passer par la commande, l'identifiant complet tapé de mémoire.
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openEditor.consumeClick()) {
                if (fr.blueprint.client.net.BlueprintNet.connected()) {
                    mc.setScreen(new fr.blueprint.client.browser.BlueprintBrowserScreen());
                } else {
                    openDemoEditor(mc);
                }
            }
        });

        // /blueprint-edit n'est plus qu'un ALIAS de /blueprint edit. Il ne décide plus
        // rien : il réécrit la commande et la renvoie au serveur, qui la traite comme
        // celle qu'on aurait tapée à la main.
        //
        // Les deux commandes avaient beau viser les mêmes blueprints, elles n'avaient
        // en commun ni leurs suggestions — l'une lisait le gestionnaire du serveur,
        // l'autre une liste reçue au join — ni leurs vérifications. Une seule
        // implémentation ferme la question plutôt que de la corriger sans cesse.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("blueprint-edit")
                        .executes(context -> forward(context.getSource(), "blueprint edit"))
                        // « demo » reste local : c'est le seul chemin qui fonctionne
                        // hors serveur, quand il n'y a rien à demander à personne.
                        .then(ClientCommandManager.literal("demo").executes(context -> {
                            Minecraft mc = Minecraft.getInstance();
                            mc.schedule(() -> openDemoEditor(mc));
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(ClientCommandManager.literal("create")
                                .then(ClientCommandManager.argument("id", StringArgumentType.greedyString())
                                        .executes(context -> forward(context.getSource(),
                                                "blueprint create "
                                                        + StringArgumentType.getString(context, "id")))))
                        .then(ClientCommandManager.argument("id", StringArgumentType.greedyString())
                                .suggests((context, builder) -> {
                                    for (Identifier id
                                            : fr.blueprint.client.net.BlueprintNet.known()) {
                                        builder.suggest(id.toString());
                                    }
                                    builder.suggest("demo");
                                    return builder.buildFuture();
                                })
                                .executes(context -> forward(context.getSource(),
                                        "blueprint edit "
                                                + StringArgumentType.getString(context, "id"))))));

        // Synchro du registre serveur (6.2) puis ouverture/enregistrement réseau (6.3).
        fr.blueprint.client.net.RegistrySync.register();
        fr.blueprint.client.net.BlueprintNet.register();
        fr.blueprint.client.net.DebugClient.register();
        fr.blueprint.client.net.ScreenClient.register();

        BlueprintMod.LOGGER.info("Blueprint client initialisé");
    }

    /**
     * Renvoie la commande au serveur. C'est ce qui fait de {@code /blueprint-edit} un
     * alias et non un second chemin : le serveur applique ses propres vérifications,
     * ses permissions et ses quotas, exactement comme si le joueur avait tapé
     * {@code /blueprint …}.
     */
    private static int forward(FabricClientCommandSource source, String command) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            source.sendFeedback(Component.translatable("blueprint.editor.cmd.multiplayer"));
            return 0;
        }
        connection.sendCommand(command);
        return Command.SINGLE_SUCCESS;
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
