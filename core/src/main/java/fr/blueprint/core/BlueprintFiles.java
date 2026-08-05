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

    /**
     * Écritures en attente, par blueprint — elles se chaînent au lieu de courir ensemble.
     *
     * <p>Deux enregistrements rapprochés du même graphe partent sinon vers le disque en
     * parallèle, et rien ne dit lequel arrive en dernier : le fichier pourrait conserver
     * l'avant-dernière version. Une file par identifiant suffit, et n'empêche pas deux
     * blueprints différents de s'écrire en même temps.
     */
    private static final java.util.Map<Identifier, java.util.concurrent.CompletableFuture<Void>>
            PENDING = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Le reflet sur disque, <b>sans bloquer le fil serveur</b>.
     *
     * <p>Le texte est produit ici, sur le fil appelant : il lit le graphe vivant, qui
     * n'appartient qu'à ce fil-là. Seuls la création du dossier et l'écriture du fichier
     * partent sur le pool d'entrées-sorties du jeu.
     *
     * <p>C'est ce que le contrat de {@code BlueprintManager.mirror} demandait déjà —
     * « au mieux, jamais au prix de l'enregistrement ». Il était tenu pour les erreurs
     * (un disque plein fait perdre le reflet, pas le travail) mais pas pour le temps :
     * la latence d'écriture n'est bornée par rien, et un joueur qui enregistrait faisait
     * hoqueter tout le monde. Sur disque mécanique, antivirus ou stockage réseau, cela
     * se compte en dizaines de millisecondes — plusieurs ticks.
     */
    public static void exportAsync(Blueprint bp, Path exportsDir,
                                   PluginLoader.LoadedRegistries registries) {
        ScriptGenerator.Result script = ScriptGenerator.generate(bp, registries.nodes());
        for (String issue : script.issues()) {
            BlueprintMod.LOGGER.warn("Export de « {} » : {}", bp.id(), issue);
        }
        Identifier id = bp.id();
        String text = script.text();
        Path file = exportsDir.resolve(fileName(id));
        PENDING.compute(id, (key, previous) -> {
            var after = previous == null
                    ? java.util.concurrent.CompletableFuture.<Void>completedFuture(null)
                    // Une écriture en échec ne doit pas emporter les suivantes : le reflet
                    // est au mieux, et un disque momentanément plein se libère.
                    : previous.handle((ignored, error) -> null);
            // nonCriticalIoPool et non ioPool : le reflet est explicitement « au mieux ».
            // Le perdre ne coûte qu'un fichier réimportable, alors qu'une sauvegarde de
            // monde qui attendrait derrière lui coûterait le travail de tout le monde.
            //
            // PIÈGE DE NOMMAGE : la classe est « net.minecraft.util.Util » en 1.21.11, et
            // non « net.minecraft.Util » comme dans les versions antérieures et dans la
            // plupart des exemples qu'on trouve.
            return after.thenRunAsync(() -> write(file, exportsDir, text, id),
                    net.minecraft.util.Util.nonCriticalIoPool());
        });
    }

    private static void write(Path file, Path exportsDir, String text, Identifier id) {
        try {
            Files.createDirectories(exportsDir);
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            BlueprintMod.LOGGER.error("Export de « {} » impossible", id, e);
        }
    }

    /**
     * La même chose, <b>synchrone</b> : la commande {@code /blueprint export} attend son
     * chemin pour le montrer à celui qui l'a tapée, et l'a explicitement demandé.
     */
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
