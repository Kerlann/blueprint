package fr.blueprint.core.graph;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static fr.blueprint.core.graph.TestNodes.LOOKUP;
import static fr.blueprint.core.graph.TestNodes.apply;
import static fr.blueprint.core.graph.TestNodes.newGraph;
import static fr.blueprint.core.graph.TestNodes.node;
import static fr.blueprint.core.graph.TestNodes.refuse;
import static fr.blueprint.core.graph.TestNodes.uuid;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Refus typés des opérations d'édition (AC1, AC2) — un code de diagnostic par cas. */
class EditOperationsTest {

    @Test
    void addNodeRefusesDuplicate() {
        var bp = newGraph();
        UUID a = node(bp, "a", TestNodes.ADD);
        var d = refuse(bp, new EditOperation.AddNode(a, TestNodes.ADD, Vec2d.ZERO));
        assertEquals(DiagnosticCode.DUPLICATE_NODE, d.code());
    }

    @Test
    void addNodeRefusesBeyondLimit() {
        var bp = newGraph();
        node(bp, "a", TestNodes.ADD);
        var result = new EditOperation.AddNode(uuid("b"), TestNodes.ADD, Vec2d.ZERO)
                .apply(bp, LOOKUP, new GraphLimits(1));
        assertNotNull(result.refusal());
        assertEquals(DiagnosticCode.NODE_LIMIT_EXCEEDED, result.refusal().code());
    }

    @Test
    void removeNodeRefusesUnknown() {
        var d = refuse(newGraph(), new EditOperation.RemoveNode(uuid("ghost")));
        assertEquals(DiagnosticCode.NODE_NOT_FOUND, d.code());
    }

    @Test
    void removeNodeSeversItsLinks() {
        var bp = newGraph();
        UUID start = node(bp, "start", TestNodes.START);
        UUID print = node(bp, "print", TestNodes.PRINT);
        apply(bp, new EditOperation.AddLink(new Link(start, "exec_out", print, "exec_in")));
        apply(bp, new EditOperation.RemoveNode(print));
        assertTrue(bp.links().isEmpty(), "les liens du nœud supprimé doivent disparaître");
    }

    @Test
    void moveNodeRefusesUnknown() {
        var d = refuse(newGraph(), new EditOperation.MoveNode(uuid("ghost"), Vec2d.ZERO));
        assertEquals(DiagnosticCode.NODE_NOT_FOUND, d.code());
    }

    @Test
    void setLiteralChecksPinAndType() {
        var bp = newGraph();
        UUID print = node(bp, "print", TestNodes.PRINT);
        assertEquals(DiagnosticCode.PIN_NOT_FOUND,
                refuse(bp, new EditOperation.SetLiteral(print, "nope",
                        LiteralValue.of(PinTypes.STRING, "x"))).code());
        assertEquals(DiagnosticCode.TYPE_MISMATCH,
                refuse(bp, new EditOperation.SetLiteral(print, "text",
                        LiteralValue.of(PinTypes.INT, 3))).code());
        apply(bp, new EditOperation.SetLiteral(print, "text", LiteralValue.of(PinTypes.STRING, "ok")));
        assertEquals("ok", bp.node(print).literal("text").value());
    }

    @Test
    void setLiteralChecksListElementsDeeply() {
        // Reprise QA TYPE-001 : le conteneur passe la construction, les éléments non.
        var bp = newGraph();
        UUID first = node(bp, "first", TestNodes.FIRST);
        var wrongElements = LiteralValue.of(PinTypes.listOf(PinTypes.INT), List.of("pas", "des", "ints"));
        var d = refuse(bp, new EditOperation.SetLiteral(first, "list", wrongElements));
        assertEquals(DiagnosticCode.TYPE_MISMATCH, d.code());
    }

    @Test
    void linkOperationsRefuseProperly() {
        var bp = newGraph();
        UUID start = node(bp, "start", TestNodes.START);
        UUID print = node(bp, "print", TestNodes.PRINT);
        Link link = new Link(start, "exec_out", print, "exec_in");
        assertEquals(DiagnosticCode.LINK_NOT_FOUND, refuse(bp, new EditOperation.RemoveLink(link)).code());
        apply(bp, new EditOperation.AddLink(link));
        assertEquals(DiagnosticCode.DUPLICATE_LINK, refuse(bp, new EditOperation.AddLink(link)).code());
    }

    @Test
    void variableOperationsRefuseProperly() {
        var bp = newGraph();
        var v = new Variable("score", PinTypes.INT, LiteralValue.of(PinTypes.INT, 0), VarScope.GRAPH, false);
        apply(bp, new EditOperation.AddVariable(v));
        assertEquals(DiagnosticCode.DUPLICATE_VARIABLE, refuse(bp, new EditOperation.AddVariable(v)).code());
        assertEquals(DiagnosticCode.VARIABLE_NOT_FOUND,
                refuse(bp, new EditOperation.RemoveVariable("absent")).code());
        assertEquals(DiagnosticCode.VARIABLE_NOT_FOUND,
                refuse(bp, new EditOperation.RenameVariable("absent", "x")).code());
        apply(bp, new EditOperation.AddVariable(
                new Variable("total", PinTypes.INT, null, VarScope.GRAPH, false)));
        assertEquals(DiagnosticCode.DUPLICATE_VARIABLE,
                refuse(bp, new EditOperation.RenameVariable("score", "total")).code());
        assertEquals(DiagnosticCode.TYPE_MISMATCH,
                refuse(bp, new EditOperation.RetypeVariable("score", PinTypes.DOUBLE,
                        LiteralValue.of(PinTypes.INT, 1))).code());
    }

    @Test
    void commentOperationsRefuseProperly() {
        var bp = newGraph();
        var box = new CommentBox(uuid("c"), "note", Vec2d.ZERO, new Vec2d(100, 50), 0xFF333333);
        assertEquals(DiagnosticCode.COMMENT_NOT_FOUND,
                refuse(bp, new EditOperation.RemoveComment(box.uuid())).code());
        assertEquals(DiagnosticCode.COMMENT_NOT_FOUND,
                refuse(bp, new EditOperation.EditComment(box)).code());
        apply(bp, new EditOperation.AddComment(box));
        assertEquals(DiagnosticCode.DUPLICATE_COMMENT,
                refuse(bp, new EditOperation.AddComment(box)).code());
    }

    @Test
    void revisionIncrementsOnAppliedOperationsOnly() {
        var bp = newGraph();
        assertEquals(0, bp.revision());
        UUID a = node(bp, "a", TestNodes.ADD);
        assertEquals(1, bp.revision());
        refuse(bp, new EditOperation.AddNode(a, TestNodes.ADD, Vec2d.ZERO));
        assertEquals(1, bp.revision(), "une opération refusée ne bouge pas la révision");
        apply(bp, new EditOperation.MoveNode(a, new Vec2d(10, 20)));
        assertEquals(2, bp.revision());
    }

    @Test
    void setConfigRoundTrips() {
        var bp = newGraph();
        UUID a = node(bp, "a", TestNodes.ADD);
        CompoundTag tag = new CompoundTag();
        tag.putInt("clef", 7);
        apply(bp, new EditOperation.SetConfig(a, tag));
        assertEquals(7, bp.node(a).config().getIntOr("clef", 0));
        assertEquals(DiagnosticCode.NODE_NOT_FOUND,
                refuse(newGraph(), new EditOperation.SetConfig(uuid("x"), tag)).code());
    }
}
