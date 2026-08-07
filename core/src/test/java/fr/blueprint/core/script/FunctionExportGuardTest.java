package fr.blueprint.core.script;

import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.BlueprintFunction;
import fr.blueprint.core.graph.GraphLoader;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Un export qui perd une fonction le <b>dit</b> (story 20.1, avant l'étape 7).
 *
 * <p>Le BScript ne sait pas encore écrire un corps de fonction. Ce qui compte en attendant
 * n'est pas qu'il sache, c'est qu'il ne fasse pas semblant : un fichier exporté sans un mot,
 * dans lequel une fonction entière a disparu, est la panne qu'on vient de réparer côté NBT.
 *
 * <p>Ce test disparaîtra avec l'étape 7 — et il devra alors être remplacé par son contraire,
 * pas simplement effacé.
 */
class FunctionExportGuardTest {

    private static final PluginLoader.LoadedRegistries LOADED = PluginLoader.load(List.of(), true);

    @Test
    void unExportQuiPerdUneFonctionLeSignale() {
        Blueprint bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "export"));
        GraphLoader.addFunction(bp, BlueprintFunction.of("soigner",
                List.of(new BlueprintFunction.Param("cible", PinTypes.ENTITY)), List.of()));

        var generated = ScriptGenerator.generate(bp, LOADED.nodes());

        assertTrue(generated.issues().stream().anyMatch(i -> i.contains("soigner")),
                "l'export doit nommer la fonction qu'il laisse tomber : " + generated.issues());
    }

    /** Et un blueprint sans fonction n'hérite d'aucun avertissement. */
    @Test
    void unBlueprintSansFonctionResteSilencieux() {
        Blueprint bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "export"));

        assertTrue(ScriptGenerator.generate(bp, LOADED.nodes()).issues().isEmpty());
    }
}
