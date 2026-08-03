package fr.blueprint.client.editor;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.event.StandardEvents;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.Vec2d;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScriptViewStateTest {

    private static final PluginLoader.LoadedRegistries LOADED =
            PluginLoader.load(List.of(), true);

    private final long[] now = {10_000};
    private final ScriptViewState state = new ScriptViewState(() -> now[0]);

    private record Graph(Blueprint bp, UUID tick, UUID log) {
    }

    private Graph graph() {
        Blueprint bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "script"));
        UUID tick = UUID.randomUUID();
        UUID log = UUID.randomUUID();
        assertTrue(new EditOperation.AddNode(tick, StandardEvents.SERVER_TICK.id(),
                new Vec2d(0, 0)).apply(bp, LOADED.nodes()).applied());
        assertTrue(new EditOperation.AddNode(log,
                Identifier.fromNamespaceAndPath("blueprint", "debug/log"),
                new Vec2d(200, 0)).apply(bp, LOADED.nodes()).applied());
        assertTrue(new EditOperation.SetLiteral(log, "value",
                LiteralValue.of(PinTypes.STRING, "salut")).apply(bp, LOADED.nodes()).applied());
        assertTrue(new EditOperation.AddLink(new Link(tick, "exec_out", log, "exec_in"))
                .apply(bp, LOADED.nodes()).applied());
        return new Graph(bp, tick, log);
    }

    @Test
    void correspondanceNoeudLigneDepuisLesAnnotations() {
        Graph g = graph();
        state.toggle();
        assertTrue(state.shouldRegenerate());
        state.regenerate(g.bp(), LOADED.nodes());
        assertFalse(state.lines().isEmpty());

        // Chaque nœud a sa ligne ; cliquer une ligne sans @id remonte au nœud au-dessus.
        state.syncSelection(g.tick(), 40);
        int tickLine = state.highlightedLine();
        assertTrue(tickLine >= 0);
        assertEquals(g.tick(), state.nodeAtLine(tickLine));
        assertEquals(g.tick(), state.nodeAtLine(tickLine + 0));
        assertNotNull(state.nodeAtLine(state.lines().size() - 1));
    }

    @Test
    void debounceEtResynchronisation() {
        Graph g = graph();
        state.toggle();
        state.regenerate(g.bp(), LOADED.nodes());
        assertFalse(state.shouldRegenerate());

        state.invalidate();
        now[0] += ScriptViewState.DEBOUNCE_MS - 1;
        assertFalse(state.shouldRegenerate());
        now[0] += 1;
        assertTrue(state.shouldRegenerate());

        // Replié : jamais de régénération.
        state.toggle();
        assertFalse(state.shouldRegenerate());
    }

    @Test
    void selectionSynchroniseeEtDefilement() {
        Graph g = graph();
        state.toggle();
        state.regenerate(g.bp(), LOADED.nodes());
        state.syncSelection(g.log(), 2);
        int line = state.highlightedLine();
        assertTrue(line >= 0);
        // La ligne surlignée est dans la fenêtre visible.
        assertTrue(line >= state.scroll() && line < state.scroll() + 2);

        state.syncSelection(null, 2);
        assertEquals(-1, state.highlightedLine());
        assertNull(state.nodeAtLine(-1));
    }

    @Test
    void classificationDesLignes() {
        assertEquals(ScriptViewState.LineKind.EVENT, ScriptViewState.kindOf("on blueprint:event {"));
        assertEquals(ScriptViewState.LineKind.VARIABLE, ScriptViewState.kindOf("  var double x"));
        assertEquals(ScriptViewState.LineKind.COMMENT, ScriptViewState.kindOf("// note"));
        assertEquals(ScriptViewState.LineKind.PLAIN, ScriptViewState.kindOf("  log(...)"));
    }

    @Test
    void importArmePuisConfirme() {
        assertFalse(state.armImport());
        assertTrue(state.importArmed());
        assertTrue(state.armImport());
        assertFalse(state.importArmed());
        // Désarmé par un clic ailleurs.
        state.armImport();
        state.disarmImport();
        assertFalse(state.importArmed());
    }
}
