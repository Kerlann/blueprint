package fr.blueprint.core.graph.screen;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La passe de disposition (story 10.10) — le cœur de la refonte.
 *
 * <p>Jusqu'ici chaque élément portait son {@code x} et son {@code y} : une colonne de
 * trois boutons se posait bouton par bouton, et en insérer un quatrième obligeait à
 * repositionner les trois autres. Un conteneur range désormais ses enfants lui-même, ce
 * qui veut dire que la place d'un enfant dépend de ses <b>frères</b> — d'où une passe
 * descendante sur tout l'arbre, et non plus une remontée par élément.
 */
class ScreenLayoutSolveTest {

    private static ScreenElement panel(String name, LayoutSpec layout,
                                       double width, double height) {
        return ScreenElement.of(name, ElementKind.PANEL, 0, 0, width, height)
                .withLayout(layout);
    }

    private static ScreenElement child(String name, String parent, Extent w, Extent h) {
        return ScreenElement.of(name, ElementKind.LABEL, 0, 0, 10, 10)
                .withParent(parent)
                .resized(w, h);
    }

    private static Map<String, ScreenLayout.Rect> solve(ScreenElement... elements) {
        return ScreenLayout.solve(new Screen("menu", false, List.of(elements)), 320, 180);
    }

    // ------------------------------------------------------------------ colonne

    @Test
    void uneColonneRangeSesEnfantsDansLOrdreAvecLEspacement() {
        var rects = solve(
                panel("cadre", LayoutSpec.column(4), 100, 100),
                child("a", "cadre", Extent.of(80), Extent.of(20)),
                child("b", "cadre", Extent.of(80), Extent.of(20)),
                child("c", "cadre", Extent.of(80), Extent.of(20)));

        assertEquals(0, rects.get("a").y(), 1e-9);
        assertEquals(24, rects.get("b").y(), 1e-9, "20 de haut + 4 d'espacement");
        assertEquals(48, rects.get("c").y(), 1e-9);
        assertEquals(0, rects.get("a").x(), 1e-9, "aucun x n'a été écrit nulle part");
    }

    /**
     * <b>Le test qui compte.</b> C'est l'irritant nº 1, prouvé : insérer un élément au
     * milieu déplace les suivants sans qu'aucune coordonnée n'ait été touchée.
     */
    @Test
    void insererAuMilieuDeplaceLesSuivantsSansEcrireAucunXni() {
        List<ScreenElement> avant = List.of(
                panel("cadre", LayoutSpec.column(2), 100, 100),
                child("a", "cadre", Extent.of(80), Extent.of(10)),
                child("c", "cadre", Extent.of(80), Extent.of(10)));
        var rectsAvant = ScreenLayout.solve(new Screen("menu", false, avant), 320, 180);
        assertEquals(12, rectsAvant.get("c").y(), 1e-9);

        // On insère « b » entre les deux, sans toucher au x/y de personne.
        List<ScreenElement> apres = new ArrayList<>(avant);
        apres.add(2, child("b", "cadre", Extent.of(80), Extent.of(10)));
        var rectsApres = ScreenLayout.solve(new Screen("menu", false, apres), 320, 180);

        assertEquals(0, rectsApres.get("a").y(), 1e-9);
        assertEquals(12, rectsApres.get("b").y(), 1e-9);
        assertEquals(24, rectsApres.get("c").y(), 1e-9, "« c » a descendu tout seul");
        for (ScreenElement element : apres) {
            assertEquals(0, element.y(), 1e-9, element.name() + " garde un y de zéro");
        }
    }

    @Test
    void troisFillDePoidsEgalSePartagentLaHauteur() {
        var rects = solve(
                panel("cadre", LayoutSpec.column(0), 100, 90),
                child("a", "cadre", Extent.of(80), Extent.fill()),
                child("b", "cadre", Extent.of(80), Extent.fill()),
                child("c", "cadre", Extent.of(80), Extent.fill()));

        for (String name : List.of("a", "b", "c")) {
            assertEquals(30, rects.get(name).height(), 1e-9, name);
        }
        assertEquals(60, rects.get("c").y(), 1e-9);
    }

    @Test
    void unFillNePrendQueCeQuiResteApresLesTaillesFixes() {
        var rects = solve(
                panel("cadre", LayoutSpec.column(0), 100, 100),
                child("titre", "cadre", Extent.of(80), Extent.of(20)),
                child("corps", "cadre", Extent.of(80), Extent.fill()),
                child("pied", "cadre", Extent.of(80), Extent.of(10)));

        assertEquals(70, rects.get("corps").height(), 1e-9, "100 − 20 − 10");
        assertEquals(90, rects.get("pied").y(), 1e-9);
    }

