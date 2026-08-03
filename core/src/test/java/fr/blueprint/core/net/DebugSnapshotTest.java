package fr.blueprint.core.net;

import fr.blueprint.core.debug.DebugSession;
import fr.blueprint.core.debug.DebugSessions;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ce que le serveur met dans un instantané de débogage (story 9.1b). */
class DebugSnapshotTest {

    private static final Identifier BLUEPRINT = Identifier.fromNamespaceAndPath("test", "snap");
    private static final UUID NODE_A = UUID.nameUUIDFromBytes("a".getBytes());
    private static final UUID NODE_B = UUID.nameUUIDFromBytes("b".getBytes());

    @AfterEach
    void close() {
        DebugSessions.closeAll();
    }

    @Test
    void withoutSessionTheSnapshotSaysSoAndCarriesNothing() {
        var snapshot = DebugNet.snapshot(BLUEPRINT);
        assertFalse(snapshot.debugging());
        assertTrue(snapshot.pausedAt().isEmpty());
        assertTrue(snapshot.breakpoints().isEmpty());
        assertTrue(snapshot.values().isEmpty());
    }

    @Test
    void theSnapshotCarriesTextNotValues() {
        DebugSession session = DebugSessions.open(BLUEPRINT);
        session.breakOn(NODE_B);
        session.record(NODE_A, Map.of("cible", 42), Map.of("result", "ok"));

        var snapshot = DebugNet.snapshot(BLUEPRINT);
        assertTrue(snapshot.debugging());
        assertEquals(java.util.List.of(NODE_B), snapshot.breakpoints());
        assertEquals(1, snapshot.values().size());
        var values = snapshot.values().get(0);
        assertEquals(NODE_A, values.node());
        assertTrue(values.lines().contains("→cible = 42"), values.lines().toString());
        assertTrue(values.lines().contains("←result = ok"), values.lines().toString());
    }

    @Test
    void aPausedNodeTravels() {
        DebugSession session = DebugSessions.open(BLUEPRINT);
        session.breakOn(NODE_A);
        session.pauseBefore(NODE_A);
        assertEquals(java.util.Optional.of(NODE_A), DebugNet.snapshot(BLUEPRINT).pausedAt());
    }

    /** Un graphe bavard ne doit pas faire enfler le paquet. */
    @Test
    void theSnapshotIsBounded() {
        DebugSession session = DebugSessions.open(BLUEPRINT);
        for (int i = 0; i < BlueprintPayloads.MAX_DEBUG_NODES * 3; i++) {
            session.record(UUID.randomUUID(), Map.of("x", i), Map.of());
            session.breakOn(UUID.randomUUID());
        }
        var snapshot = DebugNet.snapshot(BLUEPRINT);
        assertTrue(snapshot.values().size() <= BlueprintPayloads.MAX_DEBUG_NODES,
                "valeurs : " + snapshot.values().size());
        assertTrue(snapshot.breakpoints().size() <= BlueprintPayloads.MAX_DEBUG_NODES,
                "points d'arrêt : " + snapshot.breakpoints().size());
    }
}
