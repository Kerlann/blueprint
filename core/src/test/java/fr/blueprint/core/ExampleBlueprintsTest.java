package fr.blueprint.core;

import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.Diagnostic;
import fr.blueprint.core.graph.GraphValidator;
import fr.blueprint.core.registry.PluginLoader;
import fr.blueprint.core.script.ScriptGenerator;
import fr.blueprint.core.script.ScriptParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Les blueprints d'exemple, validés un par un.
 *
 * <p><b>Un exemple qui ne compile pas est pire que pas d'exemple</b> : il apprend une
 * erreur à qui le lit, et il la lui apprend avec autorité. Ce test exige donc de
 * chacun ce qu'on exigerait d'un graphe de production — aucune erreur de validation,
 * un point d'entrée, un plafond de permission suffisant — et il écrit les fichiers
 * {@code .bp} de {@code docs/examples/}, comme la référence des nœuds.
 *
 * <p>Régénérer : {@code ./gradlew :core:test --tests "*ExampleBlueprintsTest"
 * -Dblueprint.regenDocs=true}
 */
class ExampleBlueprintsTest {

    private static final String REGEN = "blueprint.regenDocs";
    private static final Path OUTPUT_DIR = Path.of("docs", "examples");

    private static final PluginLoader.LoadedRegistries REGISTRIES =
            PluginLoader.load(List.of(), true);

