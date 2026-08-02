package fr.blueprint.core.graph;

import fr.blueprint.api.pin.ParameterizedPinType;
import fr.blueprint.api.pin.PinType;
import fr.blueprint.api.pin.PinTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Encodage <b>structurel</b> des types de pins (reprise QA SER-001) : l'id seul ne
 * suffit pas — {@code list<int>} et {@code list<string>} partagent {@code blueprint:list}.
 * Un type de base s'encode par son id ; un paramétré par sa structure complète ; un
 * générique par son nom d'emplacement.
 *
 * <p>Le décodage repasse obligatoirement par les fabriques de {@link PinTypes}
 * (caches) et par le résolveur du registre : les instances retournées sont les mêmes
 * qu'au démarrage, la comparaison par identité reste valide.
 */
public final class PinTypeNbt {

    private PinTypeNbt() {
    }

    public static Tag encode(PinType type) {
        if (type instanceof ParameterizedPinType p) {
            CompoundTag tag = new CompoundTag();
            tag.putString("container",
                    p.container() == ParameterizedPinType.Container.LIST ? "list" : "map");
            ListTag args = new ListTag();
            for (PinType arg : p.args()) {
                args.add(encode(arg));
            }
            tag.put("args", args);
            return tag;
        }
        if (type.isGeneric()) {
            CompoundTag tag = new CompoundTag();
            tag.putString("generic", type.genericName());
            return tag;
        }
        return StringTag.valueOf(type.id().toString());
    }

    /** Null si le type ne peut pas être résolu (mod retiré) — l'appelant préserve alors le NBT brut. */
    public static @Nullable PinType decode(@Nullable Tag tag, Function<Identifier, PinType> resolver) {
        if (tag instanceof StringTag) {
            Identifier id = Identifier.tryParse(tag.asString().orElse(""));
            return id == null ? null : resolver.apply(id);
        }
        if (!(tag instanceof CompoundTag compound)) {
            return null;
        }
        String generic = compound.getStringOr("generic", "");
        if (!generic.isEmpty()) {
            return PinTypes.generic(generic);
        }
        String container = compound.getStringOr("container", "");
        Tag argsTag = compound.get("args");
        if (!(argsTag instanceof ListTag argsList)) {
            return null;
        }
        List<PinType> args = new ArrayList<>(argsList.size());
        for (Tag argTag : argsList) {
            PinType arg = decode(argTag, resolver);
            if (arg == null) {
                return null;
            }
            args.add(arg);
        }
        return switch (container) {
            case "list" -> args.size() == 1 ? PinTypes.listOf(args.get(0)) : null;
            case "map" -> args.size() == 2 ? PinTypes.mapOf(args.get(0), args.get(1)) : null;
            default -> null;
        };
    }
}
