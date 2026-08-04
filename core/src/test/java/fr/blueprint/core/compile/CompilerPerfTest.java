package fr.blueprint.core.compile;

import fr.blueprint.api.BlueprintPlugin;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.GraphLimits;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.Vec2d;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** NFR2 : compilation d'un graphe de 1 000 nœuds < 50 ms, mesurée et consignée. */
class CompilerPerfTest {

    private static final Logger LOGGER = LoggerFactory.getLogger("blueprint-test");

    @Test
    void thousandNodeChainCompilesUnderFiftyMillis() {
        BlueprintPlugin plugin = registry -> registry.register(
                NodeType.builder(Identifier.fromNamespaceAndPath("perf", "pass"))
                        .exec().action(ctx -> {
                        }).build());
        var loaded = PluginLoader.load(List.of(new PluginLoader.PluginEntry("perf", plugin)));

        var bp = new Blueprint(Identifier.fromNamespaceAndPath("perf", "chain"));
        Identifier pass = Identifier.fromNamespaceAndPath("perf", "pass");
        GraphLimits limits = new GraphLimits(1200);
        UUID first = null;
        UUID previous = null;
        for (int i = 0; i < 1000; i++) {
            UUID uuid = UUID.nameUUIDFromBytes(("n" + i).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var add = new EditOperation.AddNode(uuid, pass, new Vec2d(i * 10, 0))
                    .apply(bp, loaded.nodes(), limits);
            assertTrue(add.applied());
            if (previous != null) {
                assertTrue(new EditOperation.AddLink(new Link(previous, "exec_out", uuid, "exec_in"))
                        .apply(bp, loaded.nodes(), limits).applied());
            } else {
                first = uuid;
            }
            previous = uuid;
        }

        // Échauffement JIT, puis meilleur de cinq mesures — en temps <b>processeur du
        // fil</b>, pas en temps mural.
        //
        // Le meilleur de cinq mesures murales ne suffisait pas : mesuré seul, ce
        // compilateur prend 18 ms ; mesuré pendant que la suite complète tourne en
        // parallèle, il « prend » 54 ms et le test échoue. Il mesurait donc la charge de
        // la machine, et un test qui rougit sans qu'aucun code n'ait changé apprend
        // surtout à relancer plutôt qu'à chercher — c'est ainsi qu'une vraie régression
        // finit par passer inaperçue.
        //
        // Le temps processeur du fil ne compte que les instants où ce fil a réellement
        // tourné : une préemption ne s'y voit pas, une régression du compilateur si.
        // C'est ce que le NFR2 veut dire, et c'était ce qu'on cherchait depuis le début.
        for (int i = 0; i < 3; i++) {
            Compiler.compile(bp, loaded.nodes(), first);
        }
        var threads = java.lang.management.ManagementFactory.getThreadMXBean();
        boolean cpuTime = threads.isCurrentThreadCpuTimeSupported();
        long bestMs = Long.MAX_VALUE;
        Compiler.CompileResult result = null;
        for (int i = 0; i < 5; i++) {
            long begin = cpuTime ? threads.getCurrentThreadCpuTime() : System.nanoTime();
            result = Compiler.compile(bp, loaded.nodes(), first);
            long end = cpuTime ? threads.getCurrentThreadCpuTime() : System.nanoTime();
            bestMs = Math.min(bestMs, (end - begin) / 1_000_000);
        }

        assertTrue(result.success());
        LOGGER.info("Compilation de 1 000 nœuds : {} ms au mieux sur 5 (budget NFR2 : 50 ms)",
                bestMs);
        assertTrue(bestMs < 50, "compilation en " + bestMs + " ms ≥ 50 ms");
    }
}
