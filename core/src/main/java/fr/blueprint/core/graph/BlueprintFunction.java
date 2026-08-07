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
    private static NodeShape shapeOf(List<Param> inputs, List<Param> outputs) {
        List<NodeShape.PinDef> in = new ArrayList<>(inputs.size() + 1);
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
