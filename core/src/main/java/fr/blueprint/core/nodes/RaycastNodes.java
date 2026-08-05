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
        // TARIF PAR ANALYSE, non par mesure — ce nœud demande un monde vivant et
        // FuelCalibrationTest ne peut donc pas l'exécuter (ctx.level() est nul headless).
        //
        // Le travail : Level.clip parcourt les blocs par DDA de « from » à « to ». À la
        // portée maximale (MAX_DISTANCE = 128), la diagonale traverse de l'ordre de trois
        // cent quatre-vingts positions, chacune coûtant un getBlockState et, pour un bloc
        // non vide, la résolution de sa VoxelShape puis une intersection. Cent unités est
        // l'ordre de grandeur retenu ; l'incertitude est d'un facteur trois, ce qui est
        // sans importance devant l'écart au tarif précédent, qui était de un.
        //
        // Le tarif couvre le PIRE cas, car « distance » est une entrée que l'auteur du
        // graphe choisit, et le fuel d'un nœud est fixé à la compilation. À cent, un tick
        // autorise cent rayons — largement de quoi jouer, trop peu pour épuiser un serveur.
        r.register(NodeType.builder(id("world/raycast")).fuelCost(100)
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
                    // CollisionContext.empty() et NON (Entity) null : la surcharge qui
                    // prend une entité fait un requireNonNull dessus, si bien que ce
                    // nœud levait à CHAQUE exécution. « Pas d'entité » se dit avec un
                    // contexte vide, pas avec une entité absente.
                    BlockHitResult result = ctx.level().clip(new ClipContext(from, to,
                            ClipContext.Block.OUTLINE,
                            Boolean.TRUE.equals(ctx.in("through_fluids"))
                                    ? ClipContext.Fluid.NONE : ClipContext.Fluid.ANY,
                            net.minecraft.world.phys.shapes.CollisionContext.empty()));
                    boolean hit = result.getType() == HitResult.Type.BLOCK;
                    ctx.out("hit", hit);
                    ctx.out("point", hit ? result.getLocation() : to);
                    ctx.out("pos", hit ? result.getBlockPos() : BlockPos.containing(to));
                    ctx.out("face", hit ? result.getDirection() : Direction.UP);
                })
                .build());

        /* Le raccourci qui sert vraiment : ce que regarde CETTE entité. Sans lui, il
         * faudrait exposer l'angle de vue et refaire la trigonométrie à la main. */
        // TARIF PAR ANALYSE : cent, comme world/raycast, et pour la même raison — c'est
        // le même Level.clip, borné par la même MAX_DISTANCE.
        //
        // Sa catégorie dit « entity/read », ce qui le range parmi les lectures : à côté
        // de entity/health et entity/name, qui coûtent deux. Il en coûte cinquante fois
        // plus. La catégorie décrit ce que le nœud RÉPOND, pas ce qu'il DÉPENSE, et
        // c'est précisément pourquoi le tarif se lit dans le code plutôt que déduit du
        // rangement dans la palette.
        r.register(NodeType.builder(id("entity/looking_at")).fuelCost(100)
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
        // TARIF PAR ANALYSE (même raison : monde vivant requis).
        //
        // Plus cher que le rayon de blocs : la boîte englobante fait jusqu'à cent
        // vingt-huit blocs de long, et getEntities balaie toutes les sections d'entités
        // qu'elle recouvre — puis chaque candidat subit un box.clip. Le coût suit donc le
        // volume balayé ET le peuplement du monde, ce dernier échappant à l'auteur du
        // graphe. Cent cinquante unités.
        r.register(NodeType.builder(id("world/raycast_entity")).fuelCost(150)
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
                    // Sans entité TIREUSE, ProjectileUtil est hors de portée : toutes ses
                    // surcharges déréférencent celle qu'on lui passe. Ce nœud lui donnait
                    // null et levait donc à chaque exécution.
                    //
                    // Le rayon est donc fait à la main : les entités du volume, filtrées
                    // par l'intersection de leur boîte avec le segment, et la PLUS PROCHE
                    // gagne. C'est ce que ProjectileUtil fait aussi, à ceci près qu'il
                    // exclut le tireur — ce qu'on n'a pas à faire, faute de tireur.
                    Entity closest = null;
                    double best = Double.MAX_VALUE;
                    Vec3 point = null;
                    for (Entity candidate : ctx.level().getEntities((Entity) null,
                            new AABB(from, to).inflate(1.0), Entity::isAlive)) {
                        var box = candidate.getBoundingBox().inflate(candidate.getPickRadius());
                        var clip = box.clip(from, to);
                        if (clip.isEmpty()) {
                            continue;
                        }
                        double squared = from.distanceToSqr(clip.get());
                        if (squared < best) {
                            best = squared;
                            closest = candidate;
                            point = clip.get();
                        }
                    }
                    EntityHitResult result = closest == null ? null
                            : new EntityHitResult(closest, point);
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
