package fr.blueprint.core.nodes;

import fr.blueprint.api.node.NodeCategories;
import fr.blueprint.api.node.NodeCategory;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.api.registry.NodeRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Lecture et retrait d'inventaire (batch 7).
 *
 * <p>{@code player/give_item} existait seul : un graphe pouvait DONNER sans jamais
 * savoir ce que le joueur possédait. « S'il a la clé, ouvre la porte » — le motif le
 * plus courant d'une aventure — était inexprimable.
 *
 * <p>La lecture est {@code SAFE}, le retrait exige {@code GAMEPLAY} : voir ce qu'un
 * joueur porte n'engage rien, le lui prendre est un effet de jeu.
 */
public final class InventoryNodes {

    /** Inventaire : ce que porte un joueur, et ce qu'on lui retire. */
    public static final NodeCategory INVENTORY = NodeCategories.PLAYER_INVENTORY;

    private InventoryNodes() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("blueprint", path);
    }

    public static void register(NodeRegistry r) {
        r.register(NodeType.builder(id("player/main_hand")).fuelCost(2)
                .category(INVENTORY).exec().permission(Permission.SAFE)
                .in("player", PinTypes.PLAYER)
                .out("item", PinTypes.ITEMSTACK)
                .action(ctx -> ctx.out("item", held(ctx.in("player"), true)))
                .build());

        r.register(NodeType.builder(id("player/off_hand")).fuelCost(2)
                .category(INVENTORY).exec().permission(Permission.SAFE)
                .in("player", PinTypes.PLAYER)
                .out("item", PinTypes.ITEMSTACK)
                .action(ctx -> ctx.out("item", held(ctx.in("player"), false)))
                .build());

        /*
         * Compter parcourt l'inventaire PRINCIPAL — les 36 emplacements — pas
         * l'armure ni le curseur : c'est ce qu'un joueur appelle « ce que j'ai ».
         */
        // TARIF PAR ANALYSE (un joueur vivant est requis) : une résolution de registre
        // puis les trente-six emplacements du sac parcourus. Dix unités, pour les deux
        // nœuds — has_item appelle exactement le même count().
        r.register(NodeType.builder(id("player/count_item")).fuelCost(10)
                .category(INVENTORY).exec().permission(Permission.SAFE)
                .in("player", PinTypes.PLAYER)
                .in("item", PinTypes.RESOURCE_LOCATION)
                .out("count", PinTypes.INT)
                .action(ctx -> ctx.out("count", count(ctx.in("player"), ctx.in("item"))))
                .build());

        /* Le raccourci du motif le plus courant : « s'il a la clé… ». L'écrire en
         * count + comparaison coûterait deux nœuds à chaque porte. */
        r.register(NodeType.builder(id("player/has_item")).fuelCost(10)
                .category(INVENTORY).exec().permission(Permission.SAFE)
                .in("player", PinTypes.PLAYER)
                .in("item", PinTypes.RESOURCE_LOCATION)
                .in("count", PinTypes.INT, 1)
                .out("has", PinTypes.BOOL)
                .action(ctx -> ctx.out("has",
                        count(ctx.in("player"), ctx.in("item"))
                                >= Math.max(1, ctx.<Integer>in("count"))))
                .build());

        /*
         * Retirer rend le nombre RÉELLEMENT retiré. Demander cinq pommes à qui n'en a
         * que deux ne doit ni échouer ni mentir : un péage qui croit avoir encaissé
         * cinq pièces alors qu'il en a pris deux laisse passer sans payer.
         */
        r.register(NodeType.builder(id("player/remove_item")).fuelCost(10)
                .category(INVENTORY).exec().permission(Permission.GAMEPLAY)
                .in("player", PinTypes.PLAYER)
                .in("item", PinTypes.RESOURCE_LOCATION)
                .in("count", PinTypes.INT, 1)
                .out("removed", PinTypes.INT)
                .action(ctx -> {
                    Item item = itemOf(ctx.in("item"));
                    int wanted = Math.max(0, ctx.<Integer>in("count"));
                    if (!(ctx.in("player") instanceof ServerPlayer player) || item == null) {
                        ctx.out("removed", 0);
                        return;
                    }
                    // ZÉRO EST UN PIÈGE, et il a coûté cher. clearOrCountMatchingItems
                    // traite maxCount == 0 comme « COMPTE sans retirer » et rend alors le
                    // total possédé : demander zéro rendait donc « j'en ai retiré trente-
                    // deux » sans qu'une seule pierre ne bouge.
                    //
                    // Ce n'est pas une bizarrerie théorique. Un écran de dépôt dont le
                    // champ n'a pas encore été rempli vaut zéro : cliquer « Déposer »
                    // créditait tout l'inventaire sans rien prendre. De la fausse monnaie,
                    // au premier clic, par un joueur qui ne cherchait même pas la faille.
                    //
                    // Le nœud promet le nombre RÉELLEMENT retiré. Retirer zéro, c'est
                    // retirer zéro.
                    if (wanted == 0) {
                        ctx.out("removed", 0);
                        return;
                    }
                    int removed = player.getInventory().clearOrCountMatchingItems(
                            stack -> stack.is(item), wanted, player.inventoryMenu.getCraftSlots());
                    ctx.out("removed", removed);
                })
                .build());
    }

    // ------------------------------------------------------------------ outillage

    private static ItemStack held(Object pin, boolean main) {
        if (!(pin instanceof LivingEntity living)) {
            return ItemStack.EMPTY;
        }
        return main ? living.getMainHandItem() : living.getOffhandItem();
    }

    private static int count(Object playerPin, Object itemPin) {
        Item item = itemOf(itemPin);
        if (!(playerPin instanceof ServerPlayer player) || item == null) {
            return 0;
        }
        int total = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Un identifiant inconnu vaut « aucun objet » : compter rend zéro, pas une faute. */
    private static Item itemOf(Object pin) {
        return pin instanceof Identifier itemId
                ? BuiltInRegistries.ITEM.getOptional(itemId).orElse(null) : null;
    }
}
