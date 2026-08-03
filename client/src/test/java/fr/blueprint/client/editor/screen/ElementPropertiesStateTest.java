package fr.blueprint.client.editor.screen;

import fr.blueprint.core.graph.screen.Anchor;
import fr.blueprint.core.graph.screen.ElementKind;
import fr.blueprint.core.graph.screen.Extent;
import fr.blueprint.core.graph.screen.ScreenElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le panneau de propriétés (story 10.2, AC7). Deux exigences : rien d'invalide n'entre
 * dans le modèle, et l'auteur le sait <b>pendant</b> qu'il tape.
 */
class ElementPropertiesStateTest {

    private static final Predicate<String> LIBRE = name -> !name.isBlank() && !name.equals("pris");

    private ElementPropertiesState state;

    @BeforeEach
    void setUp() {
        state = new ElementPropertiesState();
        state.select(ScreenElement.of("bouton", ElementKind.BUTTON, 12, 34, 60, 20));
    }

    private void typeAll(String text) {
        for (char c : text.toCharArray()) {
            state.type(c);
        }
    }

    private void edit(ElementPropertiesState.Field field, String text) {
        state.beginEdit(field);
        for (int i = state.buffer().length(); i > 0; i--) {
            state.backspace();
        }
        typeAll(text);
    }

    @Test
    void lesChampsAffichentLaValeurCourante() {
        assertEquals("bouton", state.valueOf(ElementPropertiesState.Field.NAME));
        assertEquals("12", state.valueOf(ElementPropertiesState.Field.X));
        assertEquals("60", state.valueOf(ElementPropertiesState.Field.WIDTH));
        assertEquals("", state.valueOf(ElementPropertiesState.Field.TEXTURE));
    }

    @Test
    void unPourcentageSAfficheCommeEnBScript() {
        state.select(ScreenElement.of("a", ElementKind.PANEL, 0, 0, 10, 10)
                .resized(Extent.percent(0.5, 0, 0), Extent.of(20)));
        assertEquals("50%", state.valueOf(ElementPropertiesState.Field.WIDTH));
    }

    /**
     * <b>Le test qui compte.</b> Sans tampon de frappe, taper « -1 » serait impossible :
     * le « - » seul ne se convertit pas, la conversion échouerait, et le champ
     * reviendrait à sa valeur d'avant à chaque caractère.
     */
    @Test
    void uneFrappeIntermediaireInvalideNEcrasePasLeChamp() {
        state.beginEdit(ElementPropertiesState.Field.X);
        for (int i = state.buffer().length(); i > 0; i--) {
            state.backspace();
        }
        state.type('-');
        assertFalse(state.valid(LIBRE), "« - » seul n'est pas un nombre");
        assertNull(state.commit(LIBRE), "et rien n'est écrit");

        state.type('1');
        assertTrue(state.valid(LIBRE));
        assertEquals(-1, state.commit(LIBRE).x(), 1e-9);
    }

    @Test
    void unNomDejaPrisSeVoitPendantLaFrappe() {
        edit(ElementPropertiesState.Field.NAME, "pris");
        assertFalse(state.valid(LIBRE));

        edit(ElementPropertiesState.Field.NAME, "libre");
        assertTrue(state.valid(LIBRE));
        assertEquals("libre", state.pendingName());
    }

    @Test
    void unNomVideNEstPasUnNom() {
        edit(ElementPropertiesState.Field.NAME, "   ");
        assertFalse(state.valid(LIBRE));
    }

    @Test
    void unePositionSecritDansLeModele() {
        edit(ElementPropertiesState.Field.Y, "80");
        ScreenElement out = state.commit(LIBRE);
        assertEquals(80, out.y(), 1e-9);
        assertEquals(12, out.x(), 1e-9, "l'autre axe ne bouge pas");
        assertNull(state.editing(), "et le champ se referme");
    }

