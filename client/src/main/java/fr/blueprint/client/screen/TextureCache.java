package fr.blueprint.client.screen;

import fr.blueprint.client.pack.PackTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Sait quelles textures d'un écran sont absentes (story 10.3, AC4) — et, depuis la
 * story 10.5, <b>de quel pack</b> elles auraient dû venir.
 *
 * <p>Avec un cache, parce que la question se pose <b>par élément et par image</b> :
 * interroger le gestionnaire de ressources soixante-quatre fois par frame ferait payer
 * un accès disque potentiel au budget de rendu, pour une réponse qui ne change jamais
 * pendant la vie d'un écran.
 *
 * <p>Une exception : le rechargement des packs. Là, la réponse change exprès — c'est tout
 * l'intérêt du geste. Le cache suit donc la génération de {@link PackTextures} et se vide
 * de lui-même : sans cela, {@code /blueprint-packs reload} n'aurait aucun effet visible
 * tant qu'un menu reste ouvert, ce qui est précisément le moment où on l'utilise.
 */
public final class TextureCache {

    private final Map<Identifier, Boolean> missing = new HashMap<>();
    private int generation = -1;

    /**
     * Vrai si la texture est introuvable. Vrai aussi <b>hors du jeu</b> (tests
     * headless) : mieux vaut montrer le damier que blitter une texture inexistante.
     */
    public boolean missing(Identifier texture) {
        refreshIfPacksChanged();
        return missing.computeIfAbsent(texture, id -> {
            // Une texture de pack ne vit pas dans le gestionnaire de RESSOURCES : elle
            // est téléversée directement sur la carte. L'interroger là aurait déclaré
            // absente chaque image d'un pack pourtant chargé.
            if (PackTextures.isPackTexture(id)) {
                return PackTextures.missingPackOf(id) != null;
            }
            Minecraft client = Minecraft.getInstance();
            if (client == null || client.getResourceManager() == null) {
                return true;
            }
            return client.getResourceManager().getResource(id).isEmpty();
        });
    }

    /** Le pack absent pour cette texture, ou {@code null} — le remplaçant le nomme. */
    public @Nullable String missingPack(Identifier texture) {
        refreshIfPacksChanged();
        return PackTextures.missingPackOf(texture);
    }

    private void refreshIfPacksChanged() {
        int current = PackTextures.generation();
        if (current != generation) {
            generation = current;
            missing.clear();
        }
    }

    public void clear() {
        missing.clear();
    }
}
