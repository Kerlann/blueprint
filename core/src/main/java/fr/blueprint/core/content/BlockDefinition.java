package fr.blueprint.core.content;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Un <b>bloc déclaré</b> — story 11.3.
 *
 * <p>Même contrat que {@link ItemDefinition} : le modèle est pur, les valeurs impossibles
 * sont <b>bornées</b> plutôt que refusées, et le fichier fautif n'emporte jamais les
 * autres. Ce n'est pas de la complaisance : ce code tourne avant l'écran titre, et un
 * refus y coûte le démarrage du jeu.
 *
 * <h2>La dureté et l'outil</h2>
 * <p>Un bloc de Minecraft ne sait pas seul qu'une pioche le mine plus vite : cette
 * information vit dans un <b>tag</b>, c'est-à-dire dans un datapack, c'est-à-dire dans une
 * sauvegarde de monde — hors d'atteinte à l'initialisation du mod, exactement comme les
 * registres l'étaient pour la 11.1.
 *
 * <p>La famille d'outil est donc portée ici, par la définition, et appliquée par le bloc
 * lui-même. C'est le seul endroit d'où l'on puisse le faire sans écrire dans la sauvegarde
 * de quelqu'un.
 */
public record BlockDefinition(Identifier id, String name, boolean translate,
                              float hardness, float resistance, Tool tool,
                              boolean requiresTool, int light, Sound sound,
                              @Nullable String texture) {

    /** La famille d'outil qui mine ce bloc à sa vitesse normale. */
    public enum Tool {
        /** Aucun outil ne va plus vite qu'une main — terre, laine, feuillage. */
        NONE,
        PICKAXE,
        AXE,
        SHOVEL,
        HOE
    }

    /** Le bruit du bloc, restreint à ce qui se comprend sans écouter. */
    public enum Sound {
        STONE,
        WOOD,
        METAL,
        GLASS,
        WOOL,
        GRAVEL
    }

    /** Dureté maximale. L'obsidienne est à 50 ; au-delà, plus personne ne mine. */
    public static final float MAX_HARDNESS = 100f;
    /** Résistance maximale aux explosions. Le bedrock est à 3 600 000. */
    public static final float MAX_RESISTANCE = 3_600_000f;

    public BlockDefinition {
        // Bornées, pas refusées : une dureté négative est une faute de frappe, et perdre
        // le bloc pour cela obligerait à chercher pourquoi il a disparu. Voir 11.1.
        hardness = clamp(hardness, 0f, MAX_HARDNESS);
        resistance = clamp(resistance, 0f, MAX_RESISTANCE);
        light = Math.clamp(light, 0, 15);
        tool = tool == null ? Tool.NONE : tool;
        sound = sound == null ? Sound.STONE : sound;
        name = name == null ? "" : name;
    }

    private static float clamp(float value, float min, float max) {
        return Float.isNaN(value) ? min : Math.clamp(value, min, max);
    }

    /** Un bloc de pierre ordinaire, pour ce qui n'est pas précisé. */
    public static BlockDefinition of(Identifier id) {
        return new BlockDefinition(id, "", false, 1.5f, 6f, Tool.PICKAXE, false, 0,
                Sound.STONE, null);
    }

    public boolean hasTexture() {
        return texture != null && !texture.isEmpty();
    }

    /** La famille nommée dans le JSON, ou {@code NONE} si elle est inconnue. */
    public static Tool toolOf(String raw) {
        return valueOf(Tool.class, raw, Tool.NONE);
    }

    /** Le bruit nommé dans le JSON, ou {@code STONE} s'il est inconnu. */
    public static Sound soundOf(String raw) {
        return valueOf(Sound.class, raw, Sound.STONE);
    }

    private static <E extends Enum<E>> E valueOf(Class<E> type, String raw, E fallback) {
        if (raw == null) {
            return fallback;
        }
        String upper = raw.toUpperCase(Locale.ROOT);
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(upper)) {
                return constant;
            }
        }
        return fallback;
    }

    /**
     * La vitesse de minage, à partir de celle que l'outil tenu obtiendrait sur le bloc de
     * référence de sa famille.
     *
     * <p><b>Fonction pure, et c'est tout l'intérêt.</b> Elle décide seule si un bloc
     * déclaré se mine vite, lentement, ou pas du tout — sans monde, sans joueur, sans
     * registre. La formule du jeu est irréprochable ; c'est la donnée qu'elle consulte,
     * les tags, qui nous est inaccessible. On lui fournit donc la vitesse autrement.
     *
     * @param referenceSpeed ce que l'objet tenu obtiendrait sur le bloc vanille
     *                       représentatif de la famille (la pierre pour une pioche) — 1
     *                       pour une main nue ou un outil de la mauvaise famille
     * @return le multiplicateur à appliquer, jamais inférieur à 1
     */
    public float miningSpeed(float referenceSpeed) {
        if (tool == Tool.NONE) {
            // Rien ne va plus vite qu'une main : annoncer le contraire ferait chercher
            // au joueur un outil qui n'existe pas.
            return 1f;
        }
        return Float.isNaN(referenceSpeed) || referenceSpeed < 1f ? 1f : referenceSpeed;
    }

    /**
     * Le bloc lâche-t-il quelque chose ?
     *
     * <p>Quand un outil est <b>exigé</b>, c'est la vitesse qui en fait foi : un objet qui
     * ne mine pas plus vite que la main n'est pas le bon outil, quel que soit son nom.
     * Cela vaut aussi pour les outils des autres mods, qu'aucune liste écrite d'avance
     * n'aurait pu connaître.
     */
    public boolean dropsFor(float referenceSpeed) {
        return !requiresTool || tool == Tool.NONE || miningSpeed(referenceSpeed) > 1f;
    }
}
