package fr.blueprint.core.graph;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Les nœuds de variables (story 5.5). Ils n'ont pas d'action : le compilateur les
 * abaisse en {@code LoadVar}/{@code StoreVar}, et le validateur vérifie que leur pin
 * littéral {@code var} nomme une variable existante. Constantes partagées entre
 * validateur, compilateur et éditeur — une seule source de vérité.
 */
public final class VarNodes {

    public static final Identifier GET = Identifier.fromNamespaceAndPath("blueprint", "var/get");
    public static final Identifier SET = Identifier.fromNamespaceAndPath("blueprint", "var/set");

    /** Le pin littéral qui nomme la variable liée. */
    public static final String VAR_PIN = "var";

    private VarNodes() {
    }

    public static boolean isVarNode(Identifier typeId) {
        return GET.equals(typeId) || SET.equals(typeId);
    }

    /** Le nom de variable lié à ce nœud, ou null (littéral absent ou pas une chaîne). */
    public static @Nullable String boundName(Node node) {
        var literal = node.literal(VAR_PIN);
        return literal != null && literal.value() instanceof String s && !s.isEmpty() ? s : null;
    }

    /** La variable liée, ou null si le nom ne résout pas. */
    public static @Nullable Variable boundVariable(Blueprint bp, Node node) {
        String name = boundName(node);
        return name == null ? null : bp.variables().get(name);
    }
}
