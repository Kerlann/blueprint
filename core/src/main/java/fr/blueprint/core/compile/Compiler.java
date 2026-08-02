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
import fr.blueprint.core.registry.NodeRegistryImpl;
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
                new Ir(bp.id(), bp.revision(), compiler.out, compiler.nextSlot),
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
            List<Link> incoming = into(node.uuid(), spec.name());
            if (!incoming.isEmpty()) {
                Link link = incoming.get(0);
                Node producer = bp.node(link.fromNode());
                NodeType producerType = registry.get(producer.typeId()).orElseThrow();
                if (producerType.pure() && !pureScopes.element().contains(producer.uuid())) {
                    emitPure(producer, producerType);
                }
                inputs.add(new Instruction.PinBinding(spec.name(),
                        slotFor(link.fromNode(), link.fromPin())));
            } else {
                LiteralValue literal = node.literal(spec.name());
                if (literal == null) {
                    literal = spec.defaultValue();
                }
                if (literal == null) {
                    // Défaut du type appliqué à l'exécution par le contexte (int → 0…) ;
                    // sans défaut du tout, la validation a déjà refusé (REQUIRED_PIN_UNLINKED).
                    continue;
                }
                String key = node.uuid() + "/" + spec.name();
                int slot = slotFor(node.uuid(), "literal:" + spec.name());
                if (constEmitted.add(key)) {
                    out.add(new Instruction.Const(slot, literal, node.uuid()));
                }
                inputs.add(new Instruction.PinBinding(spec.name(), slot));
            }
        }
        return inputs;
    }

    /** Un pur s'émet comme un Call sans cibles : il enchaîne linéairement (pure = true). */
    private void emitPure(Node node, NodeType type) {
        pureScopes.element().add(node.uuid());
        List<Instruction.PinBinding> inputs = prepareInputs(node, type);
        List<Instruction.PinBinding> outputs = new ArrayList<>();
        for (NodeType.PinSpec spec : type.outputs()) {
            outputs.add(new Instruction.PinBinding(spec.name(), slotFor(node.uuid(), spec.name())));
        }
        out.add(new Instruction.Call(node.typeId(), inputs, outputs, new LinkedHashMap<>(),
                type.fuelCost(), true, node.uuid()));
    }

    private int slotFor(UUID node, String pin) {
        return slotOf.computeIfAbsent(node + "/" + pin, k -> nextSlot++);
    }
}
