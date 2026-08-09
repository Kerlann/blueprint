package fr.blueprint.client.screen;

import fr.blueprint.core.graph.screen.Screen;
import fr.blueprint.platform.Platform;
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
        Platform.client().registerHudLayer(
                Identifier.fromNamespaceAndPath("blueprint", "screens"),
                BlueprintHud::render);
    }

    /**
     * Dispositions résolues, par écran affiché.
     *
     * <p>Le HUD appelait {@code ScreenPainter.paint} sans table, ce qui refaisait la passe
     * complète de {@code ScreenLayout.solve} <b>à chaque image</b> — alors que le menu
     * modal ({@code BlueprintScreen.layout()}) et le concepteur
     * ({@code ScreenCanvasController.rects()}) la mémorisent tous deux, sur l'identité de
     * l'instance {@code Screen}. Le patron était à deux fichiers d'ici.
     *
     * <p>Sur l'<b>identité</b> et non l'égalité : {@code HudView.show()} remplace
     * l'instance à chaque mise à jour reçue du serveur, donc une instance inchangée signifie
     * un écran inchangé. L'invalidation est exacte par construction, sans avoir à comparer
     * quoi que ce soit.
     */
    private static final java.util.Map<Screen, java.util.Map<String,
            fr.blueprint.core.graph.screen.ScreenLayout.Rect>> LAYOUTS =
            new java.util.IdentityHashMap<>();

    /** Les écrans du dernier passage et la taille de fenêtre qui a servi à les résoudre. */
    private static Screen[] lastVisible = new Screen[0];
    private static int lastWidth = -1;
    private static int lastHeight = -1;

    /**
     * Le peintre reçoit UN visuel, réemployé — il en allouait un anonyme par écran et par
     * image, uniquement pour capturer l'écran courant dans {@code progress}.
     */
    private static final class HudVisuals implements ScreenPainter.Visuals {
        private Screen current;

        @Override
        public boolean textureMissing(Identifier texture) {
            return TEXTURES.missing(texture);
        }

        @Override
        public String missingPack(Identifier texture) {
            return TEXTURES.missingPack(texture);
        }

        @Override
        public double progress(String element) {
            return VIEW.progressOf(current.name(), element);
        }
    }

    private static final HudVisuals VISUALS = new HudVisuals();

    /** Vide le cache si l'ensemble affiché ou la taille de fenêtre a changé. */
    private static void syncCache(java.util.List<Screen> visible, int width, int height) {
        boolean same = width == lastWidth && height == lastHeight
                && visible.size() == lastVisible.length;
        for (int i = 0; same && i < lastVisible.length; i++) {
            same = visible.get(i) == lastVisible[i];   // identité, pas égalité
        }
        if (same) {
            return;
        }
        LAYOUTS.clear();
        lastVisible = visible.toArray(new Screen[0]);
        lastWidth = width;
        lastHeight = height;
    }

    private static void render(GuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        // UN seul appel : visible() rend une copie de liste, et on en demandait deux par
        // image — une pour le test de vacuité, une pour la boucle.
        java.util.List<Screen> visible = VIEW.visible();
        // Le HUD se dessine SOUS les écrans, comme la barre de vie derrière
        // l'inventaire. Seule exception : un menu Blueprint, où l'un et l'autre se
        // disputeraient la même place et les mêmes couleurs.
        if (client == null || client.screen instanceof BlueprintScreen || visible.isEmpty()) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        syncCache(visible, width, height);
        for (Screen screen : visible) {
            var rects = LAYOUTS.computeIfAbsent(screen,
                    s -> fr.blueprint.core.graph.screen.ScreenLayout.solve(s, width, height));
            VISUALS.current = screen;
            ScreenPainter.paint(graphics, client.font, screen, rects, 0, 0, 1, VISUALS);
        }
    }
}
