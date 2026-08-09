package fr.blueprint.platform.client;

import net.minecraft.network.chat.Component;

/**
 * Comment répondre au joueur dans une commande <b>client</b>.
 *
 * <p>C'est la réponse au piège annoncé par le plan : Fabric fait passer les commandes
 * client par un type à lui, {@code FabricClientCommandSource}, qui était écrit dans cinq
 * signatures de {@code BlueprintClient}. NeoForge, lui, donne un
 * {@code CommandSourceStack} ordinaire. Les deux types n'ont aucun ancêtre commun utile.
 *
 * <p>La sortie n'est pas d'inventer un type de source à nous — il faudrait alors
 * reconstruire l'arbre Brigadier, qui est générique sur la source. Elle est de remarquer
 * que <b>tout ce dont le code a besoin de cette source, c'est de parler au joueur</b>.
 * L'arbre reste donc générique sur {@code S}, chaque chargeur le construit avec son propre
 * type, et fournit ce seul verbe.
 *
 * @param <S> le type de source du chargeur — jamais nommé dans le code commun
 */
@FunctionalInterface
public interface ClientFeedback<S> {

    /** Dit quelque chose à celui qui a tapé la commande. */
    void send(S source, Component message);
}
