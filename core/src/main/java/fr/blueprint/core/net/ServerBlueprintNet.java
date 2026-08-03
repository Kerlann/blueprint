package fr.blueprint.core.net;

import fr.blueprint.core.BlueprintManager;
import fr.blueprint.core.BlueprintMod;
import fr.blueprint.core.config.BlueprintConfig;
import fr.blueprint.core.graph.Blueprint;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Ouverture et enregistrement des blueprints par paquets (story 6.3). Le même chemin
 * sert en solo et en multi : le client n'accède plus jamais au gestionnaire du serveur
 * en direct — plus de {@code submit().join()} sur le fil de rendu, et le verrou
 * optimiste s'applique partout.
 *
 * <p>Ce que le serveur accorde est décidé ICI, jamais côté client : la lecture est
 * ouverte (comme {@code /blueprint info}), l'écriture exige la permission
 * d'administration configurée. Un client peut demander n'importe quoi, il n'obtient
 * que ce que ces gardes autorisent (bornes de taille et limitation de taux : 6.4).
 */
public final class ServerBlueprintNet {

    private ServerBlueprintNet() {
    }

    public static void register(BlueprintConfig config) {
        // Quotas et bornes : ce que dit la configuration serveur (9.3), pas des constantes.
        CONFIG = config;
        LIMITS = config.netLimits();
        SAVES = new RateLimiter(LIMITS.savesPerWindow(), LIMITS.windowMillis(),
                System::currentTimeMillis);
        REQUESTS = new RateLimiter(LIMITS.requestsPerWindow(), LIMITS.windowMillis(),
                System::currentTimeMillis);

        var s2c = PayloadTypeRegistry.playS2C();
        var c2s = PayloadTypeRegistry.playC2S();
        s2c.register(BlueprintPayloads.ListData.TYPE, BlueprintPayloads.ListData.CODEC);
        s2c.registerLarge(BlueprintPayloads.GraphData.TYPE, BlueprintPayloads.GraphData.CODEC,
                CHUNK_BYTES);
        s2c.register(BlueprintPayloads.SaveAck.TYPE, BlueprintPayloads.SaveAck.CODEC);
        c2s.register(BlueprintPayloads.ListRequest.TYPE, BlueprintPayloads.ListRequest.CODEC);
        c2s.register(BlueprintPayloads.OpenRequest.TYPE, BlueprintPayloads.OpenRequest.CODEC);
        c2s.register(BlueprintPayloads.CreateRequest.TYPE, BlueprintPayloads.CreateRequest.CODEC);
        c2s.registerLarge(BlueprintPayloads.SaveRequest.TYPE, BlueprintPayloads.SaveRequest.CODEC,
                CHUNK_BYTES);
        c2s.register(BlueprintPayloads.SetEnabled.TYPE, BlueprintPayloads.SetEnabled.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(BlueprintPayloads.ListRequest.TYPE,
                (payload, context) -> {
                    if (!allowed(REQUESTS, context, "liste")) {
                        return;
                    }
                    List<Identifier> ids = new ArrayList<>();
                    BlueprintManager.of(context.server()).all()
                            .forEach(bp -> ids.add(bp.id()));
                    context.responseSender().sendPacket(new BlueprintPayloads.ListData(
                            ids, mayEdit(config, context)));
                });

        c2s.register(BlueprintPayloads.FileListRequest.TYPE,
                BlueprintPayloads.FileListRequest.CODEC);
        s2c.register(BlueprintPayloads.FileList.TYPE, BlueprintPayloads.FileList.CODEC);
        s2c.register(BlueprintPayloads.OpenBrowser.TYPE, BlueprintPayloads.OpenBrowser.CODEC);
        c2s.register(BlueprintPayloads.ImportRequest.TYPE, BlueprintPayloads.ImportRequest.CODEC);

        // Les fichiers importables. Réservé à qui peut éditer : la liste des fichiers
        // du serveur n'est pas une information publique.
        ServerPlayNetworking.registerGlobalReceiver(BlueprintPayloads.FileListRequest.TYPE,
                (payload, context) -> {
                    if (!mayEdit(config, context) || !allowed(REQUESTS, context, "fichiers")) {
                        return;
                    }
                    context.responseSender().sendPacket(
                            new BlueprintPayloads.FileList(fr.blueprint.core.BlueprintFiles
                                    .listExports(fr.blueprint.core.BlueprintPaths.exports())));
                });

        ServerPlayNetworking.registerGlobalReceiver(BlueprintPayloads.ImportRequest.TYPE,
                (payload, context) -> {
                    if (!mayEdit(config, context) || !allowed(SAVES, context, "import")) {
                        deny(context, Identifier.withDefaultNamespace("import"),
                                BlueprintPayloads.SaveStatus.DENIED, -1);
                        return;
                    }
                    Blueprint imported = fr.blueprint.core.BlueprintFiles.importFile(
                            fr.blueprint.core.BlueprintPaths.exports(), payload.file(),
                            BlueprintMod.registries());
                    // Le garde s'applique à un fichier comme à un paquet : un .bp posé
                    // à la main est du contenu extérieur, exactement comme le réseau.
                    if (imported == null || !GraphGuard.inspect(imported.id(), imported,
                            BlueprintMod.registries().nodes(), LIMITS).accepted()) {
                        deny(context, Identifier.withDefaultNamespace("import"),
                                BlueprintPayloads.SaveStatus.INVALID, -1);
                        return;
                    }
                    BlueprintManager manager = BlueprintManager.of(context.server());
                    if (!manager.adopt(imported)) {
                        // Déjà présent : l'ouvrir plutôt que refuser. Le joueur voulait
                        // travailler dessus, pas apprendre qu'il existe.
                        Blueprint existing = manager.get(imported.id()).orElse(null);
                        if (existing != null) {
                            sendGraph(context, existing, true);
                            return;
                        }
                        deny(context, imported.id(),
                                BlueprintPayloads.SaveStatus.INVALID, -1);
                        return;
                    }
                    BlueprintMod.LOGGER.info("Blueprint « {} » importé par {}",
                            imported.id(), name(context));
                    sendGraph(context, imported, true);
                });

        ServerPlayNetworking.registerGlobalReceiver(BlueprintPayloads.OpenRequest.TYPE,
                (payload, context) -> {
                    if (!allowed(REQUESTS, context, "ouverture")) {
                        return;
                    }
                    Blueprint bp = BlueprintManager.of(context.server())
                            .get(payload.blueprint()).orElse(null);
                    if (bp == null) {
                        deny(context, payload.blueprint(),
                                BlueprintPayloads.SaveStatus.UNKNOWN, -1);
                        return;
                    }
                    sendGraph(context, bp, mayEdit(config, context));
                });

        ServerPlayNetworking.registerGlobalReceiver(BlueprintPayloads.CreateRequest.TYPE,
                (payload, context) -> {
                    if (!mayEdit(config, context)
                            || !allowed(SAVES, context, "création")) {
                        deny(context, payload.blueprint(),
                                BlueprintPayloads.SaveStatus.DENIED, -1);
                        return;
                    }
                    Blueprint created = BlueprintManager.of(context.server())
                            .create(payload.blueprint()).orElse(null);
                    if (created == null) {
                        deny(context, payload.blueprint(),
                                BlueprintPayloads.SaveStatus.INVALID, -1);
                        return;
                    }
                    BlueprintMod.LOGGER.info("Blueprint « {} » créé par {}",
                            created.id(), context.player().getGameProfile().name());
                    sendGraph(context, created, true);
                });

        ServerPlayNetworking.registerGlobalReceiver(BlueprintPayloads.SaveRequest.TYPE,
                (payload, context) -> {
                    Identifier id = payload.blueprint();
                    if (!mayEdit(config, context)
                            || !allowed(SAVES, context, "enregistrement")) {
                        deny(context, id, BlueprintPayloads.SaveStatus.DENIED, -1);
                        return;
                    }
                    // Taille : bornée AVANT toute décompression (AC1).
                    if (payload.data().length > LIMITS.maxGraphBytes()) {
                        BlueprintMod.LOGGER.warn(
                                "Enregistrement de « {} » refusé à {} : {} octets (max {})",
                                id, name(context), payload.data().length, LIMITS.maxGraphBytes());
                        deny(context, id, BlueprintPayloads.SaveStatus.INVALID, -1);
                        return;
                    }
                    Blueprint snapshot = GraphSync.fromBytes(payload.data(),
                            typeId -> BlueprintMod.registries().pinTypes()
                                    .get(typeId).orElse(null));
                    if (snapshot == null) {
                        deny(context, id, BlueprintPayloads.SaveStatus.INVALID, -1);
                        return;
                    }
                    // SEC-001 : un plafond de permission plus haut que ce que le joueur
                    // peut accorder ferait tourner des nœuds ADMIN chez qui ouvre
                    // l'édition à tous.
                    if (!GraphGuard.capAllowed(snapshot, grantableCap(context.player()))) {
                        BlueprintMod.LOGGER.warn(
                                "Enregistrement de « {} » refusé à {} : plafond {} non accordable",
                                id, name(context), snapshot.meta().permissionCap());
                        deny(context, id, BlueprintPayloads.SaveStatus.DENIED, -1);
                        return;
                    }
                    // Tout ce qui arrive du réseau repasse devant le garde (AC2) :
                    // identifiant annoncé, bornes, liens pendants, câblage.
                    GraphGuard.Verdict verdict = GraphGuard.inspect(id, snapshot,
                            BlueprintMod.registries().nodes(), LIMITS);
                    if (!verdict.accepted()) {
                        BlueprintMod.LOGGER.warn("Enregistrement de « {} » refusé à {} : {}",
                                id, name(context), verdict.reason());
                        deny(context, id, BlueprintPayloads.SaveStatus.INVALID, -1);
                        return;
                    }
                    BlueprintManager manager = BlueprintManager.of(context.server());
                    BlueprintManager.SaveResult result =
                            manager.save(snapshot, payload.baseRevision());
                    context.responseSender().sendPacket(new BlueprintPayloads.SaveAck(
                            id, statusOf(result.outcome()), result.revision()));
                    if (result.outcome() == BlueprintManager.SaveOutcome.CONFLICT) {
                        // Resynchro CIBLÉE (AC3) : le client reçoit l'état courant du
                        // serveur ; son travail local, lui, n'est pas touché.
                        manager.get(id).ifPresent(current -> sendGraph(context, current, true));
                        BlueprintMod.LOGGER.info(
                                "Enregistrement de « {} » refusé : révision {} attendue, {} reçue",
                                id, result.revision(), payload.baseRevision());
                    }
                });

        ServerPlayNetworking.registerGlobalReceiver(BlueprintPayloads.SetEnabled.TYPE,
                (payload, context) -> {
                    if (!mayEdit(config, context)
                            || !allowed(SAVES, context, "activation")) {
                        deny(context, payload.blueprint(),
                                BlueprintPayloads.SaveStatus.DENIED, -1);
                        return;
                    }
                    BlueprintManager.of(context.server())
                            .setEnabled(payload.blueprint(), payload.enabled());
                });

        s2c.register(BlueprintPayloads.ScreenOpen.TYPE, BlueprintPayloads.ScreenOpen.CODEC);
        s2c.register(BlueprintPayloads.ScreenClose.TYPE, BlueprintPayloads.ScreenClose.CODEC);
        c2s.register(BlueprintPayloads.ScreenClose.TYPE, BlueprintPayloads.ScreenClose.CODEC);

        s2c.register(BlueprintPayloads.ScreenUpdates.TYPE, BlueprintPayloads.ScreenUpdates.CODEC);
        c2s.register(BlueprintPayloads.ScreenInteraction.TYPE,
                BlueprintPayloads.ScreenInteraction.CODEC);
        CLICKS = new RateLimiter(LIMITS.clicksPerWindow(), LIMITS.windowMillis(),
                System::currentTimeMillis);
        OPENS = new RateLimiter(LIMITS.opensPerWindow(), LIMITS.windowMillis(),
                System::currentTimeMillis);

        // Le joueur a fermé son écran (Échap). Sans ce message, le serveur croirait
        // l'écran encore ouvert et accepterait des clics dessus longtemps après.
        ServerPlayNetworking.registerGlobalReceiver(BlueprintPayloads.ScreenClose.TYPE,
                (payload, context) -> {
                    var open = SCREENS.of(context.player().getUUID());
                    if (SCREENS.closed(context.player().getUUID()) && open != null) {
                        fr.blueprint.api.event.BlueprintEvents.fire(
                                fr.blueprint.core.event.StandardEvents.GUI_CLOSED,
                                payload2 -> payload2.set("player", context.player())
                                        .set("screen", open.screen()));
                    }
                });

        ServerPlayNetworking.registerGlobalReceiver(BlueprintPayloads.ScreenInteraction.TYPE,
                (payload, context) -> receiveClick(context.player(), payload));

        // Un joueur parti ne garde ni quota ni écran fantôme (10.3, AC5).
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> {
                    SAVES.forget(handler.player.getUUID());
                    REQUESTS.forget(handler.player.getUUID());
                    SCREENS.forget(handler.player.getUUID());   // HUD compris (10.9)
                });
    }

