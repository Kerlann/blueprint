package fr.blueprint.core.compile;

import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinKind;
import fr.blueprint.core.compile.ir.Instruction;
import fr.blueprint.core.compile.ir.Ir;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.Diagnostic;
import fr.blueprint.core.graph.DiagnosticCode;
import fr.blueprint.core.graph.GraphValidator;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.Node;
import fr.blueprint.core.graph.VarNodes;
import fr.blueprint.core.graph.Variable;
import fr.blueprint.core.registry.NodeRegistryImpl;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Compilation d'un graphe en IR (story 3.2). Validation d'abord — une erreur produit
 * des diagnostics ciblés, jamais une exception. Puis linéarisation du flux exec depuis
 * le nœud de départ : chaque nœud est émis une fois (un successeur déjà émis devient
 * une cible directe — les boucles exec sont des arêtes arrière vers le début du nœud,
 * préparation des purs comprise), les purs sont émis avant leur premier consommateur
 * et mémoïsés par position (FR13), les littéraux deviennent des {@code Const} dédupliqués.
 */
public final class Compiler {

    /** Résultat : IR nulle si échec, diagnostics dans tous les cas (avertissements inclus). */
    public record CompileResult(@Nullable Ir ir, List<Diagnostic> diagnostics) {
        public boolean success() {
            return ir != null;
        }
    }

    private final Blueprint bp;
    private final NodeRegistryImpl registry;
    private final List<Instruction> out = new ArrayList<>();
    private final Map<String, Integer> slotOf = new HashMap<>();
    private final Map<String, Integer> emittedAt = new HashMap<>();
    private final Set<String> constEmitted = new HashSet<>();
    // Mémoïsation des purs PAR PORTÉE DE BRANCHE (correction QA VM-COMP-001) : un pur
    // émis avant un embranchement domine les deux branches (réutilisable) ; un pur émis
    // DANS une branche ne domine pas l'autre — chaque branche part d'une copie de la
    // portée courante, sinon la seconde branche lirait un slot jamais écrit.
    private final java.util.ArrayDeque<Set<UUID>> pureScopes = new java.util.ArrayDeque<>();
    // Index des liens par pin, construit une fois : Blueprint.linksFrom/Into parcourent
    // tous les liens à chaque appel — O(nœuds × liens) au total, mesurable dès 1 000
    // nœuds (PERF-001 du gate 1.3, matérialisé par le banc NFR2). Ici : O(liens).
    private final Map<String, List<Link>> linksFromPin = new HashMap<>();
    private final Map<String, List<Link>> linksIntoPin = new HashMap<>();
    private int nextSlot;

    /**
     * Le graphe qu'on est en train d'émettre : le principal, ou un corps de fonction
     * déplié (story 20.1).
     *
     * <p>{@code prefix} distingue <b>deux dépliages du même corps</b>. Sans lui, la
     * mémoïsation par UUID ferait sauter le second appel au code du premier — c'est-à-dire
     * un {@code CallSub} sans retour — et les deux dépliages partageraient leurs slots.
     */
    private record Scope(Map<UUID, Node> nodes, Map<String, List<Link>> from,
                         Map<String, List<Link>> into, String prefix) {
    }

    private final java.util.ArrayDeque<Scope> scopes = new java.util.ArrayDeque<>();

    private Compiler(Blueprint bp, NodeRegistryImpl registry) {
        this.bp = bp;
        this.registry = registry;
        this.pureScopes.push(new HashSet<>());
        for (Link link : bp.links()) {
            linksFromPin.computeIfAbsent(link.fromNode() + "/" + link.fromPin(), k -> new ArrayList<>()).add(link);
            linksIntoPin.computeIfAbsent(link.toNode() + "/" + link.toPin(), k -> new ArrayList<>()).add(link);
        }
        scopes.push(new Scope(bp.nodes(), linksFromPin, linksIntoPin, ""));
    }

    private Node node(UUID id) {
        return scopes.peek().nodes().get(id);
    }

    /** La clé de mémoïsation et de slot d'un nœud, propre au dépliage en cours. */
    private String key(UUID node) {
        return scopes.peek().prefix() + node;
    }

