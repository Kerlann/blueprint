package fr.blueprint.client.editor.screen;

import fr.blueprint.client.editor.history.UndoStack;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.NodeTypeLookup;
import fr.blueprint.core.graph.ScreenOps;
import fr.blueprint.core.graph.screen.ElementKind;
import fr.blueprint.core.graph.screen.Extent;
import fr.blueprint.core.graph.screen.LayoutSpec;
import fr.blueprint.core.graph.screen.Screen;
import fr.blueprint.core.graph.screen.ScreenElement;
import fr.blueprint.core.graph.screen.ScreenLayout;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La passe de disposition du concepteur, <b>mémorisée</b>.
 *
 * <p>Le contrôleur appelait {@code rects()} en vingt-deux endroits et le widget quatre
 * fois de plus par image — le dessin, le cerne de dépassement, la sélection, l'infobulle
 * survolée, les repères de la barre du bas, la révélation de la sélection — et chacun
 * relançait la passe entière sur tout l'écran. L'écran de jeu garde son résultat depuis la
 * 10.7, avec le bon argument : « redessiner est inévitable à chaque image, recalculer ne
 * l'est pas ». Le concepteur n'en a jamais rien eu, et chaque story de l'épic y a ajouté
 * un appel.
 *
 * <p>Un cache n'est bon que si son invalidation l'est. Ces tests portent donc surtout sur
 * ce qui doit le <b>vider</b>.
 */
class DesignerLayoutCacheTest {

    private static final NodeTypeLookup LOOKUP = typeId -> null;

    private Blueprint bp;
    private ScreenCanvasController controller;

