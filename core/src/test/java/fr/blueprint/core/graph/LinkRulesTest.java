package fr.blueprint.core.graph;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static fr.blueprint.core.graph.TestNodes.apply;
import static fr.blueprint.core.graph.TestNodes.newGraph;
import static fr.blueprint.core.graph.TestNodes.node;
import static fr.blueprint.core.graph.TestNodes.refuse;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Règles de câblage via {@code canLink} (AC2 — cardinalité FR3, types FR4, jokers FR5). */
class LinkRulesTest {

    @Test
    void execOutputAcceptsAtMostOneLink() {
        var bp = newGraph();
        UUID start = node(bp, "s", TestNodes.START);
        UUID print1 = node(bp, "p1", TestNodes.PRINT);
        UUID print2 = node(bp, "p2", TestNodes.PRINT);
        apply(bp, new EditOperation.AddLink(new Link(start, "exec_out", print1, "exec_in")));
        var d = refuse(bp, new EditOperation.AddLink(new Link(start, "exec_out", print2, "exec_in")));
        assertEquals(DiagnosticCode.EXEC_OUT_ALREADY_LINKED, d.code());
    }

    @Test
    void execInputAcceptsManyLinks() {
        var bp = newGraph();
        UUID p1 = node(bp, "p1", TestNodes.PRINT);
        UUID p2 = node(bp, "p2", TestNodes.PRINT);
        UUID target = node(bp, "t", TestNodes.PRINT);
        apply(bp, new EditOperation.AddLink(new Link(p1, "exec_out", target, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(p2, "exec_out", target, "exec_in")));
        assertEquals(2, bp.linksInto(target, "exec_in").size());
    }

    @Test
    void dataInputAcceptsAtMostOneLink() {
        var bp = newGraph();
        UUID a1 = node(bp, "a1", TestNodes.ADD);
        UUID a2 = node(bp, "a2", TestNodes.ADD);
        UUID sink = node(bp, "sink", TestNodes.ADD);
        apply(bp, new EditOperation.AddLink(new Link(a1, "sum", sink, "a")));
        var d = refuse(bp, new EditOperation.AddLink(new Link(a2, "sum", sink, "a")));
        assertEquals(DiagnosticCode.DATA_IN_ALREADY_LINKED, d.code());
    }

    @Test
    void dataOutputFeedsManyLinks() {
        var bp = newGraph();
        UUID src = node(bp, "src", TestNodes.ADD);
        UUID s1 = node(bp, "s1", TestNodes.ADD);
        UUID s2 = node(bp, "s2", TestNodes.ADD);
        apply(bp, new EditOperation.AddLink(new Link(src, "sum", s1, "a")));
        apply(bp, new EditOperation.AddLink(new Link(src, "sum", s2, "a")));
        assertEquals(2, bp.linksFrom(src, "sum").size());
    }

    @Test
    void typeMismatchIsRefused() {
        var bp = newGraph();
        UUID makeList = node(bp, "ml", TestNodes.MAKE_LIST);
        UUID sink = node(bp, "sink", TestNodes.ADD);
        var d = refuse(bp, new EditOperation.AddLink(new Link(makeList, "list", sink, "a")));
        assertEquals(DiagnosticCode.TYPE_MISMATCH, d.code());
    }

    @Test
    void implicitCoercionIsAccepted() {
        // int → double : coercition déclarée (story 1.2).
        var bp = newGraph();
        UUID add = node(bp, "add", TestNodes.ADD);
        UUID half = node(bp, "half", TestNodes.HALF);
        apply(bp, new EditOperation.AddLink(new Link(add, "sum", half, "value")));
    }

    @Test
    void execAndDataNeverMix() {
        var bp = newGraph();
        UUID start = node(bp, "s", TestNodes.START);
        UUID add = node(bp, "add", TestNodes.ADD);
        var d = refuse(bp, new EditOperation.AddLink(new Link(start, "exec_out", add, "a")));
        assertEquals(DiagnosticCode.TYPE_MISMATCH, d.code());
    }

    @Test
    void unknownPinIsRefused() {
        var bp = newGraph();
        UUID start = node(bp, "s", TestNodes.START);
        UUID print = node(bp, "p", TestNodes.PRINT);
        var d = refuse(bp, new EditOperation.AddLink(new Link(start, "nope", print, "exec_in")));
        assertEquals(DiagnosticCode.PIN_NOT_FOUND, d.code());
    }

    @Test
    void unknownEndpointIsRefused() {
        var bp = newGraph();
        UUID print = node(bp, "p", TestNodes.PRINT);
        var d = refuse(bp, new EditOperation.AddLink(
                new Link(TestNodes.uuid("absent"), "exec_out", print, "exec_in")));
        assertEquals(DiagnosticCode.NODE_NOT_FOUND, d.code());
    }

    @Test
    void dataSelfLoopIsRefusedImmediately() {
        var bp = newGraph();
        UUID add = node(bp, "add", TestNodes.ADD);
        var d = refuse(bp, new EditOperation.AddLink(new Link(add, "sum", add, "a")));
        assertEquals(DiagnosticCode.DATA_CYCLE, d.code());
    }

    @Test
    void genericConflictIsRefusedAtLinkTime() {
        // first : entrée list<T>, sortie T. Câbler list<int> fixe T=int ;
        // brancher ensuite elem sur une entrée string doit refuser (AC5).
        var bp = newGraph();
        UUID makeList = node(bp, "ml", TestNodes.MAKE_LIST);
        UUID first = node(bp, "f", TestNodes.FIRST);
        UUID print = node(bp, "p", TestNodes.PRINT);
        apply(bp, new EditOperation.AddLink(new Link(makeList, "list", first, "list")));
        var d = refuse(bp, new EditOperation.AddLink(new Link(first, "elem", print, "text")));
        assertEquals(DiagnosticCode.GENERIC_CONFLICT, d.code());
    }

    @Test
    void genericResolutionAllowsCoherentWiring() {
        var bp = newGraph();
        UUID makeList = node(bp, "ml", TestNodes.MAKE_LIST);
        UUID first = node(bp, "f", TestNodes.FIRST);
        UUID sink = node(bp, "sink", TestNodes.ADD);
        apply(bp, new EditOperation.AddLink(new Link(makeList, "list", first, "list")));
        // T=int, donc elem (int) → add.a (int) passe.
        apply(bp, new EditOperation.AddLink(new Link(first, "elem", sink, "a")));
    }
}
