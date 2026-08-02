package fr.blueprint.core.registry;

import fr.blueprint.api.pin.GenericResolution;
import fr.blueprint.api.pin.PinTypes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Résolution des jokers au câblage (story 1.2, AC5). */
class GenericResolutionTest {

    @Test
    void bindsSlotOnFirstUnification() {
        var r = new GenericResolution();
        assertTrue(r.unify(PinTypes.generic("T"), PinTypes.INT));
        assertSame(PinTypes.INT, r.binding("T"));
        assertSame(PinTypes.INT, r.resolve(PinTypes.generic("T")));
    }

    @Test
    void conflictingBindingIsRejectedWithoutSideEffect() {
        var r = new GenericResolution();
        assertTrue(r.unify(PinTypes.generic("T"), PinTypes.INT));
        assertFalse(r.unify(PinTypes.generic("T"), PinTypes.STRING));
        // La liaison d'origine survit au conflit.
        assertSame(PinTypes.INT, r.binding("T"));
    }

    @Test
    void unifiesThroughParameterizedTypes() {
        var r = new GenericResolution();
        assertTrue(r.unify(PinTypes.listOf(PinTypes.generic("T")), PinTypes.listOf(PinTypes.STRING)));
        assertSame(PinTypes.STRING, r.binding("T"));
    }

    @Test
    void containerMismatchFails() {
        var r = new GenericResolution();
        assertFalse(r.unify(PinTypes.listOf(PinTypes.generic("T")), PinTypes.INT));
        assertNull(r.binding("T"));
    }

    @Test
    void resolutionPropagatesAcrossPinsOfSameNode() {
        // Scénario AC5 : un nœud « premier élément » — entrée list<T>, sortie T.
        // Câbler l'entrée sur list<string> résout la sortie en string.
        var node = new GenericResolution();
        assertTrue(node.unify(PinTypes.listOf(PinTypes.generic("T")), PinTypes.listOf(PinTypes.STRING)));
        assertSame(PinTypes.STRING, node.resolve(PinTypes.generic("T")));
        assertSame(PinTypes.listOf(PinTypes.STRING), node.resolve(PinTypes.listOf(PinTypes.generic("T"))));
        assertTrue(node.isFullyResolved(PinTypes.generic("T")));
    }

    @Test
    void anyIsAGroupLikeAnyOtherSlot() {
        var r = new GenericResolution();
        assertTrue(r.unify(PinTypes.ANY, PinTypes.BLOCKPOS));
        assertSame(PinTypes.BLOCKPOS, r.resolve(PinTypes.ANY));
        assertFalse(r.unify(PinTypes.ANY, PinTypes.STRING), "any déjà résolu en blockpos");
    }

    @Test
    void unresolvedSlotStaysUnresolved() {
        var r = new GenericResolution();
        assertSame(PinTypes.generic("T"), r.resolve(PinTypes.generic("T")));
        assertFalse(r.isFullyResolved(PinTypes.generic("T")));
        assertFalse(r.isFullyResolved(PinTypes.listOf(PinTypes.generic("T"))));
    }

    @Test
    void twoWildcardsLearnNothing() {
        var r = new GenericResolution();
        assertTrue(r.unify(PinTypes.generic("T"), PinTypes.ANY));
        assertNull(r.binding("T"));
    }

    @Test
    void execNeverBindsToASlot() {
        var r = new GenericResolution();
        assertFalse(r.unify(PinTypes.generic("T"), PinTypes.EXEC));
    }
}
