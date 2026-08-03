package fr.blueprint.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import fr.blueprint.core.BlueprintMod;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.Permissions;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuration serveur ({@code config/blueprint/config.json}), créée avec ses
 * valeurs par défaut au premier lancement. Volontairement plate et tolérante :
 * un fichier illisible vaut configuration par défaut, jamais un crash.
 */
public record BlueprintConfig(int commandPermissionLevel, int fuelPerTick, int maxOverBudgetTicks,
                              int maxNodes, int maxGraphKilobytes, int savesPerWindow,
                              int requestsPerWindow, boolean auditAdminNodes) {

    public static final BlueprintConfig DEFAULT = new BlueprintConfig(2);

    /** Commodité : niveau de permission seul, runtime aux défauts. */
    public BlueprintConfig(int commandPermissionLevel) {
        this(commandPermissionLevel, 10_000, 100, 1_000, 256, 10, 60, true);
    }

    /** Compatibilité : les trois premiers champs, le reste aux défauts. */
    public BlueprintConfig(int commandPermissionLevel, int fuelPerTick, int maxOverBudgetTicks) {
        this(commandPermissionLevel, fuelPerTick, maxOverBudgetTicks, 1_000, 256, 10, 60, true);
    }

    /** Bornes structurelles d'un graphe (validateur, opérations d'édition). */
    public fr.blueprint.core.graph.GraphLimits graphLimits() {
        return new fr.blueprint.core.graph.GraphLimits(Math.max(1, maxNodes));
    }

    /** Bornes et quotas du réseau (story 6.4), dérivés de la configuration. */
    public fr.blueprint.core.net.NetLimits netLimits() {
        var defaults = fr.blueprint.core.net.NetLimits.DEFAULT;
        return new fr.blueprint.core.net.NetLimits(
                Math.max(16, maxGraphKilobytes) * 1024,
                Math.max(1, maxNodes),
                defaults.maxLinks(), defaults.maxVariables(), defaults.maxComments(),
                defaults.maxTextLength(), defaults.maxGhosts(),
                Math.max(1, savesPerWindow), Math.max(1, requestsPerWindow),
                defaults.windowMillis());
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Niveau 0-4 → permission exigée des sous-commandes d'administration ; null = ouvert
     * à tous (niveau 0). On passe par {@code Permissions.*} et non {@code Commands.LEVEL_*} :
     * l'init statique de {@code Commands} exige le bootstrap des registres, celle de
     * {@code Permissions} non — condition des tests headless (vérifié).
     */
    public @Nullable Permission adminPermission() {
        return switch (Math.max(0, Math.min(4, commandPermissionLevel))) {
            case 0 -> null;
            case 1 -> Permissions.COMMANDS_MODERATOR;
            case 2 -> Permissions.COMMANDS_GAMEMASTER;
            case 3 -> Permissions.COMMANDS_ADMIN;
            default -> Permissions.COMMANDS_OWNER;
        };
    }

    /** Charge depuis {@code <configDir>/blueprint/config.json}, en l'écrivant si absent. */
    public static BlueprintConfig load(Path configDir) {
        Path file = configDir.resolve("blueprint").resolve("config.json");
        try {
            if (Files.notExists(file)) {
                Files.createDirectories(file.getParent());
                JsonObject defaults = new JsonObject();
                defaults.addProperty("commandPermissionLevel", DEFAULT.commandPermissionLevel());
                defaults.addProperty("fuelPerTick", DEFAULT.fuelPerTick());
                defaults.addProperty("maxOverBudgetTicks", DEFAULT.maxOverBudgetTicks());
                defaults.addProperty("maxNodes", DEFAULT.maxNodes());
                defaults.addProperty("maxGraphKilobytes", DEFAULT.maxGraphKilobytes());
                defaults.addProperty("savesPerWindow", DEFAULT.savesPerWindow());
                defaults.addProperty("requestsPerWindow", DEFAULT.requestsPerWindow());
                defaults.addProperty("auditAdminNodes", DEFAULT.auditAdminNodes());
                Files.writeString(file, GSON.toJson(defaults));
                return DEFAULT;
            }
            JsonObject json = GSON.fromJson(Files.readString(file), JsonObject.class);
            if (json == null) {
                return DEFAULT;
            }
            return new BlueprintConfig(
                    intOr(json, "commandPermissionLevel", DEFAULT.commandPermissionLevel()),
                    intOr(json, "fuelPerTick", DEFAULT.fuelPerTick()),
                    intOr(json, "maxOverBudgetTicks", DEFAULT.maxOverBudgetTicks()),
                    intOr(json, "maxNodes", DEFAULT.maxNodes()),
                    intOr(json, "maxGraphKilobytes", DEFAULT.maxGraphKilobytes()),
                    intOr(json, "savesPerWindow", DEFAULT.savesPerWindow()),
                    intOr(json, "requestsPerWindow", DEFAULT.requestsPerWindow()),
                    boolOr(json, "auditAdminNodes", DEFAULT.auditAdminNodes()));
        } catch (IOException | RuntimeException e) {
            BlueprintMod.LOGGER.warn("Config illisible ({}), valeurs par défaut appliquées", file, e);
            return DEFAULT;
        }
    }

    private static int intOr(JsonObject json, String key, int fallback) {
        try {
            return json.has(key) ? json.get(key).getAsInt() : fallback;
        } catch (RuntimeException e) {
            // Une valeur absurde (« beaucoup ») ne doit pas coûter TOUTE la config.
            BlueprintMod.LOGGER.warn("Config : « {} » illisible, défaut {} appliqué", key, fallback);
            return fallback;
        }
    }

    private static boolean boolOr(JsonObject json, String key, boolean fallback) {
        try {
            return json.has(key) ? json.get(key).getAsBoolean() : fallback;
        } catch (RuntimeException e) {
            BlueprintMod.LOGGER.warn("Config : « {} » illisible, défaut {} appliqué", key, fallback);
            return fallback;
        }
    }
}
