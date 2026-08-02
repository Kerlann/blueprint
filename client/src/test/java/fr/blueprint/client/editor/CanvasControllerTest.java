package fr.blueprint.client.editor;

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

class CanvasControllerTest {

    private static final Identifier TYPE = Identifier.fromNamespaceAndPath("test", "node");

    private static final NodeShape SHAPE = new NodeShape(
            List.of(new NodeShape.PinDef("exec_in", PinKind.EXEC, PinTypes.EXEC, false),
                    new NodeShape.PinDef("a", PinKind.DATA, PinTypes.DOUBLE, false)),
            List.of(new NodeShape.PinDef("exec_out", PinKind.EXEC, PinTypes.EXEC, false)),
            false, Permission.SAFE);

    private static final NodeTypeLookup LOOKUP = typeId -> SHAPE;

    private Blueprint bp;
    private Camera camera;
    private CanvasController controller;
    private UUID n1;
    private UUID n2;

    @BeforeEach
    void setUp() {
        bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "graph"));
        n1 = addNode(0, 0);
        n2 = addNode(300, 100);
        camera = new Camera();
        controller = new CanvasController(bp, LOOKUP, camera);
    }

    private UUID addNode(double x, double y) {
        UUID id = UUID.randomUUID();
        assertTrue(new EditOperation.AddNode(id, TYPE, new Vec2d(x, y)).apply(bp, LOOKUP).applied());
        return id;
    }

    @Test
    void presseSurUnNoeudLeSelectionneEtDemarreLeDeplacement() {
        controller.press(10, 10, false);
        assertTrue(controller.selection().isSelected(n1));
        assertEquals(CanvasController.Gesture.MOVE, controller.gesture());

        controller.drag(60, 35);
        controller.release(false);
        // Saisi en (10,10), nœud en (0,0) : offset (−10,−10) → cible (50,25).
        assertEquals(new Vec2d(50, 25), bp.node(n1).position());
        assertEquals(CanvasController.Gesture.NONE, controller.gesture());
    }

    @Test
    void deplacementMultiSelectionConserveLesEcarts() {
        controller.selection().selectAll(List.of(n1, n2), false);

        controller.press(10, 10, false);
        controller.drag(26, 42);
        controller.release(false);
        assertEquals(new Vec2d(16, 32), bp.node(n1).position());
        assertEquals(new Vec2d(316, 132), bp.node(n2).position());
    }

    @Test
    void deplacementAvecAccroche() {
        camera.toggleSnap();
        controller.press(5, 5, false);
        controller.drag(30, 30);
        controller.release(false);
        // Cible brute (25,25) → accrochée (32,32) : jamais de dérive incrémentale.
        assertEquals(new Vec2d(32, 32), bp.node(n1).position());
    }

    @Test
    void rectangleElastiqueSelectionne() {
        controller.press(-50, -50, false);
        assertEquals(CanvasController.Gesture.RUBBER, controller.gesture());
        controller.drag(320, 120);
        assertNotNull(controller.rubberRect());
        controller.release(false);
        assertTrue(controller.selection().isSelected(n1));
        assertTrue(controller.selection().isSelected(n2));
        assertNull(controller.rubberRect());
    }

    @Test
    void rectangleAdditifConserveLExistant() {
        controller.press(10, 10, false);
        controller.release(false);
        assertTrue(controller.selection().isSelected(n1));

        controller.press(290, 90, true);
        controller.drag(320, 120);
        controller.release(true);
        assertEquals(2, controller.selection().size());
    }

    @Test
    void rectangleNonAdditifVideALaPresse() {
        controller.selection().selectAll(List.of(n1, n2), false);
        controller.press(-500, -500, false);
        assertTrue(controller.selection().isEmpty());
        controller.release(false);
        assertTrue(controller.selection().isEmpty());
    }

    @Test
    void suppressionRetireNoeudsEtLiens() {
        assertTrue(new EditOperation.AddLink(new Link(n1, "exec_out", n2, "exec_in"))
                .apply(bp, LOOKUP).applied());
        assertEquals(1, bp.links().size());

        controller.press(10, 10, false);
        controller.release(false);
        controller.deleteSelection();

        assertNull(bp.node(n1));
        assertNotNull(bp.node(n2));
        assertTrue(bp.links().isEmpty());
        assertTrue(controller.selection().isEmpty());
    }

    @Test
    void lesInversesSontCollectesDansLOrdre() {
        controller.press(10, 10, false);
        controller.drag(60, 35);
        controller.release(false);
        int apresDeplacement = controller.inverses().size();
        assertTrue(apresDeplacement >= 1);

        controller.deleteSelection();
        assertEquals(apresDeplacement + 1, controller.inverses().size());
        // Le dernier inverse restaure le nœud supprimé.
        EditOperation dernier = controller.inverses().get(controller.inverses().size() - 1);
        assertTrue(dernier.apply(bp, LOOKUP).applied());
        assertNotNull(bp.node(n1));
    }

    @Test
    void hitTestAttrapeLeNoeudDessineAuDessus() {
        // n3 chevauche n1 et est inséré après : il se dessine au-dessus.
        UUID n3 = addNode(20, 10);
        NodeGeometry.Box hit = controller.hitTest(25, 15);
        assertNotNull(hit);
        assertEquals(n3, hit.node().uuid());
        assertFalse(controller.selection().isSelected(n3));
    }

    @Test
    void shiftDeclicSurUnNoeudSelectionne() {
        controller.selection().selectAll(List.of(n1, n2), false);
        controller.press(10, 10, true);
        assertFalse(controller.selection().isSelected(n1));
        assertTrue(controller.selection().isSelected(n2));
    }
}
