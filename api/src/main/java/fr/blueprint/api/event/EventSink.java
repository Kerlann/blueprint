package fr.blueprint.api.event;

import java.util.function.Consumer;

/**
 * Récepteur des émissions d'événements — implémenté par le cœur de Blueprint et
 * installé dans {@link BlueprintEvents} au démarrage du serveur. Les mods tiers
 * n'implémentent jamais cette interface.
 */
public interface EventSink {

    void fire(EventType event, Consumer<EventPayload> payload);
}
