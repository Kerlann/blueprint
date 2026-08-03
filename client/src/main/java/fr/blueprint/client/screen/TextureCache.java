package fr.blueprint.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Sait quelles textures d'un écran sont absentes (story 10.3, AC4).
 *
 * <p>Avec un cache, parce que la question se pose <b>par élément et par image</b> :
 * interroger le gestionnaire de ressources soixante-quatre fois par frame ferait payer
 * un accès disque potentiel au budget de rendu, pour une réponse qui ne change jamais
 * pendant la vie d'un écran.
 *
 * <p>Le cache vit exactement aussi longtemps que l'écran qui le porte. Un
 * {@code /reload} pendant qu'un menu est ouvert ne le rafraîchit donc pas — le
 * rouvrir suffit, et c'est un compromis assumé : une invalidation globale coûterait un
 * écouteur de rechargement pour un cas qui se répare en fermant la fenêtre.
 */
public final class TextureCache {

    private final Map<Identifier, Boolean> missing = new HashMap<>();

    /**
     * Vrai si la texture est introuvable. Vrai aussi <b>hors du jeu</b> (tests
     * headless) : mieux vaut montrer le damier que blitter une texture inexistante.
     */
    public boolean missing(Identifier texture) {
        return missing.computeIfAbsent(texture, id -> {
            Minecraft client = Minecraft.getInstance();
            if (client == null || client.getResourceManager() == null) {
                return true;
            }
            return client.getResourceManager().getResource(id).isEmpty();
        });
    }

    public void clear() {
        missing.clear();
    }
}
