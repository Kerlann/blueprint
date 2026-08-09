package fr.blueprint.neoforge;

import fr.blueprint.platform.PlatformPaths;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

/** Les chemins du jeu, vus par NeoForge. */
public final class NeoForgePaths implements PlatformPaths {

    @Override
    public Path gameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }
}
