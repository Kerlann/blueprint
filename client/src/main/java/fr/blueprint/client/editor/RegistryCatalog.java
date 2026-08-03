package fr.blueprint.client.editor;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalogue des items et blocs pour les sélecteurs riches (5.2c), extrait du canevas.
 *
 * <p>Les listes coûtent un parcours complet des registres du jeu et un tri : elles se
 * construisent à la <b>première ouverture</b> d'un sélecteur, jamais à l'ouverture de
 * l'éditeur, et ne se reconstruisent plus. Les icônes se retiennent au fur et à mesure —
 * une {@code ItemStack} par identifiant affiché, pas par image.
 */
final class RegistryCatalog {

    private List<PickerState.Entry> items;
    private List<PickerState.Entry> blocks;
    private final Map<Identifier, ItemStack> icons = new HashMap<>();

    List<PickerState.Entry> items() {
        if (items == null) {
            List<PickerState.Entry> out = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                Identifier id = BuiltInRegistries.ITEM.getKey(item);
                out.add(new PickerState.Entry(id, new ItemStack(item).getHoverName().getString()));
            }
            out.sort(Comparator.comparing(PickerState.Entry::title));
            items = List.copyOf(out);
        }
        return items;
    }

    List<PickerState.Entry> blocks() {
        if (blocks == null) {
            List<PickerState.Entry> out = new ArrayList<>();
            for (Block block : BuiltInRegistries.BLOCK) {
                out.add(new PickerState.Entry(BuiltInRegistries.BLOCK.getKey(block),
                        block.getName().getString()));
            }
            out.sort(Comparator.comparing(PickerState.Entry::title));
            blocks = List.copyOf(out);
        }
        return blocks;
    }

    /** Icône d'une entrée ; {@code block} choisit le registre d'origine. */
    ItemStack icon(Identifier id, boolean block) {
        return icons.computeIfAbsent(id, key -> block
                ? new ItemStack(BuiltInRegistries.BLOCK.getValue(key).asItem())
                : new ItemStack(BuiltInRegistries.ITEM.getValue(key)));
    }
}
