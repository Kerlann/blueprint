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

    /**
     * Le propriétaire des variables de ce test — un blueprint <b>et</b> un joueur.
     *
     * <p>Les deux sont nécessaires : ces tests amorcent les défauts des trois portées non
     * locales, et une valeur de portée joueur n'a nulle part où aller tant qu'on ne sait
     * pas chez qui. L'UUID est fixe pour que l'échec, s'il arrive, soit reproductible.
     */
    private static final fr.blueprint.core.vm.VarOwner OWNER =
            new fr.blueprint.core.vm.VarOwner(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("test", "vars"),
                    java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"));

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
        assertNull(store.get(VarScope.PLAYER, OWNER, "or"), "rien avant l'amorçage");

        store.seedDefaults(bp, OWNER);
        assertEquals(20.0, store.get(VarScope.PLAYER, OWNER, "or"));
    }

    /** <b>Le test qui compte.</b> Relancer le graphe ne remet pas le compteur à zéro. */
    @Test
    void unDefautNEcrasePasCeQuiExiste() {
        declare("or", VarScope.PLAYER, 20.0);
        store.seedDefaults(bp, OWNER);
        store.set(VarScope.PLAYER, OWNER, "or", 5.0);

        store.seedDefaults(bp, OWNER);
        assertEquals(5.0, store.get(VarScope.PLAYER, OWNER, "or"), "la valeur en cours survit");
    }

    @Test
    void unDefautAbsentNAmorceRien() {
        declare("sans", VarScope.GRAPH, null);
        store.seedDefaults(bp, OWNER);
        assertNull(store.get(VarScope.GRAPH, OWNER, "sans"));
    }

    /**
     * Une variable LOCALE vit dans l'état d'exécution, jamais dans le magasin :
     * l'amorcer ici lui donnerait une seconde existence, invisible et divergente.
     */
    @Test
    void unePortEeLocaleNEstPasAmorcee() {
        declare("temp", VarScope.LOCAL, 3.0);
        store.seedDefaults(bp, OWNER);
        assertNull(store.get(VarScope.LOCAL, OWNER, "temp"));
    }

    @Test
    void toutesLesPorteesNonLocalesSontAmorcees() {
        declare("g", VarScope.GRAPH, 1.0);
        declare("w", VarScope.WORLD, 2.0);
        declare("p", VarScope.PLAYER, 3.0);

        store.seedDefaults(bp, OWNER);
        assertEquals(1.0, store.get(VarScope.GRAPH, OWNER, "g"));
        assertEquals(2.0, store.get(VarScope.WORLD, OWNER, "w"));
        assertEquals(3.0, store.get(VarScope.PLAYER, OWNER, "p"));
    }

    @Test
    void unBlueprintSansVariableNeFaitRien() {
        store.seedDefaults(bp, OWNER);
        assertNull(store.get(VarScope.GRAPH, OWNER, "quoi"));
    }
}
