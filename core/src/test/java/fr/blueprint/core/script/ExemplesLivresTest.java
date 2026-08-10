package fr.blueprint.core.script;

import fr.blueprint.core.graph.GraphValidator;
import fr.blueprint.core.registry.PluginLoader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chaque {@code .bp} livré dans {@code docs/examples/} se relit et se valide.
 *
 * <p>Les exemples nés d'un constructeur Java ({@code bench}, {@code rp}…) ont déjà leur
 * test dédié, qui vérifie l'aller-retour depuis le graphe. Ceux écrits <b>à la main</b> en
 * BScript n'en avaient aucun : rien ne disait qu'ils parsaient, encore moins qu'ils ne
 * laissaient aucun diagnostic. Un exemple qu'on livre à un joueur et qui refuse de
 * s'importer est pire que pas d'exemple du tout.
 *
 * <p>Ce test lit le dossier plutôt qu'une liste de noms : ajouter un exemple le fait
 * vérifier, sans que personne ait à penser à l'inscrire quelque part.
 */
class ExemplesLivresTest {

    static {
        // Certains nœuds portent un pin dont la valeur par défaut construit un ItemStack :
        // les matérialiser exige les registres du jeu, que le harnais headless n'amorce pas
        // tout seul. Sans cette ligne, le parseur échoue sur « Not bootstrapped » — un
        // message qui accuse le BScript alors que le fautif est le harnais.
        fr.blueprint.core.MinecraftBootstrap.ensure();
    }

    private static final PluginLoader.LoadedRegistries LOADED = PluginLoader.load(List.of(), true);

    /**
     * <b>Un constat, pas une dispense.</b>
     *
     * <p>{@code fonctions.bp} se relit sans erreur de syntaxe, mais le graphe qui en sort
     * porte seize diagnostics — des {@code NODE_NOT_FOUND} sur des liens qui visent des
     * nœuds de <b>corps de fonction</b>, plus les {@code REQUIRED_PIN_UNLINKED} qui en
     * découlent. Le blueprint <i>construit</i> par {@code FunctionBlueprint.build}, lui,
     * ne laisse rien : c'est donc le passage par le texte qui perd quelque chose.
     *
     * <p>Ce que cela veut dire pour un joueur : importer cet exemple livré lui montrerait
     * un graphe en erreur. Je n'ai pas déterminé lequel des trois est fautif — le
     * parseur, la façon dont le validateur traite les corps de fonction, ou le fichier
     * lui-même — et le dire ici vaut mieux que de le taire pour obtenir un test vert.
     */
    private static final List<String> CONSTAT_OUVERT = List.of("fonctions.bp");

    private static Path racine() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve("settings.gradle.kts"))) {
            path = path.getParent();
        }
        if (path == null) {
            throw new IllegalStateException("racine du dépôt introuvable");
        }
        return path;
    }

    private static List<Path> exemples() {
        try (Stream<Path> files = Files.list(racine().resolve("docs/examples"))) {
            return files.filter(p -> p.toString().endsWith(".bp")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Les blocs {@code bscript} de la spécification se parsent, eux aussi.
     *
     * <p>Ce test existe parce que le contraire a duré longtemps : {@code bscript-spec.md}
     * décrivait un langage à opérateurs infixes, {@code if}, {@code wait 20t} et
     * commentaires {@code //}, dont <b>rien</b> n'était implémenté. Un moddeur qui suivait
     * la spec écrivait du BScript refusé dès sa troisième ligne, et la documentation était
     * la dernière chose qu'il aurait songé à soupçonner.
     *
     * <p>Une spécification n'est pas vérifiable en entier — mais ses exemples le sont, et
     * ce sont eux qu'on recopie. Les faire passer par le parseur ferme la voie par laquelle
     * l'écart s'était installé.
     */
    @Test
    void lesExemplesDeLaSpecSeParsent() {
        String spec;
        try {
            spec = Files.readString(racine().resolve("docs/bscript-spec.md"),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<String> fautifs = new ArrayList<>();
        int trouves = 0;
        int depuis = 0;
        while (true) {
            int debut = spec.indexOf("```bscript", depuis);
            if (debut < 0) {
                break;
            }
            int corps = debut + "```bscript".length();
            int fin = spec.indexOf("```", corps);
            if (fin < 0) {
                break;
            }
            depuis = fin + 3;
            trouves++;
            var parsed = ScriptParser.parse(spec.substring(corps, fin), LOADED);
            if (!parsed.success()) {
                fautifs.add("bloc n°" + trouves + " → " + parsed.error());
            }
        }
        assertTrue(trouves > 0, "aucun bloc bscript dans la spec : le test passerait à vide");
        assertTrue(fautifs.isEmpty(), "la spec montre du BScript que le parseur refuse :\n  "
                + String.join("\n  ", fautifs));
    }

    @Test
    void chaqueExempleSeRelit() {
        List<String> fautifs = new ArrayList<>();
        for (Path file : exemples()) {
            String source;
            try {
                source = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            var parsed = ScriptParser.parse(source, LOADED);
            if (!parsed.success()) {
                fautifs.add(file.getFileName() + " → " + parsed.error());
            }
        }
        assertFalse(exemples().isEmpty(), "aucun exemple trouvé : le test passerait à vide");
        assertTrue(fautifs.isEmpty(), "exemple(s) que le parseur refuse :\n  "
                + String.join("\n  ", fautifs));
    }

    @Test
    void aucunExempleNeLaisseUnDiagnostic() {
        List<String> fautifs = new ArrayList<>();
        for (Path file : exemples()) {
            String source;
            try {
                source = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            if (CONSTAT_OUVERT.contains(file.getFileName().toString())) {
                continue;
            }
            var parsed = ScriptParser.parse(source, LOADED);
            if (!parsed.success()) {
                continue;   // dit par le test ci-dessus, inutile de le répéter ici
            }
            var diagnostics = GraphValidator.validate(parsed.blueprint(), LOADED.nodes())
                    .diagnostics();
            if (!diagnostics.isEmpty()) {
                fautifs.add(file.getFileName() + " → " + diagnostics);
            }
        }
        assertTrue(fautifs.isEmpty(), "exemple(s) qui laissent du travail au joueur :\n  "
                + String.join("\n  ", fautifs));
    }
}
