package fr.blueprint.core.content;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Le <b>pack de ressources</b> qu'un contenu déclaré exige — story 11.2.
 *
 * <p>Un item enregistré par la 11.1 existe pour de bon : il se donne, se range, traverse
 * le réseau. Il s'affiche pourtant en damier magenta, parce qu'un item n'a pas de texture
 * — il a un <b>modèle</b>, et un modèle est une ressource, c'est-à-dire quelque chose que
 * le client lit dans un pack. Aucun code d'enregistrement ne peut y suppléer : c'est la
 * troisième des trois contraintes annoncées en tête de l'épic.
 *
 * <p>Cette classe ne touche à rien. Elle décrit ce que le pack <b>devrait</b> contenir, en
 * mémoire, à partir des définitions — ce qui la rend vérifiable sans client, sans jeu et
 * sans disque. {@link ContentPackWriter} s'occupe de la partie qui écrit, et elle seule.
 *
 * <h2>Trois fichiers par item, et pourquoi</h2>
 * <ul>
 *   <li>{@code assets/blueprint/items/<nom>.json} — la <i>définition de modèle d'item</i>,
 *       introduite en 1.21.4 : c'est elle que le jeu lit en premier, et son absence est
 *       exactement ce qui donne le damier ;</li>
 *   <li>{@code assets/blueprint/models/item/<nom>.json} — le modèle proprement dit, un
 *       {@code item/generated} à plat, celui de tous les objets qui ne sont pas des
 *       blocs ;</li>
 *   <li>{@code assets/blueprint/textures/item/<nom>.png} — la copie du PNG déposé à côté
 *       du JSON de définition.</li>
 * </ul>
 *
 * <p><b>Sans PNG, aucun des trois n'est écrit.</b> Pointer un modèle vers une texture
 * absente donnerait le damier <i>quand même</i>, mais sans que le journal dise pourquoi ;
 * ne rien écrire laisse le jeu produire son message habituel, et la commande
 * {@code /blueprint content} dit lequel des items n'a pas d'image. Un placeholder « joli »
 * aurait été pire : un item qui a l'air fini est un item qu'on oublie de finir.
 */
