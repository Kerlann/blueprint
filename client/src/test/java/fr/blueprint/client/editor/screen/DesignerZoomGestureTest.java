package fr.blueprint.client.editor.screen;

import fr.blueprint.client.editor.history.UndoStack;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.NodeTypeLookup;
import fr.blueprint.core.graph.ScreenOps;
import fr.blueprint.core.graph.screen.Anchor;
import fr.blueprint.core.graph.screen.ElementKind;
import fr.blueprint.core.graph.screen.Extent;
import fr.blueprint.core.graph.screen.Screen;
import fr.blueprint.core.graph.screen.ScreenElement;
import fr.blueprint.core.graph.screen.ScreenLayout;
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
 * Ce que le zoom change aux <b>gestes</b>, et ce que le grand canevas change au
 * <b>dépôt</b>.
 *
 * <p>Deux pièges, tous deux invisibles à la lecture du code. Une tolérance de souris
 * exprimée en unités d'interface cesse d'avoir un sens dès qu'on zoome : elle vaut moins
 * d'un pixel au plus large, et vingt au plus serré. Et un élément posé sur un canevas de
 * 1920×1080 sans ancre est écrit « à 1600 du bord gauche » : chez un joueur en 480×270,
 * il est hors écran, et rien ne le dit avant l'ouverture du menu.
 */
class DesignerZoomGestureTest {

    private static final NodeTypeLookup LOOKUP = typeId -> null;

    private Blueprint bp;
    private ScreenCanvasController controller;

