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
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            } else if ("{}():,@$<>=#.[]".indexOf(c) >= 0) {
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
                case "on" -> parseEvent();
                default -> throw new ParseError(token.line(),
                        "attendu meta/var/note/on, trouvé « " + token.text() + " »");
            }
        }
        // setMeta est package-private : on reconstruit avec la meta définitive.
        Blueprint complete = new Blueprint(id, meta);
        bp.nodes().values().forEach(n -> GraphLoader.addNode(complete, n));
        bp.variables().values().forEach(v -> GraphLoader.addVariable(complete, v));
        bp.comments().forEach(c -> GraphLoader.addComment(complete, c));
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
        String outPin = shape == null ? "out" : shape.outputs().stream()
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
