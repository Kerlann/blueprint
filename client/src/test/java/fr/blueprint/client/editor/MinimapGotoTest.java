package fr.blueprint.client.editor;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Projection minimap et recherche « aller au nœud » (story 5.7). */
class MinimapGotoTest {

    @Test
    void projectionAllerRetour() {
        Camera.Rect bounds = new Camera.Rect(-200, -100, 800, 500);
        double[] mini = Minimap.toMini(bounds, 300, 200); // le centre exact
        assertEquals(Minimap.W / 2.0, mini[0], 1e-9);
        assertEquals(Minimap.H / 2.0, mini[1], 1e-9);

        double[] world = Minimap.toWorld(bounds, mini[0], mini[1]);
        assertEquals(300, world[0], 1e-9);
        assertEquals(200, world[1], 1e-9);

        // Aller-retour sur un point excentré.
        double[] m2 = Minimap.toMini(bounds, -200, 500);
        double[] w2 = Minimap.toWorld(bounds, m2[0], m2[1]);
        assertEquals(-200, w2[0], 1e-6);
        assertEquals(500, w2[1], 1e-6);
        // L'échelle est uniforme et positive.
        assertTrue(Minimap.scale(bounds) > 0);
    }

    @Test
    void allerAuNoeud() {
        GotoState state = new GotoState();
        UUID branch = UUID.randomUUID();
        UUID send = UUID.randomUUID();
        state.open(List.of(
                new GotoState.Target(branch, "Branch"),
                new GotoState.Target(send, "Send message")));
        assertTrue(state.isOpen());
        assertEquals(2, state.results().size());

        state.type("send");
        assertEquals(1, state.results().size());
        assertEquals(send, state.selectedTarget().node());

        state.backspace();
        state.backspace();
        state.backspace();
        state.backspace();
        assertEquals(2, state.results().size());
        state.moveSelection(1);
        assertEquals(1, state.selectedIndex());

        state.type("zzz");
        assertTrue(state.results().isEmpty());
        assertNull(state.selectedTarget());

        state.close();
        assertFalse(state.isOpen());
    }
}
