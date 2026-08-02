package fr.blueprint.core.graph;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static fr.blueprint.core.graph.TestNodes.LOOKUP;
import static fr.blueprint.core.graph.TestNodes.apply;
import static fr.blueprint.core.graph.TestNodes.newGraph;
import static fr.blueprint.core.graph.TestNodes.node;
import static fr.blueprint.core.graph.TestNodes.uuid;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chaque opération, appliquée puis inversée, restitue le graphe à l'identique (AC1, AC8). */
class ReversibilityTest {

    /** Applique l'op, applique son inverse, et exige un contenu identique à l'instantané. */
    private static void assertReversible(Blueprint bp, EditOperation op) {
        Blueprint before = bp.copy();
        EditOperation.Result applied = op.apply(bp, LOOKUP);
        assertNull(applied.refusal(), () -> "refusée : " + applied.refusal());
        EditOperation.Result inverted = applied.inverse().apply(bp, LOOKUP);
        assertNull(inverted.refusal(), () -> "inverse refusée : " + inverted.refusal());
        assertTrue(bp.contentEquals(before),
                () -> "le graphe n'est pas revenu à son état d'origine après " + op);
    }

    private Blueprint richGraph() {
        var bp = newGraph();
        UUID start = node(bp, "s", TestNodes.START);
        UUID print = node(bp, "p", TestNodes.PRINT);
        node(bp, "add", TestNodes.ADD);
        apply(bp, new EditOperation.AddLink(new Link(start, "exec_out", print, "exec_in")));
        apply(bp, new EditOperation.SetLiteral(print, "text", LiteralValue.of(PinTypes.STRING, "x")));
        apply(bp, new EditOperation.AddVariable(
                new Variable("score", PinTypes.INT, LiteralValue.of(PinTypes.INT, 0), VarScope.GRAPH, false)));
        apply(bp, new EditOperation.AddComment(
                new CommentBox(uuid("c"), "note", Vec2d.ZERO, new Vec2d(80, 40), 0xFF222222)));
        return bp;
    }

    @Test
    void everyOperationIsReversible() {
        var bp = richGraph();
        UUID print = uuid("p");
        UUID add = uuid("add");

        assertReversible(bp, new EditOperation.AddNode(uuid("new"), TestNodes.HALF, new Vec2d(5, 5)));
        assertReversible(bp, new EditOperation.MoveNode(print, new Vec2d(42, -7)));
        assertReversible(bp, new EditOperation.SetLiteral(print, "text",
                LiteralValue.of(PinTypes.STRING, "autre")));
        assertReversible(bp, new EditOperation.SetLiteral(print, "text", null));

        CompoundTag tag = new CompoundTag();
        tag.putString("k", "v");
        assertReversible(bp, new EditOperation.SetConfig(add, tag));

        Link dataLink = new Link(add, "sum", uuid("p2sink"), "a");
        node(bp, "p2sink", TestNodes.ADD);
        assertReversible(bp, new EditOperation.AddLink(dataLink));
        apply(bp, new EditOperation.AddLink(dataLink));
        assertReversible(bp, new EditOperation.RemoveLink(dataLink));

        // RemoveNode : le cas le plus riche — le nœud, ses liens et littéraux reviennent.
        assertReversible(bp, new EditOperation.RemoveNode(print));

        assertReversible(bp, new EditOperation.AddVariable(
                new Variable("total", PinTypes.DOUBLE, null, VarScope.WORLD, true)));
        assertReversible(bp, new EditOperation.RemoveVariable("score"));
        assertReversible(bp, new EditOperation.RenameVariable("score", "points"));
        assertReversible(bp, new EditOperation.RetypeVariable("score", PinTypes.LONG,
                LiteralValue.of(PinTypes.LONG, 0L)));
        assertReversible(bp, new EditOperation.SetScope("score", VarScope.PLAYER));

        assertReversible(bp, new EditOperation.AddComment(
                new CommentBox(uuid("c2"), "n2", new Vec2d(1, 1), new Vec2d(10, 10), 0)));
        assertReversible(bp, new EditOperation.RemoveComment(uuid("c")));
        assertReversible(bp, new EditOperation.EditComment(
                new CommentBox(uuid("c"), "modifié", new Vec2d(9, 9), new Vec2d(99, 99), 0xFFFFFFFF)));
    }
}
