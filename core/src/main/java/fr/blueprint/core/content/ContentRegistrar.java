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
 * <p>Il n'y a qu'une fenêtre : après elle, {@link Registry#freeze()} passe, et toute
 * tentative lève. Ce n'est pas une convention de ce projet mais une règle de Minecraft, et
 * c'est elle qui décide de toute la forme de l'épic 11 — d'où des définitions sur le
 * disque plutôt que dans un blueprint.
 *
 * <p><b>Cette fenêtre n'est pas la même d'un chargeur à l'autre</b>, et c'est pour cela
 * que rien ici ne décide plus du <i>quand</i>. Sur Fabric, c'est l'initialisation du mod ;
 * sur NeoForge, {@code RegisterEvent}, une fois par registre. Le moment est demandé à
 * {@link fr.blueprint.platform.PlatformRegistrar}, et l'enregistrement se fait donc en
 * <b>deux passes</b> — les blocs, puis les items — au lieu d'une.
 *
 * <h2>Ce que l'ordre décide, et pourquoi il est écrit</h2>
 * <p>Un item entre dans le registre avec un <b>identifiant numérique</b>, son rang. Ce
 * rang voyage sur le réseau. Client et serveur doivent donc produire exactement la même
 * suite, sans se concerter — sinon un client lit « pièce » là où le serveur a écrit
 * « rubis ». {@link ContentLoader} tient déjà la moitié du contrat en triant les fichiers ;
 * l'autre moitié est {@link #itemOrder}, qui dit dans quel ordre les deux dossiers se
 * suivent.
 *
 * <p>Avant la coupure en deux passes, cet ordre était un effet de bord de l'écriture du
 * code — « les blocs après les items » tenait au fait que la boucle des blocs venait
 * après. Sur NeoForge, l'ordre entre les registres n'est plus le nôtre : il fallait donc
 * que la suite des items cesse d'en dépendre. C'est tout l'objet de {@link #itemOrder}.
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
     * L'ordre dans lequel les blocs déclarés entrent dans le registre des blocs.
     *
     * <p>Fonction pure : elle ne touche aucun registre et se vérifie sans jeu lancé.
     */
    public static java.util.List<Identifier> blockOrder(ContentLoader.Report report) {
        return java.util.List.copyOf(report.blocks().keySet());
    }

    /**
     * L'ordre dans lequel les items déclarés entrent dans le registre des items :
     * <b>les items du dossier {@code items/}, puis l'item de chaque bloc</b>.
     *
     * <p>Les deux suites ne se mélangent pas. C'est ce qui rend la numérotation
     * indépendante de l'ordre dans lequel le chargeur ouvre ses registres : que les blocs
     * soient enregistrés avant ou après, la suite des items ne bouge pas.
     *
     * <p>Aucun doublon possible : {@link ContentLoader} écarte tout bloc dont
     * l'identifiant est déjà pris par un item, précisément parce qu'un bloc pose son
     * propre item du même nom.
     *
     * <p>Fonction pure : elle ne touche aucun registre et se vérifie sans jeu lancé.
     */
    public static java.util.List<Identifier> itemOrder(ContentLoader.Report report) {
        java.util.List<Identifier> order =
                new java.util.ArrayList<>(report.items().size() + report.blocks().size());
        order.addAll(report.items().keySet());
        order.addAll(report.blocks().keySet());
        return java.util.List.copyOf(order);
    }

    /**
     * Première passe : les blocs, et rien qu'eux.
     *
     * <p>Un bloc qui échoue ne fait pas tomber les suivants. Le cas existe : un
     * identifiant déjà pris par un autre mod, par exemple, qu'aucune validation de fichier
     * ne peut prévoir puisqu'elle ne sait pas ce que les autres mods ont enregistré.
     */
    public static void registerBlocks(ContentLoader.Report report,
                                      java.util.List<String> rejected) {
        for (Identifier id : blockOrder(report)) {
            try {
                BLOCKS.put(id, register(report.blocks().get(id)));
            } catch (RuntimeException e) {
                rejected.add(id + " : enregistrement refusé — " + e.getMessage());
            }
        }
    }

    /**
     * Seconde passe : les items déclarés, puis l'item de chaque bloc posé par la première.
     *
     * <p>Un bloc que la première passe a refusé n'obtient pas d'item — et n'est pas
     * refusé une seconde fois : son échec a déjà été dit.
     */
    public static Map<Identifier, Item> registerItems(ContentLoader.Report report,
                                                      java.util.List<String> rejected) {
        for (Identifier id : itemOrder(report)) {
            ItemDefinition item = report.items().get(id);
            try {
                if (item != null) {
                    REGISTERED.put(id, register(item));
                    continue;
                }
                DeclaredBlock block = BLOCKS.get(id);
                if (block != null) {
                    REGISTERED.put(id, registerBlockItem(block, report.blocks().get(id)));
                }
            } catch (RuntimeException e) {
                rejected.add(id + " : enregistrement refusé — " + e.getMessage());
            }
        }
        return registered();
    }

    /**
     * Enregistre un bloc. Son item vient ensuite, à la seconde passe.
     *
     * <p>Les deux vont ensemble : un bloc sans item ne se tient pas en main, donc ne se
     * pose pas, donc n'existe que pour {@code /setblock}. Ce n'est pas ce qu'on demande
     * quand on demande un bloc — ce qui les sépare ici est le calendrier du chargeur, pas
     * un changement d'intention.
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
        return Registry.register(BuiltInRegistries.BLOCK, definition.id(),
                new DeclaredBlock(definition, properties));
    }

    /** L'item qu'on tient en main pour poser le bloc — seconde passe. */
    private static Item registerBlockItem(DeclaredBlock block, BlockDefinition definition) {
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
        return Registry.register(BuiltInRegistries.ITEM, definition.id(),
                new net.minecraft.world.item.BlockItem(block, itemProperties));
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
