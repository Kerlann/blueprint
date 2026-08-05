package fr.blueprint.core.debug;

import fr.blueprint.api.event.TriggerContext;
import fr.blueprint.api.node.BlueprintHandle;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.core.compile.ir.Instruction;
import fr.blueprint.core.compile.ir.Ir;
import fr.blueprint.core.vm.BlueprintVm;
import fr.blueprint.core.vm.ExecutionEnvironment;
import fr.blueprint.core.vm.ExecutionState;
import fr.blueprint.core.vm.VarStore;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Profileur par nœud (story 9.2). */
class ProfilerTest {

    private static final Identifier BLUEPRINT = Identifier.fromNamespaceAndPath("test", "profile");
    private static final UUID FAST = UUID.nameUUIDFromBytes("fast".getBytes());
    private static final UUID SLOW = UUID.nameUUIDFromBytes("slow".getBytes());

    private static final NodeType FAST_NODE = NodeType.builder(
            Identifier.fromNamespaceAndPath("t", "fast")).exec().action(ctx -> {
    }).build();
    private static final NodeType SLOW_NODE = NodeType.builder(
                    Identifier.fromNamespaceAndPath("t", "slow")).exec().fuelCost(5)
            .action(ctx -> {
                // Un travail mesurable, sans dormir : le test ne doit pas être long.
                long sum = 0;
                for (int i = 0; i < 200_000; i++) {
                    sum += i;
                }
                if (sum < 0) {
                    throw new IllegalStateException("jamais");
                }
            }).build();

    private static final Function<Identifier, NodeType> RESOLVER = id ->
            id.getPath().equals("fast") ? FAST_NODE : id.getPath().equals("slow") ? SLOW_NODE : null;

    private static ExecutionEnvironment env() {
        return new ExecutionEnvironment(RESOLVER, new BlueprintHandle() {
            @Override
            public Identifier id() {
                return BLUEPRINT;
            }

            @Override
            public boolean enabled() {
                return true;
            }
        }, new TriggerContext() {
            @Override
            public Identifier eventId() {
                return Identifier.fromNamespaceAndPath("test", "manual");
            }

            @Override
            public Object output(String name) {
                return null;
            }
        }, VarStore.inMemory(), null, null, LoggerFactory.getLogger("blueprint-test"));
    }

    private static Ir program() {
        return new Ir(BLUEPRINT, 0, FAST, List.of(
                new Instruction.Call(FAST_NODE.id(), List.of(), List.of(),
                        Map.of("exec_out", 1), 1, false, FAST),
                new Instruction.Call(SLOW_NODE.id(), List.of(), List.of(),
                        Map.of(), 5, false, SLOW),
                new Instruction.Return(null)), 0);
    }

    @AfterEach
    void stopProfiling() {
        Profiler.disableAll();
    }

    @Test
    void nothingIsMeasuredWhileProfilingIsOff() {
        assertFalse(Profiler.active());
        assertNull(Profiler.of(BLUEPRINT));
        Ir ir = program();
        BlueprintVm.run(ir, ExecutionState.fresh(ir), env(), 100);
        assertNull(Profiler.of(BLUEPRINT), "aucun profileur n'apparaît tout seul");
    }

    /**
     * <b>Un nœud abaissé ne ment pas sur ce qu'il a exécuté.</b>
     *
     * <p>Une boucle n'existe pas à l'exécution : le compilateur l'abaisse en plusieurs
     * instructions qui gardent toutes l'identifiant du nœud source. Le compteur ne
     * retenant que la PREMIÈRE sorte vue, le rapport affichait onze appels de
     * {@code list/size} pour un {@code flow/for_each} sur trois éléments — un chiffre
     * juste sous une étiquette fausse, ce qui est pire qu'un chiffre absent.
     *
     * <p>Vu en jeu sur le banc de performance, pas en test : aucun test n'exécutait de
     * nœud abaissé sous profileur.
     */
    @Test
    void unNoeudAbaisseAnnonceSesPlusieursSortesDInstructions() {
        Profiler profiler = Profiler.enable(BLUEPRINT);
        UUID lowered = UUID.randomUUID();
        Identifier first = Identifier.fromNamespaceAndPath("blueprint", "list/size");
        Identifier second = Identifier.fromNamespaceAndPath("blueprint", "logic/less");
        Identifier third = Identifier.fromNamespaceAndPath("blueprint", "list/get");

        profiler.record(lowered, first, 10, 1);
        profiler.record(lowered, second, 10, 1);
        profiler.record(lowered, second, 10, 1);
        profiler.record(lowered, third, 10, 1);

        var cost = profiler.top(1).get(0);
        assertEquals(4, cost.calls(), "les quatre instructions comptent pour le même nœud");
        assertTrue(cost.label().startsWith("blueprint:list/size"), cost.label());
        assertTrue(cost.label().endsWith(" +2"),
                "deux AUTRES sortes ont tourné, l'étiquette doit le dire : " + cost.label());
    }

