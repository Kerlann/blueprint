package fr.blueprint.core.docs;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.pin.PinKind;
import fr.blueprint.core.registry.PluginLoader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Référence des nœuds <b>générée depuis le registre</b> (story 9.5) : la documentation
 * ne peut pas mentir, puisque personne ne l'écrit à la main.
 *
 * <p>Le test compare le fichier commité à ce que produit le registre courant et
 * <b>échoue</b> s'ils divergent — ajouter un nœud sans régénérer la doc casse la
 * construction. Pour régénérer :
 * <pre>./gradlew :core:test --tests "*NodeReferenceTest" -Dblueprint.regenDocs=true</pre>
 */
class NodeReferenceTest {

    private static final String REGEN = "blueprint.regenDocs";
    private static final Path OUTPUT = Path.of("docs", "node-reference.md");

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

    @Test
    void theCommittedReferenceMatchesTheRegistry() {
        String generated = generate();
        Path file = repoRoot().resolve(OUTPUT);
        if (Boolean.getBoolean(REGEN)) {
            try {
                Files.writeString(file, generated, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return;
        }
        assertTrue(Files.isRegularFile(file), OUTPUT + " manquant — régénérer avec -D" + REGEN + "=true");
        String committed;
        try {
            committed = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertEquals(generated, committed,
                OUTPUT + " ne correspond plus au registre — régénérer avec -D" + REGEN + "=true");
    }

    // ------------------------------------------------------------------ génération

    private static String generate() {
        var registries = PluginLoader.load(List.of(), true);
        JsonObject en = lang();
        List<NodeType> types = new ArrayList<>(registries.nodes().all());
        types.sort(Comparator.comparing(type -> type.id().toString()));

        // Regroupement par catégorie, catégories triées : un ordre stable se relit et
        // se compare d'une version à l'autre.
        Map<String, List<NodeType>> byCategory = new LinkedHashMap<>();
        types.stream().map(type -> type.category().id()).distinct().sorted()
                .forEach(category -> byCategory.put(category, new ArrayList<>()));
        types.forEach(type -> byCategory.get(type.category().id()).add(type));

        StringBuilder out = new StringBuilder();
        out.append("# Référence des nœuds\n\n")
                .append("> **Fichier généré** — ne pas modifier à la main. Il est produit depuis le\n")
                .append("> registre par `NodeReferenceTest` ; la construction échoue s'il diverge.\n")
                .append("> Régénérer : `./gradlew :core:test --tests \"*NodeReferenceTest\" ")
                .append("-Dblueprint.regenDocs=true`\n\n")
                .append(types.size()).append(" nœuds dans ").append(byCategory.size())
                .append(" catégories.\n\n")
                .append("Légende : **P** = nœud pur (sans pin d'exécution) · **E** = point d'entrée ")
                .append("(événement) · *fuel* = coût d'un passage.\n");

        byCategory.forEach((category, list) -> {
            out.append("\n## ").append(category).append("\n\n");
            for (NodeType type : list) {
                out.append("### `").append(type.id()).append("`");
                String title = en.has(type.titleKey()) ? en.get(type.titleKey()).getAsString() : null;
                if (title != null) {
                    out.append(" — ").append(title);
                }
                out.append("\n\n");
                List<String> flags = new ArrayList<>();
                if (type.pure()) {
                    flags.add("P");
                }
                if (type.entryPoint()) {
                    flags.add("E");
                }
                if (!type.deterministic()) {
                    flags.add("non déterministe");
                }
                out.append("permission `").append(type.permission()).append("` · fuel ")
                        .append(type.fuelCost());
                if (!flags.isEmpty()) {
                    out.append(" · ").append(String.join(", ", flags));
                }
                out.append("\n\n");
                appendPins(out, "Entrées", type.inputs());
                appendPins(out, "Sorties", type.outputs());
            }
        });
        return out.toString();
    }

    private static void appendPins(StringBuilder out, String heading, List<NodeType.PinSpec> pins) {
        if (pins.isEmpty()) {
            return;
        }
        out.append("| ").append(heading).append(" | Type | Défaut |\n|---|---|---|\n");
        for (NodeType.PinSpec pin : pins) {
            out.append("| `").append(pin.name()).append("` | ")
                    .append(pin.kind() == PinKind.EXEC ? "exec" : "`" + pin.type() + "`")
                    .append(" | ")
                    .append(pin.defaultValue() == null ? "—" : "`" + pin.defaultValue().value() + "`")
                    .append(" |\n");
        }
        out.append('\n');
    }

    private static JsonObject lang() {
        Path file = repoRoot().resolve(
                "core/src/main/resources/assets/blueprint/lang/en_us.json");
        try {
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
