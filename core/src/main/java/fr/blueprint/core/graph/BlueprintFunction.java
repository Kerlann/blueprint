package fr.blueprint.core.graph;

import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.PinKind;
import fr.blueprint.api.pin.PinType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Une fonction définie par l'auteur : une signature et un corps (story 20.1).
 *
 * <p><b>Son corps lui appartient.</b> Ses nœuds et ses liens sont à elle, comme les
 * éléments d'un {@link fr.blueprint.core.graph.screen.Screen} sont à lui. L'alternative —
 * laisser ces nœuds dans la réserve du graphe principal, délimités par un nœud d'entrée —
 * aurait demandé un <b>filtrage à chaque passe</b> : la validation pour savoir ce qui est
 * atteignable, le compilateur pour écarter ce qui ne l'est pas, l'éditeur pour le cacher,
 * le profileur pour l'attribuer. Chacun de ces filtres est une occasion d'en oublier un, et
 * celui qu'on oublie est une faille : un nœud {@code ADMIN} qui échappe au plafond de
 * permission du blueprint.
 *
 * <h2>La forme d'appel, calculée une fois</h2>
 *
 * <p>Un nœud {@code func/call} prend la forme de la signature qu'il vise. Cette forme est
 * calculée <b>ici</b>, à la construction, et non à chaque demande : l'éditeur en réclame
 * une par nœud et par image, et allouer soixante fois par seconde ce qui ne change qu'à
 * l'édition serait payer cher un objet immuable.
 */
