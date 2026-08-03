package fr.blueprint.core.datapack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.blueprint.api.node.NodeContext;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.api.registry.NodeRegistry;
import fr.blueprint.core.registry.NodeRegistryImpl;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Nœuds composites de datapack (story 8.2). */
class DatapackNodesTest {

    private static final List<String> TRACE = new ArrayList<>();

    /** Deux nœuds « existants » à composer, plus un nœud interdit aux datapacks. */
    private static final PluginLoader.LoadedRegistries LOADED = PluginLoader.load(List.of(
            new PluginLoader.PluginEntry("host", registry -> {
                registry.register(NodeType.builder(id("host", "shout"))
                        .exec()
                        .in("text", PinTypes.STRING, "?")
                        .in("times", PinTypes.INT, 1)
                        .out("said", PinTypes.STRING)
                        .permission(Permission.GAMEPLAY)
                        .action(ctx -> {
                            String said = ctx.<String>in("text").repeat(ctx.<Integer>in("times"));
                            TRACE.add("shout:" + said);
                            ctx.out("said", said);
                        })
                        .build());
                registry.register(NodeType.builder(id("host", "count"))
                        .exec()
                        .in("value", PinTypes.STRING)
                        .out("length", PinTypes.INT)
                        .action(ctx -> {
                            int length = ctx.<String>in("value").length();
                            TRACE.add("count:" + length);
                            ctx.out("length", length);
                        })
                        .build());
                registry.register(NodeType.builder(id("host", "nuke"))
                        .exec()
                        .permission(Permission.ADMIN)
                        .action(ctx -> TRACE.add("nuke"))
                        .build());
                registry.register(NodeType.builder(id("host", "branch"))
                        .execIn("exec_in").execOut("yes").execOut("no")
                        .action(ctx -> ctx.exec("yes"))
                        .build());
            })));

    private static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    private static DatapackNodes.Report load(String... files) {
        Map<Identifier, String> map = new LinkedHashMap<>();
        for (int i = 0; i < files.length; i++) {
            map.put(id("mypack", "blueprint/nodes/f" + i + ".json"), files[i]);
        }
        return DatapackNodes.parseAll(map, LOADED);
    }

    private static final String HEAL_AND_SHOUT = """
            {
              "id": "mypack:shout_twice",
              "category": "text",
              "translation": { "name": "mypack.node.shout_twice" },
              "pins": {
                "in":  [ { "name": "message", "type": "blueprint:string", "default": "ho" } ],
                "out": [ { "name": "size", "type": "blueprint:int" } ]
              },
              "body": { "steps": [
                { "node": "host:shout", "args": { "text": "$message", "times": 2 } },
                { "node": "host:count", "args": { "value": "$0.said" } }
              ] },
              "returns": { "size": "$1.length" }
            }
            """;

    // ------------------------------------------------------------------ nominal

    @Test
    void aCompositeBecomesARealNode() {
        DatapackNodes.Report report = load(HEAL_AND_SHOUT);
        assertTrue(report.rejected().isEmpty(), report.rejected().toString());
        assertEquals(1, report.loadedCount());

        NodeType composite = report.loaded().get(0);
        assertEquals(id("mypack", "shout_twice"), composite.id());
        assertEquals(List.of("exec_in", "message"),
                composite.inputs().stream().map(NodeType.PinSpec::name).toList());
        assertEquals("ho", composite.inputs().get(1).defaultValue().value());
        assertEquals(List.of("exec_out", "size"),
                composite.outputs().stream().map(NodeType.PinSpec::name).toList());
        assertEquals(Permission.GAMEPLAY, composite.permission(),
                "la permission monte à celle de l'étape la plus exigeante");
    }

    @Test
    void theBodyRunsItsStepsInOrderAndWiresTheirOutputs() throws Exception {
        TRACE.clear();
        NodeType composite = load(HEAL_AND_SHOUT).loaded().get(0);
        FakeContext ctx = new FakeContext(Map.of("message", "hey"));
        composite.action().run(ctx);

        assertEquals(List.of("shout:heyhey", "count:6"), TRACE);
        assertEquals(6, ctx.written.get("size"), "la sortie du composite vient de l'étape 1");
    }

