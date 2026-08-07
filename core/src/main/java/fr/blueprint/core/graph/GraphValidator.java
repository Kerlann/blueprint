package fr.blueprint.core.graph;

import fr.blueprint.api.pin.GenericResolution;
import fr.blueprint.api.pin.ParameterizedPinType;
import fr.blueprint.api.pin.PinKind;
import fr.blueprint.api.pin.PinType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Validation structurelle d'un blueprint. Retourne des {@link Diagnostic} typés,
 * jamais d'exception : un graphe invalide est une erreur d'utilisateur, pas de
 * programme. {@link #canLink} est LA source de vérité du câblage — l'éditeur et les
 * opérations d'édition passent par elle, aucune divergence possible.
 */
public final class GraphValidator {

    private GraphValidator() {
    }

    /** Résultat : diagnostics + exécutabilité (aucune erreur, aucun fantôme). */
    public record ValidationResult(List<Diagnostic> diagnostics, boolean executable) {
        public List<Diagnostic> errors() {
            return diagnostics.stream().filter(d -> d.severity() == Diagnostic.Severity.ERROR).toList();
        }
    }

    public static ValidationResult validate(Blueprint bp, NodeTypeLookup lookup) {
        return validate(bp, lookup, GraphLimits.DEFAULT);
    }

    public static ValidationResult validate(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
        List<Diagnostic> out = new ArrayList<>();

        // Corps de fonctions COMPRIS (20.1) : c'est le blueprint entier qui est borné.
        int totalNodes = FunctionOps.totalNodes(bp);
        if (totalNodes > limits.maxNodes()) {
            out.add(Diagnostic.error(DiagnosticCode.NODE_LIMIT_EXCEEDED, Diagnostic.graph(),
                    totalNodes, limits.maxNodes()));
        }

        // Fantômes et permissions, nœud par nœud.
        boolean anyEntryPoint = false;
        Map<UUID, NodeShape> shapes = new HashMap<>();
        Set<UUID> ghosts = new HashSet<>();
        for (Node node : bp.nodes().values()) {
            NodeShape shape = lookup.shape(bp, node);
            if (shape == null) {
                ghosts.add(node.uuid());
                shapes.put(node.uuid(), GhostNode.deduceShape(bp, lookup, node));
                out.add(Diagnostic.error(DiagnosticCode.UNKNOWN_NODE_TYPE, Diagnostic.node(node.uuid()),
                        node.typeId().toString(), node.typeId().getNamespace()));
                continue;
            }
            shapes.put(node.uuid(), shape);
            anyEntryPoint |= shape.entryPoint();
            if (!shape.permission().allowedUnder(bp.meta().permissionCap())) {
                out.add(Diagnostic.error(DiagnosticCode.PERMISSION_EXCEEDED, Diagnostic.node(node.uuid()),
                        shape.permission().name(), bp.meta().permissionCap().name()));
            }
            // Littéraux : contrôle profond (TYPE-001) + pins requis.
            node.literals().forEach((pin, literal) -> {
                NodeShape.PinDef def = shape.input(pin);
                if (def == null) {
                    out.add(Diagnostic.error(DiagnosticCode.PIN_NOT_FOUND, Diagnostic.node(node.uuid()), pin));
                } else if (!def.type().isAssignableFrom(literal.type()) || !Literals.matches(literal)) {
                    out.add(Diagnostic.error(DiagnosticCode.TYPE_MISMATCH, Diagnostic.node(node.uuid()),
                            pin, def.type().toString(), literal.type().toString()));
                }
            });
            for (NodeShape.PinDef def : shape.inputs()) {
                if (def.required() && node.literal(def.name()) == null
                        && bp.linksInto(node.uuid(), def.name()).isEmpty()) {
                    out.add(Diagnostic.error(DiagnosticCode.REQUIRED_PIN_UNLINKED,
                            Diagnostic.node(node.uuid()), def.name()));
                }
            }
        }

        if (!bp.nodes().isEmpty() && !anyEntryPoint) {
            out.add(Diagnostic.warning(DiagnosticCode.NO_ENTRY_POINT, Diagnostic.graph()));
        }

        // Nœuds de variables (5.5) : le pin « var » doit être un littéral (pas câblé —
        // le compilateur résout le nom À LA COMPILATION) nommant une variable existante.
        for (Node node : bp.nodes().values()) {
            if (!VarNodes.isVarNode(node.typeId())) {
                continue;
            }
            String name = VarNodes.boundName(node);
            if (!bp.linksInto(node.uuid(), VarNodes.VAR_PIN).isEmpty()
                    || name == null || !bp.variables().containsKey(name)) {
                out.add(Diagnostic.error(DiagnosticCode.VARIABLE_NOT_FOUND,
                        Diagnostic.node(node.uuid()), name == null ? "?" : name));
            }
        }

        // Liens : validation individuelle (défense en profondeur, les ops valident déjà).
        for (Link link : bp.links()) {
            Diagnostic d = checkLink(bp, shapes, link, false);
            if (d != null) {
                out.add(d);
            }
        }

        // Jokers : une résolution par nœud, tous les liens y contribuent (FR5).
        for (Node node : bp.nodes().values()) {
            NodeShape shape = shapes.get(node.uuid());
            GenericResolution resolution = new GenericResolution();
            for (Link link : bp.linksTouching(node.uuid())) {
                Diagnostic conflict = unifyLink(bp, shapes, resolution, node, shape, link);
                if (conflict != null) {
                    out.add(conflict);
                }
            }
        }

        // Cycles de données (FR7) : DFS tricolore sur les liens DATA uniquement.
        out.addAll(findDataCycles(bp, shapes, ghosts));

        // Appels de fonction (20.1) : le pin « function » doit être un littéral nommant
        // une fonction existante. Même forme que le contrôle des variables ci-dessus, et
        // pour la même raison — le compilateur résout le nom À LA COMPILATION.
        for (Node node : bp.nodes().values()) {
            out.addAll(checkCall(bp, node));
        }

        // Écrans (épic 10) : les mêmes règles que celles appliquées à l'édition,
        // rejouées ici — un graphe reçu du réseau ou relu d'une sauvegarde n'est
        // jamais passé par les EditOperation.
        out.addAll(validateScreens(bp, limits));

        // Corps de fonctions (20.1). SANS cette passe, un nœud ADMIN caché dans un corps
        // échapperait au plafond de permission du blueprint : une fonction serait une
        // machine à blanchir les permissions.
        out.addAll(validateFunctions(bp, lookup));

        boolean executable = out.stream().noneMatch(d -> d.severity() == Diagnostic.Severity.ERROR);
        return new ValidationResult(List.copyOf(out), executable);
    }

    /** Un appel dont le nom ne désigne aucune fonction (AC6). */
    private static List<Diagnostic> checkCall(Blueprint bp, Node node) {
        if (!FuncNodes.isCall(node.typeId())) {
            return List.of();
        }
        String name = FuncNodes.boundName(node);
        if (!bp.linksInto(node.uuid(), FuncNodes.FUNCTION_PIN).isEmpty()
                || name == null || !bp.functions().containsKey(name)) {
            return List.of(Diagnostic.error(DiagnosticCode.FUNCTION_NOT_FOUND,
                    Diagnostic.node(node.uuid()), name == null ? "?" : name));
        }
        return List.of();
    }

    /**
     * Contrôle des corps de fonctions (story 20.1, AC2, AC7, AC10).
     *
     * <p><b>La permission d'abord.</b> Chaque nœud d'un corps est confronté au plafond du
     * blueprint, exactement comme ceux du graphe principal. C'est la raison d'être de cette
     * passe : sans elle, poser un nœud {@code ADMIN} dans une fonction suffirait à le faire
     * exécuter par un graphe déclaré {@code GAMEPLAY}, et rien ne le dirait.
     */
    private static List<Diagnostic> validateFunctions(Blueprint bp, NodeTypeLookup lookup) {
        List<Diagnostic> out = new ArrayList<>();
        for (BlueprintFunction function : bp.functions().values()) {
            for (Node node : function.nodes().values()) {
                NodeShape shape = lookup.shape(bp, node);
                if (shape == null) {
                    out.add(Diagnostic.error(DiagnosticCode.UNKNOWN_NODE_TYPE,
                            Diagnostic.node(node.uuid()), node.typeId().toString(),
                            node.typeId().getNamespace()));
                    continue;
                }
                // AC10 — la faille.
                if (!shape.permission().allowedUnder(bp.meta().permissionCap())) {
                    out.add(Diagnostic.error(DiagnosticCode.PERMISSION_EXCEEDED,
                            Diagnostic.node(node.uuid()), shape.permission().name(),
                            bp.meta().permissionCap().name()));
                }
                // AC2 — une fonction s'appelle, elle ne se déclenche pas. Un événement
                // dans un corps n'est pas seulement inutile : il donnerait un point
                // d'entrée que rien ne rattache à un appelant, et dont les slots
                // appartiendraient à un cadre qui n'existe pas.
                if (shape.entryPoint()) {
                    out.add(Diagnostic.error(DiagnosticCode.EVENT_IN_FUNCTION,
                            Diagnostic.node(node.uuid()), function.name()));
                }
                for (NodeShape.PinDef def : shape.inputs()) {
                    if (def.required() && node.literal(def.name()) == null
                            && function.linksInto(node.uuid(), def.name()).isEmpty()) {
                        out.add(Diagnostic.error(DiagnosticCode.REQUIRED_PIN_UNLINKED,
                                Diagnostic.node(node.uuid()), def.name()));
                    }
                }
                out.addAll(checkCall(bp, node));
            }
        }
        out.addAll(findCallCycles(bp));
        return out;
    }

    /**
     * Les cycles du graphe des appels (AC7) — récursion directe comme mutuelle.
     *
     * <p>Refusée, et non bornée. {@code ExecutionState#frames} n'a pas de plafond : une
     * récursion profonde consomme de la mémoire jusqu'à ce que le budget de carburant
     * coupe, c'est-à-dire tard, et en nommant une cause qui n'est pas la bonne.
     *
     * <p>Parcours tricolore, comme {@link #findDataCycles} — même problème, même forme.
     */
    private static List<Diagnostic> findCallCycles(Blueprint bp) {
        List<Diagnostic> out = new ArrayList<>();
        Set<String> done = new HashSet<>();
        Set<String> onPath = new LinkedHashSet<>();
        for (String name : bp.functions().keySet()) {
            visitCalls(bp, name, done, onPath, out);
        }
        return out;
    }

    private static void visitCalls(Blueprint bp, String name, Set<String> done,
                                   Set<String> onPath, List<Diagnostic> out) {
        if (done.contains(name)) {
            return;
        }
        if (!onPath.add(name)) {
            // Le chemin courant nomme le cycle entier : « a → b → a » se lit, là où
            // « récursion détectée » enverrait chercher laquelle.
            out.add(Diagnostic.error(DiagnosticCode.FUNCTION_RECURSION,
                    Diagnostic.function(name), String.join(" → ", onPath) + " → " + name));
            return;
        }
        BlueprintFunction function = bp.function(name);
        if (function != null) {
            for (Node node : function.nodes().values()) {
                String called = FuncNodes.boundName(node);
                if (called != null) {
                    visitCalls(bp, called, done, onPath, out);
                }
            }
        }
        onPath.remove(name);
        done.add(name);
    }

    /**
     * Contrôle des écrans (story 10.1, AC5).
     *
     * <p>Les mêmes règles que {@link ScreenRules}, appliquées à un graphe <b>déjà
     * constitué</b> : relu d'une sauvegarde, reçu du réseau, ou importé d'un fichier.
     * Aucun de ces chemins ne passe par les {@code EditOperation} — sans cette passe,
     * un écran malformé venu de l'extérieur ne serait vu par personne.
     */
    private static List<Diagnostic> validateScreens(Blueprint bp, GraphLimits limits) {
        List<Diagnostic> out = new ArrayList<>();
        if (bp.screens().size() > limits.maxScreens()) {
            out.add(Diagnostic.error(DiagnosticCode.SCREEN_LIMIT_EXCEEDED,
                    Diagnostic.graph(), bp.screens().size(), limits.maxScreens()));
        }
        for (var screen : bp.screens().values()) {
            if (screen.size() > limits.maxElementsPerScreen()) {
                out.add(Diagnostic.error(DiagnosticCode.ELEMENT_LIMIT_EXCEEDED,
                        Diagnostic.screen(screen.name()),
                        screen.size(), limits.maxElementsPerScreen()));
            }
            // Une seule passe de disposition pour tout l'écran : c'est ce qui donne la
            // taille RÉELLE de chaque élément, celle qui se décide entre frères. La
            // remontée par élément, elle, ne connaît que le parent (story 10.10).
            var rects = fr.blueprint.core.graph.screen.ScreenLayout.solve(
                    screen, fr.blueprint.core.graph.screen.Screen.SAFE_WIDTH,
                    fr.blueprint.core.graph.screen.Screen.SAFE_HEIGHT);
            for (var element : screen.elements().values()) {
                Diagnostic refusal =
                        ScreenRules.checkPlacement(screen.name(), screen, element, limits);
                if (refusal != null) {
                    out.add(refusal);
                }
                var rect = rects.get(element.name());
                if (rect == null) {
                    continue;   // parenté cyclique : déjà signalée par checkPlacement
                }
                // Déborder de la zone garantie n'est qu'un AVERTISSEMENT : le menu
                // reste valide, mais son auteur doit apprendre à la conception qu'une
                // partie de ses joueurs ne le verra pas entier.
                if (ScreenRules.outsideSafeArea(rect)) {
                    out.add(Diagnostic.warning(DiagnosticCode.ELEMENT_OUTSIDE_SAFE_AREA,
                            Diagnostic.element(screen.name(), element.name()),
                            element.name()));
                }
                if (ScreenRules.squeezed(rect)) {
                    out.add(Diagnostic.warning(DiagnosticCode.ELEMENT_SQUEEZED,
                            Diagnostic.element(screen.name(), element.name()),
                            element.name()));
                }
                if (ScreenRules.scrollsButHugs(element)) {
                    out.add(Diagnostic.warning(DiagnosticCode.SCREEN_SCROLL_HUGS,
                            Diagnostic.element(screen.name(), element.name()),
                            element.name()));
                }
                if (ScreenRules.styleNotFound(screen, element)) {
                    out.add(Diagnostic.warning(DiagnosticCode.SCREEN_STYLE_NOT_FOUND,
                            Diagnostic.element(screen.name(), element.name()),
                            element.name(), element.styleName()));
                }
                // Une liaison morte est une ERREUR, pas un avertissement (10.7, AC4).
                // Un élément lié à une variable renommée n'affichera jamais rien, et se
                // taire ici reviendrait à laisser l'auteur découvrir en jeu un menu qui
                // reste vide — la panne exacte que la liaison existe pour éviter.
                // Une liaison de source CLIENT ne nomme pas une variable mais une valeur
                // du catalogue : la chercher parmi les variables la déclarerait morte à
                // tous les coups. Elle est vérifiée contre le catalogue, et un nom qui
                // n'y est pas est la MÊME erreur — un élément qui n'affichera jamais rien.
                if (element.isBound() && !bindingResolves(bp, element.binding())) {
                    out.add(Diagnostic.error(DiagnosticCode.SCREEN_BINDING_NOT_FOUND,
                            Diagnostic.element(screen.name(), element.name()),
                            element.name(), element.binding().variable()));
                }
            }
        }
        return out;
    }

    /**
     * Ce que cette liaison désigne existe-t-il — variable du graphe, ou valeur client ?
     *
     * <p>Le <b>maximum</b> compte autant que la valeur. Un maximum qui ne résout pas
     * retombe silencieusement sur celui du blueprint, et la barre paraît fonctionner tout
     * en mesurant sur la mauvaise échelle : c'est exactement la panne qu'un diagnostic
     * existe pour empêcher.
     */
    private static boolean bindingResolves(Blueprint bp,
                                           fr.blueprint.core.graph.screen.ElementBinding b) {
        return resolves(bp, b, b.variable())
                && (!b.hasDynamicMax() || resolves(bp, b, b.maxVariable()));
    }

    private static boolean resolves(Blueprint bp,
                                    fr.blueprint.core.graph.screen.ElementBinding b,
                                    String name) {
        return b.source() == fr.blueprint.core.graph.screen.ElementBinding.Source.CLIENT
                ? fr.blueprint.core.graph.screen.ClientValue.byKey(name) != null
                : bp.variables().containsKey(name);
    }

    /**
     * La règle de câblage unique (AC2, FR3, FR4). Retourne le diagnostic de refus,
     * ou null si le lien est acceptable. Utilisée par {@code EditOperation.AddLink}
     * et par {@link #validate}.
     */
    public static @Nullable Diagnostic canLink(Blueprint bp, NodeTypeLookup lookup, Link link) {
        Map<UUID, NodeShape> shapes = new HashMap<>();
        Node from = bp.node(link.fromNode());
        Node to = bp.node(link.toNode());
        if (from == null || to == null) {
            return Diagnostic.error(DiagnosticCode.NODE_NOT_FOUND, Diagnostic.link(link),
                    from == null ? link.fromNode().toString() : link.toNode().toString());
        }
        shapes.put(from.uuid(), shapeOrGhost(bp, lookup, from));
        shapes.put(to.uuid(), shapeOrGhost(bp, lookup, to));
        if (bp.links().contains(link)) {
            return Diagnostic.error(DiagnosticCode.DUPLICATE_LINK, Diagnostic.link(link));
        }
        Diagnostic structural = checkLink(bp, shapes, link, true);
        if (structural != null) {
            return structural;
        }
        // Conflit de jokers si on ajoutait ce lien (résolutions des deux extrémités).
        // Les liens EXISTANTS des deux nœuds participent : leurs contreparties doivent
        // donc aussi être résolues en forme, pas seulement les extrémités du candidat.
        for (Node endpoint : List.of(from, to)) {
            NodeShape shape = shapes.get(endpoint.uuid());
            GenericResolution resolution = new GenericResolution();
            List<Link> withCandidate = new ArrayList<>(bp.linksTouching(endpoint.uuid()));
            withCandidate.add(link);
            for (Link l : withCandidate) {
                ensureShape(bp, lookup, shapes, l.fromNode());
                ensureShape(bp, lookup, shapes, l.toNode());
                Diagnostic conflict = unifyLink(bp, shapes, resolution, endpoint, shape, l);
                if (conflict != null) {
                    return conflict;
                }
            }
        }
        return null;
    }

    /**
     * Contrôle d'un lien DÉJÀ posé — mêmes règles que {@link #canLink}, sans le refus
     * « doublon » (le lien est là par construction). Sert au garde réseau (6.4) : un
     * graphe reçu repasse lien par lien devant la règle unique de câblage.
     */
    public static @Nullable Diagnostic checkExistingLink(Blueprint bp, NodeTypeLookup lookup,
                                                         Link link) {
        Node from = bp.node(link.fromNode());
        Node to = bp.node(link.toNode());
        if (from == null || to == null) {
            return Diagnostic.error(DiagnosticCode.NODE_NOT_FOUND, Diagnostic.link(link),
                    from == null ? link.fromNode().toString() : link.toNode().toString());
        }
        Map<UUID, NodeShape> shapes = new HashMap<>();
        shapes.put(from.uuid(), shapeOrGhost(bp, lookup, from));
        shapes.put(to.uuid(), shapeOrGhost(bp, lookup, to));
        return checkLink(bp, shapes, link, false);
    }

    private static void ensureShape(Blueprint bp, NodeTypeLookup lookup,
                                    Map<UUID, NodeShape> shapes, UUID uuid) {
        if (!shapes.containsKey(uuid)) {
            Node node = bp.node(uuid);
            if (node != null) {
                shapes.put(uuid, shapeOrGhost(bp, lookup, node));
            }
        }
    }

    private static NodeShape shapeOrGhost(Blueprint bp, NodeTypeLookup lookup, Node node) {
        NodeShape shape = lookup.shape(node.typeId());
        return shape != null ? shape : GhostNode.deduceShape(bp, lookup, node);
    }

    /** Contrôles pin à pin d'un lien. {@code adding} : le lien n'est pas encore posé. */
    private static @Nullable Diagnostic checkLink(Blueprint bp, Map<UUID, NodeShape> shapes,
                                                  Link link, boolean adding) {
        NodeShape fromShape = shapes.get(link.fromNode());
        NodeShape toShape = shapes.get(link.toNode());
        if (fromShape == null || toShape == null) {
            return Diagnostic.error(DiagnosticCode.NODE_NOT_FOUND, Diagnostic.link(link),
                    (fromShape == null ? link.fromNode() : link.toNode()).toString());
        }
        NodeShape.PinDef out = fromShape.output(link.fromPin());
        NodeShape.PinDef in = toShape.input(link.toPin());
        if (out == null) {
            return Diagnostic.error(DiagnosticCode.PIN_NOT_FOUND, Diagnostic.link(link), link.fromPin());
        }
        if (in == null) {
            return Diagnostic.error(DiagnosticCode.PIN_NOT_FOUND, Diagnostic.link(link), link.toPin());
        }
        if (out.kind() != in.kind()) {
            return Diagnostic.error(DiagnosticCode.TYPE_MISMATCH, Diagnostic.link(link),
                    link.toPin(), in.type().toString(), out.type().toString());
        }
        // Cardinalité (FR3) : exec sortant ≤ 1, data entrant ≤ 1.
        int tolerance = adding ? 0 : 1;
        if (out.kind() == PinKind.EXEC
                && bp.linksFrom(link.fromNode(), link.fromPin()).size() > tolerance) {
            return Diagnostic.error(DiagnosticCode.EXEC_OUT_ALREADY_LINKED, Diagnostic.link(link), link.fromPin());
        }
        if (in.kind() == PinKind.DATA
                && bp.linksInto(link.toNode(), link.toPin()).size() > tolerance) {
            return Diagnostic.error(DiagnosticCode.DATA_IN_ALREADY_LINKED, Diagnostic.link(link), link.toPin());
        }
        if (in.kind() == PinKind.DATA && !in.type().isAssignableFrom(out.type())) {
            return Diagnostic.error(DiagnosticCode.TYPE_MISMATCH, Diagnostic.link(link),
                    link.toPin(), in.type().toString(), out.type().toString());
        }
        // Auto-boucle de données : cycle trivial, refusé immédiatement.
        if (adding && in.kind() == PinKind.DATA && link.fromNode().equals(link.toNode())) {
            return Diagnostic.error(DiagnosticCode.DATA_CYCLE, Diagnostic.link(link));
        }
        return null;
    }

    /** Contribution d'un lien à la résolution des jokers d'un nœud (contrat « déclaré d'abord »). */
    private static @Nullable Diagnostic unifyLink(Blueprint bp, Map<UUID, NodeShape> shapes,
                                                   GenericResolution resolution,
                                                   Node node, NodeShape shape, Link link) {
        NodeShape.PinDef declared;
        NodeShape.PinDef concrete;
        if (link.toNode().equals(node.uuid())) {
            declared = shape.input(link.toPin());
            NodeShape other = shapes.get(link.fromNode());
            concrete = other == null ? null : other.output(link.fromPin());
        } else if (link.fromNode().equals(node.uuid())) {
            declared = shape.output(link.fromPin());
            NodeShape other = shapes.get(link.toNode());
            concrete = other == null ? null : other.input(link.toPin());
        } else {
            return null;
        }
        if (declared == null || concrete == null || declared.kind() != PinKind.DATA) {
            return null; // les pins manquants ont déjà leur diagnostic
        }
        if (!containsGeneric(declared.type()) ) {
            return null;
        }
        if (!resolution.unify(declared.type(), concrete.type())) {
            return Diagnostic.error(DiagnosticCode.GENERIC_CONFLICT, Diagnostic.link(link),
                    declared.type().toString(), concrete.type().toString());
        }
        return null;
    }

    private static boolean containsGeneric(PinType type) {
        if (type.isGeneric()) {
            return true;
        }
        if (type instanceof ParameterizedPinType p) {
            for (PinType arg : p.args()) {
                if (containsGeneric(arg)) {
                    return true;
                }
            }
        }
        return false;
    }

    // DFS tricolore : blanc (jamais vu), gris (en cours), noir (terminé).
    // Un arc DATA vers un nœud gris ferme un cycle.
    private static List<Diagnostic> findDataCycles(Blueprint bp, Map<UUID, NodeShape> shapes,
                                                    Set<UUID> ghosts) {
        Map<UUID, List<Link>> dataOut = new HashMap<>();
        for (Link link : bp.links()) {
            // GHOST-001 : entre deux fantômes, la nature du pin est indevinable (déduite
            // DATA par défaut) — un cycle exec y serait un faux positif ; on s'abstient,
            // le graphe est déjà non exécutable via UNKNOWN_NODE_TYPE.
            if (ghosts.contains(link.fromNode()) && ghosts.contains(link.toNode())) {
                continue;
            }
            NodeShape fromShape = shapes.get(link.fromNode());
            if (fromShape == null) {
                continue;
            }
            NodeShape.PinDef out = fromShape.output(link.fromPin());
            if (out != null && out.kind() == PinKind.DATA) {
                dataOut.computeIfAbsent(link.fromNode(), k -> new ArrayList<>()).add(link);
            }
        }
        List<Diagnostic> cycles = new ArrayList<>();
        Set<UUID> done = new HashSet<>();
        Set<UUID> inProgress = new HashSet<>();
        for (UUID start : bp.nodes().keySet()) {
            dfs(start, dataOut, inProgress, done, cycles);
        }
        return cycles;
    }

    private static void dfs(UUID node, Map<UUID, List<Link>> dataOut,
                            Set<UUID> inProgress, Set<UUID> done, List<Diagnostic> cycles) {
        if (done.contains(node) || !inProgress.add(node)) {
            return;
        }
        for (Link link : dataOut.getOrDefault(node, List.of())) {
            if (inProgress.contains(link.toNode())) {
                cycles.add(Diagnostic.error(DiagnosticCode.DATA_CYCLE, Diagnostic.link(link)));
            } else {
                dfs(link.toNode(), dataOut, inProgress, done, cycles);
            }
        }
        inProgress.remove(node);
        done.add(node);
    }
}
