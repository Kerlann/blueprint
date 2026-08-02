package fr.blueprint.core.vm;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Résolution des références vivantes au chargement d'une exécution suspendue.
 * Null = la référence n'existe plus (joueur parti, entité morte) → l'exécution
 * est annulée proprement, jamais reprise avec un trou (AC3 du PRD 3.4).
 * En jeu : résolu contre le serveur (6.1) ; en test : résolveur factice.
 */
public interface RefResolver {

    @Nullable ServerPlayer player(UUID id);

    @Nullable Entity entity(UUID id);

    /** Aucune référence résoluble — toute exécution portant une référence est annulée. */
    RefResolver NONE = new RefResolver() {
        @Override
        public @Nullable ServerPlayer player(UUID id) {
            return null;
        }

        @Override
        public @Nullable Entity entity(UUID id) {
            return null;
        }
    };
}
