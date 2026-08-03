package fr.blueprint.core.nodes;

import fr.blueprint.api.node.NodeCategories;
import fr.blueprint.api.node.NodeCategory;
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
                .category(NodeCategories.FLOW_BRANCH)
                .execIn("exec_in").execOut("true").execOut("false")
                .in("condition", PinTypes.BOOL, false)
                .action(ctx -> ctx.exec(ctx.<Boolean>in("condition") ? "true" : "false"))
                .build());

        r.register(NodeType.builder(id("flow/select"))
                .category(NodeCategories.FLOW_BRANCH)
                .pure().generic("T")
                .in("condition", PinTypes.BOOL, false)
                .in("if_true", PinTypes.generic("T"))
                .in("if_false", PinTypes.generic("T"))
                .out("value", PinTypes.generic("T"))
                .action(ctx -> ctx.out("value",
                        ctx.<Boolean>in("condition") ? ctx.in("if_true") : ctx.in("if_false")))
                .build());

        r.register(NodeType.builder(id("flow/wait"))
                .category(NodeCategories.FLOW_LOOP)
                .exec()
                .in("ticks", PinTypes.INT, 20)
                .action(ctx -> ctx.suspend(Math.max(1, ctx.<Integer>in("ticks"))))
                .build());

        r.register(NodeType.builder(id("flow/return"))
                .category(NodeCategories.FLOW_BRANCH)
                .execIn("exec_in")
                .action(ctx -> {
                })
                .build());

        // ------------------------------------------------------------- maths (7.2)
        binaryMath(r, "math/add", NodeCategories.MATH_ARITHMETIC, Double::sum);
        binaryMath(r, "math/sub", NodeCategories.MATH_ARITHMETIC, (a, b) -> a - b);
        binaryMath(r, "math/mul", NodeCategories.MATH_ARITHMETIC, (a, b) -> a * b);
        binaryMath(r, "math/min", NodeCategories.MATH_FUNCTION, Math::min);
        binaryMath(r, "math/max", NodeCategories.MATH_FUNCTION, Math::max);

        r.register(NodeType.builder(id("math/div"))
                .category(NodeCategories.MATH_ARITHMETIC).pure()
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
                .category(NodeCategories.MATH_ARITHMETIC).pure()
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
                .category(NodeCategories.MATH_FUNCTION).pure()
                .in("value", PinTypes.DOUBLE, 0.0).out("result", PinTypes.DOUBLE)
                .action(ctx -> ctx.out("result", Math.abs(ctx.<Double>in("value"))))
                .build());

        r.register(NodeType.builder(id("math/round"))
                .category(NodeCategories.MATH_FUNCTION).pure()
                .in("value", PinTypes.DOUBLE, 0.0).out("result", PinTypes.INT)
                .action(ctx -> ctx.out("result", (int) Math.round(ctx.<Double>in("value"))))
                .build());

        // Aléatoire déterministe (PRD 7.2) : même graine + même index → même valeur.
        // Mélange doré (correction QA RAND-001) : seed*31+index rendait (1,0) et (0,31)
        // identiques — inacceptable pour un nœud vendu « déterministe à graine ».
        r.register(NodeType.builder(id("math/random"))
                .category(NodeCategories.MATH_FUNCTION).pure().deterministic(false)
                .in("seed", PinTypes.LONG, 0L).in("index", PinTypes.INT, 0)
                .out("value", PinTypes.DOUBLE)
                .action(ctx -> ctx.out("value", new Random(
                        ctx.<Long>in("seed") ^ (ctx.<Integer>in("index") * 0x9E3779B97F4A7C15L))
                        .nextDouble()))
                .build());

        // ------------------------------------------------------ comparaisons (7.2)
        comparison(r, "logic/less", (a, b) -> a < b);
        comparison(r, "logic/less_eq", (a, b) -> a <= b);
        comparison(r, "logic/greater", (a, b) -> a > b);
        comparison(r, "logic/greater_eq", (a, b) -> a >= b);

        r.register(NodeType.builder(id("logic/equals"))
                .category(NodeCategories.LOGIC_COMPARE).pure()
                .in("a", PinTypes.ANY).in("b", PinTypes.ANY)
                .out("result", PinTypes.BOOL)
                .action(ctx -> ctx.out("result", Objects.equals(ctx.in("a"), ctx.in("b"))))
                .build());

        r.register(NodeType.builder(id("logic/not_equals"))
                .category(NodeCategories.LOGIC_COMPARE).pure()
                .in("a", PinTypes.ANY).in("b", PinTypes.ANY)
                .out("result", PinTypes.BOOL)
                .action(ctx -> ctx.out("result", !Objects.equals(ctx.in("a"), ctx.in("b"))))
                .build());

        // ---------------------------------------------------------- booléens (7.2)
        r.register(NodeType.builder(id("logic/and"))
                .category(NodeCategories.LOGIC_BOOLEAN).pure()
                .in("a", PinTypes.BOOL, false).in("b", PinTypes.BOOL, false)
                .out("result", PinTypes.BOOL)
                .action(ctx -> ctx.out("result", ctx.<Boolean>in("a") && ctx.<Boolean>in("b")))
                .build());

        r.register(NodeType.builder(id("logic/or"))
                .category(NodeCategories.LOGIC_BOOLEAN).pure()
                .in("a", PinTypes.BOOL, false).in("b", PinTypes.BOOL, false)
                .out("result", PinTypes.BOOL)
                .action(ctx -> ctx.out("result", ctx.<Boolean>in("a") || ctx.<Boolean>in("b")))
                .build());

        r.register(NodeType.builder(id("logic/xor"))
                .category(NodeCategories.LOGIC_BOOLEAN).pure()
                .in("a", PinTypes.BOOL, false).in("b", PinTypes.BOOL, false)
                .out("result", PinTypes.BOOL)
                .action(ctx -> ctx.out("result", ctx.<Boolean>in("a") ^ ctx.<Boolean>in("b")))
                .build());

        r.register(NodeType.builder(id("logic/not"))
                .category(NodeCategories.LOGIC_BOOLEAN).pure()
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
                .category(NodeCategories.MATH_FUNCTION).pure()
                .in("value", PinTypes.DOUBLE, 0.0).out("result", PinTypes.INT)
                .action(ctx -> ctx.out("result", (int) Math.floor(ctx.<Double>in("value"))))
                .build());

        // ------------------------------------------------- joueur (avant-goût 7.4)
        r.register(NodeType.builder(id("player/send_message"))
                .category(NodeCategories.PLAYER)
                .permission(fr.blueprint.api.node.Permission.GAMEPLAY)
                .exec()
                .in("player", PinTypes.PLAYER)
                .in("text", PinTypes.STRING, "")
                .action(ctx -> ctx.<net.minecraft.server.level.ServerPlayer>in("player")
                        .sendSystemMessage(Component.literal(ctx.in("text"))))
                .build());

        // ----------------------------------------------------------- débogage (7.2)
        r.register(NodeType.builder(id("debug/log"))
                .category(NodeCategories.DEBUG)
                .exec()
                .in("value", PinTypes.ANY)
                .action(ctx -> ctx.logger().info("[{}] {}", ctx.blueprint().id(),
                        String.valueOf((Object) ctx.in("value"))))
                .build());

        // ---------------------------------------------------------- variables (5.5)
        // Pas d'action réelle : le compilateur les abaisse en LoadVar/StoreVar
        // (VarNodes) ; atteindre l'action serait un bogue de compilation.
        r.register(NodeType.builder(id("var/get"))
                .category(NodeCategories.MISC)
                .pure()
                .in("var", PinTypes.STRING, "")
                .out("value", PinTypes.ANY)
                .action(ctx -> {
                    throw new IllegalStateException("var/get est abaissé en LoadVar par le compilateur");
                })
                .build());

        r.register(NodeType.builder(id("var/set"))
                .category(NodeCategories.MISC)
                .exec()
                .in("var", PinTypes.STRING, "")
                .in("value", PinTypes.ANY)
                .action(ctx -> {
                    throw new IllegalStateException("var/set est abaissé en StoreVar par le compilateur");
                })
                .build());

        // ------------------------------------------------- flux structuré (7.1b)
        // Abaissés par le compilateur (CallSub/JmpIf/Yield + Calls synthétisés) :
        // leurs actions ne doivent jamais être atteintes.
        r.register(NodeType.builder(id("flow/sequence"))
                .category(NodeCategories.FLOW_BRANCH)
                .execIn("exec_in")
                .execOut("then_1").execOut("then_2").execOut("then_3").execOut("then_4")
                .action(ctx -> {
                    throw new IllegalStateException("flow/sequence est abaissé par le compilateur");
                })
                .build());

        r.register(NodeType.builder(id("flow/while"))
                .category(NodeCategories.FLOW_LOOP)
                .execIn("exec_in").execOut("body").execOut("completed")
                .in("condition", PinTypes.BOOL, false)
                .action(ctx -> {
                    throw new IllegalStateException("flow/while est abaissé par le compilateur");
                })
                .build());

        r.register(NodeType.builder(id("flow/for"))
                .category(NodeCategories.FLOW_LOOP)
                .execIn("exec_in").execOut("body").execOut("completed")
                .in("first", PinTypes.INT, 1)
                .in("last", PinTypes.INT, 10)
                .out("index", PinTypes.DOUBLE)
                .action(ctx -> {
                    throw new IllegalStateException("flow/for est abaissé par le compilateur");
                })
                .build());

        r.register(NodeType.builder(id("flow/wait_until"))
                .category(NodeCategories.FLOW_LOOP)
                .exec()
                .in("condition", PinTypes.BOOL, false)
                .action(ctx -> {
                    throw new IllegalStateException("flow/wait_until est abaissé par le compilateur");
                })
                .build());

        // for_each (7.8) : parcourt une liste. Abaissé comme « for », avec la taille et
        // l'accès fournis par les nœuds de liste synthétisés par le compilateur.
        r.register(NodeType.builder(id("flow/for_each"))
                .category(NodeCategories.FLOW_LOOP)
                .generic("T")
                .execIn("exec_in").execOut("body").execOut("completed")
                .in("list", PinTypes.listOf(PinTypes.generic("T")))
                .out("element", PinTypes.generic("T"))
                .out("index", PinTypes.INT)
                .action(ctx -> {
                    throw new IllegalStateException("flow/for_each est abaissé par le compilateur");
                })
                .build());

        // gate (7.8) : un portail qui SE SOUVIENT. Trois entrées d'exécution — c'est ce
        // qui le distingue d'un simple branchement, et ce qui a demandé au compilateur
        // de savoir par quel pin on entre dans un nœud.
        r.register(NodeType.builder(id("flow/gate"))
                .category(NodeCategories.FLOW_BRANCH)
                .execIn("enter").execIn("open").execIn("close")
                .execOut("exit")
                .action(ctx -> {
                    throw new IllegalStateException("flow/gate est abaissé par le compilateur");
                })
                .build());

        r.register(NodeType.builder(id("flow/do_once"))
                .category(NodeCategories.FLOW_BRANCH)
                .exec()
                .action(ctx -> {
                    throw new IllegalStateException("flow/do_once est abaissé par le compilateur");
                })
                .build());

        r.register(NodeType.builder(id("flow/switch"))
                .category(NodeCategories.FLOW_BRANCH)
                .execIn("exec_in")
                .execOut("case_0").execOut("case_1").execOut("case_2").execOut("case_3")
                .execOut("default")
                .in("value", PinTypes.INT, 0)
                .action(ctx -> {
                    int value = ctx.in("value");
                    ctx.exec(value >= 0 && value <= 3 ? "case_" + value : "default");
                })
                .build());

        // ------------------------------------------------ événement command (7.7)
        // Enregistré À LA MAIN (la synthèse le saute par collision) : c'est le seul
        // nœud d'événement avec une ENTRÉE — le littéral « name » déclare la commande.
        r.register(NodeType.builder(
                        fr.blueprint.core.event.StandardEvents.COMMAND.id())
                .category(NodeCategories.EVENT_SERVER)
                .entryPoint()
                .titleKey(fr.blueprint.core.event.StandardEvents.COMMAND.titleKey())
                .execOut("exec_out")
                .in("name", PinTypes.STRING, "")
                .out("player", PinTypes.PLAYER)
                .out("name", PinTypes.STRING)
                .out("arg", PinTypes.STRING)
                .action(ctx -> {
                    for (var out : fr.blueprint.core.event.StandardEvents.COMMAND.outputs()) {
                        Object value = ctx.trigger().output(out.name());
                        if (value != null) {
                            ctx.out(out.name(), value);
                        }
                    }
                })
                .build());

        // ------------------------------------------------- signaux (batch 1, 5.15)
        // Même forme que command, et pour la même raison : le littéral « name »
        // déclare le signal ÉCOUTÉ, donc le nœud porte une entrée et échappe à la
        // synthèse. Sans cela, tout signal réveillerait tous les nœuds signal et
        // l'auteur devrait filtrer à la main à chaque fois.
        r.register(NodeType.builder(
                        fr.blueprint.core.event.StandardEvents.SIGNAL.id())
                .category(NodeCategories.EVENT_SERVER)
                .entryPoint()
                .titleKey(fr.blueprint.core.event.StandardEvents.SIGNAL.titleKey())
                .execOut("exec_out")
                .in("name", PinTypes.STRING, "")
                .out("payload", PinTypes.STRING)
                .action(ctx -> {
                    Object payload = ctx.trigger().output("payload");
                    ctx.out("payload", payload == null ? "" : payload);
                })
                .build());

        // L'émetteur. C'est LA primitive « un blueprint en appelle un autre » :
        // l'événement signal existait depuis la 7.6 et rien au monde ne le
        // déclenchait — un point d'entrée mort, qu'on pouvait poser et câbler.
        r.register(NodeType.builder(id("signal/emit"))
                .category(NodeCategories.EVENT_SERVER).exec()
                .in("name", PinTypes.STRING, "")
                .in("payload", PinTypes.STRING, "")
                .action(ctx -> {
                    String name = ctx.in("name");
                    if (name == null || name.isBlank()) {
                        ctx.fail(Component.translatable("blueprint.fault.signal_unnamed"));
                        return;
                    }
                    String payload = ctx.in("payload");
                    if (!fr.blueprint.core.BlueprintMod.emitSignal(
                            ctx.server(), name, payload == null ? "" : payload)) {
                        ctx.fail(Component.translatable("blueprint.fault.signal_budget",
                                fr.blueprint.core.event.BlueprintEventBridge.MAX_SIGNALS_PER_TICK));
                    }
                })
                .build());

        // ----------------------------------------- monde, entités, items (7.3-7.5)
        ListNodes.register(r);
        VectorNodes.register(r);
        QueryNodes.register(r);
        WorldNodes.register(r);
        EntityNodes.register(r);
        ItemNodes.register(r);
    }

    /**
     * Nœud binaire sur deux nombres. La catégorie est un paramètre : le même gabarit
     * sert aux opérations ({@code add}, {@code sub}, {@code mul}) et aux fonctions
     * ({@code min}, {@code max}), qui ne se rangent pas au même endroit.
     */
    private static void binaryMath(NodeRegistry r, String path, NodeCategory category,
                                   DoubleBinaryOperator op) {
        r.register(NodeType.builder(id(path))
                .category(category).pure()
                .in("a", PinTypes.DOUBLE, 0.0).in("b", PinTypes.DOUBLE, 0.0)
                .out("result", PinTypes.DOUBLE)
                .action(ctx -> ctx.out("result",
                        op.applyAsDouble(ctx.<Double>in("a"), ctx.<Double>in("b"))))
                .build());
    }

    private static void comparison(NodeRegistry r, String path, DoubleComparison op) {
        r.register(NodeType.builder(id(path))
                .category(NodeCategories.LOGIC_COMPARE).pure()
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
