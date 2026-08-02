package fr.blueprint.core.graph;

import fr.blueprint.api.node.Permission;

/**
 * Métadonnées d'un blueprint. {@code permissionCap} est le plafond : aucun nœud
 * au-dessus de ce niveau ne compile ni ne s'exécute (FR42).
 */
public record BlueprintMeta(String author, String description, String version,
                            Permission permissionCap) {

    public static final BlueprintMeta DEFAULT =
            new BlueprintMeta("", "", "1.0.0", Permission.GAMEPLAY);
}
