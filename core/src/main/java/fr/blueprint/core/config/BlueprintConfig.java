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
public record BlueprintConfig(int commandPermissionLevel) {

    public static final BlueprintConfig DEFAULT = new BlueprintConfig(2);

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
                Files.writeString(file, GSON.toJson(defaults));
                return DEFAULT;
            }
            JsonObject json = GSON.fromJson(Files.readString(file), JsonObject.class);
            int level = json != null && json.has("commandPermissionLevel")
                    ? json.get("commandPermissionLevel").getAsInt()
                    : DEFAULT.commandPermissionLevel();
            return new BlueprintConfig(level);
        } catch (IOException | RuntimeException e) {
            BlueprintMod.LOGGER.warn("Config illisible ({}), valeurs par défaut appliquées", file, e);
            return DEFAULT;
        }
    }
}
