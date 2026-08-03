package fr.blueprint.client;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import fr.blueprint.client.editor.BlueprintEditorScreen;
import fr.blueprint.client.editor.EditorSession;
import fr.blueprint.client.registry.ClientNodeRegistry;
import fr.blueprint.core.BlueprintManager;
import fr.blueprint.core.BlueprintMod;
import fr.blueprint.core.DemoBlueprint;
import fr.blueprint.core.graph.Blueprint;
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
import net.minecraft.server.MinecraftServer;
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
                if (lastEdited == null || !openById(mc, lastEdited, false)) {
                    openDemoEditor(mc);
                }
            }
        });

        // /blueprint-edit : liste | <id> ouvre une copie du blueprint serveur (5.9)
        // | demo | create <id>. L'ouverture est différée d'une tâche : la fermeture
        // du chat écraserait un setScreen immédiat.
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
                                    MinecraftServer server =
                                            Minecraft.getInstance().getSingleplayerServer();
                                    if (server != null) {
                                        for (Blueprint bp : BlueprintManager.of(server).all()) {
                                            builder.suggest(bp.id().toString());
                                        }
                                    }
                                    builder.suggest("demo");
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    String raw = StringArgumentType.getString(context, "id");
                                    Minecraft mc = Minecraft.getInstance();
                                    Identifier id = parseId(raw);
                                    if (id == null || !openById(mc, id, true)) {
                                        context.getSource().sendFeedback(Component.translatable(
                                                "blueprint.editor.cmd.unknown", raw, raw));
                                    }
                                    return Command.SINGLE_SUCCESS;
                                }))));

        // Synchro du registre serveur (6.2) : réception du hash puis des descripteurs.
        fr.blueprint.client.net.RegistrySync.register();

        BlueprintMod.LOGGER.info("Blueprint client initialisé");
    }

    /** Sans namespace, un identifiant nu vit sous {@code blueprint:} (comme /blueprint). */
    private static @Nullable Identifier parseId(String raw) {
        return raw.contains(":") ? Identifier.tryParse(raw)
                : Identifier.tryParse(BlueprintMod.MOD_ID + ":" + raw);
    }

    private static void list(FabricClientCommandSource source) {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            source.sendFeedback(Component.translatable("blueprint.editor.cmd.multiplayer"));
            return;
        }
        StringBuilder ids = new StringBuilder();
        for (Blueprint bp : BlueprintManager.of(server).all()) {
            if (!ids.isEmpty()) {
                ids.append(", ");
            }
            ids.append(bp.id());
        }
        source.sendFeedback(Component.translatable("blueprint.editor.cmd.list",
                ids.isEmpty() ? "—" : ids.toString()));
    }

    /**
     * Ouvre une copie d'un blueprint du serveur intégré. L'instantané se prend sur
     * le thread serveur ; l'enregistrement y retourne par {@code adopt} (5.9) — le
     * client ne mute jamais le graphe vivant du serveur.
     */
    private static boolean openById(Minecraft mc, Identifier id, boolean deferred) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            return false;
        }
        Blueprint copy;
        try {
            copy = server.submit(() ->
                    BlueprintManager.of(server).get(id).map(Blueprint::copy).orElse(null)).join();
        } catch (RuntimeException e) {
            BlueprintMod.LOGGER.error("Instantané de « {} » impossible", id, e);
            return false;
        }
        if (copy == null) {
            return false;
        }
        lastEdited = id;
        // Un adopt qui jette côté serveur ne doit JAMAIS crasher le thread client :
        // l'échec remonte en « non enregistré » (● conservé) — QA 5.9.
        EditorSession session = EditorSession.of(copy, snapshot -> {
            try {
                return server.submit(() -> BlueprintManager.of(server).adopt(snapshot)).join();
            } catch (RuntimeException e) {
                BlueprintMod.LOGGER.error("Enregistrement de « {} » impossible", id, e);
                return false;
            }
        });
        // Bouton Tester (5.6b) : après l'enregistrement, activer côté serveur.
        session.setTestHandler(() ->
                server.execute(() -> BlueprintManager.of(server).setEnabled(id, true)));
        Runnable open = () -> mc.setScreen(new BlueprintEditorScreen(session,
                BlueprintMod.registries(), fr.blueprint.client.net.RegistrySync.descriptors(),
                fr.blueprint.client.net.RegistrySync.lookup()));
        if (deferred) {
            mc.schedule(open);
        } else {
            open.run();
        }
        return true;
    }

    private static void createAndEdit(FabricClientCommandSource source, String raw) {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            source.sendFeedback(Component.translatable("blueprint.editor.cmd.multiplayer"));
            return;
        }
        Identifier id = parseId(raw);
        if (id == null) {
            source.sendFeedback(Component.translatable("blueprint.editor.cmd.unknown", raw, raw));
            return;
        }
        boolean created = server.submit(() ->
                BlueprintManager.of(server).create(id).isPresent()).join();
        if (!created) {
            source.sendFeedback(Component.translatable("blueprint.cmd.exists", id.toString()));
            return;
        }
        source.sendFeedback(Component.translatable("blueprint.cmd.created", id.toString()));
        openById(mc, id, true);
    }

    private static void openDemoEditor(Minecraft mc) {
        var registries = BlueprintMod.registries();
        mc.setScreen(new BlueprintEditorScreen(
                EditorSession.scratch(DemoBlueprint.build(registries.nodes())),
                registries, fr.blueprint.client.net.RegistrySync.descriptors(),
                fr.blueprint.client.net.RegistrySync.lookup()));
    }
}
