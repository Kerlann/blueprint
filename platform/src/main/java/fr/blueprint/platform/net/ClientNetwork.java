package fr.blueprint.platform.net;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Le réseau vu du client. La déclaration des paquets reste côté serveur
 * ({@link ServerNetwork}) : elle est commune aux deux extrémités, et la faire deux fois
 * n'ajouterait qu'une occasion de diverger.
 *
 * <p>Ce paquetage ne mentionne délibérément <b>aucune classe client de Minecraft</b>.
 * L'ancien code passait par {@code context.client()} pour atteindre {@code Minecraft} ;
 * c'est exactement {@code Minecraft.getInstance()}, que le module client appelle déjà
 * partout ailleurs. Le garder ici aurait fait entrer une classe absente d'un serveur
 * dédié dans un module que le serveur charge.
 */
public interface ClientNetwork {

    /** Pose le traitement d'un paquet descendant. */
    <T extends CustomPacketPayload> void receive(CustomPacketPayload.Type<T> type,
                                                 Receiver<T> receiver);

    /** Envoie au serveur. */
    void send(CustomPacketPayload payload);

    /**
     * Le serveur sait-il lire ce paquet ?
     *
     * <p>Symétrique de {@link ServerNetwork#canSend} et tout aussi peu rhétorique : un
     * client Blueprint sur un serveur vanilla doit se taire, pas se déconnecter.
     */
    boolean canSend(CustomPacketPayload.Type<?> type);

    /** Ce qu'on fait d'un paquet reçu. */
    @FunctionalInterface
    interface Receiver<T extends CustomPacketPayload> {
        void receive(T payload, ClientNetContext context);
    }
}
