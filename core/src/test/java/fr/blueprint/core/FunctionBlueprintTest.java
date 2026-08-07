package fr.blueprint.core;

import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.GraphValidator;
import fr.blueprint.core.registry.PluginLoader;
import fr.blueprint.core.script.ScriptGenerator;
import fr.blueprint.core.script.ScriptParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le blueprint des fonctions compile, se valide et survit au texte (story 20.1, AC8).
 */
class FunctionBlueprintTest {

    private static final PluginLoader.LoadedRegistries LOADED = PluginLoader.load(List.of(), true);

    private static Blueprint built() {
        return FunctionBlueprint.build(LOADED.nodes());
    }

    @Test
    void ilNeLaissePasUnSeulDiagnostic() {
        var diagnostics = GraphValidator.validate(built(), LOADED.nodes()).diagnostics();
        assertTrue(diagnostics.isEmpty(),
                "un exemple livre ne doit rien laisser a corriger : " + diagnostics);
    }

    @Test
    void ilRevientIdentiqueParLeTexte() {
        Blueprint original = built();
        var generated = ScriptGenerator.generate(original, LOADED.nodes());
        assertTrue(generated.issues().isEmpty(),
                "rien ne doit se perdre a l'export : " + generated.issues());

        var parsed = ScriptParser.parse(generated.text(), LOADED);
        assertTrue(parsed.success(), () -> "le texte ne se relit pas : " + parsed.error());
        assertEquals(generated.text(),
                ScriptGenerator.generate(parsed.blueprint(), LOADED.nodes()).text());
    }

    /**
     * <b>Le test qui compte.</b> Le graphe rend 25, pas 18 ni 32.
     *
     * <p>{@code carre(3)} et {@code carre(4)} vivent dans la MEME execution, appeles depuis
     * le corps d'une autre fonction, et leurs resultats sont additionnes apres que les deux
     * ont tourne. Un corps partage entre les deux sites rendrait 2x16 = 32 ; une
     * memoisation qui ne les distingue pas, 2x9 = 18.
     */
    @Test
    void deuxAppelsImbriquesRendentVingtCinq() {
        Blueprint bp = built();
        var start = bp.nodes().values().stream()
                .filter(n -> n.typeId().getPath().equals("event/command"))
                .findFirst().orElseThrow().uuid();
        var compiled = fr.blueprint.core.compile.Compiler.compile(bp, LOADED.nodes(), start);
        assertTrue(compiled.success(), () -> "compilation echouee : " + compiled.diagnostics());

        var vars = fr.blueprint.core.vm.VarStore.inMemory();
        var owner = new fr.blueprint.core.vm.VarOwner(FunctionBlueprint.ID, null);
        var env = new fr.blueprint.core.vm.ExecutionEnvironment(
                id -> LOADED.nodes().get(id).orElse(null),
                new fr.blueprint.api.node.BlueprintHandle() {
                    @Override
                    public net.minecraft.resources.Identifier id() {
                        return FunctionBlueprint.ID;
                    }

                    @Override
                    public boolean enabled() {
                        return true;
                    }
                },
                new fr.blueprint.api.event.TriggerContext() {
                    @Override
                    public net.minecraft.resources.Identifier eventId() {
                        return net.minecraft.resources.Identifier
                                .fromNamespaceAndPath("blueprint", "event/command");
                    }

                    @Override
                    public Object output(String name) {
                        return null;   // pas de joueur : le message faute, la variable non
                    }
                },
                vars, owner, null, null,
                org.slf4j.LoggerFactory.getLogger("blueprint-test"));

        fr.blueprint.core.vm.BlueprintVm.run(compiled.ir(),
                fr.blueprint.core.vm.ExecutionState.fresh(compiled.ir()), env, 10_000);

        assertEquals(25.0,
                vars.get(fr.blueprint.core.graph.VarScope.GRAPH, owner, "resultat"),
                "3x3 + 4x4 doit faire 25 : 32 signifie un corps partage entre les deux "
                        + "appels, 18 une memoisation qui ne les distingue pas");
    }

    /** Une fonction en appelle une autre : c'est ce que cet exemple est la pour montrer. */
    @Test
    void uneFonctionEnAppelleUneAutre() {
        var hyp = built().function("hypotenuse_carree");
        long appels = hyp.nodes().values().stream()
                .filter(n -> fr.blueprint.core.graph.FuncNodes.isCall(n.typeId()))
                .count();

        assertEquals(2, appels,
                "deux appels a carre : un seul ne prouverait pas que deux appels de la "
                        + "meme fonction ne se marchent pas dessus");
    }
}
