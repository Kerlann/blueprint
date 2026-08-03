package fr.blueprint.core.net;

import fr.blueprint.api.pin.PinType;
import fr.blueprint.core.BlueprintManager;
import fr.blueprint.core.DemoBlueprint;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Transport du graphe et verrou optimiste (story 6.3). */
class GraphSyncTest {

    private static final PluginLoader.LoadedRegistries LOADED = PluginLoader.load(List.of(), true);
    private static final Function<Identifier, PinType> TYPES =
            id -> LOADED.pinTypes().get(id).orElse(null);

    private static Blueprint demo() {
        return DemoBlueprint.build(LOADED.nodes());
    }

    /** Une édition quelconque : la révision monte, le contenu change. */
    private static void describe(Blueprint bp, String description) {
        var meta = bp.meta();
        new EditOperation.SetMeta(new fr.blueprint.core.graph.BlueprintMeta(
                meta.author(), description, meta.version(), meta.permissionCap()))
                .apply(bp, LOADED.nodes());
    }

    @Test
    void graphSurvivesTheWire() {
        Blueprint original = demo();
        Blueprint decoded = GraphSync.fromBytes(GraphSync.toBytes(original), TYPES);
        assertNotNull(decoded);
        assertEquals(original.id(), decoded.id());
        assertTrue(original.contentEquals(decoded), "aller-retour fidèle du graphe");
    }

    @Test
    void oversizedOrCorruptStreamsAreRefused() {
        assertNull(GraphSync.fromBytes(new byte[0], TYPES));
        assertNull(GraphSync.fromBytes(new byte[]{1, 2, 3, 4}, TYPES), "octets qui ne sont pas du gzip");
        assertNull(GraphSync.fromBytes(new byte[GraphSync.MAX_BYTES + 1], TYPES),
                "au-delà de la borne, on ne décompresse même pas");
    }

    // ------------------------------------------------------------ verrou optimiste

    @Test
    void saveAdoptsTheSnapshotAndBumpsTheRevision() {
        BlueprintManager manager = new BlueprintManager();
        Blueprint served = demo();
        assertTrue(manager.adopt(served));
        int base = served.revision();

        Blueprint edited = served.copy();
        describe(edited, "édité");

        var result = manager.save(edited, base);
        assertEquals(BlueprintManager.SaveOutcome.SAVED, result.outcome());
        assertEquals(base + 1, result.revision());
        assertSame(edited, manager.get(edited.id()).orElseThrow(),
                "l'instantané REMPLACE le graphe vivant (adopt seul ne le pouvait pas)");
        assertEquals(base + 1, manager.get(edited.id()).orElseThrow().revision(),
                "le serveur est seul maître du compteur de révision");
    }

    @Test
    void aStaleBaseRevisionIsRefusedAndNothingIsOverwritten() {
        BlueprintManager manager = new BlueprintManager();
        Blueprint served = demo();
        manager.adopt(served);
        int base = served.revision();

        // Un premier éditeur enregistre…
        Blueprint first = served.copy();
        describe(first, "premier");
        assertEquals(BlueprintManager.SaveOutcome.SAVED, manager.save(first, base).outcome());

        // … le second est parti de la même base : refusé, et le premier survit (AC3/AC4).
        Blueprint second = served.copy();
        describe(second, "second");
        var conflict = manager.save(second, base);
        assertEquals(BlueprintManager.SaveOutcome.CONFLICT, conflict.outcome());
        assertEquals(base + 1, conflict.revision(), "la révision courante sert à se recaler");
        assertEquals("premier",
                manager.get(served.id()).orElseThrow().meta().description());

        // Recalé sur la révision annoncée, le second passe.
        assertEquals(BlueprintManager.SaveOutcome.SAVED,
                manager.save(second, conflict.revision()).outcome());
        assertEquals("second", manager.get(served.id()).orElseThrow().meta().description());
    }

    @Test
    void savingSomethingThatNoLongerExistsIsRefused() {
        BlueprintManager manager = new BlueprintManager();
        var result = manager.save(demo(), 0);
        assertEquals(BlueprintManager.SaveOutcome.UNKNOWN, result.outcome());
        assertTrue(manager.all().isEmpty(), "un enregistrement ne crée jamais un blueprint");
    }

    /** MODEL-001 : l'état de cycle de vie reste au serveur, l'instantané ne l'impose pas. */
    @Test
    void enabledStateIsNotTakenFromTheClientSnapshot() {
        BlueprintManager manager = new BlueprintManager();
        Blueprint served = demo();
        manager.adopt(served);
        manager.setEnabled(served.id(), false);

        Blueprint edited = served.copy();
        edited.setEnabled(true);
        manager.save(edited, served.revision());

        assertFalse(manager.get(served.id()).orElseThrow().enabled(),
                "un client ne réactive pas un blueprint en enregistrant");
    }
}