    @Test
    void anUnboundInputFallsBackToTheCalledNodesDefault() throws Exception {
        TRACE.clear();
        // « times » n'est pas lié : le défaut du nœud appelé (1) s'applique.
        NodeType composite = load("""
                {
                  "id": "mypack:once",
                  "pins": { "in": [ { "name": "message", "type": "blueprint:string" } ] },
                  "body": { "steps": [ { "node": "host:shout", "args": { "text": "$message" } } ] }
                }
                """).loaded().get(0);
        composite.action().run(new FakeContext(Map.of("message", "salut")));
        assertEquals(List.of("shout:salut"), TRACE);
    }

    // ------------------------------------------------------------------ refus

    @Test
    void aBrokenFileNeverTakesTheOthersDownWithIt() {
        DatapackNodes.Report report = load("{ pas du json", HEAL_AND_SHOUT, """
                { "id": "mypack:vide", "body": { "steps": [] } }
                """);
        assertEquals(1, report.loadedCount(), "le fichier valide charge quand même");
        assertEquals(2, report.rejected().size());
        assertTrue(report.rejected().values().stream()
                .anyMatch(errors -> errors.toString().contains("JSON illisible")));
        assertTrue(report.rejected().values().stream()
                .anyMatch(errors -> errors.toString().contains("vide")));
    }

    /** AC4 : un datapack ne dépasse pas GAMEPLAY, ni en le déclarant ni en composant. */
    @Test
    void thePermissionCeilingHoldsBothWays() {
        DatapackNodes.Report declared = load("""
                {
                  "id": "mypack:trop",
                  "permission": "ADMIN",
                  "body": { "steps": [ { "node": "host:count", "args": { "value": "x" } } ] }
                }
                """);
        assertEquals(0, declared.loadedCount());
        assertTrue(declared.rejected().values().toString().contains("plafond"),
                declared.rejected().toString());

        DatapackNodes.Report composed = load("""
                {
                  "id": "mypack:contournement",
                  "body": { "steps": [ { "node": "host:nuke" } ] }
                }
                """);
        assertEquals(0, composed.loadedCount(),
                "composer un nœud ADMIN ne contourne pas le plafond");
        assertTrue(composed.rejected().values().toString().contains("plafond"),
                composed.rejected().toString());
    }

    @Test
    void everyStructuralMistakeIsNamed() {
        assertRejected("""
                { "id": "mypack:inconnu",
                  "body": { "steps": [ { "node": "host:absent" } ] } }
                """, "inconnu");
        assertRejected("""
                { "id": "mypack:branche",
                  "body": { "steps": [ { "node": "host:branch" } ] } }
                """, "sorties d'exécution");
        assertRejected("""
                { "id": "mypack:mauvais_pin",
                  "body": { "steps": [ { "node": "host:count", "args": { "valeur": "x" } } ] } }
                """, "n'a pas d'entrée");
        assertRejected("""
                { "id": "mypack:avant",
                  "body": { "steps": [ { "node": "host:count", "args": { "value": "$3.said" } } ] } }
                """, "ANTÉRIEURE");
        assertRejected("""
                { "id": "pas un id", "body": { "steps": [ { "node": "host:count" } ] } }
                """, "identifiant invalide");
        assertRejected("""
                { "id": "mypack:type_inconnu",
                  "pins": { "in": [ { "name": "x", "type": "mypack:inexistant" } ] },
                  "body": { "steps": [ { "node": "host:count", "args": { "value": "x" } } ] } }
                """, "type inconnu");
        assertRejected("""
                { "id": "mypack:sortie_orpheline",
                  "pins": { "out": [ { "name": "ok", "type": "blueprint:bool" } ] },
                  "body": { "steps": [ { "node": "host:count", "args": { "value": "x" } } ] } }
                """, "n'est alimentée");
        assertRejected("""
                { "id": "mypack:bscript",
                  "body": { "source": "shout($message)" } }
                """, "v1.1");
    }

    /** Une recursion directe est refusée — l'appel n'existe simplement pas encore. */
    @Test
    void aCompositeCannotCallItself() {
        assertRejected("""
                { "id": "mypack:boucle",
                  "body": { "steps": [ { "node": "mypack:boucle" } ] } }
                """, "inconnu");
    }

