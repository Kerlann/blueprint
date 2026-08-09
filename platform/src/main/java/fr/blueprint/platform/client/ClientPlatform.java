package fr.blueprint.platform.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

/**
 * Les deux choses que le chargeur seul peut faire côté client : déclarer une touche, et
 * poser une couche de dessin sur le HUD.
 *
 * <p>Contrairement à {@link fr.blueprint.platform.net.ClientNetwork}, ce paquetage
 * <b>ne peut pas</b> éviter les classes client de Minecraft : on ne déclare pas une touche
 * sans nommer {@link KeyMapping}. Ce fichier n'est donc chargé que côté client — c'est
 * {@code Platform.client()} qui le résout, et seul le module client l'appelle.
 */
public interface ClientPlatform {

    /**
     * Déclare une touche et la rend telle quelle.
     *
     * <p>Rendre la touche plutôt que de la ranger quelque part : c'est l'appelant qui sait
     * ce qu'il veut en faire, et Fabric comme NeoForge rendent la même instance.
     */
    KeyMapping registerKey(KeyMapping mapping);

    /**
     * Pose une couche de dessin <b>au-dessus du HUD vanilla</b>, sous les écrans modaux.
     *
     * <p>L'identifiant n'est pas décoratif : c'est lui qui permet aux autres mods de se
     * situer par rapport à nous dans l'ordre de dessin.
     */
    void registerHudLayer(Identifier id, HudLayer layer);

    /** Ce qu'on dessine. Le compteur de ticks de Fabric n'est pas repris : rien ne s'en sert. */
    @FunctionalInterface
    interface HudLayer {
        void render(GuiGraphics graphics);
    }
}
