package fr.blueprint.client.editor.screen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Les libellés du panneau de propriétés (story 10.2, AC7). Leur clé se forme à
 * l'exécution — {@code "blueprint.designer.field." + champ} — donc l'extraction de
 * sources de {@code LocalizationTest} ne peut pas la voir. Sans ce test, ajouter un
 * champ afficherait sa clé brute au lieu de son nom.
 */
class DesignerLocalizationTest {

    private static Path repoRoot() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.isDirectory(path.resolve("docs"))) {
            path = path.getParent();
        }
        if (path == null) {
            throw new IllegalStateException("racine du dépôt introuvable");
        }
        return path;
    }

    private static JsonObject lang(String locale) {
        Path file = repoRoot().resolve("core/src/main/resources/assets/blueprint/lang")
                .resolve(locale + ".json");
        try {
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void everyPropertyFieldIsTranslated() {
        JsonObject english = lang("en_us");
        JsonObject french = lang("fr_fr");
        List<String> missing = new ArrayList<>();
        for (ElementPropertiesState.Field field : ElementPropertiesState.Field.values()) {
            String key = "blueprint.designer.field." + field.name().toLowerCase(Locale.ROOT);
            if (!english.has(key)) {
                missing.add(key + " (en_us)");
            }
            if (!french.has(key)) {
                missing.add(key + " (fr_fr)");
            }
        }
        assertTrue(missing.isEmpty(), "champs de propriétés non traduits : " + missing);
    }
}
