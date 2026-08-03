package fr.blueprint.core.net;

import fr.blueprint.core.BlueprintMod;
import fr.blueprint.core.graph.ScreenNbt;
import fr.blueprint.core.graph.screen.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Le transport d'un écran (story 10.3). Le pendant de {@link GraphSync}, avec des
 * bornes bien plus serrées : un écran plafonne à 128 éléments, là où un graphe peut
 * porter mille nœuds.
 *
 * <p>Le serveur envoie la <b>description</b> de l'écran, pas des ordres de dessin. Cela
 * divise le trafic par le nombre d'images, permet au client de réagir au
 * redimensionnement de la fenêtre sans aller-retour, et surtout garde <b>une seule</b>
 * définition d'un écran — celle du modèle (10.1).
 */
public final class ScreenSync {

    /**
     * Borne du flux compressé. 128 éléments richement stylés tiennent très en dessous ;
     * au-delà, c'est un abus, pas un menu.
     */
    public static final int MAX_BYTES = 64 * 1024;

    /** Borne du NBT décompressé : un flux minuscule ne doit pas pouvoir gonfler. */
    private static final long MAX_INFLATED = 1L << 20;

    private ScreenSync() {
    }

    public static byte[] toBytes(Screen screen) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NbtIo.writeCompressed(ScreenNbt.encodeOne(screen), bytes);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Sérialisation de l'écran impossible", e);
        }
    }

    /** Null si le flux dépasse la borne, ne se décompresse pas, ou ne décode pas. */
    public static @Nullable Screen fromBytes(byte[] bytes) {
        if (bytes.length == 0 || bytes.length > MAX_BYTES) {
            BlueprintMod.LOGGER.warn("Flux d'écran refusé : {} octets", bytes.length);
            return null;
        }
        try {
            CompoundTag tag = NbtIo.readCompressed(new ByteArrayInputStream(bytes),
                    NbtAccounter.create(MAX_INFLATED));
            return ScreenNbt.decodeOne(tag);
        } catch (IOException | RuntimeException e) {
            BlueprintMod.LOGGER.warn("Flux d'écran illisible", e);
            return null;
        }
    }
}
