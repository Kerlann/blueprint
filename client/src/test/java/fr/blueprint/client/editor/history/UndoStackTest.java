package fr.blueprint.client.editor.history;

import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.PinKind;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.NodeShape;
import fr.blueprint.core.graph.NodeTypeLookup;
import fr.blueprint.core.graph.Vec2d;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UndoStackTest {

    private static final Identifier TYPE = Identifier.fromNamespaceAndPath("test", "node");

    private static final NodeShape SHAPE = new NodeShape(
            List.of(new NodeShape.PinDef("exec_in", PinKind.EXEC, PinTypes.EXEC, false)),
            List.of(new NodeShape.PinDef("exec_out", PinKind.EXEC, PinTypes.EXEC, false)),
            false, Permission.SAFE);

    private static final NodeTypeLookup LOOKUP = typeId -> SHAPE;

    private Blueprint bp;
    private UndoStack stack;
    private UUID n1;
    private UUID n2;

    @BeforeEach
    void setUp() {
        bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "undo"));
        stack = new UndoStack();
        n1 = UUID.randomUUID();
        n2 = UUID.randomUUID();
        new EditOperation.AddNode(n1, TYPE, new Vec2d(0, 0)).apply(bp, LOOKUP);
        new EditOperation.AddNode(n2, TYPE, new Vec2d(200, 0)).apply(bp, LOOKUP);
    }

    private void tracked(EditOperation op) {
        EditOperation.Result r = op.apply(bp, LOOKUP);
        assertTrue(r.applied());
        stack.record(r.inverse());
    }

    @Test
    void unGesteDe40DeplacementsEstUneSeuleEntree() {
        stack.beginGesture();
        for (int i = 1; i <= 40; i++) {
            tracked(new EditOperation.MoveNode(n1, new Vec2d(i * 10, 0)));
        }
        stack.endGesture();
        assertEquals(1, stack.undoDepth());

        assertTrue(stack.undo(bp, LOOKUP));
        assertEquals(new Vec2d(0, 0), bp.node(n1).position());
        assertTrue(stack.redo(bp, LOOKUP));
        assertEquals(new Vec2d(400, 0), bp.node(n1).position());
    }

    @Test
    void suppressionAvecLiensRestaureeParUndo() {
        tracked(new EditOperation.AddLink(new Link(n1, "exec_out", n2, "exec_in")));
        stack.beginGesture();
        tracked(new EditOperation.RemoveNode(n1));
        stack.endGesture();
        assertNull(bp.node(n1));
        assertTrue(bp.links().isEmpty());

        assertTrue(stack.undo(bp, LOOKUP));
        assertNotNull(bp.node(n1));
        assertEquals(1, bp.links().size());

        assertTrue(stack.redo(bp, LOOKUP));
        assertNull(bp.node(n1));
        assertTrue(bp.links().isEmpty());
    }

    @Test
    void uneNouvelleActionPurgeLeRedo() {
        tracked(new EditOperation.MoveNode(n1, new Vec2d(50, 0)));
        assertTrue(stack.undo(bp, LOOKUP));
        assertTrue(stack.canRedo());

        tracked(new EditOperation.MoveNode(n1, new Vec2d(99, 0)));
        assertFalse(stack.canRedo());
    }

    @Test
    void leGesteVideNeCreePasDEntree() {
        stack.beginGesture();
        stack.endGesture();
        assertFalse(stack.canUndo());
    }

    @Test
    void plafondDe50Entrees() {
        for (int i = 1; i <= 60; i++) {
            tracked(new EditOperation.MoveNode(n1, new Vec2d(i, 0)));
        }
        assertEquals(UndoStack.MAX_ENTRIES, stack.undoDepth());
        int undone = 0;
        while (stack.undo(bp, LOOKUP)) {
            undone++;
        }
        assertEquals(UndoStack.MAX_ENTRIES, undone);
        // Les 10 plus anciennes sont tombées : on revient à la position 10, pas 0.
        assertEquals(new Vec2d(10, 0), bp.node(n1).position());
    }

    @Test
    void undoPendantUnGesteLeTermineDAbord() {
        stack.beginGesture();
        tracked(new EditOperation.MoveNode(n1, new Vec2d(70, 0)));
        assertTrue(stack.undo(bp, LOOKUP));
        assertEquals(new Vec2d(0, 0), bp.node(n1).position());
    }
}
