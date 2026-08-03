package fr.blueprint.client.pack;

import com.mojang.blaze3d.platform.NativeImage;
import fr.blueprint.core.BlueprintMod;
import fr.blueprint.core.graph.screen.PackRef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Les textures des packs, côté carte graphique (story 10.5).
 *
 * <p>La séparation avec {@link ScriptPackLoader} est délibérée : <b>lire</b> un pack se
 * teste sans client, <b>téléverser</b> une image ne se teste pas du tout. Tout ce qui
 * décide — les bornes, le refus, le nommage des erreurs — vit du côté testable ; il ne
 * reste ici que l'appel au moteur.
 *
 * <p>Chaque texture est enregistrée sous {@code blueprint:pack/<pack>/<image>}. L'espace
 * de nom reste {@code blueprint} : un pack ne peut donc pas, même en le voulant,
 * remplacer une texture du jeu ou d'un autre mod.
 */
public final class PackTextures {

    private static final Map<String, ScriptPack> PACKS = new LinkedHashMap<>();
    private static final Set<Identifier> REGISTERED = new LinkedHashSet<>();
    private static List<ScriptPackLoader.Rejection> rejections = List.of();

    private PackTextures() {
    }


    /**
     * Combien de fois les packs ont été rechargés. Ce qui en dépend — les caches de
     * texture des écrans ouverts — s'en sert pour se vider au bon moment, sans qu'aucun
     * d'eux ait à s'enregistrer quelque part.
     */
    private static int generation;

    /**
     * L'identifiant sous lequel {@code <pack>/<image>} est enregistré — la MÊME écriture
     * que celle du modèle ({@link PackRef}). Deux conventions séparées auraient fini par
     * diverger d'un caractère, et un menu aurait marché à l'édition sans marcher en jeu.
     */
    public static Identifier identifier(String pack, String texture) {
        return Identifier.fromNamespaceAndPath(BlueprintMod.MOD_ID,
                PackRef.PREFIX + pack + "/" + texture);
    }

    /** Cet identifiant désigne-t-il une image de pack ? */
    public static boolean isPackTexture(Identifier texture) {
        return PackRef.isPackTexture(texture);
    }

    public static int generation() {
        return generation;
    }

    public static Map<String, ScriptPack> packs() {
        return Map.copyOf(PACKS);
    }

    public static List<ScriptPackLoader.Rejection> rejections() {
        return rejections;
    }

    /**
     * Le pack qui manque pour cette texture, ou {@code null} si tout va bien.
     *
     * <p>C'est ce qui permet au remplaçant d'être <b>nommé</b> plutôt que muet (AC3) : un
     * joueur doit apprendre <i>ce qu'il lui manque</i>, pas seulement que quelque chose
     * manque — la même promesse que les nœuds fantômes, qui nomment le mod absent.
     */
    public static @Nullable String missingPackOf(Identifier texture) {
        String pack = PackRef.packOf(texture);
        if (pack == null) {
            return null;
        }
        ScriptPack loaded = PACKS.get(pack);
        return loaded != null && loaded.has(PackRef.fileOf(texture)) ? null : pack;
    }

    /**
     * Relit tous les packs et téléverse leurs images. Rend le rapport de ce qui a été
     * écarté, pour que la commande puisse le montrer au joueur.
     *
     * <p>Les anciennes textures sont <b>libérées</b> d'abord : sans cela, recharger dix
     * fois laisserait dix jeux de textures sur la carte, et le joueur qui ajuste son pack
     * en rechargeant à chaque essai est précisément celui qui rechargera dix fois.
     */
    public static ScriptPackLoader.Result reload(Path scriptsDir) {
        releaseAll();
        ScriptPackLoader.Result result = ScriptPackLoader.load(scriptsDir);

        List<ScriptPackLoader.Rejection> problems = new ArrayList<>(result.rejections());
        var manager = Minecraft.getInstance().getTextureManager();
        for (ScriptPack pack : result.packs()) {
            Map<String, Path> kept = new LinkedHashMap<>();
            for (var entry : pack.textures().entrySet()) {
                Identifier id = identifier(pack.name(), entry.getKey());
                try (InputStream in = Files.newInputStream(entry.getValue())) {
                    NativeImage image = NativeImage.read(in);
                    manager.register(id, new DynamicTexture(id::toString, image));
                    REGISTERED.add(id);
                    kept.put(entry.getKey(), entry.getValue());
                } catch (IOException | RuntimeException e) {
                    // Le fichier a passé les bornes mais son contenu est corrompu : on
                    // le nomme et on garde le reste du pack. Une image illisible ne doit
                    // pas coûter au joueur les neuf autres.
                    problems.add(new ScriptPackLoader.Rejection(pack.name(),
                            entry.getKey() + ".png : décodage impossible (" + e + ")"));
                }
            }
            PACKS.put(pack.name(), new ScriptPack(pack.name(), pack.version(), pack.author(),
                    pack.description(), kept, pack.blueprintFile()));
        }

        rejections = List.copyOf(problems);
        generation++;
        BlueprintMod.LOGGER.info("Packs de script : {} chargé(s), {} écarté(s)",
                PACKS.size(), rejections.size());
        for (var rejection : rejections) {
            BlueprintMod.LOGGER.warn("Pack « {} » : {}", rejection.pack(), rejection.detail());
        }
        return new ScriptPackLoader.Result(result.packs(), rejections);
    }

    private static void releaseAll() {
        var manager = Minecraft.getInstance().getTextureManager();
        for (Identifier id : REGISTERED) {
            manager.release(id);
        }
        REGISTERED.clear();
        PACKS.clear();
    }
}
