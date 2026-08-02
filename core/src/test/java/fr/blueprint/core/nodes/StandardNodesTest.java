package fr.blueprint.core.nodes;

import fr.blueprint.api.event.TriggerContext;
import fr.blueprint.api.node.BlueprintHandle;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.core.registry.PluginLoader;
import fr.blueprint.core.vm.NodeContextImpl;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Comportement des nœuds standard (stories 7.1a + 7.2). */
class StandardNodesTest {

    private static final PluginLoader.LoadedRegistries LOADED = PluginLoader.load(List.of(), true);

    private static final BlueprintHandle HANDLE = new BlueprintHandle() {
        @Override
        public Identifier id() {
            return Identifier.fromNamespaceAndPath("test", "std");
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

    private static NodeType type(String path) {
        return LOADED.nodes().get(Identifier.fromNamespaceAndPath("blueprint", path)).orElseThrow();
    }

    private static NodeContextImpl run(String path, Map<String, Object> inputs) throws Exception {
        NodeType type = type(path);
        return NodeContextImpl.invoke(type, new NodeContextImpl(type, inputs, null, null,
                HANDLE, TRIGGER, LoggerFactory.getLogger("blueprint-test")));
    }

    @Test
    void libraryIsRegisteredUnderBlueprintProvider() {
        assertTrue(LOADED.nodes().all().size() >= 32 + 9, "bibliothèque + nœuds d'événement synthétisés");
        assertEquals("blueprint",
                LOADED.nodes().providerOf(Identifier.fromNamespaceAndPath("blueprint", "math/add")).orElseThrow());
    }

    @Test
    void mathNodesComputeWithNumericAdaptation() throws Exception {
        // AC3 : des entrées Integer sur des pins double — l'adaptation runtime les absorbe.
        assertEquals(7.0, run("math/add", Map.of("a", 3, "b", 4)).outputs().get("result"));
        assertEquals(-1.0, run("math/sub", Map.of("a", 3.0, "b", 4.0)).outputs().get("result"));
        assertEquals(12.0, run("math/mul", Map.of("a", 3, "b", 4.0)).outputs().get("result"));
        assertEquals(2.5, run("math/div", Map.of("a", 5.0, "b", 2)).outputs().get("result"));
        assertEquals(1.0, run("math/mod", Map.of("a", 7, "b", 3)).outputs().get("result"));
        assertEquals(3.0, run("math/min", Map.of("a", 3, "b", 4)).outputs().get("result"));
        assertEquals(4.0, run("math/max", Map.of("a", 3, "b", 4)).outputs().get("result"));
        assertEquals(3.5, run("math/abs", Map.of("value", -3.5)).outputs().get("result"));
        assertEquals(4, run("math/round", Map.of("value", 3.6)).outputs().get("result"));
        assertEquals(3, run("convert/to_int", Map.of("value", 3.9)).outputs().get("result"));
    }

    @Test
    void divisionByZeroFailsCleanlyNeverThrows() throws Exception {
        // AC PRD 7.2 : division par zéro → diagnostic, pas d'exception.
        var div = run("math/div", Map.of("a", 5.0, "b", 0.0));
        assertNotNull(div.failReason());
        assertNull(div.outputs().get("result"));
        var mod = run("math/mod", Map.of("a", 5.0, "b", 0.0));
        assertNotNull(mod.failReason());
    }

    @Test
    void comparisonsAndBooleans() throws Exception {
        assertEquals(true, run("logic/less", Map.of("a", 1, "b", 2)).outputs().get("result"));
        assertEquals(false, run("logic/greater", Map.of("a", 1, "b", 2)).outputs().get("result"));
        assertEquals(true, run("logic/less_eq", Map.of("a", 2, "b", 2)).outputs().get("result"));
        assertEquals(true, run("logic/greater_eq", Map.of("a", 2, "b", 2)).outputs().get("result"));
        assertEquals(true, run("logic/equals", Map.of("a", "x", "b", "x")).outputs().get("result"));
        assertEquals(true, run("logic/not_equals", Map.of("a", 1, "b", 2)).outputs().get("result"));
        assertEquals(true, run("logic/and", Map.of("a", true, "b", true)).outputs().get("result"));
        assertEquals(true, run("logic/or", Map.of("a", false, "b", true)).outputs().get("result"));
        assertEquals(true, run("logic/xor", Map.of("a", true, "b", false)).outputs().get("result"));
        assertEquals(false, run("logic/not", Map.of("value", true)).outputs().get("result"));
    }

    @Test
    void stringNodes() throws Exception {
        assertEquals("château fort", run("string/concat",
                Map.of("a", "château ", "b", "fort")).outputs().get("result"));
        assertEquals(7, run("string/length", Map.of("value", "château")).outputs().get("result"));
        assertEquals(true, run("string/contains",
                Map.of("value", "château", "search", "âte")).outputs().get("result"));
        assertEquals("CHÂTEAU", run("string/upper", Map.of("value", "château")).outputs().get("result"));
        assertEquals("château", run("string/lower", Map.of("value", "CHÂTEAU")).outputs().get("result"));
        assertEquals("42", run("convert/to_string", Map.of("value", 42)).outputs().get("result"));
    }

    @Test
    void seededRandomIsDeterministic() throws Exception {
        Object first = run("math/random", Map.of("seed", 123L, "index", 7)).outputs().get("value");
        Object second = run("math/random", Map.of("seed", 123L, "index", 7)).outputs().get("value");
        Object other = run("math/random", Map.of("seed", 123L, "index", 8)).outputs().get("value");
        assertEquals(first, second, "même graine + même index → même valeur");
        assertTrue(!first.equals(other), "un index différent change la valeur");
        // Régression QA RAND-001 : (1,0) et (0,31) entraient en collision avec seed*31+index.
        Object a = run("math/random", Map.of("seed", 1L, "index", 0)).outputs().get("value");
        Object b = run("math/random", Map.of("seed", 0L, "index", 31)).outputs().get("value");
        assertTrue(!a.equals(b), "le mélange doré évite les collisions triviales");
    }

    @Test
    void flowNodesBehave() throws Exception {
        assertEquals("true", run("flow/branch", Map.of("condition", true)).chosenExec());
        assertEquals("false", run("flow/branch", Map.of("condition", false)).chosenExec());
        assertEquals("oui", run("flow/select",
                Map.of("condition", true, "if_true", "oui", "if_false", "non")).outputs().get("value"));
        assertEquals("non", run("flow/select",
                Map.of("condition", false, "if_true", "oui", "if_false", "non")).outputs().get("value"));
        assertEquals(40, run("flow/wait", Map.of("ticks", 40)).suspendTicks());
        assertEquals(1, run("flow/wait", Map.of("ticks", -5)).suspendTicks(), "borné à ≥ 1");
        assertNull(run("flow/return", Map.of()).chosenExec());
    }
}
