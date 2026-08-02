package fr.blueprint.core.event;

import fr.blueprint.api.event.EventPayload;
import fr.blueprint.api.event.EventSink;
import fr.blueprint.api.event.EventType;
import fr.blueprint.api.event.TriggerContext;
import fr.blueprint.core.BlueprintMod;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Distribution des événements aux abonnés (story 2.5). Deux garanties structurantes :
 *
 * <ul>
 * <li><b>Paresse</b> (AC4) : sans abonné, le constructeur de charge utile n'est même
 *     pas invoqué — émettre un événement que personne n'écoute coûte une lecture de map.</li>
 * <li><b>Thread-sûreté</b> (AC3) : une émission hors du thread serveur est reportée
 *     sur le thread serveur via la porte injectée — les mods peuvent émettre depuis
 *     leurs workers sans se poser de question.</li>
 * </ul>
 *
 * <p>Les abonnés réels (exécutions de blueprints) arrivent avec l'ordonnanceur (3.5)
 * et les événements du monde (7.6).
 */
public final class EventDispatcher implements EventSink {

    /** Porte vers le thread serveur — en jeu : {@code server.isSameThread()} / {@code server.execute}. */
    public interface ThreadGate {
        boolean isOnThread();

        void submit(Runnable task);
    }

    /** Un abonné : plus tard, le lancement d'une exécution de blueprint. */
    @FunctionalInterface
    public interface EventSubscriber {
        void onEvent(TriggerContext trigger);
    }

    private final ThreadGate gate;
    // ConcurrentHashMap obligatoire : fire() lit cette map potentiellement HORS du
    // thread serveur (c'est le contrat AC3), pendant que subscribe/unsubscribe la
    // mutent sur le thread serveur. Les listes sont des CopyOnWriteArrayList pour la
    // même raison. (Correction QA EVT-001, review 2.5.)
    private final Map<Identifier, List<EventSubscriber>> subscribers = new ConcurrentHashMap<>();

    public EventDispatcher(ThreadGate gate) {
        this.gate = gate;
    }

    public void subscribe(Identifier eventId, EventSubscriber subscriber) {
        subscribers.computeIfAbsent(eventId, k -> new CopyOnWriteArrayList<>()).add(subscriber);
    }

    public void unsubscribe(Identifier eventId, EventSubscriber subscriber) {
        List<EventSubscriber> list = subscribers.get(eventId);
        if (list != null) {
            list.remove(subscriber);
        }
    }

    @Override
    public void fire(EventType event, Consumer<EventPayload> payload) {
        // AC4 : le test d'abonnement précède TOUT — la charge utile n'existe jamais pour rien.
        List<EventSubscriber> list = subscribers.get(event.id());
        if (list == null || list.isEmpty()) {
            return;
        }
        if (gate.isOnThread()) {
            deliver(event, payload, list);
        } else {
            gate.submit(() -> deliver(event, payload, list));
        }
    }

    private void deliver(EventType event, Consumer<EventPayload> payload, List<EventSubscriber> list) {
        PayloadImpl collected = new PayloadImpl(event);
        payload.accept(collected);
        TriggerContext trigger = new TriggerContextImpl(event, collected.values);
        for (EventSubscriber subscriber : list) {
            try {
                subscriber.onEvent(trigger);
            } catch (Exception e) {
                // Un abonné fautif n'empêche jamais les autres d'être servis.
                BlueprintMod.LOGGER.error("Abonné en échec sur l'événement « {} »", event.id(), e);
            }
        }
    }

    /** Charge utile validée à l'écriture contre les sorties déclarées de l'événement. */
    private static final class PayloadImpl implements EventPayload {

        private final EventType event;
        private final Map<String, Object> values = new LinkedHashMap<>();

        private PayloadImpl(EventType event) {
            this.event = event;
        }

        @Override
        public EventPayload set(String output, Object value) {
            EventType.OutDef def = event.output(output);   // lève si sortie inconnue
            if (value != null && !def.type().javaType().isInstance(value)) {
                throw new IllegalStateException("Événement « " + event.id() + " » : la sortie « "
                        + output + " » attend " + def.type().javaType().getSimpleName()
                        + ", reçu " + value.getClass().getSimpleName());
            }
            values.put(output, value);
            return this;
        }
    }
}
