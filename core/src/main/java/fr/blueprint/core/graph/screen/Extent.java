package fr.blueprint.core.graph.screen;

/**
 * Une longueur d'écran : un nombre d'unités, ou une fraction du parent, bornée
 * (story 10.1, AC1b).
 *
 * <p>Les trois ensemble et pas au choix. Une taille fixe ne suit pas la fenêtre ; un
 * pourcentage seul devient illisible en 320×180 et démesuré en 960×540. Les bornes
 * sont ce qui empêche les deux extrêmes.
 *
 * @param value    unités si {@code relative} est faux, fraction de 0 à 1 sinon
 * @param relative vrai si {@code value} est une fraction du parent
 * @param min      borne basse en unités, jamais dépassée vers le bas
 * @param max      borne haute en unités ; {@code 0} = pas de borne
 */
public record Extent(double value, boolean relative, double min, double max) {

    public Extent {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("longueur non finie : " + value);
        }
        if (min < 0 || max < 0) {
            throw new IllegalArgumentException("bornes négatives : " + min + ".." + max);
        }
        if (max > 0 && max < min) {
            throw new IllegalArgumentException("borne haute sous la basse : "
                    + min + ".." + max);
        }
    }

    /** Une taille fixe, sans borne. */
    public static Extent of(double units) {
        return new Extent(units, false, 0, 0);
    }

    /** Une fraction du parent (0,5 = la moitié), bornée. */
    public static Extent percent(double fraction, double min, double max) {
        return new Extent(fraction, true, min, max);
    }

    /**
     * La longueur en unités, une fois la place du parent connue.
     *
     * <p>C'est la <b>seule</b> résolution du produit : le concepteur (10.2) et le rendu
     * en jeu (10.3) l'appellent tous deux. Deux calculs distincts divergeraient, et
     * l'auteur découvrirait l'écart une fois en jeu — le même piège que la géométrie
     * du clic sur un fil, partagée avec son tracé depuis la story 5.12.
     */
    public double resolve(double parentSize) {
        double raw = relative ? value * parentSize : value;
        double bounded = Math.max(min, raw);
        return max > 0 ? Math.min(max, bounded) : bounded;
    }
}
