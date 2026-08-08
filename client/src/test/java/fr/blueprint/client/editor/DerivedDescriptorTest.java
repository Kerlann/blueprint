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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
     * <p>Le dessin parcourt les pins du descripteur, le hit-test et la géométrie ceux de la
     * forme d'affichage. Trois listes qui ne coïncident pas donneraient des pins peints à
     * côté de l'endroit où on les attrape — le pire des défauts, parce que tout a l'air
     * correct.
     */
    @Test
    void lOrdreDesRangeesEstLeMemeAuDessinEtAuClic() {
        Blueprint bp = withCarre();
        Node call = poser(bp, FuncNodes.CALL);
        NodeShape shape = EditorShape.display(call, LOADED.nodes().shape(bp, call));
        NodeDescriptor dessine = DerivedDescriptor.withPins(registryDescriptor(FuncNodes.CALL),
                shape);

        assertEquals(shape.inputs().stream().map(NodeShape.PinDef::name).toList(),
                dessine.inputs().stream().map(NodeDescriptor.PinDescriptor::name).toList());
        assertEquals(shape.outputs().stream().map(NodeShape.PinDef::name).toList(),
                dessine.outputs().stream().map(NodeDescriptor.PinDescriptor::name).toList());
    }

    /**
     * <b>Un paramètre scalaire offre sa case ; un objet demande un fil.</b>
     *
     * <p>Un champ de saisie n'apparaît sur une entrée que si elle porte une valeur — c'est
     * ce qui distingue « on peut taper ici » de « il faut brancher quelque chose ». Sans
     * cette valeur, appeler une fonction avec la constante 3 demandait de poser un nœud
     * littéral et de le câbler, là où le nœud d'appel a la place de la case.
     *
     * <p>Et l'inverse compte autant : une entité ne s'écrit pas au clavier, un champ vide y
     * serait une promesse fausse.
     */
    @Test
    void unParametreScalaireOffreSaCaseUnObjetDemandeUnFil() {
        Blueprint bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "cases"));
        GraphLoader.addFunction(bp, BlueprintFunction.of("soigner",
                List.of(new BlueprintFunction.Param("points", PinTypes.DOUBLE),
                        new BlueprintFunction.Param("cible", PinTypes.ENTITY)),
                List.of()));
        Node call = poserVers(bp, FuncNodes.CALL, "soigner");
        NodeDescriptor dessine = DerivedDescriptor.withPins(registryDescriptor(FuncNodes.CALL),
                EditorShape.display(call, LOADED.nodes().shape(bp, call)));

        assertNotNull(pin(dessine, "points").defaultValue(),
                "un nombre se tape : sans valeur, aucune case n'est dessinée");
        assertNull(pin(dessine, "cible").defaultValue(),
                "une entité ne s'écrit pas au clavier — une case vide y promettrait une "
                        + "saisie impossible");
    }

    /**
     * <b>Le pin qui nomme la fonction ne se dessine pas.</b>
     *
     * <p>Le modèle en a besoin : c'est lui qui résout la forme, que le validateur contrôle
     * et que le compilateur lit. L'auteur, lui, n'a rien à en faire — il l'a choisie en
     * posant le nœud, et le titre la lui redit. Dessiné, il donne une ligne « function »
     * avec un champ de saisie au-dessus du pin d'exécution : la seule ligne du nœud qu'il ne
     * faut pas toucher, et la première qu'on voit.
     */
    @Test
    void lePinQuiNommeLaFonctionNeSeDessinePas() {
        Blueprint bp = withCarre();
        Node call = poser(bp, FuncNodes.CALL);

        NodeShape complete = LOADED.nodes().shape(bp, call);
        assertNotNull(complete.input(FuncNodes.FUNCTION_PIN),
                "le modèle garde le pin : c'est lui qui rattache le nœud à sa fonction");

        NodeShape affichee = EditorShape.display(call, complete);
        assertNull(affichee.input(FuncNodes.FUNCTION_PIN));
        assertEquals(complete.inputs().size() - 1, affichee.inputs().size(),
                "une seule ligne en moins — et surtout pas les paramètres avec");
        assertEquals(complete.outputs(), affichee.outputs());
    }

    /** Un nœud ordinaire n'est pas filtré : la même instance ressort. */
    @Test
    void unNoeudOrdinaireTraverseLeFiltreIntact() {
        Blueprint bp = withCarre();
        UUID id = UUID.randomUUID();
        Identifier log = Identifier.fromNamespaceAndPath("blueprint", "debug/log");
        assertTrue(new fr.blueprint.core.graph.EditOperation.AddNode(id, log, new Vec2d(0, 0))
                .apply(bp, LOADED.nodes()).applied());
        NodeShape shape = LOADED.nodes().shape(bp, bp.node(id));

        assertSame(shape, EditorShape.display(bp.node(id), shape),
                "rien à filtrer : ne pas reconstruire une forme par nœud visible et par "
                        + "image");
    }

    private static NodeDescriptor.PinDescriptor pin(NodeDescriptor desc, String name) {
        return desc.inputs().stream().filter(p -> p.name().equals(name))
                .findFirst().orElseThrow();
    }

    private static Node poserVers(Blueprint bp, Identifier typeId, String function) {
        UUID id = UUID.randomUUID();
        assertTrue(new fr.blueprint.core.graph.EditOperation.AddNode(id, typeId,
                new Vec2d(0, 0)).apply(bp, LOADED.nodes()).applied());
        assertTrue(new fr.blueprint.core.graph.EditOperation.SetLiteral(id,
                FuncNodes.FUNCTION_PIN,
                fr.blueprint.api.pin.LiteralValue.of(PinTypes.STRING, function))
                .apply(bp, LOADED.nodes()).applied());
        return bp.node(id);
    }
}
