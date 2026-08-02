package fr.blueprint.core.nodes;

import fr.blueprint.api.node.NodeCategories;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.api.registry.NodeRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.Random;
import java.util.function.DoubleBinaryOperator;

/**
 * La bibliothèque standard (stories 7.1a + 7.2) : flux de base compatible avec la VM
 * actuelle, purs mathématiques/logiques/chaînes, conversions, aléatoire déterministe.
 * Les nœuds de flux structurés (sequence, while, for…) exigent les frames VM (v1.1) —
 * story 7.1b. Division et modulo par zéro → {@code ctx.fail} traduit, jamais d'exception.
 */
public final class StandardNodes {

    private StandardNodes() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("blueprint", path);
    }

    public static void register(NodeRegistry r) {
        // ------------------------------------------------------------- flux (7.1a)
        r.register(NodeType.builder(id("flow/branch"))
                .category(NodeCategories.FLOW)
                .execIn("exec_in").execOut("true").execOut("false")
                .in("condition", PinTypes.BOOL, false)
                .action(ctx -> ctx.exec(ctx.<Boolean>in("condition") ? "true" : "false"))
                .build());

        r.register(NodeType.builder(id("flow/select"))
                .category(NodeCategories.FLOW)
                .pure().generic("T")
                .in("condition", PinTypes.BOOL, false)
                .in("if_true", PinTypes.generic("T"))
                .in("if_false", PinTypes.generic("T"))
                .out("value", PinTypes.generic("T"))
                .action(ctx -> ctx.out("value",
                        ctx.<Boolean>in("condition") ? ctx.in("if_true") : ctx.in("if_false")))
                .build());

        r.register(NodeType.builder(id("flow/wait"))
                .category(NodeCategories.FLOW)
                .exec()
                .in("ticks", PinTypes.INT, 20)
                .action(ctx -> ctx.suspend(Math.max(1, ctx.<Integer>in("ticks"))))
                .build());

        r.register(NodeType.builder(id("flow/return"))
                .category(NodeCategories.FLOW)
                .execIn("exec_in")
                .action(ctx -> {
                })
                .build());

        // ------------------------------------------------------------- maths (7.2)
        binaryMath(r, "math/add", Double::sum);
        binaryMath(r, "math/sub", (a, b) -> a - b);
        binaryMath(r, "math/mul", (a, b) -> a * b);
        binaryMath(r, "math/min", Math::min);
        binaryMath(r, "math/max", Math::max);

        r.register(NodeType.builder(id("math/div"))
                .category(NodeCategories.MATH).pure()
                .in("a", PinTypes.DOUBLE, 0.0).in("b", PinTypes.DOUBLE, 1.0)
                .out("result", PinTypes.DOUBLE)
                .action(ctx -> {
                    double b = ctx.in("b");
                    if (b == 0.0) {
                        ctx.fail(Component.translatable("blueprint.fault.division_by_zero"));
                        return;
                    }
                    ctx.out("result", ctx.<Double>in("a") / b);
                })
                .build());

        r.register(NodeType.builder(id("math/mod"))
                .category(NodeCategories.MATH).pure()
                .in("a", PinTypes.DOUBLE, 0.0).in("b", PinTypes.DOUBLE, 1.0)
                .out("result", PinTypes.DOUBLE)
                .action(ctx -> {
                    double b = ctx.in("b");
                    if (b == 0.0) {
                        ctx.fail(Component.translatable("blueprint.fault.division_by_zero"));
                        return;
                    }
                    ctx.out("result", ctx.<Double>in("a") % b);
                })
                .build());

        r.register(NodeType.builder(id("math/abs"))
                .category(NodeCategories.MATH).pure()
                .in("value", PinTypes.DOUBLE, 0.0).out("result", PinTypes.DOUBLE)
                .action(ctx -> ctx.out("result", Math.abs(ctx.<Double>in("value"))))
                .build());

        r.register(NodeType.builder(id("math/round"))
                .category(NodeCategories.MATH).pure()
                .in("value", PinTypes.DOUBLE, 0.0).out("result", PinTypes.INT)
                .action(ctx -> ctx.out("result", (int) Math.round(ctx.<Double>in("value"))))
                .build());

        // Aléatoire déterministe (PRD 7.2) : même graine + même index → même valeur.
        r.register(NodeType.builder(id("math/random"))
                .category(NodeCategories.MATH).pure().deterministic(false)
                .in("seed", PinTypes.LONG, 0L).in("index", PinTypes.INT, 0)
                .out("value", PinTypes.DOUBLE)
                .action(ctx -> ctx.out("value",
                        new Random(ctx.<Long>in("seed") * 31 + ctx.<Integer>in("index")).nextDouble()))
                .build());

        // ------------------------------------------------------ comparaisons (7.2)
        comparison(r, "logic/less", (a, b) -> a < b);
        comparison(r, "logic/less_eq", (a, b) -> a <= b);
        comparison(r, "logic/greater", (a, b) -> a > b);
        comparison(r, "logic/greater_eq", (a, b) -> a >= b);

        r.register(NodeType.builder(id("logic/equals"))
                .category(NodeCategories.LOGIC).pure()
                .in("a", PinTypes.ANY).in("b", PinTypes.ANY)
                .out("result", PinTypes.BOOL)
                .action(ctx -> ctx.out("result", Objects.equals(ctx.in("a"), ctx.in("b"))))
                .build());

        r.register(NodeType.builder(id("logic/not_equals"))
                .category(NodeCategories.LOGIC).pure()
                .in("a", PinTypes.ANY).in("b", PinTypes.ANY)
                .out("result", PinTypes.BOOL)
                .action(ctx -> ctx.out("result", !Objects.equals(ctx.in("a"), ctx.in("b"))))
                .build());

        // ---------------------------------------------------------- booléens (7.2)
        r.register(NodeType.builder(id("logic/and"))
                .category(NodeCategories.LOGIC).pure()
                .in("a", PinTypes.BOOL, false).in("b", PinTypes.BOOL, false)
                .out("result", PinTypes.BOOL)
                .action(ctx -> ctx.out("result", ctx.<Boolean>in("a") && ctx.<Boolean>in("b")))
                .build());

        r.register(NodeType.builder(id("logic/or"))
                .category(NodeCategories.LOGIC).pure()
                .in("a", PinTypes.BOOL, false).in("b", PinTypes.BOOL, false)
                .out("result", PinTypes.BOOL)
                .action(ctx -> ctx.out("result", ctx.<Boolean>in("a") || ctx.<Boolean>in("b")))
                .build());

        r.register(NodeType.builder(id("logic/xor"))
                .category(NodeCategories.LOGIC).pure()
                .in("a", PinTypes.BOOL, false).in("b", PinTypes.BOOL, false)
                .out("result", PinTypes.BOOL)
                .action(ctx -> ctx.out("result", ctx.<Boolean>in("a") ^ ctx.<Boolean>in("b")))
                .build());

        r.register(NodeType.builder(id("logic/not"))
                .category(NodeCategories.LOGIC).pure()
                .in("value", PinTypes.BOOL, false).out("result", PinTypes.BOOL)
                .action(ctx -> ctx.out("result", !ctx.<Boolean>in("value")))
                .build());

        // ----------------------------------------------------------- chaînes (7.2)
        r.register(NodeType.builder(id("string/concat"))
                .category(NodeCategories.STRING).pure()
                .in("a", PinTypes.STRING, "").in("b", PinTypes.STRING, "")
                .out("result", PinTypes.STRING)
                .action(ctx -> ctx.out("result", ctx.<String>in("a") + ctx.<String>in("b")))
                .build());

        r.register(NodeType.builder(id("string/length"))
                .category(NodeCategories.STRING).pure()
                .in("value", PinTypes.STRING, "").out("result", PinTypes.INT)
                .action(ctx -> ctx.out("result", ctx.<String>in("value").length()))
                .build());

        r.register(NodeType.builder(id("string/contains"))
                .category(NodeCategories.STRING).pure()
                .in("value", PinTypes.STRING, "").in("search", PinTypes.STRING, "")
                .out("result", PinTypes.BOOL)
                .action(ctx -> ctx.out("result", ctx.<String>in("value").contains(ctx.in("search"))))
                .build());

        r.register(NodeType.builder(id("string/upper"))
                .category(NodeCategories.STRING).pure()
                .in("value", PinTypes.STRING, "").out("result", PinTypes.STRING)
                .action(ctx -> ctx.out("result", ctx.<String>in("value").toUpperCase(java.util.Locale.ROOT)))
                .build());

        r.register(NodeType.builder(id("string/lower"))
                .category(NodeCategories.STRING).pure()
                .in("value", PinTypes.STRING, "").out("result", PinTypes.STRING)
                .action(ctx -> ctx.out("result", ctx.<String>in("value").toLowerCase(java.util.Locale.ROOT)))
                .build());

        // ------------------------------------------------------- conversions (7.2)
        r.register(NodeType.builder(id("convert/to_string"))
                .category(NodeCategories.STRING).pure()
                .in("value", PinTypes.ANY).out("result", PinTypes.STRING)
                .action(ctx -> ctx.out("result", String.valueOf((Object) ctx.in("value"))))
                .build());

        r.register(NodeType.builder(id("convert/to_int"))
                .category(NodeCategories.MATH).pure()
                .in("value", PinTypes.DOUBLE, 0.0).out("result", PinTypes.INT)
                .action(ctx -> ctx.out("result", (int) Math.floor(ctx.<Double>in("value"))))
                .build());

        // ----------------------------------------------------------- débogage (7.2)
        r.register(NodeType.builder(id("debug/log"))
                .category(NodeCategories.DEBUG)
                .exec()
                .in("value", PinTypes.ANY)
                .action(ctx -> ctx.logger().info("[{}] {}", ctx.blueprint().id(),
                        String.valueOf((Object) ctx.in("value"))))
                .build());
    }

    private static void binaryMath(NodeRegistry r, String path, DoubleBinaryOperator op) {
        r.register(NodeType.builder(id(path))
                .category(NodeCategories.MATH).pure()
                .in("a", PinTypes.DOUBLE, 0.0).in("b", PinTypes.DOUBLE, 0.0)
                .out("result", PinTypes.DOUBLE)
                .action(ctx -> ctx.out("result",
                        op.applyAsDouble(ctx.<Double>in("a"), ctx.<Double>in("b"))))
                .build());
    }

    private static void comparison(NodeRegistry r, String path, DoubleComparison op) {
        r.register(NodeType.builder(id(path))
                .category(NodeCategories.LOGIC).pure()
                .in("a", PinTypes.DOUBLE, 0.0).in("b", PinTypes.DOUBLE, 0.0)
                .out("result", PinTypes.BOOL)
                .action(ctx -> ctx.out("result", op.test(ctx.<Double>in("a"), ctx.<Double>in("b"))))
                .build());
    }

    @FunctionalInterface
    private interface DoubleComparison {
        boolean test(double a, double b);
    }
}