    @BeforeEach
    void setUp() {
        bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "zoom"));
        new ScreenOps.AddScreen(Screen.empty("menu")).apply(bp, LOOKUP);
        controller = new ScreenCanvasController(bp, LOOKUP, new UndoStack(), "menu");
    }

    private ScreenElement element(String name) {
        return bp.screen("menu").element(name);
    }

    // ------------------------------------------------------- tolérances de geste

    /**
     * <b>Le test qui compte.</b> Une poignée s'attrape dans le même nombre de PIXELS,
     * quel que soit le zoom.
     *
     * <p>Formulée en unités — ce qu'elle était — la tolérance vaut 0,6 pixel au zoom le
     * plus large : la poignée devient insaisissable, et l'auteur croit le redimensionnement
     * cassé. Au plus serré elle vaut vingt pixels et recouvre l'élément entier, si bien
     * qu'on ne peut plus déplacer ce qu'on voulait redimensionner. Le zoom aurait été livré
     * inutilisable aux deux bouts sans que rien ne le signale.
     */
    @Test
    void unePoigneeSAttrapeDansLeMemeNombreDePixelsAToutZoom() {
        // Un élément large : à 0,25 un bouton de 60×20 ne mesure que 15×5 pixels, et ses
        // propres poignées se recouvrent. Ce test parle de la tolérance, pas de ce cas-là.
        controller.setViewport(ScreenCanvasController.Viewport.FULL);
        new ScreenOps.AddElement("menu",
                ScreenElement.of("a", ElementKind.PANEL, 100, 100, 400, 200)).apply(bp, LOOKUP);
        controller.selection().selectAll(List.of("a"), false);

        for (double zoom : new double[]{0.25, 1, 4, 8}) {
            controller.setUnitsPerPixel(1 / zoom);
            double pixel = 1 / zoom;   // un pixel, exprimé en unités

            // Deux pixels du coin haut-gauche : c'est encore la poignée, à tout zoom.
            assertNotNull(controller.handleAt(100 + 2 * pixel, 100 + 2 * pixel),
                    () -> "poignée manquée à deux pixels, zoom " + zoom);
            // Cinq pixels : c'est déjà le corps de l'élément.
            assertNull(controller.handleAt(100 + 5 * pixel, 100 + 5 * pixel),
                    () -> "poignée attrapée à cinq pixels, zoom " + zoom);
        }
    }

    /** L'accroche des guides suit la même règle, et pour la même raison. */
    @Test
    void lAccrocheDesGuidesSeMesureAussiEnPixels() {
        controller.setUnitsPerPixel(4);   // zoom 0,25
        assertEquals(AlignmentGuides.SNAP_PIXELS * 4, controller.snapTolerance(), 1e-9);
        controller.setUnitsPerPixel(0.125);   // zoom 8
        assertEquals(AlignmentGuides.SNAP_PIXELS * 0.125, controller.snapTolerance(), 1e-9);
    }

    // ------------------------------------------------------------ panneaux repliés

    /**
     * <b>Le second test qui compte.</b> La largeur cliquable d'un panneau replié est sa
     * largeur dessinée.
     *
     * <p>Ce projet s'est déjà fait prendre deux fois : un panneau qui rétrécit à l'écran
     * sans que sa zone cliquable suive laisse une bande invisible qui avale les gestes du
     * canevas, et rien ne l'explique — le bord ne répond simplement plus.
     */
    @Test
    void unPanneauReplieNAvalePlusLesClicsDuCanevas() {
        DesignerPanels open = DesignerPanels.OPEN;
        DesignerPanels shut = open.withPalette(false).withProperties(false);
        int width = 640;

        assertTrue(open.inPalette(50), "déplié, 50 est dans la palette");
        assertFalse(shut.inPalette(50), "replié, 50 revient au canevas");
        assertTrue(shut.inPalette(3), "mais sa bande, elle, répond encore");

        assertTrue(open.inProperties(width - 50, width));
        assertFalse(shut.inProperties(width - 50, width));

        assertEquals(shut.paletteWidth(), DesignerPanels.COLLAPSED);
        assertEquals(width - DesignerPanels.COLLAPSED * 2, shut.canvasWidth(width),
                "tout ce qui n'est plus peint revient au canevas");
    }

    @Test
    void laPoigneeDeRepliResteAtteignableDansLesDeuxEtats() {
        DesignerPanels open = DesignerPanels.OPEN;
        assertTrue(open.onPaletteToggle(DesignerPanels.PALETTE_WIDTH - 1));
        assertFalse(open.onPaletteToggle(10), "déplié, le milieu de la palette n'est pas la poignée");

        DesignerPanels shut = open.withPalette(false);
        assertTrue(shut.onPaletteToggle(0), "replié, toute la bande rouvre");
        assertTrue(shut.onPaletteToggle(DesignerPanels.COLLAPSED - 1));
        assertFalse(shut.onPaletteToggle(DesignerPanels.COLLAPSED));
    }

    @Test
    void laBasculeDUnCoupReplieOuRouvreLesDeux() {
        assertEquals(new DesignerPanels(false, false), DesignerPanels.OPEN.toggledBoth());
        assertEquals(new DesignerPanels(true, true),
                new DesignerPanels(false, false).toggledBoth());
        assertEquals(new DesignerPanels(false, false),
                new DesignerPanels(true, false).toggledBoth(),
                "un seul ouvert : la bascule replie tout — c'est de la place qu'on demande, "
                        + "et inverser chacun de son côté rouvrirait ce qu'on venait de fermer");
    }

    // -------------------------------------------------------------- dépôt d'élément

    /** Le point donné est le CENTRE du dépôt : on pose « là », pas « à côté de là ». */
    @Test
    void unElementSePoseCentreSurLePointDemande() {
        controller.setViewport(ScreenCanvasController.Viewport.FULL);
        String name = controller.addElement(ElementKind.BUTTON, 600, 400);

        assertNotNull(name);
        ScreenLayout.Rect rect = controller.rects().get(name);
        assertNotNull(rect);
        assertEquals(600, rect.x() + rect.width() / 2, 1.5);
        assertEquals(400, rect.y() + rect.height() / 2, 1.5);
    }

    /**
     * <b>Le troisième test qui compte.</b> Un bouton posé en bas à droite d'un canevas de
     * 1920×1080 est <b>toujours en bas à droite, et dans l'écran</b>, une fois la fenêtre
     * ramenée à 320×180.
     *
     * <p>C'est le défaut signalé en jeu. Tout élément neuf naissait ancré en haut à gauche,
     * ce qui ne se remarquait pas tant qu'on dessinait petit. Sur un grand canevas, cela
     * écrit « à 1770 du bord gauche » — une position que la fenêtre d'un joueur ordinaire
     * ne contient pas. L'ancre automatique n'est donc pas un agrément : c'est la
     * contrepartie obligatoire d'un concepteur qui s'ouvre au large.
     */
    @Test
    void unBoutonPoseEnBasADroiteYResteQuelleQueSoitLaFenetre() {
        controller.setViewport(ScreenCanvasController.Viewport.FULL);
        String name = controller.addElement(ElementKind.BUTTON, 1800, 1000);
        assertNotNull(name);

        assertEquals(Anchor.BOTTOM_RIGHT, element(name).anchor(),
                "posé dans le tiers bas-droit, il doit s'y ancrer");

        Screen screen = bp.screen("menu");
        Double marginRight = null;
        Double marginBottom = null;
        for (int[] window : new int[][]{{320, 180}, {480, 270}, {960, 540}, {1920, 1080}}) {
            ScreenLayout.Rect rect =
                    ScreenLayout.solve(screen, window[0], window[1]).get(name);
            assertNotNull(rect);
            assertTrue(rect.x() >= 0 && rect.y() >= 0
                            && rect.right() <= window[0] && rect.bottom() <= window[1],
                    () -> "hors écran en " + window[0] + "×" + window[1] + " : " + rect);

            // La garantie d'une ancre : l'écart aux bords de RÉFÉRENCE ne bouge pas. Sans
            // elle, cet écart se comptait depuis le bord GAUCHE — 1770 unités, que la
            // fenêtre d'un joueur ordinaire ne contient tout simplement pas.
            if (marginRight == null) {
                marginRight = window[0] - rect.right();
                marginBottom = window[1] - rect.bottom();
            }
            assertEquals(marginRight, window[0] - rect.right(), 1e-9,
                    () -> "l'écart au bord droit a changé en " + window[0] + "×" + window[1]);
            assertEquals(marginBottom, window[1] - rect.bottom(), 1e-9,
                    () -> "l'écart au bord bas a changé en " + window[0] + "×" + window[1]);
        }
    }

    /**
     * Le clic résout à la taille <b>simulée</b>, et un pourcentage y tombe ailleurs qu'à
     * 320×180.
     *
     * <p>C'est la mesure du défaut qui vivait dans le concepteur : il peignait l'écran en
     * passant au peintre les dimensions de la fenêtre <i>garantie</i>, en dur, pendant que
     * le hit-test interrogeait la table du contrôleur, résolue à la taille choisie. Dès
     * qu'on quittait le plus petit préréglage, on cliquait à côté de ce qu'on voyait — et
     * le sélecteur de taille de fenêtre, dont c'est toute la raison d'être, ne montrait
     * rien de ce qu'il promettait.
     *
     * <p>Ce test ne voit pas le dessin ; il montre l'écart que le dessin ignorait. Le
     * dessin lit désormais {@code controller.rects()} — la même table, pas les mêmes
     * paramètres.
     */
    @Test
    void leClicResoutALaTailleSimuleeEtUnPourcentageYTombeAilleurs() {
        new ScreenOps.AddElement("menu", ScreenElement.of("a", ElementKind.PANEL, 0, 0, 1, 1)
                .resized(Extent.percent(0.5, 0, 0), Extent.percent(0.5, 0, 0)))
                .apply(bp, LOOKUP);

        controller.setViewport(ScreenCanvasController.Viewport.SMALL);
        ScreenLayout.Rect small = controller.rects().get("a");
        controller.setViewport(ScreenCanvasController.Viewport.HUGE);
        ScreenLayout.Rect huge = controller.rects().get("a");

        assertNotNull(small);
        assertNotNull(huge);
        assertEquals(160, small.width(), 1e-9);
        assertEquals(480, huge.width(), 1e-9,
                "la moitié de 960 : un peintre figé sur 320×180 aurait dessiné 160");
    }

    @Test
    void chaqueTiersDonneSonAncre() {
        ScreenLayout.Rect area = new ScreenLayout.Rect(0, 0, 900, 900);
        assertEquals(Anchor.TOP_LEFT,
                ScreenCanvasController.anchorFor(area, new ScreenLayout.Rect(0, 0, 100, 100)));
        assertEquals(Anchor.CENTER,
                ScreenCanvasController.anchorFor(area, new ScreenLayout.Rect(400, 400, 100, 100)));
        assertEquals(Anchor.BOTTOM_RIGHT,
                ScreenCanvasController.anchorFor(area, new ScreenLayout.Rect(800, 800, 100, 100)));
        assertEquals(Anchor.TOP_RIGHT,
                ScreenCanvasController.anchorFor(area, new ScreenLayout.Rect(800, 0, 100, 100)));
    }

    /**
     * Un conteneur naît en <b>pourcentage</b>, ce qui porte du texte naît en unités fixes.
     *
     * <p>Ce n'est pas un compromis mais la conséquence d'une police de taille fixe : la
     * police de Minecraft mesure 9 unités, toujours. Un bouton proportionnel au canevas
     * serait illisible en 320×180 — son texte ne rapetisse pas avec lui. Un panneau de
     * fond, lui, n'a que sa surface à offrir, et doit épouser la fenêtre.
     */
    @Test
    void unConteneurEpouseLaFenetreLaOuUnBoutonGardeSaTaille() {
        controller.setViewport(ScreenCanvasController.Viewport.FULL);

        String panel = controller.addElement(ElementKind.PANEL, 300, 300);
        assertNotNull(panel);
        assertEquals(Extent.Mode.PERCENT, element(panel).width().mode());
        assertEquals(Extent.Mode.PERCENT, element(panel).height().mode());

        String button = controller.addElement(ElementKind.BUTTON, 1500, 200);
        assertNotNull(button);
        assertEquals(Extent.Mode.FIXED, element(button).width().mode());
        assertEquals(60, element(button).width().value(), 1e-9);
        assertEquals(20, element(button).height().value(), 1e-9);
    }

    /**
     * Le concepteur s'ouvre au large. Le <b>modèle</b>, lui, garde 320×180 comme fenêtre
     * garantie : c'est elle que le validateur mesure, et confondre les deux ferait passer
     * pour valide un menu que personne ne peut afficher.
     */
    @Test
    void leDefautDuConcepteurNestPasCeluiDuModele() {
        assertEquals(ScreenCanvasController.Viewport.FULL,
                ScreenCanvasController.Viewport.DESIGN_DEFAULT);
        assertEquals(1920, ScreenCanvasController.Viewport.DESIGN_DEFAULT.width());
        assertEquals(Screen.SAFE_WIDTH, controller.viewportWidth(), 1e-9,
                "un contrôleur neuf reste sur la fenêtre garantie ; c'est le widget "
                        + "qui choisit d'ouvrir au large");
    }
}
