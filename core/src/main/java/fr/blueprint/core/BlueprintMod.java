package fr.blueprint.core;

import fr.blueprint.core.command.BlueprintCommand;
import fr.blueprint.core.config.BlueprintConfig;
import fr.blueprint.platform.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Le démarrage de Blueprint, côté serveur.
 *
 * <p>Cette classe <b>était</b> le point d'entrée Fabric ({@code implements
 * ModInitializer}). Elle ne l'est plus : c'est {@code fr.blueprint.fabric.FabricBootstrap}
 * qui l'est, et qui appelle {@link #init()}. La nuance n'est pas cosmétique — tant que le
 * démarrage <i>était</i> un point d'entrée Fabric, aucun autre chargeur ne pouvait
 * l'atteindre. Maintenant, n'importe lequel le peut, et aucun n'a de traitement de faveur.
 */
public class BlueprintMod {
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

    /** Ce que le contenu déclaré a refusé, gardé pour {@code /blueprint content}. */
    private static java.util.List<String> contentRejected = java.util.List.of();

    public static java.util.List<String> contentRejected() {
        return contentRejected;
    }

    /** Les définitions retenues, pour dire lesquelles n'ont pas d'image (11.2). */
    private static java.util.Map<net.minecraft.resources.Identifier,
            fr.blueprint.core.content.ItemDefinition> contentDeclared = java.util.Map.of();

    public static java.util.Map<net.minecraft.resources.Identifier,
            fr.blueprint.core.content.ItemDefinition> contentDeclared() {
        return contentDeclared;
    }

    /** Les blocs déclarés, pour la commande et le pack de ressources (11.3). */
    private static java.util.Map<net.minecraft.resources.Identifier,
            fr.blueprint.core.content.BlockDefinition> contentBlocks = java.util.Map.of();

    public static java.util.Map<net.minecraft.resources.Identifier,
            fr.blueprint.core.content.BlockDefinition> contentBlocks() {
        return contentBlocks;
    }

    /**
     * Lit et enregistre les items déclarés.
     *
     * <p>Journalisé même quand il n'y a rien : sur un serveur où quelqu'un vient de
     * déposer un fichier, savoir que zéro item a été lu vaut mieux qu'un silence dont on
     * ne peut pas dire s'il signifie « tout va bien » ou « le dossier n'a pas été vu ».
     */
    private static void registerDeclaredContent() {
        var report = fr.blueprint.core.content.ContentLoader.load(BlueprintPaths.content());
        var rejected = new java.util.ArrayList<>(report.rejected());
        // Gardées pour la commande : un item sans image s'enregistre très bien et
        // s'affiche en damier. C'est la question la plus posée d'un contenu déclaré, et
        // seule la définition sait y répondre (11.2). Posées ici, dès la lecture du
        // disque : elles ne dépendent d'aucun registre.
        contentDeclared = report.items();
        contentBlocks = report.blocks();
        // Le butin des blocs déclarés (11.3) n'est plus branché ici : ils n'ont pas de
        // table de butin, et c'est le chargeur qui appelle ContentDrops.afterBlockBroken
        // sur son propre événement de casse.

        // Les deux passes, et c'est le chargeur qui décide quand chacune part. Les blocs
        // d'abord : l'item d'un bloc a besoin du bloc. Cela ne change rien à la suite des
        // items — voir ContentRegistrar.itemOrder, qui est justement là pour ça.
        var registrar = Platform.registrar();
        registrar.whenOpen(net.minecraft.core.registries.Registries.BLOCK,
                () -> fr.blueprint.core.content.ContentRegistrar.registerBlocks(report, rejected));
        registrar.whenOpen(net.minecraft.core.registries.Registries.ITEM, () -> {
            var registered =
                    fr.blueprint.core.content.ContentRegistrar.registerItems(report, rejected);
            // Le bilan DANS l'action, pas après elle : sur un chargeur dont la fenêtre
            // s'ouvre plus tard, « après » signifierait « avant que quoi que ce soit ne
            // soit enregistré », et le journal annoncerait zéro item sur un dossier plein.
            contentRejected = java.util.List.copyOf(rejected);
            if (registered.isEmpty() && rejected.isEmpty()) {
                return;
            }
            // Comptés, pas déduits : un bloc refusé par la première passe n'a pas d'item,
            // et l'ancienne soustraction « tout moins les blocs déclarés » annonçait alors
            // moins d'items qu'il n'y en a — voire un nombre négatif.
            int items = 0;
            for (net.minecraft.resources.Identifier id : registered.keySet()) {
                if (report.items().containsKey(id)) {
                    items++;
                }
            }
            LOGGER.info("Contenu déclaré : {} item(s) et {} bloc(s) enregistré(s), {} écarté(s)",
                    items, registered.size() - items, rejected.size());
            rejected.forEach(reason -> LOGGER.warn("Contenu écarté — {}", reason));
        });
    }

