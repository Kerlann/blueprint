package fr.blueprint.client.pack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * La lecture des packs de {@code blueprint/scripts/} (story 10.5).
 *
 * <p><b>Aucune image n'est décodée ici.</b> Les dimensions sont lues dans l'en-tête PNG,
 * qui les donne dans ses vingt-quatre premiers octets. C'est ce qui permet de refuser une
 * image de vingt mille pixels de côté <i>avant</i> qu'elle n'atteigne le décodeur et la
 * carte graphique — refuser après l'avoir décodée serait refuser trop tard. C'est aussi
 * ce qui rend toutes les bornes de l'AC4 vérifiables sans client.
 *
 * <p>Un pack illisible n'empêche jamais les autres de charger, ni le jeu de démarrer :
 * il est nommé et écarté, comme un plugin de mod (2.2) ou un fichier de datapack (8.2).
 */
public final class ScriptPackLoader {

    /** Côté d'image maximal. Au-delà, la carte graphique du joueur trinque. */
    public static final int MAX_TEXTURE_SIZE = 2048;
    /** Poids maximal d'un PNG, en octets. Large pour une image d'interface. */
    public static final long MAX_TEXTURE_BYTES = 4L * 1024 * 1024;
    /** Nombre maximal d'images par pack. */
    public static final int MAX_TEXTURES_PER_PACK = 256;
    /** Nombre maximal de packs chargés à la fois. */
    public static final int MAX_PACKS = 64;

    private ScriptPackLoader() {
    }

    /** Ce qui a été écarté, et <b>pourquoi</b> — le pourquoi est la moitié utile. */
    public record Rejection(String pack, String detail) {
    }

    public record Result(List<ScriptPack> packs, List<Rejection> rejections) {

        public Result {
            packs = List.copyOf(packs);
            rejections = List.copyOf(rejections);
        }

        public @Nullable ScriptPack pack(String name) {
            return packs.stream().filter(p -> p.name().equals(name)).findFirst().orElse(null);
        }
    }

    /**
     * Lit tous les packs d'un dossier. Un dossier absent n'est pas une erreur : c'est
     * l'état d'un joueur qui n'a encore rien reçu.
     */
    public static Result load(Path scriptsDir) {
        List<ScriptPack> packs = new ArrayList<>();
        List<Rejection> rejections = new ArrayList<>();
        if (!Files.isDirectory(scriptsDir)) {
            return new Result(packs, rejections);
        }

        List<Path> candidates = new ArrayList<>();
        try (Stream<Path> entries = Files.list(scriptsDir)) {
            entries.filter(Files::isDirectory).sorted().forEach(candidates::add);
        } catch (IOException e) {
            rejections.add(new Rejection("", "dossier des packs illisible : " + e.getMessage()));
            return new Result(packs, rejections);
        }

        for (Path directory : candidates) {
            if (packs.size() >= MAX_PACKS) {
                rejections.add(new Rejection(directory.getFileName().toString(),
                        "au-delà de " + MAX_PACKS + " packs chargés"));
                continue;
            }
            try {
                packs.add(readPack(directory, rejections));
            } catch (PackException e) {
                rejections.add(new Rejection(directory.getFileName().toString(), e.getMessage()));
            } catch (RuntimeException e) {
                // Un pack ne fait jamais tomber le chargement des autres, quelle que
                // soit la façon dont il est cassé.
                rejections.add(new Rejection(directory.getFileName().toString(),
                        "illisible : " + e));
            }
        }
        return new Result(packs, rejections);
    }

    /** Levée pour un pack entier écarté ; le message est montré au joueur. */
    private static final class PackException extends RuntimeException {
        PackException(String message) {
            super(message);
        }
    }

