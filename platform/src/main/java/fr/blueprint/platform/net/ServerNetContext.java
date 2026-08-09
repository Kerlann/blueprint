package fr.blueprint.platform.net;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Qui a envoyé le paquet, et par où lui répondre.
 *
 * <p>C'est le type qui justifie à lui seul l'existence de ce paquetage. Le contexte du
 * chargeur ne circulait pas seulement dans des lambdas : il était écrit dans <b>six
 * signatures</b> de {@code ServerBlueprintNet} et {@code DebugNet}
 * ({@code allowed}, {@code name}, {@code mayEdit}, {@code sendGraph}, {@code deny}…).
 * Un type du chargeur dans une signature n'est pas un import à remplacer — il se propage
 * à tous les appelants, et c'est ainsi qu'une frontière se perd sans que personne ne
 * décide rien.
 */
public interface ServerNetContext {

    /** Le joueur d'où vient le paquet. */
    ServerPlayer player();

    /** Le serveur qui l'a reçu. */
    MinecraftServer server();

    /**
     * Répond à ce joueur, sur cette connexion.
     *
     * <p>Distinct de {@link ServerNetwork#send} : répondre est une réaction à un paquet
     * reçu, envoyer est une initiative. Fabric ne fait pas la différence — les deux
     * finissent au même endroit — mais NeoForge donne bien deux chemins, et surtout le
     * code se lit mieux quand la réponse dit qu'elle en est une.
     */
    void reply(CustomPacketPayload payload);
}
