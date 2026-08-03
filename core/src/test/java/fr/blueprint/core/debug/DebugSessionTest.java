package fr.blueprint.core.debug;

import fr.blueprint.api.event.TriggerContext;
import fr.blueprint.api.node.BlueprintHandle;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.compile.ir.Instruction;
import fr.blueprint.core.compile.ir.Ir;
import fr.blueprint.core.vm.BlueprintVm;
import fr.blueprint.core.vm.ExecResult;
import fr.blueprint.core.vm.ExecutionEnvironment;
import fr.blueprint.core.vm.ExecutionState;
import fr.blueprint.core.vm.VarStore;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Débogueur : points d'arrêt, pas-à-pas, valeurs (story 9.1a). */
class DebugSessionTest {

    private static final Identifier BLUEPRINT = Identifier.fromNamespaceAndPath("test", "debug");
    private static final List<String> RAN = new ArrayList<>();

    private static final BlueprintHandle HANDLE = new BlueprintHandle() {
        @Override
        public Identifier id() {
            return BLUEPRINT;
        }

        @Override
        public boolean enabled() {
            return true;
        }
    };

    private static final TriggerContext TRIGGER = new TriggerContext() {
        @Override
        public Identifier eventId() {
            return Identifier.fromNamespaceAndPath("test", "manual");
        }

        @Override
        public Object output(String name) {
            return null;
        }
    };

    /** Deux nœuds qui laissent une trace, le second calculant une sortie. */
    private static final NodeType FIRST = NodeType.builder(
                    Identifier.fromNamespaceAndPath("t", "first"))
            .exec().action(ctx -> RAN.add("first")).build();
    private static final NodeType SECOND = NodeType.builder(
                    Identifier.fromNamespaceAndPath("t", "second"))
            .exec().in("value", PinTypes.INT, 3).out("doubled", PinTypes.INT)
            .action(ctx -> {
                RAN.add("second");
                ctx.out("doubled", ctx.<Integer>in("value") * 2);
            })
            .build();

    private static final Function<Identifier, NodeType> RESOLVER = id ->
            id.getPath().equals("first") ? FIRST : id.getPath().equals("second") ? SECOND : null;

    private static final UUID NODE_A = UUID.nameUUIDFromBytes("a".getBytes());
    private static final UUID NODE_B = UUID.nameUUIDFromBytes("b".getBytes());

    private static ExecutionEnvironment env() {
        return new ExecutionEnvironment(RESOLVER, HANDLE, TRIGGER, VarStore.inMemory(),
                null, null, LoggerFactory.getLogger("blueprint-test"));
    }

    /** IR : first → second → fin, sans compilateur. */
    private static Ir program() {
        return new Ir(BLUEPRINT, 0, NODE_A, List.of(
                new Instruction.Call(FIRST.id(), List.of(), List.of(),
                        Map.of("exec_out", 1), 1, false, NODE_A),
                new Instruction.Call(SECOND.id(), List.of(),
                        List.of(new Instruction.PinBinding("doubled", 0)),
                        Map.of(), 1, false, NODE_B),
                new Instruction.Return(null)), 1);
    }

    @AfterEach
    void closeSessions() {
        DebugSessions.closeAll();
        RAN.clear();
    }

    @Test
    void withoutAnySessionNothingIsObserved() {
        assertFalse(DebugSessions.active(), "aucune session : le drapeau reste faux");
        Ir ir = program();
        assertInstanceOf(ExecResult.Done.class,
                BlueprintVm.run(ir, ExecutionState.fresh(ir), env(), 100));
        assertEquals(List.of("first", "second"), RAN);
    }

    @Test
    void aSessionOnAnotherBlueprintDoesNotPauseThisOne() {
        DebugSessions.open(Identifier.fromNamespaceAndPath("test", "autre")).breakOn(NODE_A);
        Ir ir = program();
        assertInstanceOf(ExecResult.Done.class,
                BlueprintVm.run(ir, ExecutionState.fresh(ir), env(), 100));
        assertEquals(List.of("first", "second"), RAN);
    }

    @Test
    void aBreakpointStopsBeforeTheNodeRuns() {
        DebugSession session = DebugSessions.open(BLUEPRINT);
        session.breakOn(NODE_B);

        Ir ir = program();
        ExecutionState state = ExecutionState.fresh(ir);
        var first = BlueprintVm.run(ir, state, env(), 100);
        assertInstanceOf(ExecResult.Suspended.class, first, "l'exécution rend la main");
        assertEquals(List.of("first"), RAN, "le nœud du point d'arrêt n'a PAS tourné");
        assertEquals(NODE_B, session.pausedAt());

        // Tant qu'on ne reprend pas, chaque tick repasse sans rien exécuter.
        assertInstanceOf(ExecResult.Suspended.class, BlueprintVm.run(ir, state, env(), 100));
        assertEquals(List.of("first"), RAN);

        session.resume();
        assertInstanceOf(ExecResult.Done.class, BlueprintVm.run(ir, state, env(), 100));
        assertEquals(List.of("first", "second"), RAN);
        assertNull(session.pausedAt());
    }

