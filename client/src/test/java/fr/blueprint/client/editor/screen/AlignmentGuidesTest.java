package fr.blueprint.client.editor.screen;

import fr.blueprint.core.graph.screen.ScreenLayout;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Les guides d'alignement (story 10.2, AC3), purs et testés sans écran : « étant donné
 * ces voisins et ce rectangle, où l'élément s'accroche-t-il et quelles lignes s'affichent ».
 */
class AlignmentGuidesTest {

    private static ScreenLayout.Rect rect(double x, double y, double w, double h) {
        return new ScreenLayout.Rect(x, y, w, h);
    }

    @Test
    void sansVoisinRienNeBouge() {
        AlignmentGuides.Result result = AlignmentGuides.snap(rect(37, 41, 40, 20), List.of());
        assertEquals(37, result.rect().x(), 1e-9);
        assertEquals(41, result.rect().y(), 1e-9);
        assertTrue(result.guides().isEmpty());
    }

    @Test
    void leBordGaucheSAccrocheAuBordGauche() {
        AlignmentGuides.Result result =
                AlignmentGuides.snap(rect(101, 100, 40, 20), List.of(rect(100, 40, 60, 20)));
        assertEquals(100, result.rect().x(), 1e-9);
        assertEquals(100, result.rect().y(), 1e-9, "l'axe vertical ne bouge pas pour autant");
        assertEquals(1, result.guides().size());
        assertTrue(result.guides().getFirst().vertical());
    }

    @Test
    void leBordGaucheSAccrocheAuBordDroit() {
        AlignmentGuides.Result result =
                AlignmentGuides.snap(rect(159, 100, 40, 20), List.of(rect(100, 40, 60, 20)));
        assertEquals(160, result.rect().x(), 1e-9, "posé contre le bord droit du voisin");
    }

    /** Le centre compte autant que les bords : c'est lui qu'on vise pour empiler. */
    @Test
    void lesCentresSAlignent() {
        AlignmentGuides.Result result =
                AlignmentGuides.snap(rect(109, 100, 40, 20), List.of(rect(100, 40, 60, 20)));
        assertEquals(110, result.rect().x(), 1e-9, "centre 130 contre centre 130");
    }

    @Test
    void auDelaDeLaDistanceDAccrocheRienNeSePasse() {
        double far = AlignmentGuides.SNAP_DISTANCE + 0.5;
        AlignmentGuides.Result result =
                AlignmentGuides.snap(rect(100 + far, 100, 40, 20), List.of(rect(100, 40, 60, 20)));
        assertEquals(100 + far, result.rect().x(), 1e-9, "l'auteur veut vraiment ce décalage");
        assertTrue(result.guides().isEmpty());
    }

    @Test
    void lesDeuxAxesSAccrochentIndependamment() {
        AlignmentGuides.Result result =
                AlignmentGuides.snap(rect(101, 41, 40, 20), List.of(rect(100, 40, 60, 20)));
        assertEquals(100, result.rect().x(), 1e-9);
        assertEquals(40, result.rect().y(), 1e-9);
        assertEquals(2, result.guides().size(), "une ligne par axe");
        assertTrue(result.guides().stream().anyMatch(AlignmentGuides.Guide::vertical));
        assertTrue(result.guides().stream().anyMatch(g -> !g.vertical()));
    }

    /**
     * <b>Une seule accroche par axe.</b> Deux voisins également proches mais
     * incompatibles feraient sauter l'élément de l'un à l'autre à chaque image, et
     * l'auteur ne pourrait plus le poser du tout.
     */
    @Test
    void deuxVoisinsProchesNeSeDisputentPasLElement() {
        AlignmentGuides.Result result = AlignmentGuides.snap(rect(101, 100, 40, 20),
                List.of(rect(100, 40, 60, 20), rect(102, 200, 60, 20)));

        assertEquals(100, result.rect().x(), 1e-9, "le plus proche gagne, et lui seul");
        assertEquals(1, result.guides().stream().filter(AlignmentGuides.Guide::vertical).count());
    }

    /** La taille ne change jamais : accrocher déplace, ça ne redimensionne pas. */
    @Test
    void lAccrocheNeChangePasLaTaille() {
        AlignmentGuides.Result result =
                AlignmentGuides.snap(rect(101, 41, 40, 20), List.of(rect(100, 40, 60, 20)));
        assertEquals(40, result.rect().width(), 1e-9);
        assertEquals(20, result.rect().height(), 1e-9);
    }

    /** La ligne couvre les deux rectangles : elle doit expliquer ce qu'elle aligne. */
    @Test
    void laLigneRelieLesDeuxRectangles() {
        AlignmentGuides.Guide guide = AlignmentGuides.snap(rect(101, 100, 40, 20),
                List.of(rect(100, 40, 60, 20))).guides().getFirst();

        assertEquals(100, guide.position(), 1e-9);
        assertEquals(40, guide.from(), 1e-9, "du haut du voisin");
        assertEquals(120, guide.to(), 1e-9, "au bas de l'élément déplacé");
        assertFalse(guide.from() > guide.to());
    }
}
