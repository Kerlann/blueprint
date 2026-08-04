package fr.blueprint.core.content;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Écrit le pack de ressources du contenu déclaré — et rien d'autre.
 *
 * <p>Le dossier visé est {@code resourcepacks/blueprint_content/}, chez le joueur, à côté
 * de ses propres packs. Ce voisinage est le point délicat de la story : <b>ce code
 * supprime des fichiers dans un dossier que le joueur possède</b>. Trois règles s'en
 * déduisent, et elles ne sont pas négociables.
 *
 * <h2>1. Ne jamais écrire dans un dossier qu'on n'a pas créé</h2>
 * Un dossier existant, non vide et sans notre empreinte appartient à quelqu'un d'autre —
 * un pack téléchargé qui porterait ce nom, ou une version antérieure faite à la main.
 * On refuse, on le dit, et on ne touche à rien. Écraser aurait détruit un travail qu'on
 * n'a pas fait, sans que rien ne le signale et sans que git puisse le rendre.
 *
 * <h2>2. Ne supprimer que ce qu'on a écrit</h2>
 * L'élagage porte sur les chemins revendiqués par le plan précédent, pas sur le contenu
 * du dossier : un fichier qu'on n'a jamais écrit n'est jamais effacé. Sans cela, retirer
 * un item laisserait son modèle et sa texture en place — le jeu continuerait de l'afficher
 * comme si de rien n'était, et personne ne comprendrait pourquoi.
 *
 * <h2>3. Ne rien faire quand rien n'a changé</h2>
 * L'empreinte est comparée d'abord. Le cas courant — un joueur qui relance sans avoir
 * touché à ses items — coûte alors la lecture d'un fichier de soixante-quatre octets, et
 * surtout <b>aucun rechargement de ressources</b> : celui-ci fige le jeu plusieurs
 * secondes, et l'infliger à chaque démarrage pour rien serait une régression déguisée en
 * fonctionnalité.
 */
public final class ContentPackWriter {

    /** Le nom du dossier sous {@code resourcepacks/}, et donc l'identifiant du pack. */
    public static final String DIRECTORY = "blueprint_content";
    /** L'identifiant qu'en fait Minecraft dans sa liste de packs sélectionnés. */
    public static final String PACK_ID = "file/" + DIRECTORY;

    private ContentPackWriter() {
    }

    /**
     * Ce qui s'est passé : le pack a-t-il changé, était-ce sa <b>création</b>, et si rien
     * n'a été fait, pourquoi.
     *
     * <p>{@code created} n'est pas un détail de journalisation. C'est lui qui autorise
     * l'activation automatique, et donc lui qui empêche de la refaire : un joueur qui
     * décoche le pack dans son menu a pris une décision, et la lui reprendre au démarrage
     * suivant ferait de ce réglage un bouton qui ne fait rien.
     */
    public record Outcome(boolean changed, boolean created, @Nullable String refusal) {

        public static Outcome unchanged() {
            return new Outcome(false, false, null);
        }

        public static Outcome written(boolean created) {
            return new Outcome(true, created, null);
        }

        public static Outcome refused(String reason) {
            return new Outcome(false, false, reason);
        }

        public boolean ok() {
            return refusal == null;
        }
    }

    /**
     * Écrit le pack si — et seulement si — son contenu diffère de ce qui s'y trouve.
     *
     * @return {@code changed} vrai quand le disque a bougé, donc quand un rechargement
     *         de ressources est justifié ; {@code refusal} non nul quand on a préféré ne
     *         rien faire.
     */
    public static Outcome writeIfChanged(ContentPack pack, Path target) {
        String stamp = pack.stamp();
        String previous = readStamp(target);
        if (previous == null && !isOurs(target)) {
            return Outcome.refused(target + " existe déjà et n'a pas été créé par "
                    + "Blueprint — rien n'a été écrit, renommez-le ou supprimez-le");
        }
        if (stamp.equals(previous)) {
            return Outcome.unchanged();
        }
        try {
            prune(target, pack.paths());
            Files.createDirectories(target);
            for (var entry : pack.files().entrySet()) {
                Path file = target.resolve(entry.getKey());
                Files.createDirectories(file.getParent());
                Files.writeString(file, entry.getValue(), StandardCharsets.UTF_8);
            }
            for (var entry : pack.textures().entrySet()) {
                Path file = target.resolve(entry.getKey());
                Files.createDirectories(file.getParent());
                Files.copy(entry.getValue(), file,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            // L'empreinte en dernier : une écriture interrompue laisse alors une
            // empreinte absente ou périmée, donc un pack qui se réécrit au démarrage
            // suivant. L'écrire en premier aurait figé un pack à moitié construit.
            Files.writeString(target.resolve(ContentPack.STAMP), stamp, StandardCharsets.UTF_8);
            return Outcome.written(previous == null);
        } catch (IOException e) {
            return Outcome.refused(target + " : " + e.getMessage());
        }
    }

    /**
     * Supprime ce que <b>nous</b> avions écrit et qui n'est plus revendiqué.
     *
     * <p>La liste de référence est celle des chemins qu'un pack Blueprint peut produire —
     * les trois dossiers d'assets et le {@code pack.mcmeta}. Tout le reste du dossier est
     * laissé intact, y compris ce qu'un joueur curieux y aurait déposé.
     */
    private static void prune(Path target, List<String> keep) throws IOException {
        if (!Files.isDirectory(target)) {
            return;
        }
        Set<Path> kept = new HashSet<>();
        for (String path : keep) {
            kept.add(target.resolve(path).normalize());
        }
        Path assets = target.resolve("assets");
        List<Path> doomed = new ArrayList<>();
        if (Files.isDirectory(assets)) {
            try (Stream<Path> walk = Files.walk(assets)) {
                walk.filter(Files::isRegularFile)
                        .filter(file -> !kept.contains(file.normalize()))
                        .forEach(doomed::add);
            }
        }
        for (Path file : doomed) {
            Files.deleteIfExists(file);
        }
        // Les dossiers vidés par l'élagage : un « assets/blueprint/textures/item » vide
        // n'est pas une faute, mais il donne l'impression qu'un item est encore là.
        if (Files.isDirectory(assets)) {
            try (Stream<Path> walk = Files.walk(assets)) {
                List<Path> directories = walk.filter(Files::isDirectory)
                        .sorted(Comparator.reverseOrder()).toList();
                for (Path directory : directories) {
                    try (Stream<Path> children = Files.list(directory)) {
                        if (children.findAny().isEmpty()) {
                            Files.deleteIfExists(directory);
                        }
                    }
                }
            }
        }
    }

    /** L'empreinte présente sur le disque, ou {@code null} s'il n'y en a pas. */
    private static @Nullable String readStamp(Path target) {
        Path stamp = target.resolve(ContentPack.STAMP);
        if (!Files.isRegularFile(stamp)) {
            return null;
        }
        try {
            return Files.readString(stamp, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Un dossier absent ou vide est à prendre ; un dossier habité sans notre empreinte
     * ne l'est pas.
     */
    private static boolean isOurs(Path target) {
        if (!Files.exists(target)) {
            return true;
        }
        if (!Files.isDirectory(target)) {
            return false;
        }
        try (Stream<Path> children = Files.list(target)) {
            return children.findAny().isEmpty();
        } catch (IOException e) {
            return false;
        }
    }
}