    private List<Link> from(UUID node, String pin) {
        return scopes.peek().from().getOrDefault(node + "/" + pin, List.of());
    }

    private List<Link> into(UUID node, String pin) {
        return scopes.peek().into().getOrDefault(node + "/" + pin, List.of());
    }

    public static CompileResult compile(Blueprint bp, NodeRegistryImpl registry, UUID startNode) {
        GraphValidator.ValidationResult validation = GraphValidator.validate(bp, registry);
        if (!validation.executable()) {
            return new CompileResult(null, validation.diagnostics());
        }
        if (bp.node(startNode) == null) {
            List<Diagnostic> diagnostics = new ArrayList<>(validation.diagnostics());
            diagnostics.add(Diagnostic.error(DiagnosticCode.NODE_NOT_FOUND,
                    Diagnostic.node(startNode), startNode.toString()));
            return new CompileResult(null, diagnostics);
        }
        Compiler compiler = new Compiler(bp, registry);
        compiler.emitNode(startNode);
        return new CompileResult(
                new Ir(bp.id(), bp.revision(), startNode, compiler.out, compiler.nextSlot),
                validation.diagnostics());
    }

    /**
     * Émet un nœud (préparation des entrées puis {@code Call}) et, récursivement, ses
     * successeurs exec. Retourne l'index de début — la cible qu'un prédécesseur doit viser.
     */
    private int emitNode(UUID id) {
        return emitNode(id, null);
    }

    /**
     * {@code entryPin} : le pin d'exécution PAR LEQUEL on entre. Presque tous les nœuds
     * s'en moquent — mais un {@code gate} exécute du code différent selon qu'on l'ouvre,
     * qu'on le ferme ou qu'on le traverse, et deux entrées différentes ne peuvent donc
     * pas partager le même code émis (d'où la clé de mémoïsation composite).
     */
    private int emitNode(UUID id, @Nullable String entryPin) {
        String key = key(id) + "#" + (entryPin == null ? "" : entryPin);
        Integer existing = emittedAt.get(key);
        if (existing != null) {
            return existing;
        }
        // L'index de début est réservé AVANT la préparation : une arête arrière (boucle)
        // revient sur les Const/purs du nœud, qui se ré-exécutent à chaque itération.
        int startIndex = out.size();
        emittedAt.put(key, startIndex);

        Node node = node(id);
        NodeType type = registry.get(node.typeId()).orElseThrow();   // garanti par la validation

        // var/set s'abaisse en StoreVar (story 5.5) — pas de Call, pas d'action.
        if (VarNodes.SET.equals(node.typeId())) {
            emitVarSet(node, type);
            return startIndex;
        }
        // Flux structuré (7.1b) : abaissé en CallSub/JmpIf/Yield + Calls synthétisés.
        String path = node.typeId().getNamespace().equals("blueprint")
                ? node.typeId().getPath() : "";
        switch (path) {
            case "func/call" -> {
                emitCall(node);
                return startIndex;
            }
            case "func/param", "func/result" -> {
                // Atteints hors d'un dépliage : la validation l'interdit, mais un corps
                // orphelin ne doit pas faire tomber le compilateur.
                return startIndex;
            }
            case "flow/return" -> {
                // Terminal pour TOUTE l'exécution (les frames ne le rattrapent pas).
                out.add(new Instruction.Return(id));
                return startIndex;
            }
            case "flow/sequence" -> {
                emitSequence(node, type);
                return startIndex;
            }
            case "flow/do_once" -> {
                emitDoOnce(node);
                return startIndex;
            }
            case "flow/while" -> {
                emitWhile(node, type, startIndex);
                return startIndex;
            }
            case "flow/for" -> {
                emitFor(node, type);
                return startIndex;
            }
            case "flow/for_each" -> {
                emitForEach(node, type);
                return startIndex;
            }
            case "flow/gate" -> {
                emitGate(node, entryPin);
                return startIndex;
            }
            case "flow/wait_until" -> {
                emitWaitUntil(node, type, startIndex);
                return startIndex;
            }
            default -> {
            }
        }

        List<Instruction.PinBinding> inputs = prepareInputs(node, type);

        List<Instruction.PinBinding> outputs = new ArrayList<>();
        for (NodeType.PinSpec spec : type.outputs()) {
            if (spec.kind() == PinKind.DATA) {
                outputs.add(new Instruction.PinBinding(spec.name(), slotFor(id, spec.name())));
            }
        }

        // Table mutable, remplie après l'émission des successeurs (normalisée par Ir).
        Map<String, Integer> execTargets = new LinkedHashMap<>();
        out.add(new Instruction.Call(node.typeId(), inputs, outputs, execTargets,
                type.fuelCost(), type.pure(), id));

        // Plusieurs cibles exec câblées = embranchement : les portées de purs divergent.
        int linkedTargets = 0;
        for (NodeType.PinSpec spec : type.outputs()) {
            if (spec.kind() == PinKind.EXEC && !from(id, spec.name()).isEmpty()) {
                linkedTargets++;
            }
        }
        boolean branching = linkedTargets > 1;
        for (NodeType.PinSpec spec : type.outputs()) {
            if (spec.kind() != PinKind.EXEC) {
                continue;
            }
            List<Link> links = from(id, spec.name());
            if (links.isEmpty()) {
                execTargets.put(spec.name(), -1);
                continue;
            }
            if (branching) {
                pureScopes.push(new HashSet<>(pureScopes.element()));
            }
            execTargets.put(spec.name(), emitNode(links.get(0).toNode(), links.get(0).toPin()));
            if (branching) {
                pureScopes.pop();
            }
        }
        return startIndex;
    }

