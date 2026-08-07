package fr.blueprint.core.graph;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Résolution d'un identifiant de nœud vers sa forme (pins, permission, drapeaux).
 * Raccord provisoire : la story 2.2 fera implémenter cette interface par le
 * {@code NodeRegistry} réel. Volontairement dans {@code core}, pas dans {@code api} —
 * les mods tiers passeront par {@code NodeType}, pas par cette vue interne.
 */
public interface NodeTypeLookup {

    /** La forme du type, ou null si l'identifiant est inconnu (→ nœud fantôme). */
    @Nullable NodeShape shape(Identifier typeId);

    /**
     * La forme d'un nœud <b>dans son graphe</b>.
     *
     * <p>Presque toujours celle de son type, et le registre suffit. Un nœud d'appel de
     * fonction (story 20.1) fait exception : sa forme dépend d'une signature déclarée dans
     * le blueprint, et deux graphes peuvent définir {@code soigner} avec deux signatures
     * différentes. Le cache du registre, qui est mémoïsé <b>par identifiant</b>, rendrait
     * alors la forme de l'un à l'autre.
     *
     * <p>La forme d'appel n'est pas construite ici mais lue sur la fonction, où elle est
     * calculée une fois à l'édition : l'éditeur en réclame une par nœud et par image.
     *
     * <p>Un appel dont le nom ne résout pas rend {@code null} — le même signal qu'un type
     * inconnu, et ce qu'attendent les appelants existants. La <b>raison</b> du refus, elle,
     * est le travail du validateur, qui distingue les deux et le dit.
     */
    default @Nullable NodeShape shape(Blueprint bp, Node node) {
        if (!FuncNodes.isFunctionNode(node.typeId())) {
            return shape(node.typeId());
        }
        BlueprintFunction function = FuncNodes.boundFunction(bp, node);
        if (function == null) {
            return null;
        }
        if (FuncNodes.CALL.equals(node.typeId())) {
            return function.callShape();
        }
        return FuncNodes.PARAM.equals(node.typeId())
                ? function.paramShape() : function.resultShape();
    }

    /** Aucun type connu : tous les nœuds sont fantômes. */
    NodeTypeLookup EMPTY = typeId -> null;
}
