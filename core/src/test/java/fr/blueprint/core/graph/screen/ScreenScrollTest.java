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

    // ------------------------------------------------------- l'axe horizontal

    /** Un panneau large : six boutons de 100 en ligne dans un cadre de 200. */
    private static Screen large(LayoutSpec.Scroll axis) {
        List<ScreenElement> elements = new ArrayList<>();
        elements.add(ScreenElement.of("cadre", ElementKind.PANEL, 0, 0, 200, 100)
                .withLayout(LayoutSpec.row(0).withScroll(axis)));
        for (int i = 0; i < 6; i++) {
            elements.add(ScreenElement.of("b" + i, ElementKind.BUTTON, 0, 0, 100, 20)
                    .withParent("cadre"));
        }
        return new Screen("menu", false, elements);
    }

    @Test
    void leDecalageHorizontalPousseLesEnfantsVersLaGauche() {
        Screen screen = large(LayoutSpec.Scroll.HORIZONTAL);
        Map<String, ScreenLayout.Rect> placed = ScreenLayout.solve(screen, 320, 180,
                new ScreenLayout.Scrolls() {
                    @Override
                    public double of(String container) {
                        return 0;
                    }

                    @Override
                    public double xOf(String container) {
                        return 150;
                    }
                });

        assertEquals(-150, placed.get("b0").x(), 1e-9, "le premier est sorti par la gauche");
        assertEquals(0, placed.get("cadre").x(), 1e-9, "le cadre, lui, ne bouge pas");
        assertEquals(0, placed.get("b0").y(), 1e-9, "et rien n'a bougé verticalement");
    }

    /**
     * <b>Le test qui compte pour l'axe.</b> Un panneau ne défile que sur l'axe qu'on lui a
     * donné.
     *
     * <p>Sans ce contrôle, un décalage horizontal envoyé à un panneau vertical déplacerait
     * son contenu sur un axe où rien ne peut le ramener : il n'y aurait ni curseur, ni
     * molette, ni touche pour revenir, et la moitié du menu serait définitivement hors du
     * cadre.
     */
    @Test
    void chaqueAxeNeRepondQueSiOnLeLuiADonne() {
        for (var axis : LayoutSpec.Scroll.values()) {
            Screen screen = large(axis);
            ScreenElement cadre = screen.element("cadre");
            var placed = ScreenLayout.solve(screen, 320, 180);

            assertEquals(axis.horizontal(),
                    ScreenLayout.scrollRangeX(screen, cadre, placed, 0) > 0,
                    () -> "plage horizontale incohérente pour " + axis);
            // Six boutons de 20 en ligne tiennent en hauteur : rien ne dépasse
            // verticalement, donc aucun axe n'a de plage verticale ici.
            assertEquals(0, ScreenLayout.scrollRange(screen, cadre, placed, 0), 1e-9,
                    () -> "plage verticale inattendue pour " + axis);
        }
    }

    @Test
    void laPlageHorizontaleEstCeQuiDepasseADroite() {
        Screen screen = large(LayoutSpec.Scroll.BOTH);
        assertEquals(400, ScreenLayout.scrollRangeX(screen, screen.element("cadre"),
                ScreenLayout.solve(screen, 320, 180), 0), 1e-9,
                "six boutons de 100 dans un cadre de 200 : il en dépasse 400");
    }

    /** Maj vise l'horizontal, mais un panneau à UN seul axe répond sans qu'on le sache. */
    @Test
    void laMoletteChoisitLAxeSansQuOnLuiDise() {
        var vertical = large(LayoutSpec.Scroll.VERTICAL).element("cadre");
        var horizontal = large(LayoutSpec.Scroll.HORIZONTAL).element("cadre");
        var both = large(LayoutSpec.Scroll.BOTH).element("cadre");

        assertTrue(ScreenLayout.scrollVertical(vertical, false));
        assertTrue(ScreenLayout.scrollVertical(vertical, true),
                "Maj sur un panneau qui ne défile que verticalement : il répond quand même, "
                        + "exiger la bonne touche serait une devinette");
        assertFalse(ScreenLayout.scrollVertical(horizontal, false),
                "sans Maj sur un panneau qui ne défile qu'horizontalement : il répond aussi");
        assertTrue(ScreenLayout.scrollVertical(both, false));
        assertFalse(ScreenLayout.scrollVertical(both, true),
                "sur les deux axes, c'est Maj qui tranche");
    }

    /**
     * Les deux curseurs se <b>gênent</b> : chacun s'arrête avant le coin que l'autre
     * occupe. Les calculer séparément les ferait se recouvrir, et celui du dessous
     * deviendrait inattrapable dans son dernier centimètre — là où l'on va chercher la
     * fin d'une page.
     */
    @Test
    void lesDeuxCurseursSeLaissentLeCoin() {
        var seul = ScreenLayout.scrollBarsOf(CADRE, 100, 0, 0, 0, 0);
        var deux = ScreenLayout.scrollBarsOf(CADRE, 100, 100, 0, 0, 0);

        assertNotNull(seul.vertical());
        assertNotNull(deux.vertical());
        assertNotNull(deux.horizontal());
        assertTrue(deux.vertical().track().height() < seul.vertical().track().height(),
                "avec une barre en bas, celle de droite doit s'arrêter avant");
        assertEquals(ScreenLayout.SCROLLBAR_WIDTH,
                seul.vertical().track().height() - deux.vertical().track().height(), 1e-9);
    }

    @Test
    void leCurseurHorizontalEstEnBasEtSeLitDeGaucheADroite() {
        var bars = ScreenLayout.scrollBarsOf(CADRE, 0, 100, 0, 100, 0);
        var bar = bars.horizontal();

        assertNotNull(bar);
        assertNull(bars.vertical(), "rien ne dépasse verticalement : pas de barre à droite");
        assertFalse(bar.vertical());
        assertEquals(CADRE.bottom() - ScreenLayout.SCROLLBAR_WIDTH, bar.track().y(), 1e-9);
        assertEquals(bar.track().right(), bar.thumb().right(), 1e-9,
                "tout lu : le curseur est collé à droite");
    }

    /** L'aller-retour du curseur horizontal est exact lui aussi. */
    @Test
    void placerLeCurseurHorizontalEtLeLireSontDesInversesExacts() {
        double range = 400;
        for (double offset : new double[]{0, 3, 137.5, 200, 399, 400}) {
            var bar = ScreenLayout.scrollBarsOf(CADRE, 0, range, 0, offset, 2).horizontal();
            assertNotNull(bar);
            assertEquals(offset, bar.offsetFor(bar.thumbStart(), range), 1e-6,
                    () -> "aller-retour faux pour un décalage de " + offset);
        }
    }

    @Test
    void ramenerHorizontalementSuitLeMemeSensQueVerticalement() {
        ScreenLayout.Rect clip = new ScreenLayout.Rect(0, 0, 100, 100);

        assertEquals(0, ScreenLayout.revealDeltaX(clip,
                new ScreenLayout.Rect(10, 0, 50, 10)), 1e-9);
        assertEquals(-15, ScreenLayout.revealDeltaX(clip,
                new ScreenLayout.Rect(-15, 0, 50, 10)), 1e-9, "sorti par la gauche");
        assertEquals(20, ScreenLayout.revealDeltaX(clip,
                new ScreenLayout.Rect(110, 0, 10, 10)), 1e-9, "sorti par la droite");
    }

    /**
     * L'axe traverse la sauvegarde et l'export — et un fichier écrit <b>avant</b> l'axe,
     * qui portait un simple booléen, se relit comme un défilement vertical.
     */
    @Test
    void lAxeTraverseLaSauvegardeEtLExport() {
        Blueprint bp = with(large(LayoutSpec.Scroll.BOTH));

        Blueprint relu = GraphNbt.decode(GraphNbt.encode(bp),
                id -> fr.blueprint.api.pin.PinTypes.builtin().stream()
                        .filter(type -> type.id().equals(id)).findFirst().orElse(null));
        assertEquals(LayoutSpec.Scroll.BOTH,
                relu.screen("menu").element("cadre").layout().scroll());

        var generated = ScriptGenerator.generate(bp, LOADED.nodes());
        var parsed = ScriptParser.parse(generated.text(), LOADED);
        assertTrue(parsed.success(), () -> "parse échoué : " + parsed.error());
        assertEquals(LayoutSpec.Scroll.BOTH,
                parsed.blueprint().screen("menu").element("cadre").layout().scroll());
    }

    @Test
    void unAncienBooleenSeRelitCommeUnDefilementVertical() {
        String script = """
                blueprint test:ancien {
                  screen "menu" {
                    panel "cadre" @at(top_left, 0, 0) @size(200, 100) \
                @layout(column, gap: 2, scroll: true)
                  }
                }
                """;
        var parsed = ScriptParser.parse(script, LOADED);

        assertTrue(parsed.success(), () -> "parse échoué : " + parsed.error());
        assertEquals(LayoutSpec.Scroll.VERTICAL,
                parsed.blueprint().screen("menu").element("cadre").layout().scroll(),
                "c'est ce que « true » voulait dire avant que l'axe existe");
    }

    @Test
    void unPanneauLargeQuiSajusteEnLargeurEstSignale() {
        Blueprint bp = with(new Screen("menu", false, List.of(
                ScreenElement.of("cadre", ElementKind.PANEL, 0, 0, 200, 100)
                        .resized(Extent.hug(), Extent.of(100))
                        .withLayout(LayoutSpec.row(2).withScroll(LayoutSpec.Scroll.HORIZONTAL)),
                ScreenElement.of("b", ElementKind.BUTTON, 0, 0, 100, 20).withParent("cadre"))));

        assertTrue(GraphValidator.validate(bp, LOADED.nodes()).diagnostics().stream()
                        .anyMatch(d -> d.code() == DiagnosticCode.SCREEN_SCROLL_HUGS),
                "il grandit en largeur avec son contenu : il ne défilera jamais");
    }

    // ------------------------------------------- ce que le graphe peut repositionner

    /**
     * <b>Le test qui compte pour les deux nœuds.</b> « Remets en haut » et « remets à
     * gauche » ne se remplacent pas l'un l'autre.
     *
     * <p>Les modifications d'un même tick sont regroupées par leur clé, et deux
     * modifications de même clé se remplacent — c'est ce qui évite d'envoyer deux fois
     * l'or dans le même tick. Un axe caché dans un champ plutôt que dans le TYPE aurait
     * donc fait disparaître l'une des deux en silence, et le panneau serait resté décalé
     * sur un axe sans que rien ne l'explique.
     */
    @Test
    void lesDeuxAxesNeSeMarchentPasDessusDansUnMemeTick() {
        ScreenUpdate haut = ScreenUpdate.scroll("menu", "cadre", 0);
        ScreenUpdate gauche = ScreenUpdate.scrollX("menu", "cadre", 0);

        assertEquals(ScreenUpdate.Kind.SCROLL, haut.kind());
        assertEquals(ScreenUpdate.Kind.SCROLL_X, gauche.kind());
        assertFalse(haut.key().equals(gauche.key()),
                "même écran, même élément — mais deux axes : deux clés, ou l'une des deux "
                        + "modifications disparaîtrait sans un mot");
    }

    /** Un décalage négatif ou non fini est ramené à zéro : le haut d'une page existe. */
    @Test
    void unDecalageImpossibleRamèneAuDebut() {
        assertEquals(0, ScreenUpdate.scroll("menu", "cadre", -50).number(), 1e-9);
        assertEquals(0, ScreenUpdate.scrollX("menu", "cadre", Double.NaN).number(), 1e-9);
        assertEquals(0, ScreenUpdate.scrollX("menu", "cadre",
                Double.NEGATIVE_INFINITY).number(), 1e-9);
    }

    // ------------------------------------------------- le curseur de défilement

    private static final ScreenLayout.Rect CADRE = new ScreenLayout.Rect(0, 0, 200, 100);

    @Test
    void sansRienAParcourirIlNyAPasDeCurseur() {
        assertNull(ScreenLayout.scrollBarsOf(CADRE, 0, 0, 0, 0, 2).vertical(),
                "un curseur qui occupe toute sa glissière dit qu'il y a autre chose à "
                        + "voir alors qu'il n'y a rien, et l'on cherche");
    }

    @Test
    void leCurseurDitLaProportionVisible() {
        // 100 visibles, 100 qui dépassent : la moitié se voit, donc la moitié de la
        // glissière. C'est ce que le joueur lit sans compter.
        var bar = ScreenLayout.scrollBarsOf(CADRE, 100, 0, 0, 0, 0).vertical();
        assertNotNull(bar);
        assertEquals(50, bar.thumb().height(), 1e-9);
        assertEquals(100, bar.track().height(), 1e-9);
    }

    @Test
    void unCurseurMinusculeGardeUneTailleAttrapable() {
        var bar = ScreenLayout.scrollBarsOf(CADRE, 100000, 0, 0, 0, 0).vertical();
        assertNotNull(bar);
        assertEquals(ScreenLayout.MIN_THUMB, bar.thumb().height(), 1e-9,
                "en dessous, il ne se voit plus et ne s'attrape plus");
    }

    @Test
    void leCurseurVaDUnBoutALAutre() {
        var haut = ScreenLayout.scrollBarsOf(CADRE, 100, 0, 0, 0, 0).vertical();
        var bas = ScreenLayout.scrollBarsOf(CADRE, 100, 0, 100, 0, 0).vertical();
        assertNotNull(haut);
        assertNotNull(bas);

        assertEquals(haut.track().y(), haut.thumb().y(), 1e-9, "en haut quand on n'a rien lu");
        assertEquals(haut.track().bottom(), bas.thumb().bottom(), 1e-9,
                "et collé en bas quand on a tout lu");
    }

    /**
     * <b>Le test qui compte pour la barre.</b> Le placement du curseur et sa lecture sont
     * des <b>inverses exacts</b>.
     *
     * <p>Le dessin place le curseur d'après la position de lecture ; le glisser fait
     * l'inverse. Deux calculs séparés donneraient un curseur qui <b>saute</b> au moment où
     * on l'attrape — le défaut classique d'une barre de défilement, et celui qu'on met le
     * plus longtemps à croire réel, parce qu'il ne se produit que sous le doigt.
     */
    @Test
    void placerLeCurseurEtLeLireSontDesInversesExacts() {
        double range = 240;
        for (double offset : new double[]{0, 1, 37.5, 120, 239, 240}) {
            var bar = ScreenLayout.scrollBarsOf(CADRE, range, 0, offset, 0, 2).vertical();
            assertNotNull(bar);
            assertEquals(offset, bar.offsetFor(bar.thumb().y(), range), 1e-6,
                    () -> "aller-retour faux pour un décalage de " + offset);
        }
    }

    @Test
    void leCurseurTireAuDelaDesBoutsSyArrete() {
        var bar = ScreenLayout.scrollBarsOf(CADRE, 100, 0, 0, 0, 0).vertical();
        assertNotNull(bar);

        assertEquals(0, bar.offsetFor(-500, 100), 1e-9);
        assertEquals(100, bar.offsetFor(5000, 100), 1e-9);
    }

    // ---------------------------------------------------- ramener sous les yeux

    @Test
    void ramenerNeFaitRienSurCeQuiEstDejaVisible() {
        ScreenLayout.Rect clip = new ScreenLayout.Rect(0, 0, 100, 100);
        assertEquals(0, ScreenLayout.revealDelta(clip, new ScreenLayout.Rect(0, 10, 50, 10)),
                1e-9, "faire défiler pour rien ferait sauter la page sous les yeux");
    }

    @Test
    void ramenerRemonteOuDescendSelonLeBordFranchi() {
        ScreenLayout.Rect clip = new ScreenLayout.Rect(0, 0, 100, 100);

        assertEquals(-15, ScreenLayout.revealDelta(clip,
                new ScreenLayout.Rect(0, -15, 50, 10)), 1e-9, "sorti par le haut");
        assertEquals(20, ScreenLayout.revealDelta(clip,
                new ScreenLayout.Rect(0, 110, 50, 10)), 1e-9, "sorti par le bas");
    }

    @Test
    void leResponsableDuDecalageEstLAncetreDefilantLePlusProche() {
        Screen screen = new Screen("menu", false, List.of(
                ScreenElement.of("dehors", ElementKind.PANEL, 0, 0, 200, 150)
                        .withLayout(LayoutSpec.ABSOLUTE.withScroll(true)),
                ScreenElement.of("dedans", ElementKind.PANEL, 0, 0, 100, 60)
                        .withParent("dehors").withLayout(LayoutSpec.ABSOLUTE.withScroll(true)),
                ScreenElement.of("feuille", ElementKind.LABEL, 0, 0, 50, 10)
                        .withParent("dedans"),
                ScreenElement.of("libre", ElementKind.LABEL, 0, 0, 50, 10)));

        assertEquals("dedans", ScreenLayout.scrollerOf(screen, screen.element("feuille")));
        assertEquals("dehors", ScreenLayout.scrollerOf(screen, screen.element("dedans")));
        assertNull(ScreenLayout.scrollerOf(screen, screen.element("libre")),
                "hors de tout panneau défilant, personne n'a de décalage à bouger");
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
