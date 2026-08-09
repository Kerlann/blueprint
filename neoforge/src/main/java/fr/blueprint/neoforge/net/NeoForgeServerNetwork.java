package fr.blueprint.neoforge.net;

import fr.blueprint.platform.net.ServerNetContext;
import fr.blueprint.platform.net.ServerNetwork;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;

/**
 * Le réseau serveur, câblé sur NeoForge.
 *
 * <p>Même principe que {@link fr.blueprint.neoforge.NeoForgeRegistrar} : NeoForge ne
 * déclare ses paquets que pendant {@link RegisterPayloadHandlersEvent}, donc tout ce que
 * le code commun demande est mis en file et posé d'un coup au bon moment.
 *
 * <p><b>Ce fichier ne pose que les types et les traitements MONTANTS.</b> Les traitements
 * descendants vivent dans {@link NeoForgeClientNetwork}, sur un événement séparé —
 * NeoForge les sépare pour que du code client ne soit jamais chargé sur un serveur dédié,
 * et il vérifie au démarrage du client que chaque paquet descendant a bien trouvé son
 * traitement. C'est cette vérification qui a fait échouer la première version, où les deux
 * moitiés étaient posées ici.
 */
public final class NeoForgeServerNetwork implements ServerNetwork {

    /** Un type déclaré, et son codec, en attente de l'événement. */
    private record Declared(CustomPacketPayload.Type<?> type,
                            StreamCodec<? super ByteBuf, ?> codec) {
    }

    private static final List<Declared> VERS_SERVEUR = new ArrayList<>();
    private static final List<Declared> VERS_CLIENT = new ArrayList<>();
    private static final Map<CustomPacketPayload.Type<?>, Receiver<?>> RECEVEURS_SERVEUR =
            new LinkedHashMap<>();

    @Override
    public <T extends CustomPacketPayload> void registerC2S(CustomPacketPayload.Type<T> type,
                                                            StreamCodec<? super ByteBuf, T> codec) {
        VERS_SERVEUR.add(new Declared(type, codec));
    }

    @Override
    public <T extends CustomPacketPayload> void registerS2C(CustomPacketPayload.Type<T> type,
                                                            StreamCodec<? super ByteBuf, T> codec) {
        VERS_CLIENT.add(new Declared(type, codec));
    }

    /**
     * NeoForge ne sait pas fragmenter un paquet : il n'a pas d'équivalent du
     * {@code registerLarge} de Fabric.
     *
     * <p>Le type est donc déclaré normalement, et la charge reste bornée par la limite du
     * protocole. C'est une <b>limite connue</b> de ce chargeur, pas un oubli : un graphe
     * assez gros pour dépasser la limite passera sur Fabric et échouera ici. Elle est
     * inscrite dans {@code docs/plan-multiloader.md}, et se lèvera en fragmentant nous-mêmes
     * — ce qui est un travail de protocole, pas de plateforme.
     */
    @Override
    public <T extends CustomPacketPayload> void registerC2SLarge(CustomPacketPayload.Type<T> type,
                                                                 StreamCodec<? super ByteBuf, T> codec,
                                                                 int chunkBytes) {
        registerC2S(type, codec);
    }

    /** Voir {@link #registerC2SLarge}. */
    @Override
    public <T extends CustomPacketPayload> void registerS2CLarge(CustomPacketPayload.Type<T> type,
                                                                 StreamCodec<? super ByteBuf, T> codec,
                                                                 int chunkBytes) {
        registerS2C(type, codec);
    }

    @Override
    public <T extends CustomPacketPayload> void receive(CustomPacketPayload.Type<T> type,
                                                        Receiver<T> receiver) {
        RECEVEURS_SERVEUR.put(type, receiver);
    }

    @Override
    public void send(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return player.connection.hasChannel(type);
    }

    /**
     * Déclare les types, et pose les traitements <b>montants</b>. Appelé sur
     * {@link RegisterPayloadHandlersEvent}.
     *
     * <p><b>Un paquet déclaré dans les deux sens se déclare une seule fois.</b> Fabric
     * tient deux registres séparés — montant et descendant — où le même identifiant peut
     * figurer des deux côtés ; c'est le cas de {@code screen_close}, que le serveur envoie
     * pour fermer un écran et que le client renvoie quand le joueur appuie sur Échap.
     * NeoForge n'en tient qu'un, et refuse le second enregistrement.
     *
     * <p>Aucun traitement descendant n'est posé ici, pas même quand on tourne sur un
     * client : c'est {@link NeoForgeClientNetwork} qui s'en charge, sur son propre
     * événement.
     */
    @SuppressWarnings("unchecked")
    public static void flush(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        Set<CustomPacketPayload.Type<?>> descendants = new LinkedHashSet<>();
        VERS_CLIENT.forEach(declared -> descendants.add(declared.type()));
        Set<CustomPacketPayload.Type<?>> posés = new LinkedHashSet<>();

        for (Declared declared : VERS_SERVEUR) {
            var type = (CustomPacketPayload.Type<CustomPacketPayload>) declared.type();
            var codec = (StreamCodec<? super RegistryFriendlyByteBuf, CustomPacketPayload>)
                    declared.codec();
            posés.add(declared.type());
            if (descendants.contains(declared.type())) {
                registrar.playBidirectional(type, codec, NeoForgeServerNetwork::dispatch);
            } else {
                registrar.playToServer(type, codec, NeoForgeServerNetwork::dispatch);
            }
        }

        for (Declared declared : VERS_CLIENT) {
            if (posés.contains(declared.type())) {
                continue;   // déjà posé en bidirectionnel ci-dessus
            }
            var type = (CustomPacketPayload.Type<CustomPacketPayload>) declared.type();
            var codec = (StreamCodec<? super RegistryFriendlyByteBuf, CustomPacketPayload>)
                    declared.codec();
            registrar.playToClient(type, codec);
        }

        VERS_SERVEUR.clear();
        VERS_CLIENT.clear();
    }

    /**
     * Le traitement d'un paquet reçu par le serveur.
     *
     * <p>La garde sur le sens n'est pas décorative : un type bidirectionnel passe aussi
     * par ici côté client, où il n'a rien à faire — le contexte y porte un joueur client,
     * que le transtypage vers {@code ServerPlayer} ferait éclater.
     */
    @SuppressWarnings("unchecked")
    private static void dispatch(CustomPacketPayload payload, IPayloadContext context) {
        if (context.flow() != PacketFlow.SERVERBOUND) {
            return;
        }
        var receiver = (Receiver<CustomPacketPayload>) RECEVEURS_SERVEUR.get(payload.type());
        if (receiver != null) {
            receiver.receive(payload, wrap(context));
        }
    }

    private static ServerNetContext wrap(IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        return new ServerNetContext() {
            @Override
            public ServerPlayer player() {
                return player;
            }

            @Override
            public MinecraftServer server() {
                return player.level().getServer();
            }

            @Override
            public void reply(CustomPacketPayload payload) {
                context.reply(payload);
            }
        };
    }
}
