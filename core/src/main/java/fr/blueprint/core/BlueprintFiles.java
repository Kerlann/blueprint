package fr.blueprint.core;

import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.GraphNbt;
import fr.blueprint.core.registry.PluginLoader;
import fr.blueprint.core.script.ScriptGenerator;
import fr.blueprint.core.script.ScriptParser;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Export/import d'un blueprint sur disque. Depuis les stories 4.1-4.3, l'export écrit
 * du <b>BScript texte</b> (lisible, diffable) ; l'import détecte le format — magie gzip
 * → NBT hérité (4.4a), sinon texte — les anciens {@code .bp} restent importables.
 */
public final class BlueprintFiles {

    private BlueprintFiles() {
    }

    /** Nom de fichier sûr dérivé d'un identifiant : {@code blueprint_demo.bp}. */
    public static String fileName(Identifier id) {
        return (id.getNamespace() + "_" + id.getPath()).replace('/', '_') + ".bp";
    }

    /** Écrit le blueprint en BScript ; retourne le chemin, ou null (journalisé). */
    public static @Nullable Path export(Blueprint bp, Path exportsDir,
                                        PluginLoader.LoadedRegistries registries) {
        try {
            Files.createDirectories(exportsDir);
            Path file = exportsDir.resolve(fileName(bp.id()));
            ScriptGenerator.Result script = ScriptGenerator.generate(bp, registries.nodes());
            for (String issue : script.issues()) {
                BlueprintMod.LOGGER.warn("Export de « {} » : {}", bp.id(), issue);
            }
            Files.writeString(file, script.text(), StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            BlueprintMod.LOGGER.error("Export de « {} » impossible", bp.id(), e);
            return null;
        }
    }

    /** Lit {@code <nom>.bp} (BScript ou NBT hérité) ; null si absent ou indécodable. */
    public static @Nullable Blueprint importFile(Path exportsDir, String name,
                                                 PluginLoader.LoadedRegistries registries) {
        Path file = exportsDir.resolve(name + ".bp");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0x1F && (bytes[1] & 0xFF) == 0x8B) {
                return GraphNbt.decode(
                        NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()),
                        typeId -> registries.pinTypes().get(typeId).orElse(null));
            }
            ScriptParser.ParseResult result = ScriptParser.parse(
                    new String(bytes, StandardCharsets.UTF_8), registries);
            if (!result.success()) {
                BlueprintMod.LOGGER.error("Import de « {} » : {}", file, result.error());
                return null;
            }
            return result.blueprint();
        } catch (IOException | RuntimeException e) {
            BlueprintMod.LOGGER.error("Import de « {} » impossible", file, e);
            return null;
        }
    }
}
