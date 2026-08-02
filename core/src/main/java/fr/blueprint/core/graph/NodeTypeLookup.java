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

    /** Aucun type connu : tous les nœuds sont fantômes. */
    NodeTypeLookup EMPTY = typeId -> null;
}
