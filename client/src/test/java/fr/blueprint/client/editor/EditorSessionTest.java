package fr.blueprint.client.editor;

import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.PinKind;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.NodeShape;
import fr.blueprint.core.graph.NodeTypeLookup;
import fr.blueprint.core.graph.Vec2d;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorSessionTest {

    private static final Identifier TYPE = Identifier.fromNamespaceAndPath("test", "node");

    private static final NodeShape SHAPE = new NodeShape(
            List.of(new NodeShape.PinDef("exec_in", PinKind.EXEC, PinTypes.EXEC, false)),
            List.of(new NodeShape.PinDef("exec_out", PinKind.EXEC, PinTypes.EXEC, false)),
            false, Permission.SAFE);

    private static final NodeTypeLookup LOOKUP = typeId -> SHAPE;

    private static Blueprint graph() {
        Blueprint bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "session"));
        new EditOperation.AddNode(UUID.randomUUID(), TYPE, new Vec2d(0, 0)).apply(bp, LOOKUP);
        return bp;
    }

    @Test
    void saleParComparaisonDeRevisions() {
        Blueprint bp = graph();
        EditorSession session = EditorSession.of(bp, snapshot -> true);
        assertFalse(session.dirty());

        new EditOperation.AddNode(UUID.randomUUID(), TYPE, new Vec2d(100, 0)).apply(bp, LOOKUP);
        assertTrue(session.dirty());

        assertTrue(session.save());
        assertFalse(session.dirty());
    }

    @Test
    void lEnregistrementPousseUnInstantaneIndependant() {
        Blueprint bp = graph();
        AtomicReference<Blueprint> adopted = new AtomicReference<>();
        EditorSession session = EditorSession.of(bp, snapshot -> {
            adopted.set(snapshot);
            return true;
        });

        new EditOperation.AddNode(UUID.randomUUID(), TYPE, new Vec2d(100, 0)).apply(bp, LOOKUP);
        assertTrue(session.save());
        assertNotNull(adopted.get());
        assertNotSame(bp, adopted.get());
        assertTrue(bp.contentEquals(adopted.get()));

        // Éditer APRÈS l'enregistrement ne mute pas l'objet adopté.
        new EditOperation.AddNode(UUID.randomUUID(), TYPE, new Vec2d(200, 0)).apply(bp, LOOKUP);
        assertEquals(2, adopted.get().nodes().size());
        assertEquals(3, bp.nodes().size());
        assertTrue(session.dirty());
    }

    @Test
    void unEchecDEnregistrementResteSale() {
        Blueprint bp = graph();
        EditorSession session = EditorSession.of(bp, snapshot -> false);
        new EditOperation.AddNode(UUID.randomUUID(), TYPE, new Vec2d(100, 0)).apply(bp, LOOKUP);
        assertFalse(session.save());
        assertTrue(session.dirty());
    }

    @Test
    void laSessionJetableNEstJamaisSale() {
        Blueprint bp = graph();
        EditorSession session = EditorSession.scratch(bp);
        assertFalse(session.savable());
        new EditOperation.AddNode(UUID.randomUUID(), TYPE, new Vec2d(100, 0)).apply(bp, LOOKUP);
        assertFalse(session.dirty());
        assertFalse(session.save());
    }
}
