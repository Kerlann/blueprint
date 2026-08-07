package fr.blueprint.client.screen;

import fr.blueprint.core.graph.screen.ClientValue;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Ce que le client sait de son propre joueur, pour les liaisons de source
 * {@link fr.blueprint.core.graph.screen.ElementBinding.Source#CLIENT}.
 *
 * <p>Toute la classe est une <b>lecture</b> : aucun paquet, aucun appel au serveur, aucun
 * état gardé. C'est là toute la raison d'être des sources client — une barre de vie ne
 * doit pas coûter au serveur un parcours des joueurs connectés à chaque tick pour une
 * valeur que le joueur a déjà sous les yeux dans ses propres cœurs.
 *
 * <p>Le {@code switch} est exhaustif sur l'énumération : ajouter une valeur au catalogue
 * sans la résoudre ici ne compile pas. Un catalogue et un résolveur qui divergent
 * donneraient un élément lié à un nom valide et éternellement vide, ce qui se
 * diagnostique très mal.
 */
public final class ClientValues {

    private ClientValues() {
    }

    /**
     * La valeur portant ce nom, ou {@code null}.
     *
     * <p>{@code null} pour un joueur absent — le temps d'un changement de dimension, par
     * exemple — plutôt qu'un zéro : la liaison affiche alors du vide, ce qui est vrai,
     * là où « 0 » annoncerait un joueur à l'agonie.
     */
    public static @Nullable Object of(@Nullable LocalPlayer player, String name) {
        ClientValue value = ClientValue.byKey(name);
        if (player == null || value == null) {
            return null;
        }
        return switch (value) {
            case HEALTH -> (double) player.getHealth();
            case MAX_HEALTH -> (double) player.getMaxHealth();
            case ABSORPTION -> (double) player.getAbsorptionAmount();
            case FOOD -> (double) player.getFoodData().getFoodLevel();
            case SATURATION -> (double) player.getFoodData().getSaturationLevel();
            case ARMOR -> (double) player.getArmorValue();
            case XP_LEVEL -> (double) player.experienceLevel;
            case XP_PROGRESS -> (double) player.experienceProgress;
            case AIR -> (double) player.getAirSupply();
            case NAME -> player.getGameProfile().name();
            case X -> player.getX();
            case Y -> player.getY();
            case Z -> player.getZ();
            case DIMENSION -> player.level().dimension().identifier().toString();
        };
    }
}