    /** Un nœud d'une seule sorte garde une étiquette nue — pas de bruit inutile. */
    @Test
    void unNoeudOrdinaireGardeSonNomSeul() {
        Profiler profiler = Profiler.enable(BLUEPRINT);
        UUID plain = UUID.randomUUID();
        Identifier type = Identifier.fromNamespaceAndPath("blueprint", "math/add");
        profiler.record(plain, type, 10, 1);
        profiler.record(plain, type, 10, 1);

        assertEquals("blueprint:math/add", profiler.top(1).get(0).label());
    }

    @Test
    void everyNodeIsCountedTimedAndFuelled() {
        Profiler profiler = Profiler.enable(BLUEPRINT);
        Ir ir = program();
        BlueprintVm.run(ir, ExecutionState.fresh(ir), env(), 100);
        BlueprintVm.run(ir, ExecutionState.fresh(ir), env(), 100);

        assertEquals(2, profiler.nodeCount());
        assertEquals(4, profiler.totalCalls(), "deux nœuds × deux exécutions");
        List<Profiler.NodeCost> top = profiler.top(10);
        assertEquals(SLOW, top.get(0).node(), "le plus coûteux vient en tête");
        assertEquals(2, top.get(0).calls());
        assertEquals(10, top.get(0).fuel(), "le fuel du nœud, deux fois");
        assertTrue(top.get(0).nanos() > 0, "le temps est mesuré");
        assertTrue(top.get(0).nanos() >= top.get(1).nanos());
        assertTrue(profiler.totalNanos() >= top.get(0).nanos());
    }

    @Test
    void aProfilerOnAnotherBlueprintIgnoresThisOne() {
        Profiler other = Profiler.enable(Identifier.fromNamespaceAndPath("test", "autre"));
        Ir ir = program();
        BlueprintVm.run(ir, ExecutionState.fresh(ir), env(), 100);
        assertEquals(0, other.nodeCount());
    }

    @Test
    void topIsBoundedAndDeterministic() {
        Profiler profiler = new Profiler();
        Identifier type = Identifier.fromNamespaceAndPath("t", "n");
        for (int i = 0; i < 25; i++) {
            profiler.record(UUID.nameUUIDFromBytes(("n" + i).getBytes()), type, 1_000, 1);
        }
        assertEquals(10, profiler.top(10).size());
        assertEquals(profiler.top(10), profiler.top(10),
                "à temps égal, l'ordre ne bouge pas d'un affichage à l'autre");
        assertEquals(25, profiler.nodeCount());
    }

    @Test
    void syntheticNodesAreNotAttributed() {
        Profiler profiler = new Profiler();
        profiler.record(null, Identifier.fromNamespaceAndPath("t", "n"), 500, 1);
        assertEquals(0, profiler.nodeCount(), "un nœud synthétisé n'appartient à personne");
    }

    @Test
    void theReportNamesTheHeaviestNodes() {
        Profiler profiler = new Profiler();
        profiler.record(SLOW, SLOW_NODE.id(), 8_000_000, 5);
        profiler.record(FAST, FAST_NODE.id(), 2_000_000, 1);

        String report = profiler.report(BLUEPRINT, 10);
        assertTrue(report.contains("test:profile"));
        assertTrue(report.contains("t:slow"), report);
        assertTrue(report.indexOf("t:slow") < report.indexOf("t:fast"),
                "le plus coûteux d'abord");
        assertTrue(report.contains("80.0%"), "sa part du temps total : " + report);
    }

    @Test
    void resetAndDisableForgetEverything() {
        Profiler profiler = Profiler.enable(BLUEPRINT);
        profiler.record(FAST, FAST_NODE.id(), 100, 1);
        profiler.reset();
        assertEquals(0, profiler.nodeCount());

        assertTrue(Profiler.disable(BLUEPRINT));
        assertFalse(Profiler.active(), "plus aucun profileur : le drapeau retombe");
        assertNull(Profiler.of(BLUEPRINT));
    }
}
