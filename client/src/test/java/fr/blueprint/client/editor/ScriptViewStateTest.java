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

    /**
     * <b>La vue script montre les fonctions, et leurs nœuds se retrouvent</b> (story 20.2,
     * AC10).
     *
     * <p>La 20.1 a fait écrire les fonctions par le générateur ; rien ne l'avait encore
     * exercé <b>depuis la vue script</b>. Le point qui pouvait manquer n'est pas le texte
     * mais la correspondance ligne ↔ nœud : elle est bâtie sur les annotations {@code @id},
     * et un corps dont les nœuds n'en portent pas donnerait un texte qu'on peut lire et
     * dans lequel on ne peut pas naviguer.
     */
    @Test
    void laVueScriptMontreLesFonctionsEtLeursNoeudsSeRetrouvent() {
        Graph g = graph();
        assertTrue(new fr.blueprint.core.graph.FunctionOps.AddFunction(
                fr.blueprint.core.graph.BlueprintFunction.of("carre",
                        List.of(new fr.blueprint.core.graph.BlueprintFunction.Param(
                                "n", PinTypes.DOUBLE)),
                        List.of(new fr.blueprint.core.graph.BlueprintFunction.Param(
                                "r", PinTypes.DOUBLE))))
                .apply(g.bp(), LOADED.nodes()).applied());
        // Le corps est câblé pour de bon : le générateur suit le flux d'exécution depuis
        // l'entrée de la fonction, et un nœud posé à côté du fil n'apparaîtrait pas plus
        // qu'un nœud orphelin du graphe principal.
        UUID entree = UUID.randomUUID();
        assertTrue(new fr.blueprint.core.graph.FunctionOps.AddNodeIn("carre", entree,
                fr.blueprint.core.graph.FuncNodes.PARAM, new Vec2d(-160, 0))
                .apply(g.bp(), LOADED.nodes()).applied());
        assertTrue(new fr.blueprint.core.graph.FunctionOps.SetLiteralIn("carre", entree,
                fr.blueprint.core.graph.FuncNodes.FUNCTION_PIN,
                LiteralValue.of(PinTypes.STRING, "carre")).apply(g.bp(), LOADED.nodes()).applied());
        UUID dansLeCorps = UUID.randomUUID();
        assertTrue(new fr.blueprint.core.graph.FunctionOps.AddNodeIn("carre", dansLeCorps,
                Identifier.fromNamespaceAndPath("blueprint", "debug/log"), new Vec2d(0, 0))
                .apply(g.bp(), LOADED.nodes()).applied());
        assertTrue(new fr.blueprint.core.graph.FunctionOps.SetLiteralIn("carre", dansLeCorps,
                "value", LiteralValue.of(PinTypes.STRING, "dans le corps"))
                .apply(g.bp(), LOADED.nodes()).applied());
        assertTrue(new fr.blueprint.core.graph.FunctionOps.AddLinkIn("carre",
                new Link(entree, fr.blueprint.core.graph.BlueprintFunction.EXEC_OUT,
                        dansLeCorps, "exec_in")).apply(g.bp(), LOADED.nodes()).applied());

        state.toggle();
        state.regenerate(g.bp(), LOADED.nodes());

        assertTrue(state.lines().stream().anyMatch(l -> l.contains("carre")),
                "le texte doit contenir la fonction");
        state.syncSelection(dansLeCorps, 40);
        int ligne = state.highlightedLine();
        assertTrue(ligne >= 0,
                "un nœud de corps sans ligne rendrait la vue script illisible pour tout ce "
                        + "qui vit dans une fonction");
        assertEquals(dansLeCorps, state.nodeAtLine(ligne));
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
