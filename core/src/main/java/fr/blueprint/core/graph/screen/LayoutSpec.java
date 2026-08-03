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
 */
public record LayoutSpec(Mode mode, double gap, double crossGap, int columns,
                         Distribute main, Cross cross) {

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
            new LayoutSpec(Mode.ABSOLUTE, 0, 0, 1, Distribute.START, Cross.START);

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
        return new LayoutSpec(Mode.COLUMN, gap, 0, 1, Distribute.START, Cross.START);
    }

    public static LayoutSpec row(double gap) {
        return new LayoutSpec(Mode.ROW, gap, 0, 1, Distribute.START, Cross.START);
    }

    public static LayoutSpec grid(int columns, double gap, double crossGap) {
        return new LayoutSpec(Mode.GRID, gap, crossGap, columns,
                Distribute.START, Cross.START);
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
        return new LayoutSpec(newMode, gap, crossGap, columns, main, cross);
    }

    public LayoutSpec withGap(double newGap) {
        return new LayoutSpec(mode, newGap, crossGap, columns, main, cross);
    }

    public LayoutSpec withCrossGap(double newCrossGap) {
        return new LayoutSpec(mode, gap, newCrossGap, columns, main, cross);
    }

    public LayoutSpec withColumns(int newColumns) {
        return new LayoutSpec(mode, gap, crossGap, newColumns, main, cross);
    }

    public LayoutSpec withMain(Distribute newMain) {
        return new LayoutSpec(mode, gap, crossGap, columns, newMain, cross);
    }

    public LayoutSpec withCross(Cross newCross) {
        return new LayoutSpec(mode, gap, crossGap, columns, main, newCross);
    }
}
