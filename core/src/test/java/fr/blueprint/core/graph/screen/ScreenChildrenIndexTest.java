package fr.blueprint.core.graph.screen;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * « Quels sont les enfants de ce conteneur ? » ne doit pas coûter un parcours de l'écran.
 *
 * <p>{@link Screen#childrenOf} parcourait <b>tous</b> les éléments et allouait deux listes
 * à chaque appel. {@link ScreenLayout} le pose une fois par élément en descendant l'arbre :
 * la passe de disposition était donc <b>quadratique</b> en nombre d'éléments, jusqu'au
 * plafond de 128 par écran.
 *
 * <p>Ce n'est pas théorique : le HUD refait cette passe à chaque image (épic 17b), et le
 * concepteur la refait à chaque édition.
 *
 * <h2>Pourquoi une pente et non une durée</h2>
 *
 * <p>Les standards (§7.1) préfèrent « un rapport entre deux mesures prises au même
 * moment » : les deux subissent la même machine, donc leur rapport n'en dépend plus. La
 * propriété qu'on a changée s'énonce simplement — <b>quadrupler le nombre d'éléments ne
 * doit pas quadrupler le coût PAR élément</b>.
 */
class ScreenChildrenIndexTest {

    private static final Logger LOGGER = LoggerFactory.getLogger("blueprint-test");

    private static final int SMALL = 32;
    private static final int LARGE = SMALL * 4;

    /**
     * Un écran plat : un conteneur, et {@code count} enfants dedans.
     *
     * <p>Plat à dessein — c'est le pire cas pour un parcours par conteneur, et c'est aussi
     * la forme la plus courante d'un HUD réel : quelques dizaines d'éléments côte à côte.
     */
    private static Screen flat(int count) {
        List<ScreenElement> elements = new ArrayList<>(count + 1);
        elements.add(ScreenElement.of("boite", ElementKind.PANEL, 0, 0, 200, 200));
        for (int i = 0; i < count; i++) {
            elements.add(ScreenElement.of("e" + i, ElementKind.LABEL, 0, i * 2, 40, 10)
                    .withParent("boite"));
        }
        return new Screen("ecran" + count, true, elements);
    }

    private static long round(Screen screen, int count) {
        long begin = System.nanoTime();
        int seen = 0;
        // Une question par élément, comme le fait la passe de disposition en descendant.
        for (int repeat = 0; repeat < 200; repeat++) {
            seen += screen.childrenOf("boite").size();
            for (int i = 0; i < count; i++) {
                seen += screen.childrenOf("e" + i).size();
            }
        }
        assertTrue(seen > 0, "le banc doit réellement interroger l'écran");
        return System.nanoTime() - begin;
    }

    /**
     * <b>Le test qui compte.</b> Quadrupler les éléments ne quadruple pas le coût par élément.
     *
     * <h2>La marge, mesurée</h2>
     *
     * <p>Voir le journal du test. Sans index, le rapport vaut celui des tailles — quatre ;
     * avec, il tombe vers un. Le seuil de <b>2,0</b> laisse un facteur deux de chaque côté.
     */
    @Test
    void leCoutParElementNeSuitPasLaTailleDeLEcran() {
        Screen small = flat(SMALL);
        Screen large = flat(LARGE);

        for (int i = 0; i < 5; i++) {
            round(small, SMALL);
            round(large, LARGE);
        }
        long bestSmall = Long.MAX_VALUE;
        long bestLarge = Long.MAX_VALUE;
        for (int i = 0; i < 15; i++) {
            bestSmall = Math.min(bestSmall, round(small, SMALL));
            bestLarge = Math.min(bestLarge, round(large, LARGE));
        }

        // Normalisé PAR élément : sans cela, un coût linéaire donnerait déjà quatre.
        double perSmall = (double) bestSmall / SMALL;
        double perLarge = (double) bestLarge / LARGE;
        double ratio = perLarge / perSmall;

        LOGGER.info("childrenOf : {} µs à {} éléments, {} µs à {} éléments"
                        + " — coût par élément × {}",
                bestSmall / 1000, SMALL, bestLarge / 1000, LARGE,
                String.format(Locale.ROOT, "%.2f", ratio));

        assertTrue(bestSmall > 0 && bestLarge > 0, "mesure nulle : le banc ne mesure rien");
        assertTrue(ratio < 2.0, String.format(Locale.ROOT,
                "quadrupler les éléments a multiplié le coût PAR ÉLÉMENT par %.2f —"
                        + " la passe de disposition est redevenue quadratique", ratio));
    }

    /** Et la réponse reste exactement la même, index ou pas. */
    @Test
    void lIndexRepondCommeLeParcours() {
        Screen screen = flat(12);
        assertEquals(12, screen.childrenOf("boite").size());
        assertEquals(List.of(), screen.childrenOf("e0"));
        assertEquals(List.of("boite"),
                screen.childrenOf(null).stream().map(ScreenElement::name).toList());

        // L'ordre de dessin est un contrat : les enfants sortent dans l'ordre déclaré.
        List<String> names = screen.childrenOf("boite").stream().map(ScreenElement::name).toList();
        for (int i = 0; i < 12; i++) {
            assertEquals("e" + i, names.get(i), "l'ordre de dessin doit être préservé");
        }
    }

    /** Un parent inconnu rend une liste vide, sans allouer ni lever. */
    @Test
    void unParentInconnuRendVide() {
        assertEquals(List.of(), flat(4).childrenOf("absent"));
    }
}
