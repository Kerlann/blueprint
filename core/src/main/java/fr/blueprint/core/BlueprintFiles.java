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
    /**
     * Les {@code .bp} du dossier, sans leur extension et triés — ce que le navigateur
     * propose à l'import. Un dossier illisible rend une liste vide plutôt qu'une
     * erreur : ne rien pouvoir importer se comprend tout seul.
     */
    public static java.util.List<String> listExports(Path exportsDir) {
        if (!Files.isDirectory(exportsDir)) {
            return java.util.List.of();
        }
        try (var files = Files.list(exportsDir)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".bp"))
                    .map(name -> name.substring(0, name.length() - 3))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            BlueprintMod.LOGGER.warn("Dossier d'exports illisible : {}", exportsDir, e);
            return java.util.List.of();
        }
    }

    public static @Nullable Blueprint importFile(Path exportsDir, String name,
                                                 PluginLoader.LoadedRegistries registries) {
        // L'extension est tolérée : le joueur voit « demo_boutique.bp » dans son
        // dossier, et la recopier telle quelle cherchait « demo_boutique.bp.bp ».
        String bare = name.endsWith(".bp") ? name.substring(0, name.length() - 3) : name;
        // Un nom de fichier, pas un chemin. Sans ce refus, « ../../../quelque_chose »
        // ferait lire et analyser un fichier arbitraire du disque — la commande est
        // réservée aux administrateurs, ce qui n'est pas une raison de l'ouvrir.
        if (bare.isBlank() || bare.contains("/") || bare.contains("\\") || bare.contains("..")) {
            BlueprintMod.LOGGER.warn("Import refusé : « {} » n'est pas un nom de fichier", name);
            return null;
        }
        Path file = exportsDir.resolve(bare + ".bp");
        if (!Files.isRegularFile(file)) {
            BlueprintMod.LOGGER.warn("Import de « {} » : fichier introuvable ({})", name, file);
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
