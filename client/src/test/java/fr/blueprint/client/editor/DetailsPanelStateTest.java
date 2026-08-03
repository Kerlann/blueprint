package fr.blueprint.client.editor;

import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.BlueprintMeta;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.Vec2d;
import fr.blueprint.core.registry.NodeDescriptor;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DetailsPanelStateTest {

    private static final PluginLoader.LoadedRegistries LOADED =
            PluginLoader.load(List.of(), true);

    private Blueprint bp;
    private DetailsPanelState state;

    @BeforeEach
    void setUp() {
        bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "details"));
        state = new DetailsPanelState(bp,
                id -> LOADED.nodes().get(id).map(NodeDescriptor::of).orElse(null),
                op -> op.apply(bp, LOADED.nodes()).applied(),
                key -> key);
    }

    private UUID add(String path, double x) {
        UUID id = UUID.randomUUID();
        assertTrue(new EditOperation.AddNode(id,
                Identifier.fromNamespaceAndPath("blueprint", path), new Vec2d(x, 0))
                .apply(bp, LOADED.nodes()).applied());
        return id;
    }

    private static List<DetailsPanelState.Row> ofKind(List<DetailsPanelState.Row> rows,
                                                      DetailsPanelState.Kind kind) {
        return rows.stream().filter(r -> r.kind() == kind).toList();
    }

    @Test
    void selectionVideMontreLeBlueprint() {
        var rows = state.rows(List.of());
        assertEquals(DetailsPanelState.Kind.HEADER, rows.get(0).kind());
        assertEquals("test:details", rows.get(0).value());
        assertEquals(1, ofKind(rows, DetailsPanelState.Kind.META_AUTHOR).size());
        assertEquals(1, ofKind(rows, DetailsPanelState.Kind.META_CAP).size());
    }

    @Test
    void noeudSeulLitterauxEtLienCliquable() {
        UUID sum = add("math/add", 0);
        UUID mul = add("math/mul", -200);
        assertTrue(new EditOperation.SetLiteral(sum, "b", LiteralValue.of(PinTypes.DOUBLE, 4.0))
                .apply(bp, LOADED.nodes()).applied());
        assertTrue(new EditOperation.AddLink(new Link(mul, "result", sum, "a"))
                .apply(bp, LOADED.nodes()).applied());

        var rows = state.rows(Set.of(sum));
        // « a » est câblé → ligne WIRED pointant la source ; « b » → LITERAL 4.
        var wired = ofKind(rows, DetailsPanelState.Kind.WIRED);
        assertEquals(1, wired.size());
        assertEquals(mul, wired.get(0).node());
        var literals = ofKind(rows, DetailsPanelState.Kind.LITERAL);
        assertEquals(1, literals.size());
        assertEquals("b", literals.get(0).pin());
        assertEquals("4", literals.get(0).value());
    }

    @Test
    void selectionMultipleEtFantome() {
        UUID a = add("math/add", 0);
        UUID b = add("math/mul", 200);
        assertEquals(DetailsPanelState.Kind.HEADER, state.rows(Set.of(a, b)).get(0).kind());
        assertEquals("2", state.rows(Set.of(a, b)).get(0).value());

        UUID ghost = UUID.randomUUID();
        assertTrue(new EditOperation.AddNode(ghost,
                Identifier.fromNamespaceAndPath("gonemod", "node"), new Vec2d(0, 0))
                .apply(bp, LOADED.nodes()).applied());
        var rows = state.rows(Set.of(ghost));
        assertEquals("gonemod:node", rows.get(0).label());
        assertEquals("gonemod", rows.get(1).value());
    }

    @Test
    void editionDesMetadonnees() {
        state.openMetaEdit(DetailsPanelState.MetaField.AUTHOR);
        state.type("Hakan");
        assertTrue(state.commitMetaEdit());
        assertEquals("Hakan", bp.meta().author());
        assertFalse(state.isEditingMeta());

        state.openMetaEdit(DetailsPanelState.MetaField.DESCRIPTION);
        state.type("x");
        state.cancelMetaEdit();
        assertEquals("", bp.meta().description());
    }

    @Test
    void cycleDuPlafondDePermission() {
        assertEquals(Permission.GAMEPLAY, bp.meta().permissionCap());
        assertTrue(state.cyclePermissionCap());
        assertEquals(Permission.WORLD, bp.meta().permissionCap());
        // L'inverse de SetMeta restaure l'ancien plafond.
        BlueprintMeta before = bp.meta();
        EditOperation.Result result = new EditOperation.SetMeta(new BlueprintMeta(
                "a", "b", "2.0.0", Permission.ADMIN)).apply(bp, LOADED.nodes());
        assertTrue(result.applied());
        assertTrue(result.inverse().apply(bp, LOADED.nodes()).applied());
        assertEquals(before, bp.meta());
    }
}
