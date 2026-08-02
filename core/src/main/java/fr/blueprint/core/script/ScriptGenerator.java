package fr.blueprint.core.script;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.ParameterizedPinType;
import fr.blueprint.api.pin.PinKind;
import fr.blueprint.api.pin.PinType;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.CommentBox;
import fr.blueprint.core.graph.GhostNode;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.Node;
import fr.blueprint.core.graph.NodeShape;
import fr.blueprint.core.graph.NodeTypeLookup;
import fr.blueprint.core.graph.Variable;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Génération BScript v1 (story 4.2) : sortie <b>déterministe</b> (même graphe → mêmes
 * octets), fidèle grâce aux annotations {@code @id}/{@code @pos}. Les purs sont inlinés
 * en expressions (les partagés se dédupliquent par {@code @id} au parse), les
 * embranchements deviennent des blocs par pin, les fusions et boucles des
 * {@code label}/{@code goto} (repli spec §6).
 */
public final class ScriptGenerator {

    /** Texte + points non émissibles (orphelins, config non vide, littéral exotique). */
    public record Result(String text, List<String> issues) {
    }

    private final Blueprint bp;
    private final NodeTypeLookup lookup;
    private final StringBuilder out = new StringBuilder(512);
    private final List<String> issues = new ArrayList<>();
    private final Set<UUID> emitted = new HashSet<>();
    private final Set<UUID> inlining = new HashSet<>();
    private final Map<UUID, String> labelNames = new HashMap<>();
    private final Map<UUID, NodeShape> shapes = new HashMap<>();
    private UUID currentEvent;
    private int indent;

    private ScriptGenerator(Blueprint bp, NodeTypeLookup lookup) {
        this.bp = bp;
        this.lookup = lookup;
        for (Node node : bp.nodes().values()) {
            NodeShape shape = lookup.shape(node.typeId());
            shapes.put(node.uuid(), shape != null ? shape : GhostNode.deduceShape(bp, lookup, node));
        }
        // Noms séquentiels dans l'ordre (déterministe) de la pré-passe : un préfixe
        // d'UUID tronqué pouvait entrer en collision et recâbler un goto en silence.
        int next = 1;
        for (UUID uuid : computeLabels()) {
            labelNames.put(uuid, "l_" + next++);
        }
    }

    public static Result generate(Blueprint bp, NodeTypeLookup lookup) {
        return new ScriptGenerator(bp, lookup).run();
    }

    private Result run() {
        line("blueprint " + bp.id() + " {");
        indent++;
        emitMeta();
        emitVariables();
        emitNotes();
        for (Node event : sortedEvents()) {
            emitEvent(event);
        }
        indent--;
        line("}");
        if (bp.hasPreservedVariables()) {
            issues.add("variables préservées (P4) non émissibles en texte");
        }
        for (Node node : bp.nodes().values()) {
            if (node.hasPreservedLiterals()) {
                issues.add("littéraux préservés (P4) non émissibles en texte : " + node.uuid());
            }
            if (!emitted.contains(node.uuid()) && !shapes.get(node.uuid()).entryPoint()) {
                issues.add("nœud orphelin non émis : " + node.uuid() + " (" + node.typeId() + ")");
            }
        }
        return new Result(out.toString(), List.copyOf(issues));
    }

    // ------------------------------------------------------------------ sections

    private void emitMeta() {
        line("meta {");
        indent++;
        line("author " + quote(bp.meta().author()));
        line("description " + quote(bp.meta().description()));
        line("version " + quote(bp.meta().version()));
        line("permission " + bp.meta().permissionCap().name());
        indent--;
        line("}");
    }

    private void emitVariables() {
        bp.variables().values().stream()
                .sorted(Comparator.comparing(Variable::name))
                .forEach(variable -> {
                    StringBuilder sb = new StringBuilder("var ");
                    sb.append(renderType(variable.type())).append(' ').append(variable.name());
                    if (variable.defaultValue() != null) {
                        sb.append(" = ").append(renderLiteral(variable.defaultValue()));
                    }
                    sb.append(" @").append(variable.scope().name().toLowerCase(java.util.Locale.ROOT));
                    if (variable.replicated()) {
                        sb.append(" @replicated");
                    }
                    line(sb.toString());
                });
    }

    private void emitNotes() {
        bp.comments().stream()
                .sorted(Comparator.comparing(CommentBox::uuid))
                .forEach(note -> line("note " + quote(note.text())
                        + " @id(" + quote(note.uuid().toString()) + ")"
                        + " @pos(" + num(note.position().x()) + ", " + num(note.position().y()) + ")"
                        + " @size(" + num(note.size().x()) + ", " + num(note.size().y()) + ")"
                        + " @color(" + quote(String.format("#%08X", note.color())) + ")"));
    }