    @Test
    void stepRunsExactlyOneNodePerResume() {
        DebugSession session = DebugSessions.open(BLUEPRINT);
        session.step();

        Ir ir = program();
        ExecutionState state = ExecutionState.fresh(ir);
        assertInstanceOf(ExecResult.Suspended.class, BlueprintVm.run(ir, state, env(), 100));
        assertEquals(List.of(), RAN, "on s'arrête AVANT le premier nœud");
        assertEquals(NODE_A, session.pausedAt());

        session.step();
        assertInstanceOf(ExecResult.Suspended.class, BlueprintVm.run(ir, state, env(), 100));
        assertEquals(List.of("first"), RAN, "un pas = un nœud");
        assertEquals(NODE_B, session.pausedAt());

        session.resume();
        assertInstanceOf(ExecResult.Done.class, BlueprintVm.run(ir, state, env(), 100));
        assertEquals(List.of("first", "second"), RAN);
    }

    @Test
    void valuesAndTraceAreRecordedForEveryNode() {
        DebugSession session = DebugSessions.open(BLUEPRINT);
        Ir ir = program();
        BlueprintVm.run(ir, ExecutionState.fresh(ir), env(), 100);

        assertEquals(List.of(NODE_A, NODE_B), session.trace());
        assertEquals(1, session.hits(NODE_B));
        Map<String, String> values = session.valuesOf(NODE_B);
        assertEquals("3", values.get("→value"), "l'entrée par défaut est visible");
        assertEquals("6", values.get("←doubled"), "la sortie calculée aussi");
    }

    /** Une valeur vivante n'est jamais retenue : seule sa forme texte, bornée. */
    @Test
    void valuesAreRenderedAndTruncatedNeverKeptAlive() {
        String huge = "x".repeat(500);
        String rendered = DebugSession.render(huge);
        assertEquals(DebugSession.MAX_VALUE_LENGTH, rendered.length());
        assertTrue(rendered.endsWith("…"));
        assertEquals("null", DebugSession.render(null));

        DebugSession session = new DebugSession(BLUEPRINT);
        Object living = new Object() {
            @Override
            public String toString() {
                return "entité vivante";
            }
        };
        session.record(NODE_A, Map.of("cible", living), Map.of());
        assertEquals("entité vivante", session.valuesOf(NODE_A).get("→cible"));
        assertFalse(session.allValues().values().stream()
                        .anyMatch(map -> map.containsValue(living)),
                "la table ne contient que du texte");
    }

    /** Désigner un nœud par préfixe : 8 caractères suffisent, l'ambigu est refusé. */
    @Test
    void nodesAreResolvedByUuidPrefix() {
        DebugSession session = new DebugSession(BLUEPRINT);
        session.record(NODE_A, Map.of(), Map.of());
        session.breakOn(NODE_B);

        assertEquals(NODE_A, session.resolve(NODE_A.toString().substring(0, 8)).node());
        assertEquals(NODE_B, session.resolve(NODE_B.toString()).node(),
                "un UUID complet passe aussi");

        var nothing = session.resolve("zzzzzzzz");
        assertFalse(nothing.found());
        assertFalse(nothing.ambiguous());

        var ambiguous = session.resolve("");
        assertTrue(ambiguous.ambiguous(), "un préfixe vide vise tout le monde");
        assertFalse(ambiguous.found(), "et n'est donc pas résolu");

        // Un UUID inconnu de la session reste accepté : l'éditeur les connaît tous.
        UUID elsewhere = UUID.randomUUID();
        assertEquals(elsewhere, session.resolve(elsewhere.toString()).node());
    }

    @Test
    void theTraceIsBounded() {
        DebugSession session = new DebugSession(BLUEPRINT);
        for (int i = 0; i < DebugSession.TRACE_LENGTH * 2; i++) {
            session.record(UUID.randomUUID(), Map.of(), Map.of());
        }
        assertEquals(DebugSession.TRACE_LENGTH, session.trace().size());
    }

    /** Fermer le débogueur ne doit pas laisser un blueprint figé pour toujours. */
    @Test
    void closingASessionReleasesAPausedRun() {
        DebugSession session = DebugSessions.open(BLUEPRINT);
        session.breakOn(NODE_A);
        Ir ir = program();
        ExecutionState state = ExecutionState.fresh(ir);
        assertInstanceOf(ExecResult.Suspended.class, BlueprintVm.run(ir, state, env(), 100));
        assertEquals(List.of(), RAN);

        assertTrue(DebugSessions.close(BLUEPRINT));
        assertFalse(DebugSessions.active());
        assertInstanceOf(ExecResult.Done.class, BlueprintVm.run(ir, state, env(), 100));
        assertEquals(List.of("first", "second"), RAN, "l'exécution reprend là où elle en était");
    }
}