    /** Prépare les entrées DATA : purs émis d'abord (une fois), littéraux en Const dédupliqués. */
    private List<Instruction.PinBinding> prepareInputs(Node node, NodeType type) {
        List<Instruction.PinBinding> inputs = new ArrayList<>();
        for (NodeType.PinSpec spec : type.inputs()) {
            if (spec.kind() != PinKind.DATA) {
                continue;
            }
            Instruction.PinBinding binding = prepareInput(node, spec);
            if (binding != null) {
                inputs.add(binding);
            }
        }
        return inputs;
    }

    /** Prépare une entrée DATA (pur amont ou littéral), ou null si rien à lier. */
    private Instruction.@Nullable PinBinding prepareInput(Node node, NodeType.PinSpec spec) {
        List<Link> incoming = into(node.uuid(), spec.name());
        if (!incoming.isEmpty()) {
            Link link = incoming.get(0);
            Node producer = node(link.fromNode());
            NodeType producerType = registry.get(producer.typeId()).orElseThrow();
            if (producerType.pure() && !pureScopes.element().contains(producer.uuid())) {
                emitPure(producer, producerType);
            }
            return new Instruction.PinBinding(spec.name(),
                    slotFor(link.fromNode(), link.fromPin()));
        }
        LiteralValue literal = node.literal(spec.name());
        if (literal == null) {
            literal = spec.defaultValue();
        }
        if (literal == null) {
            // Défaut du type appliqué à l'exécution par le contexte (int → 0…) ;
            // sans défaut du tout, la validation a déjà refusé (REQUIRED_PIN_UNLINKED).
            return null;
        }
        String key = node.uuid() + "/" + spec.name();
        int slot = slotFor(node.uuid(), "literal:" + spec.name());
        if (constEmitted.add(key)) {
            out.add(new Instruction.Const(slot, literal, node.uuid()));
        }
        return new Instruction.PinBinding(spec.name(), slot);
    }

    /** Un pur s'émet comme un Call sans cibles : il enchaîne linéairement (pure = true). */
    private void emitPure(Node node, NodeType type) {
        pureScopes.element().add(node.uuid());
        // var/get s'abaisse en LoadVar (story 5.5) — pas de Call, pas d'action.
        if (VarNodes.GET.equals(node.typeId())) {
            Variable variable = VarNodes.boundVariable(bp, node);   // garanti par la validation
            out.add(new Instruction.LoadVar(variable.scope(), variable.name(),
                    slotFor(node.uuid(), "value"), node.uuid()));
            return;
        }
        List<Instruction.PinBinding> inputs = prepareInputs(node, type);
        List<Instruction.PinBinding> outputs = new ArrayList<>();
        for (NodeType.PinSpec spec : type.outputs()) {
            outputs.add(new Instruction.PinBinding(spec.name(), slotFor(node.uuid(), spec.name())));
        }
        out.add(new Instruction.Call(node.typeId(), inputs, outputs, new LinkedHashMap<>(),
                type.fuelCost(), true, node.uuid()));
    }

