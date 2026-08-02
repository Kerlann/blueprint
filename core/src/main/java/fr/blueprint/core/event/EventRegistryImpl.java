package fr.blueprint.core.event;

import fr.blueprint.api.event.EventRegistry;
import fr.blueprint.api.event.EventType;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Implémentation du registre des événements — même discipline que les autres registres. */
public final class EventRegistryImpl implements EventRegistry {

    private final Map<Identifier, EventType> byId = new LinkedHashMap<>();
    private final Map<Identifier, String> providerById = new HashMap<>();
    private String currentProvider = "blueprint";
    private boolean frozen;

    public void currentProvider(String modId) {
        this.currentProvider = modId;
    }

    @Override
    public void register(EventType type) {
        if (frozen) {
            throw new IllegalStateException("Registre des événements gelé : « " + type.id()
                    + " » (mod « " + currentProvider + " ») arrive après la phase d'enregistrement");
        }
        String existing = providerById.get(type.id());
        if (existing != null) {
            throw new IllegalStateException("Événement en doublon : « " + type.id()
                    + " » est déjà fourni par « " + existing
                    + " », refusé pour « " + currentProvider + " »");
        }
        byId.put(type.id(), type);
        providerById.put(type.id(), currentProvider);
    }

    public void removeAllFrom(String provider) {
        List<Identifier> ids = new ArrayList<>();
        providerById.forEach((id, p) -> {
            if (p.equals(provider)) {
                ids.add(id);
            }
        });
        for (Identifier id : ids) {
            byId.remove(id);
            providerById.remove(id);
        }
    }

    public void freeze() {
        frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public Optional<String> providerOf(Identifier id) {
        return Optional.ofNullable(providerById.get(id));
    }

    @Override
    public Optional<EventType> get(Identifier id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Collection<EventType> all() {
        return Collections.unmodifiableCollection(byId.values());
    }
}
