package fr.blueprint.client.editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Espace : tap = palette, maintenu + pan = jamais la palette (UX §3 vs §6). */
class TapTrackerTest {

    @Test
    void tapProprOuvreLaPalette() {
        TapTracker tap = new TapTracker();
        tap.press();
        assertTrue(tap.isDown());
        assertTrue(tap.release());
        assertFalse(tap.isDown());
    }

    @Test
    void servirAuPanDesarmeLeTap() {
        TapTracker tap = new TapTracker();
        tap.press();
        tap.use();
        assertFalse(tap.release());
        // Le prochain cycle repart neuf.
        tap.press();
        assertTrue(tap.release());
    }

    @Test
    void repetitionClavierNeReamorcePas() {
        TapTracker tap = new TapTracker();
        tap.press();
        tap.use();
        tap.press(); // auto-repeat pendant le maintien : ignoré
        assertFalse(tap.release());
    }
}