    /**
     * var/set : prépare la valeur, émet {@code StoreVar}, puis enchaîne sur le
     * successeur exec — retour si le fil s'arrête, {@code Jmp} si c'est une arête
     * arrière (successeur déjà émis).
     */
    private void emitVarSet(Node node, NodeType type) {
        NodeType.PinSpec valueSpec = null;
        for (NodeType.PinSpec spec : type.inputs()) {
            if (spec.name().equals("value")) {
                valueSpec = spec;
            }
        }
        Instruction.PinBinding value = prepareInput(node, valueSpec);
        Variable variable = VarNodes.boundVariable(bp, node);   // garanti par la validation
        int slot = value != null ? value.slot() : slotFor(node.uuid(), "literal:value");
        out.add(new Instruction.StoreVar(variable.scope(), variable.name(), slot, node.uuid()));

        // Fin de fil : Jmp(-1) et pas Return — dans une sous-chaîne (7.1b), la fin
        // pendante doit dépiler la frame, pas terminer toute l'exécution.
        emitNext(node, "exec_out");
    }

    private int slotFor(UUID node, String pin) {
        return slotOf.computeIfAbsent(key(node) + "/" + pin, k -> nextSlot++);
    }

    // -------------------------------------------------------------- fonctions (20.1)

    /**
     * Un appel de fonction : le corps est <b>déplié sur place</b>.
     *
     * <h2>Pourquoi déplier plutôt qu'appeler</h2>
     *
     * <p>La story prévoyait un {@code CallSub} avec une fenêtre de slots par appel. La
     * lecture du compilateur a montré qu'un dépliage coûte moins et rapporte plus :
     *
     * <ul>
     *   <li><b>Aucun changement du format d'exécution suspendue.</b> {@code ExecutionNbt}
     *       sérialise les cadres en {@code int[]} — des adresses de retour. Une fenêtre de
     *       slots aurait changé ce format, donc obligé à abandonner les exécutions
     *       suspendues écrites par la version d'avant. Ici, un {@code wait} dans un corps
     *       est un {@code wait} comme un autre.</li>
     *   <li><b>La mémoïsation des purs redevient juste.</b> Elle est faite par position ;
     *       deux dépliages sont deux positions, donc deux calculs. Avec un appel partagé,
     *       le second aurait relu le résultat du premier.</li>
     *   <li><b>{@code do_once} compte par site d'appel</b>, et non une fois pour le graphe :
     *       son drapeau est nommé d'après le nœud, et le préfixe de dépliage entre dans ce
     *       nom. C'est plus proche de ce qu'un auteur attend.</li>
     *   <li><b>Le carburant est exact sans rien ajouter</b> : le corps déplié coûte ce
     *       qu'il exécute, comme s'il était posé en ligne — ce qu'il est.</li>
     * </ul>
     *
     * <p>Ce que ça coûte, et qu'il faut savoir : l'IR grossit du corps à <b>chaque</b>
     * appel. Le plafond de nœuds borne l'ensemble, et la récursion est refusée — sans quoi
     * le dépliage ne terminerait pas. Et le profileur voit les nœuds d'un corps une fois
     * par UUID, tous sites confondus : c'est la situation des boucles abaissées, déjà
     * documentée.
     */
    private void emitCall(Node call) {
        var function = fr.blueprint.core.graph.FuncNodes.boundFunction(bp, call);
        var entry = function == null ? null
                : bodyNode(function, fr.blueprint.core.graph.FuncNodes.PARAM);
        if (function == null || entry == null) {
            // La validation a déjà parlé, ou le corps est vide : on ne tombe pas ici, et
            // l'exécution enchaîne comme si l'appel n'avait rien fait.
            emitSuccessors(call);
            return;
        }
        // Les ARGUMENTS d'abord, dans la portée de l'APPELANT : ce sont ses valeurs.
        Map<String, Integer> arguments = new LinkedHashMap<>();
        for (var param : function.inputs()) {
            Integer slot = inputSlot(call, param.name());
            if (slot != null) {
                arguments.put(param.name(), slot);
            }
        }

        // Le préfixe rend ce dépliage unique. Sans lui, un second appel du même corps
        // sauterait au code du premier — un CallSub sans retour — et les deux
        // partageraient leurs slots.
        String prefix = key(call.uuid()) + "|";
        Scope body = new Scope(function.nodes(), indexOf(function, true),
                indexOf(function, false), prefix);
        scopes.push(body);
        pureScopes.push(new HashSet<>());

        // Les paramètres du corps LISENT les slots de l'appelant. Aucune copie, donc
        // aucune instruction : c'est tout l'intérêt d'un dépliage.
        arguments.forEach((pin, slot) -> slotOf.put(prefix + entry.uuid() + "/" + pin, slot));
        for (Link link : from(entry.uuid(), fr.blueprint.core.graph.BlueprintFunction.EXEC_OUT)) {
            emitNode(link.toNode(), link.toPin());
        }

        // Les résultats, résolus dans la portée du corps.
        Map<String, Integer> results = new LinkedHashMap<>();
        var exit = bodyNode(function, fr.blueprint.core.graph.FuncNodes.RESULT);
        if (exit != null) {
            for (var param : function.outputs()) {
                Integer slot = inputSlot(exit, param.name());
                if (slot != null) {
                    results.put(param.name(), slot);
                }
            }
        }
        pureScopes.pop();
        scopes.pop();

        // Et l'appelant lit CES slots-là. Un alias, pas une copie : ajouter un Move aurait
        // coûté une instruction par sortie et par appel pour recopier une valeur qui ne
        // bouge plus.
        results.forEach((pin, slot) -> slotOf.put(key(call.uuid()) + "/" + pin, slot));
        emitSuccessors(call);
    }

