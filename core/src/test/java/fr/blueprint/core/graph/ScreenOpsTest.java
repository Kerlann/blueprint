package fr.blueprint.core.graph;

import fr.blueprint.core.graph.screen.ElementKind;
import fr.blueprint.core.graph.screen.Extent;
import fr.blueprint.core.graph.screen.Screen;
import fr.blueprint.core.graph.screen.ScreenElement;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Les opérations d'écran (story 10.1, AC4). Ce qui compte ici est la
 * <b>réversibilité</b> : chaque opération doit rendre l'inverse exact, sinon
 * l'annuler/rétablir de l'éditeur et les patchs réseau mentent tous les deux.
 */
class ScreenOpsTest {

    private static final NodeTypeLookup LOOKUP = typeId -> null;

    private Blueprint bp;

    @BeforeEach
    void setUp() {
        bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "ecrans"));
        assertTrue(new ScreenOps.AddScreen(Screen.empty("menu"))
                .apply(bp, LOOKUP).applied());
    }

    private static ScreenElement button(String name) {
        return ScreenElement.of(name, ElementKind.BUTTON, 0, 0, 80, 20);
    }

    private static ScreenElement panel(String name) {
        return ScreenElement.of(name, ElementKind.PANEL, 0, 0, 200, 100);
    }

    /** Applique et rend l'inverse ; échoue si l'opération est refusée. */
    private EditOperation applyOk(EditOperation op) {
        EditOperation.Result result = op.apply(bp, LOOKUP);
        assertTrue(result.applied(), () -> "refusée : " + result.refusal());
        assertNotNull(result.inverse(), "une opération appliquée doit porter son inverse");
        return result.inverse();
    }

    private DiagnosticCode refusalOf(EditOperation op) {
        EditOperation.Result result = op.apply(bp, LOOKUP);
        assertFalse(result.applied(), "cette opération aurait dû être refusée");
        return result.refusal().code();
    }

    // -------------------------------------------------------------- réversibilité

    @Test
    void ajouterUnEcranSeDefaitExactement() {
        int before = bp.screens().size();
        EditOperation undo = applyOk(new ScreenOps.AddScreen(Screen.empty("second")));
        assertEquals(before + 1, bp.screens().size());

        assertTrue(undo.apply(bp, LOOKUP).applied());
        assertEquals(before, bp.screens().size());
        assertNull(bp.screen("second"));
    }

    @Test
    void retirerUnEcranSeDefaitAvecSonContenu() {
        applyOk(new ScreenOps.AddElement("menu", button("ok")));
        Screen before = bp.screen("menu");

        EditOperation undo = applyOk(new ScreenOps.RemoveScreen("menu"));
        assertNull(bp.screen("menu"));

        assertTrue(undo.apply(bp, LOOKUP).applied());
        assertEquals(before, bp.screen("menu"), "l'écran revient identique, éléments compris");
    }

    @Test
    void ajouterEtRetirerUnElementSeDefont() {
        EditOperation undo = applyOk(new ScreenOps.AddElement("menu", button("ok")));
        assertNotNull(bp.screen("menu").element("ok"));

        assertTrue(undo.apply(bp, LOOKUP).applied());
        assertNull(bp.screen("menu").element("ok"));
    }

    /**
     * <b>Le test qui compte.</b> Retirer un conteneur emporte ses descendants ; son
     * inverse doit donc TOUT rendre. Rendre le seul élément laisserait ses enfants
     * perdus — invisibles, indélogeables, et comptés dans le plafond.
     */
    @Test
    void defaireUneSuppressionEnCascadeRendToutLArbre() {
        applyOk(new ScreenOps.AddElement("menu", panel("page")));
        applyOk(new ScreenOps.AddElement("menu", button("ok").withParent("page")));
        applyOk(new ScreenOps.AddElement("menu", button("annuler").withParent("page")));
        Screen before = bp.screen("menu");
        assertEquals(3, before.size());

        EditOperation undo = applyOk(new ScreenOps.RemoveElement("menu", "page"));
        assertEquals(0, bp.screen("menu").size(), "les trois partent ensemble");

        assertTrue(undo.apply(bp, LOOKUP).applied());
        assertEquals(before, bp.screen("menu"), "et reviennent ensemble, dans l'ordre");
    }

    @Test
    void modifierUnElementSeDefait() {
        applyOk(new ScreenOps.AddElement("menu", button("ok")));
        ScreenElement before = bp.screen("menu").element("ok");

        EditOperation undo = applyOk(new ScreenOps.SetElement("menu",
                before.movedTo(40, 60).resized(Extent.of(120), Extent.of(30))));
        assertEquals(40, bp.screen("menu").element("ok").x(), 1e-9);

        assertTrue(undo.apply(bp, LOOKUP).applied());
        assertEquals(before, bp.screen("menu").element("ok"));
    }

    @Test
    void reordonnerSeDefait() {
        applyOk(new ScreenOps.AddElement("menu", button("a")));
        applyOk(new ScreenOps.AddElement("menu", button("b")));
        Screen before = bp.screen("menu");

        EditOperation undo = applyOk(new ScreenOps.ReorderElement("menu", "a", 1));
        assertEquals(List.of("b", "a"), List.copyOf(bp.screen("menu").elements().keySet()));

        assertTrue(undo.apply(bp, LOOKUP).applied());
        assertEquals(before, bp.screen("menu"));
    }

    // ------------------------------------------------------------- le renommage

    /**
     * Renommer rattache les enfants. Les laisser pointer vers l'ancien nom les rendrait
     * orphelins : ils n'apparaîtraient plus nulle part et resteraient dans le plafond.
     */
    @Test
    void renommerUnConteneurRattacheSesEnfants() {
        applyOk(new ScreenOps.AddElement("menu", panel("page")));
        applyOk(new ScreenOps.AddElement("menu", button("ok").withParent("page")));

        applyOk(new ScreenOps.RenameElement("menu", "page", "onglet1"));
        Screen after = bp.screen("menu");

        assertNull(after.element("page"));
        assertNotNull(after.element("onglet1"));
        assertEquals("onglet1", after.element("ok").parent(),
                "l'enfant suit son parent renommé");
        assertEquals(1, after.childrenOf("onglet1").size());
    }

    @Test
    void renommerSeDefait() {
        applyOk(new ScreenOps.AddElement("menu", panel("page")));
        applyOk(new ScreenOps.AddElement("menu", button("ok").withParent("page")));
        Screen before = bp.screen("menu");

        EditOperation undo = applyOk(new ScreenOps.RenameElement("menu", "page", "onglet1"));
        assertTrue(undo.apply(bp, LOOKUP).applied());
        assertEquals(before, bp.screen("menu"));
    }

    @Test
    void renommerVersUnNomDejaPrisEstRefuse() {
        applyOk(new ScreenOps.AddElement("menu", button("a")));
        applyOk(new ScreenOps.AddElement("menu", button("b")));
        assertEquals(DiagnosticCode.DUPLICATE_ELEMENT,
                refusalOf(new ScreenOps.RenameElement("menu", "a", "b")));
    }

    @Test
    void renommerVersLeMemeNomEstAccepte() {
        applyOk(new ScreenOps.AddElement("menu", button("a")));
        applyOk(new ScreenOps.RenameElement("menu", "a", "a"));
        assertNotNull(bp.screen("menu").element("a"));
    }

    // ------------------------------------------------------------------- refus

    @Test
    void lesCiblesInexistantesSontRefuseesNommement() {
        assertEquals(DiagnosticCode.SCREEN_NOT_FOUND,
                refusalOf(new ScreenOps.AddElement("absent", button("x"))));
        assertEquals(DiagnosticCode.ELEMENT_NOT_FOUND,
                refusalOf(new ScreenOps.RemoveElement("menu", "absent")));
        assertEquals(DiagnosticCode.SCREEN_NOT_FOUND,
                refusalOf(new ScreenOps.RemoveScreen("absent")));
        assertEquals(DiagnosticCode.DUPLICATE_SCREEN,
                refusalOf(new ScreenOps.AddScreen(Screen.empty("menu"))));
    }

    @Test
    void unNomDElementEnDoubleEstRefuse() {
        applyOk(new ScreenOps.AddElement("menu", button("ok")));
        assertEquals(DiagnosticCode.DUPLICATE_ELEMENT,
                refusalOf(new ScreenOps.AddElement("menu", button("ok"))));
    }

    /** Un élément sous la taille minimale ne se clique plus : il devient un piège. */
    @Test
    void unElementTropPetitEstRefuse() {
        assertEquals(DiagnosticCode.ELEMENT_TOO_SMALL,
                refusalOf(new ScreenOps.AddElement("menu",
                        ScreenElement.of("miette", ElementKind.BUTTON, 0, 0, 1, 1))));
    }

    @Test
    void unParentInexistantOuNonConteneurEstRefuse() {
        applyOk(new ScreenOps.AddElement("menu", button("feuille")));

        assertEquals(DiagnosticCode.ELEMENT_PARENT_NOT_FOUND,
                refusalOf(new ScreenOps.AddElement("menu",
                        button("perdu").withParent("nulle_part"))));
        assertEquals(DiagnosticCode.ELEMENT_NOT_CONTAINER,
                refusalOf(new ScreenOps.AddElement("menu",
                        button("dedans").withParent("feuille"))),
                "un bouton n'accueille pas d'enfants");
    }

    /**
     * Un cycle de parenté rendrait toute la branche invisible et inatteignable : ni le
     * rendu ni le concepteur ne sauraient par où commencer.
     */
    @Test
    void unCycleDeParenteEstRefuse() {
        applyOk(new ScreenOps.AddElement("menu", panel("a")));
        applyOk(new ScreenOps.AddElement("menu", panel("b").withParent("a")));

        assertEquals(DiagnosticCode.ELEMENT_PARENT_CYCLE,
                refusalOf(new ScreenOps.SetElement("menu",
                        bp.screen("menu").element("a").withParent("b"))),
                "a enfant de b, qui est enfant de a");
        assertEquals(DiagnosticCode.ELEMENT_PARENT_CYCLE,
                refusalOf(new ScreenOps.SetElement("menu",
                        bp.screen("menu").element("a").withParent("a"))),
                "et un élément n'est pas son propre parent");
    }

    /** Un HUD ne capte pas la souris : un bouton y serait un leurre (story 10.9). */
    @Test
    void unElementInteractifDansUnHudEstRefuse() {
        applyOk(new ScreenOps.AddScreen(new Screen("barre", true, List.of())));
        assertEquals(DiagnosticCode.INTERACTIVE_IN_HUD,
                refusalOf(new ScreenOps.AddElement("barre", button("cliquez"))));

        applyOk(new ScreenOps.AddElement("barre",
                ScreenElement.of("texte", ElementKind.LABEL, 0, 0, 80, 20)));
        assertNotNull(bp.screen("barre").element("texte"), "l'affichage, lui, passe");
    }

    // ------------------------------------------------------------------ bornes

    @Test
    void lePlafondDEcransEstApplique() {
        GraphLimits tight = new GraphLimits(1000, 2, 128);
        Blueprint small = new Blueprint(Identifier.fromNamespaceAndPath("test", "petit"));
        assertTrue(new ScreenOps.AddScreen(Screen.empty("a")).apply(small, LOOKUP, tight).applied());
        assertTrue(new ScreenOps.AddScreen(Screen.empty("b")).apply(small, LOOKUP, tight).applied());

        EditOperation.Result third =
                new ScreenOps.AddScreen(Screen.empty("c")).apply(small, LOOKUP, tight);
        assertFalse(third.applied());
        assertEquals(DiagnosticCode.SCREEN_LIMIT_EXCEEDED, third.refusal().code());
    }

    @Test
    void lePlafondDElementsEstApplique() {
        GraphLimits tight = new GraphLimits(1000, 16, 2);
        assertTrue(new ScreenOps.AddElement("menu", button("a")).apply(bp, LOOKUP, tight).applied());
        assertTrue(new ScreenOps.AddElement("menu", button("b")).apply(bp, LOOKUP, tight).applied());

        EditOperation.Result third =
                new ScreenOps.AddElement("menu", button("c")).apply(bp, LOOKUP, tight);
        assertFalse(third.applied());
        assertEquals(DiagnosticCode.ELEMENT_LIMIT_EXCEEDED, third.refusal().code());
    }

    // ---------------------------------------------------------------- révision

    /** Chaque opération appliquée compte ; un refus ne compte pas. */
    @Test
    void laRevisionSuitLesOperationsAppliquees() {
        int start = bp.revision();
        applyOk(new ScreenOps.AddElement("menu", button("ok")));
        assertEquals(start + 1, bp.revision());

        new ScreenOps.AddElement("menu", button("ok")).apply(bp, LOOKUP); // refusée
        assertEquals(start + 1, bp.revision(), "un refus ne fait pas avancer la révision");
    }

    /** Un blueprint copié garde ses écrans — sinon un instantané réseau les perdrait. */
    @Test
    void lesEcransSurviventALaCopieEtALaComparaison() {
        applyOk(new ScreenOps.AddElement("menu", button("ok")));

        Blueprint copy = bp.copy();
        assertEquals(bp.screen("menu"), copy.screen("menu"));
        assertTrue(bp.contentEquals(copy));

        new ScreenOps.AddElement("menu", button("autre")).apply(copy, LOOKUP);
        assertFalse(bp.contentEquals(copy), "une différence d'écran se voit");
    }
}
