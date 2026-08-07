package fr.blueprint.client.editor;

import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.PinKind;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.BlueprintFunction;
import fr.blueprint.core.graph.FuncNodes;
import fr.blueprint.core.graph.GraphLoader;
import fr.blueprint.core.graph.Node;
import fr.blueprint.core.graph.NodeShape;
import fr.blueprint.core.graph.Vec2d;
import fr.blueprint.core.registry.NodeDescriptor;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Un nœud de fonction <b>montre les pins de sa signature</b>, pas ceux du registre
 * (story 20.2, AC7).
 *
 * <p>Le contrôleur avait été corrigé, le <b>dessin</b> non : la story ne comptait que les
 * sept appels de {@code CanvasController}, et les descripteurs sont un second chemin, qui ne
 * passe pas par {@code NodeShape}. Résultat en jeu : un nœud à la bonne taille — la
 * géométrie, elle, lisait la bonne forme — dont les pins n'apparaissaient nulle part, et des
 * liens qu'on posait à l'aveugle.
 */
class DerivedDescriptorTest {

    private static final PluginLoader.LoadedRegistries LOADED = PluginLoader.load(List.of(), true);

    private static Blueprint withCarre() {
        Blueprint bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "desc"));
        GraphLoader.addFunction(bp, BlueprintFunction.of("carre",
                List.of(new BlueprintFunction.Param("n", PinTypes.DOUBLE)),
                List.of(new BlueprintFunction.Param("r", PinTypes.DOUBLE))));
        return bp;
    }

    /** Pose un nœud de fonction lié à « carre » dans le graphe, et le rend. */
    private static Node poser(Blueprint bp, Identifier typeId) {
        UUID id = UUID.randomUUID();
        assertTrue(new fr.blueprint.core.graph.EditOperation.AddNode(id, typeId,
                new Vec2d(0, 0)).apply(bp, LOADED.nodes()).applied());
        assertTrue(new fr.blueprint.core.graph.EditOperation.SetLiteral(id,
                FuncNodes.FUNCTION_PIN,
                fr.blueprint.api.pin.LiteralValue.of(PinTypes.STRING, "carre"))
                .apply(bp, LOADED.nodes()).applied());
        return bp.node(id);
    }

    private static NodeDescriptor registryDescriptor(Identifier typeId) {
        return LOADED.nodes().get(typeId).map(NodeDescriptor::of).orElseThrow();
    }

    /**
     * <b>Le squelette du registre n'a pas les paramètres ; la forme, si.</b>
     *
     * <p>La première moitié du test est ce qui rend la seconde utile : sans elle, un
     * registre qui porterait déjà les paramètres ferait passer le test sans rien prouver.
     */
    @Test
    void unAppelMontreLesPinsDeSaSignature() {
        Blueprint bp = withCarre();
        Node call = poser(bp, FuncNodes.CALL);

        NodeDescriptor registre = registryDescriptor(FuncNodes.CALL);
        assertTrue(registre.inputs().stream().noneMatch(p -> p.name().equals("n")),
                "le registre ne peut PAS connaître les paramètres : ils vivent dans le "
                        + "blueprint — c'est toute la raison de cette classe");

        NodeShape shape = LOADED.nodes().shape(bp, call);
        assertNotNull(shape);
        NodeDescriptor dessine = DerivedDescriptor.withPins(registre, shape);

        assertTrue(dessine.inputs().stream().anyMatch(p -> p.name().equals("n")
                        && p.kind() == PinKind.DATA),
                "sans ce pin, l'auteur câble vers un point qui n'est pas dessiné");
        assertTrue(dessine.outputs().stream().anyMatch(p -> p.name().equals("r")));
        assertEquals(registre.titleKey(), dessine.titleKey(),
                "seuls les pins viennent du blueprint ; le reste reste celui du registre");
        assertEquals(registre.permission(), dessine.permission());
    }

    /**
     * <b>Les deux bords du corps aussi.</b>
     *
     * <p>C'est là que le défaut se voyait le plus : le nœud d'entrée d'une fonction à un
     * paramètre se dessinait vide, donc on ne pouvait pas relier le paramètre à quoi que ce
     * soit dans son propre corps.
     */
    @Test
    void lesBordsDuCorpsMontrentAussiLeursPins() {
        Blueprint bp = withCarre();
        Node entree = poser(bp, FuncNodes.PARAM);

        NodeShape shape = LOADED.nodes().shape(bp, entree);
        assertNotNull(shape);
        NodeDescriptor dessine =
                DerivedDescriptor.withPins(registryDescriptor(FuncNodes.PARAM), shape);

        assertTrue(dessine.outputs().stream().anyMatch(p -> p.name().equals("n")),
                "le paramètre SORT du nœud d'entrée : c'est par là qu'il entre dans le corps");

        Node sortie = poser(bp, FuncNodes.RESULT);
        NodeDescriptor fin = DerivedDescriptor.withPins(
                registryDescriptor(FuncNodes.RESULT), LOADED.nodes().shape(bp, sortie));

        assertTrue(fin.inputs().stream().anyMatch(p -> p.name().equals("r")),
                "le résultat ENTRE dans le nœud de sortie : c'est là qu'on le pose");
    }

    /**
     * Les rangées dessinées sont <b>celles que le clic vise</b>.
     *
     * <p>Le dessin parcourt les pins du descripteur, le hit-test ceux de la forme. Deux
     * listes dans des ordres différents donneraient des pins peints à côté de l'endroit où
     * on les attrape — le pire des deux mondes, parce que tout a l'air correct.
     */
    @Test
    void lOrdreDesRangeesEstLeMemeAuDessinEtAuClic() {
        Blueprint bp = withCarre();
        Node call = poser(bp, FuncNodes.CALL);
        NodeShape shape = LOADED.nodes().shape(bp, call);
        NodeDescriptor dessine = DerivedDescriptor.withPins(registryDescriptor(FuncNodes.CALL),
                shape);

        assertEquals(shape.inputs().stream().map(NodeShape.PinDef::name).toList(),
                dessine.inputs().stream().map(NodeDescriptor.PinDescriptor::name).toList());
        assertEquals(shape.outputs().stream().map(NodeShape.PinDef::name).toList(),
                dessine.outputs().stream().map(NodeDescriptor.PinDescriptor::name).toList());
    }
}
