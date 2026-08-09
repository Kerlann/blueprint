package fr.blueprint.client.net;

import fr.blueprint.client.editor.BlueprintEditorScreen;
import fr.blueprint.core.net.BlueprintPayloads;
import fr.blueprint.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.Optional;
import java.util.UUID;

/**
 * Côté client du débogueur (story 9.1b) : envoie les actions, remet les instantanés à
 * l'éditeur ouvert.
 *
 * <p>Un instantané n'est appliqué qu'à l'écran <b>réellement affiché</b> — même règle
 * qu'en 6.3 après la correction NET-001 : un drapeau collant finirait par mentir.
 */
public final class DebugClient {

    private DebugClient() {
    }

    public static void register() {
        var network = Platform.clientNetwork();
        network.receive(BlueprintPayloads.DebugSnapshot.TYPE,
                (payload, context) -> {
                    if (Minecraft.getInstance().screen instanceof BlueprintEditorScreen editor) {
                        editor.debug().accept(payload);
                    }
                });
        // Déconnexion : plus de serveur, donc plus de vérité à afficher.
    }

    /** Déconnexion : le débogueur affiché ne parle plus de rien. */
    public static void onDisconnect() {
        if (Minecraft.getInstance().screen instanceof BlueprintEditorScreen editor) {
            editor.debug().clear();
        }
    }

    private static void send(Identifier blueprint, BlueprintPayloads.DebugAction action,
                             Optional<UUID> node) {
        if (Platform.clientNetwork().canSend(BlueprintPayloads.DebugCommand.TYPE)) {
            Platform.clientNetwork().send(new BlueprintPayloads.DebugCommand(blueprint, action, node));
        }
    }

    public static void start(Identifier blueprint) {
        send(blueprint, BlueprintPayloads.DebugAction.ON, Optional.empty());
    }

    public static void stop(Identifier blueprint) {
        send(blueprint, BlueprintPayloads.DebugAction.OFF, Optional.empty());
    }

    public static void toggleBreakpoint(Identifier blueprint, UUID node) {
        send(blueprint, BlueprintPayloads.DebugAction.TOGGLE_BREAKPOINT, Optional.of(node));
    }

    public static void step(Identifier blueprint) {
        send(blueprint, BlueprintPayloads.DebugAction.STEP, Optional.empty());
    }

    public static void resume(Identifier blueprint) {
        send(blueprint, BlueprintPayloads.DebugAction.CONTINUE, Optional.empty());
    }
}
