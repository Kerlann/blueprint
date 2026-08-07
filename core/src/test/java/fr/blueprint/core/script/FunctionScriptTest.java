package fr.blueprint.core.script;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.BlueprintFunction;
import fr.blueprint.core.graph.FuncNodes;
import fr.blueprint.core.graph.GraphLoader;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.Node;
import fr.blueprint.core.graph.Vec2d;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Une fonction revient identique par le texte (story 20.1, AC8).
 *
 * <p>C'est la garantie centrale du produit, et elle ne souffre pas d'exception : un corps
 * de fonction est un graphe, et un graphe doit s'écrire et se relire sans rien perdre.
 */
class FunctionScriptTest {

    private static final PluginLoader.LoadedRegistries LOADED = PluginLoader.load(List.of(), true);
    private static final Identifier BP = Identifier.fromNamespaceAndPath("test", "script");

    private static Node node(String seed, String path) {
        return new Node(UUID.nameUUIDFromBytes(seed.getBytes()),
                Identifier.fromNamespaceAndPath("blueprint", path), new Vec2d(12, 34));
    }

    private static Blueprint withFunction() {
        Blueprint bp = new Blueprint(BP);
        Node param = node("p", "func/param");
        Node add = node("add", "math/add");
        Node result = node("r", "func/result");
        for (Node n : List.of(param, result)) {
            GraphLoader.setLiteral(n, FuncNodes.FUNCTION_PIN,
                    LiteralValue.of(PinTypes.STRING, "doubler"));
        }
        GraphLoader.setLiteral(add, "b", LiteralValue.of(PinTypes.DOUBLE, 2.0));

        Map<UUID, Node> nodes = new LinkedHashMap<>();
        for (Node n : List.of(param, add, result)) {
            nodes.put(n.uuid(), n);
        }
        Set<Link> links = new LinkedHashSet<>(List.of(
                new Link(param.uuid(), "exec_out", result.uuid(), "exec_in"),
                new Link(param.uuid(), "n", add.uuid(), "a"),
                new Link(add.uuid(), "result", result.uuid(), "resultat")));

        GraphLoader.addFunction(bp, BlueprintFunction.of("doubler",
                        List.of(new BlueprintFunction.Param("n", PinTypes.DOUBLE)),
                        List.of(new BlueprintFunction.Param("resultat", PinTypes.DOUBLE)))
                .withBody(nodes, links));
        return bp;
    }

    /** <b>Le test qui compte.</b> Écrire, relire, réécrire : le même texte, octet pour octet. */
    @Test
    void uneFonctionRevientIdentiqueParLeTexte() {
        Blueprint before = withFunction();
        var generated = ScriptGenerator.generate(before, LOADED.nodes());
        assertTrue(generated.issues().isEmpty(),
                "l'export ne doit plus rien laisser de côté : " + generated.issues());

        var parsed = ScriptParser.parse(generated.text(), LOADED);
        assertTrue(parsed.success(), () -> "le texte ne se relit pas : " + parsed.error());
        assertEquals(generated.text(),
                ScriptGenerator.generate(parsed.blueprint(), LOADED.nodes()).text(),
                "aller-retour non identique — quelque chose se perd à l'écriture ou à la lecture");
    }

    /** Et la signature revient typée, pas seulement nommée. */
    @Test
    void laSignatureRevientAvecSesTypes() {
        var text = ScriptGenerator.generate(withFunction(), LOADED.nodes()).text();
        var parsed = ScriptParser.parse(text, LOADED);
        assertTrue(parsed.success(), () -> String.valueOf(parsed.error()));

        BlueprintFunction after = parsed.blueprint().function("doubler");
        assertNotNull(after, "la fonction a disparu à la relecture");
        assertEquals(PinTypes.DOUBLE, after.input("n").type());
        assertEquals(PinTypes.DOUBLE, after.output("resultat").type());
        assertEquals(3, after.nodes().size(), "le corps entier doit revenir");
    }

    /** Le texte se lit : la signature est là où on la cherche. */
    @Test
    void leTexteEstLisible() {
        var text = ScriptGenerator.generate(withFunction(), LOADED.nodes()).text();
        assertTrue(text.contains("func \"doubler\"(n: double) returns (resultat: double)"),
                "un format qu'on ne peut pas lire ne vaut pas mieux qu'un binaire :\n" + text);
    }
}
