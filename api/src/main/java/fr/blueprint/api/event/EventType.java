package fr.blueprint.api.event;

import fr.blueprint.api.pin.PinType;
import fr.blueprint.api.pin.PinTypes;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Type d'événement déclencheur : le point d'entrée d'un blueprint. Ses sorties sont
 * les données contextuelles offertes au graphe ({@code player}, {@code pos}…).
 *
 * <pre>{@code
 * EventType ON_RITUAL = EventType.builder(Identifier.fromNamespaceAndPath("mymod", "ritual_complete"))
 *     .out("player", PinTypes.PLAYER)
 *     .out("power", PinTypes.DOUBLE)
 *     .dispatch(Dispatch.PER_PLAYER)
 *     .build();
 * }</pre>
 */
public final class EventType {

    /** Une sortie de données de l'événement. */
    public record OutDef(String name, PinType type) {
    }

    private final Identifier id;
    private final List<OutDef> outputs;
    private final Dispatch dispatch;
    private final String titleKey;

    private EventType(Builder b) {
        this.id = b.id;
        this.outputs = List.copyOf(b.outputs);
        this.dispatch = b.dispatch;
        this.titleKey = b.titleKey != null ? b.titleKey
                : "blueprint.event." + b.id.getNamespace() + "." + b.id.getPath() + ".name";
    }

    public Identifier id() {
        return id;
    }

    public List<OutDef> outputs() {
        return outputs;
    }

    public OutDef output(String name) {
        for (OutDef out : outputs) {
            if (out.name().equals(name)) {
                return out;
            }
        }
        throw new IllegalStateException("Événement « " + id + " » : pas de sortie « " + name + " »");
    }

    public Dispatch dispatch() {
        return dispatch;
    }

    public String titleKey() {
        return titleKey;
    }

    @Override
    public String toString() {
        return id.toString();
    }

    public static Builder builder(Identifier id) {
        return new Builder(id);
    }

    /** Construit un {@link EventType}. Toute incohérence lève au {@code build()}. */
    public static final class Builder {

        private final Identifier id;
        private final List<OutDef> outputs = new ArrayList<>();
        private Dispatch dispatch = Dispatch.GLOBAL;
        private String titleKey;

        private Builder(Identifier id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public Builder out(String name, PinType type) {
            if (type == PinTypes.EXEC) {
                throw new IllegalStateException("Événement « " + id + " » : la sortie « " + name
                        + " » ne peut pas être de type exec — l'exécution part du nœud d'événement lui-même");
            }
            outputs.add(new OutDef(name, type));
            return this;
        }

        public Builder dispatch(Dispatch dispatch) {
            this.dispatch = dispatch;
            return this;
        }

        public Builder titleKey(String key) {
            this.titleKey = key;
            return this;
        }

        public EventType build() {
            Set<String> seen = new HashSet<>();
            for (OutDef out : outputs) {
                if (!seen.add(out.name())) {
                    throw new IllegalStateException("Événement « " + id
                            + " » : deux sorties nommées « " + out.name() + " »");
                }
            }
            return new EventType(this);
        }
    }
}
