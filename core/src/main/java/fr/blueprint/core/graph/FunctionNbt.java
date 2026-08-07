package fr.blueprint.core.graph;

import fr.blueprint.api.pin.PinType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Sérialisation NBT des fonctions (story 20.1), sur le modèle de {@link ScreenNbt}.
 *
 * <p>Les nœuds d'un corps passent par <b>le même</b> encodeur que ceux du graphe principal :
 * un second encodage de nœud finirait par diverger, et la divergence se verrait comme un
 * littéral perdu au rechargement — la pire des pannes, parce qu'elle ne se remarque que
 * bien après.
 *
 * <p><b>Préservation.</b> Une fonction dont un paramètre porte un type irrésoluble — un mod
 * retiré — est conservée <b>telle qu'arrivée</b> et ré-émise à l'identique, comme les
 * variables et les écrans. Sans quoi désinstaller un mod effacerait une fonction entière et
 * tout ce qu'elle contient, sans un mot.
 */
public final class FunctionNbt {

    private FunctionNbt() {
    }

    public static ListTag encode(Blueprint bp) {
        ListTag out = new ListTag();
        for (BlueprintFunction function : bp.functions().values()) {
            out.add(encodeOne(function));
        }
        return out;
    }

    private static CompoundTag encodeOne(BlueprintFunction function) {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", function.name());
        tag.put("inputs", encodeParams(function.inputs()));
        tag.put("outputs", encodeParams(function.outputs()));

        ListTag nodes = new ListTag();
        for (Node node : function.nodes().values()) {
            nodes.add(GraphNbt.encodeNode(node));
        }
        tag.put("nodes", nodes);

        ListTag links = new ListTag();
        for (Link link : function.links()) {
            CompoundTag l = new CompoundTag();
            l.putString("from", link.fromNode().toString());
            l.putString("fromPin", link.fromPin());
            l.putString("to", link.toNode().toString());
            l.putString("toPin", link.toPin());
            links.add(l);
        }
        tag.put("links", links);
        return tag;
    }

    private static ListTag encodeParams(List<BlueprintFunction.Param> params) {
        ListTag out = new ListTag();
        for (BlueprintFunction.Param param : params) {
            CompoundTag tag = new CompoundTag();
            tag.putString("name", param.name());
            tag.put("type", PinTypeNbt.encode(param.type()));
            out.add(tag);
        }
        return out;
    }

    /**
     * Relit les fonctions. Celles qu'on ne sait pas décoder vont dans {@code preserved}.
     *
     * @param preserved reçoit les fonctions intactes — jamais jetées.
     */
    public static void decode(Blueprint bp, List<Tag> tags, ListTag preserved,
                              Function<Identifier, PinType> types) {
        for (Tag tag : tags) {
            if (!(tag instanceof CompoundTag c)) {
                continue;
            }
            BlueprintFunction function = decodeOne(c, types);
            if (function == null) {
                preserved.add(c.copy());
            } else {
                bp.putFunction(function);
            }
        }
    }

    private static @Nullable BlueprintFunction decodeOne(CompoundTag tag,
                                                         Function<Identifier, PinType> types) {
        String name = tag.getStringOr("name", "");
        if (name.isEmpty()) {
            return null;
        }
        List<BlueprintFunction.Param> inputs = decodeParams(tag, "inputs", types);
        List<BlueprintFunction.Param> outputs = decodeParams(tag, "outputs", types);
        if (inputs == null || outputs == null) {
            return null;   // un type irrésoluble : on préserve plutôt que d'amputer
        }

        // Un blueprint jetable sert de réceptacle : decodeNode sait poser un nœud dans un
        // Blueprint, et lui en donner un vrai ferait entrer les nœuds du corps dans la
        // réserve principale — exactement ce que le modèle sépare.
        Blueprint scratch = new Blueprint(Identifier.fromNamespaceAndPath("blueprint", "scratch"));
        for (Tag node : GraphNbt.list(tag, "nodes")) {
            if (node instanceof CompoundTag n) {
                GraphNbt.decodeNode(scratch, n, types);
            }
        }
        Map<UUID, Node> nodes = new LinkedHashMap<>(scratch.nodes());

        Set<Link> links = new LinkedHashSet<>();
        for (Tag link : GraphNbt.list(tag, "links")) {
            if (link instanceof CompoundTag l) {
                UUID from = GraphNbt.uuid(l.getStringOr("from", ""));
                UUID to = GraphNbt.uuid(l.getStringOr("to", ""));
                if (from != null && to != null) {
                    links.add(new Link(from, l.getStringOr("fromPin", ""),
                            to, l.getStringOr("toPin", "")));
                }
            }
        }
        return BlueprintFunction.of(name, inputs, outputs).withBody(nodes, links);
    }

    /** {@code null} si un seul type ne résout pas : la fonction entière est préservée. */
    private static @Nullable List<BlueprintFunction.Param> decodeParams(
            CompoundTag tag, String key, Function<Identifier, PinType> types) {
        List<BlueprintFunction.Param> out = new ArrayList<>();
        for (Tag entry : GraphNbt.list(tag, key)) {
            if (!(entry instanceof CompoundTag p)) {
                continue;
            }
            PinType type = PinTypeNbt.decode(p.get("type"), types::apply);
            if (type == null) {
                return null;
            }
            out.add(new BlueprintFunction.Param(p.getStringOr("name", ""), type));
        }
        return out;
    }
}
