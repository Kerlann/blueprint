package fr.blueprint.fabric.net;

import fr.blueprint.platform.net.ServerNetContext;
import fr.blueprint.platform.net.ServerNetwork;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Le réseau serveur, câblé sur Fabric. Aucune décision ici — que de la traduction. */
public final class FabricServerNetwork implements ServerNetwork {

    @Override
    public <T extends CustomPacketPayload> void registerC2S(CustomPacketPayload.Type<T> type,
                                                            StreamCodec<? super ByteBuf, T> codec) {
        PayloadTypeRegistry.playC2S().register(type, codec);
    }

    @Override
    public <T extends CustomPacketPayload> void registerS2C(CustomPacketPayload.Type<T> type,
                                                            StreamCodec<? super ByteBuf, T> codec) {
        PayloadTypeRegistry.playS2C().register(type, codec);
    }

    @Override
    public <T extends CustomPacketPayload> void registerC2SLarge(CustomPacketPayload.Type<T> type,
                                                                 StreamCodec<? super ByteBuf, T> codec,
                                                                 int chunkBytes) {
        PayloadTypeRegistry.playC2S().registerLarge(type, codec, chunkBytes);
    }

    @Override
    public <T extends CustomPacketPayload> void registerS2CLarge(CustomPacketPayload.Type<T> type,
                                                                 StreamCodec<? super ByteBuf, T> codec,
                                                                 int chunkBytes) {
        PayloadTypeRegistry.playS2C().registerLarge(type, codec, chunkBytes);
    }

    @Override
    public <T extends CustomPacketPayload> void receive(CustomPacketPayload.Type<T> type,
                                                        Receiver<T> receiver) {
        ServerPlayNetworking.registerGlobalReceiver(type,
                (payload, context) -> receiver.receive(payload, wrap(context)));
    }

    @Override
    public void send(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return ServerPlayNetworking.canSend(player, type);
    }

    /**
     * Emballe le contexte de Fabric dans le nôtre.
     *
     * <p>Une allocation par paquet reçu. Elle est admissible ici et le serait beaucoup
     * moins ailleurs : le réseau n'est pas un chemin par tick — un paquet arrive quand un
     * joueur agit — alors que la règle « aucune allocation » de coding-standards §5 vise
     * la boucle de la VM et le rendu.
     */
    private static ServerNetContext wrap(ServerPlayNetworking.Context context) {
        return new ServerNetContext() {
            @Override
            public ServerPlayer player() {
                return context.player();
            }

            @Override
            public MinecraftServer server() {
                return context.server();
            }

            @Override
            public void reply(CustomPacketPayload payload) {
                context.responseSender().sendPacket(payload);
            }
        };
    }
}
