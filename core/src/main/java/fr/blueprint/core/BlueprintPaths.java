package fr.blueprint.core;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Où vivent les fichiers de Blueprint : <b>un seul dossier</b>, {@code blueprint/} à la
 * racine du jeu — {@code .minecraft/blueprint/} en jeu, {@code run/blueprint/} en
 * développement.
 *
 * <p>Ils étaient auparavant dans {@code config/blueprint/}. Le dossier de configuration
 * est celui des <i>réglages</i> ; Blueprint y mettait aussi les exports, c'est-à-dire le
 * <b>travail</b> de l'auteur — et bientôt ses textures (10.5). Ranger un fichier qu'on
 * échange avec quelqu'un d'autre à côté des préférences de la palette rend l'ensemble
 * introuvable : personne ne cherche ses créations dans {@code config}.
 *
 * <p>Un dossier à la racine, comme {@code saves} ou {@code resourcepacks} : on l'ouvre,
 * on voit ses écrans et ses graphes, on en donne un à un ami.
 */
public final class BlueprintPaths {

    private BlueprintPaths() {
    }

    /** {@code <jeu>/blueprint} — créé à la demande. */
    public static Path root() {
        return ensure(FabricLoader.getInstance().getGameDir().resolve("blueprint"));
    }

    /** Les {@code .bp} exportés et importés. */
    public static Path exports() {
        return ensure(root().resolve("exports"));
    }

    /**
     * Les <b>packs</b> : un dossier par pack, avec ses textures et son {@code pack.json}
     * (story 10.5). C'est le dossier qu'on donne à quelqu'un.
     */
    public static Path scripts() {
        return ensure(root().resolve("scripts"));
    }

    /**
     * L'ancien emplacement, {@code config/blueprint}. Conservé pour la <b>reprise</b> :
     * une mise à jour du mod ne doit pas faire disparaître les fichiers de quelqu'un
     * parce qu'on a changé d'avis sur le rangement.
     */
    public static Path legacyRoot() {
        return FabricLoader.getInstance().getConfigDir().resolve("blueprint");
    }

    /**
     * Déplace ce qui traîne dans l'ancien dossier vers le nouveau, une fois.
     *
     * <p>Ne remplace jamais un fichier déjà présent : si les deux existent, celui du
     * nouveau dossier est celui que l'auteur utilise aujourd'hui. Et l'ancien dossier
     * n'est pas supprimé — on ne jette pas le dossier de quelqu'un, on le vide de ce
     * qu'on sait ranger ailleurs.
     */
    public static void migrateLegacy() {
        Path legacy = legacyRoot();
        if (!Files.isDirectory(legacy)) {
            return;
        }
        Path target = root();
        try (Stream<Path> entries = Files.walk(legacy)) {
            for (Path source : entries.toList()) {
                Path destination = target.resolve(legacy.relativize(source).toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(destination);
                } else if (!Files.exists(destination)) {
                    Files.createDirectories(destination.getParent());
                    Files.move(source, destination);
                }
            }
        } catch (IOException e) {
            // Un déplacement raté ne doit pas empêcher le mod de démarrer : au pire,
            // l'auteur retrouve ses fichiers dans l'ancien dossier et les copie.
            BlueprintMod.LOGGER.warn("Reprise de « {} » incomplète : {}", legacy, e.toString());
        }
    }

    private static Path ensure(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            BlueprintMod.LOGGER.warn("Dossier « {} » impossible à créer : {}", path, e.toString());
        }
        return path;
    }
}
