package fr.blueprint.core.net;

import fr.blueprint.core.BlueprintManager;
import fr.blueprint.core.BlueprintMod;
import fr.blueprint.core.config.BlueprintConfig;
import fr.blueprint.core.debug.DebugSession;
import fr.blueprint.core.debug.DebugSessions;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Le débogueur vu par l'éditeur (story 9.1b) : l'éditeur envoie des actions, le serveur
 * pousse des instantanés.
 *
 * <p>Deux règles tiennent tout le reste. <b>Seul un joueur autorisé à éditer débogue</b> —
 * voir les valeurs d'un graphe, c'est voir ce que fait son auteur. Et <b>on ne pousse
 * qu'aux abonnés</b>, quatre fois par seconde : sans abonnés, il ne se passe
 * rigoureusement rien (le drapeau de {@link DebugSessions} reste faux).
 */
public final class DebugNet {

    /** Un instantané tous les 5 ticks : assez vivant pour suivre, assez rare pour être gratuit. */
    private static final int PUSH_EVERY_TICKS = 5;

    private DebugNet() {
    }

    /** Abonnés par blueprint : les joueurs dont l'éditeur affiche ce débogueur. */
    private static final Map<Identifier, Set<UUID>> SUBSCRIBERS = new ConcurrentHashMap<>();
    private static int tickCounter;

    /** Quota des actions de débogage : large pour un humain, borné pour un script. */
    private static final RateLimiter COMMANDS =
            new RateLimiter(60, 10_000L, System::currentTimeMillis);

    public static void register(BlueprintConfig config) {
        PayloadTypeRegistry.playS2C().register(BlueprintPayloads.DebugSnapshot.TYPE,
                BlueprintPayloads.DebugSnapshot.CODEC);
        PayloadTypeRegistry.playC2S().register(BlueprintPayloads.DebugCommand.TYPE,
                BlueprintPayloads.DebugCommand.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(BlueprintPayloads.DebugCommand.TYPE,
                (payload, context) -> {
                    // QA SEC-005 : les actions de débogage sont des paquets comme les
                    // autres — chacune déclenche un instantané en réponse, donc chacune
                    // se limite (6.4 ne couvrait que l'ouverture et l'enregistrement).
                    if (!COMMANDS.allow(context.player().getUUID())) {
                        return;
                    }
                    handle(config, payload, context);
                });

        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> unsubscribeEverywhere(handler.player.getUUID()));

        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(
                server -> {
                    if (SUBSCRIBERS.isEmpty() || ++tickCounter % PUSH_EVERY_TICKS != 0) {
                        return;
                    }
                    SUBSCRIBERS.forEach((blueprint, players) -> {
                        // QA DBG-001 : la session a pu être fermée par la commande
                        // /blueprint debug off, qui ne connaît pas les abonnés. Sans ce
                        // ménage, on pousserait « débogage inactif » à ces joueurs
                        // jusqu'à la fin des temps.
                        if (DebugSessions.of(blueprint) == null) {
                            push(server, blueprint, players);
                            SUBSCRIBERS.remove(blueprint);
                            return;
                        }
                        push(server, blueprint, players);
                    });
                });
    }

