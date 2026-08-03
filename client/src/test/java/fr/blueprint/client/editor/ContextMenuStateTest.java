package fr.blueprint.client.editor;

import fr.blueprint.core.graph.Link;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Menu contextuel (story 5.13). Le piège de ce composant est l'arithmétique des
 * lignes : les séparateurs occupent de la hauteur, et un rendu qui les compte face à
 * un clic qui les ignore fait choisir une action pour une autre.
 */
class ContextMenuStateTest {

    private static final int WIDTH = ContextMenuPopup.WIDTH;
    private static final UUID NODE = UUID.randomUUID();

    private static ContextMenuState nodeMenu() {
        ContextMenuState menu = new ContextMenuState();
        menu.openForNode(100, 100, NODE, true, 3);
        return menu;
    }

    @Test
    void fermeParDefautEtSeRefermeApresUsage() {
        ContextMenuState menu = new ContextMenuState();
        assertFalse(menu.isOpen());
        assertEquals(-1, menu.itemAt(100, 100, WIDTH), "fermé, rien n'est cliquable");

        menu.openForNode(10, 10, NODE, false, 1);
        assertTrue(menu.isOpen());
        menu.close();
        assertFalse(menu.isOpen());
    }

    /**
     * Le cœur du composant : cliquer au milieu d'une ligne rend CETTE ligne, y
     * compris après un séparateur. Le test parcourt toutes les lignes plutôt que
     * d'en vérifier une : c'est le décalage cumulé qui se glisse ici.
     */
    @Test
    void chaqueLigneEstCliquableAuBonEndroitSeparateursCompris() {
        ContextMenuState menu = nodeMenu();
        var items = menu.items();
        assertTrue(items.stream().anyMatch(ContextMenuState.Item::separatorBefore),
                "ce menu DOIT contenir un séparateur, sinon le test ne prouve rien");

        for (int i = 0; i < items.size(); i++) {
            double middle = menu.rowTop(i) + ContextMenuState.ROW_HEIGHT / 2.0;
            assertEquals(i, menu.itemAt(menu.x() + 5, middle, WIDTH),
                    "ligne " + i + " (" + items.get(i).labelKey() + ")");
        }
    }

    @Test
    void horsDuMenuOnNeChoisitRien() {
        ContextMenuState menu = nodeMenu();
        assertEquals(-1, menu.itemAt(menu.x() - 1, menu.y() + 5, WIDTH), "à gauche");
        assertEquals(-1, menu.itemAt(menu.x() + WIDTH, menu.y() + 5, WIDTH), "à droite");
        assertEquals(-1, menu.itemAt(menu.x() + 5, menu.y() - 10, WIDTH), "au-dessus");
        assertEquals(-1, menu.itemAt(menu.x() + 5, menu.y() + menu.height() + 10, WIDTH),
                "en dessous");
    }

    /** Une entrée grisée se voit (U2) mais ne s'exécute pas. */
    @Test
    void uneEntreeGriseeNeRenvoieAucuneAction() {
        ContextMenuState menu = new ContextMenuState();
        // Un seul nœud sélectionné, sans lien : « aligner » et « casser » sont grisés.
        menu.openForNode(0, 0, NODE, false, 1);

        int align = indexOf(menu, ContextMenuState.Action.ALIGN_SELECTION);
        assertTrue(align >= 0, "l'entrée reste VISIBLE, elle n'est pas masquée");
        assertFalse(menu.items().get(align).enabled());
        assertNull(menu.choose(menu.x() + 5, middleOf(menu, align), WIDTH));

        int duplicate = indexOf(menu, ContextMenuState.Action.DUPLICATE);
        assertEquals(ContextMenuState.Action.DUPLICATE,
                menu.choose(menu.x() + 5, middleOf(menu, duplicate), WIDTH));
    }

