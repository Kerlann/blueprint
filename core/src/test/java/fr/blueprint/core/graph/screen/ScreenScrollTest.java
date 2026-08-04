package fr.blueprint.core.graph.screen;

import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.DiagnosticCode;
import fr.blueprint.core.graph.GraphNbt;
import fr.blueprint.core.graph.GraphValidator;
import fr.blueprint.core.graph.ScreenOps;
import fr.blueprint.core.registry.PluginLoader;
import fr.blueprint.core.script.ScriptGenerator;
import fr.blueprint.core.script.ScriptParser;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le panneau défilant (story 10.13).
 *
 * <p>Une <b>liste</b> défilait déjà, mais ses lignes sont un gabarit répété : du texte, et
 * rien d'autre. Un menu de réglages, une page de règles ou une fiche de personnage sont
 * faits d'éléments <i>différents</i>. Sans conteneur défilant, il fallait les répartir sur
 * plusieurs écrans reliés par des boutons « suivant » — ce qui est une pagination, pas un
 * menu.
 *
 * <p>Le piège de ce chantier n'est pas le dessin, c'est que le <b>décalage doit être vu
 * par le clic</b> exactement comme par le dessin. C'est ce que ces tests verrouillent.
 */
class ScreenScrollTest {

    private static final PluginLoader.LoadedRegistries LOADED = PluginLoader.load(List.of(), true);

    /** Un panneau de 100 de haut, en colonne, contenant six boutons de 20 : 120 + écarts. */
    private static Screen panneau(boolean scrolls) {
        List<ScreenElement> elements = new ArrayList<>();
        elements.add(ScreenElement.of("cadre", ElementKind.PANEL, 0, 0, 200, 100)
                .withLayout(LayoutSpec.column(0).withScroll(scrolls)));
        for (int i = 0; i < 6; i++) {
            elements.add(ScreenElement.of("b" + i, ElementKind.BUTTON, 0, 0, 100, 20)
                    .withParent("cadre"));
        }
        return new Screen("menu", false, elements);
    }

