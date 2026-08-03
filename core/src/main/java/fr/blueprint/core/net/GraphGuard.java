package fr.blueprint.core.net;

import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.CommentBox;
import fr.blueprint.core.graph.GraphValidator;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.Node;
import fr.blueprint.core.graph.NodeShape;
import fr.blueprint.core.graph.NodeTypeLookup;
import fr.blueprint.core.graph.Variable;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Contrôle serveur d'un graphe reçu (story 6.4, AC2 : aucune confiance au client).
 * Pur — aucune dépendance réseau : le même contrôle vaut pour un paquet, un import
 * de fichier ou un test.
 *
 * <p>Ce que ce garde refuse est du domaine de l'<b>abus</b> : bornes dépassées, liens
 * qui ne mènent nulle part, câblage que l'éditeur n'aurait jamais permis. Ce qu'il
 * <b>tolère</b> est du domaine du désaccord légitime : un type de nœud que le serveur
 * ne connaît pas devient fantôme et le blueprint refuse de s'exécuter (FR40/FR41) —
 * le rejeter détruirait le graphe d'un joueur qui a un mod de plus que le serveur.
 */
public final class GraphGuard {

    /** Accepté, ou refusé avec la raison (journal serveur, jamais montrée au client). */
    public record Verdict(boolean accepted, @Nullable String reason) {

        public static final Verdict OK = new Verdict(true, null);

        static Verdict no(String reason) {
            return new Verdict(false, reason);
        }
    }

    private GraphGuard() {
    }

    /**
     * @param declared identifiant annoncé par le paquet — doit être celui du graphe
     */
    public static Verdict inspect(Identifier declared, Blueprint bp, NodeTypeLookup lookup,
                                  NetLimits limits) {
        if (!bp.id().equals(declared)) {
            return Verdict.no("identifiant annoncé " + declared + " ≠ " + bp.id());
        }
        if (bp.nodes().size() > limits.maxNodes()) {
            return Verdict.no(bp.nodes().size() + " nœuds (max " + limits.maxNodes() + ")");
        }
        if (bp.links().size() > limits.maxLinks()) {
            return Verdict.no(bp.links().size() + " liens (max " + limits.maxLinks() + ")");
        }
        if (bp.variables().size() > limits.maxVariables()) {
            return Verdict.no(bp.variables().size() + " variables (max "
                    + limits.maxVariables() + ")");
        }
        if (bp.comments().size() > limits.maxComments()) {
            return Verdict.no(bp.comments().size() + " commentaires (max "
                    + limits.maxComments() + ")");
        }

        Verdict text = checkText(bp, limits);
        if (!text.accepted()) {
            return text;
        }

        int ghosts = 0;
        for (Node node : bp.nodes().values()) {
            if (lookup.shape(node.typeId()) == null) {
                ghosts++;
            }
        }
        if (ghosts > limits.maxGhosts()) {
            return Verdict.no(ghosts + " nœuds fantômes (max " + limits.maxGhosts() + ")");
        }

        for (Link link : bp.links()) {
            Node from = bp.node(link.fromNode());
            Node to = bp.node(link.toNode());
            // Extrémité manquante : soit le client l'a inventée, soit un nœud est
            // tombé au décodage (identifiant malformé) — dans les deux cas le graphe
            // est incohérent, on ne le répare pas à sa place.
            if (from == null || to == null) {
                return Verdict.no("lien pendant " + link.fromNode() + " → " + link.toNode());
            }
            // Câblage : la MÊME règle que l'éditeur. Les liens touchant un fantôme
            // échappent au contrôle (aucune forme de référence) et restent tolérés.
            if (lookup.shape(from.typeId()) == null || lookup.shape(to.typeId()) == null) {
                continue;
            }
            var refusal = GraphValidator.checkExistingLink(bp, lookup, link);
            if (refusal != null) {
                return Verdict.no("lien refusé (" + refusal.code().name() + ") : "
                        + link.fromPin() + " → " + link.toPin());
            }
        }
        return Verdict.OK;
    }

    private static Verdict checkText(Blueprint bp, NetLimits limits) {
        int max = limits.maxTextLength();
        var meta = bp.meta();
        if (meta.author().length() > max || meta.description().length() > max
                || meta.version().length() > max) {
            return Verdict.no("métadonnées au-delà de " + max + " caractères");
        }
        for (Map.Entry<String, Variable> entry : bp.variables().entrySet()) {
            if (entry.getKey().length() > max) {
                return Verdict.no("nom de variable au-delà de " + max + " caractères");
            }
        }
        for (CommentBox comment : bp.comments()) {
            if (comment.text().length() > max) {
                return Verdict.no("commentaire au-delà de " + max + " caractères");
            }
        }
        for (Node node : bp.nodes().values()) {
            for (Map.Entry<String, ?> literal : node.literals().entrySet()) {
                if (literal.getKey().length() > max) {
                    return Verdict.no("nom de pin au-delà de " + max + " caractères");
                }
                Object value = ((fr.blueprint.api.pin.LiteralValue) literal.getValue()).value();
                if (value instanceof String s && s.length() > max) {
                    return Verdict.no("littéral texte au-delà de " + max + " caractères");
                }
            }
        }
        return Verdict.OK;
    }

    /** Vue pratique : le graphe est-il exécutable côté serveur (FR41) ? */
    public static boolean executable(Blueprint bp, NodeTypeLookup lookup) {
        return GraphValidator.validate(bp, lookup).executable();
    }
}
