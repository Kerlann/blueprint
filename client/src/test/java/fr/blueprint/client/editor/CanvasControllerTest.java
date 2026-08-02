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
    void unGesteDeDeplacementSAnnuleEnUneFois() {
        controller.press(10, 10, false);
        controller.drag(60, 35);
        controller.drag(80, 55);
        controller.release(false);
        assertEquals(1, controller.history().undoDepth());
        assertEquals(new Vec2d(70, 45), bp.node(n1).position());

        assertTrue(controller.undo());
        assertEquals(new Vec2d(0, 0), bp.node(n1).position());
        assertTrue(controller.redo());
        assertEquals(new Vec2d(70, 45), bp.node(n1).position());
    }

    @Test
    void laSuppressionSAnnule() {
        controller.press(10, 10, false);
        controller.release(false);
        controller.deleteSelection();
        assertNull(bp.node(n1));

        assertTrue(controller.undo());
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

    // ------------------------------------------------------------- câblage (5.3)

    private Vec2d out1() {
        return NodeGeometry.outputPinCenter(controller.boxOf(n1), 0);
    }

    private Vec2d in2(int row) {
        return NodeGeometry.inputPinCenter(controller.boxOf(n2), row);
    }

    @Test
    void cablageParGlisserDePinAPin() {
        Vec2d from = out1();
        controller.press(from.x(), from.y(), false);
        assertEquals(CanvasController.Gesture.WIRE, controller.gesture());
        assertNotNull(controller.wireFrom());

        Vec2d to = in2(0); // exec_in
        controller.drag(to.x(), to.y());
        assertNull(controller.release(false));
        assertEquals(1, bp.links().size());
        Link link = bp.links().iterator().next();
        assertEquals(n1, link.fromNode());
        assertEquals("exec_out", link.fromPin());
        assertEquals("exec_in", link.toPin());
    }

    @Test
    void lienInvalideRefuseParCanLink() {
        Vec2d from = out1(); // exec_out
        controller.press(from.x(), from.y(), false);
        Vec2d to = in2(1); // « a » : pin data double — kind incompatible
        controller.drag(to.x(), to.y());
        controller.release(false);
        assertTrue(bp.links().isEmpty());
    }

    @Test
    void relacheDansLeVideSignaleLaPalette() {
        Vec2d from = out1();
        controller.press(from.x(), from.y(), false);
        controller.drag(700, 400);
        CanvasController.WireDrop drop = controller.release(false);
        assertNotNull(drop);
        assertEquals("exec_out", drop.from().pin());
        assertEquals(700, drop.worldX());
        assertTrue(bp.links().isEmpty());
    }

    @Test
    void altClicDetacheLesLiensDuPin() {
        assertTrue(new EditOperation.AddLink(new Link(n1, "exec_out", n2, "exec_in"))
                .apply(bp, LOOKUP).applied());
        Vec2d pin = in2(0);
        controller.press(pin.x(), pin.y(), false, true);
        assertTrue(bp.links().isEmpty());
        assertEquals(CanvasController.Gesture.NONE, controller.gesture());
    }

    @Test
    void compatibiliteViaCanLink() {
        CanvasController.PinRef from = controller.pinAt(out1().x(), out1().y());
        assertNotNull(from);
        assertTrue(controller.canConnect(from, n2, "exec_in", false));
        assertFalse(controller.canConnect(from, n2, "a", false));
        // Même direction : jamais.
        assertFalse(controller.canConnect(from, n2, "exec_out", true));
        // Pin inexistant : refusé par le validateur.
        assertFalse(controller.canConnect(from, n2, "inconnu", false));
    }

    // ----------------------------------------------------------- littéraux (5.2b)

    @Test
    void zoneLitteraleDUnPinDataNonCable() {
        // Rangée 1 de n1 : pin data « a ». La zone est à droite du label.
        NodeGeometry.Box box = controller.boxOf(n1);
        Camera.Rect zone = NodeGeometry.literalZone(box, 1);
        double cx = (zone.left() + zone.right()) / 2;
        double cy = (zone.top() + zone.bottom()) / 2;

        CanvasController.LiteralRef ref = controller.literalAt(cx, cy);
        assertNotNull(ref);
        assertEquals(n1, ref.node());
        assertEquals("a", ref.pin());
        assertEquals(1, ref.row());

        // Rangée 0 : exec — jamais de littéral.
        Camera.Rect execZone = NodeGeometry.literalZone(box, 0);
        assertNull(controller.literalAt((execZone.left() + execZone.right()) / 2,
                (execZone.top() + execZone.bottom()) / 2));
    }

    @Test
    void unPinCableMasqueSonLitteral() {
        // Le lien prime : dès que « a » est câblé depuis une sortie data, sa zone
        // littérale disparaît (AC1 5.2b).
        NodeShape withDataOut = new NodeShape(
                List.of(),
                List.of(new NodeShape.PinDef("out", PinKind.DATA, PinTypes.DOUBLE, false)),
                false, Permission.SAFE);
        Identifier srcType = Identifier.fromNamespaceAndPath("test", "src");
        NodeTypeLookup lookup2 = typeId -> srcType.equals(typeId) ? withDataOut : SHAPE;
        Blueprint bp2 = new Blueprint(Identifier.fromNamespaceAndPath("test", "wired"));
        UUID src = UUID.randomUUID();
        UUID dst = UUID.randomUUID();
        assertTrue(new EditOperation.AddNode(src, srcType, new Vec2d(0, 0)).apply(bp2, lookup2).applied());
        assertTrue(new EditOperation.AddNode(dst, TYPE, new Vec2d(300, 0)).apply(bp2, lookup2).applied());
        CanvasController c2 = new CanvasController(bp2, lookup2, new Camera());

        Camera.Rect zone = NodeGeometry.literalZone(c2.boxOf(dst), 1);
        double cx = (zone.left() + zone.right()) / 2;
        double cy = (zone.top() + zone.bottom()) / 2;
        assertNotNull(c2.literalAt(cx, cy));

        assertTrue(new EditOperation.AddLink(new Link(src, "out", dst, "a")).apply(bp2, lookup2).applied());
        assertNull(c2.literalAt(cx, cy));
    }

    @Test
    void setLiteralPasseParLOperationEtSAnnule() {
        int avant = controller.history().undoDepth();
        assertTrue(controller.setLiteral(n1, "a",
                fr.blueprint.api.pin.LiteralValue.of(PinTypes.DOUBLE, 2.5)));
        assertEquals(2.5, bp.node(n1).literal("a").value());
        assertEquals(avant + 1, controller.history().undoDepth());
        assertTrue(controller.undo());
        assertNull(bp.node(n1).literal("a"));
        // Type incompatible : refusé par SetLiteral, rien de collecté.
        assertFalse(controller.setLiteral(n1, "a",
                fr.blueprint.api.pin.LiteralValue.of(PinTypes.STRING, "nope")));
    }

    @Test
    void insertionDepuisLaPaletteAvecAutoConnexion() {
        camera.toggleSnap();
        CanvasController.PinRef from = controller.pinAt(out1().x(), out1().y());
        assertNotNull(from);
        UUID inserted = controller.insertNode(TYPE, 601, 299, from);
        assertNotNull(inserted);
        // Accroche au pas de 16 : (601, 299) → (608, 304).
        assertEquals(new Vec2d(608, 304), bp.node(inserted).position());
        assertEquals(1, bp.links().size());
        Link link = bp.links().iterator().next();
        assertEquals(inserted, link.toNode());
        assertEquals("exec_in", link.toPin());
    }
}