    /** Les écrans ouverts, par joueur (story 10.3) — un seul chacun. */
    private static final ScreenSessions SCREENS = new ScreenSessions();

    /** Cadence des ouvertures, par joueur CIBLÉ (AC5b) — voir openScreen. */
    private static RateLimiter OPENS = new RateLimiter(
            NetLimits.DEFAULT.opensPerWindow(), NetLimits.DEFAULT.windowMillis(),
            System::currentTimeMillis);

    /** Cadence des clics, par joueur (AC5) : un clic est bon marché, mille non. */
    private static RateLimiter CLICKS = new RateLimiter(
            NetLimits.DEFAULT.clicksPerWindow(), NetLimits.DEFAULT.windowMillis(),
            System::currentTimeMillis);

    /**
     * Un clic reçu — le MÊME chemin que celui du récepteur de paquets, exposé pour que
     * le gametest exerce la vraie validation et non une imitation.
     *
     * <p><b>Rien de ce paquet n'est cru</b> (FR52) : le client annonce ce
     * qu'il veut, et chacune des cinq vérifications suivantes existe parce qu'un client
     * modifié peut mentir sur ce point précis.
     *
     * <p>Le contrôle de visibilité et d'activation n'est pas du zèle : un auteur qui
     * masque le bouton « acheter » tant que le joueur n'a pas l'argent compte sur ce
     * masquage comme sur une règle. Sans cette vérification, elle ne serait qu'un
     * effet visuel contournable en une ligne de mod client.
     */
    public static void receiveClick(ServerPlayer player,
                                    BlueprintPayloads.ScreenInteraction click) {
        // 1. La cadence, avant tout le reste : le refus doit coûter moins que l'attaque.
        if (!CLICKS.allow(player.getUUID())) {
            BlueprintMod.LOGGER.warn("Clics d'écran de {} au-delà du quota — ignorés",
                    player.getGameProfile().name());
            return;
        }
        // 2. Cet écran est-il bien celui que LE SERVEUR a envoyé à ce joueur ?
        var open = SCREENS.of(player.getUUID());
        if (open == null || !open.blueprint().equals(click.blueprint())
                || !open.screen().equals(click.screen())) {
            return;
        }
        // 3. Est-ce la même OUVERTURE ? Un clic parti juste avant une fermeture ne doit
        //    pas s'appliquer à l'écran suivant.
        if (open.instance() != click.instance()) {
            return;
        }
        net.minecraft.server.MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        Blueprint bp = BlueprintManager.of(server).get(click.blueprint()).orElse(null);
        var screen = bp == null || !bp.enabled() ? null : bp.screen(click.screen());
        if (screen == null) {
            return;
        }
        // 4. L'élément existe-t-il, et est-il CLIQUABLE ?
        var element = screen.element(click.element());
        if (element == null || !element.kind().interactive()) {
            return;
        }
        // 5. Est-il visible et activé — parent masqué compris ? Un menu à onglets
        //    masque des pages entières ; cliquer au travers d'une page cachée
        //    déclencherait un bouton que le joueur ne voit pas.
        if (!element.enabled() || !visibleThroughParents(screen, element)) {
            return;
        }
        // Ciblé et filtré : le nœud écoute UN élément d'UN blueprint. Passer par le
        // répartiteur générique réveillerait tous les nœuds gui_clicked du serveur.
        BlueprintMod.emitGuiClick(server, click.blueprint(), player,
                click.screen(), click.element());
    }

