package fr.blueprint.core.storage;

import com.mojang.serialization.Codec;
import fr.blueprint.core.graph.VarScope;
import fr.blueprint.core.vm.VarOwner;
import fr.blueprint.core.vm.VarStore;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les variables de blueprint dans la sauvegarde du monde.
 *
 * <p>Elles n'y étaient pas. {@link VarStore#inMemory()} servait la VM et les tests, et
 * {@code BlueprintMod.varsOf} le câblait tel quel : le prénom qu'un joueur venait de
 * choisir disparaissait au redémarrage du serveur, ce qui vide de son sens la portée
 * {@code PLAYER} — « persistante par joueur », dit sa déclaration.
 *
 * <h2>Ce qui se persiste, et ce qui ne se persiste pas</h2>
 *
 * <p>Une valeur de variable n'a pas de type à l'écriture : {@code StoreVar} porte une
 * portée, un nom et un slot, pas de {@code PinType}. L'encodage se fait donc sur le type
 * <b>Java</b> de la valeur, avec une étiquette pour le relire — chaînes, nombres,
 * booléens, listes et tables de ceux-là.
 *
 * <p>Le reste — une pile d'objets, une entité, un état de bloc dans une variable — n'est
 * <b>pas</b> écrit, et le journal le dit en nommant la variable. Deviner le type déclaré
 * en fouillant les blueprints aurait été possible, mais deux graphes peuvent déclarer le
 * même nom de variable joueur avec deux types différents : le devinement aurait alors
 * rendu la valeur d'un type à un graphe qui en attend un autre, ce qui est pire que de ne
 * rien rendre du tout.
 */
public final class VarStorage extends SavedData implements VarStore {

    public static final SavedDataType<VarStorage> TYPE = new SavedDataType<>(
            "blueprint_vars", VarStorage::new, codec(), null);

    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("blueprint-vars");

    private final Map<String, Object> world = new HashMap<>();
    private final Map<Identifier, Map<String, Object>> graph = new HashMap<>();
    private final Map<UUID, Map<String, Object>> player = new HashMap<>();

    // ------------------------------------------------------------------ le magasin

    private @Nullable Map<String, Object> bucket(VarScope scope, VarOwner owner,
                                                 boolean create) {
        return switch (scope) {
            case WORLD -> world;
            case GRAPH -> create
                    ? graph.computeIfAbsent(owner.blueprint(), k -> new HashMap<>())
                    : graph.get(owner.blueprint());
            case PLAYER -> create
                    ? player.computeIfAbsent(owner.player(), k -> new HashMap<>())
                    : player.get(owner.player());
            case LOCAL -> null;
        };
    }

    @Override
    public @Nullable Object get(VarScope scope, VarOwner owner, String name) {
        if (!VarStore.owns(scope, owner)) {
            return null;
        }
        Map<String, Object> bucket = bucket(scope, owner, false);
        return bucket == null ? null : bucket.get(name);
    }

    @Override
    public void set(VarScope scope, VarOwner owner, String name, @Nullable Object value) {
        if (!VarStore.owns(scope, owner)) {
            return;
        }
        Map<String, Object> bucket = bucket(scope, owner, true);
        if (bucket != null) {
            bucket.put(name, value);
        }
    }

    @Override
    public boolean isDirty() {
        // Comme BlueprintStorage : données vivantes, on laisse Minecraft écrire à chaque
        // sauvegarde du monde. Suivre les écritures une à une coûterait un drapeau posé
        // dans le chemin le plus chaud de la VM pour économiser une écriture toutes les
        // cinq minutes.
        return true;
    }

    // -------------------------------------------------------------- la sérialisation

    private static Codec<VarStorage> codec() {
        return CompoundTag.CODEC.xmap(VarStorage::fromTag, VarStorage::toTag);
    }

