package fr.blueprint.core.graph;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinType;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.registry.PinTypeRegistryImpl;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static fr.blueprint.core.graph.TestNodes.apply;
import static fr.blueprint.core.graph.TestNodes.node;
import static fr.blueprint.core.graph.TestNodes.uuid;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Round-trip complet (story 1.4, AC1, AC3, AC5) sur un graphe de 200 nœuds. */
class GraphNbtRoundTripTest {

    private static final Logger LOGGER = LoggerFactory.getLogger("blueprint-test");

    static Function<Identifier, PinType> resolver() {
        var registry = new PinTypeRegistryImpl();
        registry.registerBuiltins();
        return id -> registry.get(id).orElse(null);
    }

    /** 200 nœuds : 50 grappes start→print + add→half, littéraux, 12 variables, 5 commentaires. */
    static Blueprint bigGraph() {
        var bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "big"),
                new BlueprintMeta("Léa", "graphe de charge", "2.1.0",
                        fr.blueprint.api.node.Permission.WORLD));
        for (int i = 0; i < 50; i++) {
            UUID start = node(bp, "start" + i, TestNodes.START);
            UUID print = node(bp, "print" + i, TestNodes.PRINT);
            UUID add = node(bp, "add" + i, TestNodes.ADD);
            UUID half = node(bp, "half" + i, TestNodes.HALF);
            apply(bp, new EditOperation.AddLink(new Link(start, "exec_out", print, "exec_in")));
            apply(bp, new EditOperation.AddLink(new Link(add, "sum", half, "value")));
            apply(bp, new EditOperation.SetLiteral(print, "text",
                    LiteralValue.of(PinTypes.STRING, "message n°" + i + " — château")));
            apply(bp, new EditOperation.SetLiteral(add, "a", LiteralValue.of(PinTypes.INT, i)));
            apply(bp, new EditOperation.MoveNode(add, new Vec2d(i * 40, i * 25)));
        }
        for (int i = 0; i < 10; i++) {
            apply(bp, new EditOperation.AddVariable(new Variable("var" + i, PinTypes.INT,
                    LiteralValue.of(PinTypes.INT, i * 7), VarScope.values()[i % 4], i % 2 == 0)));
        }
        // SER-001 en conditions réelles : des variables aux types structurels.
        apply(bp, new EditOperation.AddVariable(new Variable("scores",
                PinTypes.listOf(PinTypes.INT),
                LiteralValue.of(PinTypes.listOf(PinTypes.INT), List.of(1, 2, 3)),
                VarScope.GRAPH, false)));
        apply(bp, new EditOperation.AddVariable(new Variable("tableau",
                PinTypes.mapOf(PinTypes.STRING, PinTypes.listOf(PinTypes.INT)), null,
                VarScope.WORLD, false)));
        for (int i = 0; i < 5; i++) {
            apply(bp, new EditOperation.AddComment(new CommentBox(uuid("comment" + i),
                    "zone n°" + i, new Vec2d(i * 100, -50), new Vec2d(200, 120), 0xFF000000 + i)));
        }
        return bp;
    }

    @Test
    void twoHundredNodesRoundTripIdentically() {
        Blueprint original = bigGraph();
        assertEquals(200, original.nodes().size());

        CompoundTag encoded = GraphNbt.encode(original);
        Blueprint decoded = GraphNbt.decode(encoded, resolver());

        assertTrue(original.contentEquals(decoded), "round-trip non identique");
        assertEquals(original.revision(), decoded.revision());
        // Second tour : encoder le décodé redonne un NBT égal (stabilité).
        assertEquals(encoded, GraphNbt.encode(decoded));
    }

    @Test
    void compressedSizeStaysWithinBudget() throws IOException {
        // NFR5 : < 1 Mo pour 1 000 nœuds → budget test 200 Ko pour 200 nœuds.
        CompoundTag encoded = GraphNbt.encode(bigGraph());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIo.writeCompressed(encoded, out);
        int size = out.size();
        LOGGER.info("Graphe de 200 nœuds : {} octets gzip ({} octets/nœud)", size, size / 200);
        assertTrue(size < 200_000, "taille gzip " + size + " ≥ 200 Ko");

        // Et le flux compressé se relit tel quel.
        CompoundTag reread = NbtIo.readCompressed(
                new ByteArrayInputStream(out.toByteArray()), NbtAccounter.unlimitedHeap());
        assertEquals(encoded, reread);
    }

    @Test
    void emptyBlueprintRoundTrips() {
        var bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "vide"));
        bp.setEnabled(false);
        Blueprint decoded = GraphNbt.decode(GraphNbt.encode(bp), resolver());
        assertTrue(bp.contentEquals(decoded));
        assertEquals(false, decoded.enabled());
    }
}
