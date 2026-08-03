package fr.blueprint.core.nodes;

import fr.blueprint.api.node.NodeCategories;
import fr.blueprint.api.node.NodeContext;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.api.registry.NodeRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Nœuds entité et joueur (story 7.4). Sécurité systématique : entité nulle, retirée
 * du monde ou non-vivante → {@code ctx.fail} traduit, jamais d'exception (AC PRD).
 */
final class EntityNodes {

    private EntityNodes() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("blueprint", path);
    }

    /** L'entité du pin, vivante au sens « présente dans le monde », ou null + faute. */
    private static @Nullable Entity entity(NodeContext ctx, String pin) {
        Object value = ctx.in(pin);
        if (value instanceof Entity entity && !entity.isRemoved()) {
            return entity;
        }
        ctx.fail(Component.translatable("blueprint.fault.invalid_entity", pin));
        return null;
    }

    private static @Nullable LivingEntity living(NodeContext ctx, String pin) {
        Entity entity = entity(ctx, pin);
        if (entity == null) {
            return null;
        }
        if (entity instanceof LivingEntity livingEntity) {
            return livingEntity;
        }
        ctx.fail(Component.translatable("blueprint.fault.not_living", pin));
        return null;
    }

    private static @Nullable ServerPlayer player(NodeContext ctx, String pin) {
        Entity entity = entity(ctx, pin);
        if (entity == null) {
            return null;
        }
        if (entity instanceof ServerPlayer serverPlayer) {
            return serverPlayer;
        }
        ctx.fail(Component.translatable("blueprint.fault.not_player", pin));
        return null;
    }

    static void register(NodeRegistry r) {
        r.register(NodeType.builder(id("entity/position"))
                .category(NodeCategories.ENTITY_READ).exec().permission(Permission.GAMEPLAY)
                .in("entity", PinTypes.ENTITY)
                .out("pos", PinTypes.VEC3)
                .action(ctx -> {
                    Entity entity = entity(ctx, "entity");
                    if (entity != null) {
                        ctx.out("pos", entity.position());
                    }
                })
                .build());

        r.register(NodeType.builder(id("entity/teleport"))
                .category(NodeCategories.ENTITY_ACT).exec().permission(Permission.WORLD)
                .in("entity", PinTypes.ENTITY)
                .in("pos", PinTypes.VEC3)
                .action(ctx -> {
                    Entity entity = entity(ctx, "entity");
                    if (entity != null) {
                        Vec3 pos = ctx.in("pos");
                        entity.teleportTo(pos.x, pos.y, pos.z);
                    }
                })
                .build());

        r.register(NodeType.builder(id("entity/health"))
                .category(NodeCategories.ENTITY_READ).exec().permission(Permission.GAMEPLAY)
                .in("entity", PinTypes.ENTITY)
                .out("health", PinTypes.DOUBLE)
                .action(ctx -> {
                    LivingEntity living = living(ctx, "entity");
                    if (living != null) {
                        ctx.out("health", (double) living.getHealth());
                    }
                })
                .build());

        r.register(NodeType.builder(id("entity/set_health"))
                .category(NodeCategories.ENTITY_ACT).exec().permission(Permission.GAMEPLAY)
                .in("entity", PinTypes.ENTITY)
                .in("health", PinTypes.DOUBLE, 20.0)
                .action(ctx -> {
                    LivingEntity living = living(ctx, "entity");
                    if (living != null) {
                        living.setHealth((float) Math.max(0, ctx.<Double>in("health")));
                    }
                })
                .build());

        r.register(NodeType.builder(id("entity/heal"))
                .category(NodeCategories.ENTITY_ACT).exec().permission(Permission.GAMEPLAY)
                .in("entity", PinTypes.ENTITY)
                .in("amount", PinTypes.DOUBLE, 1.0)
                .action(ctx -> {
                    LivingEntity living = living(ctx, "entity");
                    if (living != null) {
                        living.heal((float) Math.max(0, ctx.<Double>in("amount")));
                    }
                })
                .build());

        r.register(NodeType.builder(id("entity/add_effect"))
                .category(NodeCategories.ENTITY_ACT).exec().permission(Permission.GAMEPLAY)
                .in("entity", PinTypes.ENTITY)
                .in("effect", PinTypes.RESOURCE_LOCATION)
                .in("duration", PinTypes.INT, 200)
                .in("amplifier", PinTypes.INT, 0)
                .action(ctx -> {
                    LivingEntity living = living(ctx, "entity");
                    if (living == null) {
                        return;
                    }
                    Identifier effectId = ctx.in("effect");
                    Optional<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.getOptional(effectId);
                    if (effect.isEmpty()) {
                        ctx.fail(Component.translatable("blueprint.fault.unknown_id",
                                effectId.toString()));
                        return;
                    }
                    living.addEffect(new MobEffectInstance(
                            BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect.get()),
                            Math.max(1, ctx.<Integer>in("duration")),
                            Math.max(0, ctx.<Integer>in("amplifier"))));
                })
                .build());

        r.register(NodeType.builder(id("player/give_item"))
                .category(NodeCategories.PLAYER_ACT).exec().permission(Permission.GAMEPLAY)
                .in("player", PinTypes.PLAYER)
                .in("item", PinTypes.ITEMSTACK)
                .action(ctx -> {
                    ServerPlayer player = player(ctx, "player");
                    if (player == null) {
                        return;
                    }
                    ItemStack stack = ctx.<ItemStack>in("item").copy();
                    if (!stack.isEmpty() && !player.getInventory().add(stack)) {
                        player.drop(stack, false); // inventaire plein : au sol, pas perdu
                    }
                })
                .build());

        r.register(NodeType.builder(id("player/title"))
                .category(NodeCategories.PLAYER_FEEDBACK).exec().permission(Permission.GAMEPLAY)
                .in("player", PinTypes.PLAYER)
                .in("text", PinTypes.STRING, "")
                .action(ctx -> {
                    ServerPlayer player = player(ctx, "player");
                    if (player != null) {
                        player.connection.send(new ClientboundSetTitleTextPacket(
                                Component.literal(ctx.in("text"))));
                    }
                })
                .build());

        r.register(NodeType.builder(id("player/give_xp"))
                .category(NodeCategories.PLAYER_ACT).exec().permission(Permission.GAMEPLAY)
                .in("player", PinTypes.PLAYER)
                .in("amount", PinTypes.INT, 1)
                .action(ctx -> {
                    ServerPlayer player = player(ctx, "player");
                    if (player != null) {
                        player.giveExperiencePoints(ctx.in("amount"));
                    }
                })
                .build());

        r.register(NodeType.builder(id("player/set_gamemode"))
                .category(NodeCategories.PLAYER_ACT).exec().permission(Permission.ADMIN)
                .in("player", PinTypes.PLAYER)
                .in("mode", PinTypes.STRING, "survival")
                .action(ctx -> {
                    ServerPlayer player = player(ctx, "player");
                    if (player == null) {
                        return;
                    }
                    String mode = ctx.in("mode");
                    GameType type = GameType.byName(mode, null);
                    if (type == null) {
                        ctx.fail(Component.translatable("blueprint.fault.unknown_id", mode));
                        return;
                    }
                    player.setGameMode(type);
                })
                .build());
    }
}
