package fr.blueprint.client.screen;

import fr.blueprint.core.graph.screen.Anchor;
import fr.blueprint.core.graph.screen.ElementBinding;
import fr.blueprint.core.graph.screen.ElementKind;
import fr.blueprint.core.graph.screen.ElementStyle;
import fr.blueprint.core.graph.screen.Extent;
import fr.blueprint.core.graph.screen.LayoutSpec;
import fr.blueprint.core.graph.screen.Screen;
import fr.blueprint.core.graph.screen.ScreenElement;
import fr.blueprint.core.graph.screen.ScreenLayout;
import fr.blueprint.core.graph.screen.ScreenText;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Banc du HUD (story 10.9, AC6, NFR1).
 *
 * <p>Le coût d'un HUD est d'une <b>autre nature</b> que celui d'un menu. Un écran modal
 * est regardé quelques secondes ; un HUD est dessiné soixante fois par seconde pendant
 * toute la partie. Ce qui est négligeable dans l'un devient mesurable dans l'autre.
 *
 * <p>Ce qui est mesuré est la passe CPU par image : résolution des ancres, des
 * pourcentages et de la chaîne de visibilité, pour deux HUD de 32 éléments.
 */
class HudRenderBenchTest {

    private static final int PER_HUD = 32;
    private static final int WARMUP_FRAMES = 500;
    private static final int MEASURED_FRAMES = 2_000;
    /** Seuil large (machine CI inconnue), très en dessous des 16,6 ms d'une image. */
    private static final double BUDGET_NANOS_PER_FRAME = 400_000;

    /** Deux HUD réalistes : un bandeau ancré à droite, une barre ancrée en bas. */
    private static Screen hud(String name, Anchor anchor) {
        List<ScreenElement> elements = new ArrayList<>();
        elements.add(new ScreenElement("cadre_" + name, ElementKind.PANEL, null, anchor,
                -4, 4, Extent.percent(0.25, 80, 160), Extent.of(120),
                ScreenText.EMPTY, null, ElementStyle.DEFAULT, "", LayoutSpec.ABSOLUTE, ElementBinding.NONE, true, true));
        for (int i = 0; i < PER_HUD - 1; i++) {
            elements.add(new ScreenElement("l" + i, ElementKind.LABEL, "cadre_" + name,
                    Anchor.values()[i % 9], 0, i * 4,
                    Extent.percent(0.9, 40, 150), Extent.of(9),
                    ScreenText.literal("Ligne " + i), null, ElementStyle.DEFAULT, "", LayoutSpec.ABSOLUTE, ElementBinding.NONE, true, true));
        }
        return new Screen(name, true, elements);
    }

    @Test
    void deuxHudDeTrenteDeuxElementsTiennentDansLeBudget() {
        List<Screen> huds = List.of(hud("mana", Anchor.TOP_RIGHT), hud("quete", Anchor.BOTTOM_LEFT));
        for (Screen screen : huds) {
            assertEquals(PER_HUD, screen.size());
        }

        // Deux tailles de fenêtre alternées : le HUD suit le redimensionnement sans
        // aller-retour serveur, donc rien n'est mis en cache entre deux images.
        double[][] viewports = {{320, 180}, {960, 540}};
        double checksum = 0;
        for (int frame = 0; frame < WARMUP_FRAMES; frame++) {
            checksum += resolveFrame(huds, viewports[frame % 2]);
        }

        long start = System.nanoTime();
        for (int frame = 0; frame < MEASURED_FRAMES; frame++) {
            checksum += resolveFrame(huds, viewports[frame % 2]);
        }
        double perFrame = (System.nanoTime() - start) / (double) MEASURED_FRAMES;

        assertTrue(checksum != 0, "la boucle n'a pas été éliminée par le JIT");
        assertTrue(perFrame < BUDGET_NANOS_PER_FRAME,
                () -> String.format("%.0f ns par image pour %d éléments (budget %.0f)",
                        perFrame, PER_HUD * 2, BUDGET_NANOS_PER_FRAME));
    }

    /**
     * Exactement ce que fait {@code ScreenPainter} avant le premier appel de dessin :
     * une passe de disposition par HUD, puis le dessin lit la table (story 10.10).
     */
    private static double resolveFrame(List<Screen> huds, double[] viewport) {
        double sum = 0;
        for (Screen screen : huds) {
            var placed = ScreenLayout.solve(screen, viewport[0], viewport[1]);
            for (ScreenElement element : screen.elements().values()) {
                if (!ScreenPainter.visible(screen, element, ScreenPainter.Visuals.NONE)) {
                    continue;
                }
                ScreenLayout.Rect rect = placed.get(element.name());
                if (rect != null) {
                    sum += rect.x() + rect.width();
                }
            }
        }
        return sum;
    }
}
