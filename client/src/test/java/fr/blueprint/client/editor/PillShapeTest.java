package fr.blueprint.client.editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La <b>forme</b> de la pastille, prise seule.
 *
 * <p>Le rendu ne se teste pas sans client, mais la géométrie qui le décide, si — et c'est
 * elle qui était fausse. La première pastille livrée réutilisait l'escalier de
 * {@code roundedFill}, qui rentre d'un pixel par rangée : c'est un coin abattu à un rayon
 * de deux, et un <b>chanfrein à quarante-cinq degrés</b> à un rayon de onze. À l'écran,
 * un hexagone.
 */
class PillShapeTest {

    /** La hauteur réelle d'une pastille : une rangée plus ses deux retraits. */
    private static final int HEIGHT = (int) NodeGeometry.ROW_HEIGHT + 6;

    /**
     * <b>Le test qui compte.</b> La capsule est plus large qu'un chanfrein à 45° partout
     * sauf à ses deux rangées extrêmes.
     *
     * <p>C'est la formulation exacte du défaut : l'ancienne forme retirait {@code r − row}
     * à la rangée {@code row}, ce qui est la diagonale. Un cercle, lui, ne quitte le bord
     * que lentement — il est donc <b>strictement</b> moins rentré sur toutes les rangées
     * intermédiaires. Une pastille qui repasserait à l'escalier échouerait ici.
     */
    @Test
    void laCapsuleSuitUnCercleEtNonUneDiagonale() {
        int r = HEIGHT / 2;
        for (int row = 1; row < r; row++) {
            int chanfrein = r - row;
            int cercle = NodeWidget.capsuleInset(row, HEIGHT);
            assertTrue(cercle < chanfrein,
                    "rangée " + row + " : le cercle doit rentrer moins que la diagonale, "
                            + cercle + " contre " + chanfrein);
        }
    }

    /** Au milieu, la capsule occupe toute sa largeur : c'est là que tombe le pin. */
    @Test
    void auMilieuLaCapsuleNEstPasRentree() {
        assertEquals(0, NodeWidget.capsuleInset(HEIGHT / 2, HEIGHT));
        assertEquals(0, NodeWidget.capsuleInset((HEIGHT - 1) / 2, HEIGHT));
    }

    /**
     * Haut et bas se répondent. Une asymétrie ferait pencher la pastille d'un pixel, ce
     * qui ne se nomme pas mais se voit dès qu'il y en a deux l'une sous l'autre.
     */
    @Test
    void laCapsuleEstSymetrique() {
        for (int row = 0; row < HEIGHT; row++) {
            assertEquals(NodeWidget.capsuleInset(row, HEIGHT),
                    NodeWidget.capsuleInset(HEIGHT - 1 - row, HEIGHT),
                    "rangée " + row + " et son reflet");
        }
    }

    /**
     * Le retrait ne dépasse jamais le rayon : au-delà, la capsule se replierait sur
     * elle-même et {@code fill} recevrait un rectangle inversé — invisible, donc pire.
     */
    @Test
    void leRetraitResteDansLeRayon() {
        for (int height = 4; height <= 40; height++) {
            for (int row = 0; row < height; row++) {
                int inset = NodeWidget.capsuleInset(row, height);
                assertTrue(inset >= 0 && inset <= height / 2 + 1,
                        "hauteur " + height + ", rangée " + row + " : retrait " + inset);
            }
        }
    }
}
