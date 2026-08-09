package fr.blueprint.platform;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

/**
 * « Appelle-moi quand ce registre est ouvert. »
 *
 * <p>C'est le seul endroit du dépôt où la portabilité change une <b>décision de
 * conception</b> et pas un import.
 *
 * <p>Minecraft n'accepte un item neuf que dans une fenêtre, avant
 * {@link Registry#freeze()}. Sur Fabric, cette fenêtre est l'initialisation du mod : on y
 * appelle {@code Registry.register} directement, et c'est ce que Blueprint faisait.
 * Sur NeoForge, elle n'existe pas sous cette forme — les registres sont gelés partout sauf
 * dans {@code RegisterEvent}, et l'appel direct y lèverait.
 *
 * <p>Le code commun ne peut donc pas <i>choisir</i> le moment : il peut seulement dire ce
 * qu'il veut enregistrer, et dans quel registre. Le chargeur décide du quand.
 *
 * <h2>Ce que cela impose à l'appelant</h2>
 * <p>L'action peut s'exécuter <b>plus tard</b> que l'appel. Tout ce qui dépend du
 * résultat de l'enregistrement — un compte, une liste de refus, une trace — doit vivre
 * <i>dans</i> l'action, pas après elle. C'est la seule vraie contrainte, et elle ne se
 * voit pas sur Fabric, où l'action part immédiatement.
 */
public interface PlatformRegistrar {

    /**
     * Programme un enregistrement dans {@code registry}.
     *
     * <p>Deux appels sur le <b>même</b> registre s'exécutent dans l'ordre où ils ont été
     * programmés : c'est ce qui fait tenir la numérotation réseau (voir
     * {@code ContentRegistrar}). Entre registres différents, aucun ordre n'est promis —
     * NeoForge suit le sien.
     */
    void whenOpen(ResourceKey<? extends Registry<?>> registry, Runnable action);
}
