package fr.blueprint.core.registry;

import fr.blueprint.api.pin.PinType;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.api.registry.PinTypeRegistry;
import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Implémentation du registre des types de pins. Le fournisseur courant est
 * positionné par le chargeur de plugins avant chaque appel à
 * {@code BlueprintPlugin#registerTypes}, pour que les messages d'erreur nomment
 * le mod fautif (story 2.2).
 */
public final class PinTypeRegistryImpl implements PinTypeRegistry {

    private final Map<Identifier, PinType> byId = new LinkedHashMap<>();
    private final Map<Identifier, String> providerById = new HashMap<>();
    private String currentProvider = "blueprint";
    private boolean frozen;

    /** Enregistre les 16 types de base sous le fournisseur « blueprint ». */
    public void registerBuiltins() {
        String previous = currentProvider;
        currentProvider = "blueprint";
        try {
            for (PinType type : PinTypes.builtin()) {
                register(type);
            }
        } finally {
            currentProvider = previous;
        }
    }

    /** Nom du mod dont les enregistrements sont en cours (pour les diagnostics). */
    public void currentProvider(String modId) {
        this.currentProvider = modId;
    }

    @Override
    public void register(PinType type) {
        if (frozen) {
            throw new IllegalStateException("Registre des types de pins gelé : « " + type.id()
                    + " » (mod « " + currentProvider + " ») arrive après la phase d'enregistrement");
        }
        String existingProvider = providerById.get(type.id());
        if (existingProvider != null) {
            throw new IllegalStateException("Type de pin en doublon : « " + type.id()
                    + " » est déjà fourni par « " + existingProvider
                    + " », refusé pour « " + currentProvider + " »");
        }
        if (type.supportsLiteral() && (!type.hasCodec() || !type.hasStreamCodec())) {
            throw new IllegalStateException("Type de pin incomplet : « " + type.id()
                    + " » (mod « " + currentProvider + " ») supporte les littéraux mais ne fournit pas "
                    + (type.hasCodec() ? "de stream codec" : "de codec")
                    + " — un littéral ne survivrait pas à une sauvegarde");
        }
        if (type.translationKey() == null || type.translationKey().isBlank()) {
            throw new IllegalStateException("Type de pin sans clé de traduction : « " + type.id()
                    + " » (mod « " + currentProvider + " »)");
        }
        byId.put(type.id(), type);
        providerById.put(type.id(), currentProvider);
    }

    /** Fige le registre ; tout {@link #register} ultérieur lève. */
    public void freeze() {
        frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }

    /** Le mod ayant fourni un type, pour les diagnostics et les nœuds fantômes. */
    public Optional<String> providerOf(Identifier id) {
        return Optional.ofNullable(providerById.get(id));
    }

    @Override
    public Optional<PinType> get(Identifier id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Collection<PinType> all() {
        return Collections.unmodifiableCollection(byId.values());
    }
}
