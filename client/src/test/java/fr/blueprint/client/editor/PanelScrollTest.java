package fr.blueprint.client.editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Défilement des panneaux (story 5.12) : bornes, re-bornage, curseur. */
class PanelScrollTest {

    @Test
    void onNeDefilePasAuDelaDuContenu() {
        PanelScroll scroll = new PanelScroll();
        // 30 lignes, 10 visibles → la dernière position utile est 20.
        scroll.scrollBy(100, 30, 10);
        assertEquals(20, scroll.offset(30, 10));

        scroll.scrollBy(-100, 30, 10);
        assertEquals(0, scroll.offset(30, 10), "et jamais au-dessus de la première");
    }

    @Test
    void unContenuQuiTientNeDefilePas() {
        PanelScroll scroll = new PanelScroll();
        scroll.scrollBy(5, 4, 10);
        assertEquals(0, scroll.offset(4, 10));
        assertFalse(PanelScroll.overflows(4, 10));
        assertTrue(PanelScroll.overflows(11, 10));
    }

    /**
     * Le contenu change sous le panneau (autre nœud sélectionné, diagnostic résolu) :
     * une position devenue trop grande laisserait un panneau vide sans rien expliquer.
     */
    @Test
    void laPositionSeReborneQuandLeContenuRetrecit() {
        PanelScroll scroll = new PanelScroll();
        scroll.scrollBy(20, 30, 10);
        assertEquals(20, scroll.offset(30, 10));

        assertEquals(2, scroll.offset(12, 10), "12 lignes, 10 visibles → 2 au maximum");
        assertEquals(0, scroll.offset(3, 10), "tout tient : retour en haut");
    }

    @Test
    void leCurseurRefleteLaPositionEtLaProportion() {
        int track = 100;
        int[] haut = PanelScroll.thumb(0, 40, 10, track);
        int[] bas = PanelScroll.thumb(30, 40, 10, track);

        assertEquals(0, haut[0], "en haut de la piste");
        assertEquals(track / 4, haut[1], "un quart du contenu visible → un quart de piste");
        assertEquals(track - bas[1], bas[0], "en bas quand on a tout défilé");

        int[] minuscule = PanelScroll.thumb(0, 10_000, 10, track);
        assertTrue(minuscule[1] >= 8, "jamais moins de 8 px : sinon il ne s'attrape pas");
    }

    @Test
    void sansDebordementLeCurseurOccupeToutePiste() {
        int[] thumb = PanelScroll.thumb(0, 5, 10, 60);
        assertEquals(0, thumb[0]);
        assertEquals(60, thumb[1]);
    }

    @Test
    void resetRamemeEnHaut() {
        PanelScroll scroll = new PanelScroll();
        scroll.scrollBy(15, 40, 10);
        scroll.reset();
        assertEquals(0, scroll.offset(40, 10));
    }
}
