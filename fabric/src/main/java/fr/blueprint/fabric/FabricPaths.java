package fr.blueprint.fabric;

import fr.blueprint.platform.PlatformPaths;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/** Les chemins du jeu, vus par Fabric. */
public final class FabricPaths implements PlatformPaths {

    @Override
    public Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }
}
