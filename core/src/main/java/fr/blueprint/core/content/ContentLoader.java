package fr.blueprint.core.content;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Rarity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Lit {@code blueprint/content/items/*.json} — le contenu déclaré (épic 11).
 *
 * <p>Appelé pendant l'initialisation du mod, <b>avant le gel des registres</b>. Il n'y a
 * alors ni serveur, ni monde, ni joueur à qui se plaindre : tout ce que ce chargeur peut
 * faire d'un fichier fautif, c'est l'écarter et le <b>nommer</b> dans un rapport, que la
 * commande {@code /blueprint content} affichera plus tard.
 *
 * <p><b>Un fichier fautif n'emporte pas les autres.</b> C'est la leçon de la 8.2, où un
 * JSON au mauvais type faisait échouer tout le rechargement des nœuds de datapack : sur
 * vingt items déclarés, une virgule oubliée dans le dix-septième ne doit pas priver le
 * serveur des dix-neuf autres. Et surtout, un identifiant invalide ferait <b>lever
 * Minecraft à l'enregistrement</b>, c'est-à-dire empêcherait le jeu de démarrer : mieux
 * vaut un item manquant qu'un jeu qui ne se lance pas.
 */
public final class ContentLoader {

    private static final Gson GSON = new Gson();

    /** Ce qui a été retenu, et ce qui a été écarté avec sa raison. */
    public record Report(Map<Identifier, ItemDefinition> items, List<String> rejected) {

        public Report {
            // PAS Map.copyOf : il ne préserve pas l'ordre d'insertion, et l'ordre est
            // ici une garantie — les identifiants numériques du réseau sont attribués
            // dans l'ordre d'enregistrement, si bien qu'un ordre instable ferait rouvrir
            // un monde avec les items permutés. Le projet s'est déjà fait prendre par ce
            // même Map.copyOf sur les textures d'un pack (10.5).
            items = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(items));
            rejected = List.copyOf(rejected);
        }

        public static Report empty() {
            return new Report(Map.of(), List.of());
        }
    }

    private ContentLoader() {
    }

    /**
     * Lit le dossier ; un dossier absent rend un rapport vide.
     *
     * <p>Absent et non créé : un dossier vide qui apparaît tout seul dans le répertoire de
     * jeu de quelqu'un qui n'a jamais déclaré de contenu est du bruit. Il sera créé le jour
     * où l'on y écrira.
     */
    public static Report load(Path contentDir) {
        Path itemsDir = contentDir.resolve("items");
        if (!Files.isDirectory(itemsDir)) {
            return Report.empty();
        }
        Map<Identifier, ItemDefinition> items = new LinkedHashMap<>();
        List<String> rejected = new ArrayList<>();
        try (Stream<Path> files = Files.list(itemsDir)) {
            // Triés : deux démarrages doivent enregistrer dans le même ordre, sinon les
            // identifiants numériques du réseau changeraient d'une fois sur l'autre.
            files.filter(path -> path.toString().endsWith(".json")).sorted()
                    .forEach(file -> read(file, items, rejected));
        } catch (IOException e) {
            rejected.add(itemsDir + " : dossier illisible (" + e.getMessage() + ")");
        }
        return new Report(items, rejected);
    }

    private static void read(Path file, Map<Identifier, ItemDefinition> items,
                             List<String> rejected) {
        String fileName = file.getFileName().toString();
        String name = fileName.substring(0, fileName.length() - ".json".length())
                .toLowerCase(Locale.ROOT);
        if (!name.matches(ItemDefinition.PATH)) {
            rejected.add(fileName + " : nom de fichier inutilisable comme identifiant "
                    + "(lettres minuscules, chiffres, « _ », « - » et « . » seulement)");
            return;
        }
        Identifier id = Identifier.fromNamespaceAndPath("blueprint", name);
        if (items.containsKey(id)) {
            rejected.add(fileName + " : « " + id + " » est déjà déclaré");
            return;
        }
        try {
            JsonObject json = GSON.fromJson(Files.readString(file), JsonObject.class);
            if (json == null) {
                rejected.add(fileName + " : fichier vide");
                return;
            }
            items.put(id, new ItemDefinition(id,
                    stringOr(json, "name", ""),
                    json.has("translate") && json.get("translate").getAsBoolean(),
                    json.has("stackSize") ? json.get("stackSize").getAsInt() : 64,
                    rarityOf(json), textureBeside(file, name)));
        } catch (IOException | RuntimeException e) {
            // RuntimeException large à dessein : Gson lève une demi-douzaine de types
            // différents selon la faute, et les distinguer n'apprendrait rien à l'auteur —
            // ce qu'il lui faut, c'est le nom du fichier et le message.
            rejected.add(fileName + " : " + e.getMessage());
        }
    }

    /**
     * La texture est le PNG <b>portant le même nom</b>, à côté du fichier.
     *
     * <p>Aucun chemin à écrire, donc aucun chemin à écrire faux : l'auteur dépose
     * {@code rubis.json} et {@code rubis.png}, et c'est tout. Un champ « texture » dans le
     * JSON aurait demandé de connaître la syntaxe des identifiants de ressource, et se
     * serait trompé une fois sur deux.
     */
    private static String textureBeside(Path file, String name) {
        Path png = file.resolveSibling(name + ".png");
        return Files.isRegularFile(png) ? png.toString() : null;
    }

    private static String stringOr(JsonObject json, String key, String fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive()
                ? json.get(key).getAsString() : fallback;
    }

    /** Une rareté inconnue vaut « commune » : la couleur d'un nom n'est pas un motif de refus. */
    private static Rarity rarityOf(JsonObject json) {
        String raw = stringOr(json, "rarity", "").toUpperCase(Locale.ROOT);
        for (Rarity rarity : Rarity.values()) {
            if (rarity.name().equals(raw)) {
                return rarity;
            }
        }
        return Rarity.COMMON;
    }
}
