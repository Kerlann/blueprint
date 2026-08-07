package fr.blueprint.client.editor;

import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.BlueprintFunction;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.GraphLoader;
import fr.blueprint.core.graph.VarNodes;
import fr.blueprint.core.graph.VarScope;
import fr.blueprint.core.graph.Variable;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ce que le blueprint ajoute à la palette (story 5.13, étendue par la 20.2, AC6).
 *
 * <p>Le choix porte des règles qu'on ne voit pas à l'œil — une fonction ne se propose pas
 * depuis son propre corps, une variable donne deux entrées et non une. Laissées dans le
 * widget, elles étaient invérifiables.
 */
class BlueprintPaletteEntriesTest {

    private static final PluginLoader.LoadedRegistries LOADED = PluginLoader.load(List.of(), true);

    private Blueprint bp;

    @BeforeEach
    void setUp() {
        bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "palette"));
        GraphLoader.addFunction(bp, BlueprintFunction.of("carre",
                List.of(new BlueprintFunction.Param("n", PinTypes.DOUBLE)),
                List.of(new BlueprintFunction.Param("r", PinTypes.DOUBLE))));
        GraphLoader.addFunction(bp, BlueprintFunction.of("soigner", List.of(), List.of()));
        assertTrue(new EditOperation.AddVariable(new Variable("score", PinTypes.INT, null,
                VarScope.GRAPH, false)).apply(bp, LOADED.nodes()).applied());
    }

    private List<NodeSearch.Entry> entries(String openBody) {
        return BlueprintPaletteEntries.of(bp, openBody,
                (key, arg) -> key + ":" + arg, type -> type.toString());
    }

    /** Chaque variable donne deux entrées, chaque fonction une. */
    @Test
    void chaqueMembreDuBlueprintDonneSesEntrees() {
        List<NodeSearch.Entry> out = entries(null);

        assertEquals(2, out.stream().filter(e -> VarNodes.GET.equals(e.id())
                || VarNodes.SET.equals(e.id())).count(),
                "lire et écrire sont deux nœuds différents, comme dans Unreal");
        assertEquals(2, out.stream().filter(NodeSearch.Entry::isCall).count());
        assertTrue(out.stream().anyMatch(e -> e.isCall() && "carre".equals(e.bound())));
    }

    /**
     * <b>Une entrée d'appel porte la signature en sous-titre.</b>
     *
     * <p>Un nom nu obligerait à ouvrir chaque corps pour savoir lequel prend une entité —
     * ce qui est précisément la question qu'on se pose en cherchant quoi appeler.
     */
    @Test
    void uneEntreeDAppelPorteLaSignature() {
        NodeSearch.Entry appel = entries(null).stream()
                .filter(e -> "carre".equals(e.bound())).findFirst().orElseThrow();

        assertEquals("carre(n) → r", appel.description());
        assertEquals(PaletteState.FUNCTIONS, appel.category(),
                "« Appeler carre » n'est pas une variable");
    }

    /**
     * <b>Une fonction ne se propose pas depuis son propre corps</b> (AC6).
     *
     * <p>La récursion est refusée par le validateur. Offrir l'appel mènerait à un
     * diagnostic plutôt qu'à un nœud utilisable — la palette proposerait une impasse.
     */
    @Test
    void uneFonctionNeSeProposePasDepuisSonPropreCorps() {
        List<NodeSearch.Entry> dansCarre = entries("carre");

        assertFalse(dansCarre.stream().anyMatch(e -> "carre".equals(e.bound())),
                "s'appeler soi-même est refusé : le proposer serait une impasse");
        assertTrue(dansCarre.stream().anyMatch(e -> "soigner".equals(e.bound())),
                "les AUTRES fonctions restent appelables depuis un corps");
        assertEquals(2, dansCarre.stream().filter(e -> VarNodes.GET.equals(e.id())
                        || VarNodes.SET.equals(e.id())).count(),
                "et les variables aussi : elles appartiennent au blueprint, pas au graphe");
    }
}