    private List<Node> sortedEvents() {
        return bp.nodes().values().stream()
                .filter(node -> shapes.get(node.uuid()).entryPoint())
                .sorted(Comparator.comparing(Node::uuid))
                .toList();
    }

    private void emitEvent(Node event) {
        NodeShape shape = shapes.get(event.uuid());
        List<String> outs = shape.outputs().stream()
                .filter(pin -> pin.kind() == PinKind.DATA)
                .map(NodeShape.PinDef::name).toList();
        emitted.add(event.uuid());
        currentEvent = event.uuid();
        line("on " + event.typeId() + "(" + String.join(", ", outs) + ")"
                + " @id(" + quote(event.uuid().toString()) + ")"
                + " @pos(" + num(event.position().x()) + ", " + num(event.position().y()) + ") {");
        indent++;
        for (NodeShape.PinDef pin : shape.outputs()) {
            if (pin.kind() == PinKind.EXEC) {
                List<Link> links = bp.linksFrom(event.uuid(), pin.name());
                if (!links.isEmpty()) {
                    emitChain(links.get(0).toNode());
                }
            }
        }
        indent--;
        line("}");
        currentEvent = null;
    }

    // ------------------------------------------------------------------ chaînes

    /** Pré-passe : les cibles atteintes une seconde fois (fusions, boucles) reçoivent une étiquette. */
    private Set<UUID> computeLabels() {
        Set<UUID> labels = new LinkedHashSet<>();
        Set<UUID> visited = new HashSet<>();
        for (Node event : sortedEvents()) {
            visited.add(event.uuid());
            for (NodeShape.PinDef pin : shapes.get(event.uuid()).outputs()) {
                if (pin.kind() == PinKind.EXEC) {
                    for (Link link : bp.linksFrom(event.uuid(), pin.name())) {
                        walk(link.toNode(), visited, labels);
                    }
                }
            }
        }
        return labels;
    }

    private void walk(UUID node, Set<UUID> visited, Set<UUID> labels) {
        if (!visited.add(node)) {
            labels.add(node);
            return;
        }
        for (NodeShape.PinDef pin : shapes.get(node).outputs()) {
            if (pin.kind() == PinKind.EXEC) {
                for (Link link : bp.linksFrom(node, pin.name())) {
                    walk(link.toNode(), visited, labels);
                }
            }
        }
    }

    private void emitChain(UUID uuid) {
        if (emitted.contains(uuid)) {
            line("goto " + labelNames.get(uuid));
            return;
        }
        emitted.add(uuid);
        if (labelNames.containsKey(uuid)) {
            line("label " + labelNames.get(uuid));
        }
        Node node = bp.node(uuid);
        NodeShape shape = shapes.get(uuid);
        if (!node.config().isEmpty()) {
            issues.add("config non vide non émise : " + uuid);
        }

        // Cibles exec câblées, dans l'ordre déclaré.
        List<NodeShape.PinDef> linkedOuts = shape.outputs().stream()
                .filter(pin -> pin.kind() == PinKind.EXEC
                        && !bp.linksFrom(uuid, pin.name()).isEmpty())
                .toList();
        // Linéaire seulement si le nœud n'a qu'UNE sortie exec déclarée : une branche
        // à sortie unique câblée doit rester un bloc « true: { … } », sinon la suite
        // se lirait comme inconditionnelle.
        long declaredExecOuts = shape.outputs().stream()
                .filter(pin -> pin.kind() == PinKind.EXEC).count();
        boolean firstIsLinear = declaredExecOuts == 1 && linkedOuts.size() == 1
                && firstExecOut(shape) != null
                && linkedOuts.get(0).name().equals(firstExecOut(shape).name());

        String statement = renderCall(node, shape)
                + " @id(" + quote(uuid.toString()) + ")"
                + " @pos(" + num(node.position().x()) + ", " + num(node.position().y()) + ")";
        if (linkedOuts.isEmpty() || firstIsLinear) {
            line(statement);
            if (firstIsLinear) {
                emitChain(bp.linksFrom(uuid, linkedOuts.get(0).name()).get(0).toNode());
            }
        } else {
            line(statement + " {");
            indent++;
            for (NodeShape.PinDef pin : linkedOuts) {
                line(pin.name() + ": {");
                indent++;
                emitChain(bp.linksFrom(uuid, pin.name()).get(0).toNode());
                indent--;
                line("}");
            }
            indent--;
            line("}");
        }
    }

