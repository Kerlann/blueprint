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
 * booléens, listes et tables de ceux-là, plus les trois types de géométrie (vecteur,
 * position de bloc, direction), qui s'écrivent sans rien demander aux registres du jeu.
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

    /** Le MÊME rangement que le magasin mémoire : une seule règle de possession. */
    private final fr.blueprint.core.vm.VarBuckets buckets =
            new fr.blueprint.core.vm.VarBuckets();

    // ------------------------------------------------------------------ le magasin

    @Override
    public @Nullable Object get(VarScope scope, VarOwner owner, String name) {
        if (!VarStore.owns(scope, owner)) {
            return null;
        }
        Map<String, Object> bucket = buckets.of(scope, owner, false);
        return bucket == null ? null : bucket.get(name);
    }

    @Override
    public void set(VarScope scope, VarOwner owner, String name, @Nullable Object value) {
        if (!VarStore.owns(scope, owner)) {
            return;
        }
        Map<String, Object> bucket = buckets.of(scope, owner, true);
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
        readBucket(root.getCompoundOrEmpty("world"), storage.buckets.world());

        CompoundTag graphs = root.getCompoundOrEmpty("graph");
        for (String key : graphs.keySet()) {
            Identifier id = Identifier.tryParse(key);
            if (id == null) {
                continue;   // un identifiant illisible ne doit pas faire tomber le monde
            }
            Map<String, Object> bucket = new HashMap<>();
            readBucket(graphs.getCompoundOrEmpty(key), bucket);
            storage.buckets.graph().put(id, bucket);
        }

        CompoundTag shared = root.getCompoundOrEmpty("playerShared");
        for (String key : shared.keySet()) {
            UUID uuid = uuidOrNull(key);
            if (uuid == null) {
                continue;
            }
            Map<String, Object> bucket = new HashMap<>();
            readBucket(shared.getCompoundOrEmpty(key), bucket);
            storage.buckets.sharedPlayer().put(uuid, bucket);
        }

        // Par joueur, PUIS par blueprint. Un monde écrit avant cette imbrication a des
        // valeurs à plat sous « player » : elles ne se relisent pas, et les défauts
        // déclarés reprennent la main au premier lancement. C'est assumé — le format
        // datait du jour même, aucun serveur n'a pu s'en servir.
        CompoundTag players = root.getCompoundOrEmpty("player");
        for (String key : players.keySet()) {
            UUID uuid = uuidOrNull(key);
            if (uuid == null) {
                continue;
            }
            CompoundTag byBlueprint = players.getCompoundOrEmpty(key);
            for (String owner : byBlueprint.keySet()) {
                Identifier id = Identifier.tryParse(owner);
                if (id == null) {
                    continue;
                }
                Map<String, Object> bucket = new HashMap<>();
                readBucket(byBlueprint.getCompoundOrEmpty(owner), bucket);
                storage.buckets.player()
                        .computeIfAbsent(uuid, k -> new java.util.LinkedHashMap<>())
                        .put(id, bucket);
            }
        }
        return storage;
    }

    private static @Nullable UUID uuidOrNull(String key) {
        try {
            return UUID.fromString(key);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private CompoundTag toTag() {
        CompoundTag root = new CompoundTag();
        root.put("world", writeBucket(buckets.world()));

        CompoundTag graphs = new CompoundTag();
        buckets.graph().forEach((id, bucket) -> graphs.put(id.toString(), writeBucket(bucket)));
        root.put("graph", graphs);

        CompoundTag shared = new CompoundTag();
        buckets.sharedPlayer().forEach((uuid, bucket) ->
                shared.put(uuid.toString(), writeBucket(bucket)));
        root.put("playerShared", shared);

        CompoundTag players = new CompoundTag();
        buckets.player().forEach((uuid, byBlueprint) -> {
            CompoundTag owners = new CompoundTag();
            byBlueprint.forEach((id, bucket) -> owners.put(id.toString(), writeBucket(bucket)));
            players.put(uuid.toString(), owners);
        });
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
            // Les trois types de géométrie. Ils s'écrivent sans toucher aux registres du
            // jeu, ce qui les distingue d'un ItemStack ou d'un Component : le codec de
            // ceux-là exige un HolderLookup que ce format, volontairement plat, n'a pas.
            case net.minecraft.world.phys.Vec3 vec -> {
                ListTag coords = new ListTag();
                coords.add(DoubleTag.valueOf(vec.x));
                coords.add(DoubleTag.valueOf(vec.y));
                coords.add(DoubleTag.valueOf(vec.z));
                tag.putString("t", "v3");
                tag.put("v", coords);
            }
            // En long empaqueté, comme Minecraft le fait partout ailleurs : trois entiers
            // bornés dans une seule valeur, et la relecture est exacte.
            case net.minecraft.core.BlockPos pos -> {
                tag.putString("t", "bp");
                tag.put("v", LongTag.valueOf(pos.asLong()));
            }
            // Par son NOM, pas son ordinal : un ordinal lierait la sauvegarde à l'ordre de
            // déclaration d'une énumération de Minecraft, que rien ne nous promet stable.
            case net.minecraft.core.Direction dir -> {
                tag.putString("t", "dir");
                tag.put("v", StringTag.valueOf(dir.getSerializedName()));
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
            case "v3" -> {
                if (!(value instanceof ListTag coords) || coords.size() != 3) {
                    yield null;
                }
                Double x = coords.get(0).asDouble().orElse(null);
                Double y = coords.get(1).asDouble().orElse(null);
                Double z = coords.get(2).asDouble().orElse(null);
                yield x == null || y == null || z == null
                        ? null : new net.minecraft.world.phys.Vec3(x, y, z);
            }
            case "bp" -> value.asLong().map(net.minecraft.core.BlockPos::of).orElse(null);
            // Un nom inconnu — sauvegarde d'une version où la direction s'appelait
            // autrement — rend null, donc le défaut déclaré reprend la main. Mieux qu'un
            // NORTH arbitraire, qui se ferait passer pour une valeur choisie.
            case "dir" -> value.asString()
                    .map(net.minecraft.core.Direction::byName).orElse(null);
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
