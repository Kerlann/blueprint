package fr.blueprint.core.event;

import fr.blueprint.api.event.BlueprintEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ce que le monde vient de faire, dit une fois pour tous les chargeurs (story 7.6).
 *
 * <p>Ces méthodes vivaient dans {@code BlueprintMod}, chacune collée à l'événement Fabric
 * qui l'appelait. Les séparer trace la ligne qui compte pour le multiloader :
 *
 * <ul>
 *   <li><b>Ici</b> — ce qui est vrai du jeu : quelle charge utile porte l'événement, quelle
 *       valeur est copiée et pourquoi, quel nom prend chaque sortie. Écrit une fois.</li>
 *   <li><b>Dans le module du chargeur</b> — ce qui est vrai de <i>ce</i> chargeur : que
 *       Fabric appelle son callback pour chaque main, qu'il émet le sommeil pour toute
 *       entité vivante, que son drapeau se nomme {@code alive}. Recopié par chargeur, et
 *       c'est normal : ces faits ne se ressemblent pas d'un chargeur à l'autre.</li>
 * </ul>
 *
 * <p>La règle pour trancher : si la correction d'un bug ici devait être recopiée dans
 * chaque chargeur, c'est qu'elle est du mauvais côté de la ligne.
 */
public final class WorldEvents {

    private WorldEvents() {
    }

    public static void playerJoined(ServerPlayer player) {
        BlueprintEvents.fire(StandardEvents.PLAYER_JOIN,
                payload -> payload.set("player", player));
    }

    public static void playerQuit(ServerPlayer player) {
        BlueprintEvents.fire(StandardEvents.PLAYER_QUIT,
                payload -> payload.set("player", player));
    }

    public static void playerUsedBlock(ServerPlayer player, BlockPos pos, Direction face) {
        BlueprintEvents.fire(StandardEvents.PLAYER_USE_BLOCK,
                payload -> payload.set("player", player)
                        .set("pos", pos)
                        .set("face", face));
    }

    public static void playerUsedItem(ServerPlayer player) {
        // La pile est COPIÉE : l'événement peut s'exécuter des ticks plus tard
        // (flow/wait), et d'ici là le joueur aura pu consommer, jeter ou empiler ce
        // qu'il tenait. Un graphe lirait alors un objet différent de celui qui l'a
        // déclenché.
        var held = player.getMainHandItem().copy();
        BlueprintEvents.fire(StandardEvents.PLAYER_USE_ITEM,
                payload -> payload.set("player", player)
                        .set("stack", held)
                        .set("item", BuiltInRegistries.ITEM.getKey(held.getItem())));
    }

    /**
     * @param state l'état <b>reçu de l'événement</b>, jamais le bloc relu à la position :
     *              le bloc n'y est plus, et une relecture rendrait de l'air
     */
    public static void playerBrokeBlock(ServerPlayer player, BlockPos pos, BlockState state) {
        BlueprintEvents.fire(StandardEvents.PLAYER_BREAK_BLOCK,
                payload -> payload.set("player", player)
                        .set("pos", pos.immutable())
                        .set("block", BuiltInRegistries.BLOCK.getKey(state.getBlock())));
    }

    public static void entityDied(LivingEntity entity) {
        BlueprintEvents.fire(StandardEvents.ENTITY_DEATH,
                payload -> payload.set("entity", entity));
    }

    public static void playerChatted(ServerPlayer sender, String message) {
        BlueprintEvents.fire(StandardEvents.PLAYER_CHAT,
                payload -> payload.set("player", sender).set("message", message));
    }

    /**
     * Dégâts subis — le socle de tout script de combat : sans eux, un graphe ne savait
     * d'une entité que sa mort.
     *
     * @param amountTaken ce que l'entité a <b>réellement encaissé</b>, armure et
     *                    enchantements déduits — et non les dégâts bruts de la source.
     *                    Les deux chiffres existent chez tous les chargeurs ; c'est
     *                    celui-ci que le pin {@code amount} promet.
     */
    public static void entityDamaged(LivingEntity entity, DamageSource source,
                                     float amountTaken) {
        BlueprintEvents.fire(StandardEvents.ENTITY_DAMAGED,
                payload -> {
                    payload.set("entity", entity).set("amount", (double) amountTaken);
                    if (source.getEntity() != null) {
                        payload.set("attacker", source.getEntity());
                    }
                });
    }

    public static void entityKilled(Entity killer, Entity victim) {
        BlueprintEvents.fire(StandardEvents.ENTITY_KILLED,
                payload -> payload.set("killer", killer).set("victim", victim));
    }

    public static void playerAttackedEntity(ServerPlayer player, Entity target) {
        BlueprintEvents.fire(StandardEvents.PLAYER_ATTACK_ENTITY,
                payload -> payload.set("player", player).set("target", target));
    }

    public static void playerUsedEntity(ServerPlayer player, Entity target) {
        BlueprintEvents.fire(StandardEvents.PLAYER_USE_ENTITY,
                payload -> payload.set("player", player).set("target", target));
    }

    /**
     * @param endPortal vrai si le joueur revient par le <b>portail de l'End</b>, faux
     *                  s'il réapparaît après être mort. Le pin porte ce nom-là parce que
     *                  c'est la question que se pose l'auteur du graphe ; les chargeurs,
     *                  eux, exposent plutôt un « il n'était pas mort » à traduire.
     */
    public static void playerRespawned(ServerPlayer newPlayer, boolean endPortal) {
        BlueprintEvents.fire(StandardEvents.PLAYER_RESPAWN,
                payload -> payload.set("player", newPlayer).set("end_portal", endPortal));
    }

    public static void playerChangedWorld(ServerPlayer player, ServerLevel from,
                                          ServerLevel to) {
        // Le monde se recharge sous ses pieds : un menu qui resterait affiché montrerait
        // un état d'avant le changement (10.3, AC5).
        fr.blueprint.core.net.ServerBlueprintNet.closeScreen(player);
        BlueprintEvents.fire(StandardEvents.PLAYER_CHANGE_WORLD,
                payload -> payload.set("player", player)
                        .set("from", from.dimension().identifier())
                        .set("to", to.dimension().identifier()));
    }

    public static void playerSlept(ServerPlayer player, BlockPos pos) {
        BlueprintEvents.fire(StandardEvents.PLAYER_SLEEP,
                payload -> payload.set("player", player).set("pos", pos.immutable()));
    }

    public static void playerWokeUp(ServerPlayer player) {
        BlueprintEvents.fire(StandardEvents.PLAYER_WAKE_UP,
                payload -> payload.set("player", player));
    }
}
