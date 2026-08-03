package fr.blueprint.client.screen;

import fr.blueprint.core.graph.screen.Screen;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

/**
 * Le dessin des HUD de blueprint (story 10.9), branché dans la couche prévue par
 * Fabric — au-dessus du monde, sous les écrans modaux.
 *
 * <p>C'est le <b>même peintre</b> que le concepteur et que les écrans modaux : un HUD
 * n'est pas un second moteur de rendu, seulement une autre manière de montrer les
 * mêmes éléments.
 *
 * <p>Il est dessiné à <b>chaque image</b>, contrairement à un menu qu'on regarde deux
 * secondes. Rien n'est calculé ici que la géométrie — pas d'allocation par élément, pas
 * de requête au serveur, pas de relecture du modèle.
 */
public final class BlueprintHud {

    private static final HudView VIEW = new HudView();
    private static final TextureCache TEXTURES = new TextureCache();

    private BlueprintHud() {
    }

    public static HudView view() {
        return VIEW;
    }

    public static void register() {
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("blueprint", "screens"),
                (graphics, tickCounter) -> render(graphics));
    }

    private static void render(GuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        // Le HUD se dessine SOUS les écrans, comme la barre de vie derrière
        // l'inventaire. Seule exception : un menu Blueprint, où l'un et l'autre se
        // disputeraient la même place et les mêmes couleurs.
        if (client == null || client.screen instanceof BlueprintScreen
                || VIEW.visible().isEmpty()) {
            return;
        }
        for (Screen screen : VIEW.visible()) {
            ScreenPainter.paint(graphics, client.font, screen, 0, 0, 1,
                    graphics.guiWidth(), graphics.guiHeight(), new ScreenPainter.Visuals() {
                        @Override
                        public boolean textureMissing(Identifier texture) {
                            return TEXTURES.missing(texture);
                        }

                        @Override
                        public double progress(String element) {
                            return VIEW.progressOf(screen.name(), element);
                        }
                    });
        }
    }
}
