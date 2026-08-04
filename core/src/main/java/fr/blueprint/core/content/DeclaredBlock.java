package fr.blueprint.core.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Un bloc enregistré depuis une définition — story 11.3.
 *
 * <p>Il n'existe que pour <b>une</b> raison : rendre au joueur le lien entre son outil et
 * sa vitesse de minage. Ce lien passe normalement par les tags {@code mineable/*}, qui
 * sont des données de datapack, donc de sauvegarde de monde — inaccessibles à
 * l'initialisation du mod, où le bloc doit pourtant être enregistré. Sans cette classe, un
 * bloc déclaré se minerait à la main aussi vite qu'à la pioche de netherite, et personne
 * ne comprendrait pourquoi.
 *
 * <p>Écrire dans la sauvegarde du joueur pour y déposer un tag aurait été l'autre voie.
 * Elle est refusée : ce produit n'écrit pas dans les mondes de quelqu'un d'autre pour se
 * simplifier la vie, et un tag y survivrait à la désinstallation du mod.
 */
public class DeclaredBlock extends Block {

    private final BlockDefinition definition;

    public DeclaredBlock(BlockDefinition definition, Properties properties) {
        super(properties);
        this.definition = definition;
    }

    public BlockDefinition definition() {
        return definition;
    }

    /**
     * La formule du jeu, avec une vitesse d'outil que nous fournissons.
     *
     * <p>Le diviseur 30 est celui du cas « bon outil » de la vanille. Il est constant ici
     * parce que la sanction du mauvais outil est portée par la vitesse elle-même — un
     * objet de la mauvaise famille rend 1, soit la main — et par le refus de lâcher quoi
     * que ce soit. Deux sanctions pour la même faute rendraient certains blocs quasiment
     * impossibles à retirer, ce qui n'est pas de la difficulté mais un piège.
     */
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level,
                                       BlockPos pos) {
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness <= 0f) {
            return 1f;
        }
        return definition.miningSpeed(referenceSpeed(player)) / hardness / 30f;
    }

    /**
     * Le bloc vient d'être posé — story 11.5.
     *
     * <p>C'est le seul endroit d'où l'on puisse le savoir : Fabric expose la <b>casse</b>
     * d'un bloc, pas sa <b>pose</b>. Ce qui rend l'événement possible malgré tout, c'est
     * que les blocs déclarés sont les nôtres, et qu'un bloc sait quand on le pose.
     *
     * <p>Filtré sur le serveur et sur un joueur : la même méthode est appelée côté client
     * pour l'affichage, et par un distributeur qui n'a pas de joueur derrière lui.
     */
    @Override
    public void setPlacedBy(net.minecraft.world.level.Level level,
                            net.minecraft.core.BlockPos pos, BlockState state,
                            @org.jetbrains.annotations.Nullable
                            net.minecraft.world.entity.LivingEntity placer,
                            net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()
                || !(placer instanceof net.minecraft.server.level.ServerPlayer player)) {
            return;
        }
        fr.blueprint.api.event.BlueprintEvents.fire(
                fr.blueprint.core.event.StandardEvents.BLOCK_PLACED,
                payload -> payload.set("player", player)
                        .set("pos", pos.immutable())
                        .set("block", definition.id()));
    }

    /**
     * Ce que l'objet tenu obtiendrait sur le bloc vanille représentatif de la famille.
     *
     * <p>Poser la question au jeu plutôt que de tenir une liste d'outils : une pioche d'un
     * autre mod répond correctement sans que nous ayons à la connaître, et une pioche qui
     * cesserait d'en être une répondrait correctement aussi.
     */
    public float referenceSpeed(Player player) {
        ItemStack held = player.getMainHandItem();
        return switch (definition.tool()) {
            case NONE -> 1f;
            case PICKAXE -> held.getDestroySpeed(Blocks.STONE.defaultBlockState());
            case AXE -> held.getDestroySpeed(Blocks.OAK_PLANKS.defaultBlockState());
            case SHOVEL -> held.getDestroySpeed(Blocks.DIRT.defaultBlockState());
            case HOE -> held.getDestroySpeed(Blocks.HAY_BLOCK.defaultBlockState());
        };
    }
}
