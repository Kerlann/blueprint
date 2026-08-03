package fr.blueprint.core.graph.screen;

import java.util.List;

/**
 * Ce qu'une liste défilante montre, à un instant donné (story 10.8, AC1).
 *
 * <p>Purement arithmétique : combien de lignes tiennent, laquelle est en haut, laquelle
 * est sous le curseur. Aucun dessin, aucun état mutable — c'est ce qui rend le
 * défilement vérifiable sans client, alors que c'est justement là que se logent les
 * erreurs d'un décalage : une ligne de trop en bas, une ligne sautée au premier cran.
 *
 * <p>Les lignes sont un <b>gabarit répété</b> et non des éléments empilés : décrire une
 * ligne et la répéter garde l'écran de la taille de sa description, quand un élément par
 * ligne le ferait grossir avec les données — un paquet d'ouverture proportionnel au
 * nombre d'articles, et le plafond d'éléments (10.6) atteint pour une liste banale.
 */
public record ListView(int total, int visibleRows, int offset) {

    public ListView {
        total = Math.max(0, total);
        visibleRows = Math.max(0, visibleRows);
        offset = Math.clamp(offset, 0, Math.max(0, total - visibleRows));
    }

    /**
     * Combien de lignes tiennent dans {@code height}, à raison de {@code rowHeight}
     * chacune. Une ligne partiellement visible ne compte pas : la découper en deux
     * donnerait une demi-ligne cliquable, dont on ne saurait pas si elle appartient à
     * l'entrée du dessus ou à celle du dessous.
     */
    public static int rowsThatFit(double height, double rowHeight) {
        if (rowHeight < ElementOptions.MIN_ROW_HEIGHT || height <= 0) {
            return 0;
        }
        return (int) Math.floor(height / rowHeight);
    }

    /** Un état neuf, calé en haut. */
    public static ListView of(int total, double height, double rowHeight) {
        return new ListView(total, rowsThatFit(height, rowHeight), 0);
    }

    /** Fait défiler de {@code delta} lignes ; le résultat reste dans les bornes. */
    public ListView scrolledBy(int delta) {
        return new ListView(total, visibleRows, offset + delta);
    }

    /** Les indices réellement affichés, du haut vers le bas. */
    public List<Integer> visibleIndices() {
        List<Integer> out = new java.util.ArrayList<>(visibleRows);
        for (int i = offset; i < Math.min(total, offset + visibleRows); i++) {
            out.add(i);
        }
        return List.copyOf(out);
    }

    /**
     * L'indice de l'entrée sous un point, ou {@code -1}.
     *
     * <p>C'est l'<b>indice dans la liste</b> et non le rang à l'écran : le graphe reçoit
     * « la troisième entrée », pas « la première ligne visible ». Confondre les deux
     * donnerait un clic juste tant qu'on n'a pas défilé, et faux ensuite — le pire des
     * défauts, puisqu'il passe tous les essais rapides.
     *
     * @param localY ordonnée dans la liste, en unités depuis son bord haut
     */
    public int indexAt(double localY, double rowHeight) {
        if (localY < 0 || rowHeight <= 0) {
            return -1;
        }
        int row = (int) Math.floor(localY / rowHeight);
        if (row < 0 || row >= visibleRows) {
            return -1;
        }
        int index = offset + row;
        return index < total ? index : -1;
    }

    /** Y a-t-il de quoi défiler ? Sinon le curseur ne se dessine pas. */
    public boolean scrollable() {
        return total > visibleRows;
    }

    /**
     * Hauteur du curseur de défilement, en fraction de la piste. Bornée en bas : un
     * curseur proportionnel à mille entrées ferait un pixel, qu'on ne pourrait ni voir
     * ni saisir.
     */
    public double thumbFraction() {
        return scrollable() ? Math.max(0.1, (double) visibleRows / total) : 1;
    }

    /** Position du curseur, en fraction de la course disponible. */
    public double thumbPosition() {
        int maxOffset = total - visibleRows;
        return maxOffset <= 0 ? 0 : (double) offset / maxOffset;
    }
}