    /**
     * Tout le démarrage serveur, appelé par le module du chargeur — et par lui seul.
     *
     * <p>Ce qu'elle fait n'a rien de spécifique à Fabric ; ce qui l'était, c'était la
     * façon d'y arriver.
     */
    public static void init() {
        LOGGER.info("Blueprint initialisé");

        // Un seul dossier, à la racine du jeu. La reprise de l'ancien emplacement
        // passe AVANT la lecture : sinon la config existante serait ignorée et
        // réécrite aux valeurs par défaut.
        BlueprintPaths.migrateLegacy();
        config = BlueprintConfig.load(BlueprintPaths.root());
        // NFR15 : l'audit des nœuds ADMIN se coupe depuis la configuration serveur.
        fr.blueprint.core.debug.AdminAudit.enabled(config.auditAdminNodes());

        // Le contenu déclaré (épic 11), AVANT tout le reste et surtout avant le gel des
        // registres : c'est la seule fenêtre où Minecraft accepte un item neuf. Après,
        // Registry.freeze() est passé et l'enregistrement lève.
        registerDeclaredContent();

        // Compté sur ce qui a RÉELLEMENT été retenu, et non sur ce que le chargeur
        // annonce : depuis le lot C, un plugin peut venir de deux voies, et un plugin
        // déclaré des deux côtés ne compte qu'une fois.
        var plugins = fr.blueprint.core.registry.PluginLoader.discover();
        registries = fr.blueprint.core.registry.PluginLoader.load(plugins, true,
                fr.blueprint.core.registry.PluginLoader.discoverHolders());
        LOGGER.info("{} plugin(s) Blueprint détecté(s) — {} type(s) de pins, {} nœud(s), {} événement(s), {} en échec",
                plugins.size(), registries.pinTypes().all().size(), registries.nodes().all().size(),
                registries.events().all().size(), registries.failedMods().size());

        registerRegistrySync();
        fr.blueprint.core.net.ServerBlueprintNet.register(config);
        fr.blueprint.core.net.DebugNet.register(config);
    }

    /**
     * Le serveur démarre. Le dispatcher d'événements vit avec lui : installé ici (avec le
     * pont événement → ordonnanceur), retiré à l'arrêt.
     */
    public static void serverStarting(net.minecraft.server.MinecraftServer server) {
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
    }

    /**
     * Les commandes du mod, posées dans le dispatcher que le chargeur fournit.
     *
     * <p>Les deux arbres ensemble : {@code /blueprint} et {@code /bpc} s'enregistraient à
     * deux endroits différents parce que deux abonnements Fabric distincts les portaient.
     * Le chargeur n'a plus qu'un fil à brancher, et l'ordre entre elles cesse de dépendre
     * de l'ordre des abonnements.
     */
    public static void registerCommands(
            com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher) {
        dispatcher.register(BlueprintCommand.build(config));
        dispatcher.register(bpcCommand());
    }

