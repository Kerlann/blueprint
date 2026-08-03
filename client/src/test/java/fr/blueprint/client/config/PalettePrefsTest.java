package fr.blueprint.client.config;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PalettePrefsTest {

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("blueprint", path);
    }

    @Test
    void allerRetourSurDisque(@TempDir Path dir) {
        PalettePrefs prefs = new PalettePrefs();
        assertTrue(prefs.toggleFavorite(id("math/add")));
        prefs.addRecent(id("flow/branch"));
        prefs.addRecent(id("debug/log"));
        prefs.save(dir);
        assertTrue(Files.exists(dir.resolve("blueprint").resolve("editor-client.json")));

        PalettePrefs loaded = PalettePrefs.load(dir);
        assertTrue(loaded.isFavorite(id("math/add")));
        // Du plus récent au plus ancien.
        assertEquals(List.of(id("debug/log"), id("flow/branch")), loaded.recents());

        assertFalse(loaded.toggleFavorite(id("math/add")));
        assertFalse(loaded.isFavorite(id("math/add")));
    }

    @Test
    void recentsDedoublonnesEtPlafonnesA10() {
        PalettePrefs prefs = new PalettePrefs();
        for (int i = 0; i < 12; i++) {
            prefs.addRecent(id("n" + i));
        }
        assertEquals(PalettePrefs.MAX_RECENTS, prefs.recents().size());
        assertEquals(id("n11"), prefs.recents().get(0));

        prefs.addRecent(id("n5"));
        assertEquals(id("n5"), prefs.recents().get(0));
        assertEquals(PalettePrefs.MAX_RECENTS, prefs.recents().size());
    }

    @Test
    void fichierCorrompuDonneDesPreferencesVides(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("blueprint").resolve("editor-client.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{pas du json");
        PalettePrefs prefs = PalettePrefs.load(dir);
        assertTrue(prefs.favorites().isEmpty());
        assertTrue(prefs.recents().isEmpty());
    }
}
