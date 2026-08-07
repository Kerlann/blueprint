package fr.blueprint.core.graph;

import java.util.List;

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
