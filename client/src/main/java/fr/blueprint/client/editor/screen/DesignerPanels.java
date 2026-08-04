package fr.blueprint.client.editor.screen;

/**
 * La place que prennent les deux panneaux du concepteur — et surtout, celle qu'ils
 * <b>rendent</b> quand on les replie.
 *
 * <p>Ensemble ils occupent 220 unités : en échelle GUI 3, où la fenêtre en fait 640, cela
 * laisse 420 pour dessiner. Un tiers de l'écran est donc pris en permanence par une
 * palette qu'on consulte au moment de poser et par des propriétés qu'on règle après coup —
 * jamais pendant qu'on place.
 *
 * <p><b>Pourquoi un record à part.</b> Ces largeurs sont lues par le rendu <i>et</i> par le
 * clic. Un panneau qui rétrécit à l'écran sans que sa zone cliquable suive laisse une bande
 * invisible qui avale les gestes du canevas, et rien à l'écran ne l'explique : le bord ne
 * répond simplement plus. Ce projet s'y est déjà pris deux fois — le panneau des variables
 * peignait sur toute la hauteur, et les sections de la palette recalculaient leurs
 * ordonnées de chaque côté. Une seule source, donc, et un test qui la vérifie.
 */
public record DesignerPanels(boolean paletteOpen, boolean propertiesOpen) {

    /**
     * Largeur de la palette dépliée. Élargie à 92 : à 76, « Progress bar » touchait déjà
     * le bord, et sa traduction française — « Barre de progression » — le dépassait
     * franchement.
     */
    public static final int PALETTE_WIDTH = 92;

    public static final int PROPERTIES_WIDTH = 128;

    /**
     * Ce qui reste d'un panneau replié : de quoi voir un chevron et le rouvrir.
     *
     * <p>Le replier <b>entièrement</b> serait pire que de ne pas le replier du tout — un
     * panneau qui disparaît sans laisser de prise ne se rouvre qu'en devinant qu'un
     * raccourci existe.
     */
    public static final int COLLAPSED = 7;

    /** Épaisseur de la poignée de repli sur le bord intérieur d'un panneau déplié. */
    public static final int TOGGLE = 5;

    public static final DesignerPanels OPEN = new DesignerPanels(true, true);

    public int paletteWidth() {
        return paletteOpen ? PALETTE_WIDTH : COLLAPSED;
    }

    public int propertiesWidth() {
        return propertiesOpen ? PROPERTIES_WIDTH : COLLAPSED;
    }

    /** Le bord gauche de la zone de dessin. */
    public int canvasLeft() {
        return paletteWidth();
    }

    /** La largeur qui reste pour dessiner, jamais nulle. */
    public int canvasWidth(int totalWidth) {
        return Math.max(1, totalWidth - paletteWidth() - propertiesWidth());
    }

    public boolean inPalette(double mouseX) {
        return mouseX < paletteWidth();
    }

    public boolean inProperties(double mouseX, int totalWidth) {
        return mouseX >= totalWidth - propertiesWidth();
    }

    /**
     * La poignée de repli de la palette : son bord intérieur quand elle est dépliée, sa
     * bande entière quand elle est repliée — sinon la rouvrir demanderait de viser cinq
     * pixels sur sept.
     */
    public boolean onPaletteToggle(double mouseX) {
        return paletteOpen
                ? mouseX >= PALETTE_WIDTH - TOGGLE && mouseX < PALETTE_WIDTH
                : mouseX >= 0 && mouseX < COLLAPSED;
    }

    public boolean onPropertiesToggle(double mouseX, int totalWidth) {
        double left = totalWidth - propertiesWidth();
        return propertiesOpen
                ? mouseX >= left && mouseX < left + TOGGLE
                : mouseX >= left;
    }

    public DesignerPanels withPalette(boolean open) {
        return new DesignerPanels(open, propertiesOpen);
    }

    public DesignerPanels withProperties(boolean open) {
        return new DesignerPanels(paletteOpen, open);
    }

    /**
     * Replie les deux, ou rouvre les deux. La bascule d'un coup est le geste courant :
     * on replie pour placer, on rouvre pour régler.
     */
    public DesignerPanels toggledBoth() {
        boolean open = !(paletteOpen || propertiesOpen);
        return new DesignerPanels(open, open);
    }
}
