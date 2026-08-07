package fr.blueprint.core.graph;

import java.util.List;
import java.util.Set;

/**
 * Les opérations d'édition sur les fonctions (story 20.1), rangées à part sur le modèle de
 * {@link ScreenOps} — {@code EditOperation} en porte déjà dix-huit.
 *
 * <h2>Deux règles que ces opérations tiennent, et qui ne se voient pas</h2>
 *
 * <p><b>Chacune incrémente la révision.</b> L'IR est mise en cache par révision : sans ce
 * geste, un corps corrigé continuerait de tourner dans son ancienne version, et le seul
 * remède serait un redémarrage du serveur. C'est la même discipline que les opérations sur
 * les nœuds, et c'est le genre d'oubli qui se diagnostique très mal.
 *
 * <p><b>Les nœuds des corps comptent dans le plafond.</b> {@code GraphLimits.maxNodes}
 * borne un blueprint entier, corps de fonctions compris. Sans cela, un client enverrait dix
 * fonctions de mille nœuds à travers une garde réseau qui n'en verrait aucun.
 *
 * <h2>Renommer casse les appels, et c'est le précédent</h2>
 *
 * <p>{@link EditOperation.RenameVariable} repose la déclaration sous un autre nom et ne
 * touche pas aux littéraux {@code var} des nœuds qui la lisaient : ils désignent alors un
 * nom qui n'existe plus, et le validateur le dit. Renommer une fonction suit le même
 * chemin. Réécrire silencieusement les littéraux à travers tout le graphe serait la
 * mutation cachée que ce projet évite partout ailleurs ; prévenir avant d'appliquer est le
 * travail de l'éditeur, qui compte déjà ses {@code pendingBreaks} pour les variables.
 */
public final class FunctionOps {

    private FunctionOps() {
    }

    /** Le nombre de nœuds d'un blueprint, corps de fonctions compris. */
    public static int totalNodes(Blueprint bp) {
        int total = bp.nodes().size();
        for (BlueprintFunction function : bp.functions().values()) {
            total += function.nodes().size();
        }
        return total;
    }

    public record AddFunction(BlueprintFunction function) implements EditOperation {
        @Override
        public Result apply(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
            if (function.name().isEmpty()) {
                return Result.refused(Diagnostic.error(DiagnosticCode.FUNCTION_NOT_FOUND,
                        Diagnostic.function(""), ""));
            }
            if (bp.function(function.name()) != null) {
                return Result.refused(Diagnostic.error(DiagnosticCode.DUPLICATE_FUNCTION,
                        Diagnostic.function(function.name()), function.name()));
            }
            int after = totalNodes(bp) + function.nodes().size();
            if (after > limits.maxNodes()) {
                return Result.refused(Diagnostic.error(DiagnosticCode.NODE_LIMIT_EXCEEDED,
                        Diagnostic.graph(), after, limits.maxNodes()));
            }
            bp.putFunction(function);
            bp.bumpRevision();
            return Result.ok(new RemoveFunction(function.name()));
        }
    }

    public record RemoveFunction(String name) implements EditOperation {
        @Override
        public Result apply(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
            BlueprintFunction before = bp.function(name);
            if (before == null) {
                return Result.refused(Diagnostic.error(DiagnosticCode.FUNCTION_NOT_FOUND,
                        Diagnostic.function(name), name));
            }
            // Les appels ne sont PAS supprimés. Ils gardent leur identifiant, leurs liens
            // et leurs littéraux, et le validateur les signale — exactement comme un
            // var/get sur une variable supprimée. Redéfinir la fonction les fait revivre ;
            // les effacer aurait détruit un câblage que personne n'a demandé à perdre.
            bp.dropFunction(name);
            bp.bumpRevision();
            return Result.ok(new AddFunction(before));
        }
    }

    /**
     * Change la signature — donc la forme de tous les appels.
     *
     * <p>L'opération <b>s'applique</b> même si des liens deviennent incompatibles : le
     * modèle de ce projet est que les opérations passent et que les diagnostics parlent.
     * Refuser obligerait l'auteur à défaire son câblage avant de corriger sa signature,
     * c'est-à-dire à travailler à l'envers.
     */
    public record SetSignature(String name, List<BlueprintFunction.Param> inputs,
                               List<BlueprintFunction.Param> outputs) implements EditOperation {
        @Override
        public Result apply(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
            BlueprintFunction before = bp.function(name);
            if (before == null) {
                return Result.refused(Diagnostic.error(DiagnosticCode.FUNCTION_NOT_FOUND,
                        Diagnostic.function(name), name));
            }
            bp.putFunction(before.withSignature(inputs, outputs));
            bp.bumpRevision();
            return Result.ok(new SetSignature(name, before.inputs(), before.outputs()));
        }
    }