public record ContentPack(Map<String, String> files, Map<String, Path> textures,
                          List<String> rejected) {

    /**
     * Version de format des packs de ressources du jeu compilé.
     *
     * <p>Constante de compilation : elle est recopiée dans notre jar à la compilation, ce
     * qui est exactement ce qu'on veut — le pack déclare la version pour laquelle il a
     * été construit, et non celle d'un jeu qu'on n'a jamais vu.
     *
     * <p>Mojang la déclare obsolète au profit d'une lecture à l'exécution. Celle-ci
     * chargerait les métadonnées de version dans un test sans jeu, et surtout rendrait la
     * valeur variable : l'empreinte du pack changerait alors sans que le pack ait changé,
     * et le client rechargerait ses ressources pour rien. Une constante figée à la
     * compilation est ici la bonne sémantique, pas un raccourci.
     */
    @SuppressWarnings("deprecation")
    public static final int PACK_FORMAT = net.minecraft.SharedConstants.RESOURCE_PACK_FORMAT_MAJOR;

    /**
     * Côté maximal d'une texture d'item.
     *
     * <p>Une icône d'objet est dessinée sur seize pixels d'écran. Au-delà de mille
     * vingt-quatre, on paye de l'atlas et de la mémoire graphique pour une différence que
     * personne ne peut voir — et l'atlas est partagé avec tout le reste du jeu.
     */
    public static final int MAX_TEXTURE_SIZE = 1024;

    /** Le fichier qui dit ce que le pack contenait la dernière fois. */
    public static final String STAMP = ".blueprint-stamp";

    public ContentPack {
        // LinkedHashMap et non Map.copyOf : l'ordre est ce sur quoi l'empreinte est
        // calculée, et Map.copyOf ne le préserve pas. Deux démarrages identiques
        // doivent donner la MÊME empreinte, sans quoi le pack serait réécrit et le
        // client rechargé à chaque lancement — c'est le même piège que la 11.1.
        files = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(files));
        textures = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(textures));
        rejected = List.copyOf(rejected);
    }

    /** Ce que le pack devrait contenir pour ces items. */
    public static ContentPack of(Collection<ItemDefinition> items) {
        return of(items, List.of());
    }

    /** Ce que le pack devrait contenir pour ces définitions. */
    public static ContentPack of(Collection<ItemDefinition> items,
                                 Collection<BlockDefinition> blocks) {
        Map<String, String> files = new LinkedHashMap<>();
        Map<String, Path> textures = new LinkedHashMap<>();
        List<String> rejected = new ArrayList<>();

        files.put("pack.mcmeta", """
                {
                  "pack": {
                    "pack_format": %d,
                    "description": "Contenu déclaré par Blueprint — régénéré au démarrage."
                  }
                }
                """.formatted(PACK_FORMAT));

        for (ItemDefinition item : items) {
            String name = item.id().getPath();
            String refusal = textureRefusal(item);
            if (refusal != null) {
                rejected.add(name + " : " + refusal);
                continue;
            }
            String model = item.id().getNamespace() + ":item/" + name;
            files.put("assets/%s/items/%s.json".formatted(item.id().getNamespace(), name), """
                    {
                      "model": {
                        "type": "minecraft:model",
                        "model": "%s"
                      }
                    }
                    """.formatted(model));
            files.put("assets/%s/models/item/%s.json".formatted(item.id().getNamespace(), name), """
                    {
                      "parent": "minecraft:item/generated",
                      "textures": {
                        "layer0": "%s"
                      }
                    }
                    """.formatted(model));
            textures.put("assets/%s/textures/item/%s.png".formatted(item.id().getNamespace(), name),
                    Path.of(item.texture()));
        }

        for (BlockDefinition block : blocks) {
            String space = block.id().getNamespace();
            String name = block.id().getPath();
            String refusal = textureRefusal(block.id(), block.hasTexture() ? block.texture() : null);
            if (refusal != null) {
                rejected.add(name + " : " + refusal);
                continue;
            }
            String model = space + ":block/" + name;
            // Un état unique, sans variante : c'est ce qui distingue un bloc plein d'une
            // porte ou d'une clôture, et c'est tout ce que cette story promet.
            files.put("assets/%s/blockstates/%s.json".formatted(space, name), """
                    {
                      "variants": {
                        "": {
                          "model": "%s"
                        }
                      }
                    }
                    """.formatted(model));
            files.put("assets/%s/models/block/%s.json".formatted(space, name), """
                    {
                      "parent": "minecraft:block/cube_all",
                      "textures": {
                        "all": "%s:block/%s"
                      }
                    }
                    """.formatted(space, name));
            // L'item du bloc HÉRITE du modèle de bloc : c'est ainsi qu'il s'affiche en
            // cube dans la main et dans l'inventaire, et non en vignette plate.
            files.put("assets/%s/items/%s.json".formatted(space, name), """
                    {
                      "model": {
                        "type": "minecraft:model",
                        "model": "%s"
                      }
                    }
                    """.formatted(model));
            textures.put("assets/%s/textures/block/%s.png".formatted(space, name),
                    Path.of(block.texture()));
        }
        return new ContentPack(files, textures, rejected);
    }

    private static @Nullable String textureRefusal(ItemDefinition item) {
        return textureRefusal(item.id(), item.hasTexture() ? item.texture() : null);
    }

    /** {@code null} si la texture convient, sinon la raison, dite au joueur. */
    private static @Nullable String textureRefusal(net.minecraft.resources.Identifier id,
                                                   @Nullable String texture) {
        if (texture == null || texture.isBlank()) {
            return "aucune image — déposez « " + id.getPath() + ".png » à côté du JSON";
        }
        Path png = Path.of(texture);
        int[] size = PngHeader.size(png);
        if (size == null) {
            return "« " + png.getFileName() + " » n'est pas un PNG lisible";
        }
        if (size[0] > MAX_TEXTURE_SIZE || size[1] > MAX_TEXTURE_SIZE) {
            return size[0] + "×" + size[1] + ", maximum " + MAX_TEXTURE_SIZE + "×"
                    + MAX_TEXTURE_SIZE;
        }
        return null;
    }

    /** Les items réellement habillés — ceux dont le modèle a été écrit. */
    public int dressed() {
        return textures.size();
    }

    /**
     * L'empreinte de ce que le pack devrait contenir, textures comprises.
     *
     * <p>C'est elle qui décide s'il faut réécrire et recharger. Elle porte sur le
     * <b>contenu</b> et non sur les dates : une horloge qui recule, une copie de dossier,
     * un {@code touch} — autant de façons de faire mentir une date de modification, et
     * autant de rechargements inutiles au démarrage. Une image modifiée sans changer de
     * taille, à l'inverse, doit être vue : c'est pourquoi les octets des PNG y entrent.
     *
     * <p>Une texture devenue illisible entre le plan et l'empreinte compte comme absente
     * plutôt que de faire échouer : le pack sera simplement réécrit au prochain
     * démarrage.
     */
    public String stamp() {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 absent de la machine virtuelle", e);
        }
        digest.update(Integer.toString(PACK_FORMAT).getBytes(StandardCharsets.UTF_8));
        for (var entry : files.entrySet()) {
            digest.update(entry.getKey().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(entry.getValue().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
        }
        for (var entry : textures.entrySet()) {
            digest.update(entry.getKey().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            try {
                digest.update(Files.readAllBytes(entry.getValue()));
            } catch (IOException e) {
                digest.update("illisible".getBytes(StandardCharsets.UTF_8));
            }
            digest.update((byte) 0);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Tous les chemins que ce pack revendique — ceux-là, et pas un de plus. */
    public List<String> paths() {
        List<String> all = new ArrayList<>(files.keySet());
        all.addAll(textures.keySet());
        return List.copyOf(all);
    }

    /** Vrai si un item de cet identifiant a bien reçu son modèle. */
    public boolean covers(Identifier id) {
        return files.containsKey("assets/%s/items/%s.json"
                .formatted(id.getNamespace(), id.getPath()));
    }
}
