package fr.blueprint.core.graph;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import static fr.blueprint.core.graph.TestNodes.LOOKUP;
import static fr.blueprint.core.graph.TestNodes.apply;
import static fr.blueprint.core.graph.TestNodes.newGraph;
import static fr.blueprint.core.graph.TestNodes.node;
import static fr.blueprint.core.graph.TestNodes.uuid;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Propriété (AC8, jqwik) : pour toute séquence d'opérations appliquées avec succès,
 * rejouer les inverses en ordre inverse restitue exactement le graphe de départ.
 * Les opérations refusées ne comptent pas — un refus ne mute jamais le graphe,
 * ce que la propriété vérifie aussi par construction.
 */
class ReversibilityPropertyTest {

    @Property(tries = 300)
    void randomSequencesAreFullyReversible(
            @ForAll @Size(max = 40) List<@IntRange(min = 0, max = 9999) Integer> codes) {

        Blueprint bp = baseGraph();
        Blueprint snapshot = bp.copy();

        Deque<EditOperation> inverses = new ArrayDeque<>();
        for (int code : codes) {
            EditOperation.Result result = operationFor(code, bp).apply(bp, LOOKUP);
            if (result.applied()) {
                inverses.push(result.inverse());
            }
        }
        while (!inverses.isEmpty()) {
            EditOperation.Result result = inverses.pop().apply(bp, LOOKUP);
            assertTrue(result.applied(), () -> "inverse refusée : " + result.refusal());
        }
        assertTrue(bp.contentEquals(snapshot), "graphe non restitué après inversion complète");
    }

    private static Blueprint baseGraph() {
        var bp = newGraph();
        UUID start = node(bp, "s", TestNodes.START);
        UUID print = node(bp, "p", TestNodes.PRINT);
        node(bp, "add", TestNodes.ADD);
        node(bp, "half", TestNodes.HALF);
        apply(bp, new EditOperation.AddLink(new Link(start, "exec_out", print, "exec_in")));
        apply(bp, new EditOperation.AddVariable(
                new Variable("v0", PinTypes.INT, LiteralValue.of(PinTypes.INT, 0), VarScope.GRAPH, false)));
        return bp;
    }

    /** Décode un entier arbitraire en opération concrète — refus autorisés, c'est le jeu. */
    private static EditOperation operationFor(int code, Blueprint bp) {
        UUID print = uuid("p");
        UUID add = uuid("add");
        UUID half = uuid("half");
        return switch (code % 14) {
            case 0 -> new EditOperation.MoveNode(print, new Vec2d(code % 500, -(code % 300)));
            case 1 -> new EditOperation.SetLiteral(print, "text",
                    LiteralValue.of(PinTypes.STRING, "s" + (code % 7)));
            case 2 -> new EditOperation.SetLiteral(print, "text", null);
            case 3 -> new EditOperation.AddLink(new Link(add, "sum", half, "value"));
            case 4 -> new EditOperation.RemoveLink(new Link(add, "sum", half, "value"));
            case 5 -> new EditOperation.AddVariable(new Variable("v" + (code % 3), PinTypes.INT,
                    LiteralValue.of(PinTypes.INT, code % 100), VarScope.GRAPH, false));
            case 6 -> new EditOperation.RemoveVariable("v" + (code % 3));
            case 7 -> new EditOperation.SetScope("v" + (code % 3),
                    VarScope.values()[code % VarScope.values().length]);
            case 8 -> new EditOperation.RenameVariable("v" + (code % 3), "w" + (code % 2));
            case 9 -> new EditOperation.AddComment(new CommentBox(uuid("c" + (code % 2)),
                    "n" + code, new Vec2d(code % 50, code % 60), new Vec2d(20, 20), code));
            case 10 -> new EditOperation.RemoveComment(uuid("c" + (code % 2)));
            case 11 -> {
                CompoundTag tag = new CompoundTag();
                tag.putInt("k", code);
                yield new EditOperation.SetConfig(add, tag);
            }
            case 12 -> new EditOperation.AddNode(uuid("n" + (code % 2)), TestNodes.ADD,
                    new Vec2d(code % 40, code % 30));
            default -> new EditOperation.RemoveNode(uuid("n" + (code % 2)));
        };
    }
}
