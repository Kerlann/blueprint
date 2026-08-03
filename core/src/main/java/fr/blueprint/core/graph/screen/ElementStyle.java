package fr.blueprint.core.graph.screen;

/**
 * L'apparence d'un élément, par ÉTAT (story 10.1, AC1d).
 *
 * <p>Les états ne sont pas un raffinement : un bouton qui ne change pas au survol
 * <b>passe pour cassé</b>. C'est le premier retour visuel qu'un joueur attend, et
 * l'ajouter après coup obligerait à reprendre chaque style déjà écrit.
 *
 * <p>Toutes les couleurs sont en ARGB. Une valeur d'état à zéro signifie « comme
 * l'état normal » — un élément qui ne réagit pas se décrit alors en une ligne, et un
 * bouton qui réagit n'écrit que ce qui change.
 */
public record ElementStyle(int background, int border, int borderWidth, int textColor,
                           int hoverBackground, int pressedBackground, int disabledBackground,
                           int padding, TextAlign align) {

    /** Alignement horizontal du texte dans l'élément. */
    public enum TextAlign {
        LEFT, CENTER, RIGHT
    }

    /** Neutre et lisible : ce que voit un élément qu'on vient de poser. */
    public static final ElementStyle DEFAULT = new ElementStyle(
            0xC0141519, 0xFF3A3D42, 1, 0xFFE6E6E6,
            0xC02F3A55, 0xC01F2735, 0x60141519,
            2, TextAlign.LEFT);

    public ElementStyle {
        if (borderWidth < 0 || padding < 0) {
            throw new IllegalArgumentException(
                    "bordure ou marge négative : " + borderWidth + ", " + padding);
        }
        if (align == null) {
            align = TextAlign.LEFT;
        }
    }

    /** Le fond effectif d'un état ; zéro = on retombe sur le fond normal. */
    public int backgroundFor(boolean hovered, boolean pressed, boolean enabled) {
        if (!enabled) {
            return disabledBackground != 0 ? disabledBackground : background;
        }
        if (pressed && pressedBackground != 0) {
            return pressedBackground;
        }
        if (hovered && hoverBackground != 0) {
            return hoverBackground;
        }
        return background;
    }
}
