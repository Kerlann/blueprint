package fr.blueprint.core.vm;

import fr.blueprint.core.BlueprintMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistance d'une exécution suspendue (story 3.4). Les valeurs sont encodées par
 * famille ; les références vivantes deviennent des marqueurs UUID résolus au
 * chargement par un {@link RefResolver}. Deux règles :
 *
 * <ul>
 * <li>À la capture, une valeur non sérialisable → {@code encode} retourne null,
 *     l'exécution n'est pas persistée (avertissement journalisé) — jamais de
 *     sauvegarde partielle.</li>
 * <li>Au chargement, une référence irrésoluble → {@code decode} retourne null,
 *     l'appelant annule proprement — jamais de reprise avec un trou (AC3).</li>
 * </ul>
 */
public final class ExecutionNbt {

    private static final class Unsupported extends RuntimeException {
        Unsupported(String message) {
            super(message);
        }
    }

    private static final class Unresolved extends RuntimeException {
        Unresolved(String message) {
            super(message);
        }
    }

    private ExecutionNbt() {
    }

    public static @Nullable CompoundTag encode(SuspendedExecution suspended) {
        try {
            CompoundTag root = new CompoundTag();
            root.putString("blueprint", suspended.blueprintId().toString());
            root.putInt("revision", suspended.revision());
            if (suspended.entryNode() != null) {
                root.putString("entry", suspended.entryNode().toString());
            }
            root.putInt("ticks", suspended.remainingTicks());
            root.putString("event", suspended.eventId().toString());
            ExecutionState state = suspended.state();
            root.putInt("pc", state.pc());
            root.putInt("slotCount", state.slotCount());
            ListTag slots = new ListTag();
            for (Object slot : state.slots()) {
                slots.add(encodeValue(slot));
            }
            root.put("slots", slots);
            root.put("locals", encodeNamed(state.locals()));
            // Pile de sous-chaînes (7.1b) : une suspension au milieu d'une boucle
            // structurée doit reprendre au bon niveau après redémarrage.
            root.putIntArray("frames", state.frames().stream().mapToInt(Integer::intValue).toArray());
            root.put("trigger", encodeNamed(suspended.triggerValues()));
            return root;
        } catch (Unsupported e) {
            BlueprintMod.LOGGER.warn(
                    "Exécution suspendue de « {} » non persistable : {} — elle sera perdue au redémarrage",
                    suspended.blueprintId(), e.getMessage());
            return null;
        }
    }

    public static @Nullable SuspendedExecution decode(CompoundTag root, RefResolver resolver) {
        try {
            // tryParse("") rend « minecraft: » (chemin vide valide) — l'absence de champ
            // doit être un refus explicite, pas un identifiant fantoche.
            String rawBlueprint = root.getStringOr("blueprint", "");
            String rawEvent = root.getStringOr("event", "");
            if (rawBlueprint.isEmpty() || rawEvent.isEmpty()) {
                return null;
            }
            Identifier blueprintId = Identifier.tryParse(rawBlueprint);
            Identifier eventId = Identifier.tryParse(rawEvent);
            if (blueprintId == null || eventId == null) {
                return null;
            }
            ExecutionState state = ExecutionState.ofSize(root.getIntOr("slotCount", 0));
            state.setPc(root.getIntOr("pc", 0));
            Tag slotsTag = root.get("slots");
            if (slotsTag instanceof ListTag slots) {
                if (slots.size() != state.slotCount()) {
                    return null;
                }
                for (int i = 0; i < slots.size(); i++) {
                    state.slots()[i] = decodeValue(slots.get(i), resolver);
                }
            }
            state.locals().putAll(decodeNamed(root.get("locals"), resolver));
            for (int frame : root.getIntArray("frames").orElse(new int[0])) {
                state.frames().addLast(frame);
            }
            Map<String, Object> trigger = decodeNamed(root.get("trigger"), resolver);
            UUID entryNode = null;
            String entry = root.getStringOr("entry", "");
            if (!entry.isEmpty()) {
                entryNode = UUID.fromString(entry);
            }
            return new SuspendedExecution(blueprintId, root.getIntOr("revision", 0), entryNode,
                    root.getIntOr("ticks", 0), state, eventId, trigger);
        } catch (Unresolved e) {
            BlueprintMod.LOGGER.info(
                    "Exécution suspendue annulée proprement : {}", e.getMessage());
            return null;
        } catch (IllegalArgumentException e) {
            // Correction QA PERSIST-002 : UUID/enum malformé = donnée corrompue → null,
            // jamais une exception qui remonterait dans la boucle de restauration (AC2).
            BlueprintMod.LOGGER.warn("Exécution suspendue malformée — annulée", e);
            return null;
        }
    }

