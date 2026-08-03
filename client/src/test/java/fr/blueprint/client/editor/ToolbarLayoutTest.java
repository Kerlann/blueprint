package fr.blueprint.client.editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La barre d'outils porte désormais trois choses : le titre, les onglets et les boutons
 * d'action. Elles se partagent une seule ligne de seize pixels.
 *
 * <p>Les onglets occupaient auparavant une <b>seconde bande</b> sous celle-ci, sur toute
 * la largeur de l'éditeur. Or cette barre est vide entre le titre et les boutons : deux
 * bandes coûtaient treize pixels de canevas à chaque image pour ce qui tient dans une
 * seule.
 *
 * <p>Ce que ce test protège : la position des onglets ne doit dépendre <b>ni</b> de la
 * longueur du titre, <b>ni</b> de l'état enregistré. Le titre gagne un « ● » à la
 * première modification — une position déduite de sa largeur aurait décalé les onglets de
 * huit pixels au premier geste, et on aurait cliqué à côté de celui qu'on visait.
 */
class ToolbarLayoutTest {

    private static final int[] WINDOW_WIDTHS = {320, 480, 854, 1280, 1920};

    @Test
    void laPositionDesOngletsNeDependQueDeLaLargeurDeFenetre() {
        for (int width : WINDOW_WIDTHS) {
            int first = ToolbarWidget.tabsX(width);
            int second = ToolbarWidget.tabsX(width);
            assertEquals(first, second, "pure : deux appels, même réponse");
            assertTrue(first > 0, "les onglets ne commencent pas au bord gauche");
            assertTrue(first < width, "ni hors de la fenêtre, à " + width + " de large");
        }
    }

    /**
     * Le titre s'arrête avant les onglets. Il était borné à la moitié de la barre, ce
     * qui le laissait passer dessous dès qu'un identifiant était long — et un
     * identifiant de blueprint l'est souvent ({@code monserveur:boutique_du_village}).
     */
    @Test
    void leTitreSArreteAvantLesOnglets() {
        for (int width : WINDOW_WIDTHS) {
            int tabs = ToolbarWidget.tabsX(width);
            // Le rendu tronque le titre à « tabsX - 12 » depuis l'abscisse 6 : il finit
            // donc au plus tard six pixels avant les onglets.
            int titleEnd = 6 + (tabs - 12);
            assertTrue(titleEnd < tabs, () -> "titre jusqu'à " + titleEnd
                    + ", onglets dès " + tabs);
        }
    }

    /**
     * Même sur la plus petite fenêtre jouable, les onglets tiennent entre le titre et les
     * boutons. Sinon, l'onglet « Écrans » passerait sous « Compiler » et deviendrait
     * incliquable — sans que rien ne le dise.
     */
    @Test
    void lesOngletsTiennentEntreLeTitreEtLesBoutons() {
        // Largeur des boutons : six mots courts, marges comprises. Mesurée large, pour
        // que le test échoue AVANT que le chevauchement ne soit visible.
        final int buttonsWidth = 210;
        final int tabsWidth = 100;   // « Graphe » + « Écrans », marges comprises

        for (int width : WINDOW_WIDTHS) {
            if (width < 480) {
                continue;   // en dessous, l'éditeur n'est de toute façon pas utilisable
            }
            int tabs = ToolbarWidget.tabsX(width);
            assertTrue(tabs + tabsWidth <= width - buttonsWidth, () -> String.format(
                    "à %d de large : onglets de %d à %d, boutons dès %d",
                    width, tabs, tabs + tabsWidth, width - buttonsWidth));
        }
    }

    /** Une fenêtre étroite ne renvoie pas les onglets à l'abscisse zéro, sur le titre. */
    @Test
    void uneFenetreEtroiteNeColleraPasLesOngletsSurLeTitre() {
        assertTrue(ToolbarWidget.tabsX(200) >= 60,
                "un plancher garde de la place au titre, même serré");
    }
}
