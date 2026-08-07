package fr.blueprint.core.script;

import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinKind;
import fr.blueprint.api.pin.PinType;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.BlueprintMeta;
import fr.blueprint.core.graph.CommentBox;
import fr.blueprint.core.graph.GhostNode;
import fr.blueprint.core.graph.GraphLoader;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.Node;
import fr.blueprint.core.graph.NodeShape;
import fr.blueprint.core.graph.NodeTypeLookup;
import fr.blueprint.core.graph.VarScope;
import fr.blueprint.core.graph.Variable;
import fr.blueprint.core.graph.Vec2d;
import fr.blueprint.core.graph.screen.Anchor;
import fr.blueprint.core.graph.screen.ElementKind;
import fr.blueprint.core.graph.screen.ElementStyle;
import fr.blueprint.core.graph.screen.Extent;
import fr.blueprint.core.graph.screen.LayoutSpec;
import fr.blueprint.core.graph.screen.Screen;
import fr.blueprint.core.graph.screen.ScreenElement;
import fr.blueprint.core.graph.screen.ScreenText;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Parsing BScript v1 (story 4.3). Erreur de syntaxe → résultat d'échec avec ligne et
 * message, jamais d'exception ; nœud inconnu → fantôme (spec §7) ; {@code @id} absent →
 * UUID généré ; {@code @pos} absente → mise en page automatique déterministe.
 * Construction via {@link GraphLoader} : le chargement ne refuse rien (P4).
 */
public final class ScriptParser {

    public record ParseResult(@Nullable Blueprint blueprint, @Nullable String error) {
        public boolean success() {
            return blueprint != null;
        }
    }

    private static final class ParseError extends RuntimeException {
        final int line;

        ParseError(int line, String message) {
            super(message);
            this.line = line;
        }
    }

    // ------------------------------------------------------------------- lexer

    private record Token(String kind, String text, int line) {
    }

