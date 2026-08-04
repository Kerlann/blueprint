package fr.blueprint.core.content;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Enregistre les items déclarés — <b>pour de vrai</b>, dans le registre du jeu.
 *
 * <p>À appeler depuis {@code onInitialize()} et de nulle part ailleurs. C'est la seule
 * fenêtre : après elle, {@link Registry#freeze()} passe, et toute tentative lève. Ce n'est
 * pas une convention de ce projet mais une règle de Minecraft, et c'est elle qui décide de
 * toute la forme de l'épic 11 — d'où des définitions sur le disque plutôt que dans un
 * blueprint.
 *
 * <p>Ce qui en sort est un {@link Item} ordinaire : le {@code /give} le connaît, une
 * recette peut l'utiliser, il traverse le réseau, il se range dans un coffre. La seule
 * différence avec un item d'un autre mod est la façon dont il a été décrit.
 */
public final class ContentRegistrar {

    private static final Map<Identifier, Item> REGISTERED = new LinkedHashMap<>();
    private static final Map<Identifier, DeclaredBlock> BLOCKS = new LinkedHashMap<>();

    private ContentRegistrar() {
    }

    /**
     * Enregistre tout ce que le rapport a retenu, et rend ce qui a réellement été posé.
     *
     * <p>Un item qui échoue ne fait pas tomber les suivants. Le cas existe : un
     * identifiant déjà pris par un autre mod, par exemple, qu'aucune validation de fichier
     * ne peut prévoir puisqu'elle ne sait pas ce que les autres mods ont enregistré.
     */
    public static Map<Identifier, Item> registerAll(ContentLoader.Report report,
                                                    java.util.List<String> rejected) {
        for (var entry : report.items().entrySet()) {
            try {
                REGISTERED.put(entry.getKey(), register(entry.getValue()));
            } catch (RuntimeException e) {
                rejected.add(entry.getKey() + " : enregistrement refusé — " + e.getMessage());
            }
        }
        // Les blocs APRÈS les items : un bloc pose aussi un item (celui qu'on tient en
        // main), et mélanger les deux ordres rendrait les identifiants numériques du
        // réseau dépendants du contenu de deux dossiers plutôt que d'un.
        for (var entry : report.blocks().entrySet()) {
            try {
                BLOCKS.put(entry.getKey(), register(entry.getValue()));
            } catch (RuntimeException e) {
                rejected.add(entry.getKey() + " : enregistrement refusé — " + e.getMessage());
            }
        }
        return registered();
    }

    /**
     * Enregistre un bloc <b>et</b> son item.
     *
     * <p>Les deux vont ensemble : un bloc sans item ne se tient pas en main, donc ne se
     * pose pas, donc n'existe que pour {@code /setblock}. Ce n'est pas ce qu'on demande
     * quand on demande un bloc.
     */
    private static DeclaredBlock register(BlockDefinition definition) {
        ResourceKey<net.minecraft.world.level.block.Block> blockKey =
                ResourceKey.create(Registries.BLOCK, definition.id());
        var properties = net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                .setId(blockKey)
                .strength(definition.hardness(), definition.resistance())
                .sound(soundOf(definition.sound()))
                // Pas de table de butin : le jeu en chercherait une dans un datapack,
                // n'en trouverait pas, et le bloc ne lâcherait rien en se plaignant à
                // chaque coup de pioche. Ce qu'il lâche est décidé par ContentDrops.
                .noLootTable();
        if (definition.light() > 0) {
            properties = properties.lightLevel(state -> definition.light());
        }
        DeclaredBlock block = Registry.register(BuiltInRegistries.BLOCK, definition.id(),
                new DeclaredBlock(definition, properties));

        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, definition.id());
        Item.Properties itemProperties = new Item.Properties()
                .setId(itemKey)
                .useBlockDescriptionPrefix();
        if (!definition.name().isEmpty()) {
            itemProperties = itemProperties.component(
                    net.minecraft.core.component.DataComponents.ITEM_NAME,
                    definition.translate()
                            ? Component.translatable(definition.name())
                            : Component.literal(definition.name()));
        }
        REGISTERED.put(definition.id(), Registry.register(BuiltInRegistries.ITEM,
                definition.id(), new net.minecraft.world.item.BlockItem(block, itemProperties)));
        return block;
    }

    private static net.minecraft.world.level.block.SoundType soundOf(BlockDefinition.Sound sound) {
        return switch (sound) {
            case STONE -> net.minecraft.world.level.block.SoundType.STONE;
            case WOOD -> net.minecraft.world.level.block.SoundType.WOOD;
            case METAL -> net.minecraft.world.level.block.SoundType.METAL;
            case GLASS -> net.minecraft.world.level.block.SoundType.GLASS;
            case WOOL -> net.minecraft.world.level.block.SoundType.WOOL;
            case GRAVEL -> net.minecraft.world.level.block.SoundType.GRAVEL;
        };
    }

    /** Les blocs enregistrés, dans l'ordre. */
    public static Map<Identifier, DeclaredBlock> blocks() {
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(BLOCKS));
    }

    private static Item register(ItemDefinition definition) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, definition.id());
        // setId est OBLIGATOIRE depuis 1.21.2 : sans lui, Item.Properties refuse de
        // construire ses composants et lève à l'enregistrement, pas à l'usage.
        Item.Properties properties = new Item.Properties()
                .setId(key)
                .stacksTo(definition.stackSize())
                .rarity(definition.rarity());
        if (!definition.name().isEmpty()) {
            properties = properties.component(
                    net.minecraft.core.component.DataComponents.ITEM_NAME,
                    definition.translate()
                            ? Component.translatable(definition.name())
                            : Component.literal(definition.name()));
        }
        return Registry.register(BuiltInRegistries.ITEM, definition.id(), new Item(properties));
    }

    /** Ce qui a été enregistré, dans l'ordre, pour la commande et les tests. */
    public static Map<Identifier, Item> registered() {
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(REGISTERED));
    }
}
