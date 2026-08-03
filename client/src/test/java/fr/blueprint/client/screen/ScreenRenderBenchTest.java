package fr.blueprint.client.screen;

import fr.blueprint.core.graph.screen.ElementKind;
import fr.blueprint.core.graph.screen.Extent;
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
 * Banc de rendu d'un écran (story 10.3, AC6, NFR1). Ce qui est mesuré est la passe
 * <b>CPU par image</b> — résolution des ancres, des pourcentages et de la chaîne de
 * visibilité pour 64 éléments imbriqués. Les appels de dessin eux-mêmes ne sont pas
 * mesurables sans jeu ; la mesure fps réelle reste VERIFY-10.3.
 *
 * <p>La résolution est refaite <b>à chaque image</b> et non mise en cache : la fenêtre
 * peut être redimensionnée entre deux images, et un cache invalidé au mauvais moment
 * donnerait un menu figé à l'ancienne taille. D'où ce banc — il faut prouver que la
 * refaire coûte assez peu pour que ce choix tienne.
 */
class ScreenRenderBenchTest {

    private static final int ELEMENTS = 64;
    private static final int WARMUP_FRAMES = 500;
    private static final int MEASURED_FRAMES = 2_000;
    /** Seuil volontairement large (machine CI inconnue), très en dessous des 16,6 ms. */
    private static final double BUDGET_NANOS_PER_FRAME = 500_000;

    /**
     * Le pire cas plausible : quatre pages imbriquées de seize éléments, tailles en
     * pourcentage (la résolution remonte alors la chaîne de parenté à chaque fois).
     */
    private static Screen dense() {
        List<ScreenElement> elements = new ArrayList<>();
        for (int page = 0; page < 4; page++) {
            elements.add(new ScreenElement("page" + page, ElementKind.PANEL, null,
                    fr.blueprint.core.graph.screen.Anchor.CENTER, 0, 0,
                    Extent.percent(0.8, 200, 600), Extent.percent(0.8, 120, 400),
                    ScreenText.EMPTY, null,
                    fr.blueprint.core.graph.screen.ElementStyle.DEFAULT, page == 0, true));
            for (int i = 0; i < 15; i++) {
                elements.add(new ScreenElement("p" + page + "_e" + i, ElementKind.BUTTON,
                        "page" + page,
                        fr.blueprint.core.graph.screen.Anchor.values()[i % 9],
                        i % 5 * 12, i * 6,
                        Extent.percent(0.3, 40, 120), Extent.of(16),
                        ScreenText.literal("Bouton " + i), null,
                        fr.blueprint.core.graph.screen.ElementStyle.DEFAULT, true, true));
            }
        }
        return new Screen("dense", false, elements);
    }

    @Test
    void laPasseCpuTientLargementDansLeBudgetDImage() {
        Screen screen = dense();
        assertEquals(ELEMENTS, screen.size());

        // Deux tailles de fenêtre alternées : c'est le cas qui interdit tout cache, et
        // donc celui qu'il faut mesurer.
        double[][] viewports = {{320, 180}, {960, 540}};
        double checksum = 0;
        for (int frame = 0; frame < WARMUP_FRAMES; frame++) {
            checksum += resolveFrame(screen, viewports[frame % 2]);
        }

        long start = System.nanoTime();
        for (int frame = 0; frame < MEASURED_FRAMES; frame++) {
            checksum += resolveFrame(screen, viewports[frame % 2]);
        }
        double perFrame = (System.nanoTime() - start) / (double) MEASURED_FRAMES;

        assertTrue(checksum != 0, "la boucle n'a pas été éliminée par le JIT");
        assertTrue(perFrame < BUDGET_NANOS_PER_FRAME,
                () -> String.format("%.0f ns par image pour %d éléments (budget %.0f)",
                        perFrame, ELEMENTS, BUDGET_NANOS_PER_FRAME));
    }

    /** Exactement ce que fait {@code ScreenPainter} avant le premier appel de dessin. */
    private static double resolveFrame(Screen screen, double[] viewport) {
        double sum = 0;
        for (ScreenElement element : screen.elements().values()) {
            if (!ScreenPainter.visible(screen, element, ScreenPainter.Visuals.NONE)) {
                continue;
            }
            ScreenLayout.Rect rect =
                    ScreenLayout.resolve(screen, element, viewport[0], viewport[1]);
            sum += rect.x() + rect.width();
        }
        return sum;
    }

    /**
     * Masquer une page masque ses quinze enfants : c'est ce qui rend un menu à onglets
     * praticable, et c'est aussi ce qui fait que le budget tient — trois pages sur
     * quatre ne sont pas résolues.
     */
    @Test
    void masquerUnParentEcarteToutSonContenu() {
        Screen screen = dense();
        long drawn = screen.elements().values().stream()
                .filter(e -> ScreenPainter.visible(screen, e, ScreenPainter.Visuals.NONE))
                .count();
        assertEquals(16, drawn, "seule la première page et ses quinze enfants");
    }
}
