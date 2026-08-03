package fr.blueprint.client.pack;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Un pack de script lu depuis {@code blueprint/scripts/<nom>/} (story 10.5) : le dossier
 * qu'un joueur donne à un autre, avec le menu et ses images dedans.
 *
 * <p>Ce n'est <b>pas</b> un pack de ressources Minecraft. Il ne remplace aucune texture
 * du jeu, il n'a pas de {@code pack.mcmeta}, il ne demande pas de redémarrage. Il ajoute
 * seulement des images sous des noms qui n'existent que pour Blueprint — ce qui le rend
 * inoffensif pour le reste du jeu, et rechargeable à chaud.
 *
 * <p>Purement descriptif : aucune texture n'est décodée ici, seulement décrite. C'est ce
 * qui rend la lecture d'un pack testable sans client, alors que le téléversement sur la
 * carte graphique ne l'est pas.
 */
public record ScriptPack(String name, String version, String author, String description,
                         Map<String, Path> textures, @Nullable Path blueprintFile) {

    /** Le nom du fichier de description, à la racine du dossier du pack. */
    public static final String MANIFEST = "pack.json";

    public ScriptPack {
        if (!validName(name)) {
            throw new IllegalArgumentException("nom de pack invalide : « " + name + " »");
        }
        version = version == null ? "" : version;
        author = author == null ? "" : author;
        description = description == null ? "" : description;
        // LinkedHashMap non modifiable, et NON Map.copyOf : celui-ci rend une map
        // immuable mais dont l'ordre d'itération dépend du hachage. Les images auraient
        // alors changé d'ordre d'un lancement à l'autre — dans la liste des packs comme
        // dans les suggestions de l'éditeur, où c'est le genre d'instabilité qu'on
        // attribue à tort à un défaut du jeu.
        textures = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(textures));
    }

    /**
     * Un nom de pack sert d'espace de nom d'identifiant Minecraft : minuscules, chiffres,
     * {@code _} et {@code -}. Le contraindre ici plutôt qu'à l'usage évite qu'un pack se
     * charge puis rende introuvables toutes ses textures, ce qui ressemblerait à un
     * défaut du mod là où c'est un nom de dossier à changer.
     */
    public static boolean validName(@Nullable String candidate) {
        if (candidate == null || candidate.isEmpty() || candidate.length() > 64) {
            return false;
        }
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-')) {
                return false;
            }
        }
        return true;
    }

    /**
     * Le nom d'une texture tel qu'il s'écrit dans un écran : {@code <pack>/<fichier>},
     * sans l'extension. C'est aussi l'identifiant sous lequel elle est enregistrée.
     */
    public String reference(String texture) {
        return name + "/" + texture;
    }

    public boolean has(String texture) {
        return textures.containsKey(texture);
    }

    /** Le libellé montré dans la liste des packs — nom, version et compte d'images. */
    public String summary() {
        return name + (version.isEmpty() ? "" : " " + version)
                + " — " + textures.size() + (textures.size() == 1 ? " image" : " images")
                + (author.isEmpty() ? "" : " (" + author + ")");
    }
}
