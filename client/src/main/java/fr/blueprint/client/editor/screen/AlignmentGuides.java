package fr.blueprint.client.editor.screen;

import fr.blueprint.core.graph.screen.ScreenLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Les guides d'alignement du concepteur d'écrans (story 10.2, AC3).
 *
 * <p>Sur un graphe, deux nœuds mal alignés restent lisibles. Sur un écran, un bouton
 * décalé de deux unités <b>se voit</b> — c'est tout ce que le joueur regardera. Les
 * guides ne sont donc pas un confort : ils sont ce qui rend le résultat présentable
 * sans régler des coordonnées à la main.
 *
 * <p>Purs et testables sans écran : « étant donné ces voisins et ce rectangle, quelles
 * lignes s'affichent et où l'élément s'accroche-t-il ».
 */
public final class AlignmentGuides {

    /** Distance d'accroche, en unités d'interface. Au-delà, l'auteur veut vraiment décaler. */
    public static final double SNAP_DISTANCE = 2.5;

    private AlignmentGuides() {
    }

    /** Une ligne d'aide : verticale ou horizontale, à une position, sur une étendue. */
    public record Guide(boolean vertical, double position, double from, double to) {
    }

    /** Le rectangle accroché, et les lignes à dessiner pour l'expliquer. */
    public record Result(ScreenLayout.Rect rect, List<Guide> guides) {
    }

    /**
     * Accroche {@code moving} aux bords et centres de {@code neighbours}.
     *
     * <p>Une seule accroche par axe : la plus proche gagne. Cumuler deux accroches sur
     * le même axe ferait sauter l'élément entre deux positions incompatibles à chaque
     * image, et l'auteur ne pourrait plus le poser.
     */
    public static Result snap(ScreenLayout.Rect moving, List<ScreenLayout.Rect> neighbours) {
        Candidate bestX = null;
        Candidate bestY = null;
        for (ScreenLayout.Rect other : neighbours) {
            // Trois repères par axe et par rectangle : les deux bords et le centre.
            // Le centre compte autant que les bords — c'est lui qu'on vise quand on
            // empile des boutons dans un panneau.
            for (double[] pair : new double[][]{
                    {moving.x(), other.x()},
                    {moving.x(), other.right()},
                    {moving.right(), other.x()},
                    {moving.right(), other.right()},
                    {moving.x() + moving.width() / 2, other.x() + other.width() / 2}}) {
                bestX = better(bestX, pair[0], pair[1], other);
            }
            for (double[] pair : new double[][]{
                    {moving.y(), other.y()},
                    {moving.y(), other.bottom()},
                    {moving.bottom(), other.y()},
                    {moving.bottom(), other.bottom()},
                    {moving.y() + moving.height() / 2, other.y() + other.height() / 2}}) {
                bestY = better(bestY, pair[0], pair[1], other);
            }
        }

        double dx = bestX == null ? 0 : bestX.target - bestX.source;
        double dy = bestY == null ? 0 : bestY.target - bestY.source;
        ScreenLayout.Rect snapped = new ScreenLayout.Rect(
                moving.x() + dx, moving.y() + dy, moving.width(), moving.height());

        List<Guide> guides = new ArrayList<>(2);
        if (bestX != null) {
            guides.add(new Guide(true, bestX.target,
                    Math.min(snapped.y(), bestX.other.y()),
                    Math.max(snapped.bottom(), bestX.other.bottom())));
        }
        if (bestY != null) {
            guides.add(new Guide(false, bestY.target,
                    Math.min(snapped.x(), bestY.other.x()),
                    Math.max(snapped.right(), bestY.other.right())));
        }
        return new Result(snapped, List.copyOf(guides));
    }

    private record Candidate(double source, double target, double distance,
                             ScreenLayout.Rect other) {
    }

    private static Candidate better(Candidate current, double source, double target,
                                    ScreenLayout.Rect other) {
        double distance = Math.abs(target - source);
        if (distance > SNAP_DISTANCE || (current != null && distance >= current.distance())) {
            return current;
        }
        return new Candidate(source, target, distance, other);
    }
}
