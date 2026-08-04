package fr.blueprint.gametest;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.BlueprintManager;
import fr.blueprint.core.BlueprintMod;
import fr.blueprint.core.event.StandardEvents;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.Vec2d;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;

import java.util.UUID;

/**
 * Tests joués dans un <b>vrai serveur</b> (story 1.6). Ils couvrent ce qu'aucun test
 * unitaire ne peut prouver : que la chaîne complète — événement du jeu → pont →
 * compilation → VM → ordonnanceur → effet dans le monde — fonctionne une fois branchée
 * sur Minecraft, et que la persistance rend ce qu'elle a pris.
 *
 * <p>Lancement : {@code ./gradlew runGametest} (serveur dédié, sans fenêtre, rapport
 * JUnit dans {@code build/gametest/report.xml}).
 *
 * <p>Chaque test travaille sur son propre blueprint, dans sa propre zone du monde
 * (les positions viennent de {@code helper.absolutePos}), et nettoie derrière lui :
 * les tests tournent en parallèle dans le même monde.
 */
public final class BlueprintGameTests {

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("gametest", path);
    }

    private static UUID uuid(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void apply(Blueprint bp, EditOperation op) {
        var result = op.apply(bp, BlueprintMod.registries().nodes());
        if (!result.applied()) {
            throw new IllegalStateException("opération refusée : " + result.refusal());
        }
    }

    /**
     * Un blueprint qui pose un bloc d'or à une position, déclenché par l'événement
     * donné. {@code do_once} borne l'effet : sans lui, un graphe sur {@code server_tick}
     * repose le bloc à chaque tick jusqu'à la fin du test.
     */
    private static Blueprint blockPlacer(Identifier blueprintId, Identifier eventType,
                                         BlockPos target, String commandName) {
        return blockPlacer(blueprintId, eventType, target, commandName, "name");
    }

    /** Le pin du littéral filtrant varie : « name » pour command/signal, « element » pour un clic. */
    private static Blueprint blockPlacer(Identifier blueprintId, Identifier eventType,
                                         BlockPos target, String commandName,
                                         String filterPin) {
        // Plafond WORLD : poser un bloc dépasse GAMEPLAY, le graphe serait refusé à la
        // compilation (règle de permission, story 9.3) — un test doit dire ce qu'il veut.
        Blueprint bp = new Blueprint(blueprintId, new fr.blueprint.core.graph.BlueprintMeta(
                "", "", "1.0.0", fr.blueprint.api.node.Permission.WORLD));
        UUID event = uuid(blueprintId + ":event");
        UUID once = uuid(blueprintId + ":once");
        UUID place = uuid(blueprintId + ":place");

        apply(bp, new EditOperation.AddNode(event, eventType, new Vec2d(0, 0)));
        apply(bp, new EditOperation.AddNode(once,
                Identifier.fromNamespaceAndPath("blueprint", "flow/do_once"), new Vec2d(200, 0)));
        apply(bp, new EditOperation.AddNode(place,
                Identifier.fromNamespaceAndPath("blueprint", "world/set_block"), new Vec2d(400, 0)));

        if (commandName != null) {
            apply(bp, new EditOperation.SetLiteral(event, filterPin,
                    LiteralValue.of(PinTypes.STRING, commandName)));
        }
        apply(bp, new EditOperation.SetLiteral(place, "pos",
                LiteralValue.of(PinTypes.BLOCKPOS, target)));
        apply(bp, new EditOperation.SetLiteral(place, "state",
                LiteralValue.of(PinTypes.BLOCKSTATE, Blocks.GOLD_BLOCK.defaultBlockState())));
        apply(bp, new EditOperation.AddLink(new Link(event, "exec_out", once, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(once, "exec_out", place, "exec_in")));
        return bp;
    }

    private static void cleanup(GameTestHelper helper, Identifier blueprintId) {
        BlueprintManager.of(helper.getLevel().getServer()).delete(blueprintId);
    }

    // ------------------------------------------------------------------ tests

    /**
     * VERIFY-004 automatisé : un événement RÉEL du jeu traverse tout et change le monde.
     * C'est la démo « ping/pong » sans les yeux — le tick serveur remplace le chat.
     */
    @GameTest(maxTicks = 200)
    public void aWorldEventRunsTheGraph(GameTestHelper helper) {
        Identifier blueprintId = id("tick_places_block");
        BlockPos target = helper.absolutePos(new BlockPos(1, 1, 1));
        var manager = BlueprintManager.of(helper.getLevel().getServer());
        manager.delete(blueprintId);
        manager.adopt(blockPlacer(blueprintId, StandardEvents.SERVER_TICK.id(), target, null));
        manager.setEnabled(blueprintId, true);

        helper.succeedWhen(() -> {
            // Position ABSOLUE : relativePos ne rend pas exactement ce qu'absolutePos a
            // pris (repère tourné du test) — on interroge le monde là où le graphe écrit.
            helper.assertTrue(helper.getLevel().getBlockState(target).is(Blocks.GOLD_BLOCK),
                    Component.literal("bloc attendu en " + target + ", trouvé "
                            + helper.getLevel().getBlockState(target)));
            cleanup(helper, blueprintId);
        });
    }

    /** VERIFY-7.7 automatisé : {@code /bpc <nom>} déclenche le blueprint qui le déclare. */
    @GameTest(maxTicks = 200)
    public void theBpcCommandTriggersItsBlueprint(GameTestHelper helper) {
        Identifier blueprintId = id("command_places_block");
        BlockPos target = helper.absolutePos(new BlockPos(2, 1, 1));
        var server = helper.getLevel().getServer();
        var manager = BlueprintManager.of(server);
        manager.delete(blueprintId);
        manager.adopt(blockPlacer(blueprintId, StandardEvents.COMMAND.id(), target, "gametest"));
        manager.setEnabled(blueprintId, true);

        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack(), "bpc gametest depuis-le-test");

        helper.succeedWhen(() -> {
            // Position ABSOLUE : relativePos ne rend pas exactement ce qu'absolutePos a
            // pris (repère tourné du test) — on interroge le monde là où le graphe écrit.
            helper.assertTrue(helper.getLevel().getBlockState(target).is(Blocks.GOLD_BLOCK),
                    Component.literal("bloc attendu en " + target + ", trouvé "
                            + helper.getLevel().getBlockState(target)));
            cleanup(helper, blueprintId);
        });
    }

    /**
     * VERIFY-005 automatisé : ce que la persistance écrit, elle le rend — blueprint ET
     * exécution suspendue. Le vrai {@code SavedData} du monde est utilisé, pas une
     * imitation ; seul le redémarrage du serveur est remplacé par un aller-retour NBT.
     */
    @GameTest(maxTicks = 100)
    public void persistenceGivesBackWhatItTook(GameTestHelper helper) {
        Identifier blueprintId = id("persisted");
        var server = helper.getLevel().getServer();
        var manager = BlueprintManager.of(server);
        manager.delete(blueprintId);

        Blueprint bp = blockPlacer(blueprintId, StandardEvents.SERVER_TICK.id(),
                helper.absolutePos(new BlockPos(3, 1, 1)), null);
        manager.adopt(bp);
        int revision = bp.revision();

        var storage = server.overworld().getDataStorage()
                .computeIfAbsent(fr.blueprint.core.storage.BlueprintStorage.TYPE);
        storage.bindLive(manager, BlueprintMod.schedulerOf(server));

        // Aller-retour complet par le codec de la sauvegarde du monde.
        net.minecraft.nbt.CompoundTag tag = fr.blueprint.core.storage.BlueprintStorage.TYPE
                .codec().encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, storage)
                .getOrThrow(message -> new IllegalStateException(message))
                .asCompound().orElseThrow();
        var reloaded = fr.blueprint.core.storage.BlueprintStorage.TYPE.codec()
                .parse(net.minecraft.nbt.NbtOps.INSTANCE, tag)
                .getOrThrow(message -> new IllegalStateException(message));

        // Ordonnanceur JETABLE, pas celui du serveur : les tests tournent en parallèle,
        // et l'instantané peut capturer une exécution vivante d'un test voisin. La
        // rendre à l'ordonnanceur réel la ferait tourner deux fois (trouvé par la CI).
        BlueprintManager fresh = new BlueprintManager();
        var throwaway = new fr.blueprint.core.vm.BlueprintScheduler(100,
                new fr.blueprint.core.vm.BlueprintScheduler.Listener() {
                    @Override
                    public void disabled(Identifier blueprintId, int streakTicks) {
                    }

                    @Override
                    public void faulted(Identifier blueprintId, UUID node, String message) {
                    }
                });
        var report = fr.blueprint.core.storage.PersistenceHooks.restore(reloaded, fresh,
                throwaway, BlueprintMod.registries(),
                new fr.blueprint.core.storage.ServerRefResolver(server),
                (blueprint, trigger) -> null);

        helper.assertTrue(report.blueprintsLoaded() >= 1,
                Component.literal("aucun blueprint rechargé"));
        Blueprint back = fresh.get(blueprintId).orElse(null);
        helper.assertTrue(back != null, Component.literal("« " + blueprintId + " » perdu"));
        helper.assertTrue(back != null && back.contentEquals(bp),
                Component.literal("le blueprint rechargé diffère de l'original"));
        helper.assertTrue(back != null && back.revision() == revision,
                Component.literal("révision perdue"));

        cleanup(helper, blueprintId);
        helper.succeed();
    }

    /**
     * VERIFY-10.4 automatisé : ouvrir un écran, cliquer un bouton, le graphe pose un
     * bloc. Le chemin COMPLET — ouverture serveur, table des écrans ouverts, validation
     * du clic, filtrage par le littéral, exécution — et non une imitation.
     */
    @GameTest(maxTicks = 200)
    public void clickingAButtonRunsTheGraph(GameTestHelper helper) {
        Identifier blueprintId = id("gui_places_block");
        BlockPos target = helper.absolutePos(new BlockPos(3, 1, 1));
        var server = helper.getLevel().getServer();
        var manager = BlueprintManager.of(server);
        manager.delete(blueprintId);

        Blueprint bp = blockPlacer(blueprintId,
                StandardEvents.GUI_ELEMENT_CLICKED.id(), target, "acheter", "element");
        // Un écran d'un bouton, posé directement dans le modèle (le concepteur est
        // testé ailleurs ; ici c'est le CHEMIN qui compte).
        fr.blueprint.core.graph.GraphLoader.addScreen(bp,
                new fr.blueprint.core.graph.screen.Screen("menu", false, java.util.List.of(
                        fr.blueprint.core.graph.screen.ScreenElement.of("acheter",
                                fr.blueprint.core.graph.screen.ElementKind.BUTTON,
                                10, 10, 80, 20))));
        manager.adopt(bp);
        manager.setEnabled(blueprintId, true);

        var player = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(
                fr.blueprint.core.net.ServerBlueprintNet.openScreen(player, blueprintId, "menu"),
                Component.literal("l'écran « menu » n'a pas pu être ouvert"));

        var open = fr.blueprint.core.net.ServerBlueprintNet.screens().of(player.getUUID());
        helper.assertTrue(open != null,
                Component.literal("le serveur n'a pas noté l'écran ouvert"));

        // Un clic sur un bouton qui n'existe pas, et un autre au mauvais numéro
        // d'instance : ni l'un ni l'autre ne doit rien déclencher.
        fr.blueprint.core.net.ServerBlueprintNet.receiveClick(player,
                new fr.blueprint.core.net.BlueprintPayloads.ScreenInteraction(
                        blueprintId, "menu", "inexistant", open.instance()));
        fr.blueprint.core.net.ServerBlueprintNet.receiveClick(player,
                new fr.blueprint.core.net.BlueprintPayloads.ScreenInteraction(
                        blueprintId, "menu", "acheter", open.instance() + 999));
        helper.assertTrue(!helper.getLevel().getBlockState(target).is(Blocks.GOLD_BLOCK),
                Component.literal("un clic invalide a déclenché le graphe"));

        fr.blueprint.core.net.ServerBlueprintNet.receiveClick(player,
                new fr.blueprint.core.net.BlueprintPayloads.ScreenInteraction(
                        blueprintId, "menu", "acheter", open.instance()));

        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getLevel().getBlockState(target).is(Blocks.GOLD_BLOCK),
                    Component.literal("bloc attendu en " + target + ", trouvé "
                            + helper.getLevel().getBlockState(target)));
            cleanup(helper, blueprintId);
        });
    }

    /**
     * VERIFY-10.10 automatisé : un écran dont les boutons sont <b>rangés par leur
     * conteneur</b> s'ouvre, se valide et se clique — le bon.
     *
     * <p>Ce que ça prouve et que le modèle seul ne prouve pas : la place d'un enfant
     * rangé n'est écrite nulle part. Elle se recalcule côté serveur pour la validation
     * et côté client pour le dessin et le clic. Si ces deux passes divergeaient, le menu
     * s'afficherait correctement et les clics tomberaient à côté — la panne la plus
     * pénible à diagnostiquer de tout l'épic, puisque tout <i>a l'air</i> juste.
     */
    @GameTest(maxTicks = 200)
    public void aColumnLayoutScreenOpensAndTheRightButtonRuns(GameTestHelper helper) {
        Identifier blueprintId = id("gui_column_layout");
        BlockPos target = helper.absolutePos(new BlockPos(3, 1, 1));
        var server = helper.getLevel().getServer();
        var manager = BlueprintManager.of(server);
        manager.delete(blueprintId);

        Blueprint bp = blockPlacer(blueprintId,
                StandardEvents.GUI_ELEMENT_CLICKED.id(), target, "acheter", "element");

        // Une colonne de trois boutons partageant la hauteur, avec un style nommé : rien
        // ici ne porte de coordonnée, et les trois se ressemblent par construction.
        var style = new fr.blueprint.core.graph.screen.ElementStyle(
                0xFF1E2430, 0xFF3A4453, 1, 0xFFE6E6E6,
                0xFF2A3242, 0xFF141922, 0x40303030, 3,
                fr.blueprint.core.graph.screen.ElementStyle.TextAlign.CENTER, false);
        var colonne = fr.blueprint.core.graph.screen.ScreenElement.of("colonne",
                        fr.blueprint.core.graph.screen.ElementKind.PANEL, 0, 0, 160, 120)
                .withLayout(fr.blueprint.core.graph.screen.LayoutSpec.column(4)
                        .withCross(fr.blueprint.core.graph.screen.LayoutSpec.Cross.STRETCH));
        var elements = new java.util.ArrayList<fr.blueprint.core.graph.screen.ScreenElement>();
        elements.add(colonne);
        for (String name : java.util.List.of("annuler", "acheter", "vendre")) {
            elements.add(fr.blueprint.core.graph.screen.ScreenElement.of(name,
                            fr.blueprint.core.graph.screen.ElementKind.BUTTON, 0, 0, 160, 24)
                    .withParent("colonne")
                    .resized(fr.blueprint.core.graph.screen.Extent.fill(),
                            fr.blueprint.core.graph.screen.Extent.fill())
                    .withStyleName("bouton"));
        }
        fr.blueprint.core.graph.GraphLoader.addScreen(bp,
                new fr.blueprint.core.graph.screen.Screen("menu", false, elements,
                        java.util.Map.of("bouton", style)));

        // L'écran doit être ACCEPTÉ tel quel : une disposition n'est pas un défaut, et
        // c'est la règle serveur — la même que le garde réseau rejoue — qui le dit.
        for (var element : bp.screen("menu").elements().values()) {
            var refusal = fr.blueprint.core.graph.ScreenRules.checkPlacement("menu",
                    bp.screen("menu"), element, fr.blueprint.core.graph.GraphLimits.DEFAULT);
            helper.assertTrue(refusal == null, Component.literal(
                    "élément « " + element.name() + " » refusé : " + refusal));
        }

        manager.adopt(bp);
        manager.setEnabled(blueprintId, true);

        var player = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(
                fr.blueprint.core.net.ServerBlueprintNet.openScreen(player, blueprintId, "menu"),
                Component.literal("l'écran « menu » n'a pas pu être ouvert"));
        var open = fr.blueprint.core.net.ServerBlueprintNet.screens().of(player.getUUID());
        helper.assertTrue(open != null,
                Component.literal("le serveur n'a pas noté l'écran ouvert"));

        // Les trois boutons se ressemblent : celui qui déclenche est « acheter », et
        // cliquer un frère ne doit rien faire.
        fr.blueprint.core.net.ServerBlueprintNet.receiveClick(player,
                new fr.blueprint.core.net.BlueprintPayloads.ScreenInteraction(
                        blueprintId, "menu", "annuler", open.instance()));
        helper.assertTrue(!helper.getLevel().getBlockState(target).is(Blocks.GOLD_BLOCK),
                Component.literal("un clic sur le mauvais frère a déclenché le graphe"));

        fr.blueprint.core.net.ServerBlueprintNet.receiveClick(player,
                new fr.blueprint.core.net.BlueprintPayloads.ScreenInteraction(
                        blueprintId, "menu", "acheter", open.instance()));

        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getLevel().getBlockState(target).is(Blocks.GOLD_BLOCK),
                    Component.literal("bloc attendu en " + target + ", trouvé "
                            + helper.getLevel().getBlockState(target)));
            cleanup(helper, blueprintId);
        });
    }

    /**
     * VERIFY-9 automatisé : un nœud dont le mod a disparu ne fait rien perdre.
     *
     * <p>C'est la promesse la plus lourde du produit — « réinstallez le mod et tout
     * repart » — et celle qu'on ne peut pas vérifier à l'œil : un graphe amputé s'ouvre
     * sans erreur, se réenregistre, et la perte devient définitive sans que personne
     * n'ait rien vu.
     *
     * <p>Le mod n'est pas réellement retiré ici : c'est le registre qui ne connaît pas le
     * type, ce qui est <b>exactement</b> ce qui arrive au chargement quand le mod manque.
     */
    @GameTest(maxTicks = 100)
    public void aGhostNodeSurvivesSaveAndLoadWithoutLosingAnything(GameTestHelper helper) {
        Identifier blueprintId = id("ghost_survives");
        var server = helper.getLevel().getServer();
        var manager = BlueprintManager.of(server);
        manager.delete(blueprintId);

        // Un type qu'AUCUN mod ne fournit : le cas du mod retiré, sans retirer de mod.
        Identifier missing = Identifier.fromNamespaceAndPath("mod_disparu", "faire/quelque_chose");
        Blueprint bp = new Blueprint(blueprintId, new fr.blueprint.core.graph.BlueprintMeta(
                "", "", "1.0.0", fr.blueprint.api.node.Permission.GAMEPLAY));
        UUID event = uuid(blueprintId + ":event");
        UUID ghost = uuid(blueprintId + ":ghost");
        apply(bp, new EditOperation.AddNode(event, StandardEvents.SERVER_TICK.id(),
                new Vec2d(0, 0)));
        // GraphLoader et non EditOperation : celui-ci refuserait un type inconnu, ce qui
        // est son rôle à l'édition. Le CHARGEMENT, lui, ne refuse rien (P4).
        fr.blueprint.core.graph.GraphLoader.addNode(bp,
                new fr.blueprint.core.graph.Node(ghost, missing, new Vec2d(200, 0)));
        fr.blueprint.core.graph.GraphLoader.addLink(bp,
                new Link(event, "exec_out", ghost, "exec_in"));
        manager.adopt(bp);

        // Aller-retour NBT complet : ce que la sauvegarde du monde fait réellement.
        var tag = fr.blueprint.core.graph.GraphNbt.encode(manager.get(blueprintId).orElseThrow());
        Blueprint reloaded = fr.blueprint.core.graph.GraphNbt.decode(tag,
                id -> BlueprintMod.registries().pinTypes().get(id).orElse(null));

        helper.assertTrue(reloaded != null, Component.literal("le blueprint ne s'est pas relu"));
        var survivor = reloaded.node(ghost);
        helper.assertTrue(survivor != null,
                Component.literal("le nœud fantôme a DISPARU au chargement"));
        helper.assertTrue(missing.equals(survivor.typeId()), Component.literal(
                "le type a changé : " + survivor.typeId()));
        helper.assertTrue(reloaded.linksTouching(ghost).size() == 1, Component.literal(
                "le lien vers le fantôme a été perdu : " + reloaded.linksTouching(ghost)));

        // Et le graphe REFUSE de tourner, en nommant ce qui manque : l'exécuter à moitié
        // serait pire que de ne pas l'exécuter.
        var report = fr.blueprint.core.graph.GraphValidator.validate(reloaded,
                BlueprintMod.registries().nodes());
        helper.assertTrue(report.diagnostics().stream().anyMatch(d ->
                        d.code() == fr.blueprint.core.graph.DiagnosticCode.UNKNOWN_NODE_TYPE),
                Component.literal("aucun diagnostic ne nomme le nœud inconnu"));
        helper.assertTrue(!report.executable(),
                Component.literal("un graphe amputé s'est déclaré exécutable"));

        helper.succeedWhen(() -> cleanup(helper, blueprintId));
    }

    /**
     * VERIFY-15 automatisé : une boucle {@code for_each} parcourt une liste jusqu'au
     * bout, et le graphe ne part pas en boucle infinie.
     *
     * <p>Ce que le test regarde n'est pas « ça marche » mais « ça <b>s'arrête</b> » :
     * une boucle qui repart indéfiniment épuiserait le carburant du tick, le blueprint
     * serait désactivé, et le symptôme — un graphe qui s'éteint tout seul — n'indiquerait
     * pas la boucle.
     */
    @GameTest(maxTicks = 200)
    public void aForEachLoopVisitsEveryItemAndStops(GameTestHelper helper) {
        Identifier blueprintId = id("for_each_stops");
        BlockPos target = helper.absolutePos(new BlockPos(5, 1, 1));
        var server = helper.getLevel().getServer();
        var manager = BlueprintManager.of(server);
        manager.delete(blueprintId);

        Blueprint bp = new Blueprint(blueprintId, new fr.blueprint.core.graph.BlueprintMeta(
                "", "", "1.0.0", fr.blueprint.api.node.Permission.WORLD));
        fr.blueprint.core.graph.GraphLoader.addVariable(bp,
                new fr.blueprint.core.graph.Variable("tours", PinTypes.INT,
                        LiteralValue.of(PinTypes.INT, 0),
                        fr.blueprint.core.graph.VarScope.GRAPH, false));

        UUID event = uuid(blueprintId + ":event");
        UUID once = uuid(blueprintId + ":once");
        UUID list = uuid(blueprintId + ":list");
        UUID each = uuid(blueprintId + ":each");
        UUID read = uuid(blueprintId + ":read");
        UUID plus = uuid(blueprintId + ":plus");
        UUID write = uuid(blueprintId + ":write");
        UUID place = uuid(blueprintId + ":place");

        apply(bp, new EditOperation.AddNode(event, StandardEvents.SERVER_TICK.id(), Vec2d.ZERO));
        apply(bp, new EditOperation.AddNode(once, node("flow/do_once"), new Vec2d(200, 0)));
        apply(bp, new EditOperation.AddNode(list, node("list/of"), new Vec2d(200, 200)));
        apply(bp, new EditOperation.AddNode(each, node("flow/for_each"), new Vec2d(400, 0)));
        apply(bp, new EditOperation.AddNode(read, node("var/get"), new Vec2d(600, 300)));
        apply(bp, new EditOperation.AddNode(plus, node("math/add"), new Vec2d(800, 300)));
        apply(bp, new EditOperation.AddNode(write, node("var/set"), new Vec2d(600, 100)));
        apply(bp, new EditOperation.AddNode(place, node("world/set_block"), new Vec2d(800, 0)));

        for (String[] entry : new String[][]{{"a", "un"}, {"b", "deux"}, {"c", "trois"}}) {
            apply(bp, new EditOperation.SetLiteral(list, entry[0],
                    LiteralValue.of(PinTypes.STRING, entry[1])));
        }
        apply(bp, new EditOperation.SetLiteral(read, "var", LiteralValue.of(PinTypes.STRING, "tours")));
        apply(bp, new EditOperation.SetLiteral(write, "var", LiteralValue.of(PinTypes.STRING, "tours")));
        apply(bp, new EditOperation.SetLiteral(plus, "b", LiteralValue.of(PinTypes.DOUBLE, 1.0)));
        apply(bp, new EditOperation.SetLiteral(place, "pos",
                LiteralValue.of(PinTypes.BLOCKPOS, target)));
        apply(bp, new EditOperation.SetLiteral(place, "state",
                LiteralValue.of(PinTypes.BLOCKSTATE, Blocks.GOLD_BLOCK.defaultBlockState())));

        apply(bp, new EditOperation.AddLink(new Link(event, "exec_out", once, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(once, "exec_out", each, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(list, "list", each, "list")));
        // Le CORPS incrémente ; « completed » pose le bloc. C'est ce dernier lien qui
        // prouve que la boucle se termine : sans sortie de boucle, il ne partirait jamais.
        apply(bp, new EditOperation.AddLink(new Link(each, "body", write, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(read, "value", plus, "a")));
        apply(bp, new EditOperation.AddLink(new Link(plus, "result", write, "value")));
        apply(bp, new EditOperation.AddLink(new Link(each, "completed", place, "exec_in")));

        manager.adopt(bp);
        manager.setEnabled(blueprintId, true);

        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getLevel().getBlockState(target).is(Blocks.GOLD_BLOCK),
                    Component.literal("la boucle ne s'est jamais terminée : « completed » "
                            + "n'a pas été atteint"));
            Object tours = BlueprintMod.varsOf(server)
                    .get(fr.blueprint.core.graph.VarScope.GRAPH, "tours");
            helper.assertTrue(tours instanceof Number number && number.intValue() == 3,
                    Component.literal("trois entrées attendaient trois tours, obtenu " + tours));
            helper.assertTrue(manager.get(blueprintId).map(Blueprint::enabled).orElse(false),
                    Component.literal("le blueprint s'est désactivé — carburant épuisé ?"));
            cleanup(helper, blueprintId);
        });
    }

    /**
     * VERIFY-19 automatisé : le nœud déclaré par <b>annotation</b> dans le mod de test
     * est bien dans le registre du serveur, avec ses défauts.
     *
     * <p>Le chemin de l'annotation est celui qu'un mod tiers empruntera en premier. Il
     * traverse le chargeur de plugins, la réflexion et la construction du type : quatre
     * occasions de perdre un défaut en silence, et un défaut perdu ne se voit qu'au
     * moment où un auteur pose le nœud et le trouve vide.
     */
    @GameTest(maxTicks = 20)
    public void theAnnotatedNodeOfTheTestModIsRegisteredWithItsDefaults(GameTestHelper helper) {
        var registry = BlueprintMod.registries().nodes();
        Identifier shout = Identifier.fromNamespaceAndPath("blueprint_testmod", "shout");

        var type = registry.get(shout);
        helper.assertTrue(type != null, Component.literal(
                "le nœud annoté « " + shout + " » n'est pas dans le registre"));

        // Le nœud composite du DATAPACK du même mod : l'autre chemin d'extension, et
        // celui qui se recharge à chaud.
        Identifier twice = Identifier.fromNamespaceAndPath("blueprint_testmod", "shout_twice");
        helper.assertTrue(registry.get(twice) != null, Component.literal(
                "le nœud composite « " + twice + " » n'est pas chargé"));

        helper.succeed();
    }

    /**
     * VERIFY-10.8 automatisé : une liste alimentée par le graphe, un clic sur la
     * troisième ligne, et le graphe reçoit l'<b>indice 2</b>.
     *
     * <p>Puis les trois refus que seul un vrai serveur exerce : un indice qui n'existe
     * pas, une saisie trop longue pour son champ, et une saisie qui viole son filtre. Ces
     * trois-là sont ce qu'un client modifié tente en premier, et ils ne peuvent pas se
     * vérifier ailleurs — le client, lui, ne les enverrait jamais.
     */
    @GameTest(maxTicks = 200)
    public void aListReportsTheClickedIndexAndRefusesTheRest(GameTestHelper helper) {
        Identifier blueprintId = id("gui_list");
        var server = helper.getLevel().getServer();
        var manager = BlueprintManager.of(server);
        manager.delete(blueprintId);

        Blueprint bp = new Blueprint(blueprintId, new fr.blueprint.core.graph.BlueprintMeta(
                "", "", "1.0.0", fr.blueprint.api.node.Permission.GAMEPLAY));
        fr.blueprint.core.graph.GraphLoader.addScreen(bp,
                new fr.blueprint.core.graph.screen.Screen("boutique", false, java.util.List.of(
                        fr.blueprint.core.graph.screen.ScreenElement.of("articles",
                                        fr.blueprint.core.graph.screen.ElementKind.LIST,
                                        10, 10, 120, 60)
                                .withOptions(fr.blueprint.core.graph.screen.ElementOptions
                                        .list(12)),
                        fr.blueprint.core.graph.screen.ScreenElement.of("pseudo",
                                        fr.blueprint.core.graph.screen.ElementKind.INPUT,
                                        10, 80, 120, 16)
                                .withOptions(fr.blueprint.core.graph.screen.ElementOptions
                                        .input("Nom", 8,
                                                fr.blueprint.core.graph.screen.ElementOptions
                                                        .InputFilter.IDENTIFIER)))));
        // Deux nœuds d'événement qui ÉCOUTENT : sans eux, une interaction acceptée
        // réveillerait zéro écouteur — indistinguable d'un refus.
        UUID onLine = uuid(blueprintId + ":line");
        apply(bp, new EditOperation.AddNode(onLine,
                StandardEvents.GUI_LIST_CLICKED.id(), new Vec2d(0, 0)));
        apply(bp, new EditOperation.SetLiteral(onLine, "element",
                LiteralValue.of(PinTypes.STRING, "articles")));
        UUID onInput = uuid(blueprintId + ":input");
        apply(bp, new EditOperation.AddNode(onInput,
                StandardEvents.GUI_INPUT_CHANGED.id(), new Vec2d(0, 200)));
        apply(bp, new EditOperation.SetLiteral(onInput, "element",
                LiteralValue.of(PinTypes.STRING, "pseudo")));

        manager.adopt(bp);
        manager.setEnabled(blueprintId, true);

        var player = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(fr.blueprint.core.net.ServerBlueprintNet.openScreen(
                        player, blueprintId, "boutique"),
                Component.literal("l'écran « boutique » n'a pas pu être ouvert"));
        var open = fr.blueprint.core.net.ServerBlueprintNet.screens().of(player.getUUID());

        // Cliquer AVANT que la liste ait reçu ses lignes ne doit rien déclencher : le
        // serveur ne connaît alors aucune entrée à rendre au graphe.
        int before = clickLine(player, blueprintId, open.instance(), 0);
        helper.assertTrue(before == 0, Component.literal(
                "une liste vide a répondu à un clic (" + before + ")"));

        // Le graphe remplit la liste ; les lignes ne sont retenues qu'une fois PARTIES.
        fr.blueprint.core.net.ServerBlueprintNet.queueUpdate(player,
                fr.blueprint.core.graph.screen.ScreenUpdate.lines("boutique", "articles",
                        java.util.List.of("Pomme", "Epee", "Potion")));
        fr.blueprint.core.net.ServerBlueprintNet.screens().drain(player.getUUID());

        helper.assertTrue(clickLine(player, blueprintId, open.instance(), 2) > 0,
                Component.literal("le clic sur la troisième ligne a été rejeté"));

        // Un indice hors de ce que le serveur a envoyé : refusé. Un client peut annoncer
        // la ligne 900 d'une liste qui en compte trois.
        helper.assertTrue(clickLine(player, blueprintId, open.instance(), 900) == 0,
                Component.literal("un indice inexistant a été accepté"));
        helper.assertTrue(clickLine(player, blueprintId, open.instance(), -1) == 0,
                Component.literal("un indice négatif a été accepté"));

        // Saisie : trop longue, puis interdite par le filtre. Ni l'une ni l'autre ne
        // doit passer — et surtout, aucune ne doit être TRONQUÉE pour passer.
        helper.assertTrue(typeInto(player, blueprintId, open.instance(),
                "x".repeat(64)) == 0, Component.literal("une saisie trop longue a été acceptée"));
        helper.assertTrue(typeInto(player, blueprintId, open.instance(),
                "un nom") == 0, Component.literal("une saisie hors filtre a été acceptée"));
        helper.assertTrue(typeInto(player, blueprintId, open.instance(),
                "kerlann") > 0, Component.literal("une saisie valide a été rejetée"));

        helper.succeedWhen(() -> cleanup(helper, blueprintId));
    }

    /**
     * Rend le nombre d'écouteurs réveillés. Zéro signifie « refusé » : le blueprint
     * déclare bien un nœud qui écoute cette liste, donc une interaction acceptée en
     * réveille toujours au moins un.
     */
    private static int clickLine(net.minecraft.server.level.ServerPlayer player,
                                 Identifier blueprintId, int instance, int index) {
        return fr.blueprint.core.net.ServerBlueprintNet.receiveValue(player,
                new fr.blueprint.core.net.BlueprintPayloads.ScreenValue(
                        blueprintId, "boutique", "articles", instance, index, "", 0, false));
    }

    private static int typeInto(net.minecraft.server.level.ServerPlayer player,
                                Identifier blueprintId, int instance, String text) {
        return fr.blueprint.core.net.ServerBlueprintNet.receiveValue(player,
                new fr.blueprint.core.net.BlueprintPayloads.ScreenValue(
                        blueprintId, "boutique", "pseudo", instance, 0, text, 0, false));
    }

    /**
     * VERIFY-10.7 automatisé : une étiquette liée à une variable suit sa valeur quand le
     * graphe demande un rafraîchissement — et <b>rien ne circule</b> tant qu'il ne le
     * demande pas.
     *
     * <p>La seconde moitié est celle qui compte. « Coût nul au repos » est une promesse
     * qu'on ne peut pas observer en jouant : un écran immobile qui enverrait vingt paquets
     * par seconde aurait exactement la même apparence qu'un écran qui n'envoie rien.
     * Seule la table des envois en attente le dit, et elle n'est lisible que d'ici.
     */
    @GameTest(maxTicks = 200)
    public void aBoundLabelFollowsItsVariableOnlyWhenAsked(GameTestHelper helper) {
        Identifier blueprintId = id("gui_binding");
        var server = helper.getLevel().getServer();
        var manager = BlueprintManager.of(server);
        manager.delete(blueprintId);

        Blueprint bp = new Blueprint(blueprintId, new fr.blueprint.core.graph.BlueprintMeta(
                "", "", "1.0.0", fr.blueprint.api.node.Permission.GAMEPLAY));
        fr.blueprint.core.graph.GraphLoader.addVariable(bp,
                new fr.blueprint.core.graph.Variable("argent", PinTypes.INT,
                        LiteralValue.of(PinTypes.INT, 50),
                        fr.blueprint.core.graph.VarScope.GRAPH, false));
        fr.blueprint.core.graph.GraphLoader.addScreen(bp,
                new fr.blueprint.core.graph.screen.Screen("bourse", false, java.util.List.of(
                        fr.blueprint.core.graph.screen.ScreenElement.of("or",
                                        fr.blueprint.core.graph.screen.ElementKind.LABEL,
                                        10, 10, 120, 12)
                                .withBinding(fr.blueprint.core.graph.screen.ElementBinding
                                        .text("argent", "Or : %s")))));
        manager.adopt(bp);
        manager.setEnabled(blueprintId, true);

        var player = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(fr.blueprint.core.net.ServerBlueprintNet.openScreen(
                        player, blueprintId, "bourse"),
                Component.literal("l'écran « bourse » n'a pas pu être ouvert"));

        // Premier rafraîchissement : la valeur part, formatée.
        int first = fr.blueprint.core.net.ServerBlueprintNet.refreshBindings(
                player, blueprintId, "bourse");
        helper.assertTrue(first == 1,
                Component.literal("attendu 1 modification, obtenu " + first));
        var sent = fr.blueprint.core.net.ServerBlueprintNet.screens().drain(player.getUUID());
        helper.assertTrue(sent.size() == 1 && sent.getFirst().text().equals("Or : 50"),
                Component.literal("texte inattendu : " + sent));

        // <b>Rien n'a changé</b> : rien ne part, si souvent qu'on le demande.
        for (int i = 0; i < 20; i++) {
            int again = fr.blueprint.core.net.ServerBlueprintNet.refreshBindings(
                    player, blueprintId, "bourse");
            helper.assertTrue(again == 0, Component.literal(
                    "un écran immobile a produit " + again + " modification(s) au tour " + i));
        }
        helper.assertTrue(fr.blueprint.core.net.ServerBlueprintNet.screens()
                        .drain(player.getUUID()).isEmpty(),
                Component.literal("des modifications attendaient alors que rien n'a bougé"));

        // La variable bouge : une seule modification, et la bonne.
        fr.blueprint.core.BlueprintMod.varsOf(server)
                .set(fr.blueprint.core.graph.VarScope.GRAPH, "argent", 51);
        int third = fr.blueprint.core.net.ServerBlueprintNet.refreshBindings(
                player, blueprintId, "bourse");
        helper.assertTrue(third == 1,
                Component.literal("attendu 1 modification après changement, obtenu " + third));
        var after = fr.blueprint.core.net.ServerBlueprintNet.screens().drain(player.getUUID());
        helper.assertTrue(after.size() == 1 && after.getFirst().text().equals("Or : 51"),
                Component.literal("texte inattendu après changement : " + after));

        helper.succeedWhen(() -> cleanup(helper, blueprintId));
    }

    /**
     * La valeur par défaut d'une variable est LUE au premier accès, dans un vrai
     * serveur. Elle ne l'était pas : le graphe tombait en faute au lieu de tourner, et
     * le message accusait le câblage alors que le câblage était bon.
     */
    @GameTest(maxTicks = 200)
    public void aVariableDefaultIsReadableBeforeAnyWrite(GameTestHelper helper) {
        Identifier blueprintId = id("var_default");
        BlockPos target = helper.absolutePos(new BlockPos(4, 1, 1));
        var server = helper.getLevel().getServer();
        var manager = BlueprintManager.of(server);
        manager.delete(blueprintId);

        Blueprint bp = new Blueprint(blueprintId, new fr.blueprint.core.graph.BlueprintMeta(
                "", "", "1.0.0", fr.blueprint.api.node.Permission.WORLD));
        fr.blueprint.core.graph.GraphLoader.addVariable(bp,
                new fr.blueprint.core.graph.Variable("seuil", PinTypes.DOUBLE,
                        LiteralValue.of(PinTypes.DOUBLE, 7.0),
                        fr.blueprint.core.graph.VarScope.GRAPH, false));

        UUID event = uuid(blueprintId + ":event");
        UUID once = uuid(blueprintId + ":once");
        UUID get = uuid(blueprintId + ":get");
        UUID test = uuid(blueprintId + ":test");
        UUID branch = uuid(blueprintId + ":branch");
        UUID place = uuid(blueprintId + ":place");
        apply(bp, new EditOperation.AddNode(event, StandardEvents.SERVER_TICK.id(),
                new Vec2d(0, 0)));
        apply(bp, new EditOperation.AddNode(once, node("flow/do_once"), new Vec2d(200, 0)));
        apply(bp, new EditOperation.AddNode(get, node("var/get"), new Vec2d(200, 200)));
        apply(bp, new EditOperation.SetLiteral(get, "var",
                LiteralValue.of(PinTypes.STRING, "seuil")));
        apply(bp, new EditOperation.AddNode(test, node("logic/greater_eq"), new Vec2d(400, 200)));
        apply(bp, new EditOperation.SetLiteral(test, "b",
                LiteralValue.of(PinTypes.DOUBLE, 7.0)));
        apply(bp, new EditOperation.AddNode(branch, node("flow/branch"), new Vec2d(400, 0)));
        apply(bp, new EditOperation.AddNode(place, node("world/set_block"), new Vec2d(600, 0)));
        apply(bp, new EditOperation.SetLiteral(place, "pos",
                LiteralValue.of(PinTypes.BLOCKPOS, target)));
        apply(bp, new EditOperation.SetLiteral(place, "state",
                LiteralValue.of(PinTypes.BLOCKSTATE, Blocks.GOLD_BLOCK.defaultBlockState())));
        apply(bp, new EditOperation.AddLink(new Link(event, "exec_out", once, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(once, "exec_out", branch, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(get, "value", test, "a")));
        apply(bp, new EditOperation.AddLink(new Link(test, "result", branch, "condition")));
        apply(bp, new EditOperation.AddLink(new Link(branch, "true", place, "exec_in")));

        manager.adopt(bp);
        manager.setEnabled(blueprintId, true);

        helper.succeedWhen(() -> {
            // Le bloc n'apparaît que si « seuil » valait 7 — donc si le défaut a été lu.
            helper.assertTrue(helper.getLevel().getBlockState(target).is(Blocks.GOLD_BLOCK),
                    Component.literal("le défaut de la variable n'a pas été lu"));
            cleanup(helper, blueprintId);
        });
    }

    private static Identifier node(String path) {
        return Identifier.fromNamespaceAndPath("blueprint", path);
    }

    /**
     * VERIFY-10.9 automatisé : un HUD s'AFFICHE sans ouvrir d'écran, et une
     * modification le trouve. Il s'ouvrait auparavant comme un menu modal, ce qui
     * figeait le joueur sur place dès qu'un graphe voulait lui montrer son or.
     */
    @GameTest(maxTicks = 200)
    public void aHudShowsWithoutOpeningAScreen(GameTestHelper helper) {
        Identifier blueprintId = id("hud_shows");
        var server = helper.getLevel().getServer();
        var manager = BlueprintManager.of(server);
        manager.delete(blueprintId);

        Blueprint bp = new Blueprint(blueprintId);
        fr.blueprint.core.graph.GraphLoader.addScreen(bp,
                new fr.blueprint.core.graph.screen.Screen("mana", true, java.util.List.of(
                        fr.blueprint.core.graph.screen.ScreenElement.of("valeur",
                                fr.blueprint.core.graph.screen.ElementKind.LABEL,
                                4, 4, 80, 10))));
        // Un écran MODAL du même blueprint : il ne doit pas pouvoir s'afficher en HUD.
        fr.blueprint.core.graph.GraphLoader.addScreen(bp,
                new fr.blueprint.core.graph.screen.Screen("menu", false, java.util.List.of(
                        fr.blueprint.core.graph.screen.ScreenElement.of("ok",
                                fr.blueprint.core.graph.screen.ElementKind.BUTTON,
                                4, 4, 60, 20))));
        manager.adopt(bp);
        manager.setEnabled(blueprintId, true);

        var player = helper.makeMockServerPlayerInLevel();
        var net = fr.blueprint.core.net.ServerBlueprintNet.screens();

        helper.assertTrue(fr.blueprint.core.net.ServerBlueprintNet
                        .showHud(player, blueprintId, "mana"),
                Component.literal("le HUD « mana » n'a pas pu s'afficher"));
        helper.assertTrue(net.of(player.getUUID()) == null,
                Component.literal("un HUD ne doit PAS compter comme écran modal ouvert"));
        helper.assertTrue(net.hudsOf(player.getUUID()).contains("mana"),
                Component.literal("le serveur n'a pas noté le HUD"));

        helper.assertTrue(!fr.blueprint.core.net.ServerBlueprintNet
                        .showHud(player, blueprintId, "menu"),
                Component.literal("un écran modal ne doit pas s'afficher en HUD"));

        // Une modification visant le HUD est acceptée, sans aucun menu ouvert.
        fr.blueprint.core.net.ServerBlueprintNet.queueUpdate(player,
                fr.blueprint.core.graph.screen.ScreenUpdate.text("mana", "valeur",
                        fr.blueprint.core.graph.screen.ScreenText.literal("40")));
        helper.assertTrue(net.pendingPlayers().contains(player.getUUID()),
                Component.literal("la modification n'a pas trouvé le HUD"));

        // Désactiver le blueprint retire son HUD : sans Échap, il serait indélogeable.
        manager.setEnabled(blueprintId, false);
        helper.assertTrue(net.hudsOf(player.getUUID()).isEmpty(),
                Component.literal("le HUD d'un blueprint désactivé est resté affiché"));

        cleanup(helper, blueprintId);
        helper.succeed();
    }

    /**
     * NFR15 en conditions réelles : un nœud {@code ADMIN} exécuté par un graphe laisse
     * une trace nommant le blueprint et l'acteur.
     */
    @GameTest(maxTicks = 200)
    public void anAdminNodeLeavesAnAuditTrail(GameTestHelper helper) {
        Identifier blueprintId = id("admin_audited");
        var manager = BlueprintManager.of(helper.getLevel().getServer());
        manager.delete(blueprintId);
        fr.blueprint.core.debug.AdminAudit.clear();

        Blueprint bp = new Blueprint(blueprintId);
        UUID event = uuid("audit:event");
        UUID once = uuid("audit:once");
        UUID boom = uuid("audit:boom");
        apply(bp, new EditOperation.AddNode(event, StandardEvents.SERVER_TICK.id(), Vec2d.ZERO));
        apply(bp, new EditOperation.AddNode(once,
                Identifier.fromNamespaceAndPath("blueprint", "flow/do_once"), new Vec2d(200, 0)));
        apply(bp, new EditOperation.AddNode(boom,
                Identifier.fromNamespaceAndPath("blueprint", "world/explosion"), new Vec2d(400, 0)));
        // Explosion de puissance nulle : on veut la trace d'audit, pas un cratère.
        apply(bp, new EditOperation.SetLiteral(boom, "pos", LiteralValue.of(PinTypes.VEC3,
                net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(new BlockPos(4, 1, 1))))));
        apply(bp, new EditOperation.SetLiteral(boom, "power",
                LiteralValue.of(PinTypes.DOUBLE, 0.0)));
        apply(bp, new EditOperation.SetMeta(new fr.blueprint.core.graph.BlueprintMeta(
                "", "", "1.0.0", fr.blueprint.api.node.Permission.ADMIN)));
        apply(bp, new EditOperation.AddLink(new Link(event, "exec_out", once, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(once, "exec_out", boom, "exec_in")));

        manager.adopt(bp);
        manager.setEnabled(blueprintId, true);

        helper.succeedWhen(() -> {
            boolean audited = fr.blueprint.core.debug.AdminAudit.recent().stream()
                    .anyMatch(entry -> entry.blueprint().equals(blueprintId));
            helper.assertTrue(audited, Component.literal("aucune entrée d'audit pour "
                    + blueprintId));
            cleanup(helper, blueprintId);
        });
    }

    /**
     * VERIFY signal : un blueprint en appelle un autre. L'émetteur tourne au tick et
     * envoie « poser » ; le récepteur écoute ce nom et pose le bloc. Rien ne relie les
     * deux graphes sinon la chaîne de caractères — c'est tout l'intérêt.
     *
     * <p>Ce test aurait été impossible à écrire avant : l'événement signal existait
     * depuis la 7.6 et rien ne le déclenchait.
     */
    @GameTest(maxTicks = 200)
    public void aSignalCarriesFromOneBlueprintToAnother(GameTestHelper helper) {
        Identifier emitterId = id("signal_emitter");
        Identifier receiverId = id("signal_receiver");
        BlockPos target = helper.absolutePos(new BlockPos(5, 1, 1));
        var manager = BlueprintManager.of(helper.getLevel().getServer());
        manager.delete(emitterId);
        manager.delete(receiverId);

        // Le récepteur : signal « poser » → pose un bloc d'or.
        Blueprint receiver = new Blueprint(receiverId, new fr.blueprint.core.graph.BlueprintMeta(
                "", "", "1.0.0", fr.blueprint.api.node.Permission.WORLD));
        UUID listen = uuid("sig:listen");
        UUID place = uuid("sig:place");
        apply(receiver, new EditOperation.AddNode(listen,
                StandardEvents.SIGNAL.id(), Vec2d.ZERO));
        apply(receiver, new EditOperation.SetLiteral(listen, "name",
                LiteralValue.of(PinTypes.STRING, "poser")));
        apply(receiver, new EditOperation.AddNode(place,
                Identifier.fromNamespaceAndPath("blueprint", "world/set_block"),
                new Vec2d(200, 0)));
        apply(receiver, new EditOperation.SetLiteral(place, "pos",
                LiteralValue.of(PinTypes.BLOCKPOS, target)));
        apply(receiver, new EditOperation.SetLiteral(place, "state",
                LiteralValue.of(PinTypes.BLOCKSTATE, Blocks.GOLD_BLOCK.defaultBlockState())));
        apply(receiver, new EditOperation.AddLink(new Link(listen, "exec_out", place, "exec_in")));

        // L'émetteur : un tick, une seule fois, → signal « poser ».
        Blueprint emitter = new Blueprint(emitterId);
        UUID tick = uuid("sig:tick");
        UUID once = uuid("sig:once");
        UUID emit = uuid("sig:emit");
        apply(emitter, new EditOperation.AddNode(tick,
                StandardEvents.SERVER_TICK.id(), Vec2d.ZERO));
        apply(emitter, new EditOperation.AddNode(once,
                Identifier.fromNamespaceAndPath("blueprint", "flow/do_once"), new Vec2d(200, 0)));
        apply(emitter, new EditOperation.AddNode(emit,
                Identifier.fromNamespaceAndPath("blueprint", "signal/emit"), new Vec2d(400, 0)));
        apply(emitter, new EditOperation.SetLiteral(emit, "name",
                LiteralValue.of(PinTypes.STRING, "poser")));
        apply(emitter, new EditOperation.AddLink(new Link(tick, "exec_out", once, "exec_in")));
        apply(emitter, new EditOperation.AddLink(new Link(once, "exec_out", emit, "exec_in")));

        manager.adopt(receiver);
        manager.setEnabled(receiverId, true);
        manager.adopt(emitter);
        manager.setEnabled(emitterId, true);

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    helper.getLevel().getBlockState(target).is(Blocks.GOLD_BLOCK),
                    Component.literal("le signal n'a pas traversé jusqu'au second blueprint"));
            helper.getLevel().removeBlock(target, false);
            cleanup(helper, emitterId);
            cleanup(helper, receiverId);
        });
    }

    /**
     * VERIFY requêtes : les nœuds qui LISENT le monde. Ils exigent un serveur vivant
     * (heure, dimension, joueurs connectés) et n'ont donc aucun équivalent headless —
     * un mauvais nom de méthode Mojang ne se verrait qu'ici.
     */
    @GameTest(maxTicks = 100)
    public void worldQueryNodesReadTheLiveServer(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var registries = BlueprintMod.registries();

        // world/get_time : le jour courant doit être cohérent avec l'heure du monde.
        var time = runNode(helper, "world/get_time", java.util.Map.of());
        long dayTime = (Long) time.get("day_time");
        helper.assertTrue(dayTime >= 0 && dayTime < 24_000L,
                Component.literal("heure du jour hors bornes : " + dayTime));

        // world/dimension : l'identifiant réel du monde de test.
        var dimension = runNode(helper, "world/dimension", java.util.Map.of());
        helper.assertTrue(dimension.get("dimension") != null,
                Component.literal("dimension nulle"));

        // query/players : la liste des connectés, cohérente avec le serveur.
        var players = runNode(helper, "query/players", java.util.Map.of());
        int expected = server.getPlayerList().getPlayers().size();
        helper.assertTrue(players.get("players") instanceof java.util.List<?> list
                        && list.size() == expected,
                Component.literal("liste de joueurs incohérente (attendu " + expected + ")"));

        // entity/as_player sur ce qui n'est pas un joueur : le drapeau doit dire faux
        // plutôt que de rendre un joueur nul qui se propagerait en silence.
        var cow = helper.spawn(net.minecraft.world.entity.EntityType.COW, new BlockPos(6, 1, 1));
        var asPlayer = runNode(helper, "entity/as_player", java.util.Map.of("entity", cow));
        helper.assertTrue(Boolean.FALSE.equals(asPlayer.get("is_player")),
                Component.literal("une vache n'est pas un joueur"));
        cow.discard();

        helper.succeed();
    }

    /**
     * VERIFY paquets client : les nœuds de retour ciblé construisent et envoient de
     * vrais paquets clientbound. Sans joueur connecté dans le harnais, on ne peut pas
     * observer l'écran — on vérifie ce qui compte et qui casse en silence : qu'un
     * joueur ABSENT ne fait rien plutôt que lever, et que les constructeurs de paquets
     * Mojang acceptent bien nos arguments (un renommage de classe se verrait ici).
     */
    @GameTest(maxTicks = 60)
    public void clientFeedbackNodesBehaveWithoutAPlayer(GameTestHelper helper) {
        // Un pin « player » NON CÂBLÉ est une faute nommée (CTX-001), pas un silence :
        // c'est une décision du cœur, et elle vaut aussi pour ces nœuds. La VM
        // transforme l'exception en Faulted ; ici on vérifie qu'elle est bien nommée.
        for (String path : java.util.List.of("player/subtitle", "player/action_bar",
                "player/title_times", "player/play_sound", "player/particles")) {
            String message = expectThrow(helper, path, java.util.Map.of());
            helper.assertTrue(message != null && message.contains("player"),
                    Component.literal(path + " : la faute doit NOMMER le pin manquant, "
                            + "or elle dit « " + message + " »"));
        }

        // Un identifiant de son inconnu doit fauter proprement — pas lever, pas se
        // taire : c'est une faute d'auteur, et il doit l'apprendre.
        var player = helper.makeMockServerPlayerInLevel();
        var badSound = runNodeContext(helper, "player/play_sound", java.util.Map.of(
                "player", player,
                "sound", Identifier.fromNamespaceAndPath("blueprint", "pas_un_son")));
        helper.assertTrue(badSound.failReason() != null,
                Component.literal("un identifiant de son inconnu doit fauter"));

        // Un son VALIDE : le paquet se construit et part. C'est ici qu'un renommage de
        // classe Mojang ou un mauvais ordre d'arguments se verrait.
        var goodSound = runNodeContext(helper, "player/play_sound", java.util.Map.of(
                "player", player,
                "sound", Identifier.withDefaultNamespace("block.note_block.pling")));
        helper.assertTrue(goodSound.failReason() == null,
                Component.literal("un son valide ne doit pas fauter : "
                        + goodSound.failReason()));

        // Et les trois paquets de texte, avec un vrai destinataire.
        for (String path : java.util.List.of("player/subtitle", "player/action_bar")) {
            var out = runNodeContext(helper, path,
                    java.util.Map.of("player", player, "text", "essai"));
            helper.assertTrue(out.failReason() == null,
                    Component.literal(path + " a fauté : " + out.failReason()));
        }
        var times = runNodeContext(helper, "player/title_times",
                java.util.Map.of("player", player));
        helper.assertTrue(times.failReason() == null,
                Component.literal("player/title_times a fauté : " + times.failReason()));

        helper.succeed();
    }

    /** Le message de l'exception attendue, ou null si le nœud n'a pas levé. */
    private static String expectThrow(GameTestHelper helper, String path,
                                      java.util.Map<String, Object> inputs) {
        try {
            runNodeContext(helper, path, inputs);
            return null;
        } catch (RuntimeException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return cause.getMessage();
        }
    }

    /** Comme {@link #runNode} mais rend le contexte entier (sorties ET faute). */
    private static fr.blueprint.core.vm.NodeContextImpl runNodeContext(
            GameTestHelper helper, String path, java.util.Map<String, Object> inputs) {
        var type = BlueprintMod.registries().nodes()
                .get(Identifier.fromNamespaceAndPath("blueprint", path)).orElseThrow();
        try {
            return fr.blueprint.core.vm.NodeContextImpl.invoke(type,
                    new fr.blueprint.core.vm.NodeContextImpl(type, inputs,
                            helper.getLevel().getServer(), helper.getLevel(),
                            gametestHandle(), gametestTrigger(),
                            org.slf4j.LoggerFactory.getLogger("blueprint-gametest")));
        } catch (Exception e) {
            throw new IllegalStateException("échec du nœud " + path, e);
        }
    }

    private static fr.blueprint.api.node.BlueprintHandle gametestHandle() {
        return new fr.blueprint.api.node.BlueprintHandle() {
            @Override
            public Identifier id() {
                return Identifier.fromNamespaceAndPath("blueprint_gametest", "query");
            }

            @Override
            public boolean enabled() {
                return true;
            }
        };
    }

    private static fr.blueprint.api.event.TriggerContext gametestTrigger() {
        return new fr.blueprint.api.event.TriggerContext() {
            @Override
            public Identifier eventId() {
                return Identifier.fromNamespaceAndPath("blueprint_gametest", "manual");
            }

            @Override
            public Object output(String name) {
                return null;
            }
        };
    }

    /** Exécute un nœud dans le monde du test et rend ses sorties. */
    private static java.util.Map<String, Object> runNode(GameTestHelper helper, String path,
                                                         java.util.Map<String, Object> inputs) {
        var type = BlueprintMod.registries().nodes()
                .get(Identifier.fromNamespaceAndPath("blueprint", path)).orElseThrow();
        var handle = new fr.blueprint.api.node.BlueprintHandle() {
            @Override
            public Identifier id() {
                return Identifier.fromNamespaceAndPath("blueprint_gametest", "query");
            }

            @Override
            public boolean enabled() {
                return true;
            }
        };
        var trigger = new fr.blueprint.api.event.TriggerContext() {
            @Override
            public Identifier eventId() {
                return Identifier.fromNamespaceAndPath("blueprint_gametest", "manual");
            }

            @Override
            public Object output(String name) {
                return null;
            }
        };
        try {
            return fr.blueprint.core.vm.NodeContextImpl.invoke(type,
                    new fr.blueprint.core.vm.NodeContextImpl(type, inputs,
                            helper.getLevel().getServer(), helper.getLevel(), handle, trigger,
                            org.slf4j.LoggerFactory.getLogger("blueprint-gametest")))
                    .outputs();
        } catch (Exception e) {
            throw new IllegalStateException("échec du nœud " + path, e);
        }
    }
}
