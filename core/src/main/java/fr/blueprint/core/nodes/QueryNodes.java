package fr.blueprint.core.nodes;

import fr.blueprint.api.node.NodeCategories;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.api.registry.NodeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Interrogation du monde et des entités (batch 3).
 *
 * <p>Jusqu'ici un graphe ne pouvait agir que sur ce que son ÉVÉNEMENT lui donnait :
 * aucun moyen de trouver les joueurs connectés, les entités alentour, ni même de
 * lire l'heure — {@code set_time} existait sans {@code get_time}. Un script ne
 * pouvait pas répondre à « s'il fait nuit et qu'un joueur est à moins de dix
 * blocs ».
 *
 * <p>Les recherches sont <b>bornées</b> : un rayon plafonné et un nombre de
 * résultats plafonné. Une requête sur un rayon de mille blocs balaierait des
 * milliers d'entités à chaque passage, dans une boucle chaude, sans que rien ne
 * l'annonce à son auteur.
 */
public final class QueryNodes {

    /** Rayon maximal d'une recherche, en blocs. Au-delà, la valeur est ramenée ici. */
    public static final double MAX_RADIUS = 128;
    /** Nombre maximal d'entités rendues par une recherche. */
    public static final int MAX_RESULTS = 256;

    private QueryNodes() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("blueprint", path);
    }

    public static void register(NodeRegistry r) {
        entities(r);
        world(r);
    }

    // ------------------------------------------------------------------- entités

    private static void entities(NodeRegistry r) {
        r.register(NodeType.builder(id("query/players"))
                .category(NodeCategories.ENTITY_QUERY).exec()
                .out("players", PinTypes.listOf(PinTypes.PLAYER))
                .action(ctx -> ctx.out("players",
                        List.copyOf(ctx.server().getPlayerList().getPlayers())))
                .build());

        r.register(NodeType.builder(id("query/entities_near"))
                .category(NodeCategories.ENTITY_QUERY).exec()
                .in("pos", PinTypes.VEC3)
                .in("radius", PinTypes.DOUBLE, 8.0)
                .out("entities", PinTypes.listOf(PinTypes.ENTITY))
                .action(ctx -> {
                    Vec3 center = vec(ctx.in("pos"));
                    double radius = clampRadius(ctx.in("radius"));
                    List<Entity> found = ctx.level().getEntities((Entity) null,
                            new AABB(center, center).inflate(radius),
                            e -> e.isAlive() && e.distanceToSqr(center) <= radius * radius);
                    ctx.out("entities", cap(found));
                })
                .build());

        r.register(NodeType.builder(id("query/nearest_player"))
                .category(NodeCategories.ENTITY_QUERY).exec()
                .in("pos", PinTypes.VEC3)
                .in("radius", PinTypes.DOUBLE, 16.0)
                .out("player", PinTypes.PLAYER)
                .out("found", PinTypes.BOOL)
                .action(ctx -> {
                    Vec3 center = vec(ctx.in("pos"));
                    double radius = clampRadius(ctx.in("radius"));
                    ServerPlayer nearest = null;
                    double best = radius * radius;
                    for (ServerPlayer player : ctx.level().players()) {
                        double distance = player.distanceToSqr(center);
                        if (distance <= best) {
                            best = distance;
                            nearest = player;
                        }
                    }
                    // « found » plutôt qu'un joueur nul : un pin vide se propage en
                    // silence et la faute apparaît trois nœuds plus loin.
                    ctx.out("found", nearest != null);
                    if (nearest != null) {
                        ctx.out("player", nearest);
                    }
                })
                .build());

        r.register(NodeType.builder(id("entity/name"))
                .category(NodeCategories.ENTITY_READ).exec()
                .in("entity", PinTypes.ENTITY).out("name", PinTypes.STRING)
                .action(ctx -> {
                    Entity entity = ctx.in("entity");
                    ctx.out("name", entity == null ? "" : entity.getName().getString());
                })
                .build());

        r.register(NodeType.builder(id("entity/type"))
                .category(NodeCategories.ENTITY_READ).exec()
                .in("entity", PinTypes.ENTITY).out("type", PinTypes.RESOURCE_LOCATION)
                .action(ctx -> {
                    Entity entity = ctx.in("entity");
                    if (entity != null) {
                        ctx.out("type", net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                                .getKey(entity.getType()));
                    }
                })
                .build());

        r.register(NodeType.builder(id("entity/is_alive"))
                .category(NodeCategories.ENTITY_READ).exec()
                .in("entity", PinTypes.ENTITY).out("alive", PinTypes.BOOL)
                .action(ctx -> {
                    Entity entity = ctx.in("entity");
                    ctx.out("alive", entity != null && entity.isAlive());
                })
                .build());

        /*
         * Le pont manquant. L'événement « mort d'une entité » se déclenche AUSSI pour
         * un joueur, mais sa sortie est une entity : sans ce nœud, l'information
         * existait et restait inatteignable.
         */
        r.register(NodeType.builder(id("entity/as_player"))
                .category(NodeCategories.ENTITY_READ).exec()
                .in("entity", PinTypes.ENTITY)
                .out("player", PinTypes.PLAYER)
                .out("is_player", PinTypes.BOOL)
                .action(ctx -> {
                    boolean isPlayer = ctx.in("entity") instanceof ServerPlayer;
                    ctx.out("is_player", isPlayer);
                    if (isPlayer) {
                        ctx.out("player", ctx.in("entity"));
                    }
                })
                .build());
    }

    // --------------------------------------------------------------------- monde

    private static void world(NodeRegistry r) {
        /*
         * L'heure du monde et l'heure du JOUR sont deux choses différentes :
         * getGameTime monte sans fin depuis la création, getDayTime revient à zéro
         * toutes les 24 000 ticks. Confondre les deux fait des scripts qui marchent
         * le premier jour et jamais après.
         */
        r.register(NodeType.builder(id("world/get_time"))
                .category(NodeCategories.WORLD_STATE).exec()
                .out("day_time", PinTypes.LONG)
                .out("game_time", PinTypes.LONG)
                .out("day", PinTypes.LONG)
                .action(ctx -> {
                    ServerLevel level = ctx.level();
                    long dayTime = level.getDayTime();
                    ctx.out("day_time", dayTime % 24_000L);
                    ctx.out("game_time", level.getGameTime());
                    ctx.out("day", dayTime / 24_000L);
                })
                .build());

        r.register(NodeType.builder(id("world/is_day"))
                .category(NodeCategories.WORLD_STATE).exec()
                .out("is_day", PinTypes.BOOL)
                .action(ctx -> ctx.out("is_day", ctx.level().isBrightOutside()))
                .build());

        r.register(NodeType.builder(id("world/get_weather"))
                .category(NodeCategories.WORLD_STATE).exec()
                .out("raining", PinTypes.BOOL)
                .out("thundering", PinTypes.BOOL)
                .action(ctx -> {
                    ServerLevel level = ctx.level();
                    ctx.out("raining", level.isRaining());
                    ctx.out("thundering", level.isThundering());
                })
                .build());

        r.register(NodeType.builder(id("world/dimension"))
                .category(NodeCategories.WORLD_STATE).exec()
                .out("dimension", PinTypes.RESOURCE_LOCATION)
                .action(ctx -> ctx.out("dimension", ctx.level().dimension().identifier()))
                .build());

        r.register(NodeType.builder(id("world/light"))
                .category(NodeCategories.WORLD_BLOCK).exec()
                .in("pos", PinTypes.BLOCKPOS)
                .out("light", PinTypes.INT)
                .action(ctx -> ctx.out("light",
                        ctx.level().getMaxLocalRawBrightness(pos(ctx.in("pos")))))
                .build());

        /* La hauteur du sol : « faire apparaître quelque chose EN SURFACE » sans
         * sonder les blocs un par un dans une boucle. */
        /* Le pendant de entity/health, qui existait seul : sans le maximum, un script
         * ne peut pas dire « à moins de la moitié de sa vie ». */
        r.register(NodeType.builder(id("entity/max_health"))
                .category(NodeCategories.ENTITY_READ).exec().permission(Permission.SAFE)
                .in("entity", PinTypes.ENTITY)
                .out("max", PinTypes.DOUBLE)
                .action(ctx -> {
                    if (ctx.in("entity") instanceof LivingEntity living) {
                        ctx.out("max", (double) living.getMaxHealth());
                    }
                })
                .build());

        r.register(NodeType.builder(id("world/surface"))
                .category(NodeCategories.WORLD_BLOCK).exec()
                .in("pos", PinTypes.BLOCKPOS)
                .out("pos", PinTypes.BLOCKPOS)
                .action(ctx -> {
                    BlockPos p = pos(ctx.in("pos"));
                    ctx.out("pos", ctx.level().getHeightmapPos(
                            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p));
                })
                .build());
    }

    // ------------------------------------------------------------------ outillage

    /**
     * Un rayon hors bornes est RAMENÉ, pas refusé : un script qui écrit 1000 veut
     * dire « loin », et faire échouer son exécution lui apprendrait moins que de lui
     * donner le maximum possible.
     */
    private static double clampRadius(Object value) {
        double radius = value instanceof Number n ? n.doubleValue() : 8.0;
        return Math.clamp(radius, 0, MAX_RADIUS);
    }

    private static <T> List<T> cap(List<T> found) {
        return found.size() <= MAX_RESULTS ? List.copyOf(found)
                : List.copyOf(new ArrayList<>(found.subList(0, MAX_RESULTS)));
    }

    private static Vec3 vec(Object value) {
        return value instanceof Vec3 v ? v : Vec3.ZERO;
    }

    private static BlockPos pos(Object value) {
        return value instanceof BlockPos p ? p : BlockPos.ZERO;
    }
}