    private static Path repoRoot() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve("settings.gradle.kts"))) {
            path = path.getParent();
        }
        if (path == null) {
            throw new IllegalStateException("racine du dépôt introuvable");
        }
        return path;
    }

    private static String fileName(ExampleBlueprints.Example example) {
        return example.id().getPath().replace("example/", "") + ".bp";
    }

    // ------------------------------------------------------------------ validité

    /**
     * Le test qui compte : chaque exemple passe le validateur SANS erreur. C'est la
     * même règle que pour un graphe de joueur — pins obligatoires câblés, types
     * compatibles, aucun cycle de données, permissions sous le plafond.
     */
    @Test
    void chaqueExempleEstValideSansErreur() {
        List<String> broken = new ArrayList<>();
        for (ExampleBlueprints.Example example : ExampleBlueprints.all()) {
            Blueprint bp = example.build(REGISTRIES.nodes());
            var result = GraphValidator.validate(bp, REGISTRIES.nodes());
            for (Diagnostic diagnostic : result.errors()) {
                broken.add(example.id() + " → " + diagnostic.code() + " " + diagnostic.args());
            }
        }
        assertTrue(broken.isEmpty(), """
                Exemple(s) invalide(s) :
                  %s
                Un exemple qui ne compile pas apprend une erreur à qui le lit.\
                """.formatted(String.join("\n  ", broken)));
    }

    /** Un exemple sans point d'entrée ne s'exécuterait jamais : autant ne pas le livrer. */
    @Test
    void chaqueExempleEstExecutable() {
        for (ExampleBlueprints.Example example : ExampleBlueprints.all()) {
            Blueprint bp = example.build(REGISTRIES.nodes());
            assertTrue(GraphValidator.validate(bp, REGISTRIES.nodes()).executable(),
                    example.id() + " n'est pas exécutable");
        }
    }

    /**
     * Chacun se compile réellement depuis son point d'entrée. La validation dit que le
     * graphe est bien formé ; la compilation dit qu'il produit un programme.
     */
    @Test
    void chaqueExempleSeCompile() {
        for (ExampleBlueprints.Example example : ExampleBlueprints.all()) {
            Blueprint bp = example.build(REGISTRIES.nodes());
            int entryPoints = 0;
            for (var node : bp.nodes().values()) {
                boolean entry = REGISTRIES.nodes().get(node.typeId())
                        .map(fr.blueprint.api.node.NodeType::entryPoint).orElse(false);
                if (entry) {
                    var result = fr.blueprint.core.compile.Compiler.compile(
                            bp, REGISTRIES.nodes(), node.uuid());
                    assertTrue(result.success(),
                            example.id() + " : le point d'entrée " + node.typeId()
                                    + " ne compile pas — " + result.diagnostics());
                    entryPoints++;
                }
            }
            assertTrue(entryPoints > 0, example.id() + " n'a compilé aucun point d'entrée");
        }
    }

    // ---------------------------------------------------------------- cohérence

    @Test
    void lesIdentifiantsSontUniquesEtDecrits() {
        Set<String> seen = new HashSet<>();
        for (ExampleBlueprints.Example example : ExampleBlueprints.all()) {
            assertTrue(seen.add(example.id().toString()),
                    "identifiant en double : " + example.id());
            assertFalse(example.teaches().isBlank(),
                    example.id() + " ne dit pas ce qu'il enseigne");
            assertFalse(example.build(REGISTRIES.nodes()).meta().description().isBlank(),
                    example.id() + " n'a pas de description");
        }
    }

    /**
     * Un exemple long cesse d'être un exemple. La borne est basse exprès : si l'un
     * grossit, c'est qu'il faut le couper en deux, pas relever le seuil.
     */
    @Test
    void lesExemplesRestentCourts() {
        for (ExampleBlueprints.Example example : ExampleBlueprints.all()) {
            int size = example.build(REGISTRIES.nodes()).nodes().size();
            assertTrue(size <= 12,
                    example.id() + " porte " + size + " nœuds — le couper en deux");
        }
    }

    /**
     * Deux constructions donnent le même graphe, octet pour octet. Sans cela le
     * fichier commité changerait à chaque exécution et sa garde ne prouverait rien.
     */
    @Test
    void laGenerationEstDeterministe() {
        for (ExampleBlueprints.Example example : ExampleBlueprints.all()) {
            assertEquals(script(example.build(REGISTRIES.nodes())),
                    script(example.build(REGISTRIES.nodes())),
                    example.id() + " : deux constructions diffèrent");
        }
    }

    /** Ce qui s'écrit se relit : c'est la promesse de BScript, éprouvée ici aussi. */
    @Test
    void chaqueExempleFaitSonAllerRetourBScript() {
        for (ExampleBlueprints.Example example : ExampleBlueprints.all()) {
            Blueprint original = example.build(REGISTRIES.nodes());
            String text = script(original);
            var parsed = ScriptParser.parse(text, REGISTRIES);
            assertNull(parsed.error(),
                    example.id() + " ne se relit pas : " + parsed.error());
            assertNotNull(parsed.blueprint());
            assertEquals(original.nodes().size(), parsed.blueprint().nodes().size(),
                    example.id() + " a perdu des nœuds à l'aller-retour");
            assertEquals(original.links().size(), parsed.blueprint().links().size(),
                    example.id() + " a perdu des liens à l'aller-retour");
            // Le CONTENU, et pas seulement les comptes. Compter laissait passer la
            // perte du littéral « name » d'un nœud d'événement : le graphe revenait
            // entier, se validait, s'affichait — et n'écoutait plus rien.
            assertTrue(original.contentEquals(parsed.blueprint()),
                    () -> example.id() + " diffère après l'aller-retour :\n" + text);
        }
    }

    // ------------------------------------------------------------- fichiers .bp

    /**
     * Les fichiers de {@code docs/examples/} sont générés, comme la référence des
     * nœuds : ce qui est commité est ce que le registre produit. Renommer un pin sans
     * régénérer casse ici, pas chez le joueur qui charge l'exemple.
     */
    @Test
    void lesFichiersCommitesCorrespondentAuxExemples() {
        boolean regen = Boolean.getBoolean(REGEN);
        Path dir = repoRoot().resolve(OUTPUT_DIR);
        List<String> stale = new ArrayList<>();

        for (ExampleBlueprints.Example example : ExampleBlueprints.all()) {
            String expected = script(example.build(REGISTRIES.nodes()));
            Path file = dir.resolve(fileName(example));
            if (regen) {
                write(file, expected);
                continue;
            }
            if (!Files.isRegularFile(file)) {
                stale.add(fileName(example) + " (absent)");
                continue;
            }
            if (!read(file).equals(expected)) {
                stale.add(fileName(example) + " (divergent)");
            }
        }
        if (regen) {
            return;
        }
        assertTrue(stale.isEmpty(), """
                Fichier(s) d'exemple à régénérer : %s
                ./gradlew :core:test --tests "*ExampleBlueprintsTest" -Dblueprint.regenDocs=true\
                """.formatted(stale));
    }

    /** Aucun fichier orphelin : un exemple retiré ne laisse pas son .bp derrière lui. */
    @Test
    void aucunFichierOrphelinDansLeDossier() {
        Path dir = repoRoot().resolve(OUTPUT_DIR);
        if (!Files.isDirectory(dir)) {
            return; // la génération n'a pas encore tourné
        }
        Set<String> expected = new HashSet<>();
        ExampleBlueprints.all().forEach(e -> expected.add(fileName(e)));
        try (var files = Files.list(dir)) {
            List<String> orphans = files
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".bp") && !expected.contains(name))
                    .toList();
            assertTrue(orphans.isEmpty(), "fichiers .bp orphelins : " + orphans);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ------------------------------------------------------------------ outillage

    private static String script(Blueprint bp) {
        var result = ScriptGenerator.generate(bp, REGISTRIES.nodes());
        assertTrue(result.issues().isEmpty(),
                bp.id() + " ne se génère pas : " + result.issues());
        return result.text();
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
