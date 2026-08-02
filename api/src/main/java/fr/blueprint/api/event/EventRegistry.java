package fr.blueprint.api.event;

import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.Optional;

/**
 * Registre des événements déclencheurs. Les mods tiers y enregistrent leurs
 * événements depuis {@code BlueprintPlugin#registerEvents} ; le registre est
 * ensuite gelé.
 */
public interface EventRegistry {

    /**
     * @throws IllegalStateException si le registre est gelé ou si l'identifiant est
     *         déjà pris (le message nomme les deux mods fournisseurs).
     */
    void register(EventType type);

    Optional<EventType> get(Identifier id);

    Collection<EventType> all();
}
