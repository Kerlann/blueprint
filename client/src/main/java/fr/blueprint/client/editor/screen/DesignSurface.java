package fr.blueprint.client.editor.screen;

import fr.blueprint.core.graph.screen.Screen;

/**
 * La correspondance entre le canevas de conception et le rectangle du widget qui
 * l'affiche.
 *
 * <p>Un record pur, et pas trois lignes d'arithmétique recopiées dans le rendu puis dans
 * le clic : ces deux-là <b>doivent</b> utiliser exactement la même transformation, sinon
 * l'auteur clique à côté de ce qu'il voit. C'est la leçon de la story 5.12, où le tracé
 * d'un fil et son hit-test partagent le même {@code controlOffset} pour cette raison.
 *
 * <p>Le facteur d'échelle est un <b>entier</b> : Minecraft dessine des textures et du
 * texte alignés sur les pixels, et une échelle fractionnaire donne des bords baveux et
 * un texte flou — l'aperçu cesserait de ressembler au résultat.
 *
 * <p>La taille du canevas, elle, est <b>choisie</b> : c'est la fenêtre qu'on simule.
 * Elle valait 320×180 en dur — la plus petite possible — donc on concevait toujours
 * dans le pire cas sans jamais voir ce que les ancres et les pourcentages donnent à la
 * taille réelle d'un joueur, alors que c'est précisément ce qu'ils expriment.
 */
public record DesignSurface(int left, int top, int scale, int unitsWidth, int unitsHeight) {

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
        return fit(areaLeft, areaTop, areaWidth, areaHeight,
                Screen.SAFE_WIDTH, Screen.SAFE_HEIGHT);
    }

    /**
     * Centre un canevas de {@code unitsWidth} × {@code unitsHeight} unités, marge
     * comprise, au plus grand facteur entier qui tient.
     *
     * <p>La taille du canevas est CHOISIE : c'est la fenêtre qu'on simule. Elle valait
     * 320×180 en dur, donc on concevait toujours dans le pire cas sans jamais voir ce
     * que les ancres et les pourcentages donnent ailleurs.
     *
     * <p>Une échelle fractionnaire est possible ici, contrairement à avant : un canevas
     * de 960 unités dans une fenêtre de 900 pixels ne tiendrait à aucun facteur entier.
     * On retombe alors sur 1 et l'on découpe — mieux vaut voir une partie à la bonne
     * taille qu'un tout à une taille fausse.
     */
    public static DesignSurface fit(int areaLeft, int areaTop, int areaWidth, int areaHeight,
                                    int unitsWidth, int unitsHeight) {
        int outerUnitsW = unitsWidth + MARGIN * 2;
        int outerUnitsH = unitsHeight + MARGIN * 2;
        int scale = Math.clamp(Math.min(areaWidth / outerUnitsW, areaHeight / outerUnitsH),
                1, MAX_SCALE);
        return new DesignSurface(
                areaLeft + Math.max(0, areaWidth - outerUnitsW * scale) / 2 + MARGIN * scale,
                areaTop + Math.max(0, areaHeight - outerUnitsH * scale) / 2 + MARGIN * scale,
                scale, unitsWidth, unitsHeight);
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
        return unitsWidth * scale;
    }

    public int height() {
        return unitsHeight * scale;
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

    /**
     * Le point est-il sur le canevas lui-même, marge exclue ?
     *
     * <p>Ce n'est PAS « dans la zone garantie » : celle-ci n'est pas un rectangle fixe
     * qu'on pourrait dessiner ici. Un élément ancré en bas à droite reste visible sur
     * une petite fenêtre comme sur une grande ; un élément ancré en haut à gauche à 400
     * unités ne l'est sur aucune. La garantie dépend donc de l'ANCRE, élément par
     * élément — c'est {@code ScreenRules.outsideSafeArea} qui la calcule, et le liseré
     * orange du concepteur qui la montre.
     */
    public boolean insideCanvas(double screenX, double screenY) {
        return screenX >= left && screenX < right() && screenY >= top && screenY < bottom();
    }
}
