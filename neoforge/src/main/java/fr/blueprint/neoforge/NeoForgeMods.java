package fr.blueprint.neoforge;

import fr.blueprint.platform.PlatformMods;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/**
 * Ce que NeoForge sait des mods présents.
 *
 * <p>Deux différences avec Fabric, et elles étaient annoncées :
 *
 * <ul>
 *   <li><b>Aucun entrypoint.</b> {@link #plugins()} rend une liste vide, et ce n'est pas
 *       un manque : la découverte des plugins passe ici entièrement par
 *       {@link java.util.ServiceLoader}, que {@code PluginLoader} interroge de son côté.
 *       C'est tout l'objet du lot C — sans lui, ce fichier n'aurait rien à rendre.</li>
 *   <li><b>Les propriétés du mod</b> remplacent les valeurs personnalisées du
 *       {@code fabric.mod.json}, sous la même clé.</li>
 * </ul>
 */
public final class NeoForgeMods implements PlatformMods {

    /** La clé, dans {@code [modproperties.<modid>]} du {@code neoforge.mods.toml}. */
    private static final String HOLDERS_KEY = "blueprint:node_holders";

    @Override
    public boolean isLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public List<ModPlugin> plugins() {
        // Voir le javadoc de la classe : sur NeoForge, tout passe par ServiceLoader.
        return List.of();
    }

    @Override
    public List<ModNodeHolders> nodeHolders() {
        List<ModNodeHolders> out = new ArrayList<>();
        for (var mod : ModList.get().getMods()) {
            Object value = mod.getModProperties().get(HOLDERS_KEY);
            if (!(value instanceof List<?> declared)) {
                continue;
            }
            List<String> classNames = new ArrayList<>();
            for (Object entry : declared) {
                if (entry instanceof String className) {
                    classNames.add(className);
                }
            }
            if (!classNames.isEmpty()) {
                out.add(new ModNodeHolders(mod.getModId(), classNames));
            }
        }
        return out;
    }
}
