package fr.blueprint.client.editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Placement de l'infobulle (story 5.12) : jamais hors écran, jamais sous le curseur. */
class TooltipTest {

    @Test
    void parDefautEnBasADroiteDuCurseur() {
        int[] at = Tooltip.place(100, 100, 80, 30, 640, 480);
        assertTrue(at[0] > 100, "à droite");
        assertTrue(at[1] > 100, "en dessous — la pointe doit rester lisible");
    }

    @Test
    void basculeQuandElleDeborderait() {
        int[] at = Tooltip.place(600, 460, 80, 30, 640, 480);
        assertTrue(at[0] + 80 <= 640, "rentre en largeur");
        assertTrue(at[1] + 30 <= 480, "rentre en hauteur");
        assertTrue(at[0] < 600, "elle est passée à gauche du curseur");
        assertTrue(at[1] < 460, "et au-dessus");
    }

    /**
     * Un seul débordement ne doit pas faire basculer l'autre axe : sinon l'infobulle
     * saute de coin en coin en longeant un bord de l'écran.
     */
    @Test
    void unSeulAxeBasculeALaFois() {
        int[] at = Tooltip.place(600, 100, 80, 30, 640, 480);
        assertTrue(at[0] < 600, "l'axe qui déborde bascule");
        assertTrue(at[1] > 100, "l'autre reste comme avant");
    }

    /** Écran plus petit que la bulle : coller au bord, plutôt qu'un x négatif. */
    @Test
    void jamaisDeCoordonneeNegative() {
        int[] at = Tooltip.place(5, 5, 300, 200, 200, 100);
        assertEquals(0, at[0]);
        assertEquals(0, at[1]);
    }

    @Test
    void laHauteurSuitLeNombreDeLignes() {
        int une = Tooltip.height(java.util.List.of("a"));
        int trois = Tooltip.height(java.util.List.of("a", "b", "c"));
        assertTrue(trois > une);
        assertEquals(une + 20, trois, "dix pixels par ligne supplémentaire");
    }

    @Test
    void leSurvolAttendQueLaSourisSePose() {
        HoverTracker hover = new HoverTracker();
        assertFalse(hover.settled(10, 10, 1_000), "premier passage : rien tout de suite");
        assertFalse(hover.settled(10, 10, 1_000 + HoverTracker.DELAY_MS - 1));
        assertTrue(hover.settled(10, 10, 1_000 + HoverTracker.DELAY_MS));
    }

    @Test
    void leMoindreMouvementRelanceLeDelai() {
        HoverTracker hover = new HoverTracker();
        hover.settled(10, 10, 0);
        assertTrue(hover.settled(10, 10, HoverTracker.DELAY_MS));

        assertFalse(hover.settled(11, 10, HoverTracker.DELAY_MS),
                "un pixel de plus et l'infobulle disparaît le temps de se reposer");
        assertTrue(hover.settled(11, 10, 2 * HoverTracker.DELAY_MS));
    }

    @Test
    void resetAnnuleLeSurvolEnCours() {
        HoverTracker hover = new HoverTracker();
        hover.settled(10, 10, 0);
        assertTrue(hover.settled(10, 10, HoverTracker.DELAY_MS));

        hover.reset();
        assertFalse(hover.settled(10, 10, HoverTracker.DELAY_MS),
                "après un clic, on ne réaffiche pas l'infobulle instantanément");
    }
}
