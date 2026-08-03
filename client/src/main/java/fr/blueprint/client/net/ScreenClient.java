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
                    // Un HUD n'est PAS un écran : il ne capte rien et ne fige rien.
                    // Le router ici plutôt qu'au rendu est ce qui empêche un menu
                    // permanent de voler le jeu au joueur (10.9).
                    if (model.hud()) {
                        fr.blueprint.client.screen.BlueprintHud.view().show(model);
                        return;
                    }
                    // Un seul écran modal à la fois : setScreen remplace celui d'avant,
                    // ce qui déclenche son removed() — donc un ScreenClose vers un
                    // serveur qui a déjà noté le nouveau. D'où la fermeture SILENCIEUSE
                    // ici : prévenir le serveur effacerait ce qu'il vient d'ouvrir.
                    closeQuietly(context.client());
                    var screen = new BlueprintScreen(payload.blueprint(), model,
                            payload.instance(), ScreenClient::notifyClosed, element ->
                            sendClick(payload.blueprint(), model.name(), element,
                                    payload.instance()));
                    // Les éléments riches (10.8) remontent une VALEUR, par un second
                    // paquet : le clic simple reste au minimum, et c'est de loin le plus
                    // fréquent.
                    screen.setOnValue(interaction -> sendValue(payload.blueprint(),
                            model.name(), payload.instance(), interaction));
                    context.client().setScreen(screen);
                });

        ClientPlayNetworking.registerGlobalReceiver(BlueprintPayloads.ScreenClose.TYPE,
                (payload, context) -> {
                    if (payload.screen().isEmpty()) {
                        closeQuietly(context.client());
                    } else if (BlueprintPayloads.ALL_HUDS.equals(payload.screen())) {
                        fr.blueprint.client.screen.BlueprintHud.view().hideAll();
                    } else {
                        fr.blueprint.client.screen.BlueprintHud.view().hide(payload.screen());
                    }
                });

        // Les modifications d'un tick arrivent ensemble. Chacune nomme son écran :
        // elles vont donc au modal ou à l'un des HUD, et sont jetées si leur
        // destinataire n'est plus affiché.
        ClientPlayNetworking.registerGlobalReceiver(BlueprintPayloads.ScreenUpdates.TYPE,
                (payload, context) -> {
                    fr.blueprint.client.screen.BlueprintHud.view().apply(payload.updates());
                    if (context.client().screen instanceof BlueprintScreen open) {
                        open.apply(payload.instance(), payload.updates());
                    }
                });

        // Une déconnexion ne laisse pas les HUD du serveur précédent à l'écran.
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT
                .register((handler, client) ->
                        fr.blueprint.client.screen.BlueprintHud.view().clear());
    }

    private static void sendClick(net.minecraft.resources.Identifier blueprint,
                                  String screen, String element, int instance) {
        if (ClientPlayNetworking.canSend(BlueprintPayloads.ScreenInteraction.TYPE)) {
            ClientPlayNetworking.send(new BlueprintPayloads.ScreenInteraction(
                    blueprint, screen, element, instance));
        }
    }

    private static void sendValue(net.minecraft.resources.Identifier blueprint, String screen,
                                  int instance,
                                  fr.blueprint.client.screen.BlueprintScreen.Interaction value) {
        if (ClientPlayNetworking.canSend(BlueprintPayloads.ScreenValue.TYPE)) {
            ClientPlayNetworking.send(new BlueprintPayloads.ScreenValue(
                    blueprint, screen, value.element(), instance, value.index(),
                    value.text(), value.number(), value.flag()));
        }
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
            ClientPlayNetworking.send(BlueprintPayloads.ScreenClose.modal());
        }
    }
}
