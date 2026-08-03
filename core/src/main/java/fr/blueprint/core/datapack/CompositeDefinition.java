package fr.blueprint.core.datapack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.PinType;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Un nœud composite décrit en JSON (story 8.2) : des pins déclarés et un corps qui
 * enchaîne des nœuds existants. Pure — l'analyse ne touche ni au registre ni au monde,
 * elle rend une définition <b>ou</b> la liste des raisons du refus.
 *
 * <pre>{@code
 * {
 *   "id": "mypack:heal_and_feed",
 *   "category": "player",
 *   "pins": {
 *     "in":  [ { "name": "player", "type": "blueprint:player" },
 *              { "name": "amount", "type": "blueprint:double", "default": 4.0 } ],
 *     "out": [ { "name": "ok", "type": "blueprint:bool" } ]
 *   },
 *   "body": { "steps": [
 *     { "node": "blueprint:entity/heal", "args": { "entity": "$player", "amount": "$amount" } },
 *     { "node": "blueprint:player/feed", "args": { "player": "$player", "points": 4 } }
 *   ] },
 *   "returns": { "ok": true }
 * }
 * }</pre>
 */
public record CompositeDefinition(Identifier id, String category, @Nullable String titleKey,
                                  @Nullable String descKey, Permission permission, int fuelCost,
                                  List<Pin> inputs, List<Pin> outputs, List<Step> steps,
                                  Map<String, Binding> returns) {

    /** Un pin déclaré ; {@code defaultValue} est le littéral JSON brut, validé plus tard. */
    public record Pin(String name, PinType type, @Nullable Object defaultValue) {
    }

    /** Un appel de nœud existant, ses entrées liées. */
    public record Step(Identifier node, Map<String, Binding> args) {
    }

    /** Ce qui alimente une entrée : une constante, un pin du composite, une sortie d'étape. */
    public sealed interface Binding {

        /** Valeur littérale écrite dans le JSON. */
        record Constant(Object value) implements Binding {
        }

        /** {@code "$nom"} — un pin d'entrée du composite. */
        record FromPin(String pin) implements Binding {
        }

        /** {@code "$2.result"} — la sortie d'une étape précédente (index 0-based). */
        record FromStep(int step, String pin) implements Binding {
        }
    }

    /** Résultat d'analyse : une définition, ou des erreurs — jamais les deux vides. */
    public record Result(@Nullable CompositeDefinition definition, List<String> errors) {

        public boolean ok() {
            return definition != null;
        }
    }

    /** Plafond imposé aux nœuds de datapack (AC4) : jamais au-delà du jeu ordinaire. */
    public static final Permission MAX_PERMISSION = Permission.GAMEPLAY;

    public static Result parse(JsonObject json, Function<Identifier, PinType> types) {
        List<String> errors = new ArrayList<>();
        Identifier id = id(json, "id", errors);
        if (id == null) {
            return new Result(null, errors);
        }

        Permission permission = Permission.SAFE;
        if (json.has("permission")) {
            try {
                permission = Permission.valueOf(string(json, "permission", ""));
            } catch (IllegalArgumentException e) {
                errors.add("permission inconnue : « " + string(json, "permission", "") + " »");
            }
        }
        if (!permission.allowedUnder(MAX_PERMISSION)) {
            errors.add("permission " + permission + " au-delà du plafond des datapacks ("
                    + MAX_PERMISSION + ")");
        }

        JsonObject pins = json.has("pins") && json.get("pins").isJsonObject()
                ? json.getAsJsonObject("pins") : new JsonObject();
        List<Pin> inputs = pins(pins, "in", types, errors);
        List<Pin> outputs = pins(pins, "out", types, errors);

        List<Step> steps = new ArrayList<>();
        JsonElement body = json.get("body");
        if (!(body instanceof JsonObject bodyObject) || !bodyObject.has("steps")) {
            errors.add("corps absent : attendu « body ».« steps »"
                    + (body instanceof JsonObject b && b.has("source")
                    ? " (les corps BScript arrivent en v1.1)" : ""));
        } else if (!(bodyObject.get("steps") instanceof JsonArray array)) {
            errors.add("« body.steps » doit être une liste");
        } else {
            int index = 0;
            for (JsonElement element : array) {
                if (!(element instanceof JsonObject step)) {
                    errors.add("étape " + index + " : objet attendu");
                    index++;
                    continue;
                }
                Identifier node = id(step, "node", errors);
                Map<String, Binding> args = new LinkedHashMap<>();
                if (step.get("args") instanceof JsonObject argsObject) {
                    for (Map.Entry<String, JsonElement> entry : argsObject.entrySet()) {
                        Binding binding = binding(entry.getValue(), index, errors,
                                "étape " + index + ", entrée « " + entry.getKey() + " »");
                        if (binding != null) {
                            args.put(entry.getKey(), binding);
                        }
                    }
                }
                if (node != null) {
                    steps.add(new Step(node, args));
                }
                index++;
            }
            if (steps.isEmpty() && errors.isEmpty()) {
                errors.add("« body.steps » est vide : un nœud composite doit faire quelque chose");
            }
        }

        Map<String, Binding> returns = new LinkedHashMap<>();
        if (json.get("returns") instanceof JsonObject returnsObject) {
            for (Map.Entry<String, JsonElement> entry : returnsObject.entrySet()) {
                Binding binding = binding(entry.getValue(), steps.size(), errors,
                        "sortie « " + entry.getKey() + " »");
                if (binding != null) {
                    returns.put(entry.getKey(), binding);
                }
            }
        }
        for (Pin out : outputs) {
            if (!returns.containsKey(out.name())) {
                errors.add("la sortie « " + out.name() + " » n'est alimentée par aucun « returns »");
            }
        }

        if (!errors.isEmpty()) {
            return new Result(null, errors);
        }
        return new Result(new CompositeDefinition(id,
                string(json, "category", "misc"),
                json.has("translation") && json.getAsJsonObject("translation").has("name")
                        ? json.getAsJsonObject("translation").get("name").getAsString() : null,
                json.has("translation") && json.getAsJsonObject("translation").has("desc")
                        ? json.getAsJsonObject("translation").get("desc").getAsString() : null,
                permission,
                json.has("fuelCost") ? json.get("fuelCost").getAsInt() : Math.max(1, steps.size()),
                inputs, outputs, steps, returns), List.of());
    }

    // ------------------------------------------------------------------ morceaux

    private static List<Pin> pins(JsonObject pins, String side, Function<Identifier, PinType> types,
                                  List<String> errors) {
        List<Pin> out = new ArrayList<>();
        if (!(pins.get(side) instanceof JsonArray array)) {
            return out;
        }
        for (JsonElement element : array) {
            if (!(element instanceof JsonObject pin)) {
                errors.add("pin « " + side + " » : objet attendu");
                continue;
            }
            String name = string(pin, "name", "");
            if (name.isEmpty()) {
                errors.add("pin « " + side + " » sans nom");
                continue;
            }
            Identifier typeId = id(pin, "type", errors);
            if (typeId == null) {
                continue;
            }
            PinType type = types.apply(typeId);
            if (type == null) {
                errors.add("pin « " + name + " » : type inconnu « " + typeId + " »");
                continue;
            }
            Object defaultValue = null;
            if (pin.has("default")) {
                defaultValue = literal(pin.get("default"));
                if (defaultValue == null) {
                    errors.add("pin « " + name + " » : valeur par défaut non reconnue");
                    continue;
                }
            }
            out.add(new Pin(name, type, defaultValue));
        }
        return out;
    }

    /** {@code "$pin"}, {@code "$3.result"}, ou une constante JSON. */
    private static @Nullable Binding binding(JsonElement element, int stepIndex,
                                             List<String> errors, String where) {
        if (element instanceof JsonPrimitive primitive && primitive.isString()) {
            String text = primitive.getAsString();
            if (text.startsWith("$")) {
                String reference = text.substring(1);
                int dot = reference.indexOf('.');
                if (dot < 0) {
                    return new Binding.FromPin(reference);
                }
                try {
                    int step = Integer.parseInt(reference.substring(0, dot));
                    if (step < 0 || step >= stepIndex) {
                        errors.add(where + " : l'étape " + step
                                + " n'est pas ANTÉRIEURE à celle qui la lit");
                        return null;
                    }
                    return new Binding.FromStep(step, reference.substring(dot + 1));
                } catch (NumberFormatException e) {
                    errors.add(where + " : référence « " + text + " » illisible");
                    return null;
                }
            }
        }
        Object value = literal(element);
        if (value == null) {
            errors.add(where + " : valeur non reconnue");
            return null;
        }
        return new Binding.Constant(value);
    }

    /** Littéraux JSON acceptés : booléen, entier, décimal, texte. */
    private static @Nullable Object literal(JsonElement element) {
        if (!(element instanceof JsonPrimitive primitive)) {
            return null;
        }
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isString()) {
            return primitive.getAsString();
        }
        if (primitive.isNumber()) {
            String raw = primitive.getAsString();
            return raw.contains(".") || raw.contains("e") || raw.contains("E")
                    ? (Object) primitive.getAsDouble() : (Object) primitive.getAsInt();
        }
        return null;
    }

    private static @Nullable Identifier id(JsonObject json, String field, List<String> errors) {
        String raw = string(json, field, "");
        if (raw.isEmpty()) {
            errors.add("champ « " + field + " » manquant");
            return null;
        }
        Identifier id = Identifier.tryParse(raw);
        if (id == null) {
            errors.add("champ « " + field + " » : identifiant invalide « " + raw + " »");
        }
        return id;
    }

    private static String string(JsonObject json, String field, String fallback) {
        JsonElement element = json.get(field);
        return element instanceof JsonPrimitive primitive && primitive.isString()
                ? primitive.getAsString() : fallback;
    }
}
