package fr.blueprint.core.nodes;

import fr.blueprint.api.node.NodeCategories;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.api.registry.NodeRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Nœuds item et texte (story 7.5) — purs et déterministes : des constructions,
 * jamais de lecture du monde (contrat de mémoïsation des purs).
 */
final class ItemNodes {

    private ItemNodes() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("blueprint", path);
    }

    static void register(NodeRegistry r) {
        r.register(NodeType.builder(id("item/create"))
                .category(NodeCategories.ITEM).pure()
                .in("item", PinTypes.RESOURCE_LOCATION)
                .in("count", PinTypes.INT, 1)
                .out("stack", PinTypes.ITEMSTACK)
                .action(ctx -> {
                    Identifier itemId = ctx.in("item");
                    Optional<Item> item = BuiltInRegistries.ITEM.getOptional(itemId);
                    if (item.isEmpty()) {
                        ctx.fail(Component.translatable("blueprint.fault.unknown_id",
                                itemId.toString()));
                        return;
                    }
                    ctx.out("stack", new ItemStack(item.get(),
                            Math.clamp(ctx.<Integer>in("count"), 1, 99)));
                })
                .build());

        r.register(NodeType.builder(id("item/count"))
                .category(NodeCategories.ITEM).pure()
                .in("stack", PinTypes.ITEMSTACK)
                .out("count", PinTypes.INT)
                .action(ctx -> ctx.out("count", ctx.<ItemStack>in("stack").getCount()))
                .build());

        r.register(NodeType.builder(id("item/with_count"))
                .category(NodeCategories.ITEM).pure()
                .in("stack", PinTypes.ITEMSTACK)
                .in("count", PinTypes.INT, 1)
                .out("stack", PinTypes.ITEMSTACK)
                .action(ctx -> {
                    ItemStack copy = ctx.<ItemStack>in("stack").copy();
                    copy.setCount(Math.clamp(ctx.<Integer>in("count"), 0, 99));
                    ctx.out("stack", copy);
                })
                .build());

        r.register(NodeType.builder(id("item/matches"))
                .category(NodeCategories.ITEM).pure()
                .in("stack", PinTypes.ITEMSTACK)
                .in("item", PinTypes.RESOURCE_LOCATION)
                .out("matches", PinTypes.BOOL)
                .action(ctx -> {
                    ItemStack stack = ctx.in("stack");
                    ctx.out("matches", !stack.isEmpty()
                            && BuiltInRegistries.ITEM.getKey(stack.getItem())
                                    .equals(ctx.<Identifier>in("item")));
                })
                .build());

        // -------------------------------------------- identité et habillage (11.5)
        // Sans ces quatre-là, un item déclaré ne peut être ni RECONNU ni HABILLÉ par un
        // graphe : il existe et s'affiche (11.1, 11.2), mais reste inerte. C'est ce qui
        // manquait pour que l'épic 11 tienne sa promesse.

        r.register(NodeType.builder(id("item/id"))
                .category(NodeCategories.ITEM).pure()
                .in("stack", PinTypes.ITEMSTACK)
                .out("item", PinTypes.RESOURCE_LOCATION)
                .action(ctx -> ctx.out("item", BuiltInRegistries.ITEM
                        .getKey(ctx.<ItemStack>in("stack").getItem())))
                .build());

        r.register(NodeType.builder(id("item/name"))
                .category(NodeCategories.ITEM).pure()
                .in("stack", PinTypes.ITEMSTACK)
                .out("name", PinTypes.TEXT)
                // getHoverName et non le composant brut : c'est le nom RÉELLEMENT
                // affiché, celui que le joueur lit — renommage à l'enclume compris.
                // Lire le composant rendrait vide un item que tout le monde voit nommé.
                .action(ctx -> ctx.out("name", ctx.<ItemStack>in("stack").getHoverName()))
                .build());

        r.register(NodeType.builder(id("item/with_name"))
                .category(NodeCategories.ITEM).pure()
                .in("stack", PinTypes.ITEMSTACK)
                .in("name", PinTypes.TEXT)
                .out("stack", PinTypes.ITEMSTACK)
                .action(ctx -> {
                    // Copie : les nœuds purs ne modifient pas leur entrée. La pile
                    // reçue peut être celle d'un événement, voire celle de l'inventaire
                    // d'un joueur — la renommer sur place renommerait son objet.
                    ItemStack copy = ctx.<ItemStack>in("stack").copy();
                    copy.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                            ctx.<Component>in("name"));
                    ctx.out("stack", copy);
                })
                .build());

        r.register(NodeType.builder(id("item/with_lore"))
                .category(NodeCategories.ITEM).pure()
                .in("stack", PinTypes.ITEMSTACK)
                .in("lines", PinTypes.listOf(PinTypes.TEXT))
                .out("stack", PinTypes.ITEMSTACK)
                .action(ctx -> {
                    ItemStack copy = ctx.<ItemStack>in("stack").copy();
                    java.util.List<?> lines = ctx.in("lines");
                    java.util.List<Component> text = new java.util.ArrayList<>();
                    for (Object line : lines) {
                        if (line instanceof Component component) {
                            text.add(component);
                        }
                    }
                    // Tronqué à ce que le jeu accepte plutôt que refusé : une liste
                    // construite par une boucle peut déborder sans que l'auteur s'en
                    // doute, et perdre l'objet entier pour cela serait disproportionné.
                    if (text.size() > net.minecraft.world.item.component.ItemLore.MAX_LINES) {
                        text = text.subList(0,
                                net.minecraft.world.item.component.ItemLore.MAX_LINES);
                    }
                    copy.set(net.minecraft.core.component.DataComponents.LORE,
                            new net.minecraft.world.item.component.ItemLore(text));
                    ctx.out("stack", copy);
                })
                .build());

        // ------------------------------------------------------------------ texte

        r.register(NodeType.builder(id("text/literal"))
                .category(NodeCategories.TEXT).pure()
                .in("value", PinTypes.STRING, "")
                .out("text", PinTypes.TEXT)
                .action(ctx -> ctx.out("text", Component.literal(ctx.in("value"))))
                .build());

        r.register(NodeType.builder(id("text/colored"))
                .category(NodeCategories.TEXT).pure()
                .in("value", PinTypes.STRING, "")
                .in("color", PinTypes.STRING, "white")
                .out("text", PinTypes.TEXT)
                .action(ctx -> {
                    String colorName = ctx.in("color");
                    ChatFormatting color = ChatFormatting.getByName(colorName);
                    if (color == null || !color.isColor()) {
                        ctx.fail(Component.translatable("blueprint.fault.unknown_id", colorName));
                        return;
                    }
                    ctx.out("text", Component.literal(ctx.in("value")).withStyle(color));
                })
                .build());

        r.register(NodeType.builder(id("text/concat"))
                .category(NodeCategories.TEXT).pure()
                .in("left", PinTypes.TEXT)
                .in("right", PinTypes.TEXT)
                .out("text", PinTypes.TEXT)
                .action(ctx -> ctx.out("text", ctx.<Component>in("left").copy()
                        .append(ctx.<Component>in("right"))))
                .build());
    }
}
