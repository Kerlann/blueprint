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
    /**
     * Abonne le pont à <b>tous</b> les événements du registre.
     *
     * <p>C'est ce que l'épic 15b voulait corriger : la garde de paresse d'
     * {@link EventDispatcher} — « pas d'abonné, pas de charge utile » — n'est donc jamais
     * vraie en production, et toute émission entre dans {@link #launchMatching}. La
     * tentative d'abonnement sélectif a été <b>suspendue</b> : voir §5 de
     * {@code docs/plan-optimisation.md}, elle déplaçait la sensibilité d'un blueprint
     * nouvellement créé au tick suivant, ce qui est un changement de sémantique et non une
     * optimisation.
     */
    public void wire(EventDispatcher dispatcher, java.util.Collection<EventType> events) {
        for (EventType event : events) {
            dispatcher.subscribe(event.id(), trigger -> launchMatching(event.id(), trigger));
        }
    }

    private void launchMatching(Identifier eventId, TriggerContext trigger) {
        // UN seul appel : manager.all() enveloppe la collection dans un
        // unmodifiableCollection à chaque invocation. En appeler deux allouait deux
        // enveloppes par émission — et certains événements sont émis à chaque coup porté
        // sur le serveur entier, pas une fois par tick.
        var all = manager.all();
        if (all.isEmpty()) {
            return;
        }
        for (Blueprint bp : all) {
            if (!bp.enabled()) {
                continue;
            }
            for (java.util.UUID entryNode : indexOf(bp).byEvent()
                    .getOrDefault(eventId, java.util.List.of())) {
                Ir ir = compiled(bp, entryNode);
                if (ir != null) {
                    scheduler.launch(bp.id(), ir, envFactory.create(bp, trigger));
                }
            }
        }
        // Purge des blueprints disparus. Le commentaire d'origine la disait « peu
        // fréquente » : c'est faux, on est ici à CHAQUE émission de tout événement. Sans
        // Optional, donc (15a). L'en sortir demande l'abonnement sélectif, suspendu.
        entryCache.keySet().removeIf(id -> !manager.contains(id));
    }

    /**
     * Les nœuds d'entrée de <b>ce</b> blueprint pour <b>cet</b> événement, par l'index.
     *
     * <p>L'index existait déjà — construit pour la répartition générique, reconstruit
     * seulement quand un blueprint change de révision — et les <b>chemins filtrés</b>
     * l'ignoraient : signal, commande, touche et clic parcouraient chaque nœud de chaque
     * graphe à chaque émission, pour n'en retenir presque jamais aucun.
     *
     * <p>Le coût n'était pas théorique. Le budget autorise soixante-quatre signaux par
     * tick, et vingt graphes de cent cinquante nœuds coûtaient <b>1,6 ms par tick</b> à
     * n'en trouver aucun — soit trois pour cent du budget d'un tick, croissant avec la
     * taille du serveur. Le voici mesuré et borné par {@code EventDispatchPerfTest}.
     *
     * <p>Un blueprint désactivé rend une liste vide : c'est la règle de tous les chemins
     * filtrés, et la mettre ici évite de la répéter cinq fois.
     */
    private java.util.List<Node> entriesOf(Blueprint bp, Identifier eventId) {
        if (!bp.enabled()) {
            return java.util.List.of();
        }
        var uuids = indexOf(bp).byEvent().get(eventId);
        if (uuids == null || uuids.isEmpty()) {
            return java.util.List.of();
        }
        java.util.List<Node> found = new java.util.ArrayList<>(uuids.size());
        for (java.util.UUID uuid : uuids) {
            Node node = bp.nodes().get(uuid);
            if (node != null) {
                found.add(node);
            }
        }
        return found;
    }

    // ------------------------------------------------ événement command (7.7)

    /** Les noms de commandes déclarés par les blueprints ACTIFS (suggestions /bpc). */
    public java.util.Set<String> commandNames() {
        // Un blueprint désactivé rend une liste vide : commande retirée (AC 7.7).
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        for (Blueprint bp : manager.all()) {
            for (Node node : entriesOf(bp, StandardEvents.COMMAND.id())) {
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
            for (Node node : entriesOf(bp, StandardEvents.COMMAND.id())) {
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

    // ------------------------------------------------- signaux (5.15 / batch 1)

    /**
     * Budget d'émissions par tick. Un signal peut en émettre un autre : sans borne,
     * un blueprint qui s'auto-signale remplit la file d'exécution sans jamais la
     * vider — le budget de carburant se partage entre exécutions, donc chacune
     * avance moins vite à mesure qu'il y en a plus, et la mémoire monte jusqu'au
     * crash. La borne coupe la récursion là où elle se voit.
     */
    public static final int MAX_SIGNALS_PER_TICK = 64;

    private int signalsThisTick;
    private boolean signalBudgetWarned;

    /** Appelé en fin de tick : le budget de signaux repart à neuf. */
    public void endTick() {
        signalsThisTick = 0;
        signalBudgetWarned = false;
    }

    /** L'index des points d'entrée de ce blueprint, reconstruit si sa révision a bougé. */
    private EntryIndex indexOf(Blueprint bp) {
        EntryIndex index = entryCache.get(bp.id());
        if (index == null || index.revision() != bp.revision()) {
            index = scan(bp);
            entryCache.put(bp.id(), index);
        }
        return index;
    }

    /**
     * Déclenche les blueprints actifs dont un nœud {@code event/signal} porte ce nom
     * en littéral. Retourne le nombre de lancements, ou −1 si le budget du tick est
     * épuisé — l'appelant doit le dire, pas l'avaler.
     */
    public int launchSignal(String name, TriggerContext trigger) {
        if (signalsThisTick >= MAX_SIGNALS_PER_TICK) {
            if (!signalBudgetWarned) {
                signalBudgetWarned = true;
                fr.blueprint.core.BlueprintMod.LOGGER.warn(
                        "Budget de signaux épuisé ({}/tick) : « {} » ignoré. "
                                + "Un blueprint s'auto-signale probablement en boucle.",
                        MAX_SIGNALS_PER_TICK, name);
            }
            return -1;
        }
        signalsThisTick++;
        int launched = 0;
        for (Blueprint bp : manager.all()) {
            for (Node node : entriesOf(bp, StandardEvents.SIGNAL.id())) {
                if (name.equals(signalNameOf(node))) {
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

    private static @org.jetbrains.annotations.Nullable String signalNameOf(Node node) {
        return literalName(node, StandardEvents.SIGNAL.id());
    }

    /** Combien de nœuds actifs écoutent ce signal — sans rien lancer. */
    public int signalListeners(String name) {
        int count = 0;
        for (Blueprint bp : manager.all()) {
            for (Node node : entriesOf(bp, StandardEvents.SIGNAL.id())) {
                if (name.equals(signalNameOf(node))) {
                    count++;
                }
            }
        }
        return count;
    }

    private static @org.jetbrains.annotations.Nullable String commandNameOf(Node node) {
        return literalName(node, StandardEvents.COMMAND.id());
    }

    // ---------------------------------------------------- clics d'écran (10.4)

    /**
     * Lance les blueprints actifs dont un nœud {@code event/gui_clicked} écoute CET
     * élément. Le troisième cas de la même règle après {@code command} et
     * {@code signal} : le littéral filtre, sinon chaque clic réveillerait chaque
     * écouteur de chaque écran, et l'auteur devrait comparer le nom à la main.
     *
     * <p>Le blueprint est <b>ciblé</b> : seul celui qui possède l'écran est consulté.
     * Un autre blueprint ne doit pas pouvoir écouter les boutons d'un menu qui n'est
     * pas le sien — un nom d'élément est court, les collisions seraient la règle.
     */
    /**
     * Les événements d'éléments riches (10.8) : même filtrage par littéral que le clic,
     * pour la même raison — sans lui, chaque saisie réveillerait tous les écouteurs de
     * chaque écran du serveur.
     */
    public int launchGuiEvent(Identifier blueprintId, EventType event, String element,
                              TriggerContext trigger) {
        Blueprint bp = manager.get(blueprintId).orElse(null);
        if (bp == null) {
            return 0;
        }
        int launched = 0;
        for (Node node : entriesOf(bp, event.id())) {
            if (element.equals(literalName(node, event.id(), "element"))) {
                Ir ir = compiled(bp, node.uuid());
                if (ir != null) {
                    scheduler.launch(bp.id(), ir, envFactory.create(bp, trigger));
                    launched++;
                }
            }
        }
        return launched;
    }

    public int launchGuiClick(Identifier blueprintId, String element, TriggerContext trigger) {
        Blueprint bp = manager.get(blueprintId).orElse(null);
        if (bp == null) {
            return 0;
        }
        int launched = 0;
        for (Node node : entriesOf(bp, StandardEvents.GUI_ELEMENT_CLICKED.id())) {
            if (element.equals(literalName(node, StandardEvents.GUI_ELEMENT_CLICKED.id(),
                    "element"))) {
                Ir ir = compiled(bp, node.uuid());
                if (ir != null) {
                    scheduler.launch(bp.id(), ir, envFactory.create(bp, trigger));
                    launched++;
                }
            }
        }
        return launched;
    }

    // ------------------------------------------------------- touches (11.4)

    /**
     * Lance les blueprints actifs dont un nœud {@code event/key_pressed} écoute CET
     * emplacement. Cinquième cas de la règle du littéral filtrant, et le premier où il
     * est un entier plutôt qu'un nom.
     *
     * <p>Non ciblé sur un blueprint, contrairement aux clics d'écran : une touche
     * n'appartient à personne. Deux blueprints peuvent légitimement écouter le même
     * emplacement — un HUD et un menu, par exemple — et interdire cela ferait de l'ordre
     * d'installation des blueprints une donnée de conception.
     */
    public int launchKeyPress(int slot, TriggerContext trigger) {
        int launched = 0;
        for (Blueprint bp : manager.all()) {
            for (Node node : entriesOf(bp, StandardEvents.KEY_PRESSED.id())) {
                Integer listened = keyOf(node);
                if (listened != null && listened == slot) {
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

    /**
     * L'emplacement écouté par ce nœud, ou {@code null} si ce n'en est pas un.
     *
     * <p><b>Le défaut du type compte.</b> Un nœud posé et jamais édité ne porte aucun
     * littéral : sans ce repli, il n'écouterait rien du tout. L'auteur le poserait, le
     * câblerait, presserait sa touche — et rien ne se produirait, sans erreur, sans
     * diagnostic, sans rien à corriger de visible. C'est exactement la forme du point
     * d'entrée mort que ce projet a déjà payée avec {@code signal}.
     *
     * <p>Le cas ne se pose pas pour {@code command} et {@code signal}, dont le défaut est
     * la chaîne vide : une commande sans nom n'a effectivement rien à écouter, et
     * l'auteur DOIT en taper un. Un emplacement, lui, a un défaut qui veut dire quelque
     * chose — le premier.
     *
     * <p>{@code Number} et non {@code Integer} : le silence est la pire panne possible
     * ici, et accepter n'importe quel nombre ne coûte rien.
     */
    private @org.jetbrains.annotations.Nullable Integer keyOf(Node node) {
        if (!StandardEvents.KEY_PRESSED.id().equals(node.typeId())) {
            return null;
        }
        var literal = node.literal("key");
        if (literal == null) {
            literal = nodes.get(node.typeId())
                    .flatMap(type -> type.inputs().stream()
                            .filter(pin -> pin.name().equals("key")).findFirst()
                            .map(fr.blueprint.api.node.NodeType.PinSpec::defaultValue))
                    .orElse(null);
        }
        return literal != null && literal.value() instanceof Number n ? n.intValue() : null;
    }

    /** Le littéral « name » d'un nœud d'événement de ce type, s'il est renseigné. */
    private static @org.jetbrains.annotations.Nullable String literalName(
            Node node, Identifier eventId) {
        return literalName(node, eventId, "name");
    }

    /** Le littéral filtrant d'un nœud d'événement de ce type, s'il est renseigné. */
    private static @org.jetbrains.annotations.Nullable String literalName(
            Node node, Identifier eventId, String pin) {
        if (!eventId.equals(node.typeId())) {
            return null;
        }
        var literal = node.literal(pin);
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
