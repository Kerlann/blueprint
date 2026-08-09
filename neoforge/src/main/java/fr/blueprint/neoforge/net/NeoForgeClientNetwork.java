package fr.blueprint.neoforge.net;

import fr.blueprint.platform.net.ClientNetContext;
import fr.blueprint.platform.net.ClientNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Le réseau client, câblé sur NeoForge.
 *
 * <p>Les traitements descendants se posent <b>ici et pas ailleurs</b>, sur
 * {@link RegisterClientPayloadHandlersEvent}. NeoForge sépare cet événement du commun
 * exprès : un traitement client touche des classes absentes d'un serveur dédié, et les
 * déclarer au même endroit que les types les y ferait charger.
 *
 * <p>Il ne fait pas que le permettre, il le <b>vérifie</b> : au démarrage du client, tout
 * paquet descendant sans traitement fait échouer le chargement du mod, nommément. La
 * première version de ce portage posait les deux moitiés côté commun et mourait sur
 * « Some clientbound payloads are missing client-side handlers ». La contrainte a donc
 * corrigé la conception, qui était bancale : le réseau client déposait ses récepteurs
 * dans la classe du réseau serveur.
 */
public final class NeoForgeClientNetwork implements ClientNetwork {

    /** Les récepteurs en attente de l'événement client. */
    private static final Map<CustomPacketPayload.Type<?>, Receiver<?>> RECEVEURS =
            new LinkedHashMap<>();

    @Override
    public <T extends CustomPacketPayload> void receive(CustomPacketPayload.Type<T> type,
                                                        Receiver<T> receiver) {
        RECEVEURS.put(type, receiver);
    }

    @Override
    public void send(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }

    @Override
    public boolean canSend(CustomPacketPayload.Type<?> type) {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(type);
    }

    /** Pose les traitements descendants. Appelé sur l'événement client. */
    @SuppressWarnings("unchecked")
    public static void flush(RegisterClientPayloadHandlersEvent event) {
        RECEVEURS.forEach((type, receiver) -> event.register(
                (CustomPacketPayload.Type<CustomPacketPayload>) type,
                (payload, context) ->
                        ((Receiver<CustomPacketPayload>) receiver).receive(payload, REPONDRE)));
        RECEVEURS.clear();
    }

    /** Sans état ni capture : une seule instance suffit pour tous les paquets reçus. */
    private static final ClientNetContext REPONDRE = ClientPacketDistributor::sendToServer;
}
