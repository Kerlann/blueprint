package fr.blueprint.core;

import fr.blueprint.core.compile.Compiler;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.GraphValidator;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Démo, export/import et adoption (story 4.4a). */
class DemoBlueprintTest {

    private static final PluginLoader.LoadedRegistries LOADED = PluginLoader.load(List.of(), true);

    @Test
    void demoIsValidAndCompilesFromBothEntryPoints() {
        Blueprint demo = DemoBlueprint.build(LOADED.nodes());
        assertEquals(7, demo.nodes().size());

        var validation = GraphValidator.validate(demo, LOADED.nodes());
        assertTrue(validation.diagnostics().isEmpty(),
                () -> "la démo doit être irréprochable : " + validation.diagnostics());

        // Compilable depuis chacun de ses deux nœuds d'événement.
        List<UUID> entries = demo.nodes().values().stream()
                .filter(n -> LOADED.nodes().get(n.typeId()).orElseThrow().entryPoint())
                .map(fr.blueprint.core.graph.Node::uuid).toList();
        assertEquals(2, entries.size());
        for (UUID entry : entries) {
            assertTrue(Compiler.compile(demo, LOADED.nodes(), entry).success());
        }
    }

    @Test
    void exportThenImportRoundTripsOnDisk(@TempDir Path dir) throws Exception {
        Blueprint demo = DemoBlueprint.build(LOADED.nodes());
        Path file = BlueprintFiles.export(demo, dir, LOADED);
        assertNotNull(file);
        assertTrue(Files.isRegularFile(file));
        assertEquals("blueprint_demo.bp", file.getFileName().toString());
        // Depuis 4.1-4.3 : le fichier est du BScript texte lisible.
        String text = Files.readString(file);
        assertTrue(text.startsWith("blueprint blueprint:demo {"), text.substring(0, 60));

        Blueprint imported = BlueprintFiles.importFile(dir, "blueprint_demo", LOADED);
        assertNotNull(imported);
        assertTrue(demo.contentEquals(imported), "round-trip disque identique");
    }

    @Test
    void legacyNbtFilesRemainImportable(@TempDir Path dir) throws Exception {
        // Les .bp NBT de la 4.4a (magie gzip) restent lisibles.
        Blueprint demo = DemoBlueprint.build(LOADED.nodes());
        net.minecraft.nbt.NbtIo.writeCompressed(
                fr.blueprint.core.graph.GraphNbt.encode(demo), dir.resolve("legacy.bp"));
        Blueprint imported = BlueprintFiles.importFile(dir, "legacy", LOADED);
        assertNotNull(imported);
        assertTrue(demo.contentEquals(imported));
    }

    @Test
    void importFailuresAreNullNeverExceptions(@TempDir Path dir) throws Exception {
        assertNull(BlueprintFiles.importFile(dir, "absent", LOADED));
        Files.writeString(dir.resolve("corrompu.bp"), "pas du bscript valide {{{");
        assertNull(BlueprintFiles.importFile(dir, "corrompu", LOADED));
    }

    @Test
    void adoptRefusesDuplicates() {
        var manager = new BlueprintManager();
        Blueprint demo = DemoBlueprint.build(LOADED.nodes());
        assertTrue(manager.adopt(demo));
        assertFalse(manager.adopt(DemoBlueprint.build(LOADED.nodes())), "id déjà pris");
        assertEquals(1, manager.all().size());
        assertTrue(manager.get(Identifier.fromNamespaceAndPath("blueprint", "demo")).isPresent());
    }
}
