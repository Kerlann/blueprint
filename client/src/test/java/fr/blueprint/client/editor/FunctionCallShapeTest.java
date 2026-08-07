package fr.blueprint.client.editor;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.BlueprintFunction;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.FuncNodes;
import fr.blueprint.core.graph.GraphLoader;
import fr.blueprint.core.graph.Vec2d;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Un nœud d'appel de fonction <b>montre ses pins</b> dans l'éditeur (story 20.2, AC7).
 *
 * <p>La 20.1 a fait résoudre les formes par le blueprint plutôt que par le registre, et a
 * corrigé les appelants de {@code core}. Ceux du client ne l'avaient pas été, faute de quoi
 * les exerçait : un {@code func/call} posé dans l'éditeur n'aurait eu que le squelette du
 * registre — son pin littéral et ses deux pins d'exécution — donc <b>ni paramètres ni
 * sorties</b>. Il se serait dessiné sans ses pins et aurait refusé tout câblage, ce qui
 * donne l'impression que la fonctionnalité entière est cassée alors qu'il ne manquait qu'un
 * argument à sept appels de méthode.
 *
 * <p>Ce test passe par la <b>géométrie</b> — ce que l'éditeur dessine réellement — et non
 * par le résolveur seul : c'est la boîte du nœud, avec ses rangées de pins, qui décide de
 * ce que le joueur voit et de là où il peut cliquer.
 */
class FunctionCallShapeTest {

    private static final PluginLoader.LoadedRegistries LOADED = PluginLoader.load(List.of(), true);

    private static Blueprint withCall() {
        return withCall(
                List.of(new BlueprintFunction.Param("cible", PinTypes.ENTITY),
                        new BlueprintFunction.Param("points", PinTypes.DOUBLE)),
                List.of(new BlueprintFunction.Param("soigne", PinTypes.BOOL)));
    }

    private static Blueprint withCall(List<BlueprintFunction.Param> inputs,
                                      List<BlueprintFunction.Param> outputs) {
        Blueprint bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "call"));
        GraphLoader.addFunction(bp, BlueprintFunction.of("soigner", inputs, outputs));

        UUID call = UUID.randomUUID();
        assertTrue(new EditOperation.AddNode(call, FuncNodes.CALL, new Vec2d(0, 0))
                .apply(bp, LOADED.nodes()).applied());
        assertTrue(new EditOperation.SetLiteral(call, FuncNodes.FUNCTION_PIN,
                LiteralValue.of(PinTypes.STRING, "soigner")).apply(bp, LOADED.nodes()).applied());
        return bp;
    }

    /** <b>Le test qui compte.</b> La boîte dessinée porte les pins de la signature. */
    @Test
    void unAppelSeDessineAvecLesPinsDeSaSignature() {
        Blueprint bp = withCall();
        var boxes = new NodeGeometry().boxes(new GraphView(bp), LOADED.nodes());

        assertEquals(1, boxes.size());

        // La HAUTEUR, et non la présence d'une forme : « func/call » est enregistré, donc
        // le registre en rend toujours une — le squelette, sans paramètres. Une boîte
        // dessinée dessus aurait la même taille quelle que soit la signature, et le joueur
        // verrait un bloc vide. C'est le nombre de rangées de pins qui trahit la
        // différence, et c'est aussi ce sur quoi il clique.
        double avecDeuxParams = boxes.get(0).height();
        double sansParam = new NodeGeometry()
                .boxes(new GraphView(withCall(List.of(), List.of())), LOADED.nodes()).get(0).height();
        assertTrue(avecDeuxParams > sansParam,
                "la boîte ne grandit pas avec la signature (" + avecDeuxParams + " contre "
                        + sansParam + ") : la géométrie résout sa forme par le registre");

        var shape = LOADED.nodes().shape(bp, boxes.get(0).node());
        assertNotNull(shape);
        assertNotNull(shape.input("cible"),
                "le pin « cible » vient de la signature, pas du registre — sans lui, le "
                        + "nœud d'appel est un bloc vide qu'on ne peut pas câbler");
        assertEquals(PinTypes.ENTITY, shape.input("cible").type());
        assertEquals(PinTypes.DOUBLE, shape.input("points").type());
        assertNotNull(shape.output("soigne"), "la sortie déclarée doit être là aussi");
    }

    /**
     * Et un appel vers une fonction absente n'a pas de forme — donc il est fantôme.
     *
     * <p>C'est le comportement voulu : le nœud reste, ses liens restent, et le validateur
     * dit pourquoi. Redéfinir la fonction le fait revivre.
     */
    @Test
    void unAppelVersUneFonctionAbsenteEstFantome() {
        Blueprint bp = withCall();
        GraphLoader.dropFunction(bp, "soigner");

        var node = bp.nodes().values().iterator().next();
        assertEquals(null, LOADED.nodes().shape(bp, node));
    }
}
