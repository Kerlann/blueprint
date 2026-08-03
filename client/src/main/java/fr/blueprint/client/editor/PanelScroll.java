package fr.blueprint.client.editor;

/**
 * Défilement d'un panneau à lignes (détails, variables, diagnostics).
 *
 * <p>Jusqu'ici, les panneaux coupaient net en bas de l'écran : au-delà d'une trentaine
 * de lignes, le contenu était <b>inatteignable</b> — un nœud à douze pins ou un
 * blueprint à vingt variables ne se lisaient plus. Pur, donc testé sans écran.
 *
 * <p>La position est <b>toujours re-bornée à l'affichage</b>, jamais au moment du
 * défilement seul : le contenu change sous le panneau (on sélectionne un autre nœud,
 * un diagnostic disparaît) et une position devenue trop grande laisserait un panneau
 * vide sans que rien ne l'explique.
 */
public final class PanelScroll {

    private int offset;

    /** Décale de {@code rows} lignes (positif = vers le bas), borné au contenu. */
    public void scrollBy(int rows, int content, int visible) {
        offset = clamp(offset + rows, content, visible);
    }

    /** Position de la première ligne affichée, re-bornée au contenu courant. */
    public int offset(int content, int visible) {
        offset = clamp(offset, content, visible);
        return offset;
    }

    /** Remise à zéro quand le contenu change de nature (autre sélection). */
    public void reset() {
        offset = 0;
    }

    public static int clamp(int wanted, int content, int visible) {
        int max = Math.max(0, content - Math.max(1, visible));
        return Math.max(0, Math.min(wanted, max));
    }

    /** Vrai si le contenu dépasse la place : le panneau doit le signaler. */
    public static boolean overflows(int content, int visible) {
        return content > visible;
    }

    /**
     * Position et hauteur du curseur de défilement, en pixels, dans une piste de
     * {@code trackHeight}. Hauteur minimale de 8 px : un curseur d'un pixel de haut sur
     * une longue liste ne s'attrape pas.
     */
    public static int[] thumb(int offset, int content, int visible, int trackHeight) {
        if (!overflows(content, visible)) {
            return new int[]{0, trackHeight};
        }
        int height = Math.max(8, trackHeight * visible / content);
        int max = Math.max(1, content - visible);
        int y = (trackHeight - height) * Math.min(offset, max) / max;
        return new int[]{y, height};
    }
}
