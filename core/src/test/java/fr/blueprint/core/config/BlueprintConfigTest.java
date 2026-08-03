package fr.blueprint.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chargement de la configuration (story 1.5, AC3). */
class BlueprintConfigTest {

    @Test
    void firstLaunchWritesDefaults(@TempDir Path configDir) {
        var config = BlueprintConfig.load(configDir);
        assertEquals(BlueprintConfig.DEFAULT, config);
        assertTrue(Files.exists(configDir.resolve("config.json")),
                "le fichier de config doit être créé avec ses défauts");
    }

    @Test
    void existingFileIsRead(@TempDir Path configDir) throws Exception {
        Path file = configDir.resolve("config.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{\"commandPermissionLevel\": 4}");
        assertEquals(4, BlueprintConfig.load(configDir).commandPermissionLevel());
    }

    @Test
    void malformedFileFallsBackToDefaults(@TempDir Path configDir) throws Exception {
        Path file = configDir.resolve("config.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{ pas du json");
        assertEquals(BlueprintConfig.DEFAULT, BlueprintConfig.load(configDir));
    }
}
