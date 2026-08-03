package fr.blueprint.core.script;

import fr.blueprint.core.event.StandardEvents;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.Vec2d;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoLayoutTest {

    private static final PluginLoader.LoadedRegistries LOADED =
            PluginLoader.load(List.of(), true);

    private static Identifier node(String path) {
        return Identifier.fromNamespaceAndPath("blueprint", path);
    }

    @Test
    void couchesParFluxExecEtPurEntreLesColonnes() {
        Blueprint bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "layout"));
        UUID tick = UUID.randomUUID();
        UUID wait = UUID.randomUUID();
        UUID log = UUID.randomUUID();
        UUID sum = UUID.randomUUID();
        apply(bp, new EditOperation.AddNode(tick, StandardEvents.SERVER_TICK.id(), new Vec2d(999, 999)));
        apply(bp, new EditOperation.AddNode(wait, node("flow/wait"), new Vec2d(-5, 3)));
        apply(bp, new EditOperation.AddNode(log, node("debug/log"), new Vec2d(7, -8)));
        apply(bp, new EditOperation.AddNode(sum, node("math/add"), new Vec2d(0, 0)));
        apply(bp, new EditOperation.AddLink(new Link(tick, "exec_out", wait, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(wait, "exec_out", log, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(sum, "result", log, "value")));

        Map<UUID, Vec2d> layout = AutoLayout.compute(bp, LOADED.nodes());
        assertEquals(4, layout.size());
        assertEquals(0, layout.get(tick).x());
        assertEquals(AutoLayout.COLUMN, layout.get(wait).x());
        assertEquals(2 * AutoLayout.COLUMN, layout.get(log).x());
        // Le pur se glisse entre la colonne de son consommateur et la précédente.
        assertEquals(2 * AutoLayout.COLUMN - AutoLayout.PURE_SHIFT, layout.get(sum).x());

        // Déterministe : deux calculs identiques.
        assertEquals(layout, AutoLayout.compute(bp, LOADED.nodes()));
    }

    @Test
    void lesOrphelinsExecPartentEnColonneZero() {
        Blueprint bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "orphans"));
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        apply(bp, new EditOperation.AddNode(a, node("debug/log"), new Vec2d(50, 50)));
        apply(bp, new EditOperation.AddNode(b, node("debug/log"), new Vec2d(60, 60)));
        Map<UUID, Vec2d> layout = AutoLayout.compute(bp, LOADED.nodes());
        assertEquals(0, layout.get(a).x());
        assertEquals(0, layout.get(b).x());
        // Empilés, pas superposés.
        assertTrue(Math.abs(layout.get(a).y() - layout.get(b).y()) >= AutoLayout.ROW);
    }

    private static void apply(Blueprint bp, EditOperation op) {
        assertTrue(op.apply(bp, LOADED.nodes()).applied(), op::toString);
    }
}
