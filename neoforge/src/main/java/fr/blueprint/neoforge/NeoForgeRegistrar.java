package fr.blueprint.neoforge;

import fr.blueprint.platform.PlatformRegistrar;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * La fenêtre d'enregistrement, côté NeoForge — et c'est ici qu'on voit ce que le lot B
 * achetait.
 *
 * <p>Chez Fabric, {@code whenOpen} exécute tout de suite. Ici, il <b>met en file</b> :
 * NeoForge n'ouvre ses registres que pendant {@link RegisterEvent}, un par un, et à un
 * moment que le mod ne choisit pas. Le code commun n'a rien à savoir de cette différence
 * — c'est exactement pour ça que la question a été posée dans {@code platform} plutôt que
 * résolue dans {@code core}.
 *
 * <p>L'ordre à l'intérieur d'un registre est préservé (liste, pas ensemble) : c'est ce
 * qui fait tenir la numérotation réseau, et {@code ContentRegistrar.itemOrder} en dépend.
 */
public final class NeoForgeRegistrar implements PlatformRegistrar {

    /** Les actions en attente, par registre, dans l'ordre où elles ont été programmées. */
    private static final Map<ResourceKey<? extends Registry<?>>, List<Runnable>> EN_ATTENTE =
            new LinkedHashMap<>();

    @Override
    public void whenOpen(ResourceKey<? extends Registry<?>> registry, Runnable action) {
        synchronized (EN_ATTENTE) {
            EN_ATTENTE.computeIfAbsent(registry, key -> new ArrayList<>()).add(action);
        }
    }

    /**
     * Vide la file de ce registre. Appelé par {@link BlueprintNeoForge} sur
     * {@link RegisterEvent}.
     *
     * <p>Les actions appellent {@code Registry.register} directement, comme sur Fabric :
     * pendant {@code RegisterEvent}, NeoForge a dégelé le registre concerné, et l'écriture
     * directe y est donc légale. C'est ce qui permet à {@code ContentRegistrar} d'être
     * écrit une seule fois pour les deux chargeurs.
     */
    static void flush(RegisterEvent event) {
        List<Runnable> actions;
        synchronized (EN_ATTENTE) {
            actions = EN_ATTENTE.remove(event.getRegistryKey());
        }
        if (actions == null) {
            return;
        }
        actions.forEach(Runnable::run);
    }
}
