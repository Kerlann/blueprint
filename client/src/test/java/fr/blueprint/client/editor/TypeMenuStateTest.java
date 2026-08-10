package fr.blueprint.client.editor;

import fr.blueprint.api.pin.PinTypes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La géométrie du menu de type.
 *
 * <p>Elle se teste sans écran parce qu'elle est pure — et elle mérite de l'être : un menu
 * dont le hit-test décale d'une ligne applique un type que personne n'a demandé, et rien
 * dans l'affichage ne le trahit avant que le graphe ne casse.
 */
class TypeMenuStateTest {

    private static final int W = TypeMenuPopup.WIDTH;

    private static TypeMenuState ouvert() {
        TypeMenuState menu = new TypeMenuState();
        menu.open("var1", PinTypes.DOUBLE, VariablePanelState.TYPES, 100, 50);
        return menu;
    }

    @Test
    void chaqueLigneRendSonType() {
        TypeMenuState menu = ouvert();
        var types = VariablePanelState.TYPES;
        for (int i = 0; i < types.size(); i++) {
            // Au milieu de la ligne : les bords se testent à part, ci-dessous.
            double y = menu.rowTop(i) + TypeMenuState.ROW_HEIGHT / 2.0;
            assertEquals(types.get(i), menu.choose(105, y, W),
                    "la ligne " + i + " doit rendre son propre type");
        }
    }

    /**
     * Les bords, parce que c'est là que les décalages d'un pixel se cachent : le haut de
     * la première ligne et le bas de la dernière sont dedans, un pixel au-delà est dehors.
     */
    @Test
    void lesBordsTombentDuBonCote() {
        TypeMenuState menu = ouvert();
        int dernier = VariablePanelState.TYPES.size() - 1;

        assertEquals(VariablePanelState.TYPES.get(0), menu.choose(105, menu.rowTop(0), W));
        assertEquals(VariablePanelState.TYPES.get(dernier),
                menu.choose(105, menu.rowTop(dernier) + TypeMenuState.ROW_HEIGHT - 1, W));

        assertNull(menu.choose(105, menu.rowTop(0) - 1, W), "au-dessus de la première ligne");
        assertNull(menu.choose(105, menu.rowTop(dernier) + TypeMenuState.ROW_HEIGHT, W),
                "sous la dernière ligne");
        assertNull(menu.choose(99, menu.rowTop(0) + 2, W), "à gauche du menu");
        assertNull(menu.choose(100 + W, menu.rowTop(0) + 2, W), "à droite du menu");
    }

    /** Un menu fermé n'attrape aucun clic — sinon il avalerait ceux du canevas. */
    @Test
    void unMenuFermeNAttrapeRien() {
        TypeMenuState menu = ouvert();
        menu.close();
        assertFalse(menu.isOpen());
        assertNull(menu.choose(105, 55, W));
    }

    /**
     * Ouvert en bas de l'écran, le menu remonte pour tenir entier.
     *
     * <p>Sans cela, les derniers types sortiraient sous le bord — et ce sont justement les
     * nouveaux, ceux que le menu existe pour faire découvrir.
     */
    @Test
    void leMenuRemonteQuandIlDeborde() {
        TypeMenuState menu = new TypeMenuState();
        menu.open("var1", PinTypes.DOUBLE, VariablePanelState.TYPES, 10, 240);
        menu.clampToScreen(W, 400, 250);

        assertTrue(menu.y() >= 0);
        assertTrue(menu.y() + menu.height() <= 250,
                "le menu doit tenir entier dans la hauteur de l'écran");
    }

    /** Une liste vide ne fait pas de hauteur négative ni d'index hors bornes. */
    @Test
    void uneListeVideNeCasseRien() {
        TypeMenuState menu = new TypeMenuState();
        menu.open("var1", null, List.of(), 10, 10);
        assertNull(menu.choose(15, 12, W));
        assertTrue(menu.height() > 0);
    }
}
