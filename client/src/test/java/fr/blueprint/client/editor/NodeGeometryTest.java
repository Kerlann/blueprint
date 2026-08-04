package fr.blueprint.client.editor;

import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.PinKind;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.Node;
import fr.blueprint.core.graph.NodeShape;
import fr.blueprint.core.graph.NodeTypeLookup;
import fr.blueprint.core.graph.Vec2d;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeGeometryTest {

    private static final Identifier TYPE = Identifier.fromNamespaceAndPath("test", "box");

    private static final NodeShape SHAPE = new NodeShape(
            List.of(new NodeShape.PinDef("exec_in", PinKind.EXEC, PinTypes.EXEC, false),
                    new NodeShape.PinDef("a", PinKind.DATA, PinTypes.DOUBLE, false),
                    new NodeShape.PinDef("b", PinKind.DATA, PinTypes.DOUBLE, false)),
            List.of(new NodeShape.PinDef("exec_out", PinKind.EXEC, PinTypes.EXEC, false)),
            false, Permission.SAFE);

    private static final NodeTypeLookup LOOKUP = typeId -> TYPE.equals(typeId) ? SHAPE : null;

    @Test
    void hauteurDeriveeDuNombreDePins() {
        Node node = new Node(UUID.randomUUID(), TYPE, new Vec2d(10, 20));
        NodeGeometry.Box box = NodeGeometry.boxOf(node, SHAPE);
        assertEquals(10, box.x());
        assertEquals(20, box.y());
        assertEquals(NodeGeometry.WIDTH, box.width());
        // 3 entrées / 1 sortie → 3 rangées.
        assertEquals(NodeGeometry.TITLE_HEIGHT + 3 * NodeGeometry.ROW_HEIGHT, box.height());
        assertFalse(box.ghost());
    }

    /** Une lecture de variable, nommée — la pastille à la manière d'Unreal. */
    private static Node pill(String name) {
        Node node = new Node(UUID.randomUUID(), fr.blueprint.core.graph.VarNodes.GET,
                new Vec2d(0, 0));
        fr.blueprint.core.graph.GraphLoader.setLiteral(node, "var",
                fr.blueprint.api.pin.LiteralValue.of(PinTypes.STRING, name));
        return node;
    }

    /**
     * <b>Le test qui compte.</b> Une pastille n'a <b>pas d'en-tête</b>, et son pin tombe
     * au milieu de sa seule rangée.
     *
     * <p>C'est la place du pin qui décide de tout : le fil s'y raccroche, le clic l'y
     * cherche, et le dessin l'y pose. Les trois lisent la même fonction — quand ils
     * calculaient chacun la leur, ce projet a passé une story à s'en apercevoir.
     */
    @Test
    void unePastilleNAPasDEnTeteEtSonPinTombeAuMilieu() {
        NodeGeometry.Box box = NodeGeometry.boxOf(pill("argent"), null);

        assertTrue(NodeGeometry.titleHeight(box) < NodeGeometry.TITLE_HEIGHT / 2,
                "une pastille n'a pas de bandeau, tout au plus un retrait");
        double ordinaire = NodeGeometry.boxOf(
                new Node(UUID.randomUUID(), TYPE, new Vec2d(0, 0)), SHAPE).height();
        assertTrue(box.height() < ordinaire,
                "elle doit être plus compacte qu'un nœud ordinaire : "
                        + box.height() + " contre " + ordinaire);
        assertEquals(box.y() + box.height() / 2,
                NodeGeometry.outputPinCenter(box, 0).y(), 1.0,
                "le pin doit tomber au milieu de la pastille, pas sous un en-tête absent");
    }

    /**
     * La largeur suit le nom, entre deux bornes.
     *
     * <p>Une largeur fixe tronquerait les noms longs ou laisserait un vide ridicule
     * derrière les courts — et c'est justement ce que la forme en pastille sert à éviter.
     */
    @Test
    void laLargeurDUnePastilleSuitSonNom() {
        double courte = NodeGeometry.boxOf(pill("or"), null).width();
        double longue = NodeGeometry.boxOf(pill("solde_du_joueur_courant"), null).width();

        assertTrue(longue > courte, "un nom long doit élargir la pastille");
        assertEquals(NodeGeometry.PILL_MIN_WIDTH, courte,
                "un nom court garde une largeur minimale");
        assertTrue(longue <= NodeGeometry.WIDTH,
                "et jamais plus large qu'un nœud ordinaire : " + longue);
    }

    /** Un nœud ordinaire garde son en-tête : la pastille est un cas, pas la règle. */
    @Test
    void unNoeudOrdinaireGardeSonEnTete() {
        Node node = new Node(UUID.randomUUID(), TYPE, new Vec2d(0, 0));
        NodeGeometry.Box box = NodeGeometry.boxOf(node, SHAPE);
        assertEquals(NodeGeometry.TITLE_HEIGHT, NodeGeometry.titleHeight(box));
    }

    @Test
    void formeInconnueDonneUnFantomeATailleParDefaut() {
        Node node = new Node(UUID.randomUUID(), Identifier.fromNamespaceAndPath("gone", "node"),
                new Vec2d(0, 0));
        NodeGeometry.Box box = NodeGeometry.boxOf(node, null);
        assertTrue(box.ghost());
        assertEquals(NodeGeometry.TITLE_HEIGHT + 3 * NodeGeometry.ROW_HEIGHT, box.height());
    }

    @Test
    void cacheInvalideParLaRevisionDuBlueprint() {
        Blueprint bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "cache"));
        apply(bp, new EditOperation.AddNode(UUID.randomUUID(), TYPE, new Vec2d(0, 0)));

        NodeGeometry geometry = new NodeGeometry();
        List<NodeGeometry.Box> first = geometry.boxes(bp, LOOKUP);
        assertEquals(1, first.size());
        // Même révision → même liste, aucun recalcul.
        assertSame(first, geometry.boxes(bp, LOOKUP));

        apply(bp, new EditOperation.AddNode(UUID.randomUUID(), TYPE, new Vec2d(300, 0)));
        List<NodeGeometry.Box> second = geometry.boxes(bp, LOOKUP);
        assertEquals(2, second.size());
    }

    @Test
    void positionsDesPins() {
        Node node = new Node(UUID.randomUUID(), TYPE, new Vec2d(100, 200));
        NodeGeometry.Box box = NodeGeometry.boxOf(node, SHAPE);
        // Rangée 0 : centrée dans la première ligne sous le titre.
        assertEquals(new Vec2d(100 + NodeGeometry.PIN_INSET,
                        200 + NodeGeometry.TITLE_HEIGHT + NodeGeometry.ROW_HEIGHT / 2),
                NodeGeometry.inputPinCenter(box, 0));
        // Rangée 2, côté sortie : bord droit.
        assertEquals(new Vec2d(100 + NodeGeometry.WIDTH - NodeGeometry.PIN_INSET,
                        200 + NodeGeometry.TITLE_HEIGHT + 2 * NodeGeometry.ROW_HEIGHT
                                + NodeGeometry.ROW_HEIGHT / 2),
                NodeGeometry.outputPinCenter(box, 2));
    }

    @Test
    void boiteEnglobante() {
        Node a = new Node(UUID.randomUUID(), TYPE, new Vec2d(0, 0));
        Node b = new Node(UUID.randomUUID(), TYPE, new Vec2d(500, -100));
        Camera.Rect bounds = NodeGeometry.boundsOf(List.of(
                NodeGeometry.boxOf(a, SHAPE), NodeGeometry.boxOf(b, SHAPE)));
        assertEquals(0, bounds.left());
        assertEquals(-100, bounds.top());
        assertEquals(500 + NodeGeometry.WIDTH, bounds.right());
        assertEquals(NodeGeometry.TITLE_HEIGHT + 3 * NodeGeometry.ROW_HEIGHT, bounds.bottom());

        Camera.Rect vide = NodeGeometry.boundsOf(List.of());
        assertEquals(0, vide.left());
        assertEquals(0, vide.right());
    }

    private static void apply(Blueprint bp, EditOperation op) {
        assertTrue(op.apply(bp, LOOKUP).applied());
    }
}
