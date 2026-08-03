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
        // NFR15 : l'audit des nœuds ADMIN se coupe depuis la configuration serveur.
        fr.blueprint.core.debug.AdminAudit.enabled(config.auditAdminNodes());

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
            BRIDGES.put(server, bridge);
            fr.blueprint.api.event.BlueprintEvents.install(dispatcher);
        });

        // /bpc <nom> [texte] : déclenche les blueprints déclarant la commande (7.7).
        // Un préfixe fixe plutôt que des racines dynamiques : Brigadier ne sait pas
        // retirer proprement un nœud racine, /bpc suggère les noms VIVANTS.
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register(
                (dispatcher, access, environment) -> dispatcher.register(
                        net.minecraft.commands.Commands.literal("bpc")
                                .then(net.minecraft.commands.Commands.argument("name",
                                                com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            var bridge = BRIDGES.get(context.getSource().getServer());
                                            if (bridge != null) {
                                                bridge.commandNames().forEach(builder::suggest);
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> runBpc(context, ""))
                                        .then(net.minecraft.commands.Commands.argument("arg",
                                                        com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                                .executes(context -> runBpc(context,
                                                        com.mojang.brigadier.arguments.StringArgumentType
                                                                .getString(context, "arg")))))));

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
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            fr.blueprint.api.event.BlueprintEvents.uninstall();
            // Les barres de boss sont le seul état vivant que laissent les nœuds :
            // sans ce nettoyage, elles survivraient à un rechargement sans propriétaire.
            fr.blueprint.core.nodes.BossBarNodes.clear();
            BRIDGES.remove(server);
        });

        // Fin de tick : émettre server_tick (coût nul sans abonné — paresse 2.5) puis
        // ordonnancer. Un blueprint glouton ou en faute est désactivé via le manager.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            fr.blueprint.api.event.BlueprintEvents.fire(
                    fr.blueprint.core.event.StandardEvents.SERVER_TICK, payload -> {
                    });
            schedulerOf(server).tick(config.fuelPerTick());
            // Après l'ordonnancement : le budget de signaux du tick repart à neuf.
            // Avant, il bornerait les signaux émis PENDANT ce tick de façon décalée.
            var bridge = BRIDGES.get(server);
            if (bridge != null) {
                bridge.endTick();
            }
        });

        registerWorldEventBridges();
        registerRegistrySync();
        fr.blueprint.core.net.ServerBlueprintNet.register(config);
        fr.blueprint.core.net.DebugNet.register(config);
        registerDatapackNodes();
    }

    /**
     * Nœuds composites des datapacks (8.2) : relus à chaque {@code /reload}. Le hash du
     * registre change alors — les clients connectés doivent le réapprendre (6.2), sinon
     * leur palette resterait sur l'ancien lot.
     */
    private static void registerDatapackNodes() {
        net.fabricmc.fabric.api.resource.v1.ResourceLoader
                .get(net.minecraft.server.packs.PackType.SERVER_DATA)
                .registerReloader(
                        net.minecraft.resources.Identifier.fromNamespaceAndPath(MOD_ID, "datapack_nodes"),
                        (net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> {
                            fr.blueprint.core.datapack.DatapackNodes.reload(manager, registries);
                            registryHash = null;
                            descriptorStream = null;
                            announceRegistry();
                        });
    }

    /** Réannonce le hash aux joueurs connectés (après un /reload). */
    private static void announceRegistry() {
        java.util.Set<net.minecraft.server.MinecraftServer> servers;
        synchronized (BRIDGES) {
            servers = new java.util.HashSet<>(BRIDGES.keySet());
        }
        for (net.minecraft.server.MinecraftServer server : servers) {
            for (net.minecraft.server.level.ServerPlayer player
                    : server.getPlayerList().getPlayers()) {
                if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player,
                        fr.blueprint.core.net.BlueprintPayloads.RegistryHash.TYPE)) {
                    SYNCED.remove(player.getUUID());
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                            new fr.blueprint.core.net.BlueprintPayloads.RegistryHash(registryHash()));
                }
            }
        }
    }

    /** Hash du registre, calculé une fois (les registres sont gelés après l'init). */
    private static String registryHash;
    /** Flux de descripteurs compressé, calculé à la première demande. */
    private static byte[] descriptorStream;

    public static synchronized String registryHash() {
        if (registryHash == null) {
            registryHash = fr.blueprint.core.registry.RegistryHash.of(registries.nodes());
        }
        return registryHash;
    }

    private static synchronized byte[] descriptorStream() {
        if (descriptorStream == null) {
            java.util.List<fr.blueprint.core.registry.NodeDescriptor> all =
                    new java.util.ArrayList<>();
            for (fr.blueprint.api.node.NodeType type : registries.nodes().all()) {
                all.add(fr.blueprint.core.registry.NodeDescriptor.of(type));
            }
            descriptorStream = fr.blueprint.core.net.DescriptorSync.toBytes(all);
        }
        return descriptorStream;
    }

    /**
     * Synchro du registre (story 6.2, FR35) : au join le serveur annonce son hash ;
     * le client ne demande les descripteurs que s'il diverge. Le flux part fragmenté
     * et compressé — un client sans les mods du serveur voit quand même les nœuds.
     */
    private static void registerRegistrySync() {
        var s2c = net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C();
        s2c.register(fr.blueprint.core.net.BlueprintPayloads.RegistryHash.TYPE,
                fr.blueprint.core.net.BlueprintPayloads.RegistryHash.CODEC);
        s2c.register(fr.blueprint.core.net.BlueprintPayloads.DescriptorChunk.TYPE,
                fr.blueprint.core.net.BlueprintPayloads.DescriptorChunk.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(
                fr.blueprint.core.net.BlueprintPayloads.RegistryRequest.TYPE,
                fr.blueprint.core.net.BlueprintPayloads.RegistryRequest.CODEC);

        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) -> {
                    // Un client vanilla (ou sans Blueprint) ne reçoit rien : le paquet
                    // serait inconnu de sa connexion.
                    if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(
                            handler.player,
                            fr.blueprint.core.net.BlueprintPayloads.RegistryHash.TYPE)) {
                        sender.sendPacket(new fr.blueprint.core.net.BlueprintPayloads
                                .RegistryHash(registryHash()));
                    }
                });

        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
                fr.blueprint.core.net.BlueprintPayloads.RegistryRequest.TYPE,
                (payload, context) -> {
                    // Une seule livraison par connexion : le registre est gelé, une
                    // seconde demande ne peut rien apporter (et ne coûtera rien).
                    if (!SYNCED.add(context.player().getUUID())) {
                        return;
                    }
                    java.util.List<byte[]> chunks =
                            fr.blueprint.core.net.DescriptorSync.chunks(descriptorStream());
                    for (int i = 0; i < chunks.size(); i++) {
                        context.responseSender().sendPacket(
                                new fr.blueprint.core.net.BlueprintPayloads.DescriptorChunk(
                                        i, chunks.size(), chunks.get(i)));
                    }
                    LOGGER.info("Registre envoyé à {} : {} nœud(s), {} fragment(s), {} octets",
                            context.player().getGameProfile().name(),
                            registries.nodes().all().size(), chunks.size(),
                            descriptorStream().length);
                });

        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> SYNCED.remove(handler.player.getUUID()));
    }

    /** Joueurs déjà servis (par UUID) — remis à zéro à la déconnexion. */
    private static final java.util.Set<java.util.UUID> SYNCED =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());

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
        registerCombatEventBridges();
        registerPlayerEventBridges();
    }

    /**
     * Combat (batch 4). Les dégâts subis sont le socle de tout script de combat :
     * sans eux, un graphe ne savait d'une entité que sa mort.
     */
    private static void registerCombatEventBridges() {
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register(
                (entity, source, baseAmount, damageTaken, blocked) ->
                        fr.blueprint.api.event.BlueprintEvents.fire(
                                fr.blueprint.core.event.StandardEvents.ENTITY_DAMAGED,
                                payload -> {
                                    // damageTaken, pas baseAmount : c'est ce que
                                    // l'entité a RÉELLEMENT encaissé, armure et
                                    // enchantements déduits.
                                    payload.set("entity", entity)
                                            .set("amount", (double) damageTaken);
                                    if (source.getEntity() != null) {
                                        payload.set("attacker", source.getEntity());
                                    }
                                }));
        net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY
                .register((world, killer, victim, source) ->
                        fr.blueprint.api.event.BlueprintEvents.fire(
                                fr.blueprint.core.event.StandardEvents.ENTITY_KILLED,
                                payload -> payload.set("killer", killer).set("victim", victim)));
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register(
                (player, world, hand, entity, hit) -> {
                    // Garde main principale, comme pour use_block (QA EVENT-001) :
                    // Fabric appelle le callback pour chaque main.
                    if (hand == net.minecraft.world.InteractionHand.MAIN_HAND
                            && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                        fr.blueprint.api.event.BlueprintEvents.fire(
                                fr.blueprint.core.event.StandardEvents.PLAYER_ATTACK_ENTITY,
                                payload -> payload.set("player", sp).set("target", entity));
                    }
                    return net.minecraft.world.InteractionResult.PASS;
                });
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register(
                (player, world, hand, entity, hit) -> {
                    if (hand == net.minecraft.world.InteractionHand.MAIN_HAND
                            && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                        fr.blueprint.api.event.BlueprintEvents.fire(
                                fr.blueprint.core.event.StandardEvents.PLAYER_USE_ENTITY,
                                payload -> payload.set("player", sp).set("target", entity));
                    }
                    return net.minecraft.world.InteractionResult.PASS;
                });
    }

    /** Cycle de vie du joueur (batch 4) : réapparition, dimension, sommeil. */
    private static void registerPlayerEventBridges() {
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register(
                (oldPlayer, newPlayer, alive) ->
                        fr.blueprint.api.event.BlueprintEvents.fire(
                                fr.blueprint.core.event.StandardEvents.PLAYER_RESPAWN,
                                // « alive » de Fabric signifie « n'est pas mort » :
                                // c'est le retour par le portail de l'End, pas une
                                // réapparition après la mort. Le nom du pin le dit.
                                payload -> payload.set("player", newPlayer)
                                        .set("end_portal", alive)));
        net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD
                .register((player, origin, destination) ->
                        fr.blueprint.api.event.BlueprintEvents.fire(
                                fr.blueprint.core.event.StandardEvents.PLAYER_CHANGE_WORLD,
                                payload -> payload.set("player", player)
                                        .set("from", origin.dimension().identifier())
                                        .set("to", destination.dimension().identifier())));
        net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents.START_SLEEPING.register(
                (entity, pos) -> {
                    // Fabric émet pour toute entité vivante ; seul un joueur dort
                    // vraiment, et un pin « player » ne peut pas recevoir un zombie.
                    if (entity instanceof net.minecraft.server.level.ServerPlayer sp) {
                        fr.blueprint.api.event.BlueprintEvents.fire(
                                fr.blueprint.core.event.StandardEvents.PLAYER_SLEEP,
                                payload -> payload.set("player", sp).set("pos", pos.immutable()));
                    }
                });
        net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents.STOP_SLEEPING.register(
                (entity, pos) -> {
                    if (entity instanceof net.minecraft.server.level.ServerPlayer sp) {
                        fr.blueprint.api.event.BlueprintEvents.fire(
                                fr.blueprint.core.event.StandardEvents.PLAYER_WAKE_UP,
                                payload -> payload.set("player", sp));
                    }
                });
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

    /** Pont événements par serveur — consulté par /bpc (7.7). */
    private static final java.util.Map<net.minecraft.server.MinecraftServer,
            fr.blueprint.core.event.BlueprintEventBridge> BRIDGES =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /**
     * Émet un signal nommé vers les blueprints qui l'écoutent (nœud
     * {@code signal/emit} et commande {@code /blueprint signal}).
     *
     * @return faux si le budget de signaux du tick est épuisé — l'appelant doit le
     *         dire à l'auteur, pas l'avaler : c'est le symptôme d'une boucle.
     */
    public static boolean emitSignal(net.minecraft.server.MinecraftServer server,
                                     String name, String payload) {
        var bridge = BRIDGES.get(server);
        if (bridge == null) {
            return true; // pas de pont = pas d'écouteur ; ce n'est pas une faute
        }
        java.util.Map<String, Object> values = new java.util.HashMap<>();
        values.put("payload", payload);
        return bridge.launchSignal(name, new fr.blueprint.core.event.TriggerContextImpl(
                fr.blueprint.core.event.StandardEvents.SIGNAL, values)) >= 0;
    }

    /** Le nombre de blueprints qui écoutent ce signal — pour le retour de la commande. */
    public static int signalListeners(net.minecraft.server.MinecraftServer server, String name) {
        var bridge = BRIDGES.get(server);
        return bridge == null ? 0 : bridge.signalListeners(name);
    }

    /** Exécute /bpc <nom> [texte] : lance les blueprints déclarant la commande. */
    private static int runBpc(
            com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> context,
            String arg) {
        var source = context.getSource();
        var bridge = BRIDGES.get(source.getServer());
        String name = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "name");
        if (bridge == null) {
            return 0;
        }
        java.util.Map<String, Object> values = new java.util.HashMap<>();
        values.put("name", name);
        values.put("arg", arg);
        if (source.getPlayer() != null) {
            values.put("player", source.getPlayer());
        }
        int launched = bridge.launchCommand(name, new fr.blueprint.core.event.TriggerContextImpl(
                fr.blueprint.core.event.StandardEvents.COMMAND, values));
        if (launched == 0) {
            source.sendFailure(net.minecraft.network.chat.Component.translatable(
                    "blueprint.cmd.bpc_unknown", name));
        } else {
            final int count = launched;
            source.sendSuccess(() -> net.minecraft.network.chat.Component.translatable(
                    "blueprint.cmd.bpc_launched", name, count), false);
        }
        return launched;
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
