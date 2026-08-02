package fr.blueprint.core.event;

import fr.blueprint.api.event.EventType;
import fr.blueprint.api.event.TriggerContext;
import fr.blueprint.core.BlueprintManager;
import fr.blueprint.core.BlueprintMod;
import fr.blueprint.core.compile.Compiler;
import fr.blueprint.core.compile.ir.Ir;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.Node;
import fr.blueprint.core.registry.NodeRegistryImpl;
import fr.blueprint.core.vm.BlueprintScheduler;
import fr.blueprint.core.vm.ExecutionEnvironment;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Le pont événement → ordonnanceur (story 7.6, AC6) : à l'émission d'un événement,
 * chaque blueprint <b>actif</b> contenant un nœud de cet événement est compilé
 * (cache par révision) et lancé depuis ce nœud. C'est la pièce qui rend le produit
 * jouable : un clic de joueur devient une exécution de graphe.
 */
public final class BlueprintEventBridge {

    /** Fabrique l'environnement d'exécution — en jeu : serveur/monde/vars ; en test : factice. */
    public interface EnvFactory {
        ExecutionEnvironment create(Blueprint blueprint, TriggerContext trigger);
    }

    private final BlueprintManager manager;
    private final NodeRegistryImpl nodes;
    private final BlueprintScheduler scheduler;
    private final EnvFactory envFactory;
    private final Map<String, Ir> irCache = new HashMap<>();
    // Index des nœuds d'entrée par blueprint, keyé par révision (QA BRIDGE-001) : le
    // parcours par émission passe de O(blueprints × nœuds) à O(blueprints) avec une
    // comparaison d'entier ; reconstruction par blueprint seulement quand il est édité.
    private final Map<Identifier, EntryIndex> entryCache = new HashMap<>();

    private record EntryIndex(int revision, Map<Identifier, java.util.List<java.util.UUID>> byEvent) {
    }

    public BlueprintEventBridge(BlueprintManager manager, NodeRegistryImpl nodes,
                                BlueprintScheduler scheduler, EnvFactory envFactory) {
        this.manager = manager;
        this.nodes = nodes;
        this.scheduler = scheduler;
        this.envFactory = envFactory;
    }

    /** Abonne le pont à tous les événements du registre. */
    public void wire(EventDispatcher dispatcher, java.util.Collection<EventType> events) {
        for (EventType event : events) {
            dispatcher.subscribe(event.id(), trigger -> launchMatching(event.id(), trigger));
        }
    }

    private void launchMatching(Identifier eventId, TriggerContext trigger) {
        if (manager.all().isEmpty()) {
            return;
        }
        for (Blueprint bp : manager.all()) {
            if (!bp.enabled()) {
                continue;
            }
            EntryIndex index = entryCache.get(bp.id());
            if (index == null || index.revision() != bp.revision()) {
                index = scan(bp);
                entryCache.put(bp.id(), index);
            }
            for (java.util.UUID entryNode : index.byEvent().getOrDefault(eventId, java.util.List.of())) {
                Ir ir = compiled(bp, entryNode);
                if (ir != null) {
                    scheduler.launch(bp.id(), ir, envFactory.create(bp, trigger));
                }
            }
        }
        // Purge des blueprints disparus (peu fréquent, coût borné par la taille du cache).
        entryCache.keySet().removeIf(id -> manager.get(id).isEmpty());
    }

    /** Recense les nœuds d'entrée d'un blueprint, groupés par événement. */
    private EntryIndex scan(Blueprint bp) {
        Map<Identifier, java.util.List<java.util.UUID>> byEvent = new HashMap<>();
        for (Node node : bp.nodes().values()) {
            boolean entry = nodes.get(node.typeId())
                    .map(fr.blueprint.api.node.NodeType::entryPoint).orElse(false);
            if (entry) {
                byEvent.computeIfAbsent(node.typeId(), k -> new java.util.ArrayList<>()).add(node.uuid());
            }
        }
        return new EntryIndex(bp.revision(), byEvent);
    }

    /** Compile depuis le nœud d'événement, avec cache invalidé par la révision. */
    private @org.jetbrains.annotations.Nullable Ir compiled(Blueprint bp, java.util.UUID entry) {
        String key = bp.id() + "#" + entry + "@" + bp.revision();
        Ir cached = irCache.get(key);
        if (cached != null) {
            return cached;
        }
        Compiler.CompileResult result = Compiler.compile(bp, nodes, entry);
        if (!result.success()) {
            BlueprintMod.LOGGER.warn("Blueprint « {} » non exécutable ({} diagnostic(s)) — déclenchement ignoré",
                    bp.id(), result.diagnostics().size());
            return null;
        }
        irCache.put(key, result.ir());
        return result.ir();
    }
}