    public record RenameFunction(String from, String to) implements EditOperation {
        @Override
        public Result apply(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
            BlueprintFunction before = bp.function(from);
            if (before == null) {
                return Result.refused(Diagnostic.error(DiagnosticCode.FUNCTION_NOT_FOUND,
                        Diagnostic.function(from), from));
            }
            if (bp.function(to) != null) {
                return Result.refused(Diagnostic.error(DiagnosticCode.DUPLICATE_FUNCTION,
                        Diagnostic.function(to), to));
            }
            bp.dropFunction(from);
            bp.putFunction(before.withName(to));
            bp.bumpRevision();
            return Result.ok(new RenameFunction(to, from));
        }
    }

    // ---------------------------------- éditer DANS un corps, geste par geste (20.2)
    //
    // SetBody remplace un corps en bloc : c'est ce qu'il faut pour un collage, et c'est
    // désastreux pour un déplacement de nœud, qui produirait un pas d'annulation par
    // pixel — et dont l'inverse porterait tout le corps.
    //
    // Ces opérations visent donc un nœud DANS une fonction nommée. Elles restent des
    // EditOperation appliquées à la session : l'annuler/rétablir, la sauvegarde et la
    // synchronisation réseau ne changent pas de nature, et un geste fait dans un corps
    // s'annule dans le même ordre qu'un geste fait dans le graphe.

    /** La fonction visée, ou un refus prêt à rendre. */
    private static @org.jetbrains.annotations.Nullable BlueprintFunction target(
            Blueprint bp, String function) {
        return bp.function(function);
    }

    private static EditOperation.Result noFunction(String name) {
        return EditOperation.Result.refused(Diagnostic.error(DiagnosticCode.FUNCTION_NOT_FOUND,
                Diagnostic.function(name), name));
    }

    private static EditOperation.Result noNode(java.util.UUID uuid) {
        return EditOperation.Result.refused(Diagnostic.error(DiagnosticCode.NODE_NOT_FOUND,
                Diagnostic.node(uuid), uuid.toString()));
    }

    public record AddNodeIn(String function, java.util.UUID uuid,
                            net.minecraft.resources.Identifier typeId,
                            Vec2d position) implements EditOperation {
        @Override
        public Result apply(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
            BlueprintFunction f = target(bp, function);
            if (f == null) {
                return noFunction(function);
            }
            if (f.nodes().containsKey(uuid) || bp.node(uuid) != null) {
                return Result.refused(Diagnostic.error(DiagnosticCode.DUPLICATE_NODE,
                        Diagnostic.node(uuid), uuid.toString()));
            }
            if (totalNodes(bp) >= limits.maxNodes()) {
                return Result.refused(Diagnostic.error(DiagnosticCode.NODE_LIMIT_EXCEEDED,
                        Diagnostic.graph(), totalNodes(bp) + 1, limits.maxNodes()));
            }
            bp.putFunction(f.withNode(new Node(uuid, typeId, position)));
            bp.bumpRevision();
            return Result.ok(new RemoveNodeIn(function, uuid));
        }
    }

    public record RemoveNodeIn(String function, java.util.UUID uuid) implements EditOperation {
        @Override
        public Result apply(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
            BlueprintFunction f = target(bp, function);
            if (f == null) {
                return noFunction(function);
            }
            Node node = f.nodes().get(uuid);
            if (node == null) {
                return noNode(uuid);
            }
            // Les liens coupés voyagent avec l'inverse : les recalculer au moment de
            // l'annulation regarderait un corps qui a pu changer entre-temps.
            Set<Link> severed = f.linksTouching(uuid);
            bp.putFunction(f.withoutNode(uuid));
            bp.bumpRevision();
            return Result.ok(new RestoreNodeIn(function, node, Set.copyOf(severed)));
        }
    }

    /** Inverse de {@link RemoveNodeIn} : repose le nœud et ses liens tels quels. */
    public record RestoreNodeIn(String function, Node snapshot,
                                Set<Link> links) implements EditOperation {
        @Override
        public Result apply(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
            BlueprintFunction f = target(bp, function);
            if (f == null) {
                return noFunction(function);
            }
            BlueprintFunction restored = f.withNode(snapshot.copy());
            for (Link link : links) {
                restored = restored.withLink(link);
            }
            bp.putFunction(restored);
            bp.bumpRevision();
            return Result.ok(new RemoveNodeIn(function, snapshot.uuid()));
        }
    }

