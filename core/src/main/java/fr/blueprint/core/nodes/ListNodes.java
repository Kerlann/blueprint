package fr.blueprint.core.nodes;

import fr.blueprint.api.node.NodeCategories;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.api.registry.NodeRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Nœuds de listes (story 7.8). Sans eux, {@code for_each} n'aurait rien à parcourir :
 * le type {@code list<T>} existait depuis la story 1.2, mais aucun nœud ne savait en
 * produire ni en lire une.
 *
 * <p>Tous purs et tous génériques sur {@code T} : la résolution des jokers (FR5) fait
 * le reste, une {@code list<int>} ne se relie pas à une {@code list<string>}.
 *
 * <p><b>Immuables.</b> {@code add} et {@code remove} rendent une <b>nouvelle</b> liste
 * plutôt que de modifier la leur. Un nœud pur est mémoïsé et sa sortie peut être lue par
 * plusieurs consommateurs : muter la liste en place ferait dépendre le résultat de
 * l'ordre d'évaluation, c'est-à-dire de rien de compréhensible.
 */
public final class ListNodes {

    private ListNodes() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("blueprint", path);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> listOf(Object value) {
        return value instanceof List<?> list ? (List<Object>) list : List.of();
    }

    public static void register(NodeRegistry r) {
        // Construction : le point d'entrée de toute liste écrite à la main.
        r.register(NodeType.builder(id("list/of"))
                .category(NodeCategories.LIST_BUILD)
                .pure().generic("T")
                .in("a", PinTypes.generic("T"))
                .in("b", PinTypes.generic("T"))
                .in("c", PinTypes.generic("T"))
                .out("list", PinTypes.listOf(PinTypes.generic("T")))
                .action(ctx -> {
                    // Trois entrées, celles qui sont câblées comptent : un nœud à arité
                    // variable demanderait une API de pins dynamiques (v1.1).
                    List<Object> made = new ArrayList<>(3);
                    for (String pin : new String[]{"a", "b", "c"}) {
                        Object value = ctx.in(pin);
                        if (value != null) {
                            made.add(value);
                        }
                    }
                    ctx.out("list", List.copyOf(made));
                })
                .build());

        r.register(NodeType.builder(id("list/empty"))
                .category(NodeCategories.LIST_BUILD)
                .pure().generic("T")
                .out("list", PinTypes.listOf(PinTypes.generic("T")))
                .action(ctx -> ctx.out("list", List.of()))
                .build());

        r.register(NodeType.builder(id("list/size"))
                .category(NodeCategories.LIST_QUERY)
                .pure().generic("T")
                .in("list", PinTypes.listOf(PinTypes.generic("T")))
                .out("size", PinTypes.INT)
                .action(ctx -> ctx.out("size", listOf(ctx.in("list")).size()))
                .build());

        r.register(NodeType.builder(id("list/is_empty"))
                .category(NodeCategories.LIST_QUERY)
                .pure().generic("T")
                .in("list", PinTypes.listOf(PinTypes.generic("T")))
                .out("empty", PinTypes.BOOL)
                .action(ctx -> ctx.out("empty", listOf(ctx.in("list")).isEmpty()))
                .build());

        r.register(NodeType.builder(id("list/get"))
                .category(NodeCategories.LIST_QUERY)
                .pure().generic("T")
                .in("list", PinTypes.listOf(PinTypes.generic("T")))
                .in("index", PinTypes.INT, 0)
                .out("element", PinTypes.generic("T"))
                .action(ctx -> {
                    List<Object> list = listOf(ctx.in("list"));
                    int index = ctx.<Integer>in("index");
                    if (index < 0 || index >= list.size()) {
                        // Faute traduite, jamais d'exception : un index hors bornes est
                        // une erreur d'auteur de graphe, pas un crash de serveur.
                        ctx.fail(Component.translatable("blueprint.fault.index_out_of_range",
                                index, list.size()));
                        return;
                    }
                    ctx.out("element", list.get(index));
                })
                .build());

        r.register(NodeType.builder(id("list/contains"))
                .category(NodeCategories.LIST_QUERY)
                .pure().generic("T")
                .in("list", PinTypes.listOf(PinTypes.generic("T")))
                .in("value", PinTypes.generic("T"))
                .out("found", PinTypes.BOOL)
                .action(ctx -> {
                    Object value = ctx.in("value");
                    ctx.out("found", listOf(ctx.in("list")).stream()
                            .anyMatch(element -> Objects.equals(element, value)));
                })
                .build());

        r.register(NodeType.builder(id("list/index_of"))
                .category(NodeCategories.LIST_QUERY)
                .pure().generic("T")
                .in("list", PinTypes.listOf(PinTypes.generic("T")))
                .in("value", PinTypes.generic("T"))
                .out("index", PinTypes.INT)
                .action(ctx -> {
                    List<Object> list = listOf(ctx.in("list"));
                    Object value = ctx.in("value");
                    int found = -1;
                    for (int i = 0; i < list.size(); i++) {
                        if (Objects.equals(list.get(i), value)) {
                            found = i;
                            break;
                        }
                    }
                    ctx.out("index", found);   // -1 : absent, comme partout ailleurs
                })
                .build());

        r.register(NodeType.builder(id("list/add"))
                .category(NodeCategories.LIST_BUILD)
                .pure().generic("T")
                .in("list", PinTypes.listOf(PinTypes.generic("T")))
                .in("value", PinTypes.generic("T"))
                .out("result", PinTypes.listOf(PinTypes.generic("T")))
                .action(ctx -> {
                    List<Object> made = new ArrayList<>(listOf(ctx.in("list")));
                    made.add(ctx.in("value"));
                    ctx.out("result", List.copyOf(made));
                })
                .build());

        r.register(NodeType.builder(id("list/remove"))
                .category(NodeCategories.LIST_BUILD)
                .pure().generic("T")
                .in("list", PinTypes.listOf(PinTypes.generic("T")))
                .in("value", PinTypes.generic("T"))
                .out("result", PinTypes.listOf(PinTypes.generic("T")))
                .action(ctx -> {
                    Object value = ctx.in("value");
                    List<Object> made = new ArrayList<>();
                    boolean removed = false;
                    for (Object element : listOf(ctx.in("list"))) {
                        if (!removed && Objects.equals(element, value)) {
                            removed = true;   // la PREMIÈRE occurrence seulement
                            continue;
                        }
                        made.add(element);
                    }
                    ctx.out("result", List.copyOf(made));
                })
                .build());
    }
}
