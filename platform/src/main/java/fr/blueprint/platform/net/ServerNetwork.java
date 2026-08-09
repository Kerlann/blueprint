package fr.blueprint.platform.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Le réseau vu du serveur : déclarer les paquets, les recevoir, les envoyer.
 *
 * <p><b>Les charges utiles ne passent pas par ici.</b> Ce sont des
 * {@link CustomPacketPayload} de Minecraft, avec des codecs de Minecraft : elles ne
 * changent pas d'un chargeur à l'autre, et aucune ligne de {@code BlueprintPayloads} n'a
 * été touchée. Ce qui change tient en trois verbes — déclarer, envoyer, recevoir — plus
 * la question de savoir si le destinataire nous comprend.
 */
public interface ServerNetwork {

    /** Déclare un paquet montant (client → serveur). */
    <T extends CustomPacketPayload> void registerC2S(CustomPacketPayload.Type<T> type,
                                                     StreamCodec<? super ByteBuf, T> codec);

    /** Déclare un paquet descendant (serveur → client). */
    <T extends CustomPacketPayload> void registerS2C(CustomPacketPayload.Type<T> type,
                                                     StreamCodec<? super ByteBuf, T> codec);

    /**
     * Déclare un paquet montant <b>volumineux</b>, à découper au-delà de {@code chunkBytes}.
     *
     * <p>Un graphe entier ne tient pas dans un paquet ordinaire. Fabric sait le fragmenter
     * tout seul ; NeoForge n'a pas cette notion et devra la fournir autrement. La question
     * est donc posée ici — c'est une capacité dont le code commun a besoin — et chaque
     * chargeur y répond comme il peut.
     */
    <T extends CustomPacketPayload> void registerC2SLarge(CustomPacketPayload.Type<T> type,
                                                          StreamCodec<? super ByteBuf, T> codec,
                                                          int chunkBytes);

    /** Voir {@link #registerC2SLarge}, dans l'autre sens. */
    <T extends CustomPacketPayload> void registerS2CLarge(CustomPacketPayload.Type<T> type,
                                                          StreamCodec<? super ByteBuf, T> codec,
                                                          int chunkBytes);

    /** Pose le traitement d'un paquet montant. */
    <T extends CustomPacketPayload> void receive(CustomPacketPayload.Type<T> type,
                                                 Receiver<T> receiver);

    /** Envoie à un joueur, de notre propre initiative. */
    void send(ServerPlayer player, CustomPacketPayload payload);

    /**
     * Ce joueur sait-il lire ce paquet ?
     *
     * <p>La question n'est pas rhétorique : un client vanilla, ou un client sans Blueprint,
     * se déconnecte sur un paquet qu'il ne connaît pas. Elle ne se pose jamais en
     * développement — les deux côtés ont toujours le mod — et se pose tout le temps chez
     * les joueurs. C'est le point du réseau qu'il faudra tester explicitement, client nu
     * contre serveur moddé, quand un second chargeur existera.
     */
    boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type);

    /** Ce qu'on fait d'un paquet reçu. */
    @FunctionalInterface
    interface Receiver<T extends CustomPacketPayload> {
        void receive(T payload, ServerNetContext context);
    }
}
