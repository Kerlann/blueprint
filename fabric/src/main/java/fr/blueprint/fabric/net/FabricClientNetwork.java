package fr.blueprint.fabric.net;

import fr.blueprint.platform.net.ClientNetContext;
import fr.blueprint.platform.net.ClientNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Le réseau client, câblé sur Fabric.
 *
 * <p>Cette classe ne se charge que côté client — elle touche
 * {@code ClientPlayNetworking}, absent d'un serveur dédié. C'est {@code ServiceLoader} qui
 * l'instancie, donc rien ne la touche tant que {@code Platform.clientNetwork()} n'est pas
 * appelé, et seul le module client l'appelle.
 */
public final class FabricClientNetwork implements ClientNetwork {

    @Override
    public <T extends CustomPacketPayload> void receive(CustomPacketPayload.Type<T> type,
                                                        Receiver<T> receiver) {
        ClientPlayNetworking.registerGlobalReceiver(type,
                (payload, context) -> receiver.receive(payload, wrap()));
    }

    @Override
    public void send(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }

    @Override
    public boolean canSend(CustomPacketPayload.Type<?> type) {
        return ClientPlayNetworking.canSend(type);
    }

    private static ClientNetContext wrap() {
        return ClientPlayNetworking::send;
    }
}
