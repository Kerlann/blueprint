package fr.blueprint.core.vm;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.GraphLoader;
import fr.blueprint.core.graph.VarScope;
import fr.blueprint.core.graph.Variable;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * La valeur par défaut d'une variable devient réelle à l'exécution.
 *
 * <p>Elle ne l'était pas : l'éditeur l'affichait, la sérialisation la transportait, et
 * l'exécution l'ignorait. Un {@code var double or = 20} lu avant d'avoir été écrit
 * rendait {@code null}, et le nœud consommateur tombait avec « le pin n'a ni valeur ni
 * défaut » — un message qui accuse le câblage alors que le câblage était bon.
 */
class VarStoreDefaultsTest {

    private Blueprint bp;
    private VarStore store;

    @BeforeEach
    void setUp() {
        bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "vars"));
        store = VarStore.inMemory();
    }

    private void declare(String name, VarScope scope, Object value) {
        GraphLoader.addVariable(bp, new Variable(name, PinTypes.DOUBLE,
                value == null ? null : LiteralValue.of(PinTypes.DOUBLE, value), scope, false));
    }

    @Test
    void unDefautDeclareEstLuAuPremierAcces() {
        declare("or", VarScope.PLAYER, 20.0);
        assertNull(store.get(VarScope.PLAYER, "or"), "rien avant l'amorçage");

        store.seedDefaults(bp);
        assertEquals(20.0, store.get(VarScope.PLAYER, "or"));
    }

    /** <b>Le test qui compte.</b> Relancer le graphe ne remet pas le compteur à zéro. */
    @Test
    void unDefautNEcrasePasCeQuiExiste() {
        declare("or", VarScope.PLAYER, 20.0);
        store.seedDefaults(bp);
        store.set(VarScope.PLAYER, "or", 5.0);

        store.seedDefaults(bp);
        assertEquals(5.0, store.get(VarScope.PLAYER, "or"), "la valeur en cours survit");
    }

    @Test
    void unDefautAbsentNAmorceRien() {
        declare("sans", VarScope.GRAPH, null);
        store.seedDefaults(bp);
        assertNull(store.get(VarScope.GRAPH, "sans"));
    }

    /**
     * Une variable LOCALE vit dans l'état d'exécution, jamais dans le magasin :
     * l'amorcer ici lui donnerait une seconde existence, invisible et divergente.
     */
    @Test
    void unePortEeLocaleNEstPasAmorcee() {
        declare("temp", VarScope.LOCAL, 3.0);
        store.seedDefaults(bp);
        assertNull(store.get(VarScope.LOCAL, "temp"));
    }

    @Test
    void toutesLesPorteesNonLocalesSontAmorcees() {
        declare("g", VarScope.GRAPH, 1.0);
        declare("w", VarScope.WORLD, 2.0);
        declare("p", VarScope.PLAYER, 3.0);

        store.seedDefaults(bp);
        assertEquals(1.0, store.get(VarScope.GRAPH, "g"));
        assertEquals(2.0, store.get(VarScope.WORLD, "w"));
        assertEquals(3.0, store.get(VarScope.PLAYER, "p"));
    }

    @Test
    void unBlueprintSansVariableNeFaitRien() {
        store.seedDefaults(bp);
        assertNull(store.get(VarScope.GRAPH, "quoi"));
    }
}