    /** Visible, et tous ses ancêtres aussi. Résistant aux cycles d'une sauvegarde abîmée. */
    private static boolean visibleThroughParents(
            fr.blueprint.core.graph.screen.Screen screen,
            fr.blueprint.core.graph.screen.ScreenElement element) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        var cursor = element;
        while (cursor != null && seen.add(cursor.name())) {
            if (!cursor.visible()) {
                return false;
            }
            cursor = cursor.parent() == null ? null : screen.element(cursor.parent());
        }
        return true;
    }

    public static ScreenSessions screens() {
        return SCREENS;
    }

    /**
     * Ouvre un écran chez un joueur. Rend faux si le blueprint ou l'écran n'existe pas :
     * l'appelant (un nœud du graphe, 10.4) doit pouvoir le signaler comme une faute
     * plutôt que d'échouer en silence.
     */
    public static boolean openScreen(net.minecraft.server.level.ServerPlayer player,
                                     Identifier blueprintId, String screenName) {
        net.minecraft.server.MinecraftServer server = player.level().getServer();
        if (server == null) {
            return false;
        }
        var bp = fr.blueprint.core.BlueprintManager.of(server).get(blueprintId).orElse(null);
        fr.blueprint.core.graph.screen.Screen screen =
                bp == null ? null : bp.screen(screenName);
        if (screen == null) {
            return false;
        }
        // Le numéro d'instance voyage avec l'ouverture : c'est lui qui permettra au
        // client de jeter une mise à jour destinée à un écran déjà refermé.
        // Cadence bornée PAR JOUEUR CIBLÉ (AC5b) : sans elle, un blueprint qui rouvre
        // un menu plein écran à chaque tick sortirait le joueur du jeu — il ne pourrait
        // plus rien faire d'autre que refermer une fenêtre qui revient aussitôt.
        if (!OPENS.allow(player.getUUID())) {
            return false;
        }
        int instance = SCREENS.opened(player.getUUID(), blueprintId, screenName);
        ServerPlayNetworking.send(player, new BlueprintPayloads.ScreenOpen(
                blueprintId, screenName, instance, ScreenSync.toBytes(screen)));
        fr.blueprint.api.event.BlueprintEvents.fire(
                fr.blueprint.core.event.StandardEvents.GUI_OPENED,
                payload -> payload.set("player", player).set("screen", screenName));
        return true;
    }

    /**
     * Empile une modification pour ce joueur. Elle ne part pas tout de suite : les
     * modifications d'un tick s'accumulent et sortent ensemble (AC3b), et ce qui est
     * identique à ce que le client affiche déjà ne sort pas du tout (AC3c).
     */
    public static void queueUpdate(net.minecraft.server.level.ServerPlayer player,
                                   fr.blueprint.core.graph.screen.ScreenUpdate update) {
        SCREENS.queue(player.getUUID(), resolveScreen(player, update));
    }

    /**
     * Un écran vide désigne le <b>modal ouvert</b>. Le résoudre ici plutôt que côté
     * client : le serveur sait ce qu'il a envoyé, le client ne fait qu'appliquer.
     */
    private static fr.blueprint.core.graph.screen.ScreenUpdate resolveScreen(
            net.minecraft.server.level.ServerPlayer player,
            fr.blueprint.core.graph.screen.ScreenUpdate update) {
        if (!update.screen().isEmpty()) {
            return update;
        }
        var open = SCREENS.of(player.getUUID());
        return open == null ? update : new fr.blueprint.core.graph.screen.ScreenUpdate(
                open.screen(), update.element(), update.kind(),
                update.text(), update.flag(), update.number());
    }

    /**
     * Affiche un HUD chez un joueur (story 10.9). Faux si l'écran n'existe pas ou
     * <b>n'est pas déclaré HUD</b> : afficher un écran modal en permanence par-dessus
     * le jeu est exactement le piège que cette story existe pour fermer.
     */
    public static boolean showHud(net.minecraft.server.level.ServerPlayer player,
                                  Identifier blueprintId, String screenName) {
        net.minecraft.server.MinecraftServer server = player.level().getServer();
        if (server == null) {
            return false;
        }
        var bp = BlueprintManager.of(server).get(blueprintId).orElse(null);
        fr.blueprint.core.graph.screen.Screen screen =
                bp == null ? null : bp.screen(screenName);
        if (screen == null || !screen.hud()) {
            return false;
        }
        SCREENS.showHud(player.getUUID(), blueprintId, screenName);
        ServerPlayNetworking.send(player, new BlueprintPayloads.ScreenOpen(
                blueprintId, screenName, 0, ScreenSync.toBytes(screen)));
        return true;
    }

    public static void hideHud(net.minecraft.server.level.ServerPlayer player, String screenName) {
        if (SCREENS.hideHud(player.getUUID(), screenName)) {
            ServerPlayNetworking.send(player, new BlueprintPayloads.ScreenClose(screenName));
        }
    }

    public static void hideAllHuds(net.minecraft.server.level.ServerPlayer player) {
        if (SCREENS.hideAllHuds(player.getUUID())) {
            ServerPlayNetworking.send(player,
                    new BlueprintPayloads.ScreenClose(BlueprintPayloads.ALL_HUDS));
        }
    }

    /** La même chose pour tous ceux qui regardent cet écran (AC3d). */
    public static void queueUpdateForAll(Identifier blueprintId, String screenName,
                                         fr.blueprint.core.graph.screen.ScreenUpdate update) {
        for (java.util.UUID uuid : SCREENS.viewersOf(blueprintId, screenName)) {
            SCREENS.queue(uuid, update);
        }
    }

    /**
     * Envoie les modifications accumulées, une trame par joueur. Appelé en <b>fin de
     * tick</b> : c'est ce regroupement qui fait qu'un panneau de cinq éléments coûte un
     * paquet et non cinq.
     */
    public static void flushScreenUpdates(net.minecraft.server.MinecraftServer server) {
        for (java.util.UUID uuid : SCREENS.pendingPlayers()) {
            // Le joueur d'abord : vider la file marque les modifications comme
            // AFFICHÉES. Le faire avant de savoir qu'on peut envoyer laisserait la
            // comparaison croire à jour un client qui n'a jamais rien reçu.
            var player = server.getPlayerList().getPlayer(uuid);
            if (player == null) {
                continue;
            }
            // Le numéro d'instance ne concerne que le MODAL — c'est lui qui peut être
            // refermé puis rouvert entre l'envoi et l'arrivée. Un joueur qui n'a qu'un
            // HUD n'en a pas, et exiger un modal ici rendait toutes les mises à jour
            // de HUD silencieuses : la file se vidait sans que rien ne parte.
            var open = SCREENS.of(uuid);
            var updates = SCREENS.drain(uuid);
            if (!updates.isEmpty()) {
                ServerPlayNetworking.send(player, new BlueprintPayloads.ScreenUpdates(
                        open == null ? 0 : open.instance(), updates));
            }
        }
    }

    /**
     * Envoie un graphe à un joueur pour qu'il l'édite — le chemin de
     * {@code /blueprint edit <id>}. Rend faux si le blueprint n'existe pas.
     */
    public static boolean sendForEditing(ServerPlayer player, Identifier blueprintId) {
        var server = player.level().getServer();
        if (server == null) {
            return false;
        }
        Blueprint bp = BlueprintManager.of(server).get(blueprintId).orElse(null);
        if (bp == null) {
            return false;
        }
        ServerPlayNetworking.send(player, new BlueprintPayloads.GraphData(
                bp.id(), bp.revision(), mayEdit(CONFIG, player), GraphSync.toBytes(bp)));
        return true;
    }

    /** Ouvre le navigateur chez un joueur, avec de quoi le remplir. */
    public static void openBrowser(ServerPlayer player) {
        announceList(player.level().getServer());
        ServerPlayNetworking.send(player, new BlueprintPayloads.OpenBrowser());
    }

    /** Referme l'écran d'un joueur et le lui dit. */
    public static void closeScreen(net.minecraft.server.level.ServerPlayer player) {
        var open = SCREENS.of(player.getUUID());
        if (SCREENS.closed(player.getUUID())) {
            ServerPlayNetworking.send(player, BlueprintPayloads.ScreenClose.modal());
            fireClosed(player, open);
        }
    }

    /**
     * L'événement de fermeture, émis depuis <b>tous</b> les chemins : Échap, ordre du
     * serveur, blueprint désactivé, déconnexion.
     *
     * <p>Un seul endroit, parce qu'un graphe qui prépare quelque chose à l'ouverture
     * n'a que cet événement pour le défaire. Le déclencher depuis le seul chemin
     * « Échap » le ferait fuir sur tous les autres — et la fuite serait invisible.
     */
    private static void fireClosed(net.minecraft.server.level.ServerPlayer player,
                                   ScreenSessions.@org.jetbrains.annotations.Nullable Open open) {
        if (open == null) {
            return;
        }
        fr.blueprint.api.event.BlueprintEvents.fire(
                fr.blueprint.core.event.StandardEvents.GUI_CLOSED,
                payload -> payload.set("player", player).set("screen", open.screen()));
    }

    /**
     * Le blueprint vient d'être réenregistré (AC5d) : ce que les joueurs regardent
     * n'existe plus tel quel. On leur renvoie la description à jour, ou on ferme si
     * l'écran a disparu de la nouvelle version.
     *
     * <p>L'ouverture est <b>renumérotée</b> au passage : les mises à jour déjà en vol,
     * calculées pour l'ancienne description, seront jetées par le client.
     */
    public static void refreshScreensOf(net.minecraft.server.MinecraftServer server,
                                        Blueprint updated) {
        for (java.util.UUID uuid : List.copyOf(SCREENS.viewersOfBlueprint(updated.id()))) {
            var player = server.getPlayerList().getPlayer(uuid);
            var open = SCREENS.of(uuid);
            if (player == null || open == null) {
                continue;
            }
            var screen = updated.screen(open.screen());
            if (screen == null) {
                closeScreen(player);
            } else {
                openScreen(player, updated.id(), open.screen());
            }
        }
    }

    /**
     * Referme chez tous les joueurs les écrans d'un blueprint qui vient d'être désactivé
     * ou rechargé. Un menu qui reste affiché en pointant un blueprint disparu refuserait
     * chaque clic sans que le joueur comprenne pourquoi.
     */
    public static void closeScreensOf(net.minecraft.server.MinecraftServer server,
                                      Identifier blueprint) {
        for (java.util.UUID uuid : SCREENS.closeAllOf(blueprint)) {
            var player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                ServerPlayNetworking.send(player, BlueprintPayloads.ScreenClose.modal());
            }
        }
        // Les HUD aussi : un HUD n'a pas d'Échap, donc en laisser un qui pointe un
        // graphe désactivé le rendrait indélogeable autrement qu'à la touche F7.
        SCREENS.takeHudsOf(blueprint).forEach((uuid, screens) -> {
            var player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                screens.forEach(screen -> ServerPlayNetworking.send(player,
                        new BlueprintPayloads.ScreenClose(screen)));
            }
        });
    }

    /** Bornes réseau, issues de la configuration serveur (9.3). */
    private static NetLimits LIMITS = NetLimits.DEFAULT;

    private static RateLimiter SAVES = new RateLimiter(
            LIMITS.savesPerWindow(), LIMITS.windowMillis(), System::currentTimeMillis);
    private static RateLimiter REQUESTS = new RateLimiter(
            LIMITS.requestsPerWindow(), LIMITS.windowMillis(), System::currentTimeMillis);

    /**
     * Quota par joueur. Un dépassement est journalisé une fois sur dix : un flot de
     * paquets ne doit pas devenir un flot de lignes de journal (le déni de service se
     * déplacerait sur le disque).
     */
    private static boolean allowed(RateLimiter limiter, ServerPlayNetworking.Context context,
                                   String what) {
        if (limiter.allow(context.player().getUUID())) {
            return true;
        }
        if (DROPPED.incrementAndGet() % 10 == 1) {
            BlueprintMod.LOGGER.warn("Quota réseau dépassé par {} ({}) — paquet ignoré",
                    name(context), what);
        }
        return false;
    }

    private static final java.util.concurrent.atomic.AtomicLong DROPPED =
            new java.util.concurrent.atomic.AtomicLong();

    private static String name(ServerPlayNetworking.Context context) {
        return context.player().getGameProfile().name();
    }

    /** Taille des tranches des paquets scindés par Fabric (graphe et instantané). */
    private static final int CHUNK_BYTES = 28_000;

    /**
     * Plafond de permission qu'un joueur peut accorder à un blueprint (SEC-001) :
     * {@code ADMIN} pour un opérateur, {@code WORLD} sinon — poser des blocs reste
     * l'usage normal du mod, exécuter des commandes arbitraires ne l'est pas.
     */
    private static fr.blueprint.api.node.Permission grantableCap(ServerPlayer player) {
        return player.permissions().hasPermission(
                net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)
                ? fr.blueprint.api.node.Permission.ADMIN
                : fr.blueprint.api.node.Permission.WORLD;
    }

    /**
     * Écriture = permission d'administration configurée (null = ouvert à tous).
     *
     * <p>Exception qui n'en est pas une : <b>le propriétaire d'un monde solo</b> peut
     * toujours éditer ses blueprints. Sans ça, un joueur qui crée un monde sans cocher
     * « autoriser les tricheurs » a le niveau 0 et ne peut plus enregistrer SES graphes
     * dans SON monde — la permission est là pour arbitrer entre joueurs d'un serveur,
     * pas pour se protéger de soi-même.
     */
    /**
     * Prévient tous les joueurs que la liste des blueprints a changé.
     *
     * <p>Sans cela, {@code /blueprint-edit} suggérait la liste reçue <b>à la
     * connexion</b> et rien d'autre : créer un blueprint avec {@code /blueprint create}
     * ou charger les exemples ne se voyait pas côté client, et les deux commandes
     * avaient l'air de parler de deux mondes différents. Elles parlaient bien des mêmes
     * blueprints — le client en avait simplement une photo périmée.
     */
    public static void announceList(net.minecraft.server.MinecraftServer server) {
        List<Identifier> ids = new ArrayList<>();
        BlueprintManager.of(server).all().forEach(bp -> ids.add(bp.id()));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ServerPlayNetworking.canSend(player, BlueprintPayloads.ListData.TYPE)) {
                ServerPlayNetworking.send(player,
                        new BlueprintPayloads.ListData(ids, mayEdit(CONFIG, player)));
            }
        }
    }

    /** La configuration en vigueur — mémorisée pour les envois hors requête. */
    private static BlueprintConfig CONFIG = BlueprintConfig.DEFAULT;

    private static boolean mayEdit(BlueprintConfig config, ServerPlayNetworking.Context context) {
        return mayEdit(config, context.player());
    }

    private static boolean mayEdit(BlueprintConfig config, ServerPlayer player) {
        var required = config.adminPermission();
        if (required == null || player.permissions().hasPermission(required)) {
            return true;
        }
        var server = player.level().getServer();
        return server != null && server.isSingleplayer() && server.isSingleplayerOwner(
                new net.minecraft.server.players.NameAndId(player.getGameProfile()));
    }

    private static void sendGraph(ServerPlayNetworking.Context context, Blueprint bp,
                                  boolean writable) {
        context.responseSender().sendPacket(new BlueprintPayloads.GraphData(
                bp.id(), bp.revision(), writable, GraphSync.toBytes(bp)));
    }

    private static void deny(ServerPlayNetworking.Context context, Identifier id,
                             BlueprintPayloads.SaveStatus status, int revision) {
        context.responseSender().sendPacket(
                new BlueprintPayloads.SaveAck(id, status, revision));
    }

    private static BlueprintPayloads.SaveStatus statusOf(BlueprintManager.SaveOutcome outcome) {
        return switch (outcome) {
            case SAVED -> BlueprintPayloads.SaveStatus.SAVED;
            case CONFLICT -> BlueprintPayloads.SaveStatus.CONFLICT;
            case UNKNOWN -> BlueprintPayloads.SaveStatus.UNKNOWN;
        };
    }
}
