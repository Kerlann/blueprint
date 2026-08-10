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

    /**
     * Un joker du côté <b>concret</b> passe aussi — la symétrie manquait.
     *
     * <p>{@code twoWildcardsLearnNothing} couvrait déjà le joker face à un emplacement
     * nommé. Face à un type <b>paramétré</b>, il tombait : {@code unifyInto} n'avait pas de
     * branche pour ce cas et rendait faux.
     *
     * <p>Ce n'était pas théorique. La sortie d'un nœud de variable est {@code any} ; relier
     * une variable à {@code map/put} levait donc un {@code GENERIC_CONFLICT}, et
     * <b>aucune liste ni table ne pouvait être rangée dans une variable</b>. Sans
     * rangement, une collection ne survit pas à la fin de l'exécution — les nœuds
     * {@code list/} et {@code map/} étaient là sans servir à grand-chose.
     */
    @Test
    void unJokerConcretPasseFaceAUnTypeParametre() {
        var r = new GenericResolution();
        assertTrue(r.unify(PinTypes.mapOf(PinTypes.STRING, PinTypes.VEC3), PinTypes.ANY),
                "« any » ne se refuse pas : il n'apprend rien, il n'interdit rien");
        assertTrue(r.unify(PinTypes.listOf(PinTypes.generic("T")), PinTypes.ANY));
        // Rien appris : le joker ne lie aucun emplacement.
        assertNull(r.binding("T"));
    }

    /**
     * Un emplacement lié accepte un type <b>parent</b>, pas seulement le sien.
     *
     * <p>Parcourir {@code query/players} pour lire un nom était impossible : la liste lie
     * {@code T} à {@code player}, et {@code entity/name} — le seul nœud qui rend un nom —
     * prend une {@code entity}. L'identité voyait un conflit là où il n'y en a pas.
     *
     * <p>La compatibilité d'un lien est jugée ailleurs, par {@code isAssignableFrom} dans
     * le validateur. Cette résolution ne fait que lier les emplacements.
     */
    @Test
    void unEmplacementLieAccepteUnTypeParent() {
        var r = new GenericResolution();
        assertTrue(r.unify(PinTypes.generic("T"), PinTypes.PLAYER));
        assertTrue(r.unify(PinTypes.generic("T"), PinTypes.ENTITY),
                "un joueur est une entité : ce n'est pas un conflit");
        assertSame(PinTypes.PLAYER, r.binding("T"), "la première liaison, la plus précise, reste");
    }

    /** Deux types sans parenté restent un vrai conflit — c'est ce que le contrôle protège. */
    @Test
    void deuxTypesSansParenteRestentUnConflit() {
        var r = new GenericResolution();
        assertTrue(r.unify(PinTypes.generic("T"), PinTypes.INT));
        assertFalse(r.unify(PinTypes.generic("T"), PinTypes.STRING));
    }

    /** Un pin d'exécution reste refusé, joker ou pas — un lien exec n'est pas une valeur. */
    @Test
    void unJokerNeRendPasUnExecAcceptable() {
        var r = new GenericResolution();
        assertFalse(r.unify(PinTypes.EXEC, PinTypes.ANY));
    }
}
