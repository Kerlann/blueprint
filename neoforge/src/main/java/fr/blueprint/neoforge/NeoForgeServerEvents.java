package fr.blueprint.neoforge;

import fr.blueprint.core.BlueprintMod;
import fr.blueprint.core.content.ContentDrops;
import fr.blueprint.core.event.WorldEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Les fils que NeoForge branche sur Blueprint, côté serveur — le pendant de
 * {@code FabricServerEvents}.
 *
 * <p>Aucune décision de produit ici : ce qui arrive à chaque événement est décidé dans
 * {@code WorldEvents} et {@code BlueprintMod}, écrit une fois pour les deux chargeurs.
 * Ce fichier est la preuve que la ligne tracée au lot A4 était au bon endroit — il ne
 * contient que des différences de chargeur.
 *
 * <p>Trois d'entre elles ne sont pas de simples renommages et sont commentées à leur
 * ligne : la casse d'un bloc, le sommeil, et le fait que NeoForge n'appelle ses
 * interactions qu'une fois par main.
 */
final class NeoForgeServerEvents {

    private NeoForgeServerEvents() {
    }

    static void register(IEventBus bus) {
        bus.addListener((ServerStartingEvent e) -> BlueprintMod.serverStarting(e.getServer()));
        bus.addListener((ServerStartedEvent e) -> BlueprintMod.serverStarted(e.getServer()));
        bus.addListener((ServerStoppedEvent e) -> BlueprintMod.serverStopped(e.getServer()));
        bus.addListener((ServerTickEvent.Post e) -> BlueprintMod.endServerTick(e.getServer()));

        bus.addListener((RegisterCommandsEvent e) ->
                BlueprintMod.registerCommands(e.getDispatcher()));
        bus.addListener((AddServerReloadListenersEvent e) ->
                e.addListener(BlueprintMod.DATAPACK_NODES_RELOADER,
                        (net.minecraft.server.packs.resources.ResourceManagerReloadListener)
                                BlueprintMod::reloadDatapackNodes));

        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) {
                BlueprintMod.playerJoined(player);
            }
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) {
                BlueprintMod.playerDisconnected(player);
            }
        });

        interaction(bus);
        combat(bus);
        playerLifecycle(bus);
    }

    private static void interaction(IEventBus bus) {
        // NeoForge appelle ses événements d'interaction pour CHAQUE main, comme Fabric :
        // la garde est donc la même — mais elle reste ici, parce que rien ne dit qu'un
        // troisième chargeur ferait pareil.
        bus.addListener((PlayerInteractEvent.RightClickBlock e) -> {
            if (e.getHand() == InteractionHand.MAIN_HAND
                    && e.getEntity() instanceof ServerPlayer player) {
                WorldEvents.playerUsedBlock(player, e.getPos(), e.getFace());
            }
        });
        bus.addListener((PlayerInteractEvent.RightClickItem e) -> {
            if (e.getHand() == InteractionHand.MAIN_HAND
                    && e.getEntity() instanceof ServerPlayer player) {
                WorldEvents.playerUsedItem(player);
            }
        });
        bus.addListener((PlayerInteractEvent.EntityInteract e) -> {
            if (e.getHand() == InteractionHand.MAIN_HAND
                    && e.getEntity() instanceof ServerPlayer player) {
                WorldEvents.playerUsedEntity(player, e.getTarget());
            }
        });

        // DIFFÉRENCE RÉELLE, pas un renommage. Fabric expose « le bloc vient d'être
        // cassé » ; NeoForge expose « le bloc va l'être », annulable. On lit donc l'état
        // AVANT la casse au lieu d'après — ce qui donne le même état, puisque c'est
        // justement celui que Fabric prenait soin de transmettre plutôt que de relire.
        //
        // Ce qui diffère vraiment : ici l'événement part même si la casse est empêchée
        // ensuite par un autre mod. C'est une limite connue, inscrite au plan.
        bus.addListener((BlockEvent.BreakEvent e) -> {
            if (e.isCanceled()) {
                return;
            }
            ContentDrops.afterBlockBroken(
                    (net.minecraft.world.level.Level) e.getLevel(), e.getPlayer(),
                    e.getPos(), e.getState());
            if (e.getPlayer() instanceof ServerPlayer player) {
                WorldEvents.playerBrokeBlock(player, e.getPos(), e.getState());
            }
        });
    }

    private static void combat(IEventBus bus) {
        bus.addListener((LivingDeathEvent e) -> {
            WorldEvents.entityDied(e.getEntity());
            // « qui a tué qui » : Fabric a un événement dédié, NeoForge le déduit de la
            // source des dégâts. Même information, deux chemins.
            if (e.getSource().getEntity() != null) {
                WorldEvents.entityKilled(e.getSource().getEntity(), e.getEntity());
            }
        });
        bus.addListener((LivingDamageEvent.Post e) ->
                // getNewDamage : ce que l'entité a RÉELLEMENT encaissé — l'équivalent
                // exact du damageTaken de Fabric, et non getOriginalDamage.
                WorldEvents.entityDamaged(e.getEntity(), e.getSource(), e.getNewDamage()));
        bus.addListener((AttackEntityEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) {
                WorldEvents.playerAttackedEntity(player, e.getTarget());
            }
        });
        bus.addListener((ServerChatEvent e) ->
                WorldEvents.playerChatted(e.getPlayer(), e.getRawText()));
    }

    private static void playerLifecycle(IEventBus bus) {
        bus.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) {
                // isEndConquered : « il revient par le portail de l'End », c'est-à-dire
                // exactement ce que le pin end_portal promet. Fabric le disait à l'envers,
                // par un drapeau « il n'était pas mort ».
                WorldEvents.playerRespawned(player, e.isEndConquered());
            }
        });
        bus.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (!(e.getEntity() instanceof ServerPlayer player)) {
                return;
            }
            var server = player.level().getServer();
            ServerLevel from = server == null ? null : server.getLevel(e.getFrom());
            ServerLevel to = server == null ? null : server.getLevel(e.getTo());
            if (from != null && to != null) {
                WorldEvents.playerChangedWorld(player, from, to);
            }
        });
        bus.addListener((PlayerWakeUpEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) {
                WorldEvents.playerWokeUp(player);
            }
        });
        // MANQUANT ET ASSUMÉ : « le joueur s'endort ». NeoForge n'expose que
        // CanPlayerSleepEvent, une QUESTION posée avant de décider — répondre oui n'est
        // pas dormir. Émettre player_sleep depuis là ferait se déclencher un graphe pour
        // un joueur que le jeu refuse ensuite de coucher. Mieux vaut un événement qui ne
        // part pas qu'un événement qui ment ; c'est inscrit au plan.
    }
}
