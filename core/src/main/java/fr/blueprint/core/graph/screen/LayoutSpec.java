package fr.blueprint.core.graph.screen;

/**
 * Comment un conteneur <b>range ses enfants</b>.
 *
 * <p>Jusqu'ici tout était posé à la main : chaque élément portait son {@code x} et son
 * {@code y}. Une colonne de trois boutons se posait bouton par bouton, et en insérer un
 * quatrième obligeait à repositionner les trois autres. Les actions <i>aligner</i> et
 * <i>répartir</i> du concepteur étaient des pansements sur cette absence : elles
 * calculent une fois ce qu'un conteneur recalculerait toujours.
 *
 * <p>Une disposition vit sur le conteneur, et non dans une table annexe de l'écran :
 * c'est une propriété du panneau. Une table se désynchroniserait au premier renommage,
 * et {@code ScreenOps.RenameElement} devrait la maintenir en plus des parents.
 *
 * @param mode     comment les enfants s'enchaînent
 * @param gap      espace entre deux enfants sur l'axe principal, en unités
 * @param crossGap espace entre deux rangées d'une grille
 * @param columns  nombre de colonnes d'une grille ; ignoré ailleurs
 * @param main     répartition le long de l'axe principal
 * @param cross    alignement sur l'axe transverse
 * @param scroll   vrai si ce conteneur <b>défile</b> quand son contenu dépasse
 */
public record LayoutSpec(Mode mode, double gap, double crossGap, int columns,
                         Distribute main, Cross cross, Scroll scroll) {

    /**
     * Sur quel(s) axe(s) ce conteneur défile.
     *
     * <p>Une énumération et non deux booléens : « défilant » et « défilant en X » se
     * seraient lus comme deux réglages indépendants alors qu'ils décrivent une seule
     * chose, et le second aurait porté un nom que le premier n'a pas.
     */
    public enum Scroll {
        NONE, VERTICAL, HORIZONTAL, BOTH;

        public boolean vertical() {
            return this == VERTICAL || this == BOTH;
        }

        public boolean horizontal() {
            return this == HORIZONTAL || this == BOTH;
        }

        public boolean any() {
            return this != NONE;
        }
    }

    /** Ce que fait le conteneur de ses enfants. */
    public enum Mode {
        /** Chaque enfant se place lui-même, par son ancre et son décalage. */
        ABSOLUTE,
        COLUMN,
        ROW,
        GRID
    }

    /** Où va la place restante sur l'axe principal. */
    public enum Distribute {
        START, CENTER, END, SPACE_BETWEEN
    }

    /** Comment un enfant se place sur l'axe transverse. */
    public enum Cross {
        START, CENTER, END, STRETCH
    }

    /** Le comportement historique : personne ne range personne. */
    public static final LayoutSpec ABSOLUTE =
            new LayoutSpec(Mode.ABSOLUTE, 0, 0, 1, Distribute.START, Cross.START, Scroll.NONE);

    public LayoutSpec {
        if (mode == null) {
            mode = Mode.ABSOLUTE;
        }
        if (main == null) {
            main = Distribute.START;
        }
        if (cross == null) {
            cross = Cross.START;
        }
        if (scroll == null) {
            scroll = Scroll.NONE;
        }
        gap = finite(gap);
        crossGap = finite(crossGap);
        // Une grille de zéro colonne ferait une division par zéro au placement, et une
        // grille de colonnes négatives n'a pas de sens. Une colonne est le minimum,
        // et c'est exactement une COLUMN — donc une valeur sûre, pas une trahison.
        columns = Math.max(1, columns);
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? Math.max(0, value) : 0;
    }

    /** Une colonne d'enfants espacés, alignés au début. */
    public static LayoutSpec column(double gap) {
        return new LayoutSpec(Mode.COLUMN, gap, 0, 1, Distribute.START, Cross.START, Scroll.NONE);
    }

    public static LayoutSpec row(double gap) {
        return new LayoutSpec(Mode.ROW, gap, 0, 1, Distribute.START, Cross.START, Scroll.NONE);
    }

    public static LayoutSpec grid(int columns, double gap, double crossGap) {
        return new LayoutSpec(Mode.GRID, gap, crossGap, columns,
                Distribute.START, Cross.START, Scroll.NONE);
    }

    /** Vrai si ce conteneur place ses enfants lui-même. */
    public boolean arranges() {
        return mode != Mode.ABSOLUTE;
    }

    /** Vrai si l'axe principal est vertical — une colonne, ou les rangées d'une grille. */
    public boolean vertical() {
        return mode == Mode.COLUMN;
    }

    public LayoutSpec withMode(Mode newMode) {
        return new LayoutSpec(newMode, gap, crossGap, columns, main, cross, scroll);
    }

    public LayoutSpec withGap(double newGap) {
        return new LayoutSpec(mode, newGap, crossGap, columns, main, cross, scroll);
    }

    public LayoutSpec withCrossGap(double newCrossGap) {
        return new LayoutSpec(mode, gap, newCrossGap, columns, main, cross, scroll);
    }

    public LayoutSpec withColumns(int newColumns) {
        return new LayoutSpec(mode, gap, crossGap, newColumns, main, cross, scroll);
    }

    public LayoutSpec withMain(Distribute newMain) {
        return new LayoutSpec(mode, gap, crossGap, columns, newMain, cross, scroll);
    }

    public LayoutSpec withCross(Cross newCross) {
        return new LayoutSpec(mode, gap, crossGap, columns, main, newCross, scroll);
    }

    /**
     * Fait défiler ce conteneur quand son contenu dépasse (story 10.13).
     *
     * <p>Une <b>liste</b> défilait déjà, mais ses lignes sont un gabarit répété : du
     * texte, et rien d'autre. Un menu de réglages, une page de règles, une fiche de
     * personnage sont faits d'éléments <i>différents</i> — des étiquettes, des curseurs,
     * des cases, des images. Sans conteneur défilant, il fallait les répartir sur plusieurs
     * écrans reliés par des boutons « suivant », ce qui est une pagination, pas un menu.
     *
     * <p>Le décalage lui-même n'est <b>pas</b> ici : il est propre à un joueur et à une
     * ouverture, comme le remplissage d'une barre ou le texte d'un champ. L'écrire dans le
     * modèle le ferait voyager dans la sauvegarde et dans l'export texte, où deux joueurs
     * n'ont pas la même position de lecture.
     */
    public LayoutSpec withScroll(Scroll newScroll) {
        return new LayoutSpec(mode, gap, crossGap, columns, main, cross,
                newScroll == null ? Scroll.NONE : newScroll);
    }

    /**
     * L'écriture d'avant l'axe horizontal : {@code true} vaut « vertical ».
     *
     * <p>Conservée parce que c'est ce que la 10.13 a écrit partout, et que la remplacer
     * mécaniquement dans les tests et les exemples aurait enfoui l'ajout de l'axe sous
     * du bruit. Elle dit exactement ce qu'elle disait.
     */
    public LayoutSpec withScroll(boolean vertical) {
        return withScroll(vertical ? Scroll.VERTICAL : Scroll.NONE);
    }
}
