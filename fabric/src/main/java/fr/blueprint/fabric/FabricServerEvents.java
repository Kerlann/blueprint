package fr.blueprint.fabric;

import fr.blueprint.core.BlueprintMod;
import fr.blueprint.core.content.ContentDrops;
import fr.blueprint.core.event.WorldEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

/**
 * Tous les fils que Fabric branche sur Blueprint, côté serveur — et rien d'autre.
 *
 * <p>Ce fichier est <b>volontairement bête</b>. Aucune décision de produit ne doit y
 * descendre : ce qui arrive à chaque événement est décidé dans {@code WorldEvents} et
 * {@code BlueprintMod}, écrit une fois pour tous les chargeurs. Ce qui reste ici est ce
 * que le prochain chargeur devra <b>recopier différemment</b>, et c'est exactement ce
 * qu'on veut voir rassemblé.
 *
 * <p>Trois de ces adaptations méritent d'être lues avant d'en écrire l'équivalent
 * ailleurs — elles sont commentées à leur ligne : la garde de main principale, le filtre
 * des entités qui dorment, et le sens du drapeau de réapparition.
 */
final class FabricServerEvents {

    private FabricServerEvents() {
    }

    static void register() {
        lifecycle();
        connection();
        interaction();
        combat();
        playerLifecycle();
    }

    private static void lifecycle() {
        ServerLifecycleEvents.SERVER_STARTING.register(BlueprintMod::serverStarting);
        ServerLifecycleEvents.SERVER_STARTED.register(BlueprintMod::serverStarted);
        ServerLifecycleEvents.SERVER_STOPPED.register(BlueprintMod::serverStopped);
        ServerTickEvents.END_SERVER_TICK.register(BlueprintMod::endServerTick);

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, access, environment) -> BlueprintMod.registerCommands(dispatcher));

        ResourceLoader.get(PackType.SERVER_DATA).registerReloader(
                BlueprintMod.DATAPACK_NODES_RELOADER,
                (ResourceManagerReloadListener) BlueprintMod::reloadDatapackNodes);
    }

    private static void connection() {
        ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) -> BlueprintMod.playerJoined(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> BlueprintMod.playerDisconnected(handler.player));
    }

    private static void interaction() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            // Garde main principale (correction QA EVENT-001) : Fabric appelle le
            // callback pour CHAQUE main — sans garde, un clic émettrait deux événements.
            // Un autre chargeur peut très bien n'appeler qu'une fois : c'est un fait sur
            // Fabric, pas sur le jeu, d'où sa place ici.
            if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer serverPlayer) {
                WorldEvents.playerUsedBlock(serverPlayer, hit.getBlockPos(), hit.getDirection());
            }
            return InteractionResult.PASS;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer serverPlayer) {
                WorldEvents.playerUsedItem(serverPlayer);
            }
            return InteractionResult.PASS;
        });

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            // Le butin des blocs déclarés AVANT l'événement de graphe, comme avant le
            // déplacement : ContentDrops s'abonnait depuis registerDeclaredContent(),
            // c'est-à-dire plus tôt dans l'initialisation que les ponts d'événements.
            ContentDrops.afterBlockBroken(world, player, pos, state);
            if (player instanceof ServerPlayer serverPlayer) {
                WorldEvents.playerBrokeBlock(serverPlayer, pos, state);
            }
        });
    }

    private static void combat() {
        ServerLivingEntityEvents.AFTER_DEATH.register(
                (entity, damageSource) -> WorldEvents.entityDied(entity));
        ServerLivingEntityEvents.AFTER_DAMAGE.register(
                (entity, source, baseAmount, damageTaken, blocked) ->
                        // damageTaken, pas baseAmount : voir la doc du paramètre côté
                        // WorldEvents — c'est la seule ligne où le choix peut se perdre.
                        WorldEvents.entityDamaged(entity, source, damageTaken));
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register(
                (world, killer, victim, source) -> WorldEvents.entityKilled(killer, victim));

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            // Garde main principale, comme pour use_block (QA EVENT-001).
            if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer sp) {
                WorldEvents.playerAttackedEntity(sp, entity);
            }
            return InteractionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer sp) {
                WorldEvents.playerUsedEntity(sp, entity);
            }
            return InteractionResult.PASS;
        });

        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) ->
                WorldEvents.playerChatted(sender, message.signedContent()));
    }

    private static void playerLifecycle() {
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                // « alive » chez Fabric signifie « n'était pas mort » : c'est le retour
                // par le portail de l'End. Le pin s'appelle end_portal parce que c'est ce
                // que la valeur veut dire ; la traduction se fait ici.
                WorldEvents.playerRespawned(newPlayer, alive));

        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
                (player, origin, destination) ->
                        WorldEvents.playerChangedWorld(player, origin, destination));

        EntitySleepEvents.START_SLEEPING.register((entity, pos) -> {
            // Fabric émet pour toute entité vivante ; seul un joueur dort vraiment, et un
            // pin « player » ne peut pas recevoir un zombie.
            if (entity instanceof ServerPlayer sp) {
                WorldEvents.playerSlept(sp, pos);
            }
        });
        EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> {
            if (entity instanceof ServerPlayer sp) {
                WorldEvents.playerWokeUp(sp);
            }
        });
    }
}
