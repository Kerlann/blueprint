package fr.blueprint.platform.net;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * De quoi répondre au paquet qu'on vient de recevoir.
 *
 * <p>Volontairement pauvre — un seul verbe. Tout ce que l'ancien contexte offrait en plus
 * était {@code client()}, c'est-à-dire {@code Minecraft.getInstance()} sous un autre nom
 * (voir {@link ClientNetwork}).
 */
public interface ClientNetContext {

    /**
     * Répond au serveur.
     *
     * <p>Sur Fabric, indiscernable de {@link ClientNetwork#send}. Le nom porte quand même
     * une information : à la lecture, {@code reply} dit que le paquet part <i>parce
     * qu'</i>un autre est arrivé — la synchro du registre en dépend (6.2).
     */
    void reply(CustomPacketPayload payload);
}