    private void emitSuccessors(Node call) {
        for (Link link : from(call.uuid(), fr.blueprint.core.graph.BlueprintFunction.EXEC_OUT)) {
            emitNode(link.toNode(), link.toPin());
        }
    }

    /**
     * Le slot qui alimente un pin de données, sans passer par un {@code PinSpec}.
     *
     * <p>Les bords d'une fonction n'ont pas de type enregistré qui décrive leurs pins :
     * leur forme vient de la signature. C'est la seule différence avec
     * {@link #prepareInput(Node, NodeType.PinSpec)}, dont la logique est reprise à
     * l'identique — lien d'abord, littéral ensuite.
     */
    private @Nullable Integer inputSlot(Node node, String pin) {
        List<Link> incoming = into(node.uuid(), pin);
        if (!incoming.isEmpty()) {
            Link link = incoming.get(0);
            Node producer = node(link.fromNode());
            NodeType producerType = registry.get(producer.typeId()).orElse(null);
            if (producerType != null && producerType.pure()
                    && !pureScopes.element().contains(producer.uuid())) {
                emitPure(producer, producerType);
            }
            return slotFor(link.fromNode(), link.fromPin());
        }
        LiteralValue literal = node.literal(pin);
        if (literal == null) {
            return null;
        }
        String constKey = key(node.uuid()) + "/" + pin;
        int slot = slotFor(node.uuid(), "literal:" + pin);
        if (constEmitted.add(constKey)) {
            out.add(new Instruction.Const(slot, literal, node.uuid()));
        }
        return slot;
    }

    private @Nullable Node bodyNode(fr.blueprint.core.graph.BlueprintFunction function,
                                    Identifier typeId) {
        for (Node node : function.nodes().values()) {
            if (node.typeId().equals(typeId)) {
                return node;
            }
        }
        return null;
    }

    /** L'index des liens d'un corps, construit à la demande — un corps est petit. */
    private Map<String, List<Link>> indexOf(fr.blueprint.core.graph.BlueprintFunction function,
                                            boolean outgoing) {
        Map<String, List<Link>> index = new HashMap<>();
        for (Link link : function.links()) {
            String key = outgoing ? link.fromNode() + "/" + link.fromPin()
                    : link.toNode() + "/" + link.toPin();
            index.computeIfAbsent(key, k -> new ArrayList<>()).add(link);
        }
        return index;
    }

    // ------------------------------------------------------ flux structuré (7.1b)

