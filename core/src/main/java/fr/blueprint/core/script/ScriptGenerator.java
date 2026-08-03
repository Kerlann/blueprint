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
        emitScreens();
        emitNotes();
        for (Node event : sortedEvents()) {
            emitEvent(event);
        }
        indent--;
        line("}");
        if (bp.hasPreservedVariables()) {
            issues.add("variables préservées (P4) non émissibles en texte");
        }
        if (bp.hasPreservedScreens()) {
            issues.add("écrans préservés (P4) non émissibles en texte");
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

    /**
     * Les écrans (épic 10). L'ordre des éléments est <b>l'ordre de dessin</b> : il est
     * émis tel quel, jamais trié — contrairement aux variables et aux notes, où le tri
     * garantit le déterminisme. Ici, trier changerait la superposition.
     */
    private void emitScreens() {
        for (var screen : bp.screens().values()) {
            line("screen " + quote(screen.name()) + (screen.hud() ? " @hud" : "") + " {");
            indent++;
            if (!screen.styles().isEmpty()) {
                line("styles {");
                indent++;
                screen.styles().forEach((styleName, style) ->
                        line(quote(styleName) + " = " + renderStyle(style)));
                indent--;
                line("}");
            }
            for (var element : screen.elements().values()) {
                line(renderElement(element));
            }
            indent--;
            line("}");
        }
    }

    private String renderElement(fr.blueprint.core.graph.screen.ScreenElement element) {
        StringBuilder sb = new StringBuilder();
        sb.append(element.kind().name().toLowerCase(java.util.Locale.ROOT))
                .append(' ').append(quote(element.name()));
        if (element.parent() != null) {
            sb.append(" @in(").append(quote(element.parent())).append(')');
        }
        sb.append(" @at(").append(element.anchor().name().toLowerCase(java.util.Locale.ROOT))
                .append(", ").append(num(element.x())).append(", ").append(num(element.y()))
                .append(')');
        sb.append(" @size(").append(renderExtent(element.width()))
                .append(", ").append(renderExtent(element.height())).append(')');
        if (!element.text().isEmpty()) {
            sb.append(element.text().translate() ? " @key(" : " @text(")
                    .append(quote(element.text().value())).append(')');
        }
        if (element.texture() != null) {
            // L'écriture COURTE pour un pack (« ma_boutique/fond ») : c'est celle que
            // l'auteur reconnaît dans son dossier, et le parseur la relit à l'identique.
            sb.append(" @texture(")
                    .append(quote(fr.blueprint.core.graph.screen.PackRef
                            .reference(element.texture())))
                    .append(')');
        }
        if (!element.visible()) {
            sb.append(" @hidden");
        }
        if (!element.enabled()) {
            sb.append(" @disabled");
        }
        if (element.isBound()) {
            sb.append(" @bind(").append(renderBinding(element.binding())).append(')');
        }
        if (element.arranges()) {
            sb.append(" @layout(").append(renderLayout(element.layout())).append(')');
        }
        // Le style NOMMÉ et le style EN LIGNE sont émis tous les deux quand ils
        // coexistent. Le second est dormant tant que le premier existe — mais il
        // redevient le style de l'élément dès qu'on le détache, et l'écraser ici
        // ferait perdre à l'export ce que l'enregistrement, lui, conserve.
        if (element.followsNamedStyle()) {
            sb.append(" @uses(").append(quote(element.styleName())).append(')');
        }
        sb.append(" @style(").append(renderStyle(element.style())).append(')');
        return sb.toString();
    }

    /**
     * {@code 80} pour une taille fixe, {@code 50%[100, 300]} pour une relative bornée,
     * {@code fill} / {@code fill:2} pour une part de la place restante, {@code hug} pour
     * un conteneur qui s'ajuste à ses enfants.
     */
    private String renderExtent(fr.blueprint.core.graph.screen.Extent extent) {
        String head = switch (extent.mode()) {
            case FIXED -> num(extent.value());
            // BigDecimal, et pas value * 100 : en virgule flottante 0.07 * 100 vaut
            // 7.000000000000001, et le texte relu ne redonnerait plus la même fraction.
            // Passer par la décimale courte rend l'aller-retour exact dans les deux sens.
            case PERCENT -> java.math.BigDecimal.valueOf(extent.value())
                    .movePointRight(2).stripTrailingZeros().toPlainString() + "%";
            case FILL -> extent.value() == 1 ? "fill" : "fill:" + num(extent.value());
            case HUG -> "hug";
        };
        // Les bornes valent pour TOUS les modes, y compris fixe : elles sont conservées
        // à l'enregistrement, et les taire ici les perdrait au premier aller-retour.
        if (extent.min() > 0 || extent.max() > 0) {
            head += "[" + num(extent.min()) + ", " + num(extent.max()) + "]";
        }
        return head;
    }

    /** {@code "argent", text, format: "Or : %s"} — seuls les écarts au défaut sont écrits. */
    private String renderBinding(fr.blueprint.core.graph.screen.ElementBinding binding) {
        StringBuilder sb = new StringBuilder(quote(binding.variable()));
        sb.append(", ").append(lower(binding.target().name()));
        if (!binding.format().equals(fr.blueprint.core.graph.screen.ElementBinding.PLACEHOLDER)) {
            sb.append(", format: ").append(quote(binding.format()));
        }
        if (binding.decimals() != 0) {
            sb.append(", decimals: ").append(binding.decimals());
        }
        if (binding.min() != 0) {
            sb.append(", min: ").append(num(binding.min()));
        }
        if (binding.max() != 1) {
            sb.append(", max: ").append(num(binding.max()));
        }
        return sb.toString();
    }

    /** {@code column, gap: 4, cross: stretch} — seul ce qui s'écarte du défaut est écrit. */
    private String renderLayout(fr.blueprint.core.graph.screen.LayoutSpec layout) {
        StringBuilder sb = new StringBuilder(lower(layout.mode().name()));
        if (layout.gap() != 0) {
            sb.append(", gap: ").append(num(layout.gap()));
        }
        if (layout.crossGap() != 0) {
            sb.append(", crossGap: ").append(num(layout.crossGap()));
        }
        if (layout.columns() != 1) {
            sb.append(", columns: ").append(layout.columns());
        }
        if (layout.main() != fr.blueprint.core.graph.screen.LayoutSpec.Distribute.START) {
            sb.append(", main: ").append(lower(layout.main().name()));
        }
        if (layout.cross() != fr.blueprint.core.graph.screen.LayoutSpec.Cross.START) {
            sb.append(", cross: ").append(lower(layout.cross().name()));
        }
        return sb.toString();
    }

    private static String lower(String name) {
        return name.toLowerCase(java.util.Locale.ROOT);
    }

    private String renderStyle(fr.blueprint.core.graph.screen.ElementStyle style) {
        return String.join(", ",
                hex(style.background()), hex(style.border()), num(style.borderWidth()),
                hex(style.textColor()), hex(style.hoverBackground()),
                hex(style.pressedBackground()), hex(style.disabledBackground()),
                num(style.padding()),
                style.align().name().toLowerCase(java.util.Locale.ROOT));
    }

    private static String hex(int argb) {
        return "\"#" + String.format("%08X", argb) + "\"";
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

    /**
     * Les littéraux d'ENTRÉE d'un nœud d'événement, en {@code @with(pin: valeur)}.
     *
     * <p>Trois nœuds d'événement en portent : {@code command}, {@code signal} et
     * {@code gui_clicked}. Le littéral y est le <b>filtre</b> — le nom de la commande,
     * du signal, de l'élément écouté. Sans lui à l'export, un {@code .bp} réimporté
     * n'écoutait plus rien : le graphe se rechargeait entier, se validait, s'affichait
     * normalement, et ne se déclenchait jamais. La panne la plus silencieuse possible.
     */
    private String eventLiterals(Node event, NodeShape shape) {
        List<String> args = new ArrayList<>();
        for (NodeShape.PinDef pin : shape.inputs()) {
            if (pin.kind() == PinKind.DATA && event.literal(pin.name()) != null) {
                args.add(pin.name() + ": " + renderLiteral(event.literal(pin.name())));
            }
        }
        return args.isEmpty() ? "" : " @with(" + String.join(", ", args) + ")";
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
                + " @pos(" + num(event.position().x()) + ", " + num(event.position().y()) + ")"
                + eventLiterals(event, shape) + " {");
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