    private static ScriptPack readPack(Path directory, List<Rejection> rejections) {
        String name = directory.getFileName().toString();
        if (!ScriptPack.validName(name)) {
            throw new PackException("nom de dossier invalide — attendu : minuscules, "
                    + "chiffres, « _ » et « - »");
        }

        String version = "";
        String author = "";
        String description = "";
        Path manifest = directory.resolve(ScriptPack.MANIFEST);
        if (Files.isRegularFile(manifest)) {
            JsonObject json;
            try {
                json = JsonParser.parseString(Files.readString(manifest, StandardCharsets.UTF_8))
                        .getAsJsonObject();
            } catch (IOException | RuntimeException e) {
                throw new PackException(ScriptPack.MANIFEST + " illisible : " + e.getMessage());
            }
            version = string(json, "version");
            author = string(json, "author");
            description = string(json, "description");
        }
        // Le pack.json est FACULTATIF : un dossier de deux images doit marcher tel quel.
        // L'exiger transformerait le geste « je te donne mon dossier » en une formalité,
        // et le joueur découvrirait la formalité par un pack qui ne charge pas.

        Map<String, Path> textures = new LinkedHashMap<>();
        Path texturesDir = directory.resolve("textures");
        if (Files.isDirectory(texturesDir)) {
            List<Path> files = new ArrayList<>();
            try (Stream<Path> entries = Files.list(texturesDir)) {
                entries.filter(Files::isRegularFile).sorted().forEach(files::add);
            } catch (IOException e) {
                throw new PackException("dossier textures illisible : " + e.getMessage());
            }
            for (Path file : files) {
                String fileName = file.getFileName().toString();
                if (textures.size() >= MAX_TEXTURES_PER_PACK) {
                    rejections.add(new Rejection(name, fileName + " : au-delà de "
                            + MAX_TEXTURES_PER_PACK + " images"));
                    continue;
                }
                String refused = refuse(file, fileName);
                if (refused != null) {
                    rejections.add(new Rejection(name, fileName + " : " + refused));
                    continue;
                }
                textures.put(fileName.substring(0, fileName.length() - 4), file);
            }
        }

        Path blueprintFile = null;
        try (Stream<Path> entries = Files.list(directory)) {
            blueprintFile = entries.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".bp"))
                    .sorted().findFirst().orElse(null);
        } catch (IOException e) {
            // Le .bp est facultatif : son absence n'invalide rien.
            blueprintFile = null;
        }

        return new ScriptPack(name, version, author, description, textures, blueprintFile);
    }

    /**
     * Pourquoi cette image est refusée, ou {@code null} si elle est acceptable.
     *
     * <p>Nommer la raison n'est pas du confort : un joueur dont l'image ne s'affiche pas
     * cherchera d'abord dans le menu, puis dans le mod, et en dernier dans son fichier.
     */
    static @Nullable String refuse(Path file, String fileName) {
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".png")) {
            return "PNG uniquement";
        }
        long size;
        try {
            size = Files.size(file);
        } catch (IOException e) {
            return "illisible";
        }
        if (size > MAX_TEXTURE_BYTES) {
            return size / 1024 + " Ko, maximum " + MAX_TEXTURE_BYTES / 1024 + " Ko";
        }
        int[] dimensions = pngSize(file);
        if (dimensions == null) {
            return "ce n'est pas un PNG lisible";
        }
        if (dimensions[0] > MAX_TEXTURE_SIZE || dimensions[1] > MAX_TEXTURE_SIZE) {
            return dimensions[0] + "×" + dimensions[1] + ", maximum "
                    + MAX_TEXTURE_SIZE + "×" + MAX_TEXTURE_SIZE;
        }
        return null;
    }

    /**
     * Les dimensions d'un PNG, lues dans son en-tête — {@code null} si ce n'en est pas un.
     *
     * <p>Un PNG commence par une signature de huit octets, puis un bloc {@code IHDR} dont
     * les deux premiers entiers sont la largeur et la hauteur. Vingt-quatre octets
     * suffisent donc, là où décoder l'image entière demanderait de l'allouer d'abord —
     * ce qui est précisément ce qu'on cherche à éviter.
     */
    static int @Nullable [] pngSize(Path file) {
        byte[] header = new byte[24];
        try (var in = Files.newInputStream(file)) {
            if (in.readNBytes(header, 0, 24) < 24) {
                return null;
            }
        } catch (IOException e) {
            return null;
        }
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        for (int i = 0; i < signature.length; i++) {
            if (header[i] != signature[i]) {
                return null;
            }
        }
        if (header[12] != 'I' || header[13] != 'H' || header[14] != 'D' || header[15] != 'R') {
            return null;
        }
        return new int[]{intAt(header, 16), intAt(header, 20)};
    }

    private static int intAt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24) | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8) | (bytes[offset + 3] & 0xFF);
    }

    private static String string(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonPrimitive()
                ? json.get(key).getAsString() : "";
    }
}
