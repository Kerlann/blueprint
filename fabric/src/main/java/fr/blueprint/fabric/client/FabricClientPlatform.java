package fr.blueprint.fabric.client;

import fr.blueprint.platform.client.ClientPlatform;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** Touches et HUD, câblés sur Fabric. */
public final class FabricClientPlatform implements ClientPlatform {

    @Override
    public KeyMapping registerKey(KeyMapping mapping) {
        return KeyBindingHelper.registerKeyBinding(mapping);
    }

    @Override
    public void registerHudLayer(Identifier id, HudLayer layer) {
        // addLast : au-dessus du HUD vanilla, sous les écrans modaux. C'est la couche que
        // la story 10.9 a choisie, et elle ne change pas parce qu'on l'appelle d'ailleurs.
        HudElementRegistry.addLast(id, (graphics, tickCounter) -> layer.render(graphics));
    }
}
