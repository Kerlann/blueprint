package fr.blueprint.core.storage;

import fr.blueprint.core.vm.RefResolver;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Résolution des références vivantes contre un serveur réel (story 6.1). */
public record ServerRefResolver(MinecraftServer server) implements RefResolver {

    @Override
    public @Nullable ServerPlayer player(UUID id) {
        return server.getPlayerList().getPlayer(id);
    }

    @Override
    public @Nullable Entity entity(UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }
}
