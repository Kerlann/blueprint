package fr.blueprint.core.net;

import fr.blueprint.api.pin.PinType;
import fr.blueprint.core.BlueprintMod;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.GraphNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.Function;

/**
 * Transport d'un graphe complet (story 6.3) : NBT compressé, borné en lecture.
 * Pur — aucune dépendance réseau, aucune décision de sécurité : le décodage se
 * contente de refuser proprement ce qui dépasse ou ne s'analyse pas.
 */
public final class GraphSync {

    /** Borne du flux compressé accepté (un graphe réaliste pèse quelques kio). */
    public static final int MAX_BYTES = 1 << 20;

    /** Borne du NBT décompressé — un flux minuscule ne doit pas pouvoir gonfler. */
    private static final long MAX_INFLATED = 16L << 20;

    private GraphSync() {
    }

    public static byte[] toBytes(Blueprint blueprint) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NbtIo.writeCompressed(GraphNbt.encode(blueprint), bytes);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Sérialisation du blueprint impossible", e);
        }
    }

    /** Null si le flux dépasse la borne, ne se décompresse pas, ou ne décode pas. */
    public static @Nullable Blueprint fromBytes(byte[] bytes,
                                                Function<Identifier, PinType> types) {
        if (bytes.length == 0 || bytes.length > MAX_BYTES) {
            BlueprintMod.LOGGER.warn("Flux de blueprint refusé : {} octets", bytes.length);
            return null;
        }
        try {
            CompoundTag tag = NbtIo.readCompressed(new ByteArrayInputStream(bytes),
                    NbtAccounter.create(MAX_INFLATED));
            return GraphNbt.decode(tag, types);
        } catch (IOException | RuntimeException e) {
            BlueprintMod.LOGGER.warn("Flux de blueprint illisible", e);
            return null;
        }
    }
}
