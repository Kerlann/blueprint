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
    private final Map<UUID, Integer> emittedAt = new HashMap<>();
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

    private Compiler(Blueprint bp, NodeRegistryImpl registry) {
        this.bp = bp;
        this.registry = registry;
        this.pureScopes.push(new HashSet<>());
        for (Link link : bp.links()) {
            linksFromPin.computeIfAbsent(link.fromNode() + "/" + link.fromPin(), k -> new ArrayList<>()).add(link);
            linksIntoPin.computeIfAbsent(link.toNode() + "/" + link.toPin(), k -> new ArrayList<>()).add(link);
        }
    }

    private List<Link> from(UUID node, String pin) {
        return linksFromPin.getOrDefault(node + "/" + pin, List.of());
    }

    private List<Link> into(UUID node, String pin) {
        return linksIntoPin.getOrDefault(node + "/" + pin, List.of());
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
        Integer existing = emittedAt.get(id);
        if (existing != null) {
            return existing;
        }
        // L'index de début est réservé AVANT la préparation : une arête arrière (boucle)
        // revient sur les Const/purs du nœud, qui se ré-exécutent à chaque itération.
        int startIndex = out.size();
        emittedAt.put(id, startIndex);

        Node node = bp.node(id);
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
            execTargets.put(spec.name(), emitNode(links.get(0).toNode()));
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
            Node producer = bp.node(link.fromNode());
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
        return slotOf.computeIfAbsent(node + "/" + pin, k -> nextSlot++);
    }

    // ------------------------------------------------------ flux structuré (7.1b)

    private static final Identifier MATH_ADD =
            Identifier.fromNamespaceAndPath("blueprint", "math/add");
    private static final Identifier LOGIC_LESS_EQ =
            Identifier.fromNamespaceAndPath("blueprint", "logic/less_eq");

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
        int target = emitNode(next.get(0).toNode());
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
            int target = emitNode(branches.get(i).toNode());
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
                    emitNode(from(id, "body").get(0).toNode()), id));
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
                    emitNode(from(id, "body").get(0).toNode()), id));
        }
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