    @Test
    void leMenuDUnPinSAdapteAuPin() {
        ContextMenuState exec = new ContextMenuState();
        exec.openForPin(0, 0, NODE, "exec_in", false, false, false);
        assertFalse(enabled(exec, ContextMenuState.Action.PROMOTE_TO_VARIABLE),
                "un pin exec ne se promeut pas en variable");
        assertFalse(enabled(exec, ContextMenuState.Action.BREAK_PIN_LINKS), "non câblé");
        assertFalse(enabled(exec, ContextMenuState.Action.RESET_LITERAL), "pas de valeur");

        ContextMenuState data = new ContextMenuState();
        data.openForPin(0, 0, NODE, "a", true, true, true);
        assertTrue(enabled(data, ContextMenuState.Action.PROMOTE_TO_VARIABLE));
        assertTrue(enabled(data, ContextMenuState.Action.BREAK_PIN_LINKS));
        assertTrue(enabled(data, ContextMenuState.Action.RESET_LITERAL));
    }

    @Test
    void laCibleAccompagneLeMenu() {
        ContextMenuState pin = new ContextMenuState();
        pin.openForPin(0, 0, NODE, "a", true, false, true);
        assertEquals(NODE, pin.target().node());
        assertEquals("a", pin.target().pin());
        assertNull(pin.target().link());

        Link link = new Link(NODE, "out", UUID.randomUUID(), "in");
        ContextMenuState wire = new ContextMenuState();
        wire.openForLink(0, 0, link);
        assertEquals(link, wire.target().link());
        assertNull(wire.target().node());
    }

    /**
     * Le recalage DÉPLACE le menu au lieu de rendre une position à part : sans cela,
     * le rendu serait recalé et le clic resterait sur la position brute.
     */
    @Test
    void leMenuRentreDansLEcranEtLeClicSuit() {
        ContextMenuState menu = new ContextMenuState();
        menu.openForNode(630, 470, NODE, true, 2);
        menu.clampToScreen(WIDTH, 640, 480);

        assertTrue(menu.x() + WIDTH <= 640, "rentre en largeur");
        assertTrue(menu.y() + menu.height() <= 480, "rentre en hauteur");
        assertTrue(menu.x() >= 0 && menu.y() >= 0);

        // Et la première ligne se clique à sa nouvelle place, pas à l'ancienne.
        assertEquals(0, menu.itemAt(menu.x() + 5, middleOf(menu, 0), WIDTH));
    }

    @Test
    void leSurvolSuitLaSourisEtRetombeAMoinsUnHorsMenu() {
        ContextMenuState menu = nodeMenu();
        menu.hover(menu.x() + 5, middleOf(menu, 2), WIDTH);
        assertEquals(2, menu.hovered());

        menu.hover(menu.x() - 50, menu.y(), WIDTH);
        assertEquals(-1, menu.hovered());
    }

    @Test
    void laHauteurCompteLesSeparateurs() {
        ContextMenuState menu = nodeMenu();
        long separators = menu.items().stream()
                .filter(ContextMenuState.Item::separatorBefore).count();
        assertEquals(4 + menu.items().size() * ContextMenuState.ROW_HEIGHT
                        + separators * ContextMenuState.SEPARATOR_HEIGHT,
                menu.height());
    }

    // ------------------------------------------------------------------- outillage

    private static double middleOf(ContextMenuState menu, int index) {
        return menu.rowTop(index) + ContextMenuState.ROW_HEIGHT / 2.0;
    }

    private static int indexOf(ContextMenuState menu, ContextMenuState.Action action) {
        for (int i = 0; i < menu.items().size(); i++) {
            if (menu.items().get(i).action() == action) {
                return i;
            }
        }
        return -1;
    }

    private static boolean enabled(ContextMenuState menu, ContextMenuState.Action action) {
        int index = indexOf(menu, action);
        assertNotNull(index >= 0 ? menu.items().get(index) : null, action + " absent du menu");
        return menu.items().get(index).enabled();
    }
}
