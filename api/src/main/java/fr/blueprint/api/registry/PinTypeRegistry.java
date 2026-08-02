package fr.blueprint.api.registry;

import fr.blueprint.api.pin.PinType;
import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.Optional;

/**
 * Registre des types de pins. Les mods tiers y enregistrent leurs types depuis
 * {@code BlueprintPlugin#registerTypes} ; le registre est ensuite gelé — toute
 * écriture tardive est une erreur de développeur.
 */
public interface PinTypeRegistry {

    /**
     * @throws IllegalStateException si le registre est gelé, si l'identifiant est déjà
     *         pris (le message nomme les deux fournisseurs), ou si le type déclare
     *         supporter les littéraux sans fournir codec et stream codec.
     */
    void register(PinType type);

    Optional<PinType> get(Identifier id);

    Collection<PinType> all();
}
