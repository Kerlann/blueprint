package fr.blueprint.fabric;

import fr.blueprint.platform.PlatformRegistrar;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

/**
 * Sur Fabric, la fenêtre d'enregistrement est <b>maintenant</b>.
 *
 * <p>Les registres de Minecraft sont ouverts pendant l'initialisation des mods et gelés
 * juste après ; Fabric n'a pas d'événement d'enregistrement, on écrit dedans directement.
 * L'implémentation est donc un appel immédiat — ce qui n'est pas une paresse mais la
 * traduction exacte de ce que fait le chargeur.
 *
 * <p><b>Correct tant que c'est appelé depuis l'initialisation du mod</b>, et de nulle part
 * ailleurs. Appelé plus tard, l'action partirait quand même et lèverait dans
 * {@code Registry.register} — ce qui est le bon comportement : un enregistrement hors
 * fenêtre doit échouer bruyamment, pas être silencieusement ignoré.
 */
public final class FabricRegistrar implements PlatformRegistrar {

    @Override
    public void whenOpen(ResourceKey<? extends Registry<?>> registry, Runnable action) {
        action.run();
    }
}