    private static void assertRejected(String json, String expected) {
        DatapackNodes.Report report = load(json);
        assertEquals(0, report.loadedCount(), "aurait dû être refusé : " + json);
        assertTrue(report.rejected().values().toString().contains(expected),
                "message attendu autour de « " + expected + " » : " + report.rejected());
    }

    // -------------------------------------------------------------- rechargement

    /** AC2 : {@code /reload} remplace la couche entière, sans toucher aux nœuds des mods. */
    @Test
    void reloadingReplacesTheWholeDatapackLayer() {
        NodeRegistryImpl registry = (NodeRegistryImpl) LOADED.nodes();
        int modNodes = registry.all().size();
        assertTrue(registry.isFrozen(), "les nœuds des mods sont gelés");

        registry.replaceDatapackLayer(load(HEAL_AND_SHOUT).loaded());
        assertEquals(modNodes + 1, registry.all().size());
        assertEquals(NodeRegistryImpl.DATAPACK_PROVIDER,
                registry.providerOf(id("mypack", "shout_twice")).orElseThrow());
        int afterFirst = registry.generation();

        // Rechargement avec un lot vide : le nœud disparaît, les nœuds des mods restent.
        registry.replaceDatapackLayer(List.of());
        assertEquals(modNodes, registry.all().size());
        assertTrue(registry.get(id("mypack", "shout_twice")).isEmpty());
        assertTrue(registry.generation() > afterFirst,
                "la génération change : le hash de registre doit être réannoncé (6.2)");

        // Et un mod ne peut toujours pas s'enregistrer après le gel.
        assertThrows(IllegalStateException.class, () -> registry.register(
                NodeType.builder(id("host", "tardif")).exec().action(ctx -> {
                }).build()));
    }

    @Test
    void aDatapackCannotShadowAModNode() {
        NodeRegistryImpl registry = (NodeRegistryImpl) LOADED.nodes();
        NodeType impostor = NodeType.builder(id("host", "shout")).exec().action(ctx -> {
        }).build();
        registry.replaceDatapackLayer(List.of(impostor));
        assertEquals("host", registry.providerOf(id("host", "shout")).orElseThrow(),
                "le nœud du mod reste celui du mod");
        registry.replaceDatapackLayer(List.of());
    }

    // ------------------------------------------------------------------ contexte

    private static final class FakeContext implements NodeContext {
        private final Map<String, Object> values;
        private final Map<String, Object> written = new HashMap<>();

        FakeContext(Map<String, Object> values) {
            this.values = values;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T in(String pin) {
            if (!values.containsKey(pin)) {
                throw new IllegalArgumentException("pin inconnu : " + pin);
            }
            return (T) values.get(pin);
        }

        @Override
        public void out(String pin, Object value) {
            written.put(pin, value);
        }

        @Override
        public void exec(String pin) {
        }

        @Override
        public void suspend(int ticks) {
        }

        @Override
        public void fail(net.minecraft.network.chat.Component reason) {
        }

        @Override
        public net.minecraft.server.MinecraftServer server() {
            return null;
        }

        @Override
        public net.minecraft.server.level.ServerLevel level() {
            return null;
        }

        @Override
        public fr.blueprint.api.node.BlueprintHandle blueprint() {
            return null;
        }

        @Override
        public fr.blueprint.api.event.TriggerContext trigger() {
            return null;
        }

        @Override
        public org.slf4j.Logger logger() {
            return org.slf4j.LoggerFactory.getLogger("test");
        }
    }

    /** Le registre passé au constructeur reste une simple interface : rien de spécial. */
    @Test
    void buildingOnlyNeedsTheRegistryInterface() {
        NodeRegistry registry = LOADED.nodes();
        CompositeDefinition.Result parsed = CompositeDefinition.parse(
                (JsonObject) JsonParser.parseString(HEAL_AND_SHOUT),
                typeId -> LOADED.pinTypes().get(typeId).orElse(null));
        assertTrue(parsed.ok(), parsed.errors().toString());
        CompositeNode.Built built = CompositeNode.build(parsed.definition(), registry);
        assertNotNull(built.type());
        assertTrue(built.errors().isEmpty());
        assertFalse(built.type().pure(), "un composite s'enchaîne : il a des pins exec");
    }
}
