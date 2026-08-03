package fr.blueprint.core.nodes;

import fr.blueprint.api.node.NodeCategories;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.api.registry.NodeRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Retours CIBLÉS vers un joueur (batch 5).
 *
 * <p>Un seul paquet clientbound existait — le titre. Sons et particules partaient à
 * <b>tout le monde</b> : impossible de donner un retour privé, alors que c'est
 * exactement ce qu'un script veut faire (« TU as trouvé le coffre », pas « quelqu'un
 * a trouvé le coffre »).
 *
 * <p>Tous ces nœuds envoient un paquet à une connexion précise. Un pin {@code player}
 * <b>non câblé</b> reste une faute nommée — c'est CTX-001, la règle du cœur, et elle
 * vaut ici comme ailleurs. En revanche un joueur <b>déconnecté entre-temps</b> ne fait
 * rien : entre l'instant où le graphe a lu le pin et celui où il l'utilise, une
 * déconnexion peut survenir, et ce n'est pas une faute de l'auteur.
 */
public final class ClientNodes {

    private ClientNodes() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("blueprint", path);
    }

    public static void register(NodeRegistry r) {
        r.register(NodeType.builder(id("player/subtitle"))
                .category(NodeCategories.PLAYER_FEEDBACK).exec().permission(Permission.GAMEPLAY)
                .in("player", PinTypes.PLAYER)
                .in("text", PinTypes.STRING, "")
                .action(ctx -> send(ctx.in("player"), player -> player.connection.send(
                        new ClientboundSetSubtitleTextPacket(text(ctx.in("text"))))))
                .build());

        /* La barre d'action : le retour le moins intrusif du jeu — il ne remplit pas
         * le chat et disparaît seul. C'est ce qu'on veut pour un compteur ou un état. */
        r.register(NodeType.builder(id("player/action_bar"))
                .category(NodeCategories.PLAYER_FEEDBACK).exec().permission(Permission.GAMEPLAY)
                .in("player", PinTypes.PLAYER)
                .in("text", PinTypes.STRING, "")
                .action(ctx -> send(ctx.in("player"), player -> player.connection.send(
                        new ClientboundSetActionBarTextPacket(text(ctx.in("text"))))))
                .build());

        /* Sans ce nœud, un titre reste à l'écran avec les durées du DERNIER réglage —
         * y compris celles d'un autre mod ou d'une commande passée. */
        r.register(NodeType.builder(id("player/title_times"))
                .category(NodeCategories.PLAYER_FEEDBACK).exec().permission(Permission.GAMEPLAY)
                .in("player", PinTypes.PLAYER)
                .in("fade_in", PinTypes.INT, 10)
                .in("stay", PinTypes.INT, 70)
                .in("fade_out", PinTypes.INT, 20)
                .action(ctx -> send(ctx.in("player"), player -> player.connection.send(
                        new ClientboundSetTitlesAnimationPacket(
                                Math.max(0, ctx.<Integer>in("fade_in")),
                                Math.max(0, ctx.<Integer>in("stay")),
                                Math.max(0, ctx.<Integer>in("fade_out"))))))
                .build());

        /* Le son PRIVÉ. world/play_sound le joue pour tout le monde ; celui-ci ne
         * touche qu'une connexion, et se place aux oreilles du joueur par défaut. */
        r.register(NodeType.builder(id("player/play_sound"))
                .category(NodeCategories.PLAYER_FEEDBACK).exec().permission(Permission.GAMEPLAY)
                .in("player", PinTypes.PLAYER)
                .in("sound", PinTypes.RESOURCE_LOCATION)
                .in("volume", PinTypes.DOUBLE, 1.0)
                .in("pitch", PinTypes.DOUBLE, 1.0)
                .action(ctx -> {
                    Identifier soundId = ctx.in("sound");
                    Optional<Holder.Reference<SoundEvent>> sound = soundId == null
                            ? Optional.empty()
                            : BuiltInRegistries.SOUND_EVENT.get(soundId);
                    if (sound.isEmpty()) {
                        ctx.fail(Component.translatable("blueprint.fault.unknown_id",
                                String.valueOf(soundId)));
                        return;
                    }
                    send(ctx.in("player"), player -> player.connection.send(
                            new ClientboundSoundPacket(sound.get(), SoundSource.MASTER,
                                    player.getX(), player.getY(), player.getZ(),
                                    ctx.<Double>in("volume").floatValue(),
                                    ctx.<Double>in("pitch").floatValue(),
                                    player.getRandom().nextLong())));
                })
                .build());

        /* Les particules privées. Le même effet visuel que world/particles, mais vu
         * du seul joueur concerné — de quoi baliser un chemin pour lui seul. */
        r.register(NodeType.builder(id("player/particles"))
                .category(NodeCategories.PLAYER_FEEDBACK).exec().permission(Permission.GAMEPLAY)
                .in("player", PinTypes.PLAYER)
                .in("particle", PinTypes.RESOURCE_LOCATION)
                .in("pos", PinTypes.VEC3)
                .in("count", PinTypes.INT, 8)
                .in("spread", PinTypes.DOUBLE, 0.5)
                .action(ctx -> {
                    Identifier particleId = ctx.in("particle");
                    ParticleType<?> type = particleId == null ? null
                            : BuiltInRegistries.PARTICLE_TYPE.getValue(particleId);
                    if (!(type instanceof ParticleOptions options)) {
                        // Toutes les particules ne sont pas de simples options : les
                        // paramétrées (dust, block…) demandent une donnée qu'un pin
                        // « identifiant » ne peut pas porter. Le dire plutôt que de
                        // ne rien afficher sans raison.
                        ctx.fail(Component.translatable("blueprint.fault.unknown_id",
                                String.valueOf(particleId)));
                        return;
                    }
                    Vec3 pos = vec(ctx.in("pos"));
                    double spread = Math.max(0, ctx.<Double>in("spread"));
                    send(ctx.in("player"), player -> ctx.level().sendParticles(player, options,
                            false, false, pos.x, pos.y, pos.z,
                            Math.clamp(ctx.<Integer>in("count"), 0, 512),
                            spread, spread, spread, 0));
                })
                .build());
    }

    // ------------------------------------------------------------------ outillage

    /**
     * Exécute l'envoi si le joueur est encore CONNECTÉ. Un pin vide n'arrive jamais
     * jusqu'ici — {@code ctx.in} l'a déjà transformé en faute nommée ; ce qui passe
     * ici, c'est un joueur parti entre la lecture du pin et son usage, et ce n'est
     * pas une faute de l'auteur du graphe.
     */
    private static void send(Object pin, java.util.function.Consumer<ServerPlayer> action) {
        if (pin instanceof ServerPlayer player && !player.hasDisconnected()) {
            action.accept(player);
        }
    }

    private static Component text(Object value) {
        return Component.literal(value instanceof String s ? s : "");
    }

    private static Vec3 vec(Object value) {
        return value instanceof Vec3 v ? v : Vec3.ZERO;
    }
}
