package fr.blueprint.client.editor;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.event.StandardEvents;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.Node;
import fr.blueprint.core.graph.VarNodes;
import fr.blueprint.core.graph.VarScope;
import fr.blueprint.core.graph.Variable;
import fr.blueprint.core.graph.Vec2d;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story 5.8 : la sélection copiée EST du BScript, le collage la re-parse et
 * réinsère avec des UUID neufs — deux collages = deux copies indépendantes.
 */
class ClipboardCodecTest {

    private static final PluginLoader.LoadedRegistries LOADED =
            PluginLoader.load(List.of(), true);

    private static final ClipboardCodec CODEC = new ClipboardCodec(LOADED);

    private static Identifier node(String path) {
        return Identifier.fromNamespaceAndPath("blueprint", path);
    }

    private static UUID add(Blueprint bp, Identifier typeId, double x, double y) {
        UUID id = UUID.randomUUID();
        assertTrue(new EditOperation.AddNode(id, typeId, new Vec2d(x, y))
                .apply(bp, LOADED.nodes()).applied());
        return id;
    }

    private static void apply(Blueprint bp, EditOperation op) {
        assertTrue(op.apply(bp, LOADED.nodes()).applied(), op::toString);
    }

    /** Un graphe complet : événement → log(add(1,2)) — la sélection exclut l'événement. */
    private record Source(Blueprint bp, UUID tick, UUID log, UUID addNode) {
    }

    private Source source() {
        Blueprint bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "copy"));
        UUID tick = add(bp, StandardEvents.SERVER_TICK.id(), -300, 0);
        UUID log = add(bp, node("debug/log"), 0, 32);
        UUID sum = add(bp, node("math/add"), -150, 96);
        apply(bp, new EditOperation.SetLiteral(sum, "a", LiteralValue.of(PinTypes.DOUBLE, 1.0)));
        apply(bp, new EditOperation.SetLiteral(sum, "b", LiteralValue.of(PinTypes.DOUBLE, 2.0)));
        apply(bp, new EditOperation.AddLink(new Link(tick, "exec_out", log, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(sum, "result", log, "value")));
        return new Source(bp, tick, log, sum);
    }

    @Test
    void copierCollerRoundTripAvecUuidNeufs() {
        Source src = source();
        String text = CODEC.copy(src.bp(), Set.of(src.log(), src.addNode()), LOADED.nodes());
        assertFalse(text.isBlank());

        ClipboardCodec.PasteResult parsed = CODEC.paste(text);
        assertTrue(parsed.success(), () -> String.valueOf(parsed.error()));
        assertEquals(2, parsed.fragment().nodes().size());
        assertEquals(1, parsed.fragment().links().size());

        // Collage dans un blueprint cible : UUID neufs, positions décalées en bloc.
        Blueprint target = new Blueprint(Identifier.fromNamespaceAndPath("test", "paste"));
        CanvasController controller = new CanvasController(target, LOADED.nodes(), new Camera());
        List<UUID> pasted = controller.pasteFragment(parsed.fragment(), 500, 400);
        assertEquals(2, pasted.size());
        assertEquals(1, target.links().size());
        for (UUID id : pasted) {
            assertFalse(src.bp().nodes().containsKey(id), "UUID remappé");
            assertTrue(controller.selection().isSelected(id));
        }
        // Le littéral a voyagé.
        boolean literalFound = target.nodes().values().stream().anyMatch(n ->
                n.literal("a") != null && ((Number) n.literal("a").value()).doubleValue() == 1.0);
        assertTrue(literalFound);
        // Une seule entrée d'annulation pour tout le collage.
        assertTrue(controller.undo());
        assertTrue(target.nodes().isEmpty());
        assertTrue(target.links().isEmpty());

        // Deux collages = deux copies indépendantes.
        controller.redo();
        List<UUID> second = controller.pasteFragment(parsed.fragment(), 900, 400);
        assertEquals(4, target.nodes().size());
        assertEquals(2, second.size());
    }

    @Test
    void lesVariablesReferenceesVoyagentEtSontCreees() {
        Blueprint bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "vars"));
        apply(bp, new EditOperation.AddVariable(new Variable("score", PinTypes.DOUBLE,
                LiteralValue.of(PinTypes.DOUBLE, 0.0), VarScope.GRAPH, false)));
        apply(bp, new EditOperation.AddVariable(new Variable("copie", PinTypes.DOUBLE,
                null, VarScope.GRAPH, false)));
        // Un pur orphelin serait inliné/perdu par le générateur : le get voyage avec
        // son consommateur set (limitation consignée dans la story).
        UUID get = add(bp, VarNodes.GET, 0, 0);
        UUID set = add(bp, VarNodes.SET, 200, 0);
        apply(bp, new EditOperation.SetLiteral(get, "var", LiteralValue.of(PinTypes.STRING, "score")));
        apply(bp, new EditOperation.SetLiteral(set, "var", LiteralValue.of(PinTypes.STRING, "copie")));
        apply(bp, new EditOperation.AddLink(new Link(get, "value", set, "value")));

        String text = CODEC.copy(bp, Set.of(get, set), LOADED.nodes());
        ClipboardCodec.PasteResult parsed = CODEC.paste(text);
        assertTrue(parsed.success(), () -> String.valueOf(parsed.error()));

        Blueprint target = new Blueprint(Identifier.fromNamespaceAndPath("test", "t2"));
        CanvasController controller = new CanvasController(target, LOADED.nodes(), new Camera());
        List<UUID> pasted = controller.pasteFragment(parsed.fragment(), 0, 0);
        assertEquals(2, pasted.size());
        assertNotNull(target.variables().get("score"), "la variable déclarée dans le fragment est créée");
        assertNotNull(target.variables().get("copie"));
        boolean getBound = target.nodes().values().stream()
                .anyMatch(n -> "score".equals(VarNodes.boundName(n)));
        assertTrue(getBound);
    }

    @Test
    void unTexteInvalideNInsereRien() {
        ClipboardCodec.PasteResult parsed = CODEC.paste("ceci n'est pas du BScript {{{");
        assertFalse(parsed.success());
        assertNotNull(parsed.error());
        assertFalse(CODEC.paste("").success());
    }
}
