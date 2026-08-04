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
                           int padding, TextAlign align, boolean wrap) {

    /** Alignement horizontal du texte dans l'élément. */
    public enum TextAlign {
        LEFT, CENTER, RIGHT
    }

    /**
     * Neutre et lisible : ce que voit un élément qu'on vient de poser.
     *
     * <p>La bordure a été <b>éclaircie</b> (story 10.6, AC4). À {@code 0xFF3A3D42} elle
     * ne contrastait qu'à 1,74:1 avec son propre fond — sous le seuil de 3:1 des
     * éléments d'interface, c'est-à-dire invisible. Or c'est elle qui dit où finit un
     * élément quand le fond est translucide, et un menu dont on ne voit pas les bords est
     * un menu dont on ne sait pas où cliquer. {@code ElementStyleContrastTest} le mesure
     * désormais, sur les quatre états.
     */
    public static final ElementStyle DEFAULT = new ElementStyle(
            0xC0141519, 0xFF6B7280, 1, 0xFFE6E6E6,
            0xC02F3A55, 0xC01F2735, 0x60141519,
            2, TextAlign.LEFT, false);

    /**
     * Le texte revient-il à la ligne dans son cadre ?
     *
     * <p>Faux par défaut, et c'est le bon défaut : un libellé de bouton doit tenir sur une
     * ligne, et le tronquer dit à l'auteur que son texte est trop long. Mais un menu
     * complet a besoin de <b>paragraphes</b> — une description d'objet, une règle du
     * serveur, une réponse de dialogue — et sans retour à la ligne il fallait les découper
     * à la main en autant d'étiquettes empilées, à repositionner à chaque changement de
     * texte. Une traduction plus longue que l'original cassait la mise en page, ce que
     * l'auteur ne voyait pas puisqu'il ne lit pas les vingt langues de son serveur.
     *
     * <p>Vit dans le style et non dans les options : c'est une propriété d'apparence, au
     * même titre que l'alignement, et un style nommé « paragraphe » doit pouvoir la
     * porter pour tous les textes qui le suivent.
     */
    public ElementStyle withWrap(boolean newWrap) {
        return new ElementStyle(background, border, borderWidth, textColor,
                hoverBackground, pressedBackground, disabledBackground, padding, align,
                newWrap);
    }

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
