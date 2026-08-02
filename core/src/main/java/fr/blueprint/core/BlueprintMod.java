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
        LOGGER.info("{} plugin(s) Blueprint détecté(s) — {} type(s) de pins, {} nœud(s), {} événement(s), {} en échec",
                declared, registries.pinTypes().all().size(), registries.nodes().all().size(),
                registries.events().all().size(), registries.failedMods().size());

        // Le dispatcher d'événements vit avec le serveur : installé au démarrage
        // (avec le pont événement → ordonnanceur), retiré à l'arrêt.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            var dispatcher = new fr.blueprint.core.event.EventDispatcher(
                    new fr.blueprint.core.event.EventDispatcher.ThreadGate() {
                        @Override
                        public boolean isOnThread() {
                            return server.isSameThread();
                        }

                        @Override
                        public void submit(Runnable task) {
                            server.execute(task);
                        }
                    });
            var bridge = new fr.blueprint.core.event.BlueprintEventBridge(
                    BlueprintManager.of(server), registries.nodes(), schedulerOf(server),
                    envFactory(server));
            bridge.wire(dispatcher, registries.events().all());
            fr.blueprint.api.event.BlueprintEvents.install(dispatcher);
        });

        // Persistance (6.1) : chargement + rapport quand les mondes sont prêts, puis
        // liaison vivante — chaque sauvegarde du monde capture l'état courant.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            var storage = server.overworld().getDataStorage()
                    .computeIfAbsent(fr.blueprint.core.storage.BlueprintStorage.TYPE);
            var report = fr.blueprint.core.storage.PersistenceHooks.restore(
                    storage, BlueprintManager.of(server), schedulerOf(server), registries,
                    new fr.blueprint.core.storage.ServerRefResolver(server), envFactory(server));
            storage.bindLive(BlueprintManager.of(server), schedulerOf(server));
            LOGGER.info("Persistance : {} blueprint(s) chargé(s), {} préservé(s) brut(s), "
                            + "{} exécution(s) reprise(s), {} annulée(s)",
                    report.blueprintsLoaded(), report.blueprintsCorrupt(),
                    report.executionsResumed(), report.executionsCancelled());
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server ->
                fr.blueprint.api.event.BlueprintEvents.uninstall());

        // Fin de tick : émettre server_tick (coût nul sans abonné — paresse 2.5) puis
        // ordonnancer. Un blueprint glouton ou en faute est désactivé via le manager.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            fr.blueprint.api.event.BlueprintEvents.fire(
                    fr.blueprint.core.event.StandardEvents.SERVER_TICK, payload -> {
                    });
            schedulerOf(server).tick(config.fuelPerTick());
        });

        registerWorldEventBridges();
    }

    /** Ponts Fabric → événements Blueprint (story 7.6) — fins, vérifiés en jeu/gametest. */
    private static void registerWorldEventBridges() {
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) -> fr.blueprint.api.event.BlueprintEvents.fire(
                        fr.blueprint.core.event.StandardEvents.PLAYER_JOIN,
                        payload -> payload.set("player", handler.player)));
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> fr.blueprint.api.event.BlueprintEvents.fire(
                        fr.blueprint.core.event.StandardEvents.PLAYER_QUIT,
                        payload -> payload.set("player", handler.player)));
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register(
                (player, world, hand, hit) -> {
                    // Garde main principale (correction QA EVENT-001) : Fabric appelle le
                    // callback pour chaque main — sans garde, un clic émettrait deux événements.
                    if (hand == net.minecraft.world.InteractionHand.MAIN_HAND
                            && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                        fr.blueprint.api.event.BlueprintEvents.fire(
                                fr.blueprint.core.event.StandardEvents.PLAYER_USE_BLOCK,
                                payload -> payload.set("player", serverPlayer)
                                        .set("pos", hit.getBlockPos())
                                        .set("face", hit.getDirection()));
                    }
                    return net.minecraft.world.InteractionResult.PASS;
                });
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register(
                (player, world, hand) -> {
                    if (hand == net.minecraft.world.InteractionHand.MAIN_HAND
                            && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                        fr.blueprint.api.event.BlueprintEvents.fire(
                                fr.blueprint.core.event.StandardEvents.PLAYER_USE_ITEM,
                                payload -> payload.set("player", serverPlayer));
                    }
                    return net.minecraft.world.InteractionResult.PASS;
                });
        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.AFTER.register(
                (world, player, pos, state, blockEntity) -> {
                    if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                        fr.blueprint.api.event.BlueprintEvents.fire(
                                fr.blueprint.core.event.StandardEvents.PLAYER_BREAK_BLOCK,
                                payload -> payload.set("player", serverPlayer)
                                        .set("pos", pos.immutable()));
                    }
                });
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register(
                (entity, damageSource) -> fr.blueprint.api.event.BlueprintEvents.fire(
                        fr.blueprint.core.event.StandardEvents.ENTITY_DEATH,
                        payload -> payload.set("entity", entity)));
        net.fabricmc.fabric.api.message.v1.ServerMessageEvents.CHAT_MESSAGE.register(
                (message, sender, params) -> fr.blueprint.api.event.BlueprintEvents.fire(
                        fr.blueprint.core.event.StandardEvents.PLAYER_CHAT,
                        payload -> payload.set("player", sender)
                                .set("message", message.signedContent())));
    }

    /** Fabrique d'environnement d'exécution — partagée par le pont et la reprise (6.1). */
    private static fr.blueprint.core.event.BlueprintEventBridge.EnvFactory envFactory(
            net.minecraft.server.MinecraftServer server) {
        return (bp, trigger) -> new fr.blueprint.core.vm.ExecutionEnvironment(
                typeId -> registries.nodes().get(typeId).orElse(null),
                new fr.blueprint.api.node.BlueprintHandle() {
                    @Override
                    public net.minecraft.resources.Identifier id() {
                        return bp.id();
                    }

                    @Override
                    public boolean enabled() {
                        return bp.enabled();
                    }
                },
                trigger, varsOf(server), server, server.overworld(), LOGGER);
    }

    private static final java.util.Map<net.minecraft.server.MinecraftServer,
            fr.blueprint.core.vm.VarStore> VARS =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** Variables non locales du serveur — en mémoire pour l'instant (persistance : 6.x). */
    public static fr.blueprint.core.vm.VarStore varsOf(net.minecraft.server.MinecraftServer server) {
        return VARS.computeIfAbsent(server, s -> fr.blueprint.core.vm.VarStore.inMemory());
    }

    private static final java.util.Map<net.minecraft.server.MinecraftServer,
            fr.blueprint.core.vm.BlueprintScheduler> SCHEDULERS =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    public static fr.blueprint.core.vm.BlueprintScheduler schedulerOf(net.minecraft.server.MinecraftServer server) {
        return SCHEDULERS.computeIfAbsent(server, s ->
                new fr.blueprint.core.vm.BlueprintScheduler(config.maxOverBudgetTicks(),
                        new fr.blueprint.core.vm.BlueprintScheduler.Listener() {
                            @Override
                            public void disabled(net.minecraft.resources.Identifier blueprintId, int streakTicks) {
                                LOGGER.warn("Blueprint « {} » désactivé : budget dépassé {} ticks d'affilée",
                                        blueprintId, streakTicks);
                                BlueprintManager.of(s).setEnabled(blueprintId, false);
                            }

                            @Override
                            public void faulted(net.minecraft.resources.Identifier blueprintId,
                                                java.util.UUID node, String message) {
                                LOGGER.error("Blueprint « {} » en faute (nœud {}) : {}",
                                        blueprintId, node, message);
                                BlueprintManager.of(s).setEnabled(blueprintId, false);
                            }
                        }));
    }
}
