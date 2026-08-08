package fr.blueprint.client.editor;

import fr.blueprint.core.graph.FuncNodes;
import fr.blueprint.core.graph.Node;
import fr.blueprint.core.graph.NodeShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * La forme d'un nœud <b>telle que l'éditeur la montre</b> — sans sa plomberie.
 *
 * <p>Un nœud de fonction porte un pin {@code function} qui nomme la fonction à laquelle il
 * se rattache. Le modèle en a besoin : c'est lui qui résout la forme, que le validateur
 * contrôle et que le compilateur lit. L'auteur, lui, n'a rien à en faire — il l'a choisie en
 * posant le nœud, et le titre la lui redit. Dessiné, ce pin devient une ligne « function »
 * avec un champ de saisie au-dessus du pin d'exécution : la seule ligne du nœud qu'il ne
 * faut pas toucher, et la première qu'on voit.
 *
 * <p>Le filtre s'applique au <b>dessin, à la géométrie et au clic à la fois</b>. Ce point
 * n'est pas négociable : la boîte tire sa hauteur du nombre de rangées et le hit-test vise
 * une rangée par son indice. Retirer la ligne d'un seul des trois décalerait les pins d'un
 * cran par rapport à l'endroit où on les attrape — un défaut qui ne se voit pas, parce que
 * tout a l'air correct.
 *
 * <p>Les opérations, elles, gardent la forme complète : {@code SetLiteral} sur
 * {@code function} est exactement ce que fait la pose d'un appel.
 */
public final class EditorShape {

    private EditorShape() {
    }

    /**
     * {@code shape} sans les pins de plomberie, ou {@code shape} telle quelle.
     *
     * <p>Rend l'objet d'origine quand il n'y a rien à retirer : c'est le cas de tous les
     * nœuds sauf trois, et la géométrie appelle cette méthode pour chaque nœud visible à
     * chaque image.
     */
    public static @Nullable NodeShape display(Node node, @Nullable NodeShape shape) {
        if (shape == null || !FuncNodes.isFunctionNode(node.typeId())) {
            return shape;
        }
        List<NodeShape.PinDef> inputs = new ArrayList<>(shape.inputs().size());
        for (NodeShape.PinDef def : shape.inputs()) {
            if (!FuncNodes.FUNCTION_PIN.equals(def.name())) {
                inputs.add(def);
            }
        }
        if (inputs.size() == shape.inputs().size()) {
            return shape;
        }
        return new NodeShape(inputs, shape.outputs(), shape.entryPoint(), shape.permission());
    }
}
