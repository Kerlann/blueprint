package fr.blueprint.neoforge.client;

import fr.blueprint.platform.client.ClientPlatform;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Touches et HUD, câblés sur NeoForge — en file, comme le reste.
 *
 * <p>Les deux événements concernés arrivent après la construction du mod, donc après que
 * le code commun a demandé ses touches et sa couche de dessin.
 */
public final class NeoForgeClientPlatform implements ClientPlatform {

    private static final List<KeyMapping> TOUCHES = new ArrayList<>();
    private static final List<CoucheHud> COUCHES = new ArrayList<>();

    private record CoucheHud(Identifier id, HudLayer layer) {
    }

    @Override
    public KeyMapping registerKey(KeyMapping mapping) {
        TOUCHES.add(mapping);
        // Rendue telle quelle, comme sur Fabric : l'appelant la garde pour lire
        // consumeClick(), et l'instance est la même que celle que NeoForge enregistrera.
        return mapping;
    }

    @Override
    public void registerHudLayer(Identifier id, HudLayer layer) {
        COUCHES.add(new CoucheHud(id, layer));
    }

    /** Pose les touches en attente. Appelé sur {@link RegisterKeyMappingsEvent}. */
    public static void flushKeys(RegisterKeyMappingsEvent event) {
        TOUCHES.forEach(event::register);
        TOUCHES.clear();
    }

    /**
     * Pose les couches de HUD. Appelé sur {@link RegisterGuiLayersEvent}.
     *
     * <p>{@code registerAboveAll} : au-dessus du HUD vanilla, sous les écrans modaux —
     * la même couche que {@code HudElementRegistry.addLast} chez Fabric, choisie par la
     * story 10.9.
     */
    public static void flushHud(RegisterGuiLayersEvent event) {
        for (CoucheHud couche : COUCHES) {
            event.registerAboveAll(couche.id(),
                    (graphics, deltaTracker) -> couche.layer().render(graphics));
        }
        COUCHES.clear();
    }
}
