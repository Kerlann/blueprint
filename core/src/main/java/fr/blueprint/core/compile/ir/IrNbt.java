package fr.blueprint.core.compile.ir;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinType;
import fr.blueprint.core.graph.PinTypeNbt;
import fr.blueprint.core.graph.VarScope;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Sérialisation de l'IR pour le cache entre démarrages (story 3.1, AC3).
 * Les cibles exec sont encodées en <b>liste ordonnée</b>, jamais en compound :
 * l'ordre de déclaration est un contrat (branche par défaut de la VM).
 * Un décodage irrésoluble retourne null — le cache se jette, on recompile.
 */
public final class IrNbt {

    private IrNbt() {
    }

    public static CompoundTag encode(Ir ir) {
        CompoundTag root = new CompoundTag();
        root.putString("blueprint", ir.blueprintId().toString());
        root.putInt("revision", ir.revision());
        if (ir.entryNode() != null) {
            root.putString("entry", ir.entryNode().toString());
        }
        root.putInt("slots", ir.slotCount());
        ListTag instructions = new ListTag();
        for (Instruction ins : ir.instructions()) {
            instructions.add(encodeInstruction(ins));
        }
        root.put("instructions", instructions);
        return root;
    }

    private static CompoundTag encodeInstruction(Instruction ins) {
        CompoundTag tag = new CompoundTag();
        if (ins.source() != null) {
            tag.putString("src", ins.source().toString());
        }
        switch (ins) {
            case Instruction.Const c -> {
                tag.putString("op", "const");
                tag.putInt("slot", c.slot());
                tag.put("type", PinTypeNbt.encode(c.value().type()));
                tag.put("value", c.value().encode(NbtOps.INSTANCE).getOrThrow(
                        message -> new IllegalStateException("Const inencodable : " + message)));
            }
            case Instruction.Call call -> {
                tag.putString("op", "call");
                tag.putString("type", call.type().toString());
                tag.put("in", encodeBindings(call.inputs()));
                tag.put("out", encodeBindings(call.outputs()));
                ListTag exec = new ListTag();
                call.execTargets().forEach((pin, target) -> {
                    CompoundTag e = new CompoundTag();
                    e.putString("pin", pin);
                    e.putInt("target", target);
                    exec.add(e);
                });
                tag.put("exec", exec);
                tag.putInt("fuel", call.fuelCost());
                tag.putBoolean("pure", call.pure());
            }
            case Instruction.Jmp j -> {
                tag.putString("op", "jmp");
                tag.putInt("target", j.target());
            }
            case Instruction.JmpIf j -> {
                tag.putString("op", "jmp_if");
                tag.putInt("cond", j.conditionSlot());
                tag.putInt("else", j.elseTarget());
            }
            case Instruction.LoadVar l -> {
                tag.putString("op", "load_var");
                tag.putString("scope", l.scope().name());
                tag.putString("name", l.name());
                tag.putInt("slot", l.slot());
            }
            case Instruction.StoreVar s -> {
                tag.putString("op", "store_var");
                tag.putString("scope", s.scope().name());
                tag.putString("name", s.name());
                tag.putInt("slot", s.slot());
            }
            case Instruction.CallSub c -> {
                tag.putString("op", "call_sub");
                tag.putInt("target", c.target());
            }
            case Instruction.Yield y -> {
                tag.putString("op", "yield");
                tag.putInt("ticks", y.ticks());
            }
            case Instruction.Return r -> tag.putString("op", "return");
        }
        return tag;
    }

    private static ListTag encodeBindings(List<Instruction.PinBinding> bindings) {
        ListTag list = new ListTag();
        for (Instruction.PinBinding binding : bindings) {
            CompoundTag b = new CompoundTag();
            b.putString("pin", binding.pin());
            b.putInt("slot", binding.slot());
            list.add(b);
        }
        return list;
    }

