package fr.blueprint.core.graph.screen;

/**
 * Une longueur d'écran : ce que l'élément <b>demande</b> comme place.
 *
 * <p>Quatre façons de le dire, parce qu'un menu réel les mélange :
 * <ul>
 *   <li>{@link Mode#FIXED} — tant d'unités, quoi qu'il arrive.</li>
 *   <li>{@link Mode#PERCENT} — une fraction du parent, bornée. Une taille fixe ne suit
 *       pas la fenêtre ; un pourcentage seul devient illisible en 320×180 et démesuré
 *       en 960×540. Les bornes sont ce qui empêche les deux extrêmes.</li>
 *   <li>{@link Mode#FILL} — une part de ce qui reste, au prorata du poids.
 *       <b>C'est ce qui rend les piles utilisables</b> : trois boutons {@code FILL} dans
 *       une colonne se partagent la hauteur et prennent toute la largeur, sans qu'aucune
 *       coordonnée n'ait été écrite.</li>
 *   <li>{@link Mode#HUG} — un conteneur qui épouse ses enfants.</li>
 * </ul>
 *
 * <p><b>{@code FILL} et {@code HUG} n'ont de sens que dans une passe de disposition</b>
 * ({@link ScreenLayout#solve}) : la part d'un {@code FILL} dépend de ses frères, la
 * taille d'un {@code HUG} de ses enfants. {@link #resolve} ne voit qu'un élément et
 * rend donc pour eux une approximation sûre, décrite sur la méthode.
 *
 * @param mode  comment la longueur se calcule
 * @param value unités ({@code FIXED}), fraction de 0 à 1 ({@code PERCENT}), poids
 *              ({@code FILL}) ; ignoré pour {@code HUG}
 * @param min   borne basse en unités, jamais dépassée vers le bas
 * @param max   borne haute en unités ; {@code 0} = pas de borne
 */
public record Extent(Mode mode, double value, double min, double max) {

    /** Les quatre façons de demander de la place. */
    public enum Mode {
        FIXED, PERCENT, FILL, HUG
    }

    public Extent {
        if (mode == null) {
            mode = Mode.FIXED;
        }
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
        // Un poids nul ou négatif priverait l'élément de toute place et le rendrait
        // invisible sans rien dire. Un FILL demande une part ; la plus petite est une.
        if (mode == Mode.FILL && value <= 0) {
            value = 1;
        }
    }

    /** Une taille fixe, sans borne. */
    public static Extent of(double units) {
        return new Extent(Mode.FIXED, units, 0, 0);
    }

    /** Une fraction du parent (0,5 = la moitié), bornée. */
    public static Extent percent(double fraction, double min, double max) {
        return new Extent(Mode.PERCENT, fraction, min, max);
    }

    /** Une part de la place restante, au prorata du poids (1 par défaut). */
    public static Extent fill(double weight) {
        return new Extent(Mode.FILL, weight, 0, 0);
    }

    public static Extent fill() {
        return fill(1);
    }

    /** Un conteneur qui épouse ses enfants. */
    public static Extent hug() {
        return new Extent(Mode.HUG, 0, 0, 0);
    }

    /**
     * Vrai pour un pourcentage. Conservé sous ce nom parce que tout le produit le lit
     * ainsi depuis la story 10.1 — la sérialisation, le concepteur et BScript.
     */
    public boolean relative() {
        return mode == Mode.PERCENT;
    }

    /**
     * La longueur en unités, une fois la place du parent connue.
     *
     * <p>C'est la <b>seule</b> résolution du produit : le concepteur (10.2) et le rendu
     * en jeu (10.3) l'appellent tous deux. Deux calculs distincts divergeraient, et
     * l'auteur découvrirait l'écart une fois en jeu — le même piège que la géométrie
     * du clic sur un fil, partagée avec son tracé depuis la story 5.12.
     *
     * <p>Pour {@code FILL} et {@code HUG}, un élément seul ne suffit pas à décider :
     * la part dépend des frères, la taille des enfants. On rend alors une valeur
     * <b>sûre</b> — toute la place pour un {@code FILL}, la borne basse pour un
     * {@code HUG} — et c'est {@link ScreenLayout#solve} qui donne la vraie. Lever ici
     * casserait la validation d'un élément pas encore posé.
     */
    public double resolve(double parentSize) {
        double raw = switch (mode) {
            case FIXED -> value;
            case PERCENT -> value * parentSize;
            case FILL -> parentSize;
            case HUG -> Math.max(min, ScreenElement.MIN_SIZE);
        };
        double bounded = Math.max(min, raw);
        return max > 0 ? Math.min(max, bounded) : bounded;
    }

    /** Borne une longueur déjà calculée par la passe de disposition. */
    double clamp(double resolved) {
        double bounded = Math.max(min, resolved);
        return max > 0 ? Math.min(max, bounded) : bounded;
    }
}
