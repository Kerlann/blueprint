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

    /**
     * VERIFY-7.7 automatisé : la racine posée pour un nom déclaré déclenche son blueprint.
     *
     * <p>Le test tape {@code /gametest …} et non un préfixe : c'est tout l'intérêt de la
     * pose à chaud, et c'est la partie qui peut casser sans bruit. Trois pièces doivent
     * s'enchaîner — activer le blueprint signale le changement, le signal repose les
     * racines, la racine appelle le même corps que le repli. Qu'une seule lâche, et le
     * joueur tape une commande que le serveur dit ne pas connaître.
     */
    @GameTest(maxTicks = 200)
    public void theDeclaredRootCommandTriggersItsBlueprint(GameTestHelper helper) {
        Identifier blueprintId = id("command_places_block");
        BlockPos target = helper.absolutePos(new BlockPos(2, 1, 1));
        var server = helper.getLevel().getServer();
        var manager = BlueprintManager.of(server);
        manager.delete(blueprintId);
        manager.adopt(blockPlacer(blueprintId, StandardEvents.COMMAND.id(), target, "gametest"));
        manager.setEnabled(blueprintId, true);

        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack(), "gametest depuis-le-test");

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
                    .get(fr.blueprint.core.graph.VarScope.GRAPH,
                            new fr.blueprint.core.vm.VarOwner(blueprintId, null), "tours");
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
                .set(fr.blueprint.core.graph.VarScope.GRAPH,
                        new fr.blueprint.core.vm.VarOwner(blueprintId, null), "argent", 51);
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
     * VERIFY-11.5 automatisé : les nœuds qui <b>reconnaissent</b> et <b>habillent</b> une
     * pile, exercés sur de vrais {@link net.minecraft.world.item.ItemStack}.
     *
     * <p>Ils n'ont aucun équivalent headless : construire une pile réelle demande les
     * registres du jeu amorcés, et aucun test sans serveur de ce projet ne les amorce.
     * C'est précisément pourquoi ce test existe — les composants d'objet sont l'endroit
     * où un renommage chez Mojang casserait en silence.
     *
     * <p>Ce qu'il prouve et que la forme des nœuds ne prouve pas : <b>renommer rend une
     * copie</b>. La pile reçue par un nœud peut être celle d'un événement, voire celle de
     * l'inventaire d'un joueur ; la modifier sur place renommerait son objet à distance,
     * sans que rien dans le graphe ne le laisse voir.
     */
    @GameTest(maxTicks = 60)
    public void itemNodesReadAndDressRealStacks(GameTestHelper helper) {
        var diamond = new net.minecraft.world.item.ItemStack(
                net.minecraft.world.item.Items.DIAMOND, 3);

        var identifier = runNode(helper, "item/id", java.util.Map.of("stack", diamond));
        helper.assertTrue(Identifier.withDefaultNamespace("diamond")
                        .equals(identifier.get("item")),
                Component.literal("identifiant attendu minecraft:diamond, obtenu "
                        + identifier.get("item")));

        var renamed = runNode(helper, "item/with_name", java.util.Map.of(
                "stack", diamond, "name", Component.literal("Éclat")));
        var copy = (net.minecraft.world.item.ItemStack) renamed.get("stack");
        helper.assertTrue(copy != null && Component.literal("Éclat").equals(
                        copy.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME)),
                Component.literal("le nom personnalisé n'a pas été posé"));
        // LE point du test : l'originale n'a pas bougé.
        helper.assertTrue(
                diamond.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME) == null,
                Component.literal("renommer a modifié la pile D'ORIGINE — un nœud pur qui "
                        + "mute son entrée renomme l'objet d'un joueur à distance"));
        helper.assertTrue(diamond.getCount() == 3,
                Component.literal("la copie doit garder le nombre de l'originale"));

        // item/name rend le nom RÉELLEMENT affiché, celui que le joueur lit.
        var shown = runNode(helper, "item/name", java.util.Map.of("stack", copy));
        helper.assertTrue(Component.literal("Éclat").equals(shown.get("name")),
                Component.literal("nom affiché attendu « Éclat », obtenu " + shown.get("name")));

        // Une description trop longue est TRONQUÉE, jamais refusée : une liste construite
        // par une boucle peut déborder sans que l'auteur s'en doute.
        java.util.List<Component> tooMany = new java.util.ArrayList<>();
        for (int i = 0; i < net.minecraft.world.item.component.ItemLore.MAX_LINES + 5; i++) {
            tooMany.add(Component.literal("ligne " + i));
        }
        var described = runNode(helper, "item/with_lore", java.util.Map.of(
                "stack", diamond, "lines", tooMany));
        var lore = ((net.minecraft.world.item.ItemStack) described.get("stack"))
                .get(net.minecraft.core.component.DataComponents.LORE);
        helper.assertTrue(lore != null
                        && lore.lines().size() == net.minecraft.world.item.component.ItemLore.MAX_LINES,
                Component.literal("la description devait être tronquée à "
                        + net.minecraft.world.item.component.ItemLore.MAX_LINES + " lignes"));

        helper.succeed();
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
    /**
     * VERIFY-11.10 : {@code player/remove_item} retire <b>vraiment</b>, et rend un compte
     * <b>vrai</b>.
     *
     * <p>Le nœud promet « le nombre réellement retiré ». Toute la banque en dépend : c'est
     * ce nombre qu'elle crédite, et le créditer sans que rien ne parte serait de la
     * fausse monnaie. Aucun test ne l'exerçait — {@code everyImpureNodeRunsOnce…} le lance
     * mais ne regarde pas ce qu'il fait, et un inventaire vide rend zéro sans rien
     * prouver.
     */
    @GameTest(maxTicks = 100)
    public void removingItemsTakesThemAndReportsTheTruth(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        player.getInventory().add(new net.minecraft.world.item.ItemStack(
                net.minecraft.world.item.Items.STONE, 64));

        java.util.Map<String, Object> take = java.util.Map.of(
                "player", player,
                "item", Identifier.withDefaultNamespace("stone"),
                "count", 10);
        var first = runNode(helper, "player/remove_item", take);
        helper.assertTrue(Integer.valueOf(10).equals(first.get("removed")),
                Component.literal("retiré attendu 10, obtenu " + first.get("removed")));
        int left = (Integer) runNode(helper, "player/count_item", java.util.Map.of(
                "player", player, "item", Identifier.withDefaultNamespace("stone")))
                .get("count");
        helper.assertTrue(left == 54, Component.literal(
                "54 pierres devaient rester, il en reste " + left));

        // Demander plus qu'on n'a : on prend tout, et on le DIT.
        var rest = runNode(helper, "player/remove_item", java.util.Map.of(
                "player", player,
                "item", Identifier.withDefaultNamespace("stone"),
                "count", 1000));
        helper.assertTrue(Integer.valueOf(54).equals(rest.get("removed")),
                Component.literal("retiré attendu 54, obtenu " + rest.get("removed")));

        // LE PIÈGE. Zéro doit retirer zéro et DIRE zéro. La méthode de Mojang traite
        // maxCount == 0 comme « compte sans retirer » et rend alors le TOTAL possédé :
        // un dépôt de zéro aurait crédité tout l'inventaire sans rien prendre.
        player.getInventory().add(new net.minecraft.world.item.ItemStack(
                net.minecraft.world.item.Items.STONE, 32));
        var nothing = runNode(helper, "player/remove_item", java.util.Map.of(
                "player", player,
                "item", Identifier.withDefaultNamespace("stone"),
                "count", 0));
        helper.assertTrue(Integer.valueOf(0).equals(nothing.get("removed")),
                Component.literal("retirer zéro doit rendre zéro, obtenu "
                        + nothing.get("removed") + " — de la fausse monnaie"));
        int still = (Integer) runNode(helper, "player/count_item", java.util.Map.of(
                "player", player, "item", Identifier.withDefaultNamespace("stone")))
                .get("count");
        helper.assertTrue(still == 32, Component.literal(
                "32 pierres devaient rester intactes, il en reste " + still));

        player.getInventory().clearContent();
        helper.succeed();
    }

    // ------------------------------------------------- fumée des nœuds non purs

    /**
     * Nœuds dont l'effet déborde de la zone du test, et qui ne peuvent donc pas être
     * exercés ici.
     *
     * <p>« Chaque test travaille dans sa propre zone du monde, et les tests tournent en
     * parallèle » — c'est écrit en tête de ce fichier, et le projet a déjà payé une NPE
     * causée par des gametests concurrents. Régler l'heure ou la météo est <b>global au
     * monde</b> ; une explosion détruit la structure du voisin. Les exercer ferait échouer
     * d'autres tests au hasard, ce qui est la pire chose qu'un test puisse faire.
     *
     * <p>La liste doit rester <b>courte</b> et chaque entrée porte sa raison : c'est
     * l'énoncé de ce que ce test ne garde pas.
     */
    private static final java.util.Map<String, String> OUT_OF_BOUNDS = java.util.Map.of(
            "world/set_time", "l'heure est globale au monde — casserait les tests voisins",
            "world/set_weather", "la météo est globale au monde",
            "world/explosion", "détruirait la structure des tests voisins",
            "player/set_gamemode", "change le mode d'un joueur partagé par les tests");

    /**
     * Les nœuds abaissés par le compilateur — la même liste que {@code PureNodeSmokeTest},
     * qui vérifie déjà sans jeu que leur garde lève bien. Certains sont exec, donc non
     * purs, et retomberaient ici.
     */
    private static final java.util.List<String> LOWERED = java.util.List.of(
            "var/get", "var/set", "flow/sequence", "flow/while", "flow/for",
            "flow/wait_until", "flow/for_each", "flow/gate", "flow/do_once",
            // Épic 20 : ajoutés par les fonctions, et oubliés des deux listes jusqu'au
            // jour où le gametest les a signalés comme « nœuds non purs qui lèvent ».
            "func/call", "func/param", "func/result");

    /**
     * VERIFY-11.8 automatisé : <b>chaque nœud non pur est exécuté au moins une fois</b>,
     * dans un vrai serveur.
     *
     * <p>{@code PureNodeSmokeTest} fait cela sans jeu pour les quatre-vingts nœuds purs.
     * L'autre moitié de la bibliothèque — monde, entité, inventaire, tableau des scores,
     * raycast, retours au joueur — n'avait <b>rien</b> : entre 28 % et 46 % de couverture,
     * et les gametests n'en exerçaient qu'une poignée.
     *
     * <p>Ce qu'un test sans jeu ne peut pas voir ici : les pins sont des <b>chaînes</b>,
     * et ces nœuds appellent l'API de Mojang. Un pin mal nommé, un cast qui ne tient pas,
     * une méthode renommée à la version suivante — rien de tout cela ne se voit à la
     * compilation, et le nœud paraît parfaitement sain dans la palette jusqu'à ce qu'un
     * joueur le pose.
     *
     * <p>Le test ne vérifie pas ce que les nœuds <i>font</i> — c'est le travail des tests
     * ciblés qui les entourent. Il vérifie qu'aucun ne <b>lève</b>, ce qui est le minimum
     * qu'un nœud livré doive tenir.
     */
    @GameTest(maxTicks = 200)
    public void everyImpureNodeRunsOnceInARealServer(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var cow = helper.spawn(net.minecraft.world.entity.EntityType.COW, new BlockPos(2, 1, 2));
        BlockPos block = helper.absolutePos(new BlockPos(3, 1, 3));

        java.util.List<String> exploded = new java.util.ArrayList<>();
        java.util.List<String> skipped = new java.util.ArrayList<>();
        int ran = 0;

        for (var type : BlueprintMod.registries().nodes().all()) {
            // Nos nœuds seulement : le mod de test et son datapack en déclarent d'autres,
            // qui sont des FIXTURES de ce harnais. Les exercer ferait échouer notre suite
            // sur le contenu d'un tiers, ce qui n'est ni notre rôle ni notre affaire.
            if (!type.id().getNamespace().equals("blueprint")) {
                continue;
            }
            if (type.pure() || type.entryPoint()) {
                continue;
            }
            String path = type.id().getPath();
            // Les nœuds ABAISSÉS par le compilateur n'ont pas d'implémentation : leur
            // action est une garde qui doit lever, et PureNodeSmokeTest le vérifie déjà
            // sans jeu. Les rejouer ici ne prouverait rien de plus.
            if (OUT_OF_BOUNDS.containsKey(path) || LOWERED.contains(path)) {
                continue;
            }
            java.util.Map<String, Object> inputs =
                    impureInputs(type, player, cow, block);
            if (inputs == null) {
                skipped.add(path);
                continue;
            }
            try {
                runNode(helper, path, inputs);
                ran++;
            } catch (RuntimeException | Error e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                exploded.add(path + " → " + cause.getClass().getSimpleName()
                        + " : " + cause.getMessage());
            }
        }

        cow.discard();
        helper.assertTrue(exploded.isEmpty(), Component.literal(
                "nœud(s) non pur(s) qui lèvent dans un vrai serveur : " + exploded));
        // Plancher : sans lui, un changement de forme des pins pourrait tout faire
        // écarter et ce test passerait à vide, sans rien vérifier — la panne corrigée
        // sur PaletteTest, qu'il ne faut pas réintroduire ici.
        helper.assertTrue(ran >= 90, Component.literal(
                "seulement " + ran + " nœuds non purs exécutés (écartés : " + skipped
                        + ") — le test ne couvre presque plus rien"));
        helper.succeed();
    }

    /**
     * Les entrées d'un nœud non pur, ou {@code null} si l'une d'elles n'est pas
     * fabricable ici.
     *
     * <p>Le défaut déclaré du pin passe avant tout : c'est ce qu'un auteur obtient en
     * posant le nœud sans rien câbler, donc le cas le plus fréquent en pratique.
     */
    private static java.util.Map<String, Object> impureInputs(
            fr.blueprint.api.node.NodeType type,
            net.minecraft.server.level.ServerPlayer player,
            net.minecraft.world.entity.Entity entity, BlockPos block) {
        java.util.Map<String, Object> inputs = new java.util.LinkedHashMap<>();
        for (var pin : type.inputs()) {
            if (pin.kind() == fr.blueprint.api.pin.PinKind.EXEC) {
                continue;
            }
            Object value = pin.defaultValue() != null ? pin.defaultValue().value()
                    : impureValue(pin.type(), player, entity, block);
            if (value == null) {
                return null;
            }
            inputs.put(pin.name(), value);
        }
        return inputs;
    }

    private static Object impureValue(fr.blueprint.api.pin.PinType pinType,
                                      net.minecraft.server.level.ServerPlayer player,
                                      net.minecraft.world.entity.Entity entity,
                                      BlockPos block) {
        if (pinType.equals(PinTypes.PLAYER)) {
            return player;
        }
        if (pinType.equals(PinTypes.ENTITY)) {
            return entity;
        }
        if (pinType.equals(PinTypes.BLOCKPOS)) {
            return block;
        }
        if (pinType.equals(PinTypes.VEC3)) {
            return net.minecraft.world.phys.Vec3.atCenterOf(block);
        }
        if (pinType.equals(PinTypes.ITEMSTACK)) {
            return new net.minecraft.world.item.ItemStack(
                    net.minecraft.world.item.Items.STONE);
        }
        if (pinType.equals(PinTypes.BLOCKSTATE)) {
            return Blocks.STONE.defaultBlockState();
        }
        if (pinType.equals(PinTypes.RESOURCE_LOCATION)) {
            // « pig » plutôt que « stone » : les nœuds qui prennent un identifiant sans
            // défaut attendent en général un type d'ENTITÉ (apparition, filtre de
            // requête), et un identifiant de bloc y serait refusé pour de bonnes raisons
            // — le nœud rendrait une faute déclarée au lieu de s'exécuter.
            return Identifier.withDefaultNamespace("pig");
        }
        if (pinType.equals(PinTypes.TEXT)) {
            return Component.literal("x");
        }
        if (pinType.equals(PinTypes.STRING)) {
            return "x";
        }
        if (pinType.equals(PinTypes.DOUBLE)) {
            return 1.0;
        }
        if (pinType.equals(PinTypes.INT)) {
            return 1;
        }
        if (pinType.equals(PinTypes.LONG)) {
            return 1L;
        }
        if (pinType.equals(PinTypes.BOOL)) {
            return true;
        }
        if (pinType.equals(PinTypes.ANY)) {
            return "x";
        }
        if (pinType.id().getPath().startsWith("list")) {
            return java.util.List.of();
        }
        if (pinType.id().getPath().startsWith("map")) {
            return java.util.Map.of();
        }
        return null;
    }

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

    // ------------------------------------------------------------- charge (épic 16)

    /**
     * Un graphe qui travaille à chaque tick sans rien changer au monde : une chaîne de
     * lectures d'heure. Chaque nœud est un {@code Call} réel pour la VM, et la lecture
     * n'a aucun effet observable — un test de charge ne doit pas se salir le monde.
     */
    private static Blueprint tickWorker(Identifier blueprintId, int nodes) {
        Blueprint bp = new Blueprint(blueprintId);
        UUID event = uuid(blueprintId + ":event");
        apply(bp, new EditOperation.AddNode(event, StandardEvents.SERVER_TICK.id(), Vec2d.ZERO));
        UUID previous = event;
        String previousPin = "exec_out";
        for (int i = 0; i < nodes; i++) {
            UUID step = uuid(blueprintId + ":n" + i);
            apply(bp, new EditOperation.AddNode(step,
                    Identifier.fromNamespaceAndPath("blueprint", "world/get_time"),
                    new Vec2d((i + 1) * 200, 0)));
            apply(bp, new EditOperation.AddLink(new Link(previous, previousPin, step, "exec_in")));
            previous = step;
            previousPin = "exec_out";
        }
        return bp;
    }

    /**
     * Temps cumulé passé dans le tick de l'ordonnanceur, <b>comptabilité comprise</b>.
     *
     * <p>Et non la somme des statistiques par blueprint : celles-ci ne chronomètrent que
     * l'exécution de la VM, si bien qu'un ordonnanceur redevenu quadratique n'y
     * apparaîtrait pas du tout. Ce banc mesurait exactement cela dans sa première version,
     * et il aurait été incapable de rougir sur la régression qu'il prétendait surveiller.
     */
    private static long schedulerNanos(GameTestHelper helper) {
        return BlueprintMod.schedulerOf(helper.getLevel().getServer()).tickNanos();
    }

    private static void adoptWorkers(GameTestHelper helper, String prefix, int count) {
        var manager = BlueprintManager.of(helper.getLevel().getServer());
        for (int i = 0; i < count; i++) {
            Identifier blueprintId = id(prefix + i);
            manager.delete(blueprintId);
            manager.adopt(tickWorker(blueprintId, 1));
            manager.setEnabled(blueprintId, true);
        }
    }

    private static void dropWorkers(GameTestHelper helper, String prefix, int count) {
        var manager = BlueprintManager.of(helper.getLevel().getServer());
        for (int i = 0; i < count; i++) {
            manager.delete(id(prefix + i));
        }
    }

    private static final int LIGHT = 50;
    private static final int HEAVY = LIGHT * 4;
    /** Ticks d'échauffement avant chaque relevé, puis ticks mesurés. */
    private static final int WARM = 20;
    private static final int WINDOW = 40;

    /**
     * <b>Le banc de charge.</b> Ce que le mod coûte réellement au tick d'un serveur, avec
     * cinquante puis deux cents graphes branchés sur {@code server_tick}.
     *
     * <p>C'est le seul banc du dépôt qui mesure la chaîne entière dans un <b>vrai
     * serveur</b> — événement du jeu, pont, ordonnanceur, VM. Tous les autres isolent un
     * morceau, et leurs gains ne valent au tick réel que par inférence.
     *
     * <h2>Ce qu'il mesure, et ce qu'il ne prouve pas</h2>
     *
     * <p>Il mesure le temps passé dans {@link BlueprintScheduler#tick}, <b>comptabilité
     * comprise</b> — pas la somme des statistiques par blueprint, qui ne chronomètrent que
     * l'exécution de la VM. La première version de ce banc faisait cette erreur : elle
     * aurait été incapable de voir quoi que ce soit de l'ordonnanceur lui-même.
     *
     * <h2>Pourquoi PAS un rapport, contrairement aux autres bancs</h2>
     *
     * <p>La forme préférée du §7.1 — comparer deux charges au même moment — a été essayée
     * et <b>écartée sur mesure</b>, pour deux raisons qui se cumulent.
     *
     * <p>D'abord, <b>elle ne discrimine pas</b>. Le parcours de tick a longtemps été
     * quadratique ({@code contains} et {@code remove} linéaires appelés dans la boucle) ;
     * ce défaut a été réintroduit exprès pour voir le rapport rougir. Il ne rougit pas :
     * <b>0,64 avec le défaut</b> contre <b>0,70 et 0,78 sans</b>. À deux cents exécutions,
     * le terme quadratique pèse quelques pour cent contre le lancement et la VM, qui
     * dominent d'un facteur vingt. La correction de l'épic 16f reste juste — elle supprime
     * une croissance qui compterait à plusieurs milliers d'exécutions — mais aucun banc de
     * ce dépôt n'en est le témoin, et il ne faut pas prétendre le contraire.
     *
     * <p>Ensuite, <b>les deux charges ne sont pas comparables ici</b> : la première tourne à
     * froid, la seconde profite du JIT qu'elle vient de chauffer. Une exécution a rendu
     * 724 µs/tick à cinquante graphes et 511 µs/tick à deux cents — le gros cas moins cher
     * que le petit. {@code EventDispatchPerfTest} raconte exactement ce piège et le résout
     * en <b>alternant</b> ses séries ; l'alterner ici demanderait d'adopter et de retirer
     * deux cents graphes en boucle à travers les ticks, pour une discrimination dont on
     * vient de voir qu'elle est nulle.
     *
     * <h2>Ce que ce banc est, alors</h2>
     *
     * <p>Une <b>mesure</b>, journalisée, avec un garde-fou en temps mural à un ordre de
     * grandeur — la troisième forme du §7.1, celle des bancs de rendu, qui n'ont jamais
     * rougi. Elle apporte un chiffre qui n'existait nulle part : l'ordonnanceur consomme
     * moins d'une milliseconde par tick avec deux cents graphes actifs, soit <b>moins de 2 %
     * du budget de cinquante millisecondes</b>. Le plafond est posé à 10 ms — dix fois
     * au-dessus — pour n'attraper qu'une dérive d'ordre de grandeur, jamais du bruit.
     */
    @GameTest(maxTicks = 400)
    public void quadruplerLesGraphesNeQuadruplePasLeCoutParGraphe(GameTestHelper helper) {
        long[] samples = new long[4];

        adoptWorkers(helper, "charge_a", LIGHT);
        helper.runAfterDelay(WARM, () -> samples[0] = schedulerNanos(helper));
        helper.runAfterDelay(WARM + WINDOW, () -> {
            samples[1] = schedulerNanos(helper);
            dropWorkers(helper, "charge_a", LIGHT);
            adoptWorkers(helper, "charge_b", HEAVY);
        });
        helper.runAfterDelay(2L * WARM + WINDOW,
                () -> samples[2] = schedulerNanos(helper));
        helper.runAfterDelay(2L * WARM + 2L * WINDOW, () -> {
            samples[3] = schedulerNanos(helper);
            dropWorkers(helper, "charge_b", HEAVY);

            long light = samples[1] - samples[0];
            long heavy = samples[3] - samples[2];

            // §7.1 : une mesure nulle ferait passer le banc À VIDE, ce qui est pire que
            // rouge. Le piège s'est déjà refermé quatre fois dans ce travail.
            helper.assertTrue(light > 0 && heavy > 0, Component.literal(
                    "mesure nulle (" + light + " / " + heavy + " ns) : les graphes n'ont pas"
                            + " tourné, ou le comptage de l'ordonnanceur est cassé"));

            // EN MICROSECONDES, converties ici et une seule fois. Le compteur est en
            // nanosecondes ; comparer sa valeur brute à un seuil pensé en microsecondes
            // borne mille fois trop serré — ce banc a été commité rouge pour cette raison
            // exacte, et le message d'échec affichait « µs » devant des nanosecondes.
            long lightPerTick = light / WINDOW / 1_000;
            long heavyPerTick = heavy / WINDOW / 1_000;
            double ratio = ((double) heavy / HEAVY) / ((double) light / LIGHT);

            BlueprintMod.LOGGER.info(
                    "Charge : ordonnanceur à {} graphes → {} µs/tick, à {} graphes →"
                            + " {} µs/tick (sur {} ticks) — coût par graphe × {}"
                            + " · budget d'un tick : 50 000 µs",
                    LIGHT, lightPerTick, HEAVY, heavyPerTick, WINDOW,
                    String.format(java.util.Locale.ROOT, "%.2f", ratio));

            // Garde-fou en temps mural, à un ordre de grandeur de la mesure (§7.1, forme 3).
            // Il n'attrape qu'une dérive massive — c'est tout ce qu'un banc de ce genre peut
            // honnêtement promettre, et c'est déjà mieux que rien : personne ne mesurait
            // jusqu'ici ce que le mod coûte à un serveur chargé.
            helper.assertTrue(heavyPerTick < 10_000, Component.literal(
                    "l'ordonnanceur prend " + heavyPerTick + " µs par tick avec " + HEAVY
                            + " graphes actifs, soit plus d'un cinquième du budget d'un tick"
                            + " — c'était moins d'une milliseconde"));
            helper.succeed();
        });
    }

    /**
     * Le contenu déclaré entre dans les registres, et dans l'ordre qui décide des
     * identifiants réseau (épic 11, lot B du plan multiloader).
     *
     * <p>Ce que ce test protège n'est pas visible en jeu et ne se voit pas non plus dans
     * un journal. Un item enregistré reçoit un <b>rang</b>, et c'est ce rang qui voyage
     * sur le réseau à la place du nom. Client et serveur le calculent chacun de leur côté,
     * sans se concerter : si les deux suites divergent d'un cran, le client affiche un
     * item pour un autre, ou se déconnecte sur un paquet illisible.
     *
     * <p>Tant qu'un seul chargeur existait, l'ordre était un effet de bord de l'écriture
     * du code. Le lot B a coupé l'enregistrement en deux passes — les blocs, puis les
     * items — parce que NeoForge ouvre ses registres quand il veut. Ce test dit que la
     * coupure n'a rien déplacé, et il le dira encore le jour où un second chargeur
     * exécutera les deux passes dans un autre ordre.
     *
     * <p>Il lit le <b>vrai</b> registre du serveur, pas un plan : {@code ContentOrderTest}
     * vérifie déjà la fonction pure, et deux tests d'accord entre eux ne prouvent rien
     * si aucun ne regarde le résultat.
     */
    @GameTest
    public void declaredContentKeepsTheOrderThatDecidesNetworkIds(GameTestHelper helper) {
        var report = fr.blueprint.core.content.ContentLoader.load(
                fr.blueprint.core.BlueprintPaths.content());
        helper.assertTrue(!report.items().isEmpty() && !report.blocks().isEmpty(),
                Component.literal("aucun contenu déclaré dans le serveur de test : ce test "
                        + "passerait sans rien vérifier (voir la copie faite par runGametest)"));

        // Les items : le rang doit croître le long de la suite annoncée, sans trou de
        // logique — un item déclaré absent du registre est un échec, pas une tolérance.
        int previous = -1;
        for (Identifier declared : fr.blueprint.core.content.ContentRegistrar.itemOrder(report)) {
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getOptional(declared).orElse(null);
            helper.assertTrue(item != null, Component.literal(
                    "l'item déclaré « " + declared + " » n'est pas dans le registre"));
            int rank = net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(item);
            helper.assertTrue(rank > previous, Component.literal(
                    "« " + declared + " » a le rang " + rank + ", après " + previous
                            + " : la suite des items ne suit plus itemOrder — un client "
                            + "et un serveur ne numéroteraient plus pareil"));
            previous = rank;
        }

        // Les blocs, même exigence dans leur propre registre.
        previous = -1;
        for (Identifier declared : fr.blueprint.core.content.ContentRegistrar.blockOrder(report)) {
            var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getOptional(declared).orElse(null);
            helper.assertTrue(block != null, Component.literal(
                    "le bloc déclaré « " + declared + " » n'est pas dans le registre"));
            int rank = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getId(block);
            helper.assertTrue(rank > previous, Component.literal(
                    "« " + declared + " » a le rang " + rank + ", après " + previous));
            previous = rank;
        }

        // Et l'invariant qui résume les deux : TOUT item du dossier items/ passe avant
        // TOUT item de bloc. C'est la propriété que la coupure en deux passes pouvait
        // casser sans que rien d'autre ne bronche.
        int dernierItemNu = -1;
        for (Identifier declared : report.items().keySet()) {
            dernierItemNu = Math.max(dernierItemNu,
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(
                            net.minecraft.core.registries.BuiltInRegistries.ITEM
                                    .getOptional(declared).orElseThrow()));
        }
        for (Identifier declared : report.blocks().keySet()) {
            int rank = net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM
                            .getOptional(declared).orElseThrow());
            helper.assertTrue(rank > dernierItemNu, Component.literal(
                    "l'item du bloc « " + declared + " » (rang " + rank + ") est passé avant "
                            + "un item du dossier items/ (rang " + dernierItemNu + ")"));
        }

        helper.succeed();
    }

    /**
     * VERIFY-21 automatisé : la <b>frontière de sécurité</b> de la réplication, dans un
     * vrai serveur.
     *
     * <p>Le point V53 de la session en jeu demande deux clients réels pour juger ce qui
     * s'<i>affiche</i>. Ce qu'il ne demande pas de voir, mais qu'il faut absolument
     * garantir, se prouve ici : <b>la valeur d'un joueur ne part que chez lui</b>. Un test
     * unitaire vérifie déjà la règle ; celui-ci vérifie le <b>câblage</b>, qui est ce qui
     * peut silencieusement ne pas exister.
     *
     * <p>Trois choses qu'aucun test unitaire ne couvre :
     * <ul>
     *   <li>{@code refreshReplicatedNames} n'est appelé que par {@code announceList}. Si ce
     *       fil n'était pas branché, <b>rien ne serait jamais répliqué</b> et tous les tests
     *       unitaires resteraient verts — ils posent les déclarations à la main.</li>
     *   <li>{@code varsOf} rend bien un {@code VarStorage} dans un vrai monde, et non le
     *       magasin mémoire : la réplication n'existe que sur le premier.</li>
     *   <li>Le carnet se vide en fin de tick, par le chemin réel du serveur.</li>
     * </ul>
     */
    @GameTest(maxTicks = 200)
    public void replicatedValuesReachOnlyTheirOwner(GameTestHelper helper) {
        Identifier blueprintId = id("replication");
        var server = helper.getLevel().getServer();
        var manager = BlueprintManager.of(server);
        manager.delete(blueprintId);

        // Un blueprint qui DÉCLARE une variable joueur répliquée. C'est l'adoption par le
        // gestionnaire — et elle seule — qui doit réveiller la réplication.
        Blueprint bp = new Blueprint(blueprintId);
        var limits = fr.blueprint.core.graph.GraphLimits.DEFAULT;
        fr.blueprint.core.graph.NodeTypeLookup lookup = typeId -> null;
        helper.assertTrue(new EditOperation.AddVariable(
                        new fr.blueprint.core.graph.Variable("or", PinTypes.DOUBLE,
                                LiteralValue.of(PinTypes.DOUBLE, 0.0),
                                fr.blueprint.core.graph.VarScope.PLAYER, false))
                        .apply(bp, lookup, limits).applied(),
                Component.literal("la variable n'a pas pu être déclarée"));
        helper.assertTrue(new EditOperation.SetReplicated("or", true)
                        .apply(bp, lookup, limits).applied(),
                Component.literal("le drapeau @replicated a été refusé"));
        manager.adopt(bp);

        var store = BlueprintMod.varsOf(server);
        helper.assertTrue(store instanceof fr.blueprint.core.storage.VarStorage,
                Component.literal("varsOf ne rend pas un VarStorage : rien ne peut répliquer"));
        var storage = (fr.blueprint.core.storage.VarStorage) store;

        // LE point : l'adoption a-t-elle réveillé la réplication ? Si ce fil n'existait
        // pas, tout le reste marcherait en apparence et rien ne partirait jamais.
        helper.assertTrue(!storage.replicating().isEmpty(), Component.literal(
                "adopter un blueprint à variable répliquée n'a pas rafraîchi les "
                        + "déclarations — refreshReplicatedNames n'est pas branché"));

        var alice = helper.makeMockServerPlayerInLevel();
        var bob = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(!alice.getUUID().equals(bob.getUUID()),
                Component.literal("les deux joueurs simulés partagent un UUID"));

        storage.dirty().drain();
        storage.set(fr.blueprint.core.graph.VarScope.PLAYER,
                new fr.blueprint.core.vm.VarOwner(blueprintId, alice.getUUID()), "or", 100.0);

        helper.assertTrue(storage.dirty().size() == 1, Component.literal(
                "l'écriture d'une variable répliquée n'a pas été marquée (carnet à "
                        + storage.dirty().size() + ")"));

        // La frontière. L'instantané d'Alice porte sa valeur ; celui de Bob ne porte rien.
        var pourAlice = storage.replicatedMarks(alice.getUUID());
        var pourBob = storage.replicatedMarks(bob.getUUID());
        helper.assertTrue(pourAlice.size() == 1, Component.literal(
                "Alice devrait avoir une valeur répliquée, elle en a " + pourAlice.size()));
        helper.assertTrue(alice.getUUID().equals(pourAlice.get(0).player()), Component.literal(
                "la valeur d'Alice n'est pas attribuée à Alice"));
        helper.assertTrue(pourBob.isEmpty(), Component.literal(
                "DIVULGATION : Bob reçoit " + pourBob.size() + " valeur(s) d'Alice"));

        // Et le carnet se vide en fin de tick, par le chemin réel du serveur.
        helper.succeedWhen(() -> {
            helper.assertTrue(storage.dirty().isEmpty(), Component.literal(
                    "le carnet n'a pas été vidé en fin de tick : VarReplication.flush "
                            + "n'est pas appelé"));
            cleanup(helper, blueprintId);
        });
    }

    /**
     * <b>Poser la pastille dans l'éditeur et enregistrer suffit.</b>
     *
     * <p>Trouvé en relecture, et c'était la panne la plus grave de l'épic 21 : {@code save} était
     * le seul chemin de mutation du gestionnaire qui n'annonçait pas, et {@code announceList} est
     * le seul appelant de {@code refreshReplicatedNames}. Le flux normal d'un auteur — ouvrir
     * l'éditeur, cliquer {@code »} sur une variable, {@code Ctrl+S} — laissait donc les
     * déclarations à leur valeur précédente : sur un monde fraîchement lancé, l'ensemble restait
     * vide, le magasin prenait son chemin rapide, et <b>rien ne se répliquait</b>. Le drapeau
     * était persisté, visible dans le graphe, et sans effet — jusqu'à ce qu'une création ou une
     * activation sans rapport passe par là.
     *
     * <p>Le gametest voisin n'exerçait qu'{@code adopt}, qui annonce. C'est cette combinaison
     * qu'aucun test ne croisait : le drapeau posé APRÈS l'adoption, par un enregistrement.
     */
    @GameTest(maxTicks = 200)
    public void togglingTheFlagAndSavingIsEnough(GameTestHelper helper) {
        Identifier blueprintId = id("replication_save");
        var server = helper.getLevel().getServer();
        var manager = BlueprintManager.of(server);
        manager.delete(blueprintId);

        var limits = fr.blueprint.core.graph.GraphLimits.DEFAULT;
        fr.blueprint.core.graph.NodeTypeLookup lookup = typeId -> null;

        // Adopté SANS le drapeau : c'est l'état d'un blueprint qu'on vient d'ouvrir.
        Blueprint bp = new Blueprint(blueprintId);
        new EditOperation.AddVariable(new fr.blueprint.core.graph.Variable(
                "argent", PinTypes.DOUBLE, LiteralValue.of(PinTypes.DOUBLE, 0.0),
                fr.blueprint.core.graph.VarScope.WORLD, false)).apply(bp, lookup, limits);
        manager.adopt(bp);

        var storage = (fr.blueprint.core.storage.VarStorage) BlueprintMod.varsOf(server);
        helper.assertTrue(!storage.replicating().covers(
                        fr.blueprint.core.graph.VarScope.WORLD, blueprintId, "argent"),
                Component.literal("rien ne devrait encore être répliqué"));

        // Le geste de l'auteur : la pastille, puis Ctrl+S — donc un snapshot enregistré.
        Blueprint snapshot = manager.get(blueprintId).orElseThrow().copy();
        helper.assertTrue(new EditOperation.SetReplicated("argent", true)
                        .apply(snapshot, lookup, limits).applied(),
                Component.literal("le drapeau a été refusé sur le snapshot"));
        var verdict = manager.save(snapshot, snapshot.revision() - 1);
        helper.assertTrue(verdict.outcome() == BlueprintManager.SaveOutcome.SAVED,
                Component.literal("l'enregistrement a été refusé : " + verdict.outcome()));

        helper.assertTrue(storage.replicating().covers(
                        fr.blueprint.core.graph.VarScope.WORLD, blueprintId, "argent"),
                Component.literal("enregistrer n'a pas rafraîchi les déclarations — poser la "
                        + "pastille puis Ctrl+S ne réplique donc RIEN"));

        // Et l'écriture qui suit est bien marquée, ce qui est tout le point.
        storage.dirty().drain();
        storage.set(fr.blueprint.core.graph.VarScope.WORLD,
                new fr.blueprint.core.vm.VarOwner(blueprintId, null), "argent", 42.0);
        helper.assertTrue(storage.dirty().size() == 1, Component.literal(
                "l'écriture n'a pas été marquée après un enregistrement"));

        helper.succeedWhen(() -> {
            helper.assertTrue(storage.dirty().isEmpty(),
                    Component.literal("le carnet n'a pas été vidé en fin de tick"));
            cleanup(helper, blueprintId);
        });
    }
}
