package fr.blueprint.core.content;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * Ce qu'un bloc déclaré lâche quand on le casse — story 11.3.
 *
 * <p>Un bloc de Minecraft ne décide pas de ses butins : il désigne une <b>table de
 * butin</b>, qui est une donnée de datapack, donc de sauvegarde de monde. Le bloc étant
 * enregistré à l'initialisation du mod, il n'y a aucune table à désigner — et en écrire
 * une dans la sauvegarde du joueur pour s'en tirer serait franchir une ligne que ce
 * produit ne franchit pas : le fichier y survivrait à la désinstallation du mod.
 *
 * <p>Le bloc est donc déclaré <b>sans table</b> ({@code noLootTable}), ce qui évite au jeu
 * d'en chercher une à chaque coup de pioche, et ce qu'il lâche est décidé ici : lui-même,
 * une fois, si le joueur avait ce qu'il fallait.
 *
 * <p>C'est une simplification et elle est assumée : pas de Fortune, pas de Toucher de
 * soie, pas de butin variable. Un bloc déclaré se ramasse, point. Ce que perdrait une
 * table de butin complète — la variété — n'a de sens que pour du minerai, et un minerai
 * demande de toute façon une story à lui.
 */
public final class ContentDrops {

    private ContentDrops() {
    }

    /**
     * Branche la règle de butin. Appelé une fois, à l'initialisation.
     *
     * <p>{@code AFTER} et non {@code BEFORE} : à ce moment le bloc est déjà retiré et
     * l'événement n'est pas déclenché quand la casse a été annulée. Poser le butin plus tôt
     * le ferait apparaître même quand une protection de terrain refuse la casse.
     */
    public static void register() {
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, entity) -> {
            if (level.isClientSide() || player.isCreative()
                    || !(state.getBlock() instanceof DeclaredBlock declared)) {
                return;
            }
            if (!declared.definition().dropsFor(declared.referenceSpeed(player))) {
                return;
            }
            Block.popResource(level, pos, new ItemStack(state.getBlock()));
        });
    }
}
