package fr.blueprint.client.editor;

import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.BlueprintFunction;
import fr.blueprint.core.graph.CommentBox;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.FunctionOps;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.Node;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Le graphe que le canevas est en train d'éditer (story 20.2, AC2, AC3).
 *
 * <p>Un corps de fonction est un graphe : nœuds, liens, littéraux, sélection, annuler,
 * minimap, recherche. Écrire un second canevas pour le montrer reviendrait à réapprendre à
 * un jumeau tout ce que l'original sait déjà — et à corriger désormais chaque défaut deux
 * fois. Le canevas reçoit donc, non plus un {@link Blueprint}, mais <b>de quoi lire et muter
 * un</b> ensemble de nœuds et de liens ; lequel est décidé ici.
 *
 * <p>Ce qui se lit change ; <b>ce qui s'applique ne change pas de nature</b>. Le canevas
 * continue de fabriquer des {@link EditOperation} qui ne savent rien des fonctions, et
 * {@link #retarget} les redirige vers le corps ouvert. Les opérations partent toujours à la
 * session : l'annuler/rétablir, la sauvegarde et le réseau ne voient aucune différence, ce
 * qui est exactement ce qu'exige l'AC3 — une seule pile, dans l'ordre des gestes.
 *
 * <p>Cette classe ne détient <b>aucun</b> état du graphe : elle en détient le <i>nom</i>. Un
 * corps édité dans un blueprint jetable puis reposé en bloc aurait donné un {@code Ctrl+Z}
 * qui annule le repos entier plutôt que le geste, et un {@code Ctrl+S} qui enregistre un
 * graphe où le corps n'a pas encore été reposé (AC11).
 */
public final class GraphView {

    private final Blueprint bp;
    /** {@code null} : le graphe principal. Sinon le nom du corps ouvert. */
    private @Nullable String function;

    public GraphView(Blueprint bp) {
        this.bp = bp;
    }

    public Blueprint blueprint() {
        return bp;
    }

    /** Le corps ouvert, ou {@code null} pour le graphe principal. */
    public @Nullable String function() {
        return function;
    }

    public boolean inBody() {
        return function != null;
    }

    /**
     * Ouvre un corps, ou revient au graphe principal avec {@code null}.
     *
     * @return faux si la fonction n'existe pas — la vue ne bouge pas.
     */
    public boolean open(@Nullable String name) {
        if (name != null && bp.function(name) == null) {
            return false;
        }
        function = name;
        return true;
    }

    /**
     * Le corps ouvert, ou {@code null}.
     *
     * <p>Peut valoir {@code null} <b>alors qu'un nom est ouvert</b> : un {@code Ctrl+Z} qui
     * annule la création d'une fonction efface le corps qu'on regarde. Les lectures rendent
     * alors le vide plutôt que de retomber sur le graphe principal — on continuerait sinon
     * à éditer, en croyant être dans le corps, le graphe qu'on ne regarde pas.
     */
    private @Nullable BlueprintFunction body() {
        return function == null ? null : bp.function(function);
    }

    private boolean orphan() {
        return function != null && bp.function(function) == null;
    }

    // ---------------------------------------------------------------------- lectures

    public Map<UUID, Node> nodes() {
        BlueprintFunction f = body();
        if (f != null) {
            return f.nodes();
        }
        return orphan() ? Map.of() : bp.nodes();
    }

    public Set<Link> links() {
        BlueprintFunction f = body();
        if (f != null) {
            return f.links();
        }
        return orphan() ? Set.of() : bp.links();
    }

    public @Nullable Node node(UUID uuid) {
        return nodes().get(uuid);
    }

    public List<Link> linksInto(UUID node, String pin) {
        BlueprintFunction f = body();
        if (f != null) {
            return List.copyOf(f.linksInto(node, pin));
        }
        return orphan() ? List.of() : bp.linksInto(node, pin);
    }

    public List<Link> linksFrom(UUID node, String pin) {
        BlueprintFunction f = body();
        if (f == null) {
            return orphan() ? List.of() : bp.linksFrom(node, pin);
        }
        return f.links().stream()
                .filter(l -> l.fromNode().equals(node) && l.fromPin().equals(pin))
                .toList();
    }

    public List<Link> linksTouching(UUID node) {
        BlueprintFunction f = body();
        if (f != null) {
            return List.copyOf(f.linksTouching(node));
        }
        return orphan() ? List.of() : bp.linksTouching(node);
    }

    /**
     * Les commentaires — <b>toujours vides dans un corps</b>.
     *
     * <p>Un corps n'en porte pas : {@link BlueprintFunction} ne stocke que des nœuds et des
     * liens. C'est une limite assumée plutôt qu'un oubli, et elle se voit ici plutôt que de
     * se découvrir en cliquant dans le vide — {@link #retarget} refuse les gestes de
     * commentaire au lieu de les envoyer au graphe principal, où ils apparaîtraient dans un
     * graphe qu'on ne regarde pas.
     */
    public Collection<CommentBox> comments() {
        return inBody() ? List.of() : bp.comments();
    }

    public @Nullable CommentBox comment(UUID uuid) {
        return inBody() ? null : bp.comment(uuid);
    }

    /**
     * La révision de la session, plus le corps regardé.
     *
     * <p>Les caches du canevas — boîtes, index de survol, pins câblés — sont invalidés par
     * la révision. Passer d'un corps à l'autre ne modifie rien, donc ne la fait pas bouger :
     * sans le nom dans la clé, le canevas garderait la géométrie du graphe précédent et l'on
     * cliquerait sur des boîtes qui ne sont plus là.
     */
    public int revision() {
        return bp.revision() * 31 + (function == null ? 0 : function.hashCode());
    }

    /**
     * Ce lien est-il posable ici ? Le diagnostic qui l'en empêche, ou {@code null}.
     *
     * <p>Le validateur a deux portes — une par graphe. Poser la question au graphe principal
     * pendant qu'on câble un corps répondrait sur des nœuds qui ne sont pas ceux qu'on
     * regarde : « nœud introuvable » pour un câblage parfaitement valide.
     */
    public fr.blueprint.core.graph.@Nullable Diagnostic canLink(
            fr.blueprint.core.graph.NodeTypeLookup lookup, Link link) {
        BlueprintFunction f = body();
        if (function == null) {
            return fr.blueprint.core.graph.GraphValidator.canLink(bp, lookup, link);
        }
        return f == null
                ? fr.blueprint.core.graph.Diagnostic.error(
                        fr.blueprint.core.graph.DiagnosticCode.FUNCTION_NOT_FOUND,
                        fr.blueprint.core.graph.Diagnostic.function(function), function)
                : fr.blueprint.core.graph.GraphValidator.canLinkIn(bp, f, lookup, link);
    }

    /**
     * Où vit ce nœud : le nom du corps qui le contient, ou {@code null} pour le graphe
     * principal — et {@code null} aussi s'il n'existe nulle part.
     *
     * <p>Un diagnostic ne nomme que le nœud fautif, jamais le graphe où il se trouve. C'est
     * ce qui permet à un clic sur une erreur d'ouvrir le corps concerné (AC9) sans que le
     * validateur ait à enrichir chaque diagnostic — et un diagnostic qu'on ne peut pas
     * atteindre vaut à peine mieux qu'un silence.
     *
     * <p>La réponse est non ambiguë : un identifiant refusé s'il existe déjà ailleurs, à la
     * pose comme dans un corps, ne peut pas vivre dans deux graphes.
     */
    public @Nullable String owner(UUID node) {
        if (bp.node(node) != null) {
            return null;
        }
        for (BlueprintFunction f : bp.functions().values()) {
            if (f.nodes().containsKey(node)) {
                return f.name();
            }
        }
        return null;
    }

    /** Vrai si ce nœud existe quelque part — graphe principal ou corps. */
    public boolean exists(UUID node) {
        return bp.node(node) != null || owner(node) != null;
    }

    // ------------------------------------------------------------------- redirection

    /**
     * L'opération, telle quelle dans le graphe principal, redirigée vers le corps ouvert.
     *
     * @return {@code null} si le geste n'a pas de sens ici — l'appelant refuse.
     */
    public @Nullable EditOperation retarget(EditOperation op) {
        String f = function;
        if (f == null) {
            return op;
        }
        return switch (op) {
            case EditOperation.AddNode o ->
                    new FunctionOps.AddNodeIn(f, o.uuid(), o.typeId(), o.position());
            case EditOperation.RemoveNode o -> new FunctionOps.RemoveNodeIn(f, o.uuid());
            case EditOperation.RestoreNode o ->
                    new FunctionOps.RestoreNodeIn(f, o.snapshot(), o.links());
            case EditOperation.MoveNode o -> new FunctionOps.MoveNodeIn(f, o.uuid(), o.to());
            case EditOperation.SetLiteral o ->
                    new FunctionOps.SetLiteralIn(f, o.uuid(), o.pin(), o.value());
            case EditOperation.AddLink o -> new FunctionOps.AddLinkIn(f, o.link());
            case EditOperation.RemoveLink o -> new FunctionOps.RemoveLinkIn(f, o.link());
            // Un corps ne porte pas de commentaires. Les laisser passer les poserait dans le
            // graphe principal, où l'auteur ne les cherchera jamais.
            case EditOperation.AddComment ignored -> null;
            case EditOperation.EditComment ignored -> null;
            case EditOperation.RemoveComment ignored -> null;
            // Les variables, les écrans et les métadonnées appartiennent au blueprint et non
            // au graphe : elles sont les mêmes vues d'un corps.
            default -> op;
        };
    }
}
