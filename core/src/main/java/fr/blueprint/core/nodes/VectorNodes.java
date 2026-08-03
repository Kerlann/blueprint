package fr.blueprint.core.nodes;

import fr.blueprint.api.node.NodeCategories;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.api.registry.NodeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Vecteurs et positions (batch 2).
 *
 * <p>Avant ces nœuds, {@code vec3} était consommé par sept pins et produit par un
 * seul (« position de l'entité ») : on ne pouvait ni construire un vecteur à partir
 * de trois nombres, ni en décaler un. « Des particules deux blocs au-dessus du
 * joueur » était littéralement inexprimable — c'est le manque qui bloquait le plus
 * de scripts réels.
 *
 * <p>Tous purs. Les {@code Vec3} et {@code BlockPos} de Minecraft sont immuables, et
 * chaque nœud rend une nouvelle valeur — même règle qu'aux listes (7.8) : un nœud pur
 * est mémoïsé et sa sortie lue par plusieurs consommateurs.
 */
public final class VectorNodes {

    private VectorNodes() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("blueprint", path);
    }

    public static void register(NodeRegistry r) {
        vectors(r);
        positions(r);
    }

    // ------------------------------------------------------------------ vecteurs

    private static void vectors(NodeRegistry r) {
        r.register(NodeType.builder(id("vec/make"))
                .category(NodeCategories.MATH_VECTOR).pure()
                .in("x", PinTypes.DOUBLE, 0.0)
                .in("y", PinTypes.DOUBLE, 0.0)
                .in("z", PinTypes.DOUBLE, 0.0)
                .out("vec", PinTypes.VEC3)
                .action(ctx -> ctx.out("vec", new Vec3(
                        ctx.<Double>in("x"), ctx.<Double>in("y"), ctx.<Double>in("z"))))
                .build());

        r.register(NodeType.builder(id("vec/split"))
                .category(NodeCategories.MATH_VECTOR).pure()
                .in("vec", PinTypes.VEC3)
                .out("x", PinTypes.DOUBLE).out("y", PinTypes.DOUBLE).out("z", PinTypes.DOUBLE)
                .action(ctx -> {
                    Vec3 v = vec(ctx.in("vec"));
                    ctx.out("x", v.x);
                    ctx.out("y", v.y);
                    ctx.out("z", v.z);
                })
                .build());

        r.register(NodeType.builder(id("vec/add"))
                .category(NodeCategories.MATH_VECTOR).pure()
                .in("a", PinTypes.VEC3).in("b", PinTypes.VEC3)
                .out("vec", PinTypes.VEC3)
                .action(ctx -> ctx.out("vec", vec(ctx.in("a")).add(vec(ctx.in("b")))))
                .build());

        r.register(NodeType.builder(id("vec/sub"))
                .category(NodeCategories.MATH_VECTOR).pure()
                .in("a", PinTypes.VEC3).in("b", PinTypes.VEC3)
                .out("vec", PinTypes.VEC3)
                .action(ctx -> ctx.out("vec", vec(ctx.in("a")).subtract(vec(ctx.in("b")))))
                .build());

        r.register(NodeType.builder(id("vec/scale"))
                .category(NodeCategories.MATH_VECTOR).pure()
                .in("vec", PinTypes.VEC3).in("factor", PinTypes.DOUBLE, 1.0)
                .out("vec", PinTypes.VEC3)
                .action(ctx -> ctx.out("vec", vec(ctx.in("vec")).scale(ctx.<Double>in("factor"))))
                .build());

        /* Le décalage sur un seul axe : « deux blocs au-dessus » est le geste le plus
         * courant du scripting de monde, et l'écrire en make + add coûterait deux
         * nœuds et quatre liens à chaque fois. */
        r.register(NodeType.builder(id("vec/offset"))
                .category(NodeCategories.MATH_VECTOR).pure()
                .in("vec", PinTypes.VEC3)
                .in("dx", PinTypes.DOUBLE, 0.0)
                .in("dy", PinTypes.DOUBLE, 0.0)
                .in("dz", PinTypes.DOUBLE, 0.0)
                .out("vec", PinTypes.VEC3)
                .action(ctx -> ctx.out("vec", vec(ctx.in("vec")).add(
                        ctx.<Double>in("dx"), ctx.<Double>in("dy"), ctx.<Double>in("dz"))))
                .build());

        r.register(NodeType.builder(id("vec/length"))
                .category(NodeCategories.MATH_VECTOR).pure()
                .in("vec", PinTypes.VEC3).out("length", PinTypes.DOUBLE)
                .action(ctx -> ctx.out("length", vec(ctx.in("vec")).length()))
                .build());

        r.register(NodeType.builder(id("vec/distance"))
                .category(NodeCategories.MATH_VECTOR).pure()
                .in("a", PinTypes.VEC3).in("b", PinTypes.VEC3)
                .out("distance", PinTypes.DOUBLE)
                .action(ctx -> ctx.out("distance", vec(ctx.in("a")).distanceTo(vec(ctx.in("b")))))
                .build());

        /* Normaliser le vecteur nul rend le vecteur nul plutôt que NaN : Vec3.normalize
         * le fait déjà, mais le dire ici évite qu'on cherche pourquoi. */
        r.register(NodeType.builder(id("vec/normalize"))
                .category(NodeCategories.MATH_VECTOR).pure()
                .in("vec", PinTypes.VEC3).out("vec", PinTypes.VEC3)
                .action(ctx -> ctx.out("vec", vec(ctx.in("vec")).normalize()))
                .build());

        r.register(NodeType.builder(id("vec/dot"))
                .category(NodeCategories.MATH_VECTOR).pure()
                .in("a", PinTypes.VEC3).in("b", PinTypes.VEC3)
                .out("dot", PinTypes.DOUBLE)
                .action(ctx -> ctx.out("dot", vec(ctx.in("a")).dot(vec(ctx.in("b")))))
                .build());
    }

    // ----------------------------------------------------------------- positions

    private static void positions(NodeRegistry r) {
        r.register(NodeType.builder(id("pos/make"))
                .category(NodeCategories.MATH_POSITION).pure()
                .in("x", PinTypes.INT, 0).in("y", PinTypes.INT, 0).in("z", PinTypes.INT, 0)
                .out("pos", PinTypes.BLOCKPOS)
                .action(ctx -> ctx.out("pos", new BlockPos(
                        ctx.<Integer>in("x"), ctx.<Integer>in("y"), ctx.<Integer>in("z"))))
                .build());

        r.register(NodeType.builder(id("pos/split"))
                .category(NodeCategories.MATH_POSITION).pure()
                .in("pos", PinTypes.BLOCKPOS)
                .out("x", PinTypes.INT).out("y", PinTypes.INT).out("z", PinTypes.INT)
                .action(ctx -> {
                    BlockPos p = pos(ctx.in("pos"));
                    ctx.out("x", p.getX());
                    ctx.out("y", p.getY());
                    ctx.out("z", p.getZ());
                })
                .build());

        r.register(NodeType.builder(id("pos/offset"))
                .category(NodeCategories.MATH_POSITION).pure()
                .in("pos", PinTypes.BLOCKPOS)
                .in("dx", PinTypes.INT, 0).in("dy", PinTypes.INT, 0).in("dz", PinTypes.INT, 0)
                .out("pos", PinTypes.BLOCKPOS)
                .action(ctx -> ctx.out("pos", pos(ctx.in("pos")).offset(
                        ctx.<Integer>in("dx"), ctx.<Integer>in("dy"), ctx.<Integer>in("dz"))))
                .build());

        /* Le décalage DANS une direction : le pendant naturel de player_use_block,
         * qui donne une position et la face touchée. « Poser un bloc sur la face
         * cliquée » tenait sinon en un aiguillage à six branches. */
        r.register(NodeType.builder(id("pos/relative"))
                .category(NodeCategories.MATH_POSITION).pure()
                .in("pos", PinTypes.BLOCKPOS)
                .in("direction", PinTypes.DIRECTION, Direction.UP)
                .in("distance", PinTypes.INT, 1)
                .out("pos", PinTypes.BLOCKPOS)
                .action(ctx -> {
                    Direction dir = ctx.in("direction");
                    ctx.out("pos", pos(ctx.in("pos")).relative(
                            dir == null ? Direction.UP : dir, ctx.<Integer>in("distance")));
                })
                .build());

        r.register(NodeType.builder(id("pos/distance"))
                .category(NodeCategories.MATH_POSITION).pure()
                .in("a", PinTypes.BLOCKPOS).in("b", PinTypes.BLOCKPOS)
                .out("distance", PinTypes.DOUBLE)
                .action(ctx -> ctx.out("distance",
                        Math.sqrt(pos(ctx.in("a")).distSqr(pos(ctx.in("b"))))))
                .build());

        /* Les deux ponts. Un blockpos vaut le CENTRE de son bloc en vec3 : viser le
         * coin ferait apparaître les particules et les entités à cheval sur quatre
         * blocs, ce qui se voit tout de suite en jeu. */
        r.register(NodeType.builder(id("pos/to_vec"))
                .category(NodeCategories.MATH_POSITION).pure()
                .in("pos", PinTypes.BLOCKPOS)
                .in("centered", PinTypes.BOOL, true)
                .out("vec", PinTypes.VEC3)
                .action(ctx -> {
                    BlockPos p = pos(ctx.in("pos"));
                    ctx.out("vec", Boolean.TRUE.equals(ctx.in("centered"))
                            ? Vec3.atCenterOf(p) : Vec3.atLowerCornerOf(p));
                })
                .build());

        r.register(NodeType.builder(id("vec/to_pos"))
                .category(NodeCategories.MATH_POSITION).pure()
                .in("vec", PinTypes.VEC3).out("pos", PinTypes.BLOCKPOS)
                .action(ctx -> ctx.out("pos", BlockPos.containing(vec(ctx.in("vec")))))
                .build());
    }

    // ------------------------------------------------------------------ outillage

    /**
     * Un pin {@code vec3} non câblé et sans littéral rend null. Retomber sur zéro
     * plutôt que lever : un vecteur manquant est une origine, et un NPE opaque au
     * milieu d'un graphe ne dit rien à son auteur.
     */
    private static Vec3 vec(Object value) {
        return value instanceof Vec3 v ? v : Vec3.ZERO;
    }

    private static BlockPos pos(Object value) {
        return value instanceof BlockPos p ? p : BlockPos.ZERO;
    }
}
