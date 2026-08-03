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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Banc de rendu de la story 5.1 (NFR1) : la passe CPU par image — culling,
 * transformations monde→écran, construction de la liste de dessin — doit rester
 * très en dessous du budget de 16,6 ms à 60 fps pour 500 nœuds. Le GPU n'étant pas
 * mesurable sans jeu, la mesure fps réelle est consignée comme VERIFY-5.1.
 */
class CanvasBenchTest {

    private static final int NODES = 500;
    private static final int COLS = 25;
    private static final double SPACING_X = 200;
    private static final double SPACING_Y = 120;
    private static final int WARMUP_FRAMES = 200;
    private static final int MEASURED_FRAMES = 1_000;
    /** Seuil volontairement large (machine CI inconnue), 8× sous le budget d'image. */
    private static final double BUDGET_NANOS_PER_FRAME = 2_000_000;

    private static final int SCREEN_W = 854;
    private static final int SCREEN_H = 480;

    private static final Identifier TYPE = Identifier.fromNamespaceAndPath("bench", "node");

    private static final NodeShape SHAPE = new NodeShape(
            List.of(new NodeShape.PinDef("exec_in", PinKind.EXEC, PinTypes.EXEC, false),
                    new NodeShape.PinDef("a", PinKind.DATA, PinTypes.DOUBLE, false),
                    new NodeShape.PinDef("b", PinKind.DATA, PinTypes.DOUBLE, false)),
            List.of(new NodeShape.PinDef("exec_out", PinKind.EXEC, PinTypes.EXEC, false),
                    new NodeShape.PinDef("out", PinKind.DATA, PinTypes.DOUBLE, false)),
            false, Permission.SAFE);

    private static final NodeTypeLookup LOOKUP = typeId -> SHAPE;

    @Test
    void passeCpuSousLeBudgetPour500Noeuds() {
        Blueprint bp = grille(NODES);
        NodeGeometry geometry = new NodeGeometry();
        Camera camera = new Camera();
        List<NodeGeometry.Box> visible = new ArrayList<>();
        long checksum = 0;

        // Échauffement JIT + remplissage du cache de géométrie.
        for (int f = 0; f < WARMUP_FRAMES; f++) {
            checksum += frame(bp, geometry, camera, visible, f);
        }

        // MEILLEURE des trois séries : une moyenne sur une seule série reste à la merci
        // d'une préemption de l'ordonnanceur sur un runner partagé. Le minimum mesure ce
        // que la machine sait faire — une vraie régression le fait monter tout autant.
        double avgNanos = Double.MAX_VALUE;
        for (int batch = 0; batch < 3; batch++) {
            long start = System.nanoTime();
            for (int f = 0; f < MEASURED_FRAMES; f++) {
                checksum += frame(bp, geometry, camera, visible, f);
            }
            avgNanos = Math.min(avgNanos, (System.nanoTime() - start) / (double) MEASURED_FRAMES);
        }

        // Le checksum empêche l'élimination de code mort par le JIT.
        assertTrue(checksum != 0, "le banc doit réellement visiter des nœuds");
        assertTrue(avgNanos < BUDGET_NANOS_PER_FRAME, String.format(
                "passe CPU moyenne %.0f ns/image, budget %.0f ns (NFR1)",
                avgNanos, BUDGET_NANOS_PER_FRAME));
    }

    /** Une image : pan nerveux, zoom périodique, culling, transformation des visibles. */
    private static long frame(Blueprint bp, NodeGeometry geometry, Camera camera,
                              List<NodeGeometry.Box> visible, int f) {
        camera.panByScreen((f % 7) - 3, (f % 5) - 2);
        if (f % 60 == 0) {
            camera.zoomBy(f % 120 == 0 ? 1 : -1, SCREEN_W / 2.0, SCREEN_H / 2.0);
        }
        List<NodeGeometry.Box> boxes = geometry.boxes(bp, LOOKUP);
        Camera.Rect view = camera.visibleRect(SCREEN_W, SCREEN_H);
        visible.clear();
        for (int i = 0; i < boxes.size(); i++) {
            NodeGeometry.Box b = boxes.get(i);
            if (view.intersects(b.x(), b.y(), b.width(), b.height())) {
                visible.add(b);
            }
        }
        long acc = 0;
        for (int i = 0; i < visible.size(); i++) {
            NodeGeometry.Box b = visible.get(i);
            acc += Math.round(camera.toScreenX(b.x())) + Math.round(camera.toScreenY(b.y()))
                    + Math.round(camera.toScreenX(b.x() + b.width()))
                    + Math.round(camera.toScreenY(b.y() + b.height()));
        }
        return acc;
    }

    @Test
    void leCullingNeGardeQueLesNoeudsDansLeChamp() {
        Blueprint bp = grille(NODES);
        NodeGeometry geometry = new NodeGeometry();
        Camera camera = new Camera(); // origine (0,0), zoom 1

        List<NodeGeometry.Box> boxes = geometry.boxes(bp, LOOKUP);
        assertEquals(NODES, boxes.size());

        Camera.Rect view = camera.visibleRect(SCREEN_W, SCREEN_H);
        int visibles = 0;
        for (NodeGeometry.Box b : boxes) {
            if (view.intersects(b.x(), b.y(), b.width(), b.height())) {
                visibles++;
            }
        }
        // Grille au pas de 200×120, boîtes 140×54, écran 854×480 depuis l'origine :
        // colonnes 0–4 (x = 0..800 < 854) et rangées 0–3 (y = 0..360 < 480) → 20 nœuds.
        assertEquals(20, visibles);
    }

    private static Blueprint grille(int count) {
        Blueprint bp = new Blueprint(Identifier.fromNamespaceAndPath("bench", "grid"));
        for (int i = 0; i < count; i++) {
            Vec2d pos = new Vec2d((i % COLS) * SPACING_X, (i / COLS) * SPACING_Y);
            EditOperation.Result r = new EditOperation.AddNode(UUID.randomUUID(), TYPE, pos)
                    .apply(bp, LOOKUP);
            assertTrue(r.applied());
        }
        return bp;
    }
}