    /**
     * Déplace un nœud d'un corps.
     *
     * <p>Le {@link Node} est muté <b>en place</b>, comme le fait {@code MoveNode} sur le
     * graphe principal : recopier les deux tables du corps pour changer deux nombres
     * coûterait à chaque image d'un glissement.
     */
    public record MoveNodeIn(String function, java.util.UUID uuid,
                             Vec2d to) implements EditOperation {
        @Override
        public Result apply(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
            BlueprintFunction f = target(bp, function);
            Node node = f == null ? null : f.nodes().get(uuid);
            if (f == null) {
                return noFunction(function);
            }
            if (node == null) {
                return noNode(uuid);
            }
            Vec2d before = node.position();
            node.moveTo(to);
            bp.bumpRevision();
            return Result.ok(new MoveNodeIn(function, uuid, before));
        }
    }

    /** Pose un littéral sur un nœud d'un corps. Muté en place, pour la même raison. */
    public record SetLiteralIn(String function, java.util.UUID uuid, String pin,
                               @org.jetbrains.annotations.Nullable
                               fr.blueprint.api.pin.LiteralValue value) implements EditOperation {
        @Override
        public Result apply(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
            BlueprintFunction f = target(bp, function);
            Node node = f == null ? null : f.nodes().get(uuid);
            if (f == null) {
                return noFunction(function);
            }
            if (node == null) {
                return noNode(uuid);
            }
            NodeShape shape = lookup.shape(bp, node);
            if (shape != null && value != null) {
                NodeShape.PinDef def = shape.input(pin);
                if (def == null) {
                    return Result.refused(Diagnostic.error(DiagnosticCode.PIN_NOT_FOUND,
                            Diagnostic.node(uuid), pin));
                }
                if (!def.type().isAssignableFrom(value.type()) || !Literals.matches(value)) {
                    return Result.refused(Diagnostic.error(DiagnosticCode.TYPE_MISMATCH,
                            Diagnostic.node(uuid), pin, def.type().toString(),
                            value.type().toString()));
                }
            }
            fr.blueprint.api.pin.LiteralValue before = node.literal(pin);
            node.setLiteral(pin, value);
            bp.bumpRevision();
            return Result.ok(new SetLiteralIn(function, uuid, pin, before));
        }
    }

    public record AddLinkIn(String function, Link link) implements EditOperation {
        @Override
        public Result apply(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
            BlueprintFunction f = target(bp, function);
            if (f == null) {
                return noFunction(function);
            }
            if (f.links().contains(link)) {
                return Result.refused(Diagnostic.error(DiagnosticCode.DUPLICATE_LINK,
                        Diagnostic.link(link)));
            }
            Diagnostic refusal = GraphValidator.canLinkIn(bp, f, lookup, link);
            if (refusal != null) {
                return Result.refused(refusal);
            }
            bp.putFunction(f.withLink(link));
            bp.bumpRevision();
            return Result.ok(new RemoveLinkIn(function, link));
        }
    }

    public record RemoveLinkIn(String function, Link link) implements EditOperation {
        @Override
        public Result apply(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
            BlueprintFunction f = target(bp, function);
            if (f == null) {
                return noFunction(function);
            }
            if (!f.links().contains(link)) {
                return Result.refused(Diagnostic.error(DiagnosticCode.LINK_NOT_FOUND,
                        Diagnostic.link(link)));
            }
            bp.putFunction(f.withoutLink(link));
            bp.bumpRevision();
            return Result.ok(new AddLinkIn(function, link));
        }
    }

    /**
     * Remplace le corps d'une fonction.
     *
     * <p>En bloc plutôt que nœud par nœud : l'éditeur (20.2) travaillera sur une copie du
     * corps et la reposera, ce qui donne un annuler/rétablir d'un seul geste. Poser vingt
     * opérations pour un collage de vingt nœuds obligerait à les annuler vingt fois.
     */
    public record SetBody(String name, java.util.Map<java.util.UUID, Node> nodes,
                          java.util.Set<Link> links) implements EditOperation {
        @Override
        public Result apply(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
            BlueprintFunction before = bp.function(name);
            if (before == null) {
                return Result.refused(Diagnostic.error(DiagnosticCode.FUNCTION_NOT_FOUND,
                        Diagnostic.function(name), name));
            }
            int after = totalNodes(bp) - before.nodes().size() + nodes.size();
            if (after > limits.maxNodes()) {
                return Result.refused(Diagnostic.error(DiagnosticCode.NODE_LIMIT_EXCEEDED,
                        Diagnostic.graph(), after, limits.maxNodes()));
            }
            bp.putFunction(before.withBody(nodes, links));
            bp.bumpRevision();
            return Result.ok(new SetBody(name, before.nodes(), before.links()));
        }
    }
}
