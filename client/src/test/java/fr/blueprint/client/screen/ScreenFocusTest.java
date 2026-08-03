package fr.blueprint.client.screen;

import fr.blueprint.core.graph.screen.ElementKind;
import fr.blueprint.core.graph.screen.Screen;
import fr.blueprint.core.graph.screen.ScreenElement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La navigation au clavier dans un écran ouvert (story 10.6, AC3).
 *
 * <p>Un menu qu'on ne peut pas parcourir au clavier exclut des joueurs d'une
 * fonctionnalité de jeu. Ce qui se vérifie ici est donc le <b>parcours</b> : qui est
 * atteignable, dans quel ordre, et ce qui arrive quand l'écran change sous les doigts.
 */
class ScreenFocusTest {

    private static ScreenElement button(String name) {
        return ScreenElement.of(name, ElementKind.BUTTON, 0, 0, 60, 20);
    }

    private static Screen screen(ScreenElement... elements) {
        return new Screen("menu", false, List.of(elements));
    }

    @Test
    void tabParcourtLesBoutonsDansLOrdreDeDessin() {
        Screen screen = screen(button("a"), button("b"), button("c"));
        ScreenFocus focus = new ScreenFocus();

        assertEquals("a", focus.move(screen, 1));
        assertEquals("b", focus.move(screen, 1));
        assertEquals("c", focus.move(screen, 1));
        assertEquals("a", focus.move(screen, 1), "et ça boucle");
    }

    /**
     * Maj+Tab recule, et le premier Maj+Tab part de la <b>fin</b>. Repartir toujours du
     * premier ferait remonter le focus en haut du menu à chaque Maj+Tab — l'inverse de
     * ce que la touche promet.
     */
    @Test
    void majTabReculeEtDemarreParLaFin() {
        Screen screen = screen(button("a"), button("b"), button("c"));
        ScreenFocus focus = new ScreenFocus();

        assertEquals("c", focus.move(screen, -1));
        assertEquals("b", focus.move(screen, -1));
        assertEquals("a", focus.move(screen, -1));
        assertEquals("c", focus.move(screen, -1));
    }

    /** Seuls les éléments cliquables sont atteignables : un titre n'a rien à recevoir. */
    @Test
    void unLibelleNestPasAtteignable() {
        Screen screen = screen(
                ScreenElement.of("titre", ElementKind.LABEL, 0, 0, 80, 12),
                button("ok"),
                ScreenElement.of("fond", ElementKind.PANEL, 0, 0, 100, 100));

        assertEquals(List.of("ok"), ScreenFocus.reachable(screen));
    }

    /**
     * <b>Le test qui compte.</b> Un bouton dans un onglet masqué ne reçoit pas le focus.
     * Sans cela, le joueur taperait sur Entrée sans rien voir se passer, et croirait le
     * menu bloqué — alors que le bouton existe, quelque part, invisible.
     */
    @Test
    void unBoutonDansUnePageMasqueeNestPasAtteignable() {
        Screen screen = screen(
                ScreenElement.of("page", ElementKind.PANEL, 0, 0, 100, 100).withVisible(false),
                button("cache").withParent("page"),
                button("visible"));

        assertEquals(List.of("visible"), ScreenFocus.reachable(screen),
                "masquer un parent masque toute sa page, focus compris");
    }

    @Test
    void unBoutonDesactiveNestPasAtteignable() {
        Screen screen = screen(button("actif"), button("gris").withEnabled(false));
        assertEquals(List.of("actif"), ScreenFocus.reachable(screen));
    }

    /**
     * Sur un écran sans rien de cliquable, {@code Tab} ne rend rien — et l'appelant ne
     * le consomme donc pas. Le joueur s'attend à ce que la touche fasse ce qu'elle fait
     * partout ailleurs, plutôt que d'être avalée en silence.
     */
    @Test
    void tabNeConsommeRienSurUnEcranSansBouton() {
        Screen screen = screen(ScreenElement.of("titre", ElementKind.LABEL, 0, 0, 80, 12));
        ScreenFocus focus = new ScreenFocus();

        assertNull(focus.move(screen, 1));
        assertNull(focus.focused());
    }

    /**
     * Entre la tabulation et la validation, le serveur a pu désactiver le bouton (10.4).
     * L'activer quand même enverrait un clic que le serveur refuserait, sans que le
     * joueur sache pourquoi.
     */
    @Test
    void unBoutonDesactiveEntreTempsNeSActivePlus() {
        Screen avant = screen(button("acheter"));
        ScreenFocus focus = new ScreenFocus();
        assertEquals("acheter", focus.move(avant, 1));
        assertEquals("acheter", focus.activate(avant));

        Screen apres = avant.replacing("acheter", button("acheter").withEnabled(false));
        assertNull(focus.activate(apres), "le bouton n'est plus actif : rien ne part");
    }

    /** Cliquer déplace le focus : passer de la souris au clavier ne repart pas de zéro. */
    @Test
    void leClicDeplaceLeFocus() {
        Screen screen = screen(button("a"), button("b"), button("c"));
        ScreenFocus focus = new ScreenFocus();

        focus.focus(screen, "b");
        assertEquals("b", focus.focused());
        assertEquals("c", focus.move(screen, 1), "Tab repart d'où la souris a laissé");

        focus.focus(screen, "inexistant");
        assertNull(focus.focused(), "on ne cible pas ce qui n'est pas atteignable");
    }

    /** Un élément disparu sous les doigts ne fige pas le parcours. */
    @Test
    void unElementDisparuNeFigePasLeParcours() {
        Screen avant = screen(button("a"), button("b"));
        ScreenFocus focus = new ScreenFocus();
        focus.focus(avant, "b");

        Screen apres = avant.without("b");
        assertEquals("a", focus.move(apres, 1), "on repart du début plutôt que de rester coincé");
        assertTrue(ScreenFocus.reachable(apres).contains("a"));
    }
}
