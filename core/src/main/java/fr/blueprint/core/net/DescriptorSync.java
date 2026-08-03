package fr.blueprint.core.net;

import fr.blueprint.api.pin.PinType;
import fr.blueprint.core.BlueprintMod;
import fr.blueprint.core.registry.NodeDescriptor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * Sérialisation des descripteurs pour la synchro registre (story 6.2) : la liste
 * complète part en NBT compressé (gzip), découpée en fragments bornés — testée avec
 * 5 000 descripteurs. Pur : aucune dépendance réseau, les paquets ne portent que
 * des octets.
 */
public final class DescriptorSync {

    /** Taille maximale d'un fragment (les paquets custom plafonnent à 32 Kio). */
    public static final int CHUNK_BYTES = 28_000;

    private DescriptorSync() {
    }

    /** Tous les descripteurs → octets compressés. */
    public static byte[] toBytes(Collection<NodeDescriptor> descriptors) {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        for (NodeDescriptor descriptor : descriptors) {
            list.add(descriptor.encode());
        }
        root.put("descriptors", list);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NbtIo.writeCompressed(root, bytes);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Sérialisation des descripteurs impossible", e);
        }
    }

    /** Découpe en fragments ≤ {@link #CHUNK_BYTES}. */
    public static List<byte[]> chunks(byte[] full) {
        List<byte[]> out = new ArrayList<>();
        for (int from = 0; from < full.length; from += CHUNK_BYTES) {
            int to = Math.min(full.length, from + CHUNK_BYTES);
            byte[] chunk = new byte[to - from];
            System.arraycopy(full, from, chunk, 0, chunk.length);
            out.add(chunk);
        }
        return out.isEmpty() ? List.of(new byte[0]) : out;
    }

    /** Réassemble les fragments dans l'ordre. */
    public static byte[] reassemble(List<byte[]> chunks) {
        int total = chunks.stream().mapToInt(c -> c.length).sum();
        byte[] full = new byte[total];
        int at = 0;
        for (byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, full, at, chunk.length);
            at += chunk.length;
        }
        return full;
    }

    /**
     * Octets → descripteurs. Un descripteur indéchiffrable (type de pin d'un mod
     * absent côté client) est sauté avec un avertissement — jamais de crash (AC3).
     */
    public static List<NodeDescriptor> fromBytes(byte[] bytes,
                                                 Function<Identifier, PinType> types) {
        List<NodeDescriptor> out = new ArrayList<>();
        try {
            CompoundTag root = NbtIo.readCompressed(
                    new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
            Tag listTag = root.get("descriptors");
            if (!(listTag instanceof ListTag list)) {
                return out;
            }
            for (Tag entry : list) {
                if (!(entry instanceof CompoundTag tag)) {
                    continue;
                }
                NodeDescriptor descriptor = NodeDescriptor.decode(tag, types);
                if (descriptor != null) {
                    out.add(descriptor);
                } else {
                    BlueprintMod.LOGGER.warn("Descripteur reçu indéchiffrable : {}",
                            tag.getStringOr("id", "?"));
                }
            }
        } catch (IOException | RuntimeException e) {
            BlueprintMod.LOGGER.error("Flux de descripteurs illisible", e);
        }
        return out;
    }
}
