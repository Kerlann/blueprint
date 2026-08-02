package fr.blueprint.core.registry;

import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.pin.PinType;
import fr.blueprint.testmod.TestPlugin;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Descripteurs transmissibles et hash de registre (story 2.4). */
class NodeDescriptorTest {

    private static PluginLoader.LoadedRegistries loadTestmod() {
        return PluginLoader.load(List.of(
                new PluginLoader.PluginEntry("blueprint_testmod", new TestPlugin())));
    }

    private static Function<Identifier, PinType> resolver(PluginLoader.LoadedRegistries loaded) {
        return id -> loaded.pinTypes().get(id).orElse(null);
    }

    @Test
    void descriptorCarriesEverythingButTheAction() {
        var loaded = loadTestmod();
        NodeType ping = loaded.nodes().get(Identifier.fromNamespaceAndPath("blueprint_testmod", "ping")).orElseThrow();
        NodeDescriptor descriptor = NodeDescriptor.of(ping);

        assertEquals(ping.id(), descriptor.id());
        assertEquals("debug", descriptor.category());
        assertEquals(ping.titleKey(), descriptor.titleKey());
        assertEquals(2, descriptor.inputs().size());
        assertEquals("ping", descriptor.inputs().get(1).defaultValue().value());
        // AC4 : le record n'expose ni action ni classe du fournisseur — vérifié par
        // construction (aucun champ de ce type), on s'assure ici de la projection complète.
        assertEquals(ping.permission(), descriptor.permission());
        assertEquals(ping.fuelCost(), descriptor.fuelCost());
    }

    @Test
    void nbtAndNetworkRoundTripOnRealTestmodNodes() {
        var loaded = loadTestmod();
        var resolve = resolver(loaded);
        for (NodeType type : loaded.nodes().all()) {
            NodeDescriptor original = NodeDescriptor.of(type);

            // Payload NBT.
            CompoundTag tag = original.encode();
            NodeDescriptor decoded = NodeDescriptor.decode(tag, resolve);
            assertEquals(original, decoded, "round-trip NBT de " + type.id());

            // Passage réseau réel (le paquet de la 6.2 transportera ce NBT).
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            ByteBufCodecs.TRUSTED_COMPOUND_TAG.encode(buf, tag);
            CompoundTag rewired = ByteBufCodecs.TRUSTED_COMPOUND_TAG.decode(buf);
            assertEquals(0, buf.readableBytes());
            assertEquals(original, NodeDescriptor.decode(rewired, resolve),
                    "round-trip réseau de " + type.id());
        }
    }

    @Test
    void decodedTypesKeepIdentity() {
        var loaded = loadTestmod();
        NodeType pure = loaded.nodes().get(Identifier.fromNamespaceAndPath("blueprint_testmod", "double_it")).orElseThrow();
        NodeDescriptor decoded = NodeDescriptor.decode(NodeDescriptor.of(pure).encode(), resolver(loaded));
        assertSame(fr.blueprint.api.pin.PinTypes.INT, decoded.inputs().get(0).type(),
                "le décodage repasse par le registre : identité des types préservée");
    }

    @Test
    void unresolvableTypeFailsCleanly() {
        var loaded = loadTestmod();
        NodeType ping = loaded.nodes().get(Identifier.fromNamespaceAndPath("blueprint_testmod", "ping")).orElseThrow();
        CompoundTag tag = NodeDescriptor.of(ping).encode();
        assertNull(NodeDescriptor.decode(tag, id -> null),
                "type irrésoluble → null, jamais un descripteur partiel");
    }

    @Test
    void registryHashIsStableOrderIndependentAndChangeSensitive() {
        // Stable : deux chargements identiques → même hash.
        assertEquals(RegistryHash.of(loadTestmod().nodes()), RegistryHash.of(loadTestmod().nodes()));

        // Indépendant de l'ordre d'enregistrement.
        NodeType a = NodeType.builder(Identifier.fromNamespaceAndPath("m", "a"))
                .exec().action(ctx -> {
                }).build();
        NodeType b = NodeType.builder(Identifier.fromNamespaceAndPath("m", "b"))
                .exec().action(ctx -> {
                }).build();
        var ab = new NodeRegistryImpl();
        ab.currentProvider("m");
        ab.register(a);
        ab.register(b);
        var ba = new NodeRegistryImpl();
        ba.currentProvider("m");
        ba.register(b);
        ba.register(a);
        assertEquals(RegistryHash.of(ab), RegistryHash.of(ba));

        // Sensible au moindre changement : un nœud en plus → hash différent.
        var loaded = loadTestmod();
        var reference = RegistryHash.of(loaded.nodes());
        var augmented = PluginLoader.load(List.of(
                new PluginLoader.PluginEntry("blueprint_testmod", new TestPlugin()),
                new PluginLoader.PluginEntry("m", registry -> registry.register(a))));
        assertNotEquals(reference, RegistryHash.of(augmented.nodes()));
        assertTrue(reference.length() == 64, "SHA-256 hex attendu");
    }
}