    private static final Identifier MATH_ADD =
            Identifier.fromNamespaceAndPath("blueprint", "math/add");
    private static final Identifier LOGIC_LESS_EQ =
            Identifier.fromNamespaceAndPath("blueprint", "logic/less_eq");
    private static final Identifier LOGIC_LESS =
            Identifier.fromNamespaceAndPath("blueprint", "logic/less");
    private static final Identifier LIST_SIZE =
            Identifier.fromNamespaceAndPath("blueprint", "list/size");
    private static final Identifier LIST_GET =
            Identifier.fromNamespaceAndPath("blueprint", "list/get");

    /** Un Call pur synthétisé (add, less_eq…) : le compilateur fabrique ses instructions arithmétiques avec la bibliothèque standard. */
    private static Instruction.Call synth(Identifier type, UUID source,
                                          Map<String, Integer> in, String outPin, int outSlot) {
        List<Instruction.PinBinding> inputs = new ArrayList<>();
        in.forEach((pin, slot) -> inputs.add(new Instruction.PinBinding(pin, slot)));
        return new Instruction.Call(type, inputs,
                List.of(new Instruction.PinBinding(outPin, outSlot)),
                new LinkedHashMap<>(), 1, true, source);
    }

    /** Successeur exec : fall-through s'il est neuf, Jmp s'il existe, fin sinon. */
    private void emitNext(Node node, String pin) {
        List<Link> next = from(node.uuid(), pin);
        if (next.isEmpty()) {
            out.add(new Instruction.Jmp(-1, node.uuid())); // pendante → frame-pop ou fin
            return;
        }
        int before = out.size();
        int target = emitNode(next.get(0).toNode(), next.get(0).toPin());
        if (target != before) {
            out.add(new Instruction.Jmp(target, node.uuid()));
        }
    }

    /** sequence : chaque branche câblée part en sous-chaîne (CallSub) puis revient. */
    private void emitSequence(Node node, NodeType type) {
        UUID id = node.uuid();
        List<Link> branches = new ArrayList<>();
        for (NodeType.PinSpec spec : type.outputs()) {
            if (spec.kind() == PinKind.EXEC && !from(id, spec.name()).isEmpty()) {
                branches.add(from(id, spec.name()).get(0));
            }
        }
        int firstPlaceholder = out.size();
        for (int i = 0; i < branches.size(); i++) {
            out.add(new Instruction.CallSub(-1, id));
        }
        out.add(new Instruction.Jmp(-1, id));
        for (int i = 0; i < branches.size(); i++) {
            int target = emitNode(branches.get(i).toNode(), branches.get(i).toPin());
            out.set(firstPlaceholder + i, new Instruction.CallSub(target, id));
        }
    }

    /** do_once : drapeau caché en variable GRAPH — persiste comme le reste du VarStore. */
    private void emitDoOnce(Node node) {
        UUID id = node.uuid();
        int flag = slotFor(id, "__once");
        out.add(new Instruction.LoadVar(fr.blueprint.core.graph.VarScope.GRAPH,
                "__once:" + id, flag, id));
        int jmpIf = out.size();
        out.add(new Instruction.JmpIf(flag, -1, id)); // vrai → déjà fait (pc+1)
        out.add(new Instruction.Jmp(-1, id));
        int doPath = out.size();
        out.set(jmpIf, new Instruction.JmpIf(flag, doPath, id));
        int truth = slotFor(id, "__true");
        out.add(new Instruction.Const(truth,
                LiteralValue.of(fr.blueprint.api.pin.PinTypes.BOOL, true), id));
        out.add(new Instruction.StoreVar(fr.blueprint.core.graph.VarScope.GRAPH,
                "__once:" + id, truth, id));
        emitNext(node, "exec_out");
    }

    /**
     * Prépare une entrée de CONDITION DE BOUCLE dans une portée de purs fraîche :
     * un pur déjà mémoïsé hors de la boucle serait sinon lu depuis un slot figé et
     * la condition ne changerait jamais (QA 7.1b, famille VM-COMP-001). Les purs se
     * ré-émettent DANS la plage rejouée par l'arête arrière.
     */
    private Instruction.@Nullable PinBinding prepareLoopCondition(Node node, NodeType.PinSpec spec) {
        pureScopes.push(new HashSet<>());
        try {
            return prepareInput(node, spec);
        } finally {
            pureScopes.pop();
        }
    }

