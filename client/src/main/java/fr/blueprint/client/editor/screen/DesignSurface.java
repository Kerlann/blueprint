package fr.blueprint.client.editor.screen;

import fr.blueprint.core.graph.screen.Screen;

/**
 * La correspondance entre la surface de conception (320×180 unités) et le rectangle du
 * widget qui l'affiche.
 *
 * <p>Un record pur, et pas trois lignes d'arithmétique recopiées dans le rendu puis dans
 * le clic : ces deux-là <b>doivent</b> utiliser exactement la même transformation, sinon
 * l'auteur clique à côté de ce qu'il voit. C'est la leçon de la story 5.12, où le tracé
 * d'un fil et son hit-test partagent le même {@code controlOffset} pour cette raison.
 *
 * <p>Le facteur d'échelle est un <b>entier</b> : Minecraft dessine des textures et du
 * texte alignés sur les pixels, et une échelle fractionnaire donne des bords baveux et
 * un texte flou — l'aperçu cesserait de ressembler au résultat.
 */
public record DesignSurface(int left, int top, int scale) {

    /** L'aperçu occupe la place disponible sans jamais dépasser ce grossissement. */
    public static final int MAX_SCALE = 6;

    /**
     * Marge visible autour de la zone garantie, en unités.
     *
     * <p>Elle n'est pas décorative. Un élément à la racine <b>a le droit</b> de déborder
     * des 320×180 — le modèle n'en fait qu'un avertissement, pour permettre les menus
     * qui visent les grandes fenêtres. Sans marge, un tel élément serait posé puis
     * <i>invisible</i> dans le concepteur : l'auteur ne pourrait plus ni le voir ni le
     * rattraper. La marge le montre, et le cadre dit où passe la limite (AC3b).
     */
    public static final int MARGIN = 24;

    /**
     * Centre la surface dans le rectangle donné, au plus grand facteur entier qui tient
     * — <b>marge comprise</b>.
     */
    public static DesignSurface fit(int areaLeft, int areaTop, int areaWidth, int areaHeight) {
        int outerUnitsW = Screen.SAFE_WIDTH + MARGIN * 2;
        int outerUnitsH = Screen.SAFE_HEIGHT + MARGIN * 2;
        int scale = Math.clamp(Math.min(areaWidth / outerUnitsW, areaHeight / outerUnitsH),
                1, MAX_SCALE);
        return new DesignSurface(
                areaLeft + (areaWidth - outerUnitsW * scale) / 2 + MARGIN * scale,
                areaTop + (areaHeight - outerUnitsH * scale) / 2 + MARGIN * scale,
                scale);
    }

    /** Le bord de la zone dessinée, marge comprise — ce que le widget découpe. */
    public int outerLeft() {
        return left - MARGIN * scale;
    }

    public int outerTop() {
        return top - MARGIN * scale;
    }

    public int outerRight() {
        return right() + MARGIN * scale;
    }

    public int outerBottom() {
        return bottom() + MARGIN * scale;
    }

    public int width() {
        return Screen.SAFE_WIDTH * scale;
    }

    public int height() {
        return Screen.SAFE_HEIGHT * scale;
    }

    public int right() {
        return left + width();
    }

    public int bottom() {
        return top + height();
    }

    public double toDesignX(double screenX) {
        return (screenX - left) / (double) scale;
    }

    public double toDesignY(double screenY) {
        return (screenY - top) / (double) scale;
    }

    public int toScreenX(double designX) {
        return left + (int) Math.round(designX * scale);
    }

    public int toScreenY(double designY) {
        return top + (int) Math.round(designY * scale);
    }

    /**
     * Le point est-il sur la zone de travail ? La <b>marge en fait partie</b> : un
     * élément qui déborde s'y trouve, et il doit rester saisissable — sinon le
     * concepteur laisserait poser ce qu'il ne laisse plus rattraper.
     */
    public boolean contains(double screenX, double screenY) {
        return screenX >= outerLeft() && screenX < outerRight()
                && screenY >= outerTop() && screenY < outerBottom();
    }

    /** Le point est-il dans la zone GARANTIE (320×180) ? Ce que verront tous les joueurs. */
    public boolean insideSafeArea(double screenX, double screenY) {
        return screenX >= left && screenX < right() && screenY >= top && screenY < bottom();
    }
}
