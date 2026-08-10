package fr.blueprint.fabric.client;

import fr.blueprint.client.BlueprintClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * Le point d'entrée client, et les trois fils que Fabric branche dessus.
 *
 * <p>Du câblage, rien d'autre — et plus aucune commande. Ce fichier en enregistrait deux
 * racines, {@code /blueprint-edit} et {@code /blueprint-packs}, seul endroit du dépôt où
 * {@code FabricClientCommandSource} était nommé. Le mod n'expose plus qu'une racine,
 * {@code /blueprint}, côté serveur : l'alias a disparu et les packs passent par un paquet
 * ({@code BlueprintClient.applyPacksAction}).
 *
 * <p>Ce n'est pas qu'un rangement. Une racine <i>cliente</i> nommée {@code blueprint} aurait
 * intercepté tout l'arbre serveur : Fabric ne renvoie au serveur que les commandes inconnues,
 * pas celles dont seul le sous-chemin manque.
 */
public final class FabricClientBootstrap implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        BlueprintClient.init();

        ClientTickEvents.END_CLIENT_TICK.register(BlueprintClient::endClientTick);

        ClientPlayConnectionEvents.JOIN.register(
                (handler, sender, client) -> BlueprintClient.onJoin());
        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> BlueprintClient.onDisconnect());
    }
}