    /** Le poids décide de la part : un poids 3 prend trois fois un poids 1. */
    @Test
    void lePoidsDunFillDecideDeSaPart() {
        var rects = solve(
                panel("cadre", LayoutSpec.column(0), 100, 100),
                child("petit", "cadre", Extent.of(80), Extent.fill(1)),
                child("grand", "cadre", Extent.of(80), Extent.fill(3)));

        assertEquals(25, rects.get("petit").height(), 1e-9);
        assertEquals(75, rects.get("grand").height(), 1e-9);
    }

    @Test
    void unPoidsNulNePrivePasLElementDeToutePlace() {
        var rects = solve(
                panel("cadre", LayoutSpec.column(0), 100, 100),
                child("a", "cadre", Extent.of(80), Extent.fill(0)),
                child("b", "cadre", Extent.of(80), Extent.fill(0)));

        assertEquals(50, rects.get("a").height(), 1e-9, "un poids nul vaut un");
    }

    // -------------------------------------------------------------- répartition

    @Test
    void spaceBetweenColleLesExtremesAuxBords() {
        var rects = solve(
                panel("cadre", LayoutSpec.column(0).withMain(LayoutSpec.Distribute.SPACE_BETWEEN),
                        100, 100),
                child("a", "cadre", Extent.of(80), Extent.of(10)),
                child("b", "cadre", Extent.of(80), Extent.of(10)),
                child("c", "cadre", Extent.of(80), Extent.of(10)));

        assertEquals(0, rects.get("a").y(), 1e-9);
        assertEquals(100, rects.get("c").bottom(), 1e-9, "le dernier touche le bas");
        assertEquals(45, rects.get("b").y(), 1e-9, "et le milieu est au milieu");
    }

    @Test
    void centrerRegroupeLesEnfantsAuMilieu() {
        var rects = solve(
                panel("cadre", LayoutSpec.column(0).withMain(LayoutSpec.Distribute.CENTER),
                        100, 100),
                child("a", "cadre", Extent.of(80), Extent.of(20)),
                child("b", "cadre", Extent.of(80), Extent.of(20)));

        assertEquals(30, rects.get("a").y(), 1e-9, "(100 − 40) / 2");
        assertEquals(70, rects.get("b").bottom(), 1e-9);
    }

    // ----------------------------------------------------------- axe transverse

    @Test
    void stretchEtireSurLAxeTransverse() {
        var rects = solve(
                panel("cadre", LayoutSpec.column(0).withCross(LayoutSpec.Cross.STRETCH),
                        100, 100),
                child("a", "cadre", Extent.of(20), Extent.of(10)));

        assertEquals(100, rects.get("a").width(), 1e-9, "la largeur écrite est ignorée");
    }

    @Test
    void centrerSurLAxeTransverseNeChangePasLaTaille() {
        var rects = solve(
                panel("cadre", LayoutSpec.column(0).withCross(LayoutSpec.Cross.CENTER),
                        100, 100),
                child("a", "cadre", Extent.of(20), Extent.of(10)));

        assertEquals(20, rects.get("a").width(), 1e-9);
        assertEquals(40, rects.get("a").x(), 1e-9, "(100 − 20) / 2");
    }

    /** Un FILL sur l'axe transverse prend toute la largeur, quel que soit l'alignement. */
    @Test
    void unFillTransversePrendToutSansStretch() {
        var rects = solve(
                panel("cadre", LayoutSpec.column(0), 100, 100),
                child("a", "cadre", Extent.fill(), Extent.of(10)));

        assertEquals(100, rects.get("a").width(), 1e-9);
    }

    // ------------------------------------------------------------------- ligne

    @Test
    void uneLigneRangeHorizontalement() {
        var rects = solve(
                panel("barre", LayoutSpec.row(6), 200, 30),
                child("a", "barre", Extent.of(50), Extent.of(20)),
                child("b", "barre", Extent.of(50), Extent.of(20)));

        assertEquals(0, rects.get("a").x(), 1e-9);
        assertEquals(56, rects.get("b").x(), 1e-9);
        assertEquals(0, rects.get("a").y(), 1e-9, "même rangée");
    }

    // ------------------------------------------------------------------ grille