    private static NodeShape.PinDef firstExecOut(NodeShape shape) {
        for (NodeShape.PinDef pin : shape.outputs()) {
            if (pin.kind() == PinKind.EXEC) {
                return pin;
            }
        }
        return null;
    }

    // -------------------------------------------------------------- expressions

    private String renderCall(Node node, NodeShape shape) {
        List<String> args = new ArrayList<>();
        Set<String> covered = new HashSet<>();
        for (NodeShape.PinDef pin : shape.inputs()) {
            if (pin.kind() != PinKind.DATA) {
                continue;
            }
            covered.add(pin.name());
            List<Link> incoming = bp.linksInto(node.uuid(), pin.name());
            // Un pin peut porter un littéral de repli DERRIÈRE un lien (AddLink ne
            // l'efface pas) : on émet les deux — le parseur applique dans l'ordre.
            if (node.literal(pin.name()) != null) {
                args.add(pin.name() + ": " + renderLiteral(node.literal(pin.name())));
            }
            if (!incoming.isEmpty()) {
                args.add(pin.name() + ": " + renderSource(incoming.get(0)));
            }
        }
        node.literals().keySet().stream().sorted().forEach(pin -> {
            if (!covered.contains(pin)) {
                args.add(pin + ": " + renderLiteral(node.literal(pin)));
            }
        });
        return node.typeId() + "(" + String.join(", ", args) + ")";
    }

    /** L'expression qui produit la valeur d'un lien de données. */
    private String renderSource(Link link) {
        UUID producer = link.fromNode();
        if (producer.equals(currentEvent)) {
            return "$" + link.fromPin();
        }
        NodeShape shape = shapes.get(producer);
        if (shape == null) {
            // Source absente du graphe (lien pendant, préservé par P4) : référence brute.
            return "$node(" + quote(producer.toString()) + ")." + link.fromPin();
        }
        boolean pure = shape.inputs().stream().noneMatch(p -> p.kind() == PinKind.EXEC)
                && shape.outputs().stream().noneMatch(p -> p.kind() == PinKind.EXEC)
                && !shape.entryPoint();
        long dataOuts = shape.outputs().stream().filter(p -> p.kind() == PinKind.DATA).count();
        // inlining : garde anti-cycle — un chargement forgé peut câbler des purs en
        // boucle (le chargement ne refuse rien, P4) ; on retombe alors sur $node.
        if (pure && dataOuts == 1 && lookup.shape(bp.node(producer).typeId()) != null
                && inlining.add(producer)) {
            try {
                Node pureNode = bp.node(producer);
                emitted.add(producer);   // inliné = émis (sinon faux « orphelin »)
                return renderCall(pureNode, shape) + " @id(" + quote(producer.toString()) + ")"
                        + " @pos(" + num(pureNode.position().x()) + ", " + num(pureNode.position().y()) + ")";
            } finally {
                inlining.remove(producer);
            }
        }
        return "$node(" + quote(producer.toString()) + ")." + link.fromPin();
    }

    private String renderType(PinType type) {
        if (type instanceof ParameterizedPinType p) {
            String container = p.container() == ParameterizedPinType.Container.LIST ? "list" : "map";
            List<String> args = p.args().stream().map(this::renderType).toList();
            return container + "<" + String.join(", ", args) + ">";
        }
        Identifier id = type.id();
        return id.getNamespace().equals("blueprint") ? id.getPath() : id.toString();
    }

    private String renderLiteral(LiteralValue literal) {
        return renderRaw(literal.value());
    }

    private String renderRaw(Object value) {
        if (value instanceof Boolean || value instanceof Integer || value instanceof Long) {
            return String.valueOf(value);
        }
        if (value instanceof Double d) {
            return Double.toString(d);
        }
        if (value instanceof String s) {
            return quote(s);
        }
        if (value instanceof Identifier id) {
            return quote(id.toString());
        }
        if (value instanceof List<?> list) {
            List<String> items = list.stream().map(this::renderRaw).toList();
            return "[" + String.join(", ", items) + "]";
        }
        issues.add("littéral non émissible (" + value.getClass().getSimpleName() + ")");
        return quote(String.valueOf(value));
    }

    // ------------------------------------------------------------------- util

    private void line(String text) {
        out.append("  ".repeat(indent)).append(text).append('\n');
    }

    private static String num(double value) {
        return value == Math.rint(value) && !Double.isInfinite(value)
                ? String.valueOf((long) value) : Double.toString(value);
    }

    static String quote(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + '"';
    }
}
