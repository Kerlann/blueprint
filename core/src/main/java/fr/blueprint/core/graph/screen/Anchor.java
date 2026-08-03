package fr.blueprint.core.graph.screen;

/**
 * Coin ou bord de référence d'un élément dans son parent (story 10.1, AC1).
 *
 * <p>Une position en unités depuis le coin haut-gauche paraît suffisante et ne l'est
 * pas : Minecraft dessine en <b>unités d'interface</b>, pas en pixels, et le
 * <i>GUI scale</i> divise déjà la résolution. Un écran 1280×720 en <i>scale</i> 4
 * n'offre que <b>320×180 unités</b> — un bouton posé « à 400 du bord » ne rentre alors
 * chez personne, quelle que soit la taille du moniteur.
 *
 * <p>L'ancre dit <b>par rapport à quoi</b> la position est comptée ; le pourcentage
 * ({@link ScreenElement}) dit comment la taille suit la place disponible. Les deux
 * ensemble, plus les bornes, sont ce qui fait tenir un écran de 320×180 à 960×540.
 */
public enum Anchor {
    TOP_LEFT(0, 0),
    TOP_CENTER(0.5, 0),
    TOP_RIGHT(1, 0),
    MIDDLE_LEFT(0, 0.5),
    CENTER(0.5, 0.5),
    MIDDLE_RIGHT(1, 0.5),
    BOTTOM_LEFT(0, 1),
    BOTTOM_CENTER(0.5, 1),
    BOTTOM_RIGHT(1, 1);

    private final double fx;
    private final double fy;

    Anchor(double fx, double fy) {
        this.fx = fx;
        this.fy = fy;
    }

    /** Fraction horizontale du parent : 0 à gauche, 0,5 au centre, 1 à droite. */
    public double fractionX() {
        return fx;
    }

    public double fractionY() {
        return fy;
    }
}
