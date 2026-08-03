package fr.blueprint.client.theme;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Charge {@code config/blueprint/theme.json} : base {@code "contrast": true|false}
 * puis jetons hex par groupe (UX §12). Clé absente → valeur de la base ; fichier
 * absent ou corrompu → thème par défaut, jamais de crash.
 *
 * <p>Déviation consignée (5.7) : le fichier vit dans {@code config/} et se recharge
 * à chaque ouverture de l'éditeur — le vrai listener de ressources
 * {@code assets/blueprint/theme/} attend l'épic 9.
 */
public final class ThemeLoader {

    private static final Gson GSON = new Gson();

    private ThemeLoader() {
    }

    public static Theme load(Path configDir) {
        Path file = configDir.resolve("blueprint").resolve("theme.json");
        try {
            if (!Files.exists(file)) {
                return Theme.DEFAULT;
            }
            JsonObject json = GSON.fromJson(Files.readString(file), JsonObject.class);
            Theme base = json.has("contrast") && json.get("contrast").getAsBoolean()
                    ? Theme.HIGH_CONTRAST : Theme.DEFAULT;
            JsonObject canvas = section(json, "canvas");
            JsonObject node = section(json, "node");
            JsonObject state = section(json, "state");
            JsonObject wire = section(json, "wire");
            return new Theme(
                    color(canvas, "background", base.canvasBackground()),
                    color(canvas, "grid", base.grid()),
                    color(canvas, "gridMajor", base.gridMajor()),
                    color(node, "background", base.nodeBackground()),
                    color(node, "border", base.nodeBorder()),
                    color(node, "borderSelected", base.nodeSelected()),
                    color(state, "ghost", base.ghost()),
                    color(state, "error", base.error()),
                    color(state, "warning", base.warning()),
                    color(wire, "exec", base.execWire()));
        } catch (IOException | RuntimeException e) {
            return Theme.DEFAULT;
        }
    }

    private static JsonObject section(JsonObject json, String key) {
        return json != null && json.has(key) && json.get(key).isJsonObject()
                ? json.getAsJsonObject(key) : new JsonObject();
    }

    /** {@code #RRGGBB} (alpha FF implicite) ou {@code #AARRGGBB}. */
    static int color(JsonObject section, String key, int fallback) {
        if (!section.has(key)) {
            return fallback;
        }
        try {
            String hex = section.get(key).getAsString().trim();
            if (!hex.startsWith("#")) {
                return fallback;
            }
            String digits = hex.substring(1);
            long value = Long.parseLong(digits, 16);
            if (digits.length() == 6) {
                return (int) (0xFF000000L | value);
            }
            if (digits.length() == 8) {
                return (int) value;
            }
            return fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
