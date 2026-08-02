package fr.blueprint.api.event;

import org.jetbrains.annotations.ApiStatus;

import java.util.function.Consumer;

/**
 * Émission d'événements vers les blueprints abonnés.
 *
 * <pre>{@code
 * BlueprintEvents.fire(MyEvents.ON_RITUAL, payload -> payload
 *     .set("player", player)
 *     .set("power", 3.5));
 * }</pre>
 *
 * <p>Garanties : un appel hors du thread serveur est reporté sur le thread serveur ;
 * un événement sans abonné ne construit même pas sa charge utile (le consumer n'est
 * pas invoqué) ; avant l'installation du serveur — ou après son arrêt — l'appel est
 * un no-op silencieux, jamais une exception.
 */
public final class BlueprintEvents {

    private static final EventSink NO_OP = (event, payload) -> {
    };

    private static volatile EventSink sink = NO_OP;

    private BlueprintEvents() {
    }

    public static void fire(EventType event, Consumer<EventPayload> payload) {
        sink.fire(event, payload);
    }

    /** Installé par le cœur de Blueprint au démarrage du serveur. Interne. */
    @ApiStatus.Internal
    public static void install(EventSink installed) {
        sink = installed;
    }

    /** Retiré à l'arrêt du serveur. Interne. */
    @ApiStatus.Internal
    public static void uninstall() {
        sink = NO_OP;
    }
}
