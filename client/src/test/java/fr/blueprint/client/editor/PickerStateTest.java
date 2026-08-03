package fr.blueprint.client.editor;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PickerStateTest {

    private static List<PickerState.Entry> entries(int count) {
        List<PickerState.Entry> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add(new PickerState.Entry(
                    Identifier.fromNamespaceAndPath("minecraft", "item_" + i), "Objet " + i));
        }
        return out;
    }

    @Test
    void rechercheSurTitreEtIdentifiant() {
        PickerState state = new PickerState();
        state.open(UUID.randomUUID(), "item", false, List.of(
                new PickerState.Entry(Identifier.fromNamespaceAndPath("minecraft", "stone"), "Pierre"),
                new PickerState.Entry(Identifier.fromNamespaceAndPath("minecraft", "dirt"), "Terre")));
        assertEquals(2, state.filtered().size());
        state.type("pier");
        assertEquals(1, state.filtered().size());
        state.backspace();
        state.backspace();
        state.backspace();
        state.backspace();
        state.type("dirt"); // par l'identifiant aussi
        assertEquals(1, state.filtered().size());
        assertEquals("Terre", state.filtered().get(0).title());
    }

    @Test
    void fenetreEtDefilement() {
        PickerState state = new PickerState();
        state.open(UUID.randomUUID(), "item", false, entries(100));
        assertEquals(PickerState.COLS * PickerState.ROWS, state.window().size());
        assertNull(state.at(PickerState.COLS * PickerState.ROWS)); // hors fenêtre

        state.scrollBy(1000);
        assertTrue(state.scrollRow() > 0);
        assertTrue(state.window().size() <= PickerState.COLS * PickerState.ROWS);
        // La dernière fenêtre contient bien la dernière entrée.
        List<PickerState.Entry> window = state.window();
        assertEquals("Objet 99", window.get(window.size() - 1).title());

        state.scrollBy(-1000);
        assertEquals(0, state.scrollRow());
        // Une nouvelle recherche remet le défilement à zéro.
        state.scrollBy(3);
        state.type("9");
        assertEquals(0, state.scrollRow());
    }

    @Test
    void cibleConservee() {
        PickerState state = new PickerState();
        UUID node = UUID.randomUUID();
        state.open(node, "block", true, entries(3));
        assertTrue(state.isOpen());
        assertTrue(state.isBlock());
        assertEquals(node, state.node());
        assertEquals("block", state.pin());
        state.close();
        assertTrue(!state.isOpen());
    }
}
