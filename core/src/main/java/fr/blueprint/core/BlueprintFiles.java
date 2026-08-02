package fr.blueprint.core;

import fr.blueprint.api.pin.PinType;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.GraphNbt;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * Export/import d'un blueprint sur disque (story 4.4a) : NBT compressé, format de la
 * story 1.4 — préservation totale comprise. Le format texte BScript arrivera avec
 * l'épic 4 sur les mêmes fichiers {@code .bp}.
 */
public final class BlueprintFiles {

    private BlueprintFiles() {
    }

    /** Nom de fichier sûr dérivé d'un identifiant : {@code blueprint_demo.bp}. */
    public static String fileName(Identifier id) {
        return (id.getNamespace() + "_" + id.getPath()).replace('/', '_') + ".bp";
    }

    /** Écrit le blueprint ; retourne le chemin, ou null en cas d'échec (journalisé). */
    public static @Nullable Path export(Blueprint bp, Path exportsDir) {
        try {
            Files.createDirectories(exportsDir);
            Path file = exportsDir.resolve(fileName(bp.id()));
            NbtIo.writeCompressed(GraphNbt.encode(bp), file);
            return file;
        } catch (IOException e) {
            BlueprintMod.LOGGER.error("Export de « {} » impossible", bp.id(), e);
            return null;
        }
    }

    /** Lit {@code <nom>.bp} ; null si absent, illisible ou indécodable (journalisé). */
    public static @Nullable Blueprint importFile(Path exportsDir, String name,
                                                 Function<Identifier, PinType> types) {
        Path file = exportsDir.resolve(name + ".bp");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return GraphNbt.decode(NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()), types);
        } catch (IOException | RuntimeException e) {
            BlueprintMod.LOGGER.error("Import de « {} » impossible", file, e);
            return null;
        }
    }
}
