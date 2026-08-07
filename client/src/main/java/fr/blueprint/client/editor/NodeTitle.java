package fr.blueprint.client.editor;

import fr.blueprint.core.graph.FuncNodes;
import fr.blueprint.core.graph.Node;
import fr.blueprint.core.registry.NodeDescriptor;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Le titre qu'un nœud <b>montre</b> — pas celui que porte son type.
 *
 * <p>Trois appels de fonction côte à côte lisaient tous « Appeler une fonction ». Le nom de
 * la fonction, qui est la seule chose qui les distingue, n'apparaissait nulle part : il
 * fallait sélectionner chaque nœud et regarder le panneau de détails pour savoir lequel fait
 * quoi. C'est le défaut que le graphe est censé éviter.
 *
 * <p>Le précédent est celui des pastilles de variables, qui affichent depuis toujours le nom
 * de la variable plutôt que « Obtenir une variable ». La règle est la même : quand un nœud
 * est <b>lié</b> à un membre du blueprint, c'est ce membre qu'on lit.
 *
 * <p>Pur, et séparé du peintre : c'est un choix de texte, il se vérifie sans fenêtre. Le nom
 * ne passe jamais par {@code I18n.get} : une fonction nommée {@code gui.done} s'afficherait
 * « Terminé ».
 */
public final class NodeTitle {

    private NodeTitle() {
    }

    /** @param translate clé → texte, injecté pour rester vérifiable sans jeu lancé. */
    public static String of(Node node, @Nullable NodeDescriptor desc,
                            Function<String, String> translate) {
        if (desc == null) {
            return node.typeId().toString();   // fantôme : dire quel type manque
        }
        String bound = FuncNodes.isFunctionNode(node.typeId())
                ? FuncNodes.boundName(node) : null;
        if (bound == null || bound.isBlank()) {
            return translate.apply(desc.titleKey());
        }
        // Le nom NU, comme Unreal : un appel de « carre » s'intitule « carre ». Le
        // pictogramme ƒ et la couleur de la catégorie disent déjà que c'est une fonction,
        // et « Appeler » répété sur chaque nœud ne fait que voler la place du seul mot qui
        // distingue les appels entre eux.
        if (!FuncNodes.RESULT.equals(node.typeId())) {
            return bound;
        }
        // La sortie d'un corps est le « Return Node » d'Unreal, et elle ne porte PAS le nom
        // de sa fonction : on est déjà dedans, la répéter n'apprend rien. Ce qu'on a besoin
        // de lire, c'est que le flux s'arrête là.
        return translate.apply("blueprint.editor.node.func_return");
    }
}