    /** while : [start: prep cond][JmpIf→completed][CallSub corps][Jmp start]. */
    private void emitWhile(Node node, NodeType type, int startIndex) {
        UUID id = node.uuid();
        Instruction.PinBinding cond = prepareLoopCondition(node, specOf(type, "condition"));
        int condSlot = cond != null ? cond.slot() : slotFor(id, "literal:condition");
        int jmpIf = out.size();
        out.add(new Instruction.JmpIf(condSlot, -1, id));
        int callSub = -1;
        if (!from(id, "body").isEmpty()) {
            callSub = out.size();
            out.add(new Instruction.CallSub(-1, id));
        }
        out.add(new Instruction.Jmp(startIndex, id)); // re-préparer et retester
        int completed = out.size();
        out.set(jmpIf, new Instruction.JmpIf(condSlot, completed, id));
        emitNext(node, "completed");
        if (callSub >= 0) {
            out.set(callSub, new Instruction.CallSub(
                    emitNode(from(id, "body").get(0).toNode(), from(id, "body").get(0).toPin()), id));
        }
    }

    /** for : compteur en slot, init/comparaison/incrément = Calls synthétisés purs. */
    private void emitFor(Node node, NodeType type) {
        UUID id = node.uuid();
        Instruction.PinBinding first = prepareInput(node, specOf(type, "first"));
        Instruction.PinBinding last = prepareInput(node, specOf(type, "last"));
        int firstSlot = first != null ? first.slot() : slotFor(id, "literal:first");
        int lastSlot = last != null ? last.slot() : slotFor(id, "literal:last");
        int index = slotFor(id, "index");
        int zero = slotFor(id, "__zero");
        out.add(new Instruction.Const(zero,
                LiteralValue.of(fr.blueprint.api.pin.PinTypes.DOUBLE, 0.0), id));
        out.add(synth(MATH_ADD, id, Map.of("a", firstSlot, "b", zero), "result", index));
        int loop = out.size();
        int cmp = slotFor(id, "__cmp");
        out.add(synth(LOGIC_LESS_EQ, id, Map.of("a", index, "b", lastSlot), "result", cmp));
        int jmpIf = out.size();
        out.add(new Instruction.JmpIf(cmp, -1, id));
        int callSub = -1;
        if (!from(id, "body").isEmpty()) {
            callSub = out.size();
            out.add(new Instruction.CallSub(-1, id));
        }
        int one = slotFor(id, "__one");
        out.add(new Instruction.Const(one,
                LiteralValue.of(fr.blueprint.api.pin.PinTypes.DOUBLE, 1.0), id));
        out.add(synth(MATH_ADD, id, Map.of("a", index, "b", one), "result", index));
        out.add(new Instruction.Jmp(loop, id));
        int completed = out.size();
        out.set(jmpIf, new Instruction.JmpIf(cmp, completed, id));
        emitNext(node, "completed");
        if (callSub >= 0) {
            out.set(callSub, new Instruction.CallSub(
                    emitNode(from(id, "body").get(0).toNode(), from(id, "body").get(0).toPin()), id));
        }
    }

