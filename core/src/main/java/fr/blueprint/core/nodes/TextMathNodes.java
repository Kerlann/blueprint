package fr.blueprint.core.nodes;

import fr.blueprint.api.node.NodeCategories;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.api.registry.NodeRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleUnaryOperator;

/**
 * Le complément chaînes et maths (batch 6).
 *
 * <p>Six nœuds de chaînes et onze de maths ne suffisaient pas à écrire un script :
 * on pouvait concaténer sans jamais découper, comparer sans jamais borner, et lire
 * un message de chat sans pouvoir en extraire un mot.
 *
 * <p>Tous purs. Les opérations de chaîne travaillent en {@link Locale#ROOT} : la
 * casse d'un nom de bloc ne doit pas dépendre de la langue du serveur — en turc,
 * {@code "I".toLowerCase()} rend « ı », et {@code minecraft:STONE} deviendrait
 * introuvable.
 */
public final class TextMathNodes {

    /** Longueur maximale d'une chaîne produite : borne la mémoire d'une boucle. */
    public static final int MAX_LENGTH = 32_768;
    /** Nombre maximal de morceaux rendus par une découpe. */
    public static final int MAX_PARTS = 1_024;

    private TextMathNodes() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("blueprint", path);
    }

    public static void register(NodeRegistry r) {
        strings(r);
        maths(r);
    }

    // ------------------------------------------------------------------- chaînes

    private static void strings(NodeRegistry r) {
        /*
         * Découper : de quoi lire une commande de chat (« /don pierre 64 ») ou une
         * charge utile de signal. Sans lui, tout ce qui arrive en texte est opaque.
         */
        // Tarifé 3, comme string/replace et les deux ponts d'un dictionnaire : le travail
        // parcourt le texte ENTIER et bâtit une liste dont la taille suit celle du texte,
        // là où le tarif est fixe. Relevé entre ×1,6 et ×4,0 au plafond d'entrée selon
        // l'exécution — le pic touchait le seuil et le faisait rougir par intermittence.
        r.register(NodeType.builder(id("string/split")).fuelCost(3)
                .category(NodeCategories.STRING_EDIT).pure()
                .in("text", PinTypes.STRING, "")
                .in("separator", PinTypes.STRING, " ")
                .out("parts", PinTypes.listOf(PinTypes.STRING))
                .action(ctx -> {
                    String text = str(ctx.in("text"));
                    String separator = str(ctx.in("separator"));
                    if (separator.isEmpty()) {
                        // Découper sur le vide rendrait un caractère par élément et
                        // ferait exploser la liste sur un texte long ; le refuser vaut
                        // mieux que de rendre 30 000 éléments en silence.
                        ctx.out("parts", List.of(text));
                        return;
                    }
                    List<String> parts = new ArrayList<>();
                    int from = 0;
                    while (parts.size() < MAX_PARTS) {
                        int at = text.indexOf(separator, from);
                        if (at < 0) {
                            parts.add(text.substring(from));
                            break;
                        }
                        parts.add(text.substring(from, at));
                        from = at + separator.length();
                    }
                    ctx.out("parts", List.copyOf(parts));
                })
                .build());

        // Quatre fois l'unité au pire cas borné (FuelCalibrationTest) : mille vingt-quatre
        // morceaux assemblés jusqu'au plafond de longueur.
        r.register(NodeType.builder(id("string/join"))
                .category(NodeCategories.STRING_EDIT).pure().fuelCost(4)
                .in("parts", PinTypes.listOf(PinTypes.STRING))
                .in("separator", PinTypes.STRING, " ")
                .out("text", PinTypes.STRING)
                .action(ctx -> {
                    StringBuilder out = new StringBuilder();
                    String separator = str(ctx.in("separator"));
                    List<?> parts = ctx.in("parts") instanceof List<?> l ? l : List.of();
                    for (int i = 0; i < parts.size() && out.length() < MAX_LENGTH; i++) {
                        if (i > 0) {
                            out.append(separator);
                        }
                        out.append(parts.get(i));
                    }
                    ctx.out("text", clamp(out.toString()));
                })
                .build());

        // Tarifé 3 comme map/keys et map/values, et pour la même raison : le travail suit
        // la LONGUEUR du texte, là où le tarif est fixe. Le banc le mesure au plafond
        // d'entrée — trente-deux mille caractères — entre ×2,4 et ×4,0 selon l'exécution,
        // quand string/substring et string/concat, à coût constant, restent sous l'unité.
        // Trois couvre la bande relevée sans la surfacturer sur son pic.
        r.register(NodeType.builder(id("string/replace")).fuelCost(3)
                .category(NodeCategories.STRING_EDIT).pure()
                .in("text", PinTypes.STRING, "")
                .in("search", PinTypes.STRING, "")
                .in("replacement", PinTypes.STRING, "")
                .out("text", PinTypes.STRING)
                .action(ctx -> {
                    String search = str(ctx.in("search"));
                    String text = str(ctx.in("text"));
                    // Remplacer le vide insérerait entre chaque caractère : sans effet
                    // utile et coûteux. On rend le texte intact.
                    ctx.out("text", search.isEmpty() ? clamp(text)
                            : replaceBounded(text, search, str(ctx.in("replacement"))));
                })
                .build());

        /*
         * Extraire. Les bornes sont RAMENÉES au texte plutôt que de lever : un index
         * calculé par un graphe sort facilement, et une faute d'exécution pour un
         * « à partir du caractère 50 » sur un mot de trois lettres n'aide personne.
         */
        r.register(NodeType.builder(id("string/substring"))
                .category(NodeCategories.STRING_EDIT).pure()
                .in("text", PinTypes.STRING, "")
                .in("from", PinTypes.INT, 0)
                .in("length", PinTypes.INT, 1)
                .out("text", PinTypes.STRING)
                .action(ctx -> {
                    String text = str(ctx.in("text"));
                    int from = Math.clamp(ctx.<Integer>in("from"), 0, text.length());
                    int to = Math.clamp((long) from + Math.max(0, ctx.<Integer>in("length")),
                            from, text.length());
                    ctx.out("text", text.substring(from, to));
                })
                .build());

        r.register(NodeType.builder(id("string/trim"))
                .category(NodeCategories.STRING_EDIT).pure()
                .in("text", PinTypes.STRING, "").out("text", PinTypes.STRING)
                .action(ctx -> ctx.out("text", str(ctx.in("text")).strip()))
                .build());

        r.register(NodeType.builder(id("string/starts_with"))
                .category(NodeCategories.STRING_QUERY).pure()
                .in("text", PinTypes.STRING, "").in("prefix", PinTypes.STRING, "")
                .out("result", PinTypes.BOOL)
                .action(ctx -> ctx.out("result",
                        str(ctx.in("text")).startsWith(str(ctx.in("prefix")))))
                .build());

        r.register(NodeType.builder(id("string/ends_with"))
                .category(NodeCategories.STRING_QUERY).pure()
                .in("text", PinTypes.STRING, "").in("suffix", PinTypes.STRING, "")
                .out("result", PinTypes.BOOL)
                .action(ctx -> ctx.out("result",
                        str(ctx.in("text")).endsWith(str(ctx.in("suffix")))))
                .build());

        /*
         * Le retour du texte vers le nombre. « to_string » existait seul : un message
         * de chat ne pouvait jamais redevenir une quantité. « valide » plutôt qu'une
         * faute — une saisie de joueur est faillible par nature, et le graphe doit
         * pouvoir répondre poliment.
         */
        // Quatre fois l'unité au pire cas borné (FuelCalibrationTest) : l'analyse d'un
        // nombre parcourt toute la chaîne avant de conclure qu'elle n'en est pas un.
        r.register(NodeType.builder(id("convert/to_number"))
                .category(NodeCategories.STRING_EDIT).pure().fuelCost(4)
                .in("text", PinTypes.STRING, "")
                .out("value", PinTypes.DOUBLE)
                .out("valid", PinTypes.BOOL)
                .action(ctx -> {
                    try {
                        ctx.out("value", Double.parseDouble(str(ctx.in("text")).strip()));
                        ctx.out("valid", true);
                    } catch (NumberFormatException e) {
                        ctx.out("value", 0.0);
                        ctx.out("valid", false);
                    }
                })
                .build());
    }

    // --------------------------------------------------------------------- maths

    private static void maths(NodeRegistry r) {
        unary(r, "math/sqrt", NodeCategories.MATH_NUMERIC,
                value -> value < 0 ? Double.NaN : Math.sqrt(value));
        unary(r, "math/floor", NodeCategories.MATH_NUMERIC, Math::floor);
        unary(r, "math/ceil", NodeCategories.MATH_NUMERIC, Math::ceil);
        unary(r, "math/sign", NodeCategories.MATH_NUMERIC, Math::signum);
        unary(r, "math/sin", NodeCategories.MATH_TRIG, Math::sin);
        unary(r, "math/cos", NodeCategories.MATH_TRIG, Math::cos);

        r.register(NodeType.builder(id("math/pow"))
                .category(NodeCategories.MATH_NUMERIC).pure()
                .in("base", PinTypes.DOUBLE, 1.0).in("exponent", PinTypes.DOUBLE, 2.0)
                .out("result", PinTypes.DOUBLE)
                .action(ctx -> ctx.out("result",
                        Math.pow(ctx.<Double>in("base"), ctx.<Double>in("exponent"))))
                .build());

        /*
         * Borner. C'est l'opération la plus courante d'un script — une vie, un
         * compteur, un rayon — et elle demandait un min ET un max imbriqués.
         * Des bornes inversées sont RÉORDONNÉES : Math.clamp lèverait, et l'auteur
         * d'un graphe ne saurait pas d'où vient l'exception.
         */
        r.register(NodeType.builder(id("math/clamp"))
                .category(NodeCategories.MATH_NUMERIC).pure()
                .in("value", PinTypes.DOUBLE, 0.0)
                .in("min", PinTypes.DOUBLE, 0.0)
                .in("max", PinTypes.DOUBLE, 1.0)
                .out("result", PinTypes.DOUBLE)
                .action(ctx -> {
                    double low = ctx.in("min");
                    double high = ctx.in("max");
                    ctx.out("result", Math.clamp(ctx.<Double>in("value"),
                            Math.min(low, high), Math.max(low, high)));
                })
                .build());

        /* Interpoler : tout ce qui bouge progressivement (une barre, une position). */
        r.register(NodeType.builder(id("math/lerp"))
                .category(NodeCategories.MATH_NUMERIC).pure()
                .in("from", PinTypes.DOUBLE, 0.0)
                .in("to", PinTypes.DOUBLE, 1.0)
                .in("t", PinTypes.DOUBLE, 0.5)
                .out("result", PinTypes.DOUBLE)
                .action(ctx -> {
                    double from = ctx.in("from");
                    double to = ctx.in("to");
                    ctx.out("result", from + (to - from) * ctx.<Double>in("t"));
                })
                .build());

        r.register(NodeType.builder(id("math/atan2"))
                .category(NodeCategories.MATH_TRIG).pure()
                .in("y", PinTypes.DOUBLE, 0.0).in("x", PinTypes.DOUBLE, 1.0)
                .out("angle", PinTypes.DOUBLE)
                .action(ctx -> ctx.out("angle",
                        Math.atan2(ctx.<Double>in("y"), ctx.<Double>in("x"))))
                .build());
    }

    /** Gabarit un-vers-un. La catégorie est un paramètre : sqrt et sin ne se rangent
     * pas au même endroit, et un gabarit partagé qui impose sa catégorie mentirait. */
    private static void unary(NodeRegistry r, String path,
                              fr.blueprint.api.node.NodeCategory category,
                              DoubleUnaryOperator op) {
        r.register(NodeType.builder(id(path))
                .category(category).pure()
                .in("value", PinTypes.DOUBLE, 0.0).out("result", PinTypes.DOUBLE)
                .action(ctx -> {
                    double result = op.applyAsDouble(ctx.<Double>in("value"));
                    if (Double.isNaN(result)) {
                        // Un NaN empoisonne tout ce qu'il touche, sans erreur : une
                        // racine de négatif doit se dire, pas se propager.
                        ctx.fail(Component.translatable("blueprint.fault.not_a_number"));
                        return;
                    }
                    ctx.out("result", result);
                })
                .build());
    }

    // ------------------------------------------------------------------ outillage

    private static String str(Object value) {
        return value instanceof String s ? s : "";
    }

    static String clamp(String text) {
        return text.length() <= MAX_LENGTH ? text : text.substring(0, MAX_LENGTH);
    }

    /**
     * Concaténation bornée — visible du paquet parce que {@code string/concat} vit dans
     * {@link StandardNodes} et doit obéir à la même règle que le reste de la famille.
     *
     * <p>Coupe <b>avant</b> de construire : {@code clamp(a + b)} aurait d'abord alloué la
     * chaîne entière pour n'en garder que le début, ce qui laisse un graphe doubler sa
     * chaîne à chaque tour de boucle jusqu'à épuiser le tas.
     */
    static String concat(String a, String b) {
        if ((long) a.length() + b.length() <= MAX_LENGTH) {
            return a + b;
        }
        if (a.length() >= MAX_LENGTH) {
            return a.substring(0, MAX_LENGTH);
        }
        return a + b.substring(0, MAX_LENGTH - a.length());
    }

    /**
     * Remplacement borné : la taille du résultat est <b>prévue</b> avant d'être produite.
     *
     * <p>{@code clamp(text.replace(...))} tronquait après coup — donc après avoir alloué le
     * résultat entier. Avec un texte au plafond, un motif d'un caractère et un remplacement
     * au plafond, cela demandait de l'ordre du gigaoctet pour n'en conserver que 32 Ko.
     */
    static String replaceBounded(String text, String search, String replacement) {
        if (search.isEmpty()) {
            return clamp(text);
        }
        StringBuilder out = new StringBuilder(Math.min(text.length(), MAX_LENGTH));
        int from = 0;
        while (out.length() < MAX_LENGTH) {
            int hit = text.indexOf(search, from);
            if (hit < 0) {
                out.append(text, from, text.length());
                break;
            }
            out.append(text, from, hit).append(replacement);
            from = hit + search.length();
        }
        return clamp(out.toString());
    }
}
