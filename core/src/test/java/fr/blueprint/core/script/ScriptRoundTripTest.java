package fr.blueprint.core.script;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.DemoBlueprint;
import fr.blueprint.core.event.StandardEvents;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.Variable;
import fr.blueprint.core.graph.VarScope;
import fr.blueprint.core.graph.Vec2d;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** BScript v1 (stories 4.1-4.3) : génération déterministe, parsing, round-trip exact. */
class ScriptRoundTripTest {

    private static final PluginLoader.LoadedRegistries LOADED = PluginLoader.load(List.of(), true);

    private static void apply(Blueprint bp, EditOperation op) {
        var result = op.apply(bp, LOADED.nodes());
        if (!result.applied()) {
            throw new AssertionError("opération refusée : " + result.refusal());
        }
    }

    private static Blueprint roundTrip(Blueprint original) {
        ScriptGenerator.Result generated = ScriptGenerator.generate(original, LOADED.nodes());
        assertTrue(generated.issues().isEmpty(), () -> "points non émis : " + generated.issues());
        ScriptParser.ParseResult parsed = ScriptParser.parse(generated.text(), LOADED);
        assertTrue(parsed.success(), () -> "parse échoué : " + parsed.error() + "\n" + generated.text());
        return parsed.blueprint();
    }

    @Test
    void demoRoundTripsExactly() {
        // Le pivot : 7 nœuds, 2 événements, branche, purs, littéraux, wait.
        Blueprint demo = DemoBlueprint.build(LOADED.nodes());
        Blueprint back = roundTrip(demo);
        assertTrue(demo.contentEquals(back),
                () -> "round-trip non identique :\n" + ScriptGenerator.generate(demo, LOADED.nodes()).text());
    }

    @Test
    void generationIsDeterministic() {
        Blueprint demo = DemoBlueprint.build(LOADED.nodes());
        String first = ScriptGenerator.generate(demo, LOADED.nodes()).text();
        String second = ScriptGenerator.generate(demo, LOADED.nodes()).text();
        assertEquals(first, second, "même graphe → mêmes octets (FR23)");
        // Et le texte régénéré depuis le graphe re-parsé est identique octet pour octet.
        Blueprint back = roundTrip(demo);
        assertEquals(first, ScriptGenerator.generate(back, LOADED.nodes()).text());
    }

    @Test
    void outputIsReadable() {
        String text = ScriptGenerator.generate(DemoBlueprint.build(LOADED.nodes()), LOADED.nodes()).text();
        assertTrue(text.contains("on blueprint:event/player_join(player)"), text);
        assertTrue(text.contains("blueprint:flow/wait(ticks: 20)")
                || text.contains("blueprint:flow/wait(ticks: 40)"), text);
        assertTrue(text.contains("true: {"), "les branches sont des blocs par pin");
        assertTrue(text.contains("$player"), "les sorties d'événement se lisent $nom");
        assertTrue(text.contains("blueprint:string/contains("), "les purs sont inlinés");
    }

