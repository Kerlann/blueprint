package fr.blueprint.core;

/**
 * Amorce les registres de Minecraft pour les tests qui en ont réellement besoin.
 *
 * <p>La règle du projet reste inchangée : <b>les tests de {@code core} tournent sans
 * Minecraft démarré</b> (coding-standards §7). Elle porte sur le <i>serveur</i> — pas de
 * monde, pas de joueur, pas de boucle de tick — et c'est elle qui garde la couche isolée.
 *
 * <p>Quelques types de pins tombent malgré tout de l'autre côté : {@code ITEMSTACK} a pour
 * valeur par défaut {@code ItemStack.EMPTY}, dont la classe ne se charge pas tant que les
 * registres ne sont pas amorcés. Un exemple qui donne un objet à un joueur — la banque —
 * ne peut donc même pas être <b>construit</b> sans cela : la validation d'un lien résout
 * les défauts des pins.
 *
 * <p>Deux issues étaient possibles, et la mauvaise est tentante : écarter ces nœuds des
 * exemples pour ne pas avoir à amorcer. Ce serait laisser le harnais dicter ce que le
 * produit a le droit de montrer, et l'exemple le plus utile — celui qui manipule des
 * items — serait précisément celui qu'on s'interdirait.
 *
 * <p>L'amorçage est donc fait, une fois, à la demande. Il coûte environ une seconde au
 * premier appel et rien ensuite.
 */
public final class MinecraftBootstrap {

    private static boolean done;

    private MinecraftBootstrap() {
    }

    /** Amorce si ce n'est pas déjà fait. Sûr à appeler depuis plusieurs classes de test. */
    public static synchronized void ensure() {
        if (done) {
            return;
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        done = true;
    }
}
