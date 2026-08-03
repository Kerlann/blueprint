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
                    List<Identifier> ids = new ArrayList<>();
                    BlueprintManager.of(context.server()).all()
                            .forEach(bp -> ids.add(bp.id()));
                    context.responseSender().sendPacket(new BlueprintPayloads.ListData(
                            ids, mayEdit(config, context.player())));
                });

        ServerPlayNetworking.registerGlobalReceiver(BlueprintPayloads.OpenRequest.TYPE,
                (payload, context) -> {
                    Blueprint bp = BlueprintManager.of(context.server())
                            .get(payload.blueprint()).orElse(null);
                    if (bp == null) {
                        deny(context, payload.blueprint(),
                                BlueprintPayloads.SaveStatus.UNKNOWN, -1);
                        return;
                    }
                    sendGraph(context, bp, mayEdit(config, context.player()));
                });

        ServerPlayNetworking.registerGlobalReceiver(BlueprintPayloads.CreateRequest.TYPE,
                (payload, context) -> {
                    if (!mayEdit(config, context.player())) {
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
                    if (!mayEdit(config, context.player())) {
                        deny(context, id, BlueprintPayloads.SaveStatus.DENIED, -1);
                        return;
                    }
                    Blueprint snapshot = GraphSync.fromBytes(payload.data(),
                            typeId -> BlueprintMod.registries().pinTypes()
                                    .get(typeId).orElse(null));
                    // Identifiant du graphe ≠ identifiant annoncé : paquet incohérent,
                    // on ne devine pas lequel des deux le client voulait.
                    if (snapshot == null || !snapshot.id().equals(id)) {
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
                    if (!mayEdit(config, context.player())) {
                        deny(context, payload.blueprint(),
                                BlueprintPayloads.SaveStatus.DENIED, -1);
                        return;
                    }
                    BlueprintManager.of(context.server())
                            .setEnabled(payload.blueprint(), payload.enabled());
                });
    }

    /** Taille des tranches des paquets scindés par Fabric (graphe et instantané). */
    private static final int CHUNK_BYTES = 28_000;

    /** Écriture = permission d'administration configurée (null = ouvert à tous). */
    private static boolean mayEdit(BlueprintConfig config, ServerPlayer player) {
        var required = config.adminPermission();
        return required == null || player.permissions().hasPermission(required);
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
