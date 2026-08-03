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
            apply(bp, new EditOperation.SetLiteral(event, "name",
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
