package fr.blueprint.client.theme;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThemeLoaderTest {

    @Test
    void fichierAbsentOuCorrompuDonneLeDefaut(@TempDir Path dir) throws Exception {
        assertEquals(Theme.DEFAULT, ThemeLoader.load(dir));
        Path file = dir.resolve("blueprint").resolve("theme.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{cassé");
        assertEquals(Theme.DEFAULT, ThemeLoader.load(dir));
    }

    @Test
    void surchargePartielleSurLaBase(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("blueprint").resolve("theme.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                { "canvas": { "background": "#102030" },
                  "state": { "error": "#80FF0000" } }
                """);
        Theme theme = ThemeLoader.load(dir);
        assertEquals(0xFF102030, theme.canvasBackground());
        assertEquals(0x80FF0000, theme.error());
        // Les clés absentes gardent la valeur par défaut.
        assertEquals(Theme.DEFAULT.grid(), theme.grid());
        assertEquals(Theme.DEFAULT.nodeSelected(), theme.nodeSelected());
    }

    @Test
    void baseContraste(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("blueprint").resolve("theme.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{ \"contrast\": true }");
        assertEquals(Theme.HIGH_CONTRAST, ThemeLoader.load(dir));
    }

    @Test
    void couleursInvalidesRetombentSurLaBase() {
        JsonObject section = new JsonObject();
        section.addProperty("x", "pas-une-couleur");
        assertEquals(42, ThemeLoader.color(section, "x", 42));
        section.addProperty("y", "#12345");
        assertEquals(7, ThemeLoader.color(section, "y", 7));
        section.addProperty("z", "#A1B2C3");
        assertEquals(0xFFA1B2C3, ThemeLoader.color(section, "z", 0));
    }
}