    @BeforeEach
    void setUp() {
        bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "cache"));
        new ScreenOps.AddScreen(Screen.empty("menu")).apply(bp, LOOKUP);
        controller = new ScreenCanvasController(bp, LOOKUP, new UndoStack(), "menu");
        controller.setViewport(ScreenCanvasController.Viewport.SMALL);
        put(ScreenElement.of("cadre", ElementKind.PANEL, 0, 0, 200, 100)
                .withLayout(LayoutSpec.column(0).withScroll(true)));
        for (int i = 0; i < 6; i++) {
            put(ScreenElement.of("b" + i, ElementKind.BUTTON, 0, 0, 100, 20)
                    .withParent("cadre"));
        }
    }

    private void put(ScreenElement element) {
        assertTrue(new ScreenOps.AddElement("menu", element).apply(bp, LOOKUP).applied(),
                () -> "mise en place refusée : " + element.name());
    }

    @Test
    void deuxAppelsSansChangementRendentLaMemeTable() {
        assertSame(controller.rects(), controller.rects(),
                "sans rien changer, la passe ne doit pas être refaite");
    }

    /**
     * <b>Le test qui compte.</b> Une modification venue d'AILLEURS vide le cache.
     *
     * <p>C'est le cas qu'un drapeau « modifié » posé à la main aurait raté : {@code Ctrl+Z}
     * est joué par l'onglet Graphe, qui détient la pile d'annulation et modifie le même
     * blueprint sans que ce contrôleur voie rien passer. Le cache aurait alors montré une
     * mise en page annulée — et l'auteur aurait cliqué sur des éléments qui ne sont plus là.
     *
     * <p>L'invalidation ne repose donc sur aucun signal : {@link Screen} est immuable et
     * toute édition en produit un nouveau, si bien que comparer l'<i>identité</i> de
     * l'instance suffit à savoir si le contenu a changé.
     */
    @Test
    void uneModificationVenueDAilleursVideLeCache() {
        var avant = controller.rects();

        // Directement sur le blueprint, comme le fait l'annulation de l'autre onglet.
        new ScreenOps.SetElement("menu",
                bp.screen("menu").element("b0").movedTo(0, 500)).apply(bp, LOOKUP);

        var apres = controller.rects();
        assertNotSame(avant, apres, "le cache n'a pas vu passer la modification");
        assertNotSame(avant.get("b0"), apres.get("b0"));
    }

    /** Chaque geste du concepteur laisse une table à jour. */
    @Test
    void chaqueGesteLaisseUneTableAJour() {
        record Geste(String nom, Consumer<ScreenCanvasController> action) { }

        for (Geste geste : List.of(
                new Geste("déplacer", c -> {
                    c.press(10, 10, false);
                    c.drag(60, 40);
                    c.release();
                }),
                new Geste("poser", c -> c.addElement(ElementKind.LABEL, 40, 40)),
                new Geste("supprimer", c -> {
                    c.selection().selectAll(List.of("b5"), false);
                    c.deleteSelection();
                }),
                new Geste("renommer", c -> c.rename("b4", "renomme")),
                new Geste("ordonner", c -> {
                    c.selection().selectAll(List.of("b3"), false);
                    c.reorderSelection(-1);
                }),
                new Geste("redimensionner", c -> c.setElement(
                        bp.screen("menu").element("cadre")
                                .resized(Extent.of(150), Extent.of(80)))))) {
            var avant = controller.rects();
            geste.action().accept(controller);
            assertNotSame(avant, controller.rects(),
                    () -> "après « " + geste.nom() + " », la table gardée est périmée");
        }
    }

    @Test
    void changerDeFenetreSimuleeVideLeCache() {
        var avant = controller.rects();
        controller.setViewport(ScreenCanvasController.Viewport.HUGE);

        assertNotSame(avant, controller.rects());
        assertEquals(960, controller.viewportWidth(), 1e-9);
    }

    @Test
    void fairDefilerVideLeCache() {
        var avant = controller.rects();
        assertTrue(controller.scrollBy("cadre", 20));

        var apres = controller.rects();
        assertNotSame(avant, apres, "les enfants ont bougé : la table gardée est périmée");
        assertEquals(-20, apres.get("b0").y(), 1e-9);
    }

    @Test
    void changerDEcranVideLeCache() {
        new ScreenOps.AddScreen(Screen.empty("autre")).apply(bp, LOOKUP);
        var avant = controller.rects();
        controller.setScreenName("autre");

        assertNotSame(avant, controller.rects());
        assertTrue(controller.rects().isEmpty(), "l'autre écran est vide");
    }

    /**
     * Un écran disparu ne fait pas ressortir la dernière table connue.
     *
     * <p>Ce serait le pire des deux mondes : des rectangles pour des éléments qui
     * n'existent plus, donc un clic qui atteint ce qui n'est plus là.
     */
    @Test
    void unEcranSupprimeNeLaissePasSaTableDerriereLui() {
        assertTrue(controller.rects().containsKey("cadre"));
        new ScreenOps.RemoveScreen("menu").apply(bp, LOOKUP);

        assertTrue(controller.rects().isEmpty());
    }

    /**
     * Le banc du <b>concepteur</b>, qui manquait.
     *
     * <p>{@code ScreenRenderBenchTest} mesure UNE passe par image : c'est ce que fait le
     * jeu, qui la garde en plus. Il ne pouvait donc pas voir que le concepteur en lançait
     * huit. Un banc qui mesure autre chose que le code qui tourne ne garantit rien — c'est
     * la leçon des deux bancs de la 10.10, restés sur l'ancien chemin de résolution.
     *
     * <h2>Un rapport, et non une durée</h2>
     * <p>Ce banc a d'abord porté un budget absolu — 150 µs par image, entre les ≈ 69 µs
     * mesurés avec la mémorisation et les ≈ 229 µs sans. Il <b>rougissait sur la machine
     * d'intégration</b> sans qu'aucun code n'ait changé : une machine partagée met plus de
     * 150 µs à faire ce qui en prend 69 ici, et le test mesurait donc sa charge.
     *
     * <p>C'est la même maladie que {@code CompilerPerfTest} et
     * {@code EventDispatchPerfTest}, et le même remède : mesurer la <b>propriété</b> qu'on
     * veut garder plutôt qu'une durée. Ici, cette propriété est exactement ce que la 10.14
     * a corrigé — une image du concepteur doit coûter <b>nettement moins</b> que la même
     * image dont chaque appel recalculerait. Les deux mesures subissent la même machine au
     * même moment, si bien que leur rapport ne dépend plus d'elle.
     *
     * <p>Ce qu'il attrape n'est donc pas une lenteur de quelques pour cent : c'est le
     * retour du recalcul à chaque appel, qui rendrait le rapport égal à un.
     *
     * <h2>Les deux mesures ne doivent RIEN partager</h2>
     * <p>Première version de ce rapport : une image complète mémorisée contre une image
     * complète recalculée. Elle a rougi sur l'intégration continue à <b>0,51</b> contre un
     * seuil de 0,50 — un pour cent de marge, que je n'avais pas mesurée.
     *
     * <p>La cause est instructive : les deux images partageaient quatre appels
     * (survol, repères, révélation, poignées) qui passent <b>tous les deux</b> par le
     * cache. Ce travail commun, identique de part et d'autre, diluait l'écart et posait
     * un plancher au rapport. Un rapport ne vaut que si son numérateur et son
     * dénominateur ne diffèrent <b>que</b> par ce qu'on mesure.
     *
     * <p>Ici, donc : mille {@code rects()} — une passe puis neuf cent quatre-vingt-dix-neuf
     * lectures — contre mille passes de disposition. Rien de commun, et un rapport attendu
     * de l'ordre du millième.
     */
    @Test
    void uneImageDuConcepteurCouteBienMoinsQuUneImageRecalculee() {
        for (int i = 6; i < 64; i++) {
            put(ScreenElement.of("e" + i, ElementKind.LABEL, 0, 0, 80, 9).withParent("cadre"));
        }
        controller.selection().selectAll(List.of("b1"), false);

        Screen screen = bp.screen("menu");
        Runnable memoised = controller::rects;
        Runnable recomputed = () -> ScreenLayout.solve(screen, 320, 180);
        for (int i = 0; i < 2_000; i++) {
            memoised.run();
            recomputed.run();
        }

        double cached = bestNanos(memoised);
        double solved = bestNanos(recomputed);
        double ratio = cached / solved;

        // Seuil à 0,25, soit quatre fois le coût mémorisé toléré : mesuré autour de
        // 0,005 ici, il reste deux ordres de grandeur de marge — et il vaut UN si
        // quelqu'un retire la mémorisation.
        assertTrue(ratio < 0.25, () -> String.format(java.util.Locale.ROOT,
                "une lecture mémorisée coûte %.0f ns contre %.0f ns recalculée "
                        + "(rapport %.3f) — la mémorisation ne sert plus à rien",
                cached, solved, ratio));
    }

    /** Meilleur de cinq séries de mille appels, en nanosecondes par appel. */
    private static double bestNanos(Runnable call) {
        double best = Double.MAX_VALUE;
        for (int round = 0; round < 5; round++) {
            long start = System.nanoTime();
            for (int i = 0; i < 1_000; i++) {
                call.run();
            }
            best = Math.min(best, (System.nanoTime() - start) / 1_000.0);
        }
        return best;
    }


}