    public static @Nullable Ir decode(CompoundTag root, Function<Identifier, PinType> types) {
        Identifier blueprintId = Identifier.tryParse(root.getStringOr("blueprint", ""));
        if (blueprintId == null) {
            return null;
        }
        List<Instruction> instructions = new ArrayList<>();
        Tag instructionsTag = root.get("instructions");
        if (!(instructionsTag instanceof ListTag list)) {
            return null;
        }
        for (Tag entry : list) {
            if (!(entry instanceof CompoundTag tag)) {
                return null;
            }
            Instruction ins = decodeInstruction(tag, types);
            if (ins == null) {
                return null;
            }
            instructions.add(ins);
        }
        UUID entryNode = null;
        String entry = root.getStringOr("entry", "");
        if (!entry.isEmpty()) {
            try {
                entryNode = UUID.fromString(entry);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return new Ir(blueprintId, root.getIntOr("revision", 0), entryNode,
                instructions, root.getIntOr("slots", 0));
    }

    private static @Nullable Instruction decodeInstruction(CompoundTag tag,
                                                           Function<Identifier, PinType> types) {
        UUID source = null;
        String src = tag.getStringOr("src", "");
        if (!src.isEmpty()) {
            try {
                source = UUID.fromString(src);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return switch (tag.getStringOr("op", "")) {
            case "const" -> {
                PinType type = PinTypeNbt.decode(tag.get("type"), types);
                if (type == null) {
                    yield null;
                }
                LiteralValue value = LiteralValue.decode(type, NbtOps.INSTANCE, tag.get("value"))
                        .result().orElse(null);
                yield value == null ? null : new Instruction.Const(tag.getIntOr("slot", 0), value, source);
            }
            case "call" -> {
                Identifier type = Identifier.tryParse(tag.getStringOr("type", ""));
                List<Instruction.PinBinding> in = decodeBindings(tag.get("in"));
                List<Instruction.PinBinding> out = decodeBindings(tag.get("out"));
                if (type == null || in == null || out == null) {
                    yield null;
                }
                Map<String, Integer> exec = new LinkedHashMap<>();
                if (tag.get("exec") instanceof ListTag execList) {
                    for (Tag e : execList) {
                        if (!(e instanceof CompoundTag ec)) {
                            yield null;
                        }
                        exec.put(ec.getStringOr("pin", ""), ec.getIntOr("target", -1));
                    }
                }
                yield new Instruction.Call(type, in, out, exec,
                        tag.getIntOr("fuel", 1), tag.getBooleanOr("pure", false), source);
            }
            case "jmp" -> new Instruction.Jmp(tag.getIntOr("target", -1), source);
            case "jmp_if" -> new Instruction.JmpIf(tag.getIntOr("cond", 0), tag.getIntOr("else", -1), source);
            case "load_var" -> new Instruction.LoadVar(scope(tag), tag.getStringOr("name", ""),
                    tag.getIntOr("slot", 0), source);
            case "store_var" -> new Instruction.StoreVar(scope(tag), tag.getStringOr("name", ""),
                    tag.getIntOr("slot", 0), source);
            case "call_sub" -> new Instruction.CallSub(tag.getIntOr("target", -1), source);
            case "yield" -> new Instruction.Yield(tag.getIntOr("ticks", 1), source);
            case "return" -> new Instruction.Return(source);
            default -> null;
        };
    }

    private static @Nullable List<Instruction.PinBinding> decodeBindings(@Nullable Tag tag) {
        if (!(tag instanceof ListTag list)) {
            return List.of();
        }
        List<Instruction.PinBinding> bindings = new ArrayList<>(list.size());
        for (Tag entry : list) {
            if (!(entry instanceof CompoundTag b)) {
                return null;
            }
            bindings.add(new Instruction.PinBinding(b.getStringOr("pin", ""), b.getIntOr("slot", 0)));
        }
        return bindings;
    }

    private static VarScope scope(CompoundTag tag) {
        try {
            return VarScope.valueOf(tag.getStringOr("scope", ""));
        } catch (IllegalArgumentException e) {
            return VarScope.LOCAL;
        }
    }
}