    private static VarStorage fromTag(CompoundTag root) {
        VarStorage storage = new VarStorage();
        readBucket(root.getCompoundOrEmpty("world"), storage.world);

        CompoundTag graphs = root.getCompoundOrEmpty("graph");
        for (String key : graphs.keySet()) {
            Identifier id = Identifier.tryParse(key);
            if (id == null) {
                continue;   // un identifiant illisible ne doit pas faire tomber le monde
            }
            Map<String, Object> bucket = new HashMap<>();
            readBucket(graphs.getCompoundOrEmpty(key), bucket);
            storage.graph.put(id, bucket);
        }

        CompoundTag players = root.getCompoundOrEmpty("player");
        for (String key : players.keySet()) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            Map<String, Object> bucket = new HashMap<>();
            readBucket(players.getCompoundOrEmpty(key), bucket);
            storage.player.put(uuid, bucket);
        }
        return storage;
    }

    private CompoundTag toTag() {
        CompoundTag root = new CompoundTag();
        root.put("world", writeBucket(world));

        CompoundTag graphs = new CompoundTag();
        graph.forEach((id, bucket) -> graphs.put(id.toString(), writeBucket(bucket)));
        root.put("graph", graphs);

        CompoundTag players = new CompoundTag();
        player.forEach((uuid, bucket) -> players.put(uuid.toString(), writeBucket(bucket)));
        root.put("player", players);
        return root;
    }

    private static void readBucket(CompoundTag tag, Map<String, Object> into) {
        for (String name : tag.keySet()) {
            Object value = decode(tag.get(name));
            if (value != null) {
                into.put(name, value);
            }
        }
    }

    private static CompoundTag writeBucket(Map<String, Object> bucket) {
        CompoundTag tag = new CompoundTag();
        bucket.forEach((name, value) -> {
            Tag encoded = encode(value);
            if (encoded != null) {
                tag.put(name, encoded);
            } else if (value != null) {
                LOGGER.warn("Variable « {} » non persistée : le type {} ne s'écrit pas dans "
                        + "la sauvegarde. Elle garde sa valeur jusqu'au redémarrage.",
                        name, value.getClass().getSimpleName());
            }
        });
        return tag;
    }

    /**
     * Une valeur, étiquetée par son type Java.
     *
     * <p>Le type est porté par un {@code CompoundTag} à deux champs plutôt que déduit du
     * {@code TagType} : un {@code DoubleTag} ne distingue pas un {@code double} d'un
     * {@code float}, et une liste vide ne dit rien de ce qu'elle contient. Relire une
     * variable {@code int} comme un {@code double} suffit à faire fauter un nœud qui
     * attend l'un ou l'autre.
     *
     * @return null si le type ne se persiste pas.
     */
    private static @Nullable Tag encode(@Nullable Object value) {
        CompoundTag tag = new CompoundTag();
        switch (value) {
            case null -> {
                return null;
            }
            case String s -> {
                tag.putString("t", "s");
                tag.put("v", StringTag.valueOf(s));
            }
            case Boolean b -> {
                tag.putString("t", "z");
                tag.put("v", ByteTag.valueOf(b));
            }
            case Integer i -> {
                tag.putString("t", "i");
                tag.put("v", IntTag.valueOf(i));
            }
            case Long l -> {
                tag.putString("t", "l");
                tag.put("v", LongTag.valueOf(l));
            }
            case Double d -> {
                tag.putString("t", "d");
                tag.put("v", DoubleTag.valueOf(d));
            }
            case Float f -> {
                tag.putString("t", "f");
                tag.put("v", DoubleTag.valueOf(f));
            }
            case List<?> list -> {
                ListTag items = new ListTag();
                for (Object element : list) {
                    Tag encoded = encode(element);
                    if (encoded == null) {
                        return null;   // une liste à moitié écrite serait un mensonge
                    }
                    items.add(encoded);
                }
                tag.putString("t", "L");
                tag.put("v", items);
            }
            case Map<?, ?> map -> {
                ListTag entries = new ListTag();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    Tag key = encode(entry.getKey());
                    Tag val = encode(entry.getValue());
                    if (key == null || val == null) {
                        return null;
                    }
                    CompoundTag pair = new CompoundTag();
                    pair.put("k", key);
                    pair.put("v", val);
                    entries.add(pair);
                }
                tag.putString("t", "M");
                tag.put("v", entries);
            }
            default -> {
                return null;
            }
        }
        return tag;
    }

    private static @Nullable Object decode(@Nullable Tag tag) {
        if (!(tag instanceof CompoundTag compound)) {
            return null;
        }
        String type = compound.getStringOr("t", "");
        Tag value = compound.get("v");
        if (value == null) {
            return null;
        }
        return switch (type) {
            case "s" -> value.asString().orElse(null);
            case "z" -> value.asBoolean().orElse(null);
            case "i" -> value.asInt().orElse(null);
            case "l" -> value.asLong().orElse(null);
            case "d" -> value.asDouble().orElse(null);
            case "f" -> value.asFloat().orElse(null);
            case "L" -> {
                if (!(value instanceof ListTag items)) {
                    yield null;
                }
                List<Object> list = new ArrayList<>(items.size());
                for (Tag item : items) {
                    Object element = decode(item);
                    if (element == null) {
                        yield null;
                    }
                    list.add(element);
                }
                yield List.copyOf(list);
            }
            case "M" -> {
                if (!(value instanceof ListTag entries)) {
                    yield null;
                }
                Map<Object, Object> map = new HashMap<>();
                for (Tag entry : entries) {
                    if (!(entry instanceof CompoundTag pair)) {
                        yield null;
                    }
                    Object key = decode(pair.get("k"));
                    Object val = decode(pair.get("v"));
                    if (key == null || val == null) {
                        yield null;
                    }
                    map.put(key, val);
                }
                yield Map.copyOf(map);
            }
            default -> null;
        };
    }
}
