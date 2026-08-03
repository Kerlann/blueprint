package fr.blueprint.client.editor;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectionModelTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    @Test
    void clicSimpleSelectionneSeul() {
        SelectionModel<UUID> sel = new SelectionModel<>();
        sel.click(a, false);
        sel.click(b, false);
        assertFalse(sel.isSelected(a));
        assertTrue(sel.isSelected(b));
        assertEquals(1, sel.size());
    }

    @Test
    void shiftClicBascule() {
        SelectionModel<UUID> sel = new SelectionModel<>();
        sel.click(a, false);
        sel.click(b, true);
        assertTrue(sel.isSelected(a));
        assertTrue(sel.isSelected(b));
        sel.click(a, true);
        assertFalse(sel.isSelected(a));
        assertTrue(sel.isSelected(b));
    }

    @Test
    void clicSurUnNoeudDejaSelectionnePreserveLeGroupe() {
        // Sans ça, impossible de glisser une multi-sélection.
        SelectionModel<UUID> sel = new SelectionModel<>();
        sel.selectAll(List.of(a, b, c), false);
        sel.click(b, false);
        assertEquals(3, sel.size());
    }

    @Test
    void clicDansLeVide() {
        SelectionModel<UUID> sel = new SelectionModel<>();
        sel.selectAll(List.of(a, b), false);
        sel.click(null, true);
        assertEquals(2, sel.size());
        sel.click(null, false);
        assertTrue(sel.isEmpty());
    }

    @Test
    void rectangleRemplaceOuAjoute() {
        SelectionModel<UUID> sel = new SelectionModel<>();
        sel.selectAll(List.of(a), false);
        sel.selectAll(List.of(b, c), true);
        assertEquals(3, sel.size());
        sel.selectAll(List.of(c), false);
        assertEquals(1, sel.size());
        assertTrue(sel.isSelected(c));
    }
}
