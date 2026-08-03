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
     * Centre la surface 320×180 dans le rectangle donné, au plus grand facteur entier
     * qui tient.
     */
    public static DesignSurface fit(int areaLeft, int areaTop, int areaWidth, int areaHeight) {
        int scale = Math.min(areaWidth / Screen.SAFE_WIDTH, areaHeight / Screen.SAFE_HEIGHT);
        scale = Math.clamp(scale, 1, MAX_SCALE);
        int width = Screen.SAFE_WIDTH * scale;
        int height = Screen.SAFE_HEIGHT * scale;
        return new DesignSurface(areaLeft + (areaWidth - width) / 2,
                areaTop + (areaHeight - height) / 2, scale);
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

    /** Le point est-il sur la surface ? Hors d'elle, un clic ne conçoit rien. */
    public boolean contains(double screenX, double screenY) {
        return screenX >= left && screenX < right() && screenY >= top && screenY < bottom();
    }
}
