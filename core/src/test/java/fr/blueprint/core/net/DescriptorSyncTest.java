package fr.blueprint.core.net;

import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.PinKind;
import fr.blueprint.api.pin.PinType;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.registry.NodeDescriptor;
import fr.blueprint.core.registry.PluginLoader;
import fr.blueprint.core.registry.RegistryHash;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Synchro du registre (story 6.2) : hash, fragmentation, compression, décodage. */
class DescriptorSyncTest {

    private static PluginLoader.LoadedRegistries standard() {
        return PluginLoader.load(List.of(), true);
    }

    private static Function<Identifier, PinType> resolver(PluginLoader.LoadedRegistries loaded) {
        return id -> loaded.pinTypes().get(id).orElse(null);
    }

    /** AC1 : deux registres identiques → même hash, aucun descripteur n'a à circuler. */
    @Test
    void identicalRegistriesShareTheirHash() {
        assertEquals(RegistryHash.of(standard().nodes()), RegistryHash.of(standard().nodes()));
    }

    @Test
    void entryPointChangesTheHash() {
        Identifier id = Identifier.fromNamespaceAndPath("m", "n");
        NodeType plain = NodeType.builder(id).exec().action(ctx -> {
        }).build();
        NodeType entry = NodeType.builder(id).exec().entryPoint().action(ctx -> {
        }).build();
        assertNotEquals(NodeDescriptor.of(plain).canonical(),
                NodeDescriptor.of(entry).canonical(),
                "un point d'entrée ne se confond pas avec un nœud ordinaire sur le fil");
    }

    /** La bibliothèque standard entière fait l'aller-retour sans perte. */
    @Test
    void wholeStandardLibraryRoundTrips() {
        var loaded = standard();
        List<NodeDescriptor> originals = new ArrayList<>();
        for (NodeType type : loaded.nodes().all()) {
            originals.add(NodeDescriptor.of(type));
        }

        byte[] stream = DescriptorSync.toBytes(originals);
        List<NodeDescriptor> decoded = DescriptorSync.fromBytes(
                DescriptorSync.reassemble(DescriptorSync.chunks(stream)), resolver(loaded));

        assertEquals(originals.size(), decoded.size());
        Map<Identifier, NodeDescriptor> byId = new HashMap<>();
        decoded.forEach(d -> byId.put(d.id(), d));
        for (NodeDescriptor original : originals) {
            assertEquals(original, byId.get(original.id()), "aller-retour de " + original.id());
        }
        assertTrue(decoded.stream().anyMatch(NodeDescriptor::entryPoint),
                "les points d'entrée (événements) traversent le fil");
    }

    /** AC2 : 5 000 descripteurs — fragmentés sous la borne, compressés, réassemblés entiers. */
    @Test
    void fiveThousandDescriptorsChunkAndReassemble() {
        List<NodeDescriptor> many = new ArrayList<>(5_000);
        for (int i = 0; i < 5_000; i++) {
            many.add(synthetic(i));
        }

        byte[] stream = DescriptorSync.toBytes(many);
        List<byte[]> chunks = DescriptorSync.chunks(stream);

        assertTrue(chunks.size() > 1, "5 000 descripteurs tiennent en plusieurs fragments");
        for (byte[] chunk : chunks) {
            assertTrue(chunk.length <= DescriptorSync.CHUNK_BYTES,
                    "fragment de " + chunk.length + " octets au-dessus de la borne");
        }

        List<NodeDescriptor> decoded = DescriptorSync.fromBytes(
                DescriptorSync.reassemble(chunks), id -> PinTypes.INT);
        assertEquals(5_000, decoded.size());
        assertEquals(many.get(4_999), decoded.get(4_999));

        // La compression n'est pas décorative : ces descripteurs se répètent beaucoup.
        int uncompressed = 0;
        for (NodeDescriptor descriptor : many) {
            uncompressed += descriptor.canonical().length();
        }
        assertTrue(stream.length < uncompressed / 4,
                "flux compressé de " + stream.length + " octets pour " + uncompressed + " bruts");
    }

    /**
     * AC3 : un descripteur dont un type de pin ne se résout pas est sauté, jamais
     * propagé à moitié — les autres arrivent quand même.
     */
    @Test
    void unresolvableDescriptorIsSkippedWithoutLosingTheRest() {
        List<NodeDescriptor> mixed = List.of(synthetic(1), synthetic(2));
        List<NodeDescriptor> decoded = DescriptorSync.fromBytes(
                DescriptorSync.toBytes(mixed), id -> null);
        assertEquals(0, decoded.size(), "aucun type résolu → aucun descripteur, pas de crash");

        decoded = DescriptorSync.fromBytes(DescriptorSync.toBytes(mixed), id -> PinTypes.INT);
        assertEquals(2, decoded.size());
    }

    /** Un flux tronqué ne lève pas : la synchro échoue proprement. */
    @Test
    void truncatedStreamFailsQuietly() {
        byte[] stream = DescriptorSync.toBytes(List.of(synthetic(0)));
        byte[] truncated = new byte[stream.length / 2];
        System.arraycopy(stream, 0, truncated, 0, truncated.length);
        assertEquals(0, DescriptorSync.fromBytes(truncated, id -> PinTypes.INT).size());
    }

    private static NodeDescriptor synthetic(int i) {
        Identifier id = Identifier.fromNamespaceAndPath("mod" + (i % 7), "node_" + i);
        return new NodeDescriptor(id, "misc",
                "blueprint.node.mod.node_" + i + ".name",
                "blueprint.node.mod.node_" + i + ".desc",
                List.of(new NodeDescriptor.PinDescriptor("in", PinKind.DATA, PinTypes.INT, null)),
                List.of(new NodeDescriptor.PinDescriptor("out", PinKind.DATA, PinTypes.INT, null)),
                true, Permission.SAFE, 1, true, false);
    }
}