    @Test
    void variablesRoundTripAllScopesAndStructuralTypes() {
        var bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "vars"));
        apply(bp, new EditOperation.AddVariable(new Variable("compteur", PinTypes.INT,
                LiteralValue.of(PinTypes.INT, 7), VarScope.GRAPH, false)));
        apply(bp, new EditOperation.AddVariable(new Variable("mondial", PinTypes.DOUBLE,
                LiteralValue.of(PinTypes.DOUBLE, 2.5), VarScope.WORLD, true)));
        apply(bp, new EditOperation.AddVariable(new Variable("perso", PinTypes.STRING,
                LiteralValue.of(PinTypes.STRING, "salut \"toi\"\n"), VarScope.PLAYER, false)));
        apply(bp, new EditOperation.AddVariable(new Variable("scores",
                PinTypes.listOf(PinTypes.INT),
                LiteralValue.of(PinTypes.listOf(PinTypes.INT), List.of()), VarScope.LOCAL, false)));

        Blueprint back = roundTrip(bp);
        assertTrue(bp.contentEquals(back));
        assertEquals(PinTypes.listOf(PinTypes.INT), back.variables().get("scores").type());
    }

    @Test
    void execLoopRoundTripsThroughGoto() {
        var bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "loop"));
        UUID tick = UUID.nameUUIDFromBytes("lt".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        UUID a = UUID.nameUUIDFromBytes("la".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        UUID b = UUID.nameUUIDFromBytes("lb".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        apply(bp, new EditOperation.AddNode(tick, StandardEvents.SERVER_TICK.id(), Vec2d.ZERO));
        apply(bp, new EditOperation.AddNode(a,
                Identifier.fromNamespaceAndPath("blueprint", "debug/log"), new Vec2d(200, 0)));
        apply(bp, new EditOperation.AddNode(b,
                Identifier.fromNamespaceAndPath("blueprint", "debug/log"), new Vec2d(400, 0)));
        apply(bp, new EditOperation.SetLiteral(a, "value", LiteralValue.of(PinTypes.STRING, "a")));
        apply(bp, new EditOperation.SetLiteral(b, "value", LiteralValue.of(PinTypes.STRING, "b")));
        apply(bp, new EditOperation.AddLink(new Link(tick, "exec_out", a, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(a, "exec_out", b, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(b, "exec_out", a, "exec_in")));   // boucle

        String text = ScriptGenerator.generate(bp, LOADED.nodes()).text();
        assertTrue(text.contains("label l_"), "la cible de boucle est étiquetée");
        assertTrue(text.contains("goto l_"), "l'arête arrière est un goto");
        assertTrue(bp.contentEquals(roundTrip(bp)));
    }

    @Test
    void sharedPureDeduplicatesByIdOnParse() {
        var bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "shared"));
        UUID tick = UUID.nameUUIDFromBytes("st".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        UUID concat = UUID.nameUUIDFromBytes("sc".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        UUID l1 = UUID.nameUUIDFromBytes("s1".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        UUID l2 = UUID.nameUUIDFromBytes("s2".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        apply(bp, new EditOperation.AddNode(tick, StandardEvents.SERVER_TICK.id(), Vec2d.ZERO));
        apply(bp, new EditOperation.AddNode(concat,
                Identifier.fromNamespaceAndPath("blueprint", "string/concat"), new Vec2d(0, 100)));
        apply(bp, new EditOperation.AddNode(l1,
                Identifier.fromNamespaceAndPath("blueprint", "debug/log"), new Vec2d(200, 0)));
        apply(bp, new EditOperation.AddNode(l2,
                Identifier.fromNamespaceAndPath("blueprint", "debug/log"), new Vec2d(400, 0)));
        apply(bp, new EditOperation.AddLink(new Link(tick, "exec_out", l1, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(l1, "exec_out", l2, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(concat, "result", l1, "value")));
        apply(bp, new EditOperation.AddLink(new Link(concat, "result", l2, "value")));

        Blueprint back = roundTrip(bp);
        assertTrue(bp.contentEquals(back), "le pur partagé redevient UN SEUL nœud");
        assertEquals(4, back.nodes().size());
    }

    @Test
    void literalBehindLinkSurvivesRoundTrip() {
        // AddLink ne retire pas le littéral du pin : il reste en repli derrière le lien
        // (style UE). Le texte doit porter les deux, sinon le round-trip dégrade.
        var bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "hidden"));
        UUID tick = UUID.nameUUIDFromBytes("ht".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        UUID log = UUID.nameUUIDFromBytes("hl".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        UUID concat = UUID.nameUUIDFromBytes("hc".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        apply(bp, new EditOperation.AddNode(tick, StandardEvents.SERVER_TICK.id(), Vec2d.ZERO));
        apply(bp, new EditOperation.AddNode(log,
                Identifier.fromNamespaceAndPath("blueprint", "debug/log"), new Vec2d(200, 0)));
        apply(bp, new EditOperation.AddNode(concat,
                Identifier.fromNamespaceAndPath("blueprint", "string/concat"), new Vec2d(0, 100)));
        apply(bp, new EditOperation.SetLiteral(log, "value", LiteralValue.of(PinTypes.STRING, "repli")));
        apply(bp, new EditOperation.AddLink(new Link(tick, "exec_out", log, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(concat, "result", log, "value")));

        Blueprint back = roundTrip(bp);
        assertTrue(bp.contentEquals(back), "le littéral de repli derrière le lien doit survivre");
        assertEquals("repli", back.node(log).literal("value").value());
    }

    @Test
    void craftedPureCycleAndDanglingRefNeverCrashExport() {
        // GraphLoader ne refuse rien (P4) : un script forgé peut créer un cycle de purs
        // et un lien depuis un nœud absent. L'export doit rester total — jamais de
        // StackOverflowError ni de NPE — et re-parser à l'identique.
        String script = """
                blueprint test:cycle {
                  meta {
                    author ""
                    description ""
                    version "1.0.0"
                    permission GAMEPLAY
                  }
                  on blueprint:event/server_tick() @id("00000000-0000-0000-0000-000000000001") @pos(0, 0) {
                    blueprint:debug/log(value: blueprint:string/concat(a: $node("00000000-0000-0000-0000-0000000000c2").result) @id("00000000-0000-0000-0000-0000000000c1") @pos(0, 100)) @id("00000000-0000-0000-0000-000000000002") @pos(200, 0)
                    blueprint:debug/log(value: blueprint:string/concat(a: $node("00000000-0000-0000-0000-0000000000c1").result) @id("00000000-0000-0000-0000-0000000000c2") @pos(0, 200)) @id("00000000-0000-0000-0000-000000000003") @pos(400, 0)
                    blueprint:debug/log(value: $node("00000000-0000-0000-0000-0000000000dd").out) @id("00000000-0000-0000-0000-000000000004") @pos(600, 0)
                  }
                }
                """;
        ScriptParser.ParseResult parsed = ScriptParser.parse(script, LOADED);
        assertTrue(parsed.success(), parsed.error());
        ScriptGenerator.Result generated = org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> ScriptGenerator.generate(parsed.blueprint(), LOADED.nodes()));
        ScriptParser.ParseResult again = ScriptParser.parse(generated.text(), LOADED);
        assertTrue(again.success(), () -> again.error() + "\n" + generated.text());
        assertTrue(parsed.blueprint().contentEquals(again.blueprint()),
                () -> "fidélité perdue :\n" + generated.text());
    }

    @Test
    void labelsStayDistinctWhenUuidPrefixesCollide() {
        // labelOf tronquait l'UUID à 8 caractères : deux nœuds étiquetés partageant le
        // préfixe produisaient le même nom — un goto inter-événements pouvait se
        // recâbler en silence sur le mauvais nœud.
        var bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "labels"));
        UUID t1 = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        UUID t2 = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
        UUID a = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
        UUID b = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");
        UUID c = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");
        UUID d = UUID.fromString("cccccccc-0000-0000-0000-000000000001");
        Identifier logType = Identifier.fromNamespaceAndPath("blueprint", "debug/log");
        apply(bp, new EditOperation.AddNode(t1, StandardEvents.SERVER_TICK.id(), Vec2d.ZERO));
        apply(bp, new EditOperation.AddNode(t2, StandardEvents.SERVER_TICK.id(), new Vec2d(0, 300)));
        apply(bp, new EditOperation.AddNode(a, logType, new Vec2d(200, 0)));
        apply(bp, new EditOperation.AddNode(b, logType, new Vec2d(400, 0)));
        apply(bp, new EditOperation.AddNode(c, logType, new Vec2d(200, 300)));
        apply(bp, new EditOperation.AddNode(d, logType, new Vec2d(400, 300)));
        apply(bp, new EditOperation.AddLink(new Link(t1, "exec_out", a, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(a, "exec_out", b, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(b, "exec_out", a, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(t2, "exec_out", c, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(c, "exec_out", d, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(d, "exec_out", c, "exec_in")));

        String text = ScriptGenerator.generate(bp, LOADED.nodes()).text();
        long distinctLabels = text.lines()
                .map(String::strip)
                .filter(l -> l.startsWith("label "))
                .distinct()
                .count();
        assertEquals(2, distinctLabels, () -> "deux boucles = deux étiquettes distinctes :\n" + text);
        assertTrue(bp.contentEquals(roundTrip(bp)));
    }

    @Test
    void unknownNodeParsesAsGhost() {
        String script = """
                blueprint test:ghosty {
                  meta {
                    author ""
                    description ""
                    version "1.0.0"
                    permission GAMEPLAY
                  }
                  on blueprint:event/server_tick() @id("00000000-0000-0000-0000-000000000001") @pos(0, 0) {
                    absentmod:gone/action(power: 3) @id("00000000-0000-0000-0000-000000000002") @pos(200, 0)
                  }
                }
                """;
        ScriptParser.ParseResult result = ScriptParser.parse(script, LOADED);
        assertTrue(result.success(), result.error());
        var ghost = result.blueprint().node(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        assertNotNull(ghost, "nœud inconnu = fantôme, pas un échec (spec §7)");
        assertEquals("absentmod", ghost.typeId().getNamespace());
        assertEquals(3, ghost.literal("power").value());
    }

    @Test
    void syntaxErrorsReportLineAndNeverThrow() {
        ScriptParser.ParseResult missing = ScriptParser.parse("blueprint test:x {\n  var int\n}", LOADED);
        assertFalse(missing.success());
        assertNull(missing.blueprint());
        assertTrue(missing.error().contains("ligne"), missing.error());

        assertFalse(ScriptParser.parse("", LOADED).success());
        assertFalse(ScriptParser.parse("{{{ n'importe quoi", LOADED).success());
        assertFalse(ScriptParser.parse("blueprint test:x {\n  chaîne \"non terminée\n}", LOADED).success());
    }
}
