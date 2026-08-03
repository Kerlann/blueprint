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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Navigation clavier entre nœuds (U5, story 9.4). */
class KeyboardNavTest {

    private static final Identifier TYPE = Identifier.fromNamespaceAndPath("test", "node");
    private static final NodeShape SHAPE = new NodeShape(
            List.of(new NodeShape.PinDef("exec_in", PinKind.EXEC, PinTypes.EXEC, false)),
            List.of(new NodeShape.PinDef("exec_out", PinKind.EXEC, PinTypes.EXEC, false)),
            false, Permission.SAFE);
    private static final NodeTypeLookup LOOKUP = typeId -> SHAPE;

    private static final UUID CENTER = UUID.nameUUIDFromBytes("center".getBytes());
    private static final UUID RIGHT = UUID.nameUUIDFromBytes("right".getBytes());
    private static final UUID FAR_RIGHT = UUID.nameUUIDFromBytes("far".getBytes());
    private static final UUID BELOW = UUID.nameUUIDFromBytes("below".getBytes());
    private static final UUID DIAGONAL = UUID.nameUUIDFromBytes("diagonal".getBytes());

    /** Une croix de nœuds autour du centre, plus un nœud en diagonale. */
    private static Blueprint graph() {
        Blueprint bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "nav"));
        add(bp, CENTER, 0, 0);
        add(bp, RIGHT, 200, 0);
        add(bp, FAR_RIGHT, 600, 0);
        add(bp, BELOW, 0, 200);
        add(bp, DIAGONAL, 220, 220);
        return bp;
    }

    private static void add(Blueprint bp, UUID uuid, double x, double y) {
        new EditOperation.AddNode(uuid, TYPE, new Vec2d(x, y)).apply(bp, LOOKUP);
    }

    @Test
    void arrowsMoveToTheNearestNodeInThatDirection() {
        Blueprint bp = graph();
        assertEquals(RIGHT, KeyboardNav.next(bp, CENTER, 1, 0));
        assertEquals(BELOW, KeyboardNav.next(bp, CENTER, 0, 1));
        assertEquals(CENTER, KeyboardNav.next(bp, RIGHT, -1, 0));
        assertEquals(CENTER, KeyboardNav.next(bp, BELOW, 0, -1));
    }

    /** Le décalage latéral compte double : la sélection ne part pas en diagonale. */
    @Test
    void aLateralNodeLosesAgainstAnAlignedOne() {
        Blueprint bp = graph();
        // À droite : « right » (200, aligné) plutôt que « diagonal » (220 mais décalé).
        assertEquals(RIGHT, KeyboardNav.next(bp, CENTER, 1, 0));
        // En partant de « right », vers le bas : « diagonal » est le seul candidat.
        assertEquals(DIAGONAL, KeyboardNav.next(bp, RIGHT, 0, 1));
    }

    @Test
    void thereIsNothingBeyondTheLastNode() {
        Blueprint bp = graph();
        assertNull(KeyboardNav.next(bp, FAR_RIGHT, 1, 0));
        assertNull(KeyboardNav.next(bp, CENTER, -1, 0), "rien à gauche du centre");
    }

    @Test
    void withoutSelectionNavigationStartsTopLeft() {
        Blueprint bp = graph();
        assertEquals(CENTER, KeyboardNav.next(bp, null, 1, 0));
        assertEquals(CENTER, KeyboardNav.firstInReadingOrder(bp));
        assertNull(KeyboardNav.firstInReadingOrder(
                new Blueprint(Identifier.fromNamespaceAndPath("test", "vide"))));
    }

    /** Un nœud sélectionné qui n'existe plus (supprimé) ne bloque pas la navigation. */
    @Test
    void aStaleSelectionFallsBackToTheFirstNode() {
        Blueprint bp = graph();
        assertEquals(CENTER, KeyboardNav.next(bp, UUID.randomUUID(), 1, 0));
    }

    @Test
    void navigationIsDeterministicWhenTwoNodesOverlap() {
        Blueprint bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "pile"));
        UUID a = UUID.nameUUIDFromBytes("aaa".getBytes());
        UUID b = UUID.nameUUIDFromBytes("bbb".getBytes());
        add(bp, CENTER, 0, 0);
        add(bp, a, 100, 0);
        add(bp, b, 100, 0);
        UUID first = KeyboardNav.next(bp, CENTER, 1, 0);
        assertEquals(first, KeyboardNav.next(bp, CENTER, 1, 0));
        assertEquals(a.compareTo(b) < 0 ? a : b, first, "départage par identifiant");
    }
}