    /**
     * {@code /bpc <nom> [texte]} : déclenche les blueprints déclarant la commande (7.7).
     *
     * <p>Un préfixe fixe plutôt que des racines dynamiques : Brigadier ne sait pas retirer
     * proprement un nœud racine, {@code /bpc} suggère les noms VIVANTS.
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<
            net.minecraft.commands.CommandSourceStack> bpcCommand() {
        return net.minecraft.commands.Commands.literal("bpc")
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
                                                .getString(context, "arg")))));
    }

    /**
     * Persistance (6.1) : chargement + rapport quand les mondes sont prêts, puis liaison
     * vivante — chaque sauvegarde du monde capture l'état courant.
     */
    public static void serverStarted(net.minecraft.server.MinecraftServer server) {
        var storage = server.overworld().getDataStorage()
                .computeIfAbsent(fr.blueprint.core.storage.BlueprintStorage.TYPE);
        var report = fr.blueprint.core.storage.PersistenceHooks.restore(
                storage, BlueprintManager.of(server), schedulerOf(server), registries,
                new fr.blueprint.core.storage.ServerRefResolver(server), envFactory(server));
        storage.bindLive(BlueprintManager.of(server), schedulerOf(server));
        // Le dossier `blueprint/exports/` devient un REFLET de ce que le monde
        // contient, et non plus une photo du jour où l'on a pensé à exporter. Sans
        // cela il dérive dès l'enregistrement suivant — et l'on croit relire son
        // travail alors qu'on relit une version d'avant.
        //
        // Un reflet, pas une seconde source de vérité : rien ne relit ce dossier au
        // démarrage, et un blueprint SUPPRIMÉ n'y est pas effacé. Effacer le fichier
        // détruirait la dernière copie de quelque chose que le joueur vient de retirer
        // du monde ; le laisser ne coûte qu'un fichier réimportable.
        if (config().autoExport()) {
            // Le dossier est résolu UNE fois : BlueprintPaths.exports() crée au passage
            // deux niveaux de répertoires, et le rappeler à chaque enregistrement les
            // recréait pour rien, sur le fil serveur.
            java.nio.file.Path exports = BlueprintPaths.exports();
            // exportAsync et non export : l'écriture part sur le pool d'entrées-sorties.
            // Le contrat « au mieux, jamais au prix de l'enregistrement » n'était tenu
            // que pour les erreurs, pas pour le temps d'attente.
            BlueprintManager.of(server).mirrorWith(bp ->
                    BlueprintFiles.exportAsync(bp, exports, registries));
        }
        LOGGER.info("Persistance : {} blueprint(s) chargé(s), {} préservé(s) brut(s), "
                        + "{} exécution(s) reprise(s), {} annulée(s)",
                report.blueprintsLoaded(), report.blueprintsCorrupt(),
                report.executionsResumed(), report.executionsCancelled());
    }

    /** Le serveur s'arrête : on démonte ce que le démarrage avait installé. */
    public static void serverStopped(net.minecraft.server.MinecraftServer server) {
        fr.blueprint.api.event.BlueprintEvents.uninstall();
        // Les barres de boss sont le seul état vivant que laissent les nœuds :
        // sans ce nettoyage, elles survivraient à un rechargement sans propriétaire.
        fr.blueprint.core.nodes.BossBarNodes.clear();
        BRIDGES.remove(server);
    }

    /**
     * Fin de tick : émettre {@code server_tick} (coût nul sans abonné — paresse 2.5) puis
     * ordonnancer. Un blueprint glouton ou en faute est désactivé via le manager.
     */
    public static void endServerTick(net.minecraft.server.MinecraftServer server) {
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
        // En DERNIER : les modifications d'écran demandées pendant ce tick partent
        // ensemble (10.4, AC3b). Les envoyer plus tôt en laisserait passer une
        // partie dans la trame suivante, et l'écran se rafraîchirait en deux fois.
        fr.blueprint.core.net.ServerBlueprintNet.flushScreenUpdates(server);
        // Le débogueur pousse ses instantanés au même moment, et non plus depuis son
        // propre abonnement : un seul fil de tick à brancher côté chargeur.
        fr.blueprint.core.net.DebugNet.endServerTick(server);
    }

    /**
     * Nœuds composites des datapacks (8.2) : relus à chaque {@code /reload}. Le hash du
     * registre change alors — les clients connectés doivent le réapprendre (6.2), sinon
     * leur palette resterait sur l'ancien lot.
     *
     * <p>L'identifiant du rechargeur reste ici, avec ce qu'il fait : c'est une donnée du
     * mod, pas du chargeur.
     */
    public static final net.minecraft.resources.Identifier DATAPACK_NODES_RELOADER =
            net.minecraft.resources.Identifier.fromNamespaceAndPath(MOD_ID, "datapack_nodes");