public record BlueprintFunction(String name, List<Param> inputs, List<Param> outputs,
                                Map<UUID, Node> nodes, Set<Link> links,
                                NodeShape callShape) {

    /** Un paramètre : un nom et un type, comme un pin. */
    public record Param(String name, PinType type) {
    }

    /** Le pin d'exécution entrant d'un appel, et celui qui en sort. */
    public static final String EXEC_IN = "exec_in";
    public static final String EXEC_OUT = "exec_out";

    public BlueprintFunction {
        name = name == null ? "" : name.trim();
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
        nodes = Map.copyOf(nodes);
        links = Set.copyOf(links);
        callShape = callShape == null ? shapeOf(inputs, outputs) : callShape;
    }

    /** Une fonction neuve, au corps vide. */
    public static BlueprintFunction of(String name, List<Param> inputs, List<Param> outputs) {
        return new BlueprintFunction(name, inputs, outputs,
                new LinkedHashMap<>(), new LinkedHashSet<>(), null);
    }

    /**
     * La forme d'un nœud qui appelle cette signature.
     *
     * <p>Toujours un pin d'exécution entrant et un sortant : cette story ne livre pas les
     * fonctions <b>pures</b>. Elles butent sur la mémoïsation des purs par position, qui
     * rendrait au second appel le résultat calculé au premier — le compilateur documente
     * déjà ce piège pour les boucles. Elles ont leur propre story.
     *
     * <p>Les paramètres sont <b>obligatoires</b> ({@code required}) : une fonction qui
     * reçoit {@code null} là où elle attend une entité tomberait en faute au milieu de son
     * corps, en nommant un nœud que l'auteur n'a pas écrit. Le diagnostic doit tomber sur
     * l'appel.
     */
    /**
     * Le pin littéral qui nomme la fonction, présent sur les <b>trois</b> nœuds.
     *
     * <p>Il fait partie de la forme et pas seulement du type enregistré : le validateur
     * confronte chaque littéral d'un nœud à sa forme, et une forme dérivée qui l'oublierait
     * refuserait le littéral qui la désigne — un nœud qui se rendrait lui-même invalide.
     */
    private static NodeShape.PinDef namePin() {
        return new NodeShape.PinDef(FuncNodes.FUNCTION_PIN, PinKind.DATA,
                fr.blueprint.api.pin.PinTypes.STRING, false);
    }

    private static NodeShape shapeOf(List<Param> inputs, List<Param> outputs) {
        List<NodeShape.PinDef> in = new ArrayList<>(inputs.size() + 2);
        in.add(namePin());
        in.add(new NodeShape.PinDef(EXEC_IN, PinKind.EXEC, fr.blueprint.api.pin.PinTypes.EXEC,
                false));
        for (Param p : inputs) {
            in.add(new NodeShape.PinDef(p.name(), PinKind.DATA, p.type(), true));
        }
        List<NodeShape.PinDef> out = new ArrayList<>(outputs.size() + 1);
        out.add(new NodeShape.PinDef(EXEC_OUT, PinKind.EXEC, fr.blueprint.api.pin.PinTypes.EXEC,
                false));
        for (Param p : outputs) {
            out.add(new NodeShape.PinDef(p.name(), PinKind.DATA, p.type(), false));
        }
        // Jamais un point d'entrée : une fonction s'appelle, elle ne se déclenche pas.
        // La permission d'un APPEL est neutre — c'est le corps qui porte la sienne, et
        // c'est lui que le validateur confronte au plafond du blueprint.
        return new NodeShape(in, out, false, Permission.SAFE);
    }

    /**
     * La forme du nœud d'<b>entrée</b> du corps : il rend les paramètres.
     *
     * <p>Miroir de {@link #callShape()} — ce que l'appel prend en entrée, l'entrée du corps
     * le rend en sortie. Calculée à la demande et non gardée : un corps n'a qu'un
     * {@code func/param}, là où un graphe peut porter vingt appels.
     */
    public NodeShape paramShape() {
        List<NodeShape.PinDef> out = new ArrayList<>(inputs.size() + 1);
        out.add(new NodeShape.PinDef(EXEC_OUT, PinKind.EXEC, fr.blueprint.api.pin.PinTypes.EXEC,
                false));
        for (Param p : inputs) {
            out.add(new NodeShape.PinDef(p.name(), PinKind.DATA, p.type(), false));
        }
        return new NodeShape(List.of(namePin()), out, false, Permission.SAFE);
    }

    /** La forme du nœud de <b>sortie</b> : il prend les résultats et rend la main. */
    public NodeShape resultShape() {
        List<NodeShape.PinDef> in = new ArrayList<>(outputs.size() + 2);
        in.add(namePin());
        in.add(new NodeShape.PinDef(EXEC_IN, PinKind.EXEC, fr.blueprint.api.pin.PinTypes.EXEC,
                false));
        for (Param p : outputs) {
            in.add(new NodeShape.PinDef(p.name(), PinKind.DATA, p.type(), true));
        }
        return new NodeShape(in, List.of(), false, Permission.SAFE);
    }

    public BlueprintFunction withName(String newName) {
        return new BlueprintFunction(newName, inputs, outputs, nodes, links, callShape);
    }

    /** Change la signature — donc la forme d'appel, qui est recalculée. */
    public BlueprintFunction withSignature(List<Param> newInputs, List<Param> newOutputs) {
        return new BlueprintFunction(name, newInputs, newOutputs, nodes, links, null);
    }

    public BlueprintFunction withBody(Map<UUID, Node> newNodes, Set<Link> newLinks) {
        return new BlueprintFunction(name, inputs, outputs, newNodes, newLinks, callShape);
    }

    /**
     * Les liens qui entrent dans un pin de ce corps.
     *
     * <p>Par balayage, sans l'index par nœud que porte {@link Blueprint} : un corps de
     * fonction compte quelques dizaines de nœuds là où un graphe en compte mille, et
     * l'index coûterait sa maintenance à chaque édition pour épargner un parcours qui
     * n'apparaît dans aucun profil. Si un corps devient assez gros pour que ça compte,
     * c'est l'index qu'il faudra, pas un cache.
     */
    public Set<Link> linksInto(UUID node, String pin) {
        Set<Link> out = new LinkedHashSet<>();
        for (Link link : links) {
            if (link.toNode().equals(node) && link.toPin().equals(pin)) {
                out.add(link);
            }
        }
        return out;
    }

    /** Les liens qui touchent ce nœud, d'un côté ou de l'autre. */
    public Set<Link> linksTouching(UUID node) {
        Set<Link> out = new LinkedHashSet<>();
        for (Link link : links) {
            if (link.fromNode().equals(node) || link.toNode().equals(node)) {
                out.add(link);
            }
        }
        return out;
    }

    /**
     * Égalité de <b>contenu</b>, corps compris.
     *
     * <p>L'{@code equals} généré du record ne suffit pas : un {@link Node} n'a pas
     * d'égalité de contenu, donc comparer deux corps par leurs tables comparerait des
     * identités — toujours faux après un aller-retour NBT, et donc une garantie de
     * préservation qui ne garantirait rien.
     */
    public boolean contentEquals(@org.jetbrains.annotations.Nullable BlueprintFunction other) {
        if (other == null || !name.equals(other.name)
                || !inputs.equals(other.inputs) || !outputs.equals(other.outputs)
                || !links.equals(other.links)
                || !nodes.keySet().equals(other.nodes.keySet())) {
            return false;
        }
        for (Node node : nodes.values()) {
            if (!node.contentEquals(other.nodes.get(node.uuid()))) {
                return false;
            }
        }
        return true;
    }

    /** Le paramètre d'entrée portant ce nom, ou {@code null}. */
    public @org.jetbrains.annotations.Nullable Param input(String pin) {
        return find(inputs, pin);
    }

    public @org.jetbrains.annotations.Nullable Param output(String pin) {
        return find(outputs, pin);
    }

    private static @org.jetbrains.annotations.Nullable Param find(List<Param> where, String pin) {
        for (Param p : where) {
            if (p.name().equals(pin)) {
                return p;
            }
        }
        return null;
    }
}