    /**
     * for_each : [taille][index=0][boucle: index<taille ?][élément=get(liste,index)]
     * [CallSub corps][index+1][Jmp boucle] puis « completed ».
     *
     * <p>La taille est calculée UNE fois, avant la boucle : recalculer à chaque tour
     * coûterait un appel de plus par élément, et la liste ne peut pas changer sous nos
     * pieds — les nœuds de liste sont purs et immuables (7.8).
     */
    private void emitForEach(Node node, NodeType type) {
        UUID id = node.uuid();
        Instruction.PinBinding list = prepareInput(node, specOf(type, "list"));
        int listSlot = list != null ? list.slot() : slotFor(id, "literal:list");
        int size = slotFor(id, "__size");
        out.add(synth(LIST_SIZE, id, Map.of("list", listSlot), "size", size));

        int index = slotFor(id, "index");
        out.add(new Instruction.Const(index,
                LiteralValue.of(fr.blueprint.api.pin.PinTypes.INT, 0), id));

        int loop = out.size();
        int cmp = slotFor(id, "__cmp");
        out.add(synth(LOGIC_LESS, id, Map.of("a", index, "b", size), "result", cmp));
        int jmpIf = out.size();
        out.add(new Instruction.JmpIf(cmp, -1, id));

        int element = slotFor(id, "element");
        out.add(synth(LIST_GET, id, Map.of("list", listSlot, "index", index), "element", element));
        int callSub = -1;
        if (!from(id, "body").isEmpty()) {
            callSub = out.size();
            out.add(new Instruction.CallSub(-1, id));
        }
        int one = slotFor(id, "__one");
        out.add(new Instruction.Const(one,
                LiteralValue.of(fr.blueprint.api.pin.PinTypes.INT, 1), id));
        out.add(synth(MATH_ADD, id, Map.of("a", index, "b", one), "result", index));
        out.add(new Instruction.Jmp(loop, id));

        int completed = out.size();
        out.set(jmpIf, new Instruction.JmpIf(cmp, completed, id));
        emitNext(node, "completed");
        if (callSub >= 0) {
            var body = from(id, "body").get(0);
            out.set(callSub, new Instruction.CallSub(emitNode(body.toNode(), body.toPin()), id));
        }
    }

    /**
     * gate : un drapeau en variable GRAPH, comme do_once — mais lu ou écrit selon le
     * pin d'ENTRÉE. Fermé tant qu'on ne l'a pas ouvert : un état non initialisé vaut
     * « faux », et un portail qu'on n'a jamais ouvert doit bloquer, pas laisser passer.
     */
    private void emitGate(Node node, @Nullable String entryPin) {
        UUID id = node.uuid();
        String variable = "__gate:" + id;
        String entry = entryPin == null ? "enter" : entryPin;
        if ("open".equals(entry) || "close".equals(entry)) {
            int value = slotFor(id, "__set_" + entry);
            out.add(new Instruction.Const(value, LiteralValue.of(
                    fr.blueprint.api.pin.PinTypes.BOOL, "open".equals(entry)), id));
            out.add(new Instruction.StoreVar(fr.blueprint.core.graph.VarScope.GRAPH,
                    variable, value, id));
            // Ouvrir ou fermer ne traverse PAS : dans Unreal non plus, ce sont des
            // ordres, pas un passage.
            out.add(new Instruction.Jmp(-1, id));
            return;
        }
        int flag = slotFor(id, "__gate");
        out.add(new Instruction.LoadVar(fr.blueprint.core.graph.VarScope.GRAPH,
                variable, flag, id));
        // JmpIf saute vers elseTarget quand la condition est FAUSSE : le pc+1 est donc
        // le chemin « ouvert », et la cible le chemin « fermé ».
        int jmpIf = out.size();
        out.add(new Instruction.JmpIf(flag, -1, id));
        emitNext(node, "exit");                         // ouvert → on traverse
        int closed = out.size();
        out.set(jmpIf, new Instruction.JmpIf(flag, closed, id));
        out.add(new Instruction.Jmp(-1, id));           // fermé → fin de chaîne
    }

    /** wait_until : re-teste la condition chaque tick (Yield 1 + boucle). */
    private void emitWaitUntil(Node node, NodeType type, int startIndex) {
        UUID id = node.uuid();
        Instruction.PinBinding cond = prepareLoopCondition(node, specOf(type, "condition"));
        int condSlot = cond != null ? cond.slot() : slotFor(id, "literal:condition");
        int jmpIf = out.size();
        out.add(new Instruction.JmpIf(condSlot, -1, id)); // vrai → successeur (pc+1)
        emitNext(node, "exec_out");
        int waitPath = out.size();
        out.set(jmpIf, new Instruction.JmpIf(condSlot, waitPath, id));
        out.add(new Instruction.Yield(1, id));
        out.add(new Instruction.Jmp(startIndex, id));
    }

    private static NodeType.PinSpec specOf(NodeType type, String pin) {
        for (NodeType.PinSpec spec : type.inputs()) {
            if (spec.name().equals(pin)) {
                return spec;
            }
        }
        throw new IllegalStateException("pin manquant : " + pin);
    }
}
