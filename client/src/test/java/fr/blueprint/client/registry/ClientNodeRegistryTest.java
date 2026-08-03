package fr.blueprint.client.registry;

import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.PinKind;
import fr.blueprint.api.pin.PinType;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.graph.NodeShape;
import fr.blueprint.core.graph.NodeTypeLookup;
import fr.blueprint.core.net.DescriptorSync;
import fr.blueprint.core.registry.NodeDescriptor;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story 6.2 AC3 : ce que le client déduit des descripteurs reçus doit être EXACTEMENT
 * ce que le serveur valide — sinon l'éditeur accepterait des liens que le serveur
 * refuse (ou l'inverse) dès qu'un mod manque côté client.
 */
class ClientNodeRegistryTest {

    /**
     * La bibliothèque standard porte des pins {@code itemstack} dont le défaut est
     * {@code ItemStack.EMPTY} : sans amorçage, la seule lecture d'une forme lèverait.
     */
    @org.junit.jupiter.api.BeforeAll
    static void bootstrapGame() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void shapesFromDescriptorsMatchTheServerRegistryNodeForNode() {
        var loaded = PluginLoader.load(List.of(), true);
        List<NodeDescriptor> descriptors = new ArrayList<>();
        for (NodeType type : loaded.nodes().all()) {
            descriptors.add(NodeDescriptor.of(type));
        }
        // Passage par le fil : c'est bien le descripteur DÉCODÉ qui alimente la vue.
        List<NodeDescriptor> received = DescriptorSync.fromBytes(
                DescriptorSync.toBytes(descriptors),
                id -> loaded.pinTypes().get(id).orElse(null));
        assertEquals(descriptors.size(), received.size());

        NodeTypeLookup client = ClientNodeRegistry.of(received).lookup();
        for (NodeType type : loaded.nodes().all()) {
            NodeShape server = loaded.nodes().shape(type.id());
            assertEquals(server, client.shape(type.id()), "forme de " + type.id());
        }
        assertNull(client.shape(Identifier.fromNamespaceAndPath("nowhere", "nope")),
                "un type inconnu reste fantôme");
    }

    @Test
    void descriptorsFromAnAbsentModStayUsable() {
        // Type de pin que le client n'a pas : la synchro le remplace par un opaque —
        // le nœud garde ses pins, son point d'entrée et sa permission.
        PinType opaque = PinType.builder(Identifier.fromNamespaceAndPath("othermod", "mana"))
                .javaType(Object.class).noLiteral().build();
        NodeDescriptor foreign = new NodeDescriptor(
                Identifier.fromNamespaceAndPath("othermod", "drain"),
                "misc", "t", "d",
                List.of(new NodeDescriptor.PinDescriptor("mana", PinKind.DATA, opaque, null),
                        new NodeDescriptor.PinDescriptor("in", PinKind.EXEC, PinTypes.EXEC, null)),
                List.of(new NodeDescriptor.PinDescriptor("out", PinKind.EXEC, PinTypes.EXEC, null)),
                false, Permission.GAMEPLAY, 3, true, true);

        NodeShape shape = ClientNodeRegistry.of(List.of(foreign))
                .lookup().shape(foreign.id());
        assertNotNull(shape);
        assertEquals(2, shape.inputs().size());
        assertTrue(shape.entryPoint());
        assertEquals(Permission.GAMEPLAY, shape.permission());
        assertTrue(shape.input("mana").required(),
                "une entrée DATA sans défaut reste obligatoire, mod présent ou non");
    }
}
