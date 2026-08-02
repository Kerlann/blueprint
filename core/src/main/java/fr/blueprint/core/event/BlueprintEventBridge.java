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
        for (Blueprint bp : manager.all()) {
            if (!bp.enabled()) {
                continue;
            }
            for (Node node : bp.nodes().values()) {
                if (!node.typeId().equals(eventId)) {
                    continue;
                }
                Ir ir = compiled(bp, node);
                if (ir != null) {
                    scheduler.launch(bp.id(), ir, envFactory.create(bp, trigger));
                }
            }
        }
    }

    /** Compile depuis le nœud d'événement, avec cache invalidé par la révision. */
    private @org.jetbrains.annotations.Nullable Ir compiled(Blueprint bp, Node entry) {
        String key = bp.id() + "#" + entry.uuid() + "@" + bp.revision();
        Ir cached = irCache.get(key);
        if (cached != null) {
            return cached;
        }
        Compiler.CompileResult result = Compiler.compile(bp, nodes, entry.uuid());
        if (!result.success()) {
            BlueprintMod.LOGGER.warn("Blueprint « {} » non exécutable ({} diagnostic(s)) — déclenchement ignoré",
                    bp.id(), result.diagnostics().size());
            return null;
        }
        irCache.put(key, result.ir());
        return result.ir();
    }
}
