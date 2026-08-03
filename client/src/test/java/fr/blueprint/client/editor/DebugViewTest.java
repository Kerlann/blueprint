package fr.blueprint.client.editor;

import fr.blueprint.core.net.BlueprintPayloads;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ce que l'éditeur retient d'un instantané de débogage (story 9.1b). */
class DebugViewTest {

    private static final Identifier MINE = Identifier.fromNamespaceAndPath("test", "mien");
    private static final Identifier OTHER = Identifier.fromNamespaceAndPath("test", "autre");
    private static final UUID NODE_A = UUID.nameUUIDFromBytes("a".getBytes());
    private static final UUID NODE_B = UUID.nameUUIDFromBytes("b".getBytes());

    private static BlueprintPayloads.DebugSnapshot snapshot(Identifier blueprint, boolean on,
                                                            UUID paused, List<UUID> breakpoints,
                                                            List<BlueprintPayloads.NodeValues> values) {
        return new BlueprintPayloads.DebugSnapshot(blueprint, on, Optional.ofNullable(paused),
                breakpoints, values);
    }

    @Test
    void aSnapshotFillsWhatTheCanvasDraws() {
        DebugView view = new DebugView(MINE);
        assertFalse(view.debugging());

        assertTrue(view.accept(snapshot(MINE, true, NODE_A, List.of(NODE_B),
                List.of(new BlueprintPayloads.NodeValues(NODE_A, List.of("→x = 3", "←y = 6"))))));

        assertTrue(view.debugging());
        assertEquals(NODE_A, view.pausedAt());
        assertTrue(view.isPaused(NODE_A));
        assertFalse(view.isPaused(NODE_B));
        assertTrue(view.hasBreakpoint(NODE_B));
        assertFalse(view.hasBreakpoint(NODE_A));
        assertEquals(List.of("→x = 3", "←y = 6"), view.valuesOf(NODE_A));
        assertEquals(List.of(), view.valuesOf(NODE_B));
    }

    /** Deux éditeurs ouverts ne doivent pas se mélanger les valeurs. */
    @Test
    void aSnapshotOfAnotherBlueprintIsIgnored() {
        DebugView view = new DebugView(MINE);
        assertFalse(view.accept(snapshot(OTHER, true, NODE_A, List.of(NODE_A), List.of())));
        assertFalse(view.debugging());
        assertNull(view.pausedAt());
    }

    /** Débogage coupé : on n'affiche pas des valeurs figées comme si elles vivaient. */
    @Test
    void turningDebuggingOffClearsEverything() {
        DebugView view = new DebugView(MINE);
        view.accept(snapshot(MINE, true, NODE_A, List.of(NODE_B),
                List.of(new BlueprintPayloads.NodeValues(NODE_A, List.of("→x = 3")))));
        view.accept(snapshot(MINE, false, null, List.of(NODE_B),
                List.of(new BlueprintPayloads.NodeValues(NODE_A, List.of("→x = 3")))));

        assertFalse(view.debugging());
        assertNull(view.pausedAt());
        assertTrue(view.breakpoints().isEmpty());
        assertEquals(0, view.nodesWithValues());
    }

    @Test
    void aLaterSnapshotReplacesTheEarlierOne() {
        DebugView view = new DebugView(MINE);
        view.accept(snapshot(MINE, true, NODE_A, List.of(NODE_A, NODE_B),
                List.of(new BlueprintPayloads.NodeValues(NODE_A, List.of("→x = 1")))));
        view.accept(snapshot(MINE, true, NODE_B, List.of(NODE_B),
                List.of(new BlueprintPayloads.NodeValues(NODE_B, List.of("→x = 2")))));

        assertEquals(NODE_B, view.pausedAt());
        assertFalse(view.hasBreakpoint(NODE_A), "le point d'arrêt retiré disparaît");
        assertEquals(List.of(), view.valuesOf(NODE_A), "les valeurs périmées ne restent pas");
        assertEquals(List.of("→x = 2"), view.valuesOf(NODE_B));
    }

    @Test
    void disconnectingWipesTheView() {
        DebugView view = new DebugView(MINE);
        view.accept(snapshot(MINE, true, NODE_A, List.of(NODE_A), List.of()));
        view.clear();
        assertFalse(view.debugging());
        assertNull(view.pausedAt());
        assertTrue(view.breakpoints().isEmpty());
    }
}
