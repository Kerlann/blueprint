package fr.blueprint.neoforge;

import fr.blueprint.core.BlueprintMod;
import fr.blueprint.neoforge.net.NeoForgeServerNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Le point d'entrée NeoForge — le pendant exact de {@code FabricBootstrap}.
 *
 * <p>Tout le démarrage tient dans le constructeur, et ce n'est pas un raccourci : c'est
 * la fenêtre où NeoForge construit les mods, <b>avant</b> ses événements
 * d'enregistrement. {@code BlueprintMod.init()} y met donc en file tout ce qu'il veut
 * enregistrer — contenu déclaré, types de paquets — et les événements ci-dessous vident
 * ces files au moment où le jeu l'autorise.
 *
 * <p>C'est l'inversion que le lot B avait anticipée : sur Fabric, « demander » et
 * « faire » se confondent ; ici ils sont séparés par plusieurs phases de chargement, et
 * le code commun ne s'en aperçoit pas.
 */
@Mod("blueprint")
public final class BlueprintNeoForge {

    public BlueprintNeoForge(IEventBus modBus) {
        // Le bus du MOD porte l'enregistrement (registres, paquets, touches) ; le bus du
        // JEU porte ce qui arrive ensuite (serveur, monde, joueurs). Les confondre est
        // l'erreur classique de NeoForge, et elle ne se voit qu'à l'exécution.
        modBus.addListener(NeoForgeRegistrar::flush);
        modBus.addListener(NeoForgeServerNetwork::flush);

        BlueprintMod.init();

        NeoForgeServerEvents.register(NeoForge.EVENT_BUS);
    }
}
