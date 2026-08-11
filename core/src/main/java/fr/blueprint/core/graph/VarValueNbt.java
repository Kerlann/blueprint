package fr.blueprint.core.graph;

import fr.blueprint.api.pin.ParameterizedPinType;
import fr.blueprint.api.pin.PinType;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Une valeur de variable, <b>étiquetée par son type Java</b>.
 *
 * <p>Ce format vivait dans {@code VarStorage}, privé, pour la sauvegarde du monde. Il en
 * sort parce que la réplication (épic 21) a besoin d'exactement le même : les valeurs qui
 * voyagent vers un client sont celles qui survivent à un redémarrage, et il n'y a aucune
 * raison qu'un jour les deux ensembles diffèrent. Le rangement des variables avait déjà
 * vécu en double entre la mémoire et le disque, et {@code VarBuckets} existe pour avoir
 * fermé cette question ; celle-ci se ferme de la même façon.
 *
 * <h2>Pourquoi une étiquette, et pas le type du tag</h2>
 *
 * <p>Le type est porté par un {@code CompoundTag} à deux champs plutôt que déduit du
 * {@code TagType} : un {@code DoubleTag} ne distingue pas un {@code double} d'un
 * {@code float}, et une liste vide ne dit rien de ce qu'elle contient. Relire une variable
 * {@code int} comme un {@code double} suffit à faire fauter un nœud qui attend l'un ou
 * l'autre.
 *
 * <h2>Ce qui n'y entre pas</h2>
 *
 * <p>Une pile d'objets, un texte riche, un état de bloc : leurs codecs exigent un
 * {@code HolderLookup} que ce format, volontairement plat, n'a pas. Une référence vivante
 * — un joueur, une entité — n'a pas de valeur à écrire du tout. C'est une limite assumée et
 * non un oubli : elle est la même sur le disque et sur le fil, donc une variable qui ne
 * survit pas à un redémarrage ne prétend pas non plus arriver chez un client.
 */
public final class VarValueNbt {

    private VarValueNbt() {
    }

    /**
     * Les classes que ce format sait écrire — <b>la</b> liste, celle dont {@link #encode}
     * et {@link #carries(PinType)} tirent tous les deux leur réponse.
     *
     * <p>Deux listes auraient divergé, et la divergence se serait vue comme un drapeau
     * {@code @replicated} que le validateur accepte et que l'encodeur laisse tomber en
     * silence — soit exactement la panne que l'épic 21 répare.
     */
    private static final List<Class<?>> CARRIED = List.of(
            String.class, Boolean.class, Integer.class, Long.class, Double.class, Float.class,
            net.minecraft.world.phys.Vec3.class, net.minecraft.core.BlockPos.class,
            net.minecraft.core.Direction.class,
            List.class, Map.class);

    /** Ce format sait-il écrire une valeur de ce type de pin ? */
    public static boolean carries(PinType type) {
        // Une collection ne voyage que si son contenu voyage. Même raisonnement que
        // ParameterizedPinType.hasStreamCodec, et pour la même raison : le conteneur ne
        // dit rien de ce qu'il transporte.
        if (type instanceof ParameterizedPinType parameterized) {
            for (PinType arg : parameterized.args()) {
                if (!carries(arg)) {
                    return false;
                }
            }
            return true;
        }
        // Un joker rend Object.class, qui n'est pas dans la liste : refusé, et c'est juste
        // — on ne sait pas ce qu'il contiendra.
        return CARRIED.contains(type.javaType());
    }

    /**
     * Une valeur, étiquetée par son type Java.
     *
     * @return null si le type ne s'écrit pas dans ce format.
     */
    public static @Nullable Tag encode(@Nullable Object value) {
        return encode(value, 0);
    }

    /**
     * Profondeur au-delà de laquelle une valeur est <b>refusée</b>.
     *
     * <p>Et refusée, non tronquée : une collection écrite à moitié serait un mensonge, et
     * c'est déjà la règle du reste de cette classe.
     *
     * <p>Ce garde-fou manquait. {@code list/add} accepte d'ajouter une liste à une liste, donc
     * un graphe peut faire croître la profondeur d'un cran par appel — mille appels, mille
     * niveaux. La récursion tenait sur le disque, où elle ne s'exécute qu'à la sauvegarde du
     * monde ; depuis l'épic 21 elle tourne <b>dans le tick</b>, appelée par
     * {@code VarReplication.flush}, et un {@code StackOverflowError} y emporte le tick et la
     * sauvegarde avec lui — exactement ce que NFR4 interdit d'atteindre. {@code VarQuota}
     * bornait déjà sa propre récursion pour cette raison ; l'encodeur n'avait pas reçu le
     * même soin.
     */
    private static final int MAX_DEPTH = 16;

    private static @Nullable Tag encode(@Nullable Object value, int depth) {
        if (depth > MAX_DEPTH) {
            return null;
        }
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
                    Tag encoded = encode(element, depth + 1);
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
                    Tag key = encode(entry.getKey(), depth + 1);
                    Tag val = encode(entry.getValue(), depth + 1);
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

    /**
     * Relit une valeur écrite par {@link #encode}.
     *
     * <p>Rend {@code null} sur tout ce qu'elle ne comprend pas — étiquette inconnue, forme
     * abîmée, nom de direction disparu — plutôt que de lever. Sur le disque, cela laisse le
     * défaut déclaré reprendre la main ; sur le fil, cela évite qu'un paquet forgé fasse
     * tomber le client.
     */
    public static @Nullable Object decode(@Nullable Tag tag) {
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
