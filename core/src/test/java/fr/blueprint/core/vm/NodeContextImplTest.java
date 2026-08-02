package fr.blueprint.core.vm;

import fr.blueprint.api.event.TriggerContext;
import fr.blueprint.api.node.BlueprintHandle;
import fr.blueprint.api.node.NodeContext;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.testmod.TestPlugin;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contexte d'exécution (story 2.3) — première exécution de bout en bout du projet. */
class NodeContextImplTest {

    private static final BlueprintHandle HANDLE = new BlueprintHandle() {
        @Override
        public Identifier id() {
            return Identifier.fromNamespaceAndPath("test", "graph");
        }

        @Override
        public boolean enabled() {
            return true;
        }
    };

    private static final TriggerContext TRIGGER =
            () -> Identifier.fromNamespaceAndPath("blueprint", "event/server_tick");

    private static NodeType testmodNode(String path) {
        var loaded = PluginLoader.load(List.of(
                new PluginLoader.PluginEntry("blueprint_testmod", new TestPlugin())));
        return loaded.nodes().get(Identifier.fromNamespaceAndPath("blueprint_testmod", path)).orElseThrow();
    }

    private static NodeContextImpl context(NodeType type, Map<String, Object> inputs) {
        return new NodeContextImpl(type, inputs, null, null, HANDLE, TRIGGER,
                LoggerFactory.getLogger("blueprint-test"));
    }

    @Test
    void testmodNodesRunEndToEnd() throws Exception {
        // ping : écho du message d'entrée.
        var ping = NodeContextImpl.invoke(testmodNode("ping"),
                context(testmodNode("ping"), Map.of("message", "salut")));
        assertEquals("salut", ping.outputs().get("echo"));

        // ping sans entrée : le défaut du pin s'applique.
        var pingDefault = NodeContextImpl.invoke(testmodNode("ping"),
                context(testmodNode("ping"), Map.of()));
        assertEquals("ping", pingDefault.outputs().get("echo"));

        // double_it : nœud pur.
        var doubled = NodeContextImpl.invoke(testmodNode("double_it"),
                context(testmodNode("double_it"), Map.of("value", 21)));
        assertEquals(42, doubled.outputs().get("result"));

        // odd_or_even : la branche choisie dépend de l'entrée.
        var even = NodeContextImpl.invoke(testmodNode("odd_or_even"),
                context(testmodNode("odd_or_even"), Map.of("value", 4)));
        assertEquals("even", even.chosenExec());
        var odd = NodeContextImpl.invoke(testmodNode("odd_or_even"),
                context(testmodNode("odd_or_even"), Map.of("value", 7)));
        assertEquals("odd", odd.chosenExec());
    }

    private static NodeType probe(String path, fr.blueprint.api.node.NodeAction action) {
        return NodeType.builder(Identifier.fromNamespaceAndPath("probe", path))
                .exec()
                .in("number", PinTypes.INT)
                .out("text", PinTypes.STRING)
                .execOut("other")
                .action(action)
                .build();
    }

    @Test
    void developerErrorsAreImmediateAndNamed() {
        NodeType type = probe("errors", ctx -> {
        });
        NodeContextImpl ctx = context(type, Map.of("number", 5));

        assertNamed(assertThrows(IllegalStateException.class, () -> ctx.in("absent")));
        assertNamed(assertThrows(IllegalStateException.class, () -> ctx.out("absent", "x")));
        assertNamed(assertThrows(IllegalStateException.class, () -> ctx.out("text", 42)));
        assertNamed(assertThrows(IllegalStateException.class, () -> ctx.out("exec_out", "x")));
        assertNamed(assertThrows(IllegalStateException.class, () -> ctx.exec("text")));
        assertNamed(assertThrows(IllegalStateException.class, () -> ctx.exec("absent")));
        assertNamed(assertThrows(IllegalStateException.class, () -> ctx.suspend(0)));
        assertNamed(assertThrows(IllegalStateException.class, ctx::server));
        assertNamed(assertThrows(IllegalStateException.class, ctx::level));
    }

    private static void assertNamed(IllegalStateException ex) {
        assertTrue(ex.getMessage().contains("probe:errors"),
                "le message doit nommer le nœud : " + ex.getMessage());
    }

    @Test
    void leakGuardInvalidatesContextAfterInvoke() throws Exception {
        NodeContext[] leaked = new NodeContext[1];
        NodeType type = probe("leak", ctx -> leaked[0] = ctx);
        NodeContextImpl.invoke(type, context(type, Map.of("number", 1)));

        // AC5 : le contexte conservé est mort — chaque méthode lève.
        assertThrows(IllegalStateException.class, () -> leaked[0].in("number"));
        assertThrows(IllegalStateException.class, () -> leaked[0].out("text", "x"));
        assertThrows(IllegalStateException.class, () -> leaked[0].exec("other"));
        assertThrows(IllegalStateException.class, () -> leaked[0].logger());
    }

    @Test
    void contextIsInvalidatedEvenWhenActionThrows() {
        NodeContext[] leaked = new NodeContext[1];
        NodeType type = probe("thrower", ctx -> {
            leaked[0] = ctx;
            throw new RuntimeException("boum");
        });
        assertThrows(RuntimeException.class,
                () -> NodeContextImpl.invoke(type, context(type, Map.of("number", 1))));
        assertThrows(IllegalStateException.class, () -> leaked[0].in("number"));
    }

    @Test
    void suspendAndFailAreRecordedForTheVm() throws Exception {
        NodeType suspender = probe("suspender", ctx -> ctx.suspend(40));
        var suspended = NodeContextImpl.invoke(suspender, context(suspender, Map.of("number", 1)));
        assertEquals(40, suspended.suspendTicks());
        assertNull(suspended.failReason());

        NodeType failer = probe("failer", ctx -> ctx.fail(Component.literal("raison")));
        var failed = NodeContextImpl.invoke(failer, context(failer, Map.of("number", 1)));
        assertEquals(-1, failed.suspendTicks());
        assertEquals("raison", failed.failReason().getString());
    }

    @Test
    void handleAndTriggerAreExposed() throws Exception {
        NodeType type = probe("meta", ctx -> {
            assertEquals("test:graph", ctx.blueprint().id().toString());
            assertTrue(ctx.blueprint().enabled());
            assertEquals("blueprint:event/server_tick", ctx.trigger().eventId().toString());
        });
        NodeContextImpl.invoke(type, context(type, Map.of("number", 1)));
    }

    @Test
    void lastExecCallWins() throws Exception {
        NodeType type = probe("rechoose", ctx -> {
            ctx.exec("exec_out");
            ctx.exec("other");
        });
        var ctx = NodeContextImpl.invoke(type, context(type, Map.of("number", 1)));
        assertEquals("other", ctx.chosenExec());
    }
}
