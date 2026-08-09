package fr.blueprint.platform;

import java.nio.file.Path;

/**
 * Les deux dossiers que le chargeur nomme, et que Minecraft seul ne dit pas.
 *
 * <p>Volontairement réduit à ces deux-là : tout le reste de l'arborescence de Blueprint
 * se dérive du premier, et c'est {@code BlueprintPaths} qui en décide. Une interface qui
 * exposerait {@code exports()} ou {@code content()} ferait descendre une décision de
 * rangement — donc de produit — dans la couche plateforme, où chaque chargeur pourrait
 * en donner une version différente sans que rien ne s'en aperçoive.
 */
public interface PlatformPaths {

    /** La racine du jeu : {@code .minecraft/} en jeu, {@code run/} en développement. */
    Path gameDir();

    /** Le dossier des réglages, {@code config/}. Ne sert plus qu'à la reprise (§ BlueprintPaths). */
    Path configDir();
}