    private static Blueprint with(Screen screen) {
        Blueprint bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "defile"));
        new ScreenOps.AddScreen(screen).apply(bp, LOADED.nodes());
        return bp;
    }

    // ------------------------------------------------------------ le décalage

    @Test
    void sansDefilementLesEnfantsNeBougentPas() {
        Map<String, ScreenLayout.Rect> placed = ScreenLayout.solve(panneau(false), 320, 180);
        assertEquals(0, placed.get("b0").y(), 1e-9);
        assertEquals(100, placed.get("b5").y(), 1e-9);
    }

    /**
     * <b>Le test qui compte.</b> Le décalage est appliqué DANS la passe unique, donc tout
     * ce qui lit la table — le dessin comme le clic — le voit.
     *
     * <p>L'appliquer au dessin après coup aurait marché tout de suite et divergé plus
     * tard : on cliquerait une ligne et l'on en activerait une autre, sans que rien à
     * l'écran ne l'explique. Ce projet a payé cette leçon deux fois, au tracé des fils
     * (5.12) et au concepteur qui peignait à une taille pendant que le clic résolvait à
     * une autre (10.11).
     */
    @Test
    void leDecalageRemonteTousLesEnfantsDansLaMemeTable() {
        Screen screen = panneau(true);
        Map<String, ScreenLayout.Rect> placed =
                ScreenLayout.solve(screen, 320, 180, container -> 40);

        assertEquals(-40, placed.get("b0").y(), 1e-9, "le premier est sorti par le haut");
        assertEquals(60, placed.get("b5").y(), 1e-9, "le dernier est entré par le bas");
        assertEquals(0, placed.get("cadre").y(), 1e-9, "le cadre, lui, ne bouge pas");
    }

    @Test
    void unPanneauQuiNeDefilePasIgnoreLeDecalage() {
        Map<String, ScreenLayout.Rect> placed =
                ScreenLayout.solve(panneau(false), 320, 180, container -> 40);
        assertEquals(0, placed.get("b0").y(), 1e-9,
                "cocher la case est le seul moyen de faire défiler quoi que ce soit");
    }

    // --------------------------------------------------------------- la plage

    @Test
    void laPlageEstCeQuiDepasse() {
        Screen screen = panneau(true);
        ScreenElement cadre = screen.element("cadre");

        Map<String, ScreenLayout.Rect> placed = ScreenLayout.solve(screen, 320, 180);
        assertEquals(20, ScreenLayout.scrollRange(screen, cadre, placed, 0), 1e-9,
                "six boutons de 20 dans un cadre de 100 : il en dépasse 20");
    }

    /** Elle ne dépend pas de la position de lecture : c'est la même page qu'on parcourt. */
    @Test
    void laPlageNeChangePasQuandOnADejaDefile() {
        Screen screen = panneau(true);
        ScreenElement cadre = screen.element("cadre");

        for (double at : new double[]{0, 5, 20}) {
            Map<String, ScreenLayout.Rect> placed =
                    ScreenLayout.solve(screen, 320, 180, container -> at);
            assertEquals(20, ScreenLayout.scrollRange(screen, cadre, placed, at), 1e-9,
                    () -> "plage fausse à " + at);
        }
    }

    @Test
    void unContenuQuiTientNaRienAFaireDefiler() {
        Screen screen = new Screen("menu", false, List.of(
                ScreenElement.of("cadre", ElementKind.PANEL, 0, 0, 200, 100)
                        .withLayout(LayoutSpec.column(0).withScroll(true)),
                ScreenElement.of("b", ElementKind.BUTTON, 0, 0, 100, 20).withParent("cadre")));

        assertEquals(0, ScreenLayout.scrollRange(screen, screen.element("cadre"),
                ScreenLayout.solve(screen, 320, 180), 0), 1e-9,
                "un curseur de défilement sur un contenu complet mentirait sur ce qui reste");
    }

    // -------------------------------------------------------------- le découpage

    /**
     * <b>Le second test qui compte.</b> Ce qui est sorti du cadre est <b>découpé</b>, et
     * ce découpage est celui que le hit-test lira.
     *
     * <p>Sans lui, un bouton sorti du panneau — invisible — resterait cliquable : le
     * joueur viserait le menu en dessous, activerait ce qu'il ne voit pas, et le graphe
     * recevrait une action que rien à l'écran n'expliquerait.
     */
    @Test
    void ceQuiSortDuCadreEstDecoupe() {
        Screen screen = panneau(true);
        Map<String, ScreenLayout.Rect> placed =
                ScreenLayout.solve(screen, 320, 180, container -> 40);

        ScreenLayout.Rect clip = ScreenLayout.clipOf(screen, screen.element("b0"), placed);
        assertNotNull(clip, "un enfant de panneau défilant est découpé");
        assertEquals(placed.get("cadre"), clip, "au cadre du panneau, exactement");

        ScreenLayout.Rect sorti = placed.get("b0");
        assertFalse(clip.contains(sorti.x() + 1, sorti.y() + 1),
                "le premier bouton est remonté hors du cadre : il ne doit plus se cliquer");
        ScreenLayout.Rect dedans = placed.get("b3");
        assertTrue(clip.contains(dedans.x() + 1, dedans.y() + 1),
                "celui du milieu, lui, est bien dedans");
    }

    @Test
    void sansAncetreDefilantRienNestDecoupe() {
        Screen screen = panneau(false);
        assertNull(ScreenLayout.clipOf(screen, screen.element("b0"),
                ScreenLayout.solve(screen, 320, 180)));
    }

    /** Deux panneaux défilants imbriqués : le découpage est l'INTERSECTION des deux. */
    @Test
    void deuxPanneauxImbriquesSeDecoupentEnsemble() {
        Screen screen = new Screen("menu", false, List.of(
                ScreenElement.of("dehors", ElementKind.PANEL, 0, 0, 200, 100)
                        .withLayout(LayoutSpec.ABSOLUTE.withScroll(true)),
                ScreenElement.of("dedans", ElementKind.PANEL, 0, 50, 200, 200)
                        .withParent("dehors")
                        .withLayout(LayoutSpec.ABSOLUTE.withScroll(true)),
                ScreenElement.of("feuille", ElementKind.LABEL, 0, 0, 50, 10)
                        .withParent("dedans")));

        var placed = ScreenLayout.solve(screen, 320, 180);
        ScreenLayout.Rect clip =
                ScreenLayout.clipOf(screen, screen.element("feuille"), placed);

        assertNotNull(clip);
        assertEquals(50, clip.y(), 1e-9, "le bord haut vient du panneau intérieur");
        assertEquals(100, clip.bottom(), 1e-9, "le bord bas vient de l'extérieur");
    }

    // ------------------------------------------------------------ le diagnostic

    /**
     * Un conteneur défilant qui <b>s'ajuste à ses enfants</b> ne défilera jamais : il
     * grandit avec son contenu, donc rien ne dépasse. La case est cochée, la molette ne
     * fait rien, et sans ce mot l'auteur en conclurait que le défilement est cassé.
     */
    @Test
    void unPanneauQuiSajusteEtQuiDefileEstSignale() {
        Blueprint bp = with(new Screen("menu", false, List.of(
                ScreenElement.of("cadre", ElementKind.PANEL, 0, 0, 200, 100)
                        .resized(Extent.of(200), Extent.hug())
                        .withLayout(LayoutSpec.column(2).withScroll(true)),
                ScreenElement.of("b", ElementKind.BUTTON, 0, 0, 100, 20).withParent("cadre"))));

        assertTrue(GraphValidator.validate(bp, LOADED.nodes()).diagnostics().stream()
                        .anyMatch(d -> d.code() == DiagnosticCode.SCREEN_SCROLL_HUGS),
                "un contrôle qui ne peut rien faire doit le dire");
    }

    @Test
    void unPanneauDefilantDeHauteurFixeNestPasSignale() {
        Blueprint bp = with(panneau(true));
        assertFalse(GraphValidator.validate(bp, LOADED.nodes()).diagnostics().stream()
                        .anyMatch(d -> d.code() == DiagnosticCode.SCREEN_SCROLL_HUGS));
    }

    // ------------------------------------------------------------ aller-retour

    /**
     * <b>Le troisième test qui compte.</b> Un panneau défilant en disposition
     * <b>absolue</b> traverse la sauvegarde et l'export.
     *
     * <p>La disposition n'était écrite que si elle <i>rangeait</i> les enfants. Un panneau
     * qui ne range rien mais qui défile serait donc revenu figé après un aller-retour —
     * sans erreur, sans avertissement, et sans que rien ne rappelle qu'on avait coché la
     * case.
     */
    @Test
    void unPanneauDefilantEnAbsoluTraverseLaSauvegardeEtLExport() {
        Screen screen = new Screen("menu", false, List.of(
                ScreenElement.of("cadre", ElementKind.PANEL, 0, 0, 200, 100)
                        .withLayout(LayoutSpec.ABSOLUTE.withScroll(true)),
                ScreenElement.of("long", ElementKind.LABEL, 0, 0, 100, 400)
                        .withParent("cadre")));
        Blueprint bp = with(screen);

        Blueprint relu = GraphNbt.decode(GraphNbt.encode(bp),
                id -> fr.blueprint.api.pin.PinTypes.builtin().stream()
                        .filter(type -> type.id().equals(id)).findFirst().orElse(null));
        assertTrue(relu.screen("menu").element("cadre").scrolls(),
                "le défilement a disparu de l'enregistrement");

        var generated = ScriptGenerator.generate(bp, LOADED.nodes());
        var parsed = ScriptParser.parse(generated.text(), LOADED);
        assertTrue(parsed.success(), () -> "parse échoué : " + parsed.error());
        assertTrue(parsed.blueprint().screen("menu").element("cadre").scrolls(),
                "le défilement a disparu de l'export texte");
    }

    /** Un `.bp` écrit avant le défilement se relit sans changement : le mot est facultatif. */
    @Test
    void uneDispositionSansLeMotSeRelitEncore() {
        String script = """
                blueprint test:ancien {
                  screen "menu" {
                    panel "cadre" @at(top_left, 0, 0) @size(200, 100) @layout(column, gap: 2)
                  }
                }
                """;
        var parsed = ScriptParser.parse(script, LOADED);

        assertTrue(parsed.success(), () -> "parse échoué : " + parsed.error());
        assertFalse(parsed.blueprint().screen("menu").element("cadre").scrolls());
    }

    /** Cocher « défilant » sur un BOUTON ne fait rien : seul un conteneur défile. */
    @Test
    void seulUnConteneurDefile() {
        ScreenElement bouton = ScreenElement.of("b", ElementKind.BUTTON, 0, 0, 60, 20)
                .withLayout(LayoutSpec.ABSOLUTE.withScroll(true));
        assertFalse(bouton.scrolls());
    }
}
