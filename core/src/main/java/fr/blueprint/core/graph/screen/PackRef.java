package fr.blueprint.core.graph.screen;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * La façon dont une image de pack se désigne dans un écran (story 10.5).
 *
 * <p>L'auteur écrit {@code ma_boutique/fond} ; le modèle porte
 * {@code blueprint:pack/ma_boutique/fond}. La conversion vit ici, en un seul endroit,
 * parce qu'elle est faite à quatre : le panneau de propriétés, le parseur BScript, le
 * générateur, et le chargeur de textures côté client. Quatre conversions écrites
 * séparément finiraient par diverger d'un caractère, et un menu marcherait à l'édition
 * sans marcher en jeu.
 *
 * <p>Pourquoi pas simplement {@code ma_boutique:fond} comme espace de nom ? Parce qu'un
 * pack s'appelant comme un mod installé écraserait alors les textures de ce mod. Le
 * préfixe garde tous les packs dans l'espace de {@code blueprint}, où ils ne peuvent
 * rien remplacer.
 */
public final class PackRef {

    /** Le préfixe de chemin réservé aux images de packs. */
    public static final String PREFIX = "pack/";

    private PackRef() {
    }

    /**
     * L'identifiant d'une référence écrite par un auteur.
     *
     * <p>Une référence contenant un {@code :} est prise telle quelle : c'est une texture
     * du jeu ou d'un mod, que l'auteur a le droit de viser. Sinon, {@code pack/fichier}
     * désigne un pack.
     */
    public static @Nullable Identifier texture(String reference) {
        String trimmed = reference.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.contains(":")) {
            return Identifier.tryParse(trimmed);
        }
        int slash = trimmed.indexOf('/');
        if (slash <= 0 || slash == trimmed.length() - 1) {
            return null;
        }
        return Identifier.tryParse("blueprint:" + PREFIX + trimmed);
    }

    /** L'écriture courte d'un identifiant, telle qu'elle se montre à l'auteur. */
    public static String reference(Identifier texture) {
        return isPackTexture(texture) ? texture.getPath().substring(PREFIX.length())
                : texture.toString();
    }

    public static boolean isPackTexture(Identifier texture) {
        return "blueprint".equals(texture.getNamespace())
                && texture.getPath().startsWith(PREFIX)
                && texture.getPath().indexOf('/', PREFIX.length()) > PREFIX.length();
    }

    /** Le pack dont vient cette image, ou {@code null} si elle n'en vient pas. */
    public static @Nullable String packOf(Identifier texture) {
        if (!isPackTexture(texture)) {
            return null;
        }
        String rest = texture.getPath().substring(PREFIX.length());
        return rest.substring(0, rest.indexOf('/'));
    }

    /** Le nom du fichier, sans son pack ni son extension. */
    public static @Nullable String fileOf(Identifier texture) {
        if (!isPackTexture(texture)) {
            return null;
        }
        String rest = texture.getPath().substring(PREFIX.length());
        return rest.substring(rest.indexOf('/') + 1);
    }
}
