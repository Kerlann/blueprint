package fr.blueprint.client.editor.screen;

import fr.blueprint.core.graph.screen.Anchor;
import fr.blueprint.core.graph.screen.ElementKind;
import fr.blueprint.core.graph.screen.Extent;
import fr.blueprint.core.graph.screen.Screen;
import fr.blueprint.core.graph.screen.ScreenElement;
import fr.blueprint.core.graph.screen.ScreenLayout;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reparenter déplace un élément dans l'arbre, pas sur l'écran.
 *
 * <p>C'est le point 1.10 de la feuille de vérification — « au relâchement il devient son
 * enfant » — et la moitié qu'un œil vérifie mal. On regarde si le calque a changé de
 * branche ; on ne compte pas les pixels. Or la position d'un enfant est <b>relative à son
 * parent</b> : la donner à un conteneur posé en (40, 30) sans rien convertir décale
 * l'élément de quarante pixels à droite et trente vers le bas, d'un coup, au moment où on
 * lâche le bouton.
 *
 * <p>Un saut au relâchement est le pire moment pour en faire un : l'auteur vient de viser
 * une cible, et ce qu'il a visé n'est plus là où il l'a laissé.
 */
class ReparentKeepsPlaceTest {

    /** Un panneau posé loin de l'origine, et un texte libre à côté de lui. */
    private static Screen twoElements() {
        return new Screen("menu", false, List.of(
                ScreenElement.of("boite", ElementKind.PANEL, 40, 30, 120, 80),
                ScreenElement.of("texte", ElementKind.LABEL, 10, 12, 60, 10)));
    }

    private static ScreenLayout.Rect rectOf(Screen screen, String name) {
        return ScreenLayout.solve(screen, 320, 180).get(name);
    }

    /**
     * <b>Le test qui compte : l'élément ne bouge pas d'un pixel.</b>
     *
     * <p>Avant et après le reparentage, le rectangle <i>absolu</i> est le même. C'est la
     * seule formulation qui vaille : comparer les coordonnées <i>écrites</i> passerait à
     * côté du sujet, puisque c'est justement leur repère qui change.
     */
    @Test
    void adopterUnElementNeLeDeplacePasSurLecran() {
        Screen before = twoElements();
        ScreenLayout.Rect was = rectOf(before, "texte");

        Screen after = before.with(
                ScreenDesignerReparent.adopted(before, before.element("texte"), "boite"));
        ScreenLayout.Rect now = rectOf(after, "texte");

        assertEquals("boite", after.element("texte").parent(), "il a bien changé de branche");
        assertEquals(was.x(), now.x(), 1e-9, "il a sauté horizontalement");
        assertEquals(was.y(), now.y(), 1e-9, "il a sauté verticalement");
    }

    /**
     * <b>Et il ne bouge pas davantage en sortant.</b>
     *
     * <p>Le geste 1.11 — lâcher sur l'en-tête CALQUES pour revenir à la racine — est le
     * même trajet en sens inverse, et la conversion doit l'être aussi. Une conversion qui
     * ne marche que dans un sens se remarque au second glisser, pas au premier.
     */
    @Test
    void sortirUnElementNeLeDeplacePasNonPlus() {
        Screen nested = twoElements().with(
                ScreenElement.of("texte", ElementKind.LABEL, 10, 12, 60, 10)
                        .withParent("boite"));
        ScreenLayout.Rect was = rectOf(nested, "texte");

        Screen after = nested.with(
                ScreenDesignerReparent.adopted(nested, nested.element("texte"), null));
        ScreenLayout.Rect now = rectOf(after, "texte");

        assertEquals(null, after.element("texte").parent(), "il est bien remonté à la racine");
        assertEquals(was.x(), now.x(), 1e-9, "il a sauté horizontalement");
        assertEquals(was.y(), now.y(), 1e-9, "il a sauté verticalement");
    }

    /**
     * L'aller-retour rend exactement les coordonnées de départ.
     *
     * <p>Deux conversions qui se composent mal dérivent d'un pixel à chaque passage —
     * invisible une fois, exaspérant au dixième. Le test le dit tout de suite.
     */
    @Test
    void unAllerRetourRendLesCoordonneesDeDepart() {
        Screen before = twoElements();
        ScreenElement origin = before.element("texte");

        Screen in = before.with(
                ScreenDesignerReparent.adopted(before, origin, "boite"));
        Screen out = in.with(
                ScreenDesignerReparent.adopted(in, in.element("texte"), null));

        assertEquals(origin.x(), out.element("texte").x(), 1e-9);
        assertEquals(origin.y(), out.element("texte").y(), 1e-9);
        assertEquals(null, out.element("texte").parent());
    }

    /**
     * Une ancre autre que le coin haut-gauche se convertit elle aussi.
     *
     * <p>L'ancre décide du point du parent auquel les coordonnées se rapportent. La
     * conversion doit donc passer par la <b>place réellement occupée</b>, pas par une
     * soustraction des origines — celle-ci ne serait juste que pour l'ancre par défaut, et
     * fausse pour les huit autres.
     */
    @Test
    void uneAncreCentreeSeConvertitAussi() {
        Screen before = twoElements().with(
                ScreenElement.of("texte", ElementKind.LABEL, 10, 12, 60, 10)
                        .withAnchor(Anchor.CENTER));
        ScreenLayout.Rect was = rectOf(before, "texte");

        Screen after = before.with(
                ScreenDesignerReparent.adopted(before, before.element("texte"), "boite"));
        ScreenLayout.Rect now = rectOf(after, "texte");

        assertEquals(was.x(), now.x(), 1e-9, "l'ancre centrée a été traitée comme un coin");
        assertEquals(was.y(), now.y(), 1e-9);
    }

    /**
     * Un conteneur qui <b>range</b> ses enfants ne reçoit pas de coordonnées.
     *
     * <p>Une colonne place ce qu'elle contient ; lui donner un x et un y serait écrire des
     * nombres qui n'agissent sur rien. La conversion doit s'abstenir, pas produire une
     * valeur morte — c'est ce que le panneau reproche déjà aux champs sans objet.
     */
    @Test
    void unParentQuiRangeNeRecoitPasDeCoordonnees() {
        Screen before = new Screen("menu", false, List.of(
                ScreenElement.of("colonne", ElementKind.PANEL, 40, 30, 120, 80)
                        .withLayout(fr.blueprint.core.graph.screen.LayoutSpec.ABSOLUTE
                                .withMode(fr.blueprint.core.graph.screen.LayoutSpec.Mode.COLUMN)),
                ScreenElement.of("texte", ElementKind.LABEL, 10, 12, 60, 10)));

        ScreenElement moved = ScreenDesignerReparent.adopted(
                before, before.element("texte"), "colonne");

        assertEquals("colonne", moved.parent());
        assertEquals(0, moved.x(), 1e-9, "une colonne place ses enfants : x ne veut rien dire");
        assertEquals(0, moved.y(), 1e-9);
    }

    /** Une taille relative n'est pas convertie en dur au passage. */
    @Test
    void uneTailleRelativeResteRelative() {
        Screen before = twoElements().with(
                ScreenElement.of("texte", ElementKind.LABEL, 10, 12, 60, 10)
                        .resized(Extent.percent(0.5, 0, 0), Extent.of(10)));

        ScreenElement moved = ScreenDesignerReparent.adopted(
                before, before.element("texte"), "boite");

        assertEquals(Extent.Mode.PERCENT, moved.width().mode(),
                "reparenter ne fige pas une largeur relative");
        assertEquals(0.5, moved.width().value(), 1e-9);
    }
}
