package fr.blueprint.client.editor.screen;

/**
 * La vue du concepteur d'écrans : de combien on grossit, et sur quoi on regarde.
 *
 * <p>Le concepteur n'avait <b>ni zoom ni déplacement de vue</b> : {@code DesignSurface}
 * recalculait à chaque image le plus grand facteur entier qui faisait tenir le canevas
 * entre les deux panneaux, et c'était tout. En échelle GUI 3 il reste environ 420 pixels
 * de large pour dessiner ; un canevas de 320×180 s'y posait donc au facteur 1, avec des
 * boutons de 60×20 pixels et du texte de 9. On dessinait un menu sur un timbre-poste.
 *
 * <p>Et le facteur entier n'était pas seulement étroit, il était <b>impossible</b> dès
 * qu'on vise grand : aucun entier ne montre 1920 unités dans 420 pixels. Le zoom est donc
 * un réel, et le dessin passe par une matrice plutôt que par des multiplications d'entiers
 * (voir {@code ScreenDesignerWidget.renderSurface}).
 *
 * <p>Calquée sur {@link fr.blueprint.client.editor.Camera}, qui fait exactement cela pour
 * le graphe — crans bornés, pivot sous le curseur, cadrage. Deux conventions de navigation
 * dans le même éditeur divergeraient au premier correctif, et l'auteur devrait retenir
 * quel onglet répond de quelle façon.
 *
 * <p>Logique pure, sans Minecraft : c'est ce qui permet de vérifier headless que le point
 * visé ne bouge pas d'un cran de zoom à l'autre, et que le canevas ne peut pas être perdu.
 *
 * <p>Convention : {@code pan} est la coordonnée <b>en unités</b> affichée au coin
 * haut-gauche de la zone de travail ; {@code local = (unité − pan) × zoom}, où
 * {@code local} est un pixel <b>relatif à la zone</b>, pas à l'écran.
 */
public final class DesignCamera {

    /**
     * Les crans de la molette. Le bas descend à 0,15 pour qu'un canevas de 1920×1080
     * tienne entier dans une fenêtre de jeu modeste ; le haut monte à 8 pour poser à
     * l'unité près. {@code 1} en est un cran exact — c'est la taille réelle du jeu, et
     * l'auteur doit pouvoir y revenir sans viser.
     */
    public static final double[] LADDER =
            {0.15, 0.25, 0.35, 0.5, 0.7, 1, 1.5, 2, 3, 4, 6, 8};

    public static final double MIN_ZOOM = LADDER[0];
    public static final double MAX_ZOOM = LADDER[LADDER.length - 1];

    /** Le cran neutre : une unité vaut un pixel, exactement comme en jeu. */
    public static final double ONE_TO_ONE = 1;

    /** Marge de comparaison des crans : un zoom continu ne tombe jamais pile dessus. */
    private static final double EPSILON = 1e-6;

    private double zoom = ONE_TO_ONE;
    private double panX;
    private double panY;

    public double zoom() {
        return zoom;
    }

    public double panX() {
        return panX;
    }

    public double panY() {
        return panY;
    }

    // ------------------------------------------------------------ transformations

    /** Unité → pixel relatif au coin haut-gauche de la zone de travail. */
    public double toLocalX(double unitX) {
        return (unitX - panX) * zoom;
    }

    public double toLocalY(double unitY) {
        return (unitY - panY) * zoom;
    }

    public double toUnitX(double localX) {
        return localX / zoom + panX;
    }

    public double toUnitY(double localY) {
        return localY / zoom + panY;
    }

    // ------------------------------------------------------------------ navigation

    /**
     * Change de cran en gardant <b>immobile le point sous le curseur</b> — le zoom « sur
     * le curseur ».
     *
     * <p>C'est ce qui distingue un zoom d'un saut de vue : sans ce pivot, on perd à chaque
     * cran l'élément qu'on visait, et il faut le retrouver au déplacement de vue avant de
     * pouvoir zoomer encore.
     *
     * <p>Le cran suivant se cherche <b>par rapport à la valeur courante</b> et non par un
     * index : après un cadrage, le zoom vaut une valeur continue qui n'est sur aucun cran,
     * et un index mémorisé y ramènerait brutalement.
     */
    public void zoomBy(int steps, double localX, double localY) {
        double next = ladderFrom(zoom, steps);
        zoomTo(next, localX, localY);
    }

    /** Le même pivot, pour une valeur choisie — le bouton « 1:1 ». */
    public void zoomTo(double target, double localX, double localY) {
        double next = Math.clamp(target, MIN_ZOOM, MAX_ZOOM);
        if (next == zoom) {
            return;
        }
        double unitX = toUnitX(localX);
        double unitY = toUnitY(localY);
        zoom = next;
        panX = unitX - localX / zoom;
        panY = unitY - localY / zoom;
    }

