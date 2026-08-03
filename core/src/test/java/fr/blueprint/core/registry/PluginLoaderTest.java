package fr.blueprint.core.registry;

import fr.blueprint.api.BlueprintPlugin;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.pin.PinKind;
import fr.blueprint.api.pin.PinType;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.api.registry.NodeRegistry;
import fr.blueprint.api.registry.PinTypeRegistry;
import fr.blueprint.core.graph.NodeShape;
import fr.blueprint.testmod.TestPlugin;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chargement des plugins (story 2.2) : phases, isolation, gel, et le testmod réel. */
class PluginLoaderTest {

    private static Identifier id(String ns, String path) {
        return Identifier.fromNamespaceAndPath(ns, path);
    }

    private static NodeType simpleNode(Identifier nodeId) {
        return NodeType.builder(nodeId).exec().action(ctx -> {
        }).build();
    }

    @Test
    void realTestmodRegistersItsFourNodes() {
        // AC5 : le vrai TestPlugin, chargé comme le ferait Fabric.
        var loaded = PluginLoader.load(List.of(
                new PluginLoader.PluginEntry("blueprint_testmod", new TestPlugin())));

        assertTrue(loaded.failedMods().isEmpty());
        assertEquals(4, loaded.nodes().all().size(), "trois nœuds au builder + un par annotation");

        NodeType ping = loaded.nodes().get(id("blueprint_testmod", "ping")).orElseThrow();
        assertEquals("ping", ping.inputs().get(1).defaultValue().value());
        assertTrue(loaded.nodes().get(id("blueprint_testmod", "double_it")).orElseThrow().pure());
        NodeType branch = loaded.nodes().get(id("blueprint_testmod", "odd_or_even")).orElseThrow();
        assertEquals(List.of("even", "odd"), branch.outputs().stream().map(NodeType.PinSpec::name).toList());
        assertEquals("blueprint_testmod",
                loaded.nodes().providerOf(id("blueprint_testmod", "ping")).orElseThrow());

        // Story 8.1 : le nœud annoté arrive par le MÊME chemin, avec le même fournisseur.
        NodeType shout = loaded.nodes().get(id("blueprint_testmod", "shout")).orElseThrow();
        assertTrue(shout.pure());
        assertEquals(List.of("message", "times"),
                shout.inputs().stream().map(NodeType.PinSpec::name).toList());
        assertEquals("salut", shout.inputs().get(0).defaultValue().value());
        assertEquals("shouted", shout.outputs().get(0).name());
        assertEquals("blueprint_testmod",
                loaded.nodes().providerOf(id("blueprint_testmod", "shout")).orElseThrow());
    }

    @Test
    void registriesFeedTheGraphValidator() {
        // AC6 : NodeRegistryImpl est un NodeTypeLookup — forme dérivée, required correct.
        var loaded = PluginLoader.load(List.of(
                new PluginLoader.PluginEntry("blueprint_testmod", new TestPlugin())));
        NodeShape ping = loaded.nodes().shape(id("blueprint_testmod", "ping"));
        assertNotNull(ping);
        assertEquals(PinKind.EXEC, ping.input("exec_in").kind());
        // « message » a un défaut → pas requis ; « value » de double_it a un défaut de type (0) → pas requis.
        assertTrue(ping.input("message") != null && !ping.input("message").required());
        NodeShape pure = loaded.nodes().shape(id("blueprint_testmod", "double_it"));
        assertTrue(pure.input("value") != null && !pure.input("value").required());
    }

    @Test
    void throwingPluginIsIsolatedAndItsRegistrationsRemoved() {
        BlueprintPlugin broken = new BlueprintPlugin() {
            @Override
            public void registerNodes(NodeRegistry registry) {
                registry.register(simpleNode(id("badmod", "before_crash")));
                throw new IllegalStateException("boum");
            }
        };
        var loaded = PluginLoader.load(List.of(
                new PluginLoader.PluginEntry("badmod", broken),
                new PluginLoader.PluginEntry("blueprint_testmod", new TestPlugin())));

        assertEquals(List.of("badmod"), loaded.failedMods());
        assertTrue(loaded.nodes().get(id("badmod", "before_crash")).isEmpty(),
                "les enregistrements partiels du plugin en échec sont retirés");
        assertEquals(4, loaded.nodes().all().size(), "les autres plugins chargent normalement");
    }

    @Test
    void typesPhaseRunsBeforeNodesAcrossPlugins() {
        // Le mod A déclare un type ; le mod B l'utilise dans un nœud : ordre garanti.
        PinType custom = PinType.builder(id("mod_a", "mana"))
                .javaType(Integer.class)
                .codec(com.mojang.serialization.Codec.INT)
                .streamCodec(ByteBufCodecs.VAR_INT)
                .build();
        BlueprintPlugin providerMod = new BlueprintPlugin() {
            @Override
            public void registerTypes(PinTypeRegistry registry) {
                registry.register(custom);
            }

            @Override
            public void registerNodes(NodeRegistry registry) {
            }
        };
        BlueprintPlugin consumerMod = registry -> registry.register(
                NodeType.builder(id("mod_b", "spend_mana"))
                        .exec().in("cost", custom).action(ctx -> {
                        }).build());

        var loaded = PluginLoader.load(List.of(
                new PluginLoader.PluginEntry("mod_b", consumerMod),   // ordre volontairement inversé
                new PluginLoader.PluginEntry("mod_a", providerMod)));
        assertTrue(loaded.failedMods().isEmpty());
        assertTrue(loaded.nodes().get(id("mod_b", "spend_mana")).isPresent());
    }

    @Test
    void duplicateAcrossModsNamesBothAndIsolatesSecond() {
        BlueprintPlugin first = registry -> registry.register(simpleNode(id("shared", "node")));
        BlueprintPlugin second = registry -> registry.register(simpleNode(id("shared", "node")));

        var loaded = PluginLoader.load(List.of(
                new PluginLoader.PluginEntry("mod_one", first),
                new PluginLoader.PluginEntry("mod_two", second)));

        assertEquals(List.of("mod_two"), loaded.failedMods());
        // Le premier arrivé garde le nœud.
        assertEquals("mod_one", loaded.nodes().providerOf(id("shared", "node")).orElseThrow());
    }

    @Test
    void registriesAreFrozenAfterLoad() {
        var loaded = PluginLoader.load(List.of());
        assertTrue(loaded.nodes().isFrozen());
        assertTrue(loaded.pinTypes().isFrozen());
        assertThrows(IllegalStateException.class,
                () -> loaded.nodes().register(simpleNode(id("late", "node"))));
        assertEquals(16, loaded.pinTypes().all().size(), "les 16 types de base sont là");
    }

    @Test
    void emptyLoadStillProvidesBuiltins() {
        var loaded = PluginLoader.load(List.of());
        assertTrue(loaded.pinTypes().get(id("blueprint", "int")).isPresent());
        assertTrue(loaded.nodes().all().isEmpty());
        assertTrue(loaded.failedMods().isEmpty());
    }
}
