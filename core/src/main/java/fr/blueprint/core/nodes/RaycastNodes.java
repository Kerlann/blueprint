package fr.blueprint.core.nodes;

import fr.blueprint.api.node.NodeCategories;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.api.registry.NodeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Lancer de rayon (batch 7).
 *
 * <p>« Ce que le joueur regarde » n'était pas exprimable. L'événement
 * {@code player_use_block} donne le bloc CLIQUÉ, mais un script qui veut réagir au
 * regard — un panneau qui s'illumine quand on le fixe, une arme à distance — n'avait
 * rien. Le sonder bloc par bloc dans une boucle aurait coûté vingt nœuds et un
 * carburant considérable.
 *
 * <p>La portée est <b>bornée</b> à {@link #MAX_DISTANCE} : un rayon vit dans une
 * boucle chaude, et Minecraft lui-même limite l'atteinte d'un joueur à six blocs.
 */
public final class RaycastNodes {

    /** Portée maximale d'un rayon, en blocs. Au-delà, la valeur est ramenée ici. */
    public static final double MAX_DISTANCE = 128;

    private RaycastNodes() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("blueprint", path);
    }

    public static void register(NodeRegistry r) {
        /*
         * Le rayon générique : d'un point, dans une direction. « touché » à côté des
         * sorties, comme partout ailleurs — un rayon dans le vide est le cas NORMAL,
         * pas une faute, et une position nulle se propagerait en silence.
         */
        r.register(NodeType.builder(id("world/raycast"))
                .category(NodeCategories.WORLD_BLOCK).exec().permission(Permission.SAFE)
                .in("from", PinTypes.VEC3)
                .in("direction", PinTypes.VEC3)
                .in("distance", PinTypes.DOUBLE, 16.0)
                .in("through_fluids", PinTypes.BOOL, false)
                .out("hit", PinTypes.BOOL)
                .out("pos", PinTypes.BLOCKPOS)
                .out("face", PinTypes.DIRECTION)
                .out("point", PinTypes.VEC3)
                .action(ctx -> {
                    Vec3 from = vec(ctx.in("from"));
                    Vec3 to = from.add(direction(ctx.in("direction"))
                            .scale(clampDistance(ctx.in("distance"))));
                    BlockHitResult result = ctx.level().clip(new ClipContext(from, to,
                            ClipContext.Block.OUTLINE,
                            Boolean.TRUE.equals(ctx.in("through_fluids"))
                                    ? ClipContext.Fluid.NONE : ClipContext.Fluid.ANY,
                            (Entity) null));
                    boolean hit = result.getType() == HitResult.Type.BLOCK;
                    ctx.out("hit", hit);
                    ctx.out("point", hit ? result.getLocation() : to);
                    ctx.out("pos", hit ? result.getBlockPos() : BlockPos.containing(to));
                    ctx.out("face", hit ? result.getDirection() : Direction.UP);
                })
                .build());

        /* Le raccourci qui sert vraiment : ce que regarde CETTE entité. Sans lui, il
         * faudrait exposer l'angle de vue et refaire la trigonométrie à la main. */
        r.register(NodeType.builder(id("entity/looking_at"))
                .category(NodeCategories.ENTITY_READ).exec().permission(Permission.SAFE)
                .in("entity", PinTypes.ENTITY)
                .in("distance", PinTypes.DOUBLE, 6.0)
                .out("hit", PinTypes.BOOL)
                .out("pos", PinTypes.BLOCKPOS)
                .out("face", PinTypes.DIRECTION)
                .action(ctx -> {
                    if (!(ctx.in("entity") instanceof Entity entity)) {
                        ctx.out("hit", false);
                        ctx.out("pos", BlockPos.ZERO);
                        ctx.out("face", Direction.UP);
                        return;
                    }
                    Vec3 from = entity.getEyePosition();
                    Vec3 to = from.add(entity.getViewVector(1.0F)
                            .scale(clampDistance(ctx.in("distance"))));
                    BlockHitResult result = ctx.level().clip(new ClipContext(from, to,
                            ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, entity));
                    boolean hit = result.getType() == HitResult.Type.BLOCK;
                    ctx.out("hit", hit);
                    ctx.out("pos", hit ? result.getBlockPos() : BlockPos.containing(to));
                    ctx.out("face", hit ? result.getDirection() : Direction.UP);
                })
                .build());

        /*
         * Le rayon qui cherche une ENTITÉ. Séparé du précédent parce qu'il ne répond
         * pas à la même question : « quel bloc » et « quelle créature » ne se
         * répondent pas ensemble, et les fondre donnerait un nœud à sept sorties dont
         * la moitié serait toujours vide.
         */
        r.register(NodeType.builder(id("world/raycast_entity"))
                .category(NodeCategories.ENTITY_QUERY).exec().permission(Permission.SAFE)
                .in("from", PinTypes.VEC3)
                .in("direction", PinTypes.VEC3)
                .in("distance", PinTypes.DOUBLE, 16.0)
                .out("hit", PinTypes.BOOL)
                .out("entity", PinTypes.ENTITY)
                .action(ctx -> {
                    Vec3 from = vec(ctx.in("from"));
                    double distance = clampDistance(ctx.in("distance"));
                    Vec3 to = from.add(direction(ctx.in("direction")).scale(distance));
                    EntityHitResult result = ProjectileUtil.getEntityHitResult(
                            ctx.level(), null, from, to,
                            new AABB(from, to).inflate(1.0), Entity::isAlive);
                    ctx.out("hit", result != null);
                    if (result != null) {
                        ctx.out("entity", result.getEntity());
                    }
                })
                .build());
    }

    // ------------------------------------------------------------------ outillage

    /**
     * Une direction nulle ou de longueur nulle vaut « vers le bas ». Normaliser le
     * vecteur nul donnerait le vecteur nul, et le rayon n'irait nulle part — sans
     * qu'aucune erreur ne le dise.
     */
    private static Vec3 direction(Object pin) {
        Vec3 raw = pin instanceof Vec3 v ? v : Vec3.ZERO;
        return raw.lengthSqr() < 1e-9 ? new Vec3(0, -1, 0) : raw.normalize();
    }

    private static double clampDistance(Object pin) {
        double distance = pin instanceof Number n ? n.doubleValue() : 16.0;
        return Math.clamp(distance, 0, MAX_DISTANCE);
    }

    private static Vec3 vec(Object pin) {
        return pin instanceof Vec3 v ? v : Vec3.ZERO;
    }
}
