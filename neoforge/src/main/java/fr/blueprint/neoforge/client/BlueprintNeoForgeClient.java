package fr.blueprint.neoforge.client;

import fr.blueprint.client.BlueprintClient;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Le point d'entrée client NeoForge.
 *
 * <p>C'est ici que se voit le gain du lot A3. NeoForge donne aux commandes client un
 * {@code CommandSourceStack} ordinaire, là où Fabric donne son
 * {@code FabricClientCommandSource} — deux types sans ancêtre commun utile. Le code
 * commun n'en connaît aucun : il construit un arbre Brigadier générique et ne demande à
 * la source qu'un verbe. Les deux lignes ci-dessous sont tout ce que la différence coûte.
 */
@Mod(value = "blueprint", dist = Dist.CLIENT)
public final class BlueprintNeoForgeClient {

    public BlueprintNeoForgeClient(IEventBus modBus) {
        modBus.addListener(NeoForgeClientPlatform::flushKeys);
        modBus.addListener(NeoForgeClientPlatform::flushHud);
        // Les traitements des paquets descendants : NeoForge les veut sur SON événement,
        // et vérifie au démarrage qu'aucun paquet descendant n'en manque.
        modBus.addListener(fr.blueprint.neoforge.net.NeoForgeClientNetwork::flush);

        // Comme côté serveur : l'initialisation met en file, les événements vident.
        BlueprintClient.init();

        IEventBus jeu = NeoForge.EVENT_BUS;
        jeu.addListener((ClientTickEvent.Post e) ->
                BlueprintClient.endClientTick(net.minecraft.client.Minecraft.getInstance()));
        jeu.addListener((PlayerEvent.PlayerLoggedInEvent e) -> BlueprintClient.onJoin());
        jeu.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> BlueprintClient.onDisconnect());
        jeu.addListener((RegisterClientCommandsEvent e) -> {
            e.getDispatcher().register(BlueprintClient.<CommandSourceStack>editCommand(
                    (source, message) -> source.sendSuccess(() -> message, false)));
            e.getDispatcher().register(BlueprintClient.<CommandSourceStack>packsCommand(
                    (source, message) -> source.sendSuccess(() -> message, false)));
        });
    }
}