    /** Une taille tapée en pourcentage le reste : la nature du champ suit la frappe. */
    @Test
    void unePourcentageTapeResteRelatif() {
        edit(ElementPropertiesState.Field.WIDTH, "40%");
        ScreenElement out = state.commit(LIBRE);
        assertTrue(out.width().relative());
        assertEquals(0.4, out.width().value(), 1e-9);

        state.select(out);
        edit(ElementPropertiesState.Field.WIDTH, "90");
        assertFalse(state.commit(LIBRE).width().relative(), "et repasse en unités si on l'écrit");
    }

    @Test
    void unDieseFaitDuTexteUneCleDeTraduction() {
        edit(ElementPropertiesState.Field.TEXT, "#menu.acheter");
        var text = state.commit(LIBRE).text();
        assertTrue(text.translate(), "NFR10 : traduisible sans quitter le panneau");
        assertEquals("menu.acheter", text.value());

        state.select(ScreenElement.of("b", ElementKind.LABEL, 0, 0, 10, 10));
        edit(ElementPropertiesState.Field.TEXT, "Acheter");
        assertFalse(state.commit(LIBRE).text().translate());
    }

    @Test
    void uneTextureInvalideEstRefuseeEtLeVideLEfface() {
        edit(ElementPropertiesState.Field.TEXTURE, "PAS UN ID");
        assertFalse(state.valid(LIBRE));
        assertNull(state.commit(LIBRE));

        edit(ElementPropertiesState.Field.TEXTURE, "boutique:textures/gui/fond.png");
        assertNotNull(state.commit(LIBRE).texture());

        state.select(ScreenElement.of("c", ElementKind.IMAGE, 0, 0, 10, 10)
                .withTexture(net.minecraft.resources.Identifier
                        .fromNamespaceAndPath("pack", "a.png")));
        edit(ElementPropertiesState.Field.TEXTURE, "");
        assertNull(state.commit(LIBRE).texture(), "le champ vidé retire la texture");
    }

    @Test
    void uneCouleurSecritEnHexadecimal() {
        assertEquals("#C0141519", state.valueOf(ElementPropertiesState.Field.BACKGROUND));
        edit(ElementPropertiesState.Field.BACKGROUND, "#FF203040");
        assertEquals(0xFF203040, state.commit(LIBRE).style().background());

        edit(ElementPropertiesState.Field.BORDER, "zzz");
        assertFalse(state.valid(LIBRE));
    }

    @Test
    void uneMargeNegativeEstRamenneeAZero() {
        edit(ElementPropertiesState.Field.PADDING, "-5");
        assertEquals(0, state.commit(LIBRE).style().padding(),
                "le modèle refuse une marge négative : on la borne plutôt que de lever");
    }

    @Test
    void lAncreTourneDansLesDeuxSens() {
        assertEquals(Anchor.TOP_CENTER, state.cycleAnchor(1).anchor());
        assertEquals(Anchor.BOTTOM_RIGHT, state.cycleAnchor(-1).anchor(), "et boucle");
    }

    /**
     * Revalider le graphe est débouncé et retombe pendant la frappe. Effacer le tampon
     * à ce moment-là ferait perdre un caractère sur deux à l'auteur.
     */
    @Test
    void reselectionnerLeMemeElementNInterrompPasLaFrappe() {
        edit(ElementPropertiesState.Field.X, "99");
        state.select(ScreenElement.of("bouton", ElementKind.BUTTON, 12, 34, 60, 20));

        assertEquals(ElementPropertiesState.Field.X, state.editing());
        assertEquals("99", state.buffer());
    }

    @Test
    void changerDElementFermeLeChampOuvert() {
        edit(ElementPropertiesState.Field.X, "99");
        state.select(ScreenElement.of("autre", ElementKind.LABEL, 0, 0, 10, 10));

        assertNull(state.editing());
        assertEquals("", state.buffer());
    }

    @Test
    void sansSelectionLePanneauNeFaitRien() {
        state.select(null);
        state.beginEdit(ElementPropertiesState.Field.X);
        state.type('5');

        assertNull(state.editing());
        assertNull(state.commit(LIBRE));
        assertNull(state.cycleAnchor(1));
        assertEquals("", state.valueOf(ElementPropertiesState.Field.NAME));
    }
}