    // ------------------------------------------------------------------ valeurs

    private static ListTag encodeNamed(Map<String, Object> values) {
        ListTag list = new ListTag();
        values.forEach((name, value) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("n", name);
            entry.put("v", encodeValue(value));
            list.add(entry);
        });
        return list;
    }

    private static Map<String, Object> decodeNamed(@Nullable Tag tag, RefResolver resolver) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (tag instanceof ListTag list) {
            for (Tag entry : list) {
                if (entry instanceof CompoundTag e) {
                    values.put(e.getStringOr("n", ""), decodeValue(e.get("v"), resolver));
                }
            }
        }
        return values;
    }

    static CompoundTag encodeValue(@Nullable Object value) {
        CompoundTag tag = new CompoundTag();
        switch (value) {
            case null -> tag.putString("k", "0");
            case Boolean b -> {
                tag.putString("k", "b");
                tag.putBoolean("v", b);
            }
            case Integer i -> {
                tag.putString("k", "i");
                tag.putInt("v", i);
            }
            case Long l -> {
                tag.putString("k", "l");
                tag.putLong("v", l);
            }
            case Double d -> {
                tag.putString("k", "d");
                tag.putDouble("v", d);
            }
            case String s -> {
                tag.putString("k", "s");
                tag.putString("v", s);
            }
            case Identifier id -> {
                tag.putString("k", "id");
                tag.putString("v", id.toString());
            }
            case BlockPos pos -> {
                tag.putString("k", "bp");
                tag.putInt("x", pos.getX());
                tag.putInt("y", pos.getY());
                tag.putInt("z", pos.getZ());
            }
            case Vec3 vec -> {
                tag.putString("k", "v3");
                tag.putDouble("x", vec.x());
                tag.putDouble("y", vec.y());
                tag.putDouble("z", vec.z());
            }
            case Direction dir -> {
                tag.putString("k", "dir");
                tag.putString("v", dir.name());
            }
            case UUID uuid -> {
                tag.putString("k", "uuid");
                tag.putString("v", uuid.toString());
            }
            // ServerPlayer AVANT Entity : un joueur est aussi une entité.
            case ServerPlayer player -> {
                tag.putString("k", "player");
                tag.putString("v", player.getUUID().toString());
            }
            case Entity entity -> {
                tag.putString("k", "ent");
                tag.putString("v", entity.getUUID().toString());
            }
            case List<?> list -> {
                tag.putString("k", "ls");
                ListTag items = new ListTag();
                for (Object item : list) {
                    items.add(encodeValue(item));
                }
                tag.put("items", items);
            }
            default -> throw new Unsupported("valeur de type " + value.getClass().getName());
        }
        return tag;
    }

    static @Nullable Object decodeValue(@Nullable Tag rawTag, RefResolver resolver) {
        if (!(rawTag instanceof CompoundTag tag)) {
            return null;
        }
        return switch (tag.getStringOr("k", "0")) {
            case "0" -> null;
            case "b" -> tag.getBooleanOr("v", false);
            case "i" -> tag.getIntOr("v", 0);
            case "l" -> tag.getLongOr("v", 0L);
            case "d" -> tag.getDoubleOr("v", 0.0);
            case "s" -> tag.getStringOr("v", "");
            case "id" -> Identifier.tryParse(tag.getStringOr("v", ""));
            case "bp" -> new BlockPos(tag.getIntOr("x", 0), tag.getIntOr("y", 0), tag.getIntOr("z", 0));
            case "v3" -> new Vec3(tag.getDoubleOr("x", 0), tag.getDoubleOr("y", 0), tag.getDoubleOr("z", 0));
            case "dir" -> Direction.valueOf(tag.getStringOr("v", "NORTH"));
            case "uuid" -> UUID.fromString(tag.getStringOr("v", ""));
            case "player" -> {
                UUID id = UUID.fromString(tag.getStringOr("v", ""));
                ServerPlayer player = resolver.player(id);
                if (player == null) {
                    throw new Unresolved("joueur " + id + " absent");
                }
                yield player;
            }
            case "ent" -> {
                UUID id = UUID.fromString(tag.getStringOr("v", ""));
                Entity entity = resolver.entity(id);
                if (entity == null) {
                    throw new Unresolved("entité " + id + " absente");
                }
                yield entity;
            }
            case "ls" -> {
                List<Object> items = new ArrayList<>();
                if (tag.get("items") instanceof ListTag list) {
                    for (Tag item : list) {
                        items.add(decodeValue(item, resolver));
                    }
                }
                yield items;
            }
            default -> null;
        };
    }
}