    @Test
    void uneGrilleDeTroisColonnesPlaceSeptElementsSurTroisRangees() {
        List<ScreenElement> elements = new ArrayList<>();
        elements.add(panel("sac", LayoutSpec.grid(3, 2, 2), 100, 100));
        for (int i = 0; i < 7; i++) {
            elements.add(child("c" + i, "sac", Extent.of(30), Extent.of(20)));
        }
        var rects = ScreenLayout.solve(new Screen("menu", false, elements), 320, 180);

        assertEquals(rects.get("c0").y(), rects.get("c2").y(), 1e-9, "même rangée");
        assertTrue(rects.get("c3").y() > rects.get("c0").y(), "rangée suivante");
        assertTrue(rects.get("c6").y() > rects.get("c3").y(), "troisième rangée");
        assertEquals(rects.get("c0").x(), rects.get("c3").x(), 1e-9, "même colonne");
        assertEquals(22, rects.get("c3").y(), 1e-9, "20 de haut + 2 d'espacement");
    }

    // --------------------------------------------------------------- imbrication

    @Test
    void uneDispositionSImbriqueDansUneAutre() {
        var rects = solve(
                panel("page", LayoutSpec.column(4), 200, 100),
                panel("barre", LayoutSpec.row(2), 0, 0)
                        .withParent("page").resized(Extent.fill(), Extent.of(20)),
                child("gauche", "barre", Extent.of(40), Extent.of(20)),
                child("droite", "barre", Extent.of(40), Extent.of(20)));

        assertEquals(200, rects.get("barre").width(), 1e-9);
        assertEquals(0, rects.get("gauche").x(), 1e-9);
        assertEquals(42, rects.get("droite").x(), 1e-9);
    }

    /** Un conteneur ABSOLUTE garde exactement le comportement d'avant la refonte. */
    @Test
    void unConteneurAbsoluNeRangeRien() {
        var rects = solve(
                panel("cadre", LayoutSpec.ABSOLUTE, 100, 100),
                ScreenElement.of("libre", ElementKind.LABEL, 30, 40, 20, 10)
                        .withParent("cadre"));

        assertEquals(30, rects.get("libre").x(), 1e-9);
        assertEquals(40, rects.get("libre").y(), 1e-9);
    }

    /** Un non-conteneur ne range jamais, même si une disposition traîne dessus. */
    @Test
    void unNonConteneurNeRangeJamais() {
        ScreenElement label = ScreenElement.of("texte", ElementKind.LABEL, 0, 0, 100, 100)
                .withLayout(LayoutSpec.column(4));
        var rects = solve(label,
                ScreenElement.of("dessous", ElementKind.LABEL, 5, 5, 10, 10)
                        .withParent("texte"));

        assertEquals(5, rects.get("dessous").x(), 1e-9, "placement absolu conservé");
    }

    // -------------------------------------------------------- la taille d'écran

    /**
     * <b>Le second test qui compte.</b> C'est l'irritant nº 2 : le même écran, résolu à
     * deux tailles de fenêtre, garde ses proportions et ses espacements sans qu'on ait
     * réglé quoi que ce soit élément par élément.
     */
    @Test
    void leMemeEcranTientADeuxTaillesDeFenetre() {
        Screen screen = new Screen("menu", false, List.of(
                panel("cadre", LayoutSpec.column(4), 0, 0)
                        .resized(Extent.percent(0.5, 0, 0), Extent.fill()),
                child("a", "cadre", Extent.fill(), Extent.fill()),
                child("b", "cadre", Extent.fill(), Extent.fill())));

        var petit = ScreenLayout.solve(screen, 320, 180);
        var grand = ScreenLayout.solve(screen, 960, 540);

        assertEquals(160, petit.get("cadre").width(), 1e-9);
        assertEquals(480, grand.get("cadre").width(), 1e-9, "la moitié, des deux côtés");
        assertEquals(160, petit.get("a").width(), 1e-9, "l'enfant remplit son parent");
        assertEquals(480, grand.get("a").width(), 1e-9);
        // L'espacement reste en unités : c'est voulu — 4 unités restent lisibles
        // partout, alors qu'un espacement proportionnel deviendrait un gouffre.
        assertEquals(petit.get("b").y() - petit.get("a").bottom(),
                grand.get("b").y() - grand.get("a").bottom(), 1e-9);
    }

    // ------------------------------------------------------------- robustesse

    @Test
    void unCycleDeParenteNeBouclePas() {
        ScreenElement a = panel("a", LayoutSpec.column(0), 50, 50).withParent("b");
        ScreenElement b = panel("b", LayoutSpec.column(0), 50, 50).withParent("a");

        var rects = ScreenLayout.solve(new Screen("menu", false, List.of(a, b)), 320, 180);
        assertTrue(rects.isEmpty(), "aucune racine : rien n'est placé, mais rien ne boucle");
    }

