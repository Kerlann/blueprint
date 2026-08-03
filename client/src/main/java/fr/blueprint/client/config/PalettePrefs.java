package fr.blueprint.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Préférences CLIENT de la palette (story 5.4b) : favoris et nœuds récents. Hors de
 * la sauvegarde monde — c'est une préférence utilisateur — dans
 * {@code blueprint/editor-client.json}, écrite atomiquement (même dossier et
 * même Gson que la config serveur, chemin injecté pour les tests).
 */
public final class PalettePrefs {

    public static final int MAX_RECENTS = 10;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Set<Identifier> favorites = new LinkedHashSet<>();
    private final Deque<Identifier> recents = new ArrayDeque<>();

    public boolean isFavorite(Identifier id) {
        return favorites.contains(id);
    }

    /** Bascule le favori ; retourne le nouvel état. */
    public boolean toggleFavorite(Identifier id) {
        if (!favorites.remove(id)) {
            favorites.add(id);
            return true;
        }
        return false;
    }

    public List<Identifier> favorites() {
        return List.copyOf(favorites);
    }

    /** Du plus récent au plus ancien ; un doublon remonte en tête. */
    public List<Identifier> recents() {
        return List.copyOf(recents);
    }

    public void addRecent(Identifier id) {
        recents.remove(id);
        recents.addFirst(id);
        while (recents.size() > MAX_RECENTS) {
            recents.removeLast();
        }
    }

    // ------------------------------------------------------------------------ IO

    public static PalettePrefs load(Path configDir) {
        PalettePrefs prefs = new PalettePrefs();
        Path file = file(configDir);
        try {
            if (Files.exists(file)) {
                JsonObject json = GSON.fromJson(Files.readString(file), JsonObject.class);
                readIds(json, "favorites").forEach(prefs.favorites::add);
                readIds(json, "recents").forEach(prefs.recents::addLast);
            }
        } catch (IOException | RuntimeException e) {
            // Fichier corrompu : préférences vides, jamais de crash pour du confort.
        }
        return prefs;
    }

    public void save(Path configDir) {
        Path file = file(configDir);
        try {
            Files.createDirectories(file.getParent());
            JsonObject json = new JsonObject();
            json.add("favorites", writeIds(favorites()));
            json.add("recents", writeIds(recents()));
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(json));
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // Perte de confort acceptable ; consignée au prochain chargement.
        }
    }

    private static Path file(Path root) {
        return root.resolve("editor-client.json");
    }

    private static List<Identifier> readIds(JsonObject json, String key) {
        List<Identifier> out = new ArrayList<>();
        if (json != null && json.has(key)) {
            for (JsonElement element : json.getAsJsonArray(key)) {
                Identifier id = Identifier.tryParse(element.getAsString());
                if (id != null) {
                    out.add(id);
                }
            }
        }
        return out;
    }

    private static JsonArray writeIds(List<Identifier> ids) {
        JsonArray array = new JsonArray();
        ids.forEach(id -> array.add(id.toString()));
        return array;
    }
}
