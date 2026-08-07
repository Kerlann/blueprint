package fr.blueprint.core.graph;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Les nœuds de fonction (story 20.1). Sur le modèle exact de {@link VarNodes} : un
 * <b>seul</b> type enregistré, portant un littéral qui nomme la cible.
 *
 * <h2>Pourquoi un seul type, et non un par fonction</h2>
 *
 * <p>Un type {@code blueprint:func/soigner} par fonction paraît naturel et coûte trois
 * choses, toutes invisibles jusqu'à ce qu'elles mordent :
 *
 * <ul>
 *   <li><b>La synchronisation de registre.</b> Le registre des nœuds est comparé et
 *       synchronisé à la connexion. Y verser un type par fonction ferait diverger deux
 *       serveurs qui n'ont pas les mêmes blueprints — alors que les blueprints, eux,
 *       voyagent déjà.</li>
 *   <li><b>Des fantômes en trop.</b> Un type qu'aucun registre ne connaît est un nœud
 *       fantôme (FR40) chez <b>tout le monde</b>, y compris chez l'auteur qui vient de
 *       définir la fonction.</li>
 *   <li><b>Un troisième chemin de diagnostic.</b> « Ce nom ne désigne rien » est déjà
 *       écrit pour les variables et pour les liaisons d'écran. Un troisième finirait par
 *       s'en écarter.</li>
 * </ul>
 *
 * <p>Une fonction supprimée n'est donc <b>pas</b> un nœud fantôme : le type de l'appel
 * reste parfaitement connu, c'est son argument qui ne résout plus — exactement comme un
 * {@code var/get} sur une variable supprimée.
 */
public final class FuncNodes {

    /** L'unique type d'appel. Sa forme réelle vient du blueprint, pas du registre. */
    public static final Identifier CALL =
            Identifier.fromNamespaceAndPath("blueprint", "func/call");

    /** Le pin littéral qui nomme la fonction appelée. */
    public static final String FUNCTION_PIN = "function";

    private FuncNodes() {
    }

    public static boolean isCall(Identifier typeId) {
        return CALL.equals(typeId);
    }

    /** Le nom de fonction lié à ce nœud, ou null (littéral absent, vide, ou pas une chaîne). */
    public static @Nullable String boundName(Node node) {
        if (!isCall(node.typeId())) {
            return null;
        }
        var literal = node.literal(FUNCTION_PIN);
        return literal != null && literal.value() instanceof String s && !s.isEmpty() ? s : null;
    }

    /** La fonction liée, ou null si le nom ne résout pas. */
    public static @Nullable BlueprintFunction boundFunction(Blueprint bp, Node node) {
        String name = boundName(node);
        return name == null ? null : bp.functions().get(name);
    }
}