    /**
     * Un parent disparu ne doit pas <b>faire disparaître son enfant</b>. La remontée par
     * élément le faisait retomber sur l'écran ; la passe descendante, elle, ne voyait
     * l'orphelin nulle part dans l'arbre et ne le plaçait jamais. Le validateur signale
     * bien la référence morte, mais l'auteur l'aurait d'abord constatée par un menu
     * amputé — et aurait cherché la cause dans le dessin plutôt que dans la parenté.
     */
    @Test
    void unOrphelinRetombeSurLEcranAuLieuDeDisparaitre() {
        ScreenElement orphelin = ScreenElement.of("x", ElementKind.LABEL, 7, 8, 20, 10)
                .withParent("disparu");
        ScreenElement racine = ScreenElement.of("y", ElementKind.LABEL, 1, 2, 20, 10);

        var rects = ScreenLayout.solve(
                new Screen("menu", false, List.of(orphelin, racine)), 320, 180);

        assertNotNull(rects.get("x"), "l'orphelin est placé, pas escamoté");
        assertEquals(7, rects.get("x").x(), 1e-9, "à la racine, comme resolve le fait");
        assertEquals(8, rects.get("x").y(), 1e-9);
        assertNotNull(rects.get("y"), "et la vraie racine n'a pas été perdue au passage");
    }

    // -------------------------------------------------------- s'ajuster au contenu

    /**
     * Un conteneur {@code hug} épouse ses enfants au lieu de prendre la place qu'on lui
     * a écrite. C'est ce qui permet une infobulle ou un cadre de dialogue dont la hauteur
     * suit ce qu'il contient, sans la recalculer à la main à chaque ligne ajoutée.
     */
    @Test
    void unConteneurQuiSAjusteEpouseSesEnfants() {
        var rects = solve(
                panel("cadre", LayoutSpec.column(4), 999, 999)
                        .resized(Extent.hug(), Extent.hug()),
                child("a", "cadre", Extent.of(60), Extent.of(20)),
                child("b", "cadre", Extent.of(80), Extent.of(10)));

        assertEquals(34, rects.get("cadre").height(), 1e-9, "20 + 4 d'écart + 10");
        assertEquals(80, rects.get("cadre").width(), 1e-9, "le plus large de ses enfants");
    }

    /** Les bornes valent aussi pour une taille ajustée : un contenu vide ne l'écrase pas. */
    @Test
    void unConteneurQuiSAjusteRespecteSesBornes() {
        var rects = solve(
                panel("cadre", LayoutSpec.column(0), 100, 100)
                        .resized(new Extent(Extent.Mode.HUG, 0, 40, 0), Extent.hug()));

        assertEquals(40, rects.get("cadre").width(), 1e-9, "vide, mais pas plus étroit que 40");
    }

    /** Un conteneur qui s'ajuste, rangé dans une colonne, garde bien sa mesure. */
    @Test
    void unConteneurQuiSAjusteRangeDansUneColonneGardeSaMesure() {
        var rects = solve(
                panel("externe", LayoutSpec.column(0).withCross(LayoutSpec.Cross.STRETCH),
                        200, 200),
                panel("interne", LayoutSpec.column(0), 999, 999)
                        .withParent("externe")
                        .resized(Extent.hug(), Extent.hug()),
                child("a", "interne", Extent.of(50), Extent.of(30)));

        assertEquals(30, rects.get("interne").height(), 1e-9);
        assertEquals(50, rects.get("interne").width(), 1e-9,
                "l'étirement du parent ne contredit pas l'ajustement demandé");
    }

    /** Deux conteneurs qui s'ajustent l'un à l'autre ne font pas boucler la mesure. */
    @Test
    void unCycleDeConteneursQuiSAjustentNeBouclePas() {
        ScreenElement a = panel("a", LayoutSpec.column(0), 50, 50)
                .resized(Extent.hug(), Extent.hug()).withParent("b");
        ScreenElement b = panel("b", LayoutSpec.column(0), 50, 50)
                .resized(Extent.hug(), Extent.hug()).withParent("a");

        var rects = ScreenLayout.solve(new Screen("menu", false, List.of(a, b)), 320, 180);
        assertTrue(rects.isEmpty(), "rien de placé, mais la mesure s'arrête");
    }

    @Test
    void unEcranVideNeRendRien() {
        assertTrue(ScreenLayout.solve(Screen.empty("menu"), 320, 180).isEmpty());
    }

    /** Des enfants plus grands que leur parent ne produisent pas de place négative. */
    @Test
    void unDebordementNeDonnePasDeTailleNegative() {
        var rects = solve(
                panel("cadre", LayoutSpec.column(4), 100, 30),
                child("a", "cadre", Extent.of(80), Extent.of(40)),
                child("b", "cadre", Extent.of(80), Extent.fill()));

        assertTrue(rects.get("b").height() >= 0, "la part restante ne passe pas sous zéro");
    }
}
