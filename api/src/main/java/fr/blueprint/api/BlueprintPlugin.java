package fr.blueprint.api;

import fr.blueprint.api.event.EventRegistry;
import fr.blueprint.api.registry.NodeRegistry;
import fr.blueprint.api.registry.PinTypeRegistry;

/**
 * Point d'entrée d'un mod tiers dans Blueprint.
 *
 * <p>Un mod déclare son implémentation dans {@code fabric.mod.json} :
 * <pre>{@code
 * "entrypoints": { "blueprint": ["com.example.MyPlugin"] }
 * }</pre>
 *
 * <p>Ordre d'appel, tous plugins confondus par phase :
 * {@link #registerTypes} → {@link #registerNodes} → {@link #registerEvents}.
 * Une exception levée dans l'une des phases isole le plugin : elle est journalisée
 * avec le nom du mod, ses enregistrements partiels sont retirés (ses nœuds deviennent
 * fantômes dans les graphes qui les utilisent), et les autres plugins chargent
 * normalement — jamais de crash au démarrage.
 */
public interface BlueprintPlugin {

    /** Enregistre les nœuds du mod. Obligatoire. */
    void registerNodes(NodeRegistry registry);

    /** Types de pins personnalisés. Appelé AVANT {@link #registerNodes}. */
    default void registerTypes(PinTypeRegistry registry) {
    }

    /** Événements déclencheurs personnalisés. Appelé APRÈS {@link #registerNodes}. */
    default void registerEvents(EventRegistry registry) {
    }
}
