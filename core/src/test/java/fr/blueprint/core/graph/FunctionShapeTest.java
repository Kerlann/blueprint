package fr.blueprint.core.graph;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinKind;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Le point dur de la story 20.1</b> : une forme de nœud qui dépend du graphe.
 *
 * <p>{@code NodeShape} se résout depuis le registre <b>global</b>, et le registre mémoïse
 * par identifiant. La forme d'un nœud d'appel, elle, dépend du <b>blueprint</b> : deux
 * graphes peuvent définir {@code soigner} avec deux signatures différentes, et un cache par
 * identifiant rendrait la forme de l'un à l'autre — un lien accepté contre le mauvais type,
 * sans le moindre diagnostic.
 *
 * <p>La story dit de traiter ce point en premier et de s'arrêter s'il résiste. Ces tests
 * sont ce qui décide.
 */
class FunctionShapeTest {

    private static final PluginLoader.LoadedRegistries LOADED = PluginLoader.load(List.of(), true);

    private static Blueprint withFunction(String path, BlueprintFunction function) {
        Blueprint bp = new Blueprint(Identifier.fromNamespaceAndPath("test", path));
        GraphLoader.addFunction(bp, function);
        return bp;
    }

    /** Un nœud d'appel visant {@code name}, posé dans le graphe principal. */
    private static Node call(Blueprint bp, String name) {
        UUID uuid = UUID.nameUUIDFromBytes((bp.id() + "/" + name).getBytes());
        var op = new EditOperation.AddNode(uuid, FuncNodes.CALL, new Vec2d(0, 0));
        assertTrue(op.apply(bp, LOADED.nodes()).applied());
        assertTrue(new EditOperation.SetLiteral(uuid, FuncNodes.FUNCTION_PIN,
                LiteralValue.of(PinTypes.STRING, name)).apply(bp, LOADED.nodes()).applied());
        return bp.node(uuid);
    }

    /**
     * <b>Le test qui décide.</b> Deux blueprints, un même nom, deux signatures.
     *
     * <p>Sur un cache par identifiant, les deux appels rendraient la même forme et l'un des
     * deux graphes accepterait un lien contre le mauvais type.
     */
    @Test
    void deuxBlueprintsDefinissentSoignerSansSeConfondre() {
        Blueprint soins = withFunction("soins", BlueprintFunction.of("soigner",
                List.of(new BlueprintFunction.Param("cible", PinTypes.ENTITY),
                        new BlueprintFunction.Param("points", PinTypes.DOUBLE)),
                List.of(new BlueprintFunction.Param("soigne", PinTypes.BOOL))));

        Blueprint autre = withFunction("autre", BlueprintFunction.of("soigner",
                List.of(new BlueprintFunction.Param("message", PinTypes.STRING)),
                List.of()));

        NodeShape ici = LOADED.nodes().shape(soins, call(soins, "soigner"));
        NodeShape la = LOADED.nodes().shape(autre, call(autre, "soigner"));

        assertNotNull(ici);
        assertNotNull(la);
        assertEquals(PinTypes.ENTITY, ici.input("cible").type(),
                "le premier graphe doit garder SA signature");
        assertEquals(PinTypes.STRING, la.input("message").type());
        assertNull(la.input("cible"),
                "la signature de l'autre graphe a fui jusqu'ici — le cache est par "
                        + "identifiant, pas par blueprint");
        assertNull(ici.output("soigne") == null ? "manquant" : null,
                "la sortie déclarée doit exister");
    }

    /** La forme porte les pins d'exécution, et les paramètres sont obligatoires. */
    @Test
    void laFormeDAppelSuitLaSignature() {
        Blueprint bp = withFunction("f", BlueprintFunction.of("compter",
                List.of(new BlueprintFunction.Param("jusqua", PinTypes.INT)),
                List.of(new BlueprintFunction.Param("total", PinTypes.INT))));

        NodeShape shape = LOADED.nodes().shape(bp, call(bp, "compter"));

        assertNotNull(shape);
        assertEquals(PinKind.EXEC, shape.input(BlueprintFunction.EXEC_IN).kind());
        assertEquals(PinKind.EXEC, shape.output(BlueprintFunction.EXEC_OUT).kind());
        assertTrue(shape.input("jusqua").required(),
                "un paramètre non câblé doit se voir sur l'APPEL, pas fauter au milieu du corps");
        assertTrue(!shape.entryPoint(), "une fonction s'appelle, elle ne se déclenche pas");
    }

    /**
     * <b>Une fonction supprimée ne rend plus de forme.</b>
     *
     * <p>Le même signal qu'un type inconnu, parce que c'est ce qu'attendent les vingt-cinq
     * appelants existants. La <b>raison</b> du refus est le travail du validateur, qui
     * distingue « type inconnu » de « nom qui ne résout pas ».
     */
    @Test
    void uneFonctionSupprimeeNeRendPlusDeForme() {
        Blueprint bp = withFunction("f", BlueprintFunction.of("soigner", List.of(), List.of()));
        Node appel = call(bp, "soigner");
        assertNotNull(LOADED.nodes().shape(bp, appel));

        GraphLoader.dropFunction(bp, "soigner");

        assertNull(LOADED.nodes().shape(bp, appel));
    }

    /** Et le squelette du registre reste, lui, indépendant du graphe. */
    @Test
    void leTypeResteEnregistrePourLuiMeme() {
        assertNotNull(LOADED.nodes().shape(FuncNodes.CALL),
                "func/call doit exister dans le registre : sinon chaque appel serait un "
                        + "nœud fantôme, y compris chez l'auteur de la fonction");
    }
}
