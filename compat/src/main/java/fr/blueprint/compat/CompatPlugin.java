package fr.blueprint.compat;

import fr.blueprint.api.BlueprintPlugin;
import fr.blueprint.api.registry.NodeRegistry;
import fr.blueprint.core.registry.NodeRegistryImpl;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Point d'entrée des intégrations (story 8.4) : Blueprint se branche sur lui-même par
 * son propre mécanisme de plugin, déclaré dans l'entrypoint {@code blueprint}.
 *
 * <p>C'est ce qui évite un cycle de modules : {@code compat} voit {@code core}, jamais
 * l'inverse. Et l'isolation d'un plugin en échec, déjà écrite pour les mods tiers
 * (story 2.2), vaut du même coup pour nos propres intégrations.
 */
public final class CompatPlugin implements BlueprintPlugin {

    @Override
    public void registerNodes(NodeRegistry registry) {
        if (!(registry instanceof NodeRegistryImpl nodes)) {
            return;   // registre de test : rien à intégrer
        }
        CompatLoader.load(nodes, modId -> FabricLoader.getInstance().isModLoaded(modId));
    }
}
