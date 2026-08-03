package fr.blueprint.core.registry;

import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinKind;
import fr.blueprint.api.pin.PinType;
import fr.blueprint.core.graph.PinTypeNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Forme transmissible d'un {@link NodeType} : tout ce qu'il faut au client pour
 * afficher et câbler le nœud — et rien d'autre. Aucune référence à {@code NodeAction}
 * ni à une classe du mod fournisseur (AC4, principe P1) : un joueur sans le mod voit
 * le nœud comme les autres.
 */
public record NodeDescriptor(Identifier id, String category, String titleKey, String descKey,
                             List<PinDescriptor> inputs, List<PinDescriptor> outputs,
                             boolean pure, Permission permission, int fuelCost,
                             boolean deterministic, boolean entryPoint) {

    /** Compatibilité : ancien constructeur sans {@code entryPoint} (6.2). */
    public NodeDescriptor(Identifier id, String category, String titleKey, String descKey,
                          List<PinDescriptor> inputs, List<PinDescriptor> outputs,
                          boolean pure, Permission permission, int fuelCost,
                          boolean deterministic) {
        this(id, category, titleKey, descKey, inputs, outputs, pure, permission,
                fuelCost, deterministic, false);
    }

    public record PinDescriptor(String name, PinKind kind, PinType type,
                                @Nullable LiteralValue defaultValue) {
    }

    public NodeDescriptor {
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
    }

    /** Projette un type de nœud en descripteur — l'action reste derrière. */
    public static NodeDescriptor of(NodeType type) {
        return new NodeDescriptor(type.id(), type.category().id(), type.titleKey(), type.descKey(),
                type.inputs().stream().map(NodeDescriptor::pin).toList(),
                type.outputs().stream().map(NodeDescriptor::pin).toList(),
                type.pure(), type.permission(), type.fuelCost(), type.deterministic(),
                type.entryPoint());
    }

    private static PinDescriptor pin(NodeType.PinSpec spec) {
        return new PinDescriptor(spec.name(), spec.kind(), spec.type(), spec.defaultValue());
    }

    // ------------------------------------------------------------------ NBT

    public CompoundTag encode() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id.toString());
        tag.putString("category", category);
        tag.putString("title", titleKey);
        tag.putString("desc", descKey);
        tag.put("inputs", encodePins(inputs));
        tag.put("outputs", encodePins(outputs));
        tag.putBoolean("pure", pure);
        tag.putString("permission", permission.name());
        tag.putInt("fuel", fuelCost);
        tag.putBoolean("deterministic", deterministic);
        tag.putBoolean("entry", entryPoint);
        return tag;
    }

    private static ListTag encodePins(List<PinDescriptor> pins) {
        ListTag list = new ListTag();
        for (PinDescriptor pin : pins) {
            CompoundTag p = new CompoundTag();
            p.putString("name", pin.name());
            p.putString("kind", pin.kind().name());
            p.put("type", PinTypeNbt.encode(pin.type()));
            if (pin.defaultValue() != null) {
                p.put("default", pin.defaultValue().encode(NbtOps.INSTANCE).getOrThrow(
                        message -> new IllegalStateException(
                                "Défaut inencodable du pin « " + pin.name() + " » : " + message)));
            }
            list.add(p);
        }
        return list;
    }

    /** Null si un champ ou un type ne se résout pas — jamais un descripteur partiel silencieux. */
    public static @Nullable NodeDescriptor decode(CompoundTag tag, Function<Identifier, PinType> types) {
        Identifier id = Identifier.tryParse(tag.getStringOr("id", ""));
        if (id == null) {
            return null;
        }
        List<PinDescriptor> inputs = decodePins(tag.get("inputs"), types);
        List<PinDescriptor> outputs = decodePins(tag.get("outputs"), types);
        if (inputs == null || outputs == null) {
            return null;
        }
        Permission permission;
        try {
            permission = Permission.valueOf(tag.getStringOr("permission", ""));
        } catch (IllegalArgumentException e) {
            return null;
        }
        return new NodeDescriptor(id,
                tag.getStringOr("category", "misc"),
                tag.getStringOr("title", ""),
                tag.getStringOr("desc", ""),
                inputs, outputs,
                tag.getBooleanOr("pure", false),
                permission,
                tag.getIntOr("fuel", 1),
                tag.getBooleanOr("deterministic", true),
                tag.getBooleanOr("entry", false));
    }

    private static @Nullable List<PinDescriptor> decodePins(@Nullable Tag pinsTag,
                                                            Function<Identifier, PinType> types) {
        if (!(pinsTag instanceof ListTag list)) {
            return List.of();
        }
        List<PinDescriptor> pins = new ArrayList<>(list.size());
        for (Tag entry : list) {
            if (!(entry instanceof CompoundTag p)) {
                return null;
            }
            PinType type = PinTypeNbt.decode(p.get("type"), types);
            if (type == null) {
                return null;
            }
            PinKind kind;
            try {
                kind = PinKind.valueOf(p.getStringOr("kind", ""));
            } catch (IllegalArgumentException e) {
                return null;
            }
            LiteralValue defaultValue = null;
            Tag defaultTag = p.get("default");
            if (defaultTag != null) {
                defaultValue = LiteralValue.decode(type, NbtOps.INSTANCE, defaultTag)
                        .result().orElse(null);
                if (defaultValue == null) {
                    return null;
                }
            }
            pins.add(new PinDescriptor(p.getStringOr("name", ""), kind, type, defaultValue));
        }
        return pins;
    }

    // ------------------------------------------------------------------ hash

    /**
     * Forme canonique pour le hash de registre : champ par champ dans l'ordre déclaré —
     * jamais {@code CompoundTag.toString()}, dont l'ordre d'itération n'est pas garanti
     * entre versions de JVM.
     */
    public String canonical() {
        StringBuilder sb = new StringBuilder(128);
        sb.append(id).append('|').append(category).append('|').append(titleKey).append('|')
                .append(descKey).append('|').append(pure).append('|').append(permission)
                .append('|').append(fuelCost).append('|').append(deterministic)
                .append('|').append(entryPoint);
        appendPins(sb, inputs);
        appendPins(sb, outputs);
        return sb.toString();
    }

    private static void appendPins(StringBuilder sb, List<PinDescriptor> pins) {
        sb.append("[");
        for (PinDescriptor pin : pins) {
            sb.append(pin.name()).append(':').append(pin.kind()).append(':')
                    .append(pin.type().toString()).append(':')
                    .append(pin.defaultValue() == null ? "" : pin.defaultValue().value())
                    .append(';');
        }
        sb.append("]");
    }
}
