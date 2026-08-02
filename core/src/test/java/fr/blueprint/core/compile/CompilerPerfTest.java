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

        // Échauffement JIT, puis mesure.
        Compiler.compile(bp, loaded.nodes(), first);
        long begin = System.nanoTime();
        var result = Compiler.compile(bp, loaded.nodes(), first);
        long elapsedMs = (System.nanoTime() - begin) / 1_000_000;

        assertTrue(result.success());
        LOGGER.info("Compilation de 1 000 nœuds : {} ms (budget NFR2 : 50 ms)", elapsedMs);
        assertTrue(elapsedMs < 50, "compilation en " + elapsedMs + " ms ≥ 50 ms");
    }
}
