package fr.blueprint.core.net;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Quel écran chaque joueur a ouvert (story 10.3, AC1 et AC5).
 *
 * <p><b>Un seul à la fois.</b> Empiler les écrans poserait aussitôt les questions du
 * focus, de la fermeture en cascade et de la pile à restaurer après une déconnexion.
 * Un menu à plusieurs pages se fait avec UN écran dont on change le contenu (10.4) —
 * c'est aussi ce que font les interfaces vanilla.
 *
 * <p>C'est la table que le serveur consultera pour vérifier un clic (FR52) : il ne
 * croit jamais le client sur l'écran qu'il prétend avoir ouvert. Sans elle, n'importe
 * quel client modifié déclencherait le bouton d'un menu qu'il n'a jamais vu.
 *
 * <p>Pure et testable headless : elle ne connaît que des UUID de joueurs.
 */
public final class ScreenSessions {

    /** L'écran ouvert : de quel blueprint, et lequel. */
    public record Open(Identifier blueprint, String screen) {
    }

    private final Map<UUID, Open> open = new HashMap<>();

    /** Note l'ouverture, en remplaçant ce que le joueur avait — jamais en empilant. */
    public void opened(UUID player, Identifier blueprint, String screen) {
        open.put(player, new Open(blueprint, screen));
    }

    public @Nullable Open of(UUID player) {
        return open.get(player);
    }

    /**
     * Le joueur a-t-il CET écran ouvert ? La question que pose chaque clic reçu : le
     * client annonce ce qu'il veut, seul le serveur sait ce qu'il a envoyé.
     */
    public boolean hasOpen(UUID player, Identifier blueprint, String screen) {
        Open current = open.get(player);
        return current != null && current.blueprint().equals(blueprint)
                && current.screen().equals(screen);
    }

    /** Ferme, et dit si quelque chose était bien ouvert. */
    public boolean closed(UUID player) {
        return open.remove(player) != null;
    }

    /**
     * Referme tous les écrans d'un blueprint — il vient d'être désactivé, rechargé ou
     * supprimé. Rend les joueurs concernés, à qui il faut envoyer la fermeture.
     *
     * <p>Sans cela, un menu resterait affiché en pointant un blueprint qui n'existe
     * plus : chaque clic serait refusé sans que le joueur comprenne pourquoi.
     */
    public java.util.List<UUID> closeAllOf(Identifier blueprint) {
        java.util.List<UUID> affected = new java.util.ArrayList<>();
        open.entrySet().removeIf(entry -> {
            if (entry.getValue().blueprint().equals(blueprint)) {
                affected.add(entry.getKey());
                return true;
            }
            return false;
        });
        return affected;
    }

    /** Un joueur parti ne laisse pas d'écran fantôme (AC5). */
    public void forget(UUID player) {
        open.remove(player);
    }

    public int size() {
        return open.size();
    }
}
