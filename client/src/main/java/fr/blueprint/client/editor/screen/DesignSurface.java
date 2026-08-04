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
 * <p>Le facteur d'échelle était un <b>entier</b>, au nom de la netteté : Minecraft aligne
 * ses textures et son texte sur les pixels, et une échelle fractionnaire donne des bords
 * baveux. L'argument tenait tant que le peintre multipliait des entiers ; il ne tient plus
 * depuis que le dessin passe par une matrice, où tout est mis à l'échelle d'un bloc — et
 * il ne tenait de toute façon pas devant le besoin réel : <b>aucun</b> facteur entier ne
 * montre 1920 unités dans les ~420 pixels que laissent les panneaux. Le zoom est donc un
 * réel, et {@link DesignCamera} le porte.
 *
 * <p>La taille du canevas, elle, est <b>choisie</b> : c'est la fenêtre qu'on simule.
 * Elle valait 320×180 en dur — la plus petite possible — donc on concevait toujours
 * dans le pire cas sans jamais voir ce que les ancres et les pourcentages donnent à la
 * taille réelle d'un joueur, alors que c'est précisément ce qu'ils expriment.
 *
 * <p>{@code originX}/{@code originY} sont le pixel <b>réel</b> de l'unité 0 — un réel,
 * parce qu'à zoom 0,35 l'origine tombe entre deux pixels et l'arrondir à chaque image
 * ferait vibrer tout le canevas d'un pixel pendant un déplacement de vue. Les bords
 * publiés ({@link #left()}, {@link #right()}…) sont eux des entiers, tous dérivés de la
 * <b>même</b> conversion : {@code right()} vaut {@code toScreenX(unitsWidth)} et non
 * {@code left() + largeur}, sans quoi les deux arrondis pourraient différer d'un pixel et
 * le cadre ne coïnciderait plus avec ce qu'il encadre.
 */
public record DesignSurface(double originX, double originY, double zoom,
                            int unitsWidth, int unitsHeight) {

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
     * La surface que donne cette caméra dans cette zone. C'est le seul chemin employé par
     * le widget : la caméra décide, la surface convertit.
     */
    public static DesignSurface of(DesignCamera camera, int areaLeft, int areaTop,
                                   int unitsWidth, int unitsHeight) {
        return new DesignSurface(areaLeft + camera.toLocalX(0), areaTop + camera.toLocalY(0),
                camera.zoom(), unitsWidth, unitsHeight);
    }

    /** Centre la zone garantie dans le rectangle donné, marge comprise. */
    public static DesignSurface fit(int areaLeft, int areaTop, int areaWidth, int areaHeight) {
        return fit(areaLeft, areaTop, areaWidth, areaHeight,
                Screen.SAFE_WIDTH, Screen.SAFE_HEIGHT);
    }

    /**
     * Centre un canevas de {@code unitsWidth} × {@code unitsHeight} unités, marge
     * comprise, au plus grand zoom qui tient.
     *
     * <p>La taille du canevas est CHOISIE : c'est la fenêtre qu'on simule. Elle valait
     * 320×180 en dur, donc on concevait toujours dans le pire cas sans jamais voir ce
     * que les ancres et les pourcentages donnent ailleurs.
     */
    public static DesignSurface fit(int areaLeft, int areaTop, int areaWidth, int areaHeight,
                                    int unitsWidth, int unitsHeight) {
        DesignCamera camera = new DesignCamera();
        camera.fit(areaWidth, areaHeight, unitsWidth, unitsHeight);
        return of(camera, areaLeft, areaTop, unitsWidth, unitsHeight);
    }

    // ------------------------------------------------------------------- les bords

    public int left() {
        return toScreenX(0);
    }

    public int top() {
        return toScreenY(0);
    }

    public int right() {
        return toScreenX(unitsWidth);
    }

    public int bottom() {
        return toScreenY(unitsHeight);
    }

    /** Le bord de la zone dessinée, marge comprise — ce que le widget découpe. */
    public int outerLeft() {
        return toScreenX(-MARGIN);
    }

    public int outerTop() {
        return toScreenY(-MARGIN);
    }

    public int outerRight() {
        return toScreenX(unitsWidth + MARGIN);
    }

    public int outerBottom() {
        return toScreenY(unitsHeight + MARGIN);
    }

    public int width() {
        return right() - left();
    }

    public int height() {
        return bottom() - top();
    }

    // ------------------------------------------------------------ les conversions

    public double toDesignX(double screenX) {
        return (screenX - originX) / zoom;
    }

    public double toDesignY(double screenY) {
        return (screenY - originY) / zoom;
    }

    public int toScreenX(double designX) {
        return (int) Math.round(originX + designX * zoom);
    }

    public int toScreenY(double designY) {
        return (int) Math.round(originY + designY * zoom);
    }

    /**
     * Une longueur en unités, convertie en pixels. Sert aux tolérances de geste, qui se
     * pensent en pixels — une poignée doit s'attraper aussi facilement à tous les zooms.
     */
    public double toPixels(double units) {
        return units * zoom;
    }

    /** L'inverse : combien d'unités valent ce nombre de pixels au zoom courant. */
    public double toUnits(double pixels) {
        return pixels / zoom;
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
        return screenX >= left() && screenX < right() && screenY >= top() && screenY < bottom();
    }
}