    private static List<Token> lex(String source) {
        List<Token> tokens = new ArrayList<>();
        int line = 1;
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == '\n') {
                line++;
                i++;
            } else if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '"') {
                StringBuilder sb = new StringBuilder();
                i++;
                while (i < source.length() && source.charAt(i) != '"') {
                    char ch = source.charAt(i);
                    if (ch == '\\' && i + 1 < source.length()) {
                        char next = source.charAt(++i);
                        sb.append(switch (next) {
                            case 'n' -> '\n';
                            case 'r' -> '\r';
                            case 't' -> '\t';
                            default -> next;
                        });
                    } else {
                        if (ch == '\n') {
                            throw new ParseError(line, "chaîne non terminée");
                        }
                        sb.append(ch);
                    }
                    i++;
                }
                if (i >= source.length()) {
                    throw new ParseError(line, "chaîne non terminée");
                }
                i++;
                tokens.add(new Token("string", sb.toString(), line));
            } else if (Character.isDigit(c) || (c == '-' && i + 1 < source.length()
                    && Character.isDigit(source.charAt(i + 1)))) {
                int start = i;
                i++;
                while (i < source.length() && (Character.isDigit(source.charAt(i))
                        || source.charAt(i) == '.')) {
                    i++;
                }
                tokens.add(new Token("number", source.substring(start, i), line));
            } else if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < source.length() && (Character.isLetterOrDigit(source.charAt(i))
                        || "_/-".indexOf(source.charAt(i)) >= 0)) {
                    i++;
                }
                tokens.add(new Token("word", source.substring(start, i), line));
            } else if ("{}():,@$<>=#.[]%".indexOf(c) >= 0) {
                tokens.add(new Token("sym", String.valueOf(c), line));
                i++;
            } else {
                throw new ParseError(line, "caractère inattendu « " + c + " »");
            }
        }
        tokens.add(new Token("eof", "", line));
        return tokens;
    }

    // ------------------------------------------------------------------ parseur

    private final List<Token> tokens;
    private final PluginLoader.LoadedRegistries registries;
    private final NodeTypeLookup lookup;
    private int position;
    private Blueprint bp;
    private final List<Link> links = new ArrayList<>();
    private final Map<String, UUID> labels = new HashMap<>();
    private Map<String, String> eventParams = Map.of();   // nom → pin de l'événement courant
    private UUID currentEvent;
    private int autoLayout;

    private ScriptParser(List<Token> tokens, PluginLoader.LoadedRegistries registries) {
        this.tokens = tokens;
        this.registries = registries;
        this.lookup = registries.nodes();
    }

    public static ParseResult parse(String source, PluginLoader.LoadedRegistries registries) {
        try {
            ScriptParser parser = new ScriptParser(lex(source), registries);
            parser.parseBlueprint();
            for (Link link : parser.links) {
                GraphLoader.addLink(parser.bp, link);
            }
            return new ParseResult(parser.bp, null);
        } catch (ParseError e) {
            return new ParseResult(null, "ligne " + e.line + " : " + e.getMessage());
        } catch (RuntimeException e) {
            return new ParseResult(null, "erreur de parsing : " + e.getMessage());
        }
    }

    private Token peek() {
        return tokens.get(position);
    }

    /** Le jeton à {@code offset} rangs devant ; le dernier (fin de fichier) au-delà. */
    private Token peekAt(int offset) {
        return tokens.get(Math.min(position + offset, tokens.size() - 1));
    }

    private Token next() {
        return tokens.get(position++);
    }

    private Token expect(String kind, String text) {
        Token token = next();
        if (!token.kind().equals(kind) || (text != null && !token.text().equals(text))) {
            throw new ParseError(token.line(), "attendu « " + (text != null ? text : kind)
                    + " », trouvé « " + token.text() + " »");
        }
        return token;
    }

    private boolean eat(String kind, String text) {
        Token token = peek();
        if (token.kind().equals(kind) && token.text().equals(text)) {
            position++;
            return true;
        }
        return false;
    }

    private void parseBlueprint() {
        expect("word", "blueprint");
        Identifier id = qualifiedId();
        bp = new Blueprint(id);
        expect("sym", "{");
        BlueprintMeta meta = BlueprintMeta.DEFAULT;
        while (!eat("sym", "}")) {
            Token token = peek();
            switch (token.text()) {
                case "meta" -> meta = parseMeta();
                case "var" -> parseVar();
                case "note" -> parseNote();
                case "screen" -> parseScreen();
                case "func" -> parseFunction();
                case "on" -> parseEvent();
                default -> throw new ParseError(token.line(),
                        "attendu meta/var/screen/func/note/on, trouvé « " + token.text() + " »");
            }
        }
        // setMeta est package-private : on reconstruit avec la meta définitive.
        Blueprint complete = new Blueprint(id, meta);
        bp.nodes().values().forEach(n -> GraphLoader.addNode(complete, n));
        bp.variables().values().forEach(v -> GraphLoader.addVariable(complete, v));
        bp.comments().forEach(c -> GraphLoader.addComment(complete, c));
        bp.screens().values().forEach(s -> GraphLoader.addScreen(complete, s));
        bp.functions().values().forEach(f -> GraphLoader.addFunction(complete, f));
        bp = complete;
    }

    private BlueprintMeta parseMeta() {
        expect("word", "meta");
        expect("sym", "{");
        String author = "";
        String description = "";
        String version = "1.0.0";
        Permission permission = Permission.GAMEPLAY;
        while (!eat("sym", "}")) {
            Token key = expect("word", null);
            switch (key.text()) {
                case "author" -> author = expect("string", null).text();
                case "description" -> description = expect("string", null).text();
                case "version" -> version = expect("string", null).text();
                case "permission" -> {
                    Token value = expect("word", null);
                    try {
                        permission = Permission.valueOf(value.text());
                    } catch (IllegalArgumentException e) {
                        throw new ParseError(value.line(), "permission inconnue « " + value.text() + " »");
                    }
                }
                default -> throw new ParseError(key.line(), "clé meta inconnue « " + key.text() + " »");
            }
        }
        return new BlueprintMeta(author, description, version, permission);
    }

    private void parseVar() {
        expect("word", "var");
        PinType type = parseType();
        String name = expect("word", null).text();
        LiteralValue defaultValue = null;
        if (eat("sym", "=")) {
            defaultValue = parseLiteralValue(type);
        }
        VarScope scope = VarScope.GRAPH;
        boolean replicated = false;
        while (eat("sym", "@")) {
            Token ann = expect("word", null);
            switch (ann.text()) {
                case "local" -> scope = VarScope.LOCAL;
                case "graph" -> scope = VarScope.GRAPH;
                case "world" -> scope = VarScope.WORLD;
                case "player" -> scope = VarScope.PLAYER;
                // Le partage entre blueprints se DÉCLARE. Il se subissait : deux graphes
                // portant chacun un « prenom » écrivaient au même endroit.
                case "player_shared" -> scope = VarScope.PLAYER_SHARED;
                case "replicated" -> replicated = true;
                default -> throw new ParseError(ann.line(), "annotation de variable inconnue @" + ann.text());
            }
        }
        GraphLoader.addVariable(bp, new Variable(name, type, defaultValue, scope, replicated));
    }

    private PinType parseType() {
        Token token = expect("word", null);
        String name = token.text();
        if (name.equals("list") || name.equals("map")) {
            expect("sym", "<");
            PinType first = parseType();
            if (name.equals("list")) {
                expect("sym", ">");
                return PinTypes.listOf(first);
            }
            expect("sym", ",");
            PinType second = parseType();
            expect("sym", ">");
            return PinTypes.mapOf(first, second);
        }
        Identifier id = eat("sym", ":")
                ? Identifier.fromNamespaceAndPath(name, expect("word", null).text())
                : Identifier.fromNamespaceAndPath("blueprint", name);
        PinType type = registries.pinTypes().get(id).orElse(null);
        if (type == null) {
            throw new ParseError(token.line(), "type de pin inconnu « " + id + " »");
        }
        return type;
    }

    private void parseNote() {
        expect("word", "note");
        String text = expect("string", null).text();
        Annotations anns = parseAnnotations();
        GraphLoader.addComment(bp, new CommentBox(anns.idOr(UUID.randomUUID()), text,
                anns.posOr(nextAutoPos()), anns.sizeOr(new Vec2d(120, 60)), anns.color));
    }

    // ------------------------------------------------------------------- écrans

    /**
     * Un écran (épic 10) : {@code screen "nom" @hud { … }}. Les éléments sont relus
     * dans l'ordre écrit, qui est <b>l'ordre de dessin</b> — jamais trié.
     *
     * <p>Contrairement au chargement NBT, qui ne refuse rien (P4), l'import texte
     * <i>refuse</i> les noms en double. Un fichier BScript est écrit à la main : deux
     * éléments du même nom y sont une faute d'auteur, et les accepter en silence
     * perdrait le premier des deux sans que personne ne le voie.
     */
    private void parseScreen() {
        Token keyword = expect("word", "screen");
        String name = expect("string", null).text();
        boolean hud = false;
        while (peek().kind().equals("sym") && peek().text().equals("@")) {
            next();
            Token ann = expect("word", null);
            if (!ann.text().equals("hud")) {
                throw new ParseError(ann.line(),
                        "annotation inconnue @" + ann.text() + " sur un écran");
            }
            hud = true;
        }
        if (bp.screen(name) != null) {
            throw new ParseError(keyword.line(), "écran « " + name + " » déjà défini");
        }
        expect("sym", "{");
        List<ScreenElement> elements = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        java.util.LinkedHashMap<String, ElementStyle> styles = new java.util.LinkedHashMap<>();
        while (!eat("sym", "}")) {
            // « styles » n'est pas un type d'élément, et un élément est toujours suivi
            // de son nom entre guillemets : la levée d'ambiguïté tient sur l'accolade.
            if (peek().kind().equals("word") && peek().text().equals("styles")
                    && peekAt(1).kind().equals("sym") && peekAt(1).text().equals("{")) {
                parseScreenStyles(styles);
                continue;
            }
            ScreenElement element = parseElement();
            if (!seen.add(element.name())) {
                throw new ParseError(peek().line(),
                        "élément « " + element.name() + " » déjà défini dans « " + name + " »");
            }
            elements.add(element);
        }
        try {
            GraphLoader.addScreen(bp, new Screen(name, hud, elements, styles));
        } catch (IllegalArgumentException e) {
            throw new ParseError(keyword.line(), e.getMessage());
        }
    }

    private ScreenElement parseElement() {
        Token kindToken = expect("word", null);
        ElementKind kind;
        try {
            kind = ElementKind.valueOf(kindToken.text().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ParseError(kindToken.line(),
                    "type d'élément inconnu « " + kindToken.text() + " »");
        }
        String name = expect("string", null).text();

        String parent = null;
        Anchor anchor = Anchor.TOP_LEFT;
        double x = 0;
        double y = 0;
        Extent width = Extent.of(ScreenElement.MIN_SIZE);
        Extent height = Extent.of(ScreenElement.MIN_SIZE);
        ScreenText text = ScreenText.EMPTY;
        ScreenText tooltip = ScreenText.EMPTY;
        Identifier texture = null;
        ElementStyle style = ElementStyle.DEFAULT;
        String styleName = "";
        LayoutSpec layout = LayoutSpec.ABSOLUTE;
        fr.blueprint.core.graph.screen.ElementBinding binding =
                fr.blueprint.core.graph.screen.ElementBinding.NONE;
        fr.blueprint.core.graph.screen.ElementOptions options =
                fr.blueprint.core.graph.screen.ElementOptions.NONE;
        boolean visible = true;
        boolean enabled = true;

        while (peek().kind().equals("sym") && peek().text().equals("@")) {
            next();
            Token ann = expect("word", null);
            switch (ann.text()) {
                case "in" -> {
                    expect("sym", "(");
                    parent = expect("string", null).text();
                    expect("sym", ")");
                }
                case "at" -> {
                    expect("sym", "(");
                    Token anchorToken = expect("word", null);
                    try {
                        anchor = Anchor.valueOf(anchorToken.text().toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        throw new ParseError(anchorToken.line(),
                                "ancre inconnue « " + anchorToken.text() + " »");
                    }
                    expect("sym", ",");
                    x = number(next());
                    expect("sym", ",");
                    y = number(next());
                    expect("sym", ")");
                }
                case "size" -> {
                    expect("sym", "(");
                    width = parseExtent();
                    expect("sym", ",");
                    height = parseExtent();
                    expect("sym", ")");
                }
                case "text" -> {
                    expect("sym", "(");
                    text = ScreenText.literal(expect("string", null).text());
                    expect("sym", ")");
                }
                case "key" -> {
                    expect("sym", "(");
                    text = ScreenText.key(expect("string", null).text());
                    expect("sym", ")");
                }
                case "tip" -> {
                    expect("sym", "(");
                    tooltip = ScreenText.literal(expect("string", null).text());
                    expect("sym", ")");
                }
                case "tipkey" -> {
                    expect("sym", "(");
                    tooltip = ScreenText.key(expect("string", null).text());
                    expect("sym", ")");
                }
                case "texture" -> {
                    expect("sym", "(");
                    Token raw = expect("string", null);
                    // La MÊME lecture que le générateur écrit : « ma_boutique/fond »
                    // désigne un pack, un identifiant complet une texture du jeu.
                    texture = fr.blueprint.core.graph.screen.PackRef.texture(raw.text());
                    if (texture == null) {
                        throw new ParseError(raw.line(),
                                "texture invalide « " + raw.text() + " »");
                    }
                    expect("sym", ")");
                }
                case "hidden" -> visible = false;
                case "disabled" -> enabled = false;
                case "layout" -> layout = parseLayout();
                case "bind" -> binding = parseBinding();
                case "opts" -> options = parseOptions();
                case "uses" -> {
                    expect("sym", "(");
                    styleName = expect("string", null).text();
                    expect("sym", ")");
                }
                case "style" -> style = parseStyle();
                default -> throw new ParseError(ann.line(),
                        "annotation inconnue @" + ann.text() + " sur un élément");
            }
        }
        try {
            return new ScreenElement(name, kind, parent, anchor, x, y, width, height,
                    text, tooltip, texture, style, styleName, layout, binding, options,
                    visible, enabled);
        } catch (IllegalArgumentException e) {
            throw new ParseError(kindToken.line(), e.getMessage());
        }
    }

    /**
     * {@code 80} pour une taille fixe, {@code 50%[100, 300]} pour une relative bornée,
     * {@code fill} / {@code fill:2} pour une part de la place restante, {@code hug} pour
     * un conteneur qui s'ajuste. Les bornes {@code [min, max]} valent pour tous.
     */
    private Extent parseExtent() {
        Token token = next();
        Extent.Mode mode;
        double value;
        if (token.kind().equals("word")) {
            switch (token.text()) {
                case "fill" -> {
                    mode = Extent.Mode.FILL;
                    value = eat("sym", ":") ? number(next()) : 1;
                }
                case "hug" -> {
                    mode = Extent.Mode.HUG;
                    value = 0;
                }
                default -> throw new ParseError(token.line(),
                        "taille inconnue « " + token.text() + " » (attendu : un nombre, "
                                + "un pourcentage, fill ou hug)");
            }
        } else if (eat("sym", "%")) {
            mode = Extent.Mode.PERCENT;
            // Décimale courte, comme à l'émission : diviser le double par 100 ne rendrait
            // pas la fraction d'origine (7 / 100 ≠ 0,07 au bit près).
            value = new java.math.BigDecimal(token.text()).movePointLeft(2).doubleValue();
        } else {
            mode = Extent.Mode.FIXED;
            value = number(token);
        }
        double min = 0;
        double max = 0;
        if (eat("sym", "[")) {
            min = number(next());
            expect("sym", ",");
            max = number(next());
            expect("sym", "]");
        }
        Extent.Mode builtMode = mode;
        double builtValue = value;
        double boundedMin = min;
        double boundedMax = max;
        return wrapExtent(token,
                () -> new Extent(builtMode, builtValue, boundedMin, boundedMax));
    }

    /**
     * {@code @bind("argent", text, format: "Or : %s", decimals: 0, min: 0, max: 100)} —
     * la variable et la cible sont obligatoires, le reste a des défauts.
     */
    /** La valeur client que ce nom désigne, ou {@code null} si c'est une variable. */
    private static @org.jetbrains.annotations.Nullable
            fr.blueprint.core.graph.screen.ClientValue clientValueOf(String name) {
        return name.startsWith(fr.blueprint.core.graph.screen.ClientValue.PREFIX)
                ? fr.blueprint.core.graph.screen.ClientValue.byKey(name) : null;
    }

    /** Le même nom sans son préfixe, s'il en portait un que le catalogue reconnaît. */
    private static String stripClientPrefix(String name) {
        var value = clientValueOf(name);
        return value == null ? name : value.key();
    }

    private fr.blueprint.core.graph.screen.ElementBinding parseBinding() {
        expect("sym", "(");
        String variable = expect("string", null).text();
        expect("sym", ",");
        Token targetToken = expect("word", null);
        // « @vie » désigne une valeur que le client possède déjà, jamais une variable.
        // La reconnaissance passe par le CATALOGUE et non par le seul préfixe : un nom
        // inconnu reste une variable, quitte à ce que le validateur la refuse ensuite —
        // le pire serait de transformer en source client une liaison qui n'en est pas
        // une et qui cesserait alors silencieusement d'être rafraîchie.
        var client = clientValueOf(variable);
        var binding = fr.blueprint.core.graph.screen.ElementBinding.NONE
                .withVariable(client == null ? variable : client.key())
                .withSource(client == null
                        ? fr.blueprint.core.graph.screen.ElementBinding.Source.VARIABLE
                        : fr.blueprint.core.graph.screen.ElementBinding.Source.CLIENT)
                .withTarget(enumOf(fr.blueprint.core.graph.screen.ElementBinding.Target.class,
                        targetToken, "cible de liaison"));
        while (eat("sym", ",")) {
            Token key = expect("word", null);
            expect("sym", ":");
            binding = switch (key.text()) {
                case "format" -> binding.withFormat(expect("string", null).text());
                case "decimals" -> binding.withDecimals((int) number(next()));
                case "min" -> binding.withRange(number(next()), binding.max());
                case "max" -> binding.withRange(binding.min(), number(next()));
                // Le préfixe « @ » se retire ici comme pour la variable principale : la
                // source est déjà décidée par elle, et une barre dont la valeur et le
                // maximum viendraient de deux côtés différents n'aurait aucun sens.
                case "maxVar" -> binding.withMaxVariable(
                        stripClientPrefix(expect("string", null).text()));
                default -> throw new ParseError(key.line(),
                        "réglage de liaison inconnu « " + key.text() + " »");
            };
        }
        expect("sym", ")");
        return binding;
    }

    /**
     * {@code @opts(placeholder: "Nom", maxLength: 16, filter: identifier)} — les réglages
     * propres au type (10.8). Tous optionnels, chacun avec son défaut.
     */
    private fr.blueprint.core.graph.screen.ElementOptions parseOptions() {
        expect("sym", "(");
        var options = fr.blueprint.core.graph.screen.ElementOptions.NONE;
        boolean first = true;
        while (first || eat("sym", ",")) {
            first = false;
            Token key = expect("word", null);
            expect("sym", ":");
            options = switch (key.text()) {
                case "placeholder" -> options.withPlaceholder(expect("string", null).text());
                case "maxLength" -> options.withMaxLength((int) number(next()));
                case "filter" -> options.withFilter(enumOf(
                        fr.blueprint.core.graph.screen.ElementOptions.InputFilter.class,
                        expect("word", null), "filtre de saisie"));
                case "min" -> options.withRange(number(next()), options.max());
                case "max" -> options.withRange(options.min(), number(next()));
                case "step" -> options.withStep(number(next()));
                case "rowHeight" -> options.withRowHeight(number(next()));
                case "entity" -> options.withEntity(
                        Identifier.tryParse(expect("string", null).text()));
                case "live" -> options.withLive(expect("word", null).text().equals("true"));
                default -> throw new ParseError(key.line(),
                        "réglage inconnu « " + key.text() + " »");
            };
        }
        expect("sym", ")");
        return options;
    }

    /** {@code @layout(column, gap: 4, cross: stretch)} — tout est optionnel sauf le mode. */
    private LayoutSpec parseLayout() {
        expect("sym", "(");
        Token modeToken = expect("word", null);
        LayoutSpec.Mode mode = enumOf(LayoutSpec.Mode.class, modeToken, "disposition");
        LayoutSpec spec = LayoutSpec.ABSOLUTE.withMode(mode);
        while (eat("sym", ",")) {
            Token key = expect("word", null);
            expect("sym", ":");
            spec = switch (key.text()) {
                case "gap" -> spec.withGap(number(next()));
                case "crossGap" -> spec.withCrossGap(number(next()));
                case "columns" -> spec.withColumns((int) number(next()));
                case "main" -> spec.withMain(
                        enumOf(LayoutSpec.Distribute.class, expect("word", null), "répartition"));
                case "cross" -> spec.withCross(
                        enumOf(LayoutSpec.Cross.class, expect("word", null), "alignement"));
                case "scroll" -> spec.withScroll(scrollAxis(expect("word", null)));
                default -> throw new ParseError(key.line(),
                        "réglage de disposition inconnu « " + key.text() + " »");
            };
        }
        expect("sym", ")");
        return spec;
    }

    /**
     * L'axe de défilement d'un conteneur.
     *
     * <p>{@code true} et {@code false} restent acceptés : c'est ce qu'écrivaient les
     * `.bp` exportés avant l'axe horizontal, et {@code true} y voulait dire vertical.
     * Les refuser rendrait illisible un fichier que rien n'oblige à réécrire.
     */
    private LayoutSpec.Scroll scrollAxis(Token token) {
        return switch (token.text()) {
            case "true" -> LayoutSpec.Scroll.VERTICAL;
            case "false" -> LayoutSpec.Scroll.NONE;
            default -> enumOf(LayoutSpec.Scroll.class, token, "axe de défilement");
        };
    }

    /** Le bloc {@code styles { "nom" = ... }} en tête d'un écran (story 10.10). */
    private void parseScreenStyles(java.util.Map<String, ElementStyle> out) {
        expect("word", "styles");
        expect("sym", "{");
        while (!eat("sym", "}")) {
            Token name = expect("string", null);
            if (name.text().isEmpty()) {
                throw new ParseError(name.line(), "un style nommé ne peut pas être anonyme");
            }
            if (out.containsKey(name.text())) {
                throw new ParseError(name.line(),
                        "style « " + name.text() + " » déjà défini dans cet écran");
            }
            expect("sym", "=");
            out.put(name.text(), parseStyleFields());
        }
    }

    private <E extends Enum<E>> E enumOf(Class<E> type, Token token, String what) {
        try {
            return Enum.valueOf(type, token.text().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ParseError(token.line(),
                    what + " inconnue « " + token.text() + " »");
        }
    }

    private Extent wrapExtent(Token token, java.util.function.Supplier<Extent> build) {
        try {
            return build.get();
        } catch (IllegalArgumentException e) {
            throw new ParseError(token.line(), e.getMessage());
        }
    }

    private ElementStyle parseStyle() {
        expect("sym", "(");
        ElementStyle style = parseStyleFields();
        expect("sym", ")");
        return style;
    }

    /** Les neuf champs d'un style, sans leur ponctuation englobante. */
    private ElementStyle parseStyleFields() {
        Token open = peek();
        int background = argb();
        expect("sym", ",");
        int border = argb();
        expect("sym", ",");
        int borderWidth = (int) number(next());
        expect("sym", ",");
        int textColor = argb();
        expect("sym", ",");
        int hover = argb();
        expect("sym", ",");
        int pressed = argb();
        expect("sym", ",");
        int disabled = argb();
        expect("sym", ",");
        int padding = (int) number(next());
        expect("sym", ",");
        Token alignToken = expect("word", null);
        ElementStyle.TextAlign align;
        try {
            align = ElementStyle.TextAlign.valueOf(alignToken.text().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ParseError(alignToken.line(),
                    "alignement inconnu « " + alignToken.text() + " »");
        }
        // Le retour à la ligne est un DIXIÈME champ facultatif : l'ajouter comme
        // obligatoire aurait rendu illisible tout `.bp` déjà exporté, alors que rien ne
        // l'exige — un style qui ne renvoie pas à la ligne ne l'écrit tout simplement pas.
        boolean wrap = false;
        if (peek().kind().equals("sym") && ",".equals(peek().text())) {
            next();
            Token wrapToken = expect("word", null);
            if (!"wrap".equals(wrapToken.text())) {
                throw new ParseError(wrapToken.line(),
                        "attendu « wrap » après l'alignement, lu « " + wrapToken.text() + " »");
            }
            wrap = true;
        }
        try {
            return new ElementStyle(background, border, borderWidth, textColor,
                    hover, pressed, disabled, padding, align, wrap);
        } catch (IllegalArgumentException e) {
            throw new ParseError(open.line(), e.getMessage());
        }
    }

    /** Une couleur {@code "#AARRGGBB"}. */
    private int argb() {
        Token token = expect("string", null);
        try {
            return (int) Long.parseLong(token.text().replace("#", ""), 16);
        } catch (NumberFormatException e) {
            throw new ParseError(token.line(), "couleur invalide « " + token.text() + " »");
        }
    }

    /**
     * {@code func "doubler"(n: double) -> (resultat: double) @id(…) @pos(…) { … }}
     *
     * <p>Le corps se lit avec <b>la même machinerie</b> qu'un événement : mêmes
     * instructions, mêmes blocs, mêmes étiquettes. Ce qui change est l'endroit où les
     * nœuds atterrissent — un blueprint <b>jetable</b> le temps du bloc, dont on récupère
     * ensuite les nœuds et les liens pour en faire un corps.
     *
     * <p>Substituer le réceptacle plutôt que paramétrer les vingt méthodes de parcours :
     * elles écrivent toutes dans {@code bp}, et leur en donner un autre coûte deux lignes
     * là où un paramètre supplémentaire en aurait touché vingt. Le même geste que le
     * décodage NBT d'un corps, pour la même raison.
     */
    private void parseFunction() {
        expect("word", "func");
        String name = expect("string", null).text();
        List<fr.blueprint.core.graph.BlueprintFunction.Param> inputs = parseParams();
        List<fr.blueprint.core.graph.BlueprintFunction.Param> outputs = List.of();
        if (peek().text().equals("returns")) {
            next();
            outputs = parseParams();
        }
        Annotations anns = parseAnnotations();

        Blueprint outer = bp;
        bp = new Blueprint(net.minecraft.resources.Identifier
                .fromNamespaceAndPath("blueprint", "scratch"));
        UUID entryId = anns.idOr(UUID.randomUUID());
        Node entry = new Node(entryId, fr.blueprint.core.graph.FuncNodes.PARAM,
                anns.posOr(nextAutoPos()));
        GraphLoader.setLiteral(entry, fr.blueprint.core.graph.FuncNodes.FUNCTION_PIN,
                fr.blueprint.api.pin.LiteralValue.of(fr.blueprint.api.pin.PinTypes.STRING, name));
        GraphLoader.addNode(bp, entry);

        Map<String, String> scope = new HashMap<>();
        for (var param : inputs) {
            scope.put(param.name(), param.name());
        }
        Map<String, String> outerParams = eventParams;
        UUID outerEvent = currentEvent;
        eventParams = scope;
        currentEvent = entryId;
        expect("sym", "{");
        parseStatements(entryId, fr.blueprint.core.graph.BlueprintFunction.EXEC_OUT);
        eventParams = outerParams;
        currentEvent = outerEvent;

        Map<UUID, Node> nodes = new java.util.LinkedHashMap<>(bp.nodes());
        Set<Link> links = new java.util.LinkedHashSet<>(bp.links());
        bp = outer;
        GraphLoader.addFunction(bp, fr.blueprint.core.graph.BlueprintFunction
                .of(name, inputs, outputs).withBody(nodes, links));
    }

    /** {@code (n: double, cible: entity)} — vide si les parenthèses le sont. */
    private List<fr.blueprint.core.graph.BlueprintFunction.Param> parseParams() {
        expect("sym", "(");
        List<fr.blueprint.core.graph.BlueprintFunction.Param> params = new ArrayList<>();
        while (!eat("sym", ")")) {
            if (!params.isEmpty()) {
                expect("sym", ",");
            }
            String pin = expect("word", null).text();
            expect("sym", ":");
            params.add(new fr.blueprint.core.graph.BlueprintFunction.Param(pin, parseType()));
        }
        return params;
    }

    private void parseEvent() {
        Token onToken = expect("word", "on");
        Identifier eventId = qualifiedId();
        expect("sym", "(");
        List<String> params = new ArrayList<>();
        while (!eat("sym", ")")) {
            if (!params.isEmpty()) {
                expect("sym", ",");
            }
            params.add(expect("word", null).text());
        }
        Annotations anns = parseAnnotations();
        UUID uuid = anns.idOr(UUID.randomUUID());
        Node event = new Node(uuid, eventId, anns.posOr(nextAutoPos()));
        GraphLoader.addNode(bp, event);
        applyEventLiterals(event, anns);

        Map<String, String> scope = new HashMap<>();
        for (String param : params) {
            scope.put(param, param);   // nom du paramètre = nom du pin de sortie
        }
        eventParams = scope;
        currentEvent = uuid;
        expect("sym", "{");
        parseStatements(uuid, firstExecOutName(eventId, uuid));
        eventParams = Map.of();
        currentEvent = null;
    }

    /**
     * Les littéraux d'entrée d'un nœud d'événement ({@code @with}). Le type vient du
     * pin quand la forme est connue ; sur un fantôme, il est inféré du jeton — le
     * chargement ne refuse rien (P4), et un filtre préservé vaut mieux qu'un filtre
     * perdu parce que le mod qui déclare l'événement est momentanément absent.
     */
    private void applyEventLiterals(Node event, Annotations anns) {
        if (anns.with.isEmpty()) {
            return;
        }
        NodeShape shape = lookup.shape(event.typeId());
        for (var entry : anns.with.entrySet()) {
            PinType type = null;
            if (shape != null) {
                for (NodeShape.PinDef pin : shape.inputs()) {
                    if (pin.name().equals(entry.getKey())) {
                        type = pin.type();
                        break;
                    }
                }
            }
            GraphLoader.setLiteral(event, entry.getKey(),
                    convertRaw(type, entry.getValue(), anns.withLine));
        }
    }

    /** Une suite d'instructions ; la première se lie à {@code (prev, prevPin)}. */
    private void parseStatements(@Nullable UUID prev, @Nullable String prevPin) {
        String pendingLabel = null;
        while (!eat("sym", "}")) {
            Token token = peek();
            if (token.text().equals("label")) {
                next();
                pendingLabel = expect("word", null).text();
                continue;
            }
            if (token.text().equals("goto")) {
                Token gotoToken = next();
                String name = expect("word", null).text();
                UUID target = labels.get(name);
                if (target == null) {
                    throw new ParseError(gotoToken.line(), "étiquette inconnue « " + name + " »");
                }
                if (prev != null && prevPin != null) {
                    links.add(new Link(prev, prevPin, target, firstExecInName(target)));
                }
                prev = null;
                continue;
            }
            UUID statement = parseCallStatement(prev, prevPin, pendingLabel);
            pendingLabel = null;
            prev = statement;
            prevPin = statement == null ? null : firstExecOutName(bp.node(statement).typeId(), statement);
            if (statement != null && lastStatementHadBlock) {
                prev = null;   // un bloc de branches consomme toutes les sorties
            }
        }
    }

    private boolean lastStatementHadBlock;

    /** Retourne l'UUID du nœud, ou null si l'instruction ne continue pas la chaîne. */
    private @Nullable UUID parseCallStatement(@Nullable UUID prev, @Nullable String prevPin,
                                              @Nullable String label) {
        Identifier typeId = qualifiedId();
        List<Arg> args = parseArgs();
        Annotations anns = parseAnnotations();
        UUID uuid = anns.idOr(UUID.randomUUID());
        materialize(uuid, typeId, anns, args);
        if (label != null) {
            labels.put(label, uuid);
        }
        if (prev != null && prevPin != null) {
            links.add(new Link(prev, prevPin, uuid, firstExecInName(uuid)));
        }
        lastStatementHadBlock = false;
        if (eat("sym", "{")) {
            lastStatementHadBlock = true;
            while (!eat("sym", "}")) {
                String pin = expect("word", null).text();
                expect("sym", ":");
                expect("sym", "{");
                parseStatements(uuid, pin);
            }
        }
        return uuid;
    }

    /** Crée (ou retrouve — dédup des purs partagés par @id) le nœud et lie ses arguments. */
    private void materialize(UUID uuid, Identifier typeId, Annotations anns, List<Arg> args) {
        Node node = bp.node(uuid);
        if (node == null) {
            node = new Node(uuid, typeId, anns.posOr(nextAutoPos()));
            GraphLoader.addNode(bp, node);
        } else if (!node.typeId().equals(typeId)) {
            throw new ParseError(anns.line, "@id réutilisé avec un type différent : " + uuid);
        }
        NodeShape shape = shapeOf(typeId, uuid);
        for (Arg arg : args) {
            if (arg.expr() instanceof Expr.Lit lit) {
                PinType pinType = shape != null && shape.input(arg.pin()) != null
                        ? shape.input(arg.pin()).type() : null;
                GraphLoader.setLiteral(node, arg.pin(), convertRaw(pinType, lit.raw(), lit.line()));
            } else if (arg.expr() instanceof Expr.EventOut ref) {
                links.add(new Link(ref.event(), ref.pin(), uuid, arg.pin()));
            } else if (arg.expr() instanceof Expr.NodeOut ref) {
                links.add(new Link(ref.node(), ref.pin(), uuid, arg.pin()));
            } else if (arg.expr() instanceof Expr.Call call) {
                links.add(new Link(call.node(), call.outPin(), uuid, arg.pin()));
            }
        }
    }

    // -------------------------------------------------------------- expressions

    /** Littéral brut, typé plus tard par le pin ou la variable qui le reçoit. */
    private sealed interface Raw {
        record Num(String text) implements Raw {
        }

        record Str(String text) implements Raw {
        }

        record Bool(boolean value) implements Raw {
        }

        record Arr(List<Raw> items) implements Raw {
        }
    }

    private sealed interface Expr {
        record Lit(Raw raw, int line) implements Expr {
        }

        record EventOut(UUID event, String pin) implements Expr {
        }

        record NodeOut(UUID node, String pin) implements Expr {
        }

        record Call(UUID node, String outPin) implements Expr {
        }
    }

    private record Arg(String pin, Expr expr) {
    }

    private List<Arg> parseArgs() {
        expect("sym", "(");
        List<Arg> args = new ArrayList<>();
        while (!eat("sym", ")")) {
            if (!args.isEmpty()) {
                expect("sym", ",");
            }
            String pin = expect("word", null).text();
            expect("sym", ":");
            args.add(new Arg(pin, parseExpr()));
        }
        return args;
    }

    private Expr parseExpr() {
        Token token = peek();
        if (token.kind().equals("number") || token.kind().equals("string")
                || token.text().equals("true") || token.text().equals("false")
                || token.text().equals("[")) {
            return new Expr.Lit(parseRawLit(), token.line());
        }
        if (eat("sym", "$")) {
            Token name = expect("word", null);
            if (name.text().equals("node")) {
                expect("sym", "(");
                UUID node;
                try {
                    node = UUID.fromString(expect("string", null).text());
                } catch (IllegalArgumentException e) {
                    throw new ParseError(name.line(), "$node : UUID invalide");
                }
                expect("sym", ")");
                expect("sym", ".");
                String pin = expect("word", null).text();
                return new Expr.NodeOut(node, pin);
            }
            String pin = eventParams.get(name.text());
            if (pin == null || currentEvent == null) {
                throw new ParseError(name.line(), "référence inconnue $" + name.text());
            }
            return new Expr.EventOut(currentEvent, pin);
        }
        // Appel imbriqué (pur) : type(args) @id(...)
        Identifier typeId = qualifiedId();
        List<Arg> args = parseArgs();
        Annotations anns = parseAnnotations();
        UUID uuid = anns.idOr(UUID.randomUUID());
        materialize(uuid, typeId, anns, args);
        NodeShape shape = shapeOf(typeId, uuid);
        // @out l'emporte : sans lui, un nœud à plusieurs sorties rendrait toujours la
        // première, et l'écriture serait ambiguë plutôt que fausse — ce qui est pire.
        String outPin = anns.out != null ? anns.out
                : shape == null ? "out" : shape.outputs().stream()
                        .filter(p -> p.kind() == PinKind.DATA)
                        .map(NodeShape.PinDef::name).findFirst().orElse("out");
        return new Expr.Call(uuid, outPin);
    }

    // ------------------------------------------------------------- annotations

    private final class Annotations {
        @Nullable UUID id;
        @Nullable Vec2d pos;
        @Nullable Vec2d size;
        int color = 0xFF303030;
        int line;
        /**
         * Littéraux d'entrée d'un nœud d'événement ({@code @with}). Gardés BRUTS : le
         * type du pin ne se connaît qu'une fois la forme du nœud résolue, ce qui
         * n'arrive qu'après la lecture des annotations.
         */
        final Map<String, Raw> with = new java.util.LinkedHashMap<>();
        int withLine;
        /**
         * Sortie choisie d'un appel inliné ({@code @out}).
         *
         * <p>Un appel inliné rend par défaut sa <b>première</b> sortie de donnée, ce qui
         * suffit tant qu'il n'en a qu'une. Les nœuds purs à plusieurs sorties —
         * {@code vec/split}, {@code pos/split}, {@code map/get}, {@code convert/to_number}
         * — étaient pour cette raison exclus de l'inlining, donc jamais émis, donc
         * <b>perdus</b> à l'aller-retour.
         */
        @Nullable String out;

        UUID idOr(UUID fallback) {
            return id != null ? id : fallback;
        }

        Vec2d posOr(Vec2d fallback) {
            return pos != null ? pos : fallback;
        }

        Vec2d sizeOr(Vec2d fallback) {
            return size != null ? size : fallback;
        }
    }

    private Annotations parseAnnotations() {
        Annotations anns = new Annotations();
        anns.line = peek().line();
        while (peek().kind().equals("sym") && peek().text().equals("@")) {
            next();
            Token name = expect("word", null);
            switch (name.text()) {
                case "id" -> {
                    expect("sym", "(");
                    try {
                        anns.id = UUID.fromString(expect("string", null).text());
                    } catch (IllegalArgumentException e) {
                        throw new ParseError(name.line(), "@id invalide");
                    }
                    expect("sym", ")");
                }
                case "pos" -> anns.pos = parseVec();
                case "out" -> {
                    expect("sym", "(");
                    anns.out = expect("string", null).text();
                    expect("sym", ")");
                }
                case "size" -> anns.size = parseVec();
                case "color" -> {
                    expect("sym", "(");
                    String hex = expect("string", null).text();
                    try {
                        anns.color = (int) Long.parseLong(hex.replace("#", ""), 16);
                    } catch (NumberFormatException e) {
                        throw new ParseError(name.line(), "@color invalide");
                    }
                    expect("sym", ")");
                }
                case "with" -> {
                    anns.withLine = name.line();
                    expect("sym", "(");
                    while (!eat("sym", ")")) {
                        if (!anns.with.isEmpty()) {
                            expect("sym", ",");
                        }
                        String pin = expect("word", null).text();
                        expect("sym", ":");
                        anns.with.put(pin, parseRawLit());
                    }
                }
                default -> throw new ParseError(name.line(), "annotation inconnue @" + name.text());
            }
        }
        return anns;
    }

    private Vec2d parseVec() {
        expect("sym", "(");
        double x = number(next());
        expect("sym", ",");
        double y = number(next());
        expect("sym", ")");
        return new Vec2d(x, y);
    }

    // ------------------------------------------------------------------- util

    private Identifier qualifiedId() {
        Token ns = expect("word", null);
        if (eat("sym", ":")) {
            return Identifier.fromNamespaceAndPath(ns.text(), expect("word", null).text());
        }
        return Identifier.fromNamespaceAndPath("blueprint", ns.text());
    }

    private @Nullable NodeShape shapeOf(Identifier typeId, UUID uuid) {
        NodeShape shape = lookup.shape(typeId);
        if (shape != null) {
            return shape;
        }
        Node node = bp.node(uuid);
        return node == null ? null : GhostNode.deduceShape(bp, lookup, node);
    }

    private String firstExecOutName(Identifier typeId, UUID uuid) {
        NodeShape shape = shapeOf(typeId, uuid);
        if (shape != null) {
            for (NodeShape.PinDef pin : shape.outputs()) {
                if (pin.kind() == PinKind.EXEC) {
                    return pin.name();
                }
            }
        }
        return "exec_out";
    }

    private String firstExecInName(UUID uuid) {
        Node node = bp.node(uuid);
        NodeShape shape = node == null ? null : shapeOf(node.typeId(), uuid);
        if (shape != null) {
            for (NodeShape.PinDef pin : shape.inputs()) {
                if (pin.kind() == PinKind.EXEC) {
                    return pin.name();
                }
            }
        }
        return "exec_in";
    }

    /** Littéral brut : token simple ou liste {@code [a, b, …]} (imbrication permise). */
    private Raw parseRawLit() {
        if (eat("sym", "[")) {
            List<Raw> items = new ArrayList<>();
            while (!eat("sym", "]")) {
                if (!items.isEmpty()) {
                    expect("sym", ",");
                }
                items.add(parseRawLit());
            }
            return new Raw.Arr(items);
        }
        Token token = next();
        if (token.kind().equals("string")) {
            return new Raw.Str(token.text());
        }
        if (token.text().equals("true") || token.text().equals("false")) {
            return new Raw.Bool(Boolean.parseBoolean(token.text()));
        }
        if (token.kind().equals("number")) {
            return new Raw.Num(token.text());
        }
        throw new ParseError(token.line(), "littéral attendu, trouvé « " + token.text() + " »");
    }

    /** Point d'entrée quand le type receveur est connu d'avance (défaut de variable). */
    private LiteralValue parseLiteralValue(@Nullable PinType pinType) {
        int line = peek().line();
        return convertRaw(pinType, parseRawLit(), line);
    }

    /** Type le littéral brut par le pin receveur ; type inconnu (fantôme) → inféré du token. */
    private LiteralValue convertRaw(@Nullable PinType pinType, Raw raw, int line) {
        if (raw instanceof Raw.Str s) {
            if (pinType == PinTypes.RESOURCE_LOCATION) {
                Identifier id = Identifier.tryParse(s.text());
                if (id == null) {
                    throw new ParseError(line, "identifiant invalide « " + s.text() + " »");
                }
                return LiteralValue.of(pinType, id);
            }
            return LiteralValue.of(pinType != null && pinType.supportsLiteral() ? pinType : PinTypes.STRING,
                    s.text());
        }
        if (raw instanceof Raw.Bool b) {
            return LiteralValue.of(PinTypes.BOOL, b.value());
        }
        if (raw instanceof Raw.Num n) {
            double value = Double.parseDouble(n.text());
            if (pinType == PinTypes.DOUBLE) {
                return LiteralValue.of(PinTypes.DOUBLE, value);
            }
            if (pinType == PinTypes.LONG) {
                return LiteralValue.of(PinTypes.LONG, (long) value);
            }
            if (pinType == PinTypes.INT || pinType == null && value == Math.rint(value)
                    && !n.text().contains(".")) {
                return LiteralValue.of(PinTypes.INT, (int) value);
            }
            return LiteralValue.of(PinTypes.DOUBLE, value);
        }
        Raw.Arr arr = (Raw.Arr) raw;
        PinType elementType = pinType instanceof fr.blueprint.api.pin.ParameterizedPinType p
                && p.container() == fr.blueprint.api.pin.ParameterizedPinType.Container.LIST
                ? p.args().get(0) : null;
        List<Object> values = new ArrayList<>();
        PinType inferred = elementType;
        for (Raw item : arr.items()) {
            LiteralValue element = convertRaw(elementType, item, line);
            if (inferred == null) {
                inferred = element.type();
            }
            values.add(element.value());
        }
        PinType listType = pinType != null ? pinType
                : PinTypes.listOf(inferred != null ? inferred : PinTypes.STRING);
        return LiteralValue.of(listType, List.copyOf(values));
    }

    private double number(Token token) {
        if (!token.kind().equals("number")) {
            throw new ParseError(token.line(), "nombre attendu, trouvé « " + token.text() + " »");
        }
        return Double.parseDouble(token.text());
    }

    private Vec2d nextAutoPos() {
        int index = autoLayout++;
        return new Vec2d(80 + (index % 5) * 200, 60 + (index / 5) * 140);
    }
}
