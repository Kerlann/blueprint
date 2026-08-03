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
    private final Map<String, CachedIr> irCache = new HashMap<>();

    /** IR compilée et la révision qui l'a produite (QA NET-003). */
    private record CachedIr(int revision, Ir ir) {
    }

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

    // ------------------------------------------------ événement command (7.7)

    /** Les noms de commandes déclarés par les blueprints ACTIFS (suggestions /bpc). */
    public java.util.Set<String> commandNames() {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        for (Blueprint bp : manager.all()) {
            if (!bp.enabled()) {
                continue; // désactivé = commande retirée (AC 7.7)
            }
            for (Node node : bp.nodes().values()) {
                String name = commandNameOf(node);
                if (name != null) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    /**
     * Déclenche les blueprints actifs dont un nœud {@code event/command} porte ce
     * nom en littéral ; retourne le nombre de lancements.
     */
    public int launchCommand(String name, TriggerContext trigger) {
        int launched = 0;
        for (Blueprint bp : manager.all()) {
            if (!bp.enabled()) {
                continue;
            }
            for (Node node : bp.nodes().values()) {
                if (name.equals(commandNameOf(node))) {
                    Ir ir = compiled(bp, node.uuid());
                    if (ir != null) {
                        scheduler.launch(bp.id(), ir, envFactory.create(bp, trigger));
                        launched++;
                    }
                }
            }
        }
        return launched;
    }

    private static @org.jetbrains.annotations.Nullable String commandNameOf(Node node) {
        if (!StandardEvents.COMMAND.id().equals(node.typeId())) {
            return null;
        }
        var literal = node.literal("name");
        return literal != null && literal.value() instanceof String s && !s.isBlank() ? s : null;
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

    /**
     * Compile depuis le nœud d'événement, avec cache invalidé par la révision.
     *
     * <p>QA NET-003 : la révision est DANS l'entrée, pas dans la clé — sinon chaque
     * enregistrement laisserait derrière lui une IR morte, et depuis que l'éditeur
     * enregistre par le réseau (6.3) un joueur en fait des dizaines par session.
     * Le cache est ainsi borné par (blueprints × nœuds d'entrée).
     */
    private @org.jetbrains.annotations.Nullable Ir compiled(Blueprint bp, java.util.UUID entry) {
        String key = bp.id() + "#" + entry;
        CachedIr cached = irCache.get(key);
        if (cached != null && cached.revision() == bp.revision()) {
            return cached.ir();
        }
        Compiler.CompileResult result = Compiler.compile(bp, nodes, entry);
        if (!result.success()) {
            // FR41 : nommer le mod manquant. « 3 diagnostics » n'aide personne à
            // comprendre qu'il suffit de réinstaller un mod pour que tout reparte.
            var missing = fr.blueprint.core.graph.GhostNode.missingProviders(bp, nodes);
            if (missing.isEmpty()) {
                // Nommer le PREMIER diagnostic : « 1 diagnostic(s) » n'a jamais aidé
                // personne à comprendre pourquoi son graphe ne part pas.
                String first = result.diagnostics().isEmpty() ? "?"
                        : result.diagnostics().get(0).code() + " "
                        + result.diagnostics().get(0).args();
                BlueprintMod.LOGGER.warn(
                        "Blueprint « {} » non exécutable ({} diagnostic(s), dont {}) — déclenchement ignoré",
                        bp.id(), result.diagnostics().size(), first);
            } else {
                BlueprintMod.LOGGER.warn("Blueprint « {} » non exécutable : mod(s) absent(s) — {}",
                        bp.id(), fr.blueprint.core.graph.GhostNode.describeMissing(missing));
            }
            irCache.remove(key);
            return null;
        }
        irCache.put(key, new CachedIr(bp.revision(), result.ir()));
        return result.ir();
    }

    /** Taille du cache d'IR (test de non-régression NET-003). */
    public int cachedIrCount() {
        return irCache.size();
    }
}
