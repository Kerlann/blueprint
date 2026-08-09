package fr.blueprint.fabric.client;

import fr.blueprint.client.BlueprintClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * Le point d'entrée client, et les trois fils que Fabric branche dessus.
 *
 * <p>Du câblage, rien d'autre. Le seul endroit qui demande un regard est
 * {@code FabricClientCommandSource} : c'est le type de source des commandes client chez
 * Fabric, il n'existe nulle part ailleurs, et c'est ici — et seulement ici — qu'il est
 * nommé. Le code commun construit son arbre Brigadier générique sur la source et ne
 * demande d'elle qu'un verbe, {@code sendFeedback}.
 *
 * <p>{@code ClientCommandManager.literal(…)} n'apparaît plus nulle part : ce n'était
 * qu'un {@code LiteralArgumentBuilder} déjà typé sur la source de Fabric. Brigadier
 * suffit, et il est commun à tous les chargeurs.
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

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(BlueprintClient.<FabricClientCommandSource>editCommand(
                    FabricClientCommandSource::sendFeedback));
            dispatcher.register(BlueprintClient.<FabricClientCommandSource>packsCommand(
                    FabricClientCommandSource::sendFeedback));
        });
    }
}
