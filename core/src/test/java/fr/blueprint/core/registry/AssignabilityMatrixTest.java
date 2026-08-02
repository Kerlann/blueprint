package fr.blueprint.core.registry;

import fr.blueprint.api.pin.PinType;
import fr.blueprint.api.pin.PinTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Matrice d'assignabilité (story 1.2, AC3 et AC8) — sans Minecraft démarré. */
class AssignabilityMatrixTest {

    static Stream<Arguments> matrix() {
        return Stream.of(
                // Identité
                Arguments.of(PinTypes.INT, PinTypes.INT, true),
                Arguments.of(PinTypes.STRING, PinTypes.STRING, true),
                Arguments.of(PinTypes.EXEC, PinTypes.EXEC, true),
                // Coercitions déclarées : int → long → double, player → entity
                Arguments.of(PinTypes.LONG, PinTypes.INT, true),
                Arguments.of(PinTypes.DOUBLE, PinTypes.LONG, true),
                Arguments.of(PinTypes.DOUBLE, PinTypes.INT, true),   // transitive
                Arguments.of(PinTypes.ENTITY, PinTypes.PLAYER, true),
                // Sens interdit
                Arguments.of(PinTypes.INT, PinTypes.LONG, false),
                Arguments.of(PinTypes.INT, PinTypes.DOUBLE, false),
                Arguments.of(PinTypes.LONG, PinTypes.DOUBLE, false),
                Arguments.of(PinTypes.PLAYER, PinTypes.ENTITY, false),
                // Aucune coercition « intelligente »
                Arguments.of(PinTypes.STRING, PinTypes.INT, false),
                Arguments.of(PinTypes.INT, PinTypes.STRING, false),
                Arguments.of(PinTypes.BOOL, PinTypes.INT, false),
                Arguments.of(PinTypes.TEXT, PinTypes.STRING, false),
                // Exec ne se mélange jamais aux données
                Arguments.of(PinTypes.EXEC, PinTypes.BOOL, false),
                Arguments.of(PinTypes.BOOL, PinTypes.EXEC, false),
                Arguments.of(PinTypes.ANY, PinTypes.EXEC, false),
                // Jokers : lien permis en attendant la résolution
                Arguments.of(PinTypes.ANY, PinTypes.INT, true),
                Arguments.of(PinTypes.INT, PinTypes.ANY, true),
                Arguments.of(PinTypes.listOf(PinTypes.INT), PinTypes.ANY, true));
    }

    @ParameterizedTest(name = "{0} depuis {1} → {2}")
    @MethodSource("matrix")
    void assignability(PinType target, PinType source, boolean expected) {
        assertEquals(expected, target.isAssignableFrom(source));
    }

    @Test
    void parameterizedTypesAreCached() {
        assertSame(PinTypes.listOf(PinTypes.INT), PinTypes.listOf(PinTypes.INT));
        assertSame(PinTypes.mapOf(PinTypes.STRING, PinTypes.INT), PinTypes.mapOf(PinTypes.STRING, PinTypes.INT));
        assertSame(PinTypes.generic("T"), PinTypes.generic("T"));
        assertSame(PinTypes.listOf(PinTypes.listOf(PinTypes.INT)), PinTypes.listOf(PinTypes.listOf(PinTypes.INT)));
    }

    @Test
    void genericsAreInvariant() {
        // list<int> ≠ list<double>, même si int → double existe : invariance voulue.
        assertTrue(PinTypes.listOf(PinTypes.INT).isAssignableFrom(PinTypes.listOf(PinTypes.INT)));
        assertEquals(false, PinTypes.listOf(PinTypes.DOUBLE).isAssignableFrom(PinTypes.listOf(PinTypes.INT)));
        assertEquals(false, PinTypes.listOf(PinTypes.INT).isAssignableFrom(PinTypes.listOf(PinTypes.DOUBLE)));
        // Imbriqué
        assertTrue(PinTypes.listOf(PinTypes.listOf(PinTypes.INT))
                .isAssignableFrom(PinTypes.listOf(PinTypes.listOf(PinTypes.INT))));
        assertEquals(false, PinTypes.listOf(PinTypes.listOf(PinTypes.INT))
                .isAssignableFrom(PinTypes.listOf(PinTypes.listOf(PinTypes.DOUBLE))));
        // Map : clé et valeur invariantes
        assertEquals(false, PinTypes.mapOf(PinTypes.STRING, PinTypes.DOUBLE)
                .isAssignableFrom(PinTypes.mapOf(PinTypes.STRING, PinTypes.INT)));
        // Un conteneur n'est pas l'autre
        assertEquals(false, PinTypes.listOf(PinTypes.INT)
                .isAssignableFrom(PinTypes.mapOf(PinTypes.STRING, PinTypes.INT)));
        // T → list<T> jamais implicite
        assertEquals(false, PinTypes.listOf(PinTypes.INT).isAssignableFrom(PinTypes.INT));
    }

    @Test
    void coercionFunctionsConvertValues() {
        var intToDouble = PinTypes.DOUBLE.coercionFor(PinTypes.INT);
        assertNotNull(intToDouble, "int → double doit être composée transitivement");
        assertEquals(3.0, intToDouble.apply(3));

        var intToLong = PinTypes.LONG.coercionFor(PinTypes.INT);
        assertNotNull(intToLong);
        assertEquals(3L, intToLong.apply(3));

        var playerToEntity = PinTypes.ENTITY.coercionFor(PinTypes.PLAYER);
        assertNotNull(playerToEntity);

        assertNull(PinTypes.INT.coercionFor(PinTypes.DOUBLE), "pas de rétrécissement implicite");
        assertNull(PinTypes.STRING.coercionFor(PinTypes.INT), "pas de conversion vers string");
    }
}
