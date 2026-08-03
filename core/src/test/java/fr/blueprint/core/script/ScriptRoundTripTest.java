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
import fr.blueprint.core.graph.screen.Anchor;
import fr.blueprint.core.graph.screen.ElementKind;
import fr.blueprint.core.graph.screen.ElementStyle;
import fr.blueprint.core.graph.screen.Extent;
import fr.blueprint.core.graph.screen.LayoutSpec;
import fr.blueprint.core.graph.screen.ScreenElement;
import fr.blueprint.core.graph.screen.ScreenText;
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

    /**
     * <b>Le littéral d'un nœud d'ÉVÉNEMENT survit à l'aller-retour.</b>
     *
     * <p>Trois événements en portent un — {@code command}, {@code signal},
     * {@code gui_clicked} — et c'est leur <b>filtre</b> : le nom de la commande, du
     * signal, de l'élément écouté. L'export l'omettait. Un {@code .bp} réimporté
     * revenait donc entier, se validait, s'affichait normalement dans l'éditeur… et ne
     * se déclenchait plus jamais. La panne la plus silencieuse qu'on puisse écrire.
     */
    @Test
    void leFiltreDUnNoeudDEvenementSurvitAuTexte() {
        for (String[] event : new String[][]{
                {"blueprint:event/signal", "name", "alarme"},
                {"blueprint:event/command", "name", "ouvrir"},
                {"blueprint:event/gui_clicked", "element", "acheter"}}) {
            Blueprint bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "filtre"));
            UUID node = UUID.randomUUID();
            apply(bp, new EditOperation.AddNode(node,
                    Identifier.tryParse(event[0]), new Vec2d(0, 0)));
            apply(bp, new EditOperation.SetLiteral(node, event[1],
                    LiteralValue.of(PinTypes.STRING, event[2])));

            Blueprint back = roundTrip(bp);
            var literal = back.node(node).literal(event[1]);
            assertNotNull(literal, event[0] + " a perdu son filtre");
            assertEquals(event[2], literal.value(), event[0]);
            assertTrue(bp.contentEquals(back), event[0]);
        }
    }

    // ------------------------------------------------------------------- écrans

    private static Blueprint withScreens(fr.blueprint.core.graph.screen.Screen... screens) {
        Blueprint bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "ecrans"));
        for (var screen : screens) {
            fr.blueprint.core.graph.GraphLoader.addScreen(bp, screen);
        }
        return bp;
    }

    /** L'écran le plus chargé qu'on puisse écrire : chaque annotation est exercée. */
    @Test
    void unEcranRicheRevientIdentiqueParLeTexte() {
        var panel = new ScreenElement("cadre", ElementKind.PANEL, null,
                Anchor.CENTER, 10, -20,
                Extent.percent(0.8, 100, 400), Extent.of(180.5),
                ScreenText.key("menu.titre"),
                Identifier.fromNamespaceAndPath("pack", "textures/gui/fond.png"),
                new ElementStyle(0xFF102030, 0xFF405060, 2, 0xFFFFFFFF,
                        0xFF203040, 0xFF001020, 0x40101010, 4,
                        ElementStyle.TextAlign.CENTER), "", LayoutSpec.ABSOLUTE, false, false);
        var child = ScreenElement.of("ok", ElementKind.BUTTON, 5, 5, 60, 20)
                .withParent("cadre")
                .withText(ScreenText.literal("Valider \"maintenant\""));

        Blueprint before = withScreens(
                new fr.blueprint.core.graph.screen.Screen("menu", false, List.of(panel, child)),
                new fr.blueprint.core.graph.screen.Screen("barre", true, List.of(
                        ScreenElement.of("argent", ElementKind.LABEL, 0, 0, 80, 10))));

        Blueprint back = roundTrip(before);
        assertTrue(before.contentEquals(back),
                () -> "aller-retour non identique :\n"
                        + ScriptGenerator.generate(before, LOADED.nodes()).text());
        assertTrue(back.screen("barre").hud(), "le drapeau @hud survit");
    }

    /**
     * Le piège du pourcentage. {@code 0,07 × 100} vaut 7.000000000000001 en virgule
     * flottante : émettre puis relire par une multiplication ferait dériver la fraction
     * à chaque export, et un menu réenregistré dix fois finirait décalé.
     */
    @Test
    void unPourcentageIndelicatSurvitAuTexte() {
        for (double fraction : new double[]{0.07, 1.0 / 3, 0.815, 0.999, 0.001}) {
            Blueprint before = withScreens(new fr.blueprint.core.graph.screen.Screen(
                    "menu", false, List.of(ScreenElement.of("x", ElementKind.LABEL, 0, 0, 10, 10)
                            .resized(Extent.percent(fraction, 0, 0), Extent.of(10)))));
            assertEquals(fraction, roundTrip(before).screen("menu").element("x").width().value(),
                    0.0, "fraction " + fraction);
        }
    }

    /**
     * Dispositions, tailles {@code fill}/{@code hug} et styles nommés (story 10.10).
     *
     * <p>Le contenu est comparé, pas des comptes : c'est ce test-là qui a manqué au
     * projet quand l'export perdait silencieusement le filtre d'un événement. Un écran
     * dont la disposition ne survit pas au {@code .bp} se rouvre en tas d'éléments
     * empilés en haut à gauche — et rien n'aurait dit d'où ça vient.
     */
    @Test
    void dispositionsEtStylesNommesReviennentIdentiques() {
        var style = new ElementStyle(0xFF102030, 0xFF405060, 2, 0xFFFFFFFF,
                0xFF203040, 0xFF001020, 0x40101010, 4, ElementStyle.TextAlign.CENTER);
        var colonne = ScreenElement.of("colonne", ElementKind.PANEL, 0, 0, 200, 160)
                .withLayout(LayoutSpec.column(6).withMain(LayoutSpec.Distribute.SPACE_BETWEEN)
                        .withCross(LayoutSpec.Cross.STRETCH));
        var grille = ScreenElement.of("grille", ElementKind.PANEL, 0, 0, 200, 100)
                .withLayout(LayoutSpec.grid(3, 4, 2))
                .resized(Extent.fill(), Extent.hug())
                .withParent("colonne");
        var bouton = ScreenElement.of("acheter", ElementKind.BUTTON, 0, 0, 60, 20)
                .withParent("colonne")
                .resized(new Extent(Extent.Mode.FILL, 2.5, 20, 90), Extent.of(20))
                .withStyleName("bouton");
        var borne = ScreenElement.of("borne", ElementKind.LABEL, 0, 0, 40, 12)
                .withParent("colonne")
                .resized(new Extent(Extent.Mode.FIXED, 40, 10, 200), Extent.of(12));

        Blueprint before = withScreens(new fr.blueprint.core.graph.screen.Screen(
                "menu", false, List.of(colonne, grille, bouton, borne),
                java.util.Map.of("bouton", style)));

        Blueprint back = roundTrip(before);
        assertTrue(before.contentEquals(back),
                () -> "aller-retour non identique :\n"
                        + ScriptGenerator.generate(before, LOADED.nodes()).text());

        var relu = back.screen("menu");
        assertEquals(style, relu.styleOf(relu.element("acheter")),
                "l'élément suit toujours le style nommé, pas son style en ligne");
        assertEquals(2.5, relu.element("acheter").width().value(), 1e-9, "le poids du fill");
        assertEquals(200, relu.element("borne").width().max(), 1e-9,
                "les bornes d'une taille FIXE survivent aussi");
    }

    /** L'ordre des éléments est l'ordre de dessin : le texte ne le trie jamais. */
    @Test
    void lOrdreDeDessinSurvitAuTexte() {
        Blueprint before = withScreens(new fr.blueprint.core.graph.screen.Screen("menu", false,
                List.of(ScreenElement.of("z", ElementKind.LABEL, 0, 0, 10, 10),
                        ScreenElement.of("a", ElementKind.LABEL, 0, 0, 10, 10),
                        ScreenElement.of("m", ElementKind.LABEL, 0, 0, 10, 10))));
        assertEquals(List.of("z", "a", "m"),
                List.copyOf(roundTrip(before).screen("menu").elements().keySet()));
    }

    /**
     * Un fichier écrit à la main peut nommer deux éléments pareil. Le NBT ne refuse
     * rien (P4), mais ici l'auteur a un canal d'erreur : le lui dire vaut mieux que
     * perdre un élément en silence.
     */
    @Test
    void lesNomsEnDoubleSontRefusesALImportTexte() {
        var dupElement = ScriptParser.parse("""
                blueprint test:x {
                  screen "menu" {
                    label "a" @at(top_left, 0, 0) @size(10, 10)
                    label "a" @at(top_left, 0, 0) @size(10, 10)
                  }
                }""", LOADED);
        assertFalse(dupElement.success());
        assertTrue(dupElement.error().contains("déjà défini"), dupElement.error());

        var dupScreen = ScriptParser.parse("""
                blueprint test:x {
                  screen "menu" { }
                  screen "menu" { }
                }""", LOADED);
        assertFalse(dupScreen.success());
        assertTrue(dupScreen.error().contains("déjà défini"), dupScreen.error());
    }

    @Test
    void unEcranMalEcritEstRefuseAvecSaLigne() {
        for (String body : List.of(
                "screen \"m\" { hologramme \"a\" @size(10, 10) }",
                "screen \"m\" { label \"a\" @at(nord_ouest, 0, 0) }",
                "screen \"m\" { label \"a\" @size(50%[100, 10], 10) }",
                "screen \"m\" { label \"a\" @inconnue }",
                "screen \"m\" @modal { }",
                "screen \"m\" { label \"a\" @texture(\"PAS UN ID\") }")) {
            var result = ScriptParser.parse("blueprint test:x {\n  " + body + "\n}", LOADED);
            assertFalse(result.success(), body);
            assertTrue(result.error().contains("ligne"), body + " → " + result.error());
        }
    }

    /** Un blueprint sans écran n'en gagne pas, et le texte ne mentionne pas « screen ». */
    @Test
    void unBlueprintSansEcranNeGagneRien() {
        Blueprint demo = DemoBlueprint.build(LOADED.nodes());
        assertFalse(ScriptGenerator.generate(demo, LOADED.nodes()).text().contains("screen "));
        assertTrue(roundTrip(demo).screens().isEmpty());
    }

    /** Ce qui est préservé en brut ne s'écrit pas en texte : l'export doit le dire. */
    @Test
    void lesEcransPreservesSontSignalesCommeNonEmis() {
        net.minecraft.nbt.CompoundTag root = fr.blueprint.core.graph.GraphNbt.encode(
                new Blueprint(Identifier.fromNamespaceAndPath("test", "ecrans")));
        root.getListOrEmpty("screens").add(new net.minecraft.nbt.CompoundTag());
        Blueprint reloaded = fr.blueprint.core.graph.GraphNbt.decode(root,
                id -> PinTypes.builtin().stream()
                        .filter(type -> type.id().equals(id)).findFirst().orElse(null));

        var generated = ScriptGenerator.generate(reloaded, LOADED.nodes());
        assertTrue(generated.issues().stream().anyMatch(i -> i.contains("écrans préservés")),
                () -> "points signalés : " + generated.issues());
    }
}
