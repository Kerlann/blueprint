package fr.blueprint.client.net;

import fr.blueprint.client.screen.BlueprintScreen;
import fr.blueprint.core.net.BlueprintPayloads;
import fr.blueprint.core.net.ScreenSync;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Le côté client des écrans de blueprint (story 10.3) : ouvrir sur ordre du serveur,
 * fermer et le lui dire.
 *
 * <p>Ne décide de rien : le serveur envoie une <b>description</b>, le client la compose.
 * Un flux illisible ne ferme rien et ne lève rien — il se signale et laisse le joueur
 * où il était, parce qu'un écran qui se ferme tout seul sans explication est pire
 * qu'un écran qui ne s'ouvre pas.
 */
public final class ScreenClient {

    private ScreenClient() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(BlueprintPayloads.ScreenOpen.TYPE,
                (payload, context) -> {
                    var model = ScreenSync.fromBytes(payload.data());
                    if (model == null) {
                        context.client().gui.setOverlayMessage(Component.translatable(
                                "blueprint.screen.unreadable", payload.screen()), false);
                        return;
                    }
                    // Un seul écran à la fois : setScreen remplace celui d'avant, ce qui
                    // déclenche son onClose — donc un ScreenClose vers un serveur qui a
                    // déjà noté le nouveau. D'où la fermeture SILENCIEUSE ici : prévenir
                    // le serveur effacerait l'écran qu'il vient tout juste d'ouvrir.
                    closeQuietly(context.client());
                    context.client().setScreen(new BlueprintScreen(
                            payload.blueprint(), model, ScreenClient::notifyClosed));
                });

        ClientPlayNetworking.registerGlobalReceiver(BlueprintPayloads.ScreenClose.TYPE,
                (payload, context) -> closeQuietly(context.client()));
    }

    /**
     * Ferme l'écran Blueprint ouvert SANS prévenir le serveur — c'est lui qui l'a
     * demandé, ou il vient d'en ouvrir un autre. Ne touche à aucun autre écran : fermer
     * l'inventaire du joueur parce qu'un blueprint a été désactivé serait une surprise.
     */
    private static void closeQuietly(Minecraft client) {
        if (client.screen instanceof BlueprintScreen) {
            suppressNotify = true;
            try {
                client.setScreen(null);
            } finally {
                suppressNotify = false;
            }
        }
    }

    /**
     * Vrai pendant une fermeture décidée par le serveur : le {@code onClose} qui suit
     * ne doit pas lui renvoyer un message qu'il n'attend pas.
     */
    private static boolean suppressNotify;

    private static void notifyClosed() {
        if (suppressNotify) {
            return;
        }
        if (ClientPlayNetworking.canSend(BlueprintPayloads.ScreenClose.TYPE)) {
            ClientPlayNetworking.send(new BlueprintPayloads.ScreenClose());
        }
    }
}
