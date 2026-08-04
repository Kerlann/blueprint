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
        return registered();
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