    /** Voir {@link #DATAPACK_NODES_RELOADER}. Appelé à chaque rechargement des données. */
    public static void reloadDatapackNodes(
            net.minecraft.server.packs.resources.ResourceManager manager) {
        fr.blueprint.core.datapack.DatapackNodes.reload(manager, registries);
        registryHash = null;
        descriptorStream = null;
        announceRegistry();
    }

    /** Réannonce le hash aux joueurs connectés (après un /reload). */
    private static void announceRegistry() {
        java.util.Set<net.minecraft.server.MinecraftServer> servers;
        synchronized (BRIDGES) {
            servers = new java.util.HashSet<>(BRIDGES.keySet());
        }
        var network = Platform.serverNetwork();
        for (net.minecraft.server.MinecraftServer server : servers) {
            for (net.minecraft.server.level.ServerPlayer player
                    : server.getPlayerList().getPlayers()) {
                if (network.canSend(player,
                        fr.blueprint.core.net.BlueprintPayloads.RegistryHash.TYPE)) {
                    SYNCED.remove(player.getUUID());
                    network.send(player,
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
        var network = Platform.serverNetwork();
        network.registerS2C(fr.blueprint.core.net.BlueprintPayloads.RegistryHash.TYPE,
                fr.blueprint.core.net.BlueprintPayloads.RegistryHash.CODEC);
        network.registerS2C(fr.blueprint.core.net.BlueprintPayloads.DescriptorChunk.TYPE,
                fr.blueprint.core.net.BlueprintPayloads.DescriptorChunk.CODEC);
        network.registerC2S(fr.blueprint.core.net.BlueprintPayloads.RegistryRequest.TYPE,
                fr.blueprint.core.net.BlueprintPayloads.RegistryRequest.CODEC);
        network.registerS2C(fr.blueprint.core.net.BlueprintPayloads.ServerLimits.TYPE,
                fr.blueprint.core.net.BlueprintPayloads.ServerLimits.CODEC);

        network.receive(fr.blueprint.core.net.BlueprintPayloads.RegistryRequest.TYPE,
                (payload, context) -> {
                    // Une seule livraison par connexion : le registre est gelé, une
                    // seconde demande ne peut rien apporter (et ne coûtera rien).
                    if (!SYNCED.add(context.player().getUUID())) {
                        return;
                    }
                    java.util.List<byte[]> chunks =
                            fr.blueprint.core.net.DescriptorSync.chunks(descriptorStream());
                    for (int i = 0; i < chunks.size(); i++) {
                        context.reply(
                                new fr.blueprint.core.net.BlueprintPayloads.DescriptorChunk(
                                        i, chunks.size(), chunks.get(i)));
                    }
                    LOGGER.info("Registre envoyé à {} : {} nœud(s), {} fragment(s), {} octets",
                            context.player().getGameProfile().name(),
                            registries.nodes().all().size(), chunks.size(),
                            descriptorStream().length);
                });
    }

    /**
     * Un joueur arrive. <b>Un seul fil</b> pour le chargeur, où il y en avait deux : la
     * synchro du registre et l'événement {@code player_join} s'abonnaient séparément, et
     * leur ordre relatif ne tenait qu'à l'ordre des abonnements dans {@code init()}.
     * Il est maintenant écrit, et le même chez tous les chargeurs.
     */
    public static void playerJoined(net.minecraft.server.level.ServerPlayer player) {
        fr.blueprint.core.event.WorldEvents.playerJoined(player);
        greet(player);
    }

    /**
     * Un joueur part : il ne garde ni quota, ni écran fantôme, ni abonnement au débogueur
     * (10.3 AC5). Là encore, quatre abonnements distincts se rejoignent en un seul appel,
     * dans l'ordre où ils s'exécutaient.
     */
    public static void playerDisconnected(net.minecraft.server.level.ServerPlayer player) {
        fr.blueprint.core.event.WorldEvents.playerQuit(player);
        SYNCED.remove(player.getUUID());
        fr.blueprint.core.net.ServerBlueprintNet.forget(player.getUUID());
        fr.blueprint.core.net.DebugNet.forget(player.getUUID());
    }

    /**
     * Ce qu'un joueur reçoit en arrivant : le hash du registre, et les bornes du serveur.
     *
     * <p>Le passage par {@code ServerNetwork#send} plutôt que par l'émetteur fourni avec
     * l'événement d'arrivée n'est pas un détour : cet émetteur est un type du chargeur, et
     * il ne sert de toute façon qu'à écrire sur la connexion de ce joueur — ce que
     * {@code send} fait déjà.
     */
    private static void greet(net.minecraft.server.level.ServerPlayer player) {
        var network = Platform.serverNetwork();
        // Un client vanilla (ou sans Blueprint) ne reçoit rien : le paquet
        // serait inconnu de sa connexion.
        if (network.canSend(player,
                fr.blueprint.core.net.BlueprintPayloads.RegistryHash.TYPE)) {
            network.send(player, new fr.blueprint.core.net.BlueprintPayloads
                    .RegistryHash(registryHash()));
        }
        // Les bornes de CE serveur (10.6). Sans elles, l'éditeur validerait
        // avec les défauts du modèle : sur un serveur aux quotas resserrés,
        // l'auteur découvrirait le refus à l'enregistrement, après le
        // travail plutôt que pendant.
        if (network.canSend(player,
                fr.blueprint.core.net.BlueprintPayloads.ServerLimits.TYPE)) {
            var limits = config.graphLimits();
            network.send(player, new fr.blueprint.core.net.BlueprintPayloads
                    .ServerLimits(limits.maxNodes(), limits.maxScreens(),
                            limits.maxElementsPerScreen()));
        }
    }

    /** Joueurs déjà servis (par UUID) — remis à zéro à la déconnexion. */
    private static final java.util.Set<java.util.UUID> SYNCED =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    /** Fabrique d'environnement d'exécution — partagée par le pont et la reprise (6.1). */
    private static fr.blueprint.core.event.BlueprintEventBridge.EnvFactory envFactory(
            net.minecraft.server.MinecraftServer server) {
        return (bp, trigger) -> {
            // UNE seule résolution : varsOf traverse une WeakHashMap synchronisée, donc
            // une prise de moniteur et une purge de références faibles. L'appeler deux
            // fois par lancement les payait deux fois, et un graphe branché sur le tick
            // lance vingt fois par seconde.
            var vars = varsOf(server);
            // Le propriétaire des variables, résolu UNE fois par lancement : le blueprint
            // pour la portée GRAPH, le joueur de l'événement pour la portée PLAYER. Le
            // construire ici et non dans la boucle de la VM tient la règle « aucune
            // allocation dans step ».
            var owner = new fr.blueprint.core.vm.VarOwner(bp.id(), triggeringPlayer(trigger));
            // Les valeurs par défaut déclarées deviennent réelles ici, et nulle part
            // ailleurs : sans cette ligne, un « var double or = 20 » lu avant d'avoir
            // été écrit rendait null et faisait tomber le nœud consommateur.
            vars.seedDefaults(bp, owner);
            return new fr.blueprint.core.vm.ExecutionEnvironment(
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
                trigger, vars, owner, server, server.overworld(), LOGGER);
        };
    }

    /**
     * Le joueur de l'événement déclencheur, ou nul.
     *
     * <p>Par la charge utile plutôt que par le type d'événement : {@code Dispatch} dit à
     * qui l'événement est distribué, pas quelle valeur il porte, et les événements
     * d'interface ({@code gui_clicked}…) sont {@code GLOBAL} tout en nommant leur joueur.
     * Se fier au mode de distribution aurait privé de propriétaire exactement les
     * événements dont un menu a besoin.
     */
    private static @org.jetbrains.annotations.Nullable java.util.UUID triggeringPlayer(
            fr.blueprint.api.event.TriggerContext trigger) {
        try {
            return trigger.output("player") instanceof net.minecraft.world.entity.Entity e
                    ? e.getUUID() : null;
        } catch (RuntimeException e) {
            // L'événement ne déclare pas de « player » : ce n'est pas une anomalie.
            return null;
        }
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

    /**
     * Une touche de Blueprint devient une exécution (story 11.4).
     *
     * <p>Filtré par le littéral « key » du nœud, comme les clics le sont par « element ».
     * <b>Non ciblé</b> sur un blueprint en revanche : une touche n'appartient à personne,
     * et deux blueprints peuvent légitimement écouter le même emplacement.
     */
    public static int emitKeyPress(net.minecraft.server.MinecraftServer server,
                                   net.minecraft.server.level.ServerPlayer player, int slot) {
        var bridge = BRIDGES.get(server);
        if (bridge == null) {
            return 0;
        }
        java.util.Map<String, Object> values = new java.util.HashMap<>();
        values.put("player", player);
        values.put("key", slot);
        return bridge.launchKeyPress(slot, new fr.blueprint.core.event.TriggerContextImpl(
                fr.blueprint.core.event.StandardEvents.KEY_PRESSED, values));
    }

    /**
     * Un clic d'écran devient une exécution (story 10.4). Ciblé sur le blueprint qui
     * possède l'écran, et filtré par le littéral « element » du nœud : sans ce filtre,
     * chaque clic réveillerait chaque écouteur de chaque écran.
     */
    public static int emitGuiClick(net.minecraft.server.MinecraftServer server,
                                   net.minecraft.resources.Identifier blueprint,
                                   net.minecraft.server.level.ServerPlayer player,
                                   String screen, String element) {
        var bridge = BRIDGES.get(server);
        if (bridge == null) {
            return 0;
        }
        java.util.Map<String, Object> values = new java.util.HashMap<>();
        values.put("player", player);
        values.put("screen", screen);
        values.put("element", element);
        return bridge.launchGuiClick(blueprint, element,
                new fr.blueprint.core.event.TriggerContextImpl(
                        fr.blueprint.core.event.StandardEvents.GUI_ELEMENT_CLICKED, values));
    }

    /**
     * Les événements d'éléments riches (10.8). Une seule fabrique pour les trois : elles
     * ne diffèrent que par les sorties, et trois méthodes recopiées auraient fini par
     * diverger sur le filtrage — qui est la seule partie délicate.
     */
    private static int emitGuiEvent(net.minecraft.server.MinecraftServer server,
                                    net.minecraft.resources.Identifier blueprint,
                                    fr.blueprint.api.event.EventType event,
                                    net.minecraft.server.level.ServerPlayer player,
                                    String screen, String element,
                                    java.util.Map<String, Object> extra) {
        var bridge = BRIDGES.get(server);
        if (bridge == null) {
            return 0;
        }
        java.util.Map<String, Object> values = new java.util.HashMap<>(extra);
        values.put("player", player);
        values.put("screen", screen);
        values.put("element", element);
        return bridge.launchGuiEvent(blueprint, event, element,
                new fr.blueprint.core.event.TriggerContextImpl(event, values));
    }

    public static int emitGuiListClick(net.minecraft.server.MinecraftServer server,
                                       net.minecraft.resources.Identifier blueprint,
                                       net.minecraft.server.level.ServerPlayer player,
                                       String screen, String element, int index, String line) {
        return emitGuiEvent(server, blueprint,
                fr.blueprint.core.event.StandardEvents.GUI_LIST_CLICKED, player, screen, element,
                java.util.Map.of("index", index, "line", line));
    }

    public static int emitGuiInputChanged(net.minecraft.server.MinecraftServer server,
                                          net.minecraft.resources.Identifier blueprint,
                                          net.minecraft.server.level.ServerPlayer player,
                                          String screen, String element,
                                          String text, boolean submitted) {
        return emitGuiEvent(server, blueprint,
                fr.blueprint.core.event.StandardEvents.GUI_INPUT_CHANGED, player, screen, element,
                java.util.Map.of("text", text, "submitted", submitted));
    }

    public static int emitGuiValueChanged(net.minecraft.server.MinecraftServer server,
                                          net.minecraft.resources.Identifier blueprint,
                                          net.minecraft.server.level.ServerPlayer player,
                                          String screen, String element,
                                          double value, boolean checked) {
        return emitGuiEvent(server, blueprint,
                fr.blueprint.core.event.StandardEvents.GUI_VALUE_CHANGED, player, screen, element,
                java.util.Map.of("value", value, "checked", checked));
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
        // Depuis la sauvegarde du monde, plus en mémoire volatile : une identité de jeu de
        // rôle choisie une fois doit survivre au redémarrage du serveur, ce que la portée
        // PLAYER promet depuis toujours et ne tenait pas.
        return VARS.computeIfAbsent(server, s -> s.overworld().getDataStorage()
                .computeIfAbsent(fr.blueprint.core.storage.VarStorage.TYPE));
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
