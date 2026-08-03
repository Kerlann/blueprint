package fr.blueprint.core.nodes;

import fr.blueprint.api.node.NodeCategories;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.api.registry.NodeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Nœuds monde (story 7.3). Les LECTURES du monde sont des nœuds EXEC, jamais purs :
 * la mémoïsation des purs (contrat VM-COMP-001) exige des nœuds rejouables sans
 * dépendance au monde. Identifiant inconnu au runtime → {@code ctx.fail} traduit.
 */
final class WorldNodes {

    private WorldNodes() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("blueprint", path);
    }

    static void register(NodeRegistry r) {
        r.register(NodeType.builder(id("world/get_block"))
                .category(NodeCategories.WORLD_BLOCK).exec()
                .in("pos", PinTypes.BLOCKPOS)
                .out("state", PinTypes.BLOCKSTATE)
                .action(ctx -> ctx.out("state",
                        ctx.level().getBlockState(ctx.<BlockPos>in("pos"))))
                .build());

        r.register(NodeType.builder(id("world/set_block"))
                .category(NodeCategories.WORLD_BLOCK).exec().permission(Permission.WORLD)
                .in("pos", PinTypes.BLOCKPOS)
                .in("state", PinTypes.BLOCKSTATE)
                .action(ctx -> ctx.level().setBlockAndUpdate(
                        ctx.in("pos"), ctx.<BlockState>in("state")))
                .build());

        r.register(NodeType.builder(id("world/is_block"))
                .category(NodeCategories.WORLD_BLOCK).exec()
                .in("pos", PinTypes.BLOCKPOS)
                .in("block", PinTypes.RESOURCE_LOCATION)
                .out("matches", PinTypes.BOOL)
                .action(ctx -> {
                    Identifier blockId = ctx.in("block");
                    Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(blockId);
                    if (block.isEmpty()) {
                        ctx.fail(Component.translatable("blueprint.fault.unknown_id",
                                blockId.toString()));
                        return;
                    }
                    ctx.out("matches", ctx.level()
                            .getBlockState(ctx.<BlockPos>in("pos")).is(block.get()));
                })
                .build());

        /*
         * L'état de bloc depuis son identifiant. Sans lui, un blockstate n'avait
         * qu'UNE source — le bloc lu dans le monde — exactement comme vec3 n'avait
         * que la position d'une entité avant le batch 2. On pouvait recopier un bloc,
         * jamais en nommer un ; seul un littéral tapé dans l'éditeur y parvenait, et
         * un graphe ne peut pas calculer un littéral.
         */
        r.register(NodeType.builder(id("world/block_state"))
                .category(NodeCategories.WORLD_BLOCK).exec()
                .in("block", PinTypes.RESOURCE_LOCATION)
                .out("state", PinTypes.BLOCKSTATE)
                .out("valid", PinTypes.BOOL)
                .action(ctx -> {
                    Identifier blockId = ctx.in("block");
                    Optional<Block> block = blockId == null ? Optional.empty()
                            : BuiltInRegistries.BLOCK.getOptional(blockId);
                    // « valide » plutôt qu'une faute : un identifiant peut venir d'un
                    // message de chat ou d'un mod absent, et le graphe doit pouvoir
                    // répondre poliment plutôt que de s'arrêter.
                    ctx.out("valid", block.isPresent());
                    ctx.out("state", block.orElse(net.minecraft.world.level.block.Blocks.AIR)
                            .defaultBlockState());
                })
                .build());

        r.register(NodeType.builder(id("world/spawn_entity"))
                .category(NodeCategories.WORLD_STATE).exec().permission(Permission.WORLD)
                .in("type", PinTypes.RESOURCE_LOCATION)
                .in("pos", PinTypes.VEC3)
                .out("entity", PinTypes.ENTITY)
                .action(ctx -> {
                    Identifier typeId = ctx.in("type");
                    Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(typeId);
                    if (type.isEmpty()) {
                        ctx.fail(Component.translatable("blueprint.fault.unknown_id",
                                typeId.toString()));
                        return;
                    }
                    Vec3 pos = ctx.in("pos");
                    Entity spawned = type.get().spawn(ctx.level(),
                            BlockPos.containing(pos.x, pos.y, pos.z),
                            EntitySpawnReason.MOB_SUMMONED);
                    if (spawned == null) {
                        ctx.fail(Component.translatable("blueprint.fault.spawn_failed",
                                typeId.toString()));
                        return;
                    }
                    ctx.out("entity", spawned);
                })
                .build());

        r.register(NodeType.builder(id("world/play_sound"))
                .category(NodeCategories.WORLD_EFFECT).exec().permission(Permission.GAMEPLAY)
                .in("sound", PinTypes.RESOURCE_LOCATION)
                .in("pos", PinTypes.VEC3)
                .in("volume", PinTypes.DOUBLE, 1.0)
                .in("pitch", PinTypes.DOUBLE, 1.0)
                .action(ctx -> {
                    Identifier soundId = ctx.in("sound");
                    Optional<SoundEvent> sound = BuiltInRegistries.SOUND_EVENT.getOptional(soundId);
                    if (sound.isEmpty()) {
                        ctx.fail(Component.translatable("blueprint.fault.unknown_id",
                                soundId.toString()));
                        return;
                    }
                    Vec3 pos = ctx.in("pos");
                    ctx.level().playSound(null, pos.x, pos.y, pos.z, sound.get(),
                            SoundSource.MASTER, ctx.<Double>in("volume").floatValue(),
                            ctx.<Double>in("pitch").floatValue());
                })
                .build());

        r.register(NodeType.builder(id("world/particles"))
                .category(NodeCategories.WORLD_EFFECT).exec().permission(Permission.GAMEPLAY)
                .in("particle", PinTypes.RESOURCE_LOCATION)
                .in("pos", PinTypes.VEC3)
                .in("count", PinTypes.INT, 10)
                .action(ctx -> {
                    Identifier particleId = ctx.in("particle");
                    Optional<ParticleType<?>> type =
                            BuiltInRegistries.PARTICLE_TYPE.getOptional(particleId);
                    if (type.isEmpty()) {
                        ctx.fail(Component.translatable("blueprint.fault.unknown_id",
                                particleId.toString()));
                        return;
                    }
                    if (!(type.get() instanceof SimpleParticleType simple)) {
                        ctx.fail(Component.translatable("blueprint.fault.particle_complex",
                                particleId.toString()));
                        return;
                    }
                    Vec3 pos = ctx.in("pos");
                    ctx.level().sendParticles(simple, pos.x, pos.y, pos.z,
                            ctx.<Integer>in("count"), 0.2, 0.2, 0.2, 0.05);
                })
                .build());

        r.register(NodeType.builder(id("world/set_weather"))
                .category(NodeCategories.WORLD_STATE).exec().permission(Permission.WORLD)
                .in("rain", PinTypes.BOOL, false)
                .in("thunder", PinTypes.BOOL, false)
                .in("duration", PinTypes.INT, 6000)
                .action(ctx -> {
                    boolean rain = ctx.in("rain");
                    boolean thunder = ctx.in("thunder");
                    int duration = Math.max(1, ctx.<Integer>in("duration"));
                    if (rain || thunder) {
                        ctx.level().setWeatherParameters(0, duration, true, thunder);
                    } else {
                        ctx.level().setWeatherParameters(duration, 0, false, false);
                    }
                })
                .build());

        r.register(NodeType.builder(id("world/set_time"))
                .category(NodeCategories.WORLD_STATE).exec().permission(Permission.WORLD)
                .in("time", PinTypes.LONG, 1000L)
                .action(ctx -> ctx.level().setDayTime(ctx.in("time")))
                .build());

        r.register(NodeType.builder(id("world/explosion"))
                .category(NodeCategories.WORLD_EFFECT).exec().permission(Permission.ADMIN)
                .in("pos", PinTypes.VEC3)
                .in("power", PinTypes.DOUBLE, 2.0)
                .action(ctx -> {
                    Vec3 pos = ctx.in("pos");
                    // Puissance bornée : un graphe ne vaporise pas une région (NFR sûreté).
                    float power = (float) Math.clamp(ctx.<Double>in("power"), 0.0, 16.0);
                    ctx.level().explode(null, pos.x, pos.y, pos.z, power,
                            Level.ExplosionInteraction.TNT);
                })
                .build());

        r.register(NodeType.builder(id("world/drop_item"))
                .category(NodeCategories.WORLD_STATE).exec().permission(Permission.GAMEPLAY)
                .in("pos", PinTypes.VEC3)
                .in("item", PinTypes.ITEMSTACK)
                .action(ctx -> {
                    ItemStack stack = ctx.in("item");
                    if (stack.isEmpty()) {
                        return; // rien à poser, pas une faute
                    }
                    Vec3 pos = ctx.in("pos");
                    ctx.level().addFreshEntity(new ItemEntity(ctx.level(),
                            pos.x, pos.y, pos.z, stack.copy()));
                })
                .build());
    }
}