    private static void handle(BlueprintConfig config, BlueprintPayloads.DebugCommand payload,
                               ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        var required = config.adminPermission();
        boolean allowed = required == null || player.permissions().hasPermission(required)
                || (context.server().isSingleplayer() && context.server().isSingleplayerOwner(
                        new net.minecraft.server.players.NameAndId(player.getGameProfile())));
        if (!allowed) {
            BlueprintMod.LOGGER.warn("Débogage refusé à {} sur « {} »",
                    player.getGameProfile().name(), payload.blueprint());
            return;
        }
        Identifier id = payload.blueprint();
        if (BlueprintManager.of(context.server()).get(id).isEmpty()) {
            return;
        }

        switch (payload.action()) {
            case ON -> {
                DebugSessions.open(id);
                SUBSCRIBERS.computeIfAbsent(id, key -> ConcurrentHashMap.newKeySet())
                        .add(player.getUUID());
            }
            case OFF -> {
                Set<UUID> players = SUBSCRIBERS.get(id);
                if (players != null) {
                    players.remove(player.getUUID());
                    if (players.isEmpty()) {
                        SUBSCRIBERS.remove(id);
                        // Plus personne ne regarde : la session se ferme, et l'exécution
                        // éventuellement en pause repart (sinon le graphe resterait figé).
                        DebugSessions.close(id);
                    }
                }
            }
            case TOGGLE_BREAKPOINT -> {
                DebugSession session = DebugSessions.of(id);
                if (session != null && payload.node().isPresent()) {
                    UUID node = payload.node().get();
                    if (!session.unbreak(node)) {
                        session.breakOn(node);
                    }
                }
            }
            case STEP -> {
                DebugSession session = DebugSessions.of(id);
                if (session != null) {
                    session.step();
                }
            }
            case CONTINUE -> {
                DebugSession session = DebugSessions.of(id);
                if (session != null) {
                    session.resume();
                }
            }
            case CLEAR -> {
                DebugSession session = DebugSessions.of(id);
                if (session != null) {
                    session.clearBreakpoints();
                    session.clearValues();
                    session.resume();
                }
            }
        }
        // Réponse immédiate : l'éditeur ne doit pas attendre le prochain tick pour voir
        // l'effet de son propre clic.
        context.responseSender().sendPacket(snapshot(id));
    }

    private static void push(MinecraftServer server, Identifier blueprint, Set<UUID> players) {
        BlueprintPayloads.DebugSnapshot snapshot = snapshot(blueprint);
        for (UUID uuid : players) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null && ServerPlayNetworking.canSend(player,
                    BlueprintPayloads.DebugSnapshot.TYPE)) {
                ServerPlayNetworking.send(player, snapshot);
            }
        }
    }

    /** Instantané borné : les nœuds les plus récemment vus, valeurs déjà en texte. */
    static BlueprintPayloads.DebugSnapshot snapshot(Identifier blueprint) {
        DebugSession session = DebugSessions.of(blueprint);
        if (session == null) {
            return new BlueprintPayloads.DebugSnapshot(blueprint, false, Optional.empty(),
                    List.of(), List.of());
        }
        List<UUID> breakpoints = new ArrayList<>(session.breakpoints());
        breakpoints.sort(java.util.Comparator.comparing(UUID::toString));
        if (breakpoints.size() > BlueprintPayloads.MAX_DEBUG_NODES) {
            breakpoints = breakpoints.subList(0, BlueprintPayloads.MAX_DEBUG_NODES);
        }

        // Ordre de la trace, du plus récent au plus ancien : ce qui vient de se passer
        // est ce qu'on veut voir en premier quand il faut couper.
        List<UUID> recent = new ArrayList<>(session.trace());
        java.util.Collections.reverse(recent);
        Map<UUID, List<String>> lines = new LinkedHashMap<>();
        for (UUID node : recent) {
            if (lines.size() >= BlueprintPayloads.MAX_DEBUG_NODES) {
                break;
            }
            if (lines.containsKey(node)) {
                continue;
            }
            Map<String, String> values = session.valuesOf(node);
            if (values.isEmpty()) {
                continue;
            }
            List<String> rendered = new ArrayList<>(values.size());
            values.forEach((pin, value) -> rendered.add(pin + " = " + value));
            lines.put(node, List.copyOf(rendered));
        }
        List<BlueprintPayloads.NodeValues> values = new ArrayList<>(lines.size());
        lines.forEach((node, rendered) ->
                values.add(new BlueprintPayloads.NodeValues(node, rendered)));

        return new BlueprintPayloads.DebugSnapshot(blueprint, true,
                Optional.ofNullable(session.pausedAt()), List.copyOf(breakpoints),
                List.copyOf(values));
    }

    private static void unsubscribeEverywhere(UUID player) {
        COMMANDS.forget(player);
        SUBSCRIBERS.forEach((blueprint, players) -> {
            if (players.remove(player) && players.isEmpty()) {
                SUBSCRIBERS.remove(blueprint);
                DebugSessions.close(blueprint);
            }
        });
    }
}
