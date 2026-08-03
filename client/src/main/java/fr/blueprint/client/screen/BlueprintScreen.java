package fr.blueprint.client.screen;

import fr.blueprint.core.graph.screen.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * L'écran d'un blueprint, ouvert en jeu (story 10.3).
 *
 * <p><b>Générique</b> : il ne connaît aucun menu en particulier. Il reçoit la
 * description venue du serveur et la compose — pas une ligne de code par écran, et le
 * <b>même</b> {@link ScreenPainter} que le concepteur (10.2). Deux moteurs de dessin
 * donneraient deux résultats, et l'auteur découvrirait l'écart une fois en partie.
 *
 * <p>L'adaptation ne se contente pas de recentrer : les éléments sont résolus contre la
 * taille RÉELLE de la fenêtre en unités d'interface, ancres et pourcentages compris.
 * Un menu conçu une fois tient donc de 320×180 (1280×720 en <i>GUI scale</i> 4) à
 * 960×540 (3840×2160 en <i>scale</i> 4) — les deux extrêmes réels.
 */
public class BlueprintScreen extends net.minecraft.client.gui.screens.Screen {

    private final Identifier blueprint;
    private final Screen model;
    private final TextureCache textures = new TextureCache();
    private final Runnable onClosed;

    public BlueprintScreen(Identifier blueprint, Screen model, Runnable onClosed) {
        // Le titre du lecteur d'écran : sans lui, l'accessibilité annonce « écran ».
        super(Component.translatable("blueprint.screen.title", model.name()));
        this.blueprint = blueprint;
        this.model = model;
        this.onClosed = onClosed;
    }

    public Identifier blueprintId() {
        return blueprint;
    }

    public Screen model() {
        return model;
    }

    /** Ce que le peintre a besoin de savoir ; les états de survol arrivent en 10.4. */
    protected ScreenPainter.Visuals visuals() {
        return new ScreenPainter.Visuals() {
            @Override
            public boolean textureMissing(Identifier texture) {
                return textures.missing(texture);
            }
        };
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // width/height sont déjà en unités d'interface : c'est la place réelle, et
        // c'est contre elle que se résolvent les pourcentages et les ancres.
        ScreenPainter.paint(graphics, font, model, 0, 0, 1, width, height, visuals());
    }

    /**
     * Le jeu ne se met PAS en pause, comme devant un coffre : le graphe continue de
     * tourner côté serveur pendant que le menu est ouvert, et un solo figé donnerait
     * un affichage qui ne se rafraîchit plus.
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Prévenir le serveur depuis {@code removed} et non {@code onClose} : le premier est
     * appelé sur <b>tous</b> les chemins de fermeture — Échap, mais aussi la mort du
     * joueur, le retour au menu principal, ou un autre écran qui prend la place. Le
     * second ne l'est que sur Échap.
     *
     * <p>La différence n'est pas théorique : le serveur croirait l'écran encore ouvert
     * après une mort, et accepterait un clic dessus venu d'un client modifié (FR52).
     */
    @Override
    public void removed() {
        onClosed.run();
        super.removed();
    }
}
