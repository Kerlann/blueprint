package fr.blueprint.core.event;

import fr.blueprint.api.event.EventType;
import fr.blueprint.api.event.TriggerContext;
import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * Contexte de déclenchement concret : les valeurs de la charge utile, validées à
 * l'émission, exposées aux nœuds d'événement et aux abonnés.
 */
public final class TriggerContextImpl implements TriggerContext {

    private final EventType event;
    private final Map<String, Object> values;

    public TriggerContextImpl(EventType event, Map<String, Object> values) {
        this.event = event;
        this.values = Map.copyOf(values);
    }

    /** Charge utile complète — nécessaire à la capture d'une exécution suspendue (3.4). */
    public Map<String, Object> values() {
        return values;
    }

    @Override
    public Identifier eventId() {
        return event.id();
    }

    @Override
    public Object output(String name) {
        // output() lève si la sortie n'est pas déclarée — erreur de développeur nommée.
        event.output(name);
        return values.get(name);
    }
}
