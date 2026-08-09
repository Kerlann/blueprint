package fr.blueprint.fabric;

import fr.blueprint.core.BlueprintMod;
import net.fabricmc.api.ModInitializer;

/**
 * Le point d'entrée Fabric — et rien de plus.
 *
 * <p>Il vivait dans {@code core}, sous la forme d'un {@code BlueprintMod implements
 * ModInitializer}. Le déplacer ici est tout l'objet du lot A : {@code core} ne peut plus
 * <i>être</i> un mod Fabric, il ne peut plus qu'être appelé par un. Ce qui semble un
 * détail de rangement est la seule façon d'avoir un jour un {@code NeoForgeBootstrap} qui
 * appelle exactement la même méthode.
 *
 * <p>Aucune ligne de logique ne doit descendre ici. Ce que ce module contient est du
 * <b>câblage</b> : si une décision s'y installe, elle devra être recopiée pour chaque
 * chargeur — et deux copies divergent toujours.
 */
public final class FabricBootstrap implements ModInitializer {

    @Override
    public void onInitialize() {
        BlueprintMod.init();
        FabricServerEvents.register();
    }
}
