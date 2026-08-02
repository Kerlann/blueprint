package fr.blueprint.api.event;

/**
 * Charge utile d'un événement en cours d'émission. Chaque {@code set} est validé
 * contre les sorties déclarées de l'événement : pin inconnu ou valeur du mauvais
 * type Java → exception de développement immédiate nommant l'événement et le pin.
 */
public interface EventPayload {

    EventPayload set(String output, Object value);
}