    /** Déplace la vue d'un delta exprimé en pixels (glisser de souris). */
    public void panByScreen(double dx, double dy) {
        panX -= dx / zoom;
        panY -= dy / zoom;
    }

    /**
     * Cadre le canevas entier — marge comprise — dans la zone, et le centre.
     *
     * <p>Le zoom obtenu est <b>continu</b> et non un cran : aucun cran ne fait tenir
     * exactement 1920 unités dans la place disponible, et choisir le cran inférieur
     * laisserait du vide autour pendant qu'on cherchait à voir plus grand.
     */
    public void fit(int areaWidth, int areaHeight, int unitsWidth, int unitsHeight) {
        double outerWidth = unitsWidth + DesignSurface.MARGIN * 2.0;
        double outerHeight = unitsHeight + DesignSurface.MARGIN * 2.0;
        zoom = Math.clamp(Math.min(Math.max(1, areaWidth) / outerWidth,
                Math.max(1, areaHeight) / outerHeight), MIN_ZOOM, MAX_ZOOM);
        centre(areaWidth, areaHeight, unitsWidth, unitsHeight);
    }

    /**
     * Cadre sur un <b>rectangle</b> plutôt que sur le canevas entier.
     *
     * <p>Le concepteur s'ouvre sur un canevas de 1920×1080 — un choix assumé, on dessine au
     * large puis on vérifie en réduisant. Le cadrer en entier donnait 21 % de zoom en
     * échelle GUI 3 : un menu de six cents unités y tenait dans cent trente pixels, texte
     * illisible et grille invisible, si bien qu'on ne savait même pas si l'accroche était
     * active.
     *
     * <p>C'est ce que le canevas de nœuds fait de sa touche {@code F} depuis la 5.1 : il
     * cadre les <b>nœuds</b>, pas la grille infinie. Ici, les éléments — et à défaut la
     * fenêtre garantie, qui est ce qu'on dessine quand on ne dessine rien encore.
     */
    public void fitInto(int areaWidth, int areaHeight,
                        double left, double topUnits, double unitsWidth, double unitsHeight) {
        double outerWidth = Math.max(1, unitsWidth) + DesignSurface.MARGIN * 2.0;
        double outerHeight = Math.max(1, unitsHeight) + DesignSurface.MARGIN * 2.0;
        zoom = Math.clamp(Math.min(Math.max(1, areaWidth) / outerWidth,
                Math.max(1, areaHeight) / outerHeight), MIN_ZOOM, MAX_ZOOM);
        panX = left - (areaWidth / zoom - unitsWidth) / 2;
        panY = topUnits - (areaHeight / zoom - unitsHeight) / 2;
    }

    /** Recentre le canevas sans toucher au zoom. */
    public void centre(int areaWidth, int areaHeight, int unitsWidth, int unitsHeight) {
        panX = -(areaWidth / zoom - unitsWidth) / 2;
        panY = -(areaHeight / zoom - unitsHeight) / 2;
    }

    /**
     * Empêche de <b>perdre le canevas</b>.
     *
     * <p>Sans borne, un déplacement de vue un peu vif le sort de la zone et il ne reste
     * plus rien à l'écran : ni élément, ni cadre, ni indice de la direction où pousser.
     * Le seul recours serait de deviner qu'une touche de cadrage existe.
     *
     * <p>Quand la vue est plus large que le canevas, elle le <b>centre</b> plutôt que de
     * le coller à un bord : un canevas cadré doit rester au milieu quand la fenêtre change
     * de taille.
     */
    public void clampInto(int areaWidth, int areaHeight, int unitsWidth, int unitsHeight) {
        panX = clampAxis(panX, areaWidth, unitsWidth);
        panY = clampAxis(panY, areaHeight, unitsHeight);
    }

    private double clampAxis(double pan, int areaLength, int units) {
        double view = Math.max(1, areaLength) / zoom;
        double outer = units + DesignSurface.MARGIN * 2.0;
        if (view >= outer) {
            return -(view - units) / 2;
        }
        return Math.clamp(pan, -DesignSurface.MARGIN, units + DesignSurface.MARGIN - view);
    }

    /**
     * Le cran atteint depuis {@code from} après {@code steps} déplacements. Aux extrémités
     * on reste sur place plutôt que de boucler : une molette qui repart à l'autre bout
     * ferait sauter la vue au moment précis où l'on cherche le bord.
     */
    public static double ladderFrom(double from, int steps) {
        double current = from;
        for (int i = 0; i < Math.abs(steps); i++) {
            current = steps > 0 ? above(current) : below(current);
        }
        return current;
    }

    private static double above(double value) {
        for (double step : LADDER) {
            if (step > value + EPSILON) {
                return step;
            }
        }
        return MAX_ZOOM;
    }

    private static double below(double value) {
        for (int i = LADDER.length - 1; i >= 0; i--) {
            if (LADDER[i] < value - EPSILON) {
                return LADDER[i];
            }
        }
        return MIN_ZOOM;
    }
}
