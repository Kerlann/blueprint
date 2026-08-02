package fr.blueprint.client.editor;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiteralEditStateTest {

    private final UUID node = UUID.randomUUID();

    private LiteralEditState text(fr.blueprint.api.pin.PinType type, String initial) {
        LiteralEditState s = new LiteralEditState();
        s.openText(node, "p", 0, type, initial);
        return s;
    }

    @Test
    void nombresValidesEtInvalides() {
        LiteralEditState s = text(PinTypes.INT, "40");
        assertTrue(s.isValid());
        assertEquals(40, s.parse().value());

        s.type("x");
        assertFalse(s.isValid());
        assertNull(s.parse());
        s.backspace();
        assertTrue(s.isValid());

        LiteralEditState d = text(PinTypes.DOUBLE, "2.5");
        assertEquals(2.5, d.parse().value());
        // Un int ne prend pas de décimales.
        assertFalse(text(PinTypes.INT, "2.5").isValid());
        assertEquals(9_999_999_999L, text(PinTypes.LONG, "9999999999").parse().value());
    }

    @Test
    void chainesTexteEtIdentifiant() {
        assertEquals("Bienvenue !", text(PinTypes.STRING, "Bienvenue !").parse().value());
        LiteralValue t = text(PinTypes.TEXT, "salut").parse();
        assertNotNull(t);
        assertEquals("salut", ((Component) t.value()).getString());

        assertEquals(Identifier.fromNamespaceAndPath("blueprint", "demo"),
                text(PinTypes.RESOURCE_LOCATION, "blueprint:demo").parse().value());
        assertFalse(text(PinTypes.RESOURCE_LOCATION, "Pas Valide!").isValid());
    }

    @Test
    void moletteSurChampNumerique() {
        LiteralEditState s = text(PinTypes.INT, "40");
        s.adjustNumber(1);
        assertEquals("41", s.text());
        s.adjustNumber(-10);
        assertEquals("31", s.text());
        // Tampon illisible : la molette repart du delta.
        LiteralEditState broken = text(PinTypes.INT, "abc");
        broken.adjustNumber(5);
        assertEquals("5", broken.text());
        // Double : reste entier tant que la valeur est ronde.
        LiteralEditState d = text(PinTypes.DOUBLE, "2.5");
        d.adjustNumber(1);
        assertEquals("3.5", d.text());
    }

    @Test
    void enumDirection() {
        LiteralEditState s = new LiteralEditState();
        s.openEnum(node, "dir", 1, Direction.EAST);
        assertEquals(LiteralEditState.Mode.ENUM, s.mode());
        assertEquals(Direction.EAST, s.parse().value());
        s.moveOption(1);
        assertNotNull(s.parse());
        s.moveOption(-1);
        assertEquals(Direction.EAST, s.parse().value());
        // Le cycle boucle sans sortir de la liste.
        for (int i = 0; i < 10; i++) {
            s.moveOption(1);
        }
        assertNotNull(s.parse());
    }

    @Test
    void typesNonEditables() {
        assertFalse(LiteralEditState.editableAsText(PinTypes.ITEMSTACK));
        assertFalse(LiteralEditState.editableAsText(PinTypes.VEC3));
        assertFalse(LiteralEditState.editableAsText(PinTypes.BOOL));
        assertTrue(LiteralEditState.editableAsText(PinTypes.RESOURCE_LOCATION));
    }

    @Test
    void affichage() {
        assertEquals("40", LiteralEditState.display(PinTypes.INT,
                LiteralValue.of(PinTypes.INT, 40)));
        assertEquals("2", LiteralEditState.display(PinTypes.DOUBLE,
                LiteralValue.of(PinTypes.DOUBLE, 2.0)));
        assertEquals("north", LiteralEditState.display(PinTypes.DIRECTION,
                LiteralValue.of(PinTypes.DIRECTION, Direction.NORTH)));
        assertEquals("salut", LiteralEditState.display(PinTypes.TEXT,
                LiteralValue.of(PinTypes.TEXT, Component.literal("salut"))));
        assertEquals("", LiteralEditState.display(PinTypes.STRING, null));
    }

    @Test
    void fermetureVideLEtat() {
        LiteralEditState s = text(PinTypes.INT, "1");
        assertTrue(s.isOpen());
        s.close();
        assertFalse(s.isOpen());
        assertEquals("", s.text());
    }
}
