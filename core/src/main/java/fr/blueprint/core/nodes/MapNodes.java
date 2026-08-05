package fr.blueprint.core.nodes;

import fr.blueprint.api.node.NodeCategories;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.api.registry.NodeRegistry;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dictionnaires (batch 6). Le type {@code map<K, V>} existait depuis la story 1.2 et
 * <b>aucun nœud ne l'utilisait</b> : il se déclarait sur un pin de mod tiers, et
 * aucun graphe ne pouvait en produire ni en lire un. Les listes avaient neuf nœuds ;
 * les dictionnaires, zéro.
 *
 * <p><b>Immuables</b>, comme les listes (7.8) et pour la même raison : un nœud pur est
 * mémoïsé et sa sortie lue par plusieurs consommateurs. Muter en place ferait dépendre
 * le résultat de l'ordre d'évaluation, c'est-à-dire de rien de compréhensible.
 *
 * <p>L'ordre d'insertion est <b>conservé</b> ({@code LinkedHashMap}). Un dictionnaire
 * dont l'itération change d'un lancement à l'autre produirait des graphes
 * non déterministes, et le déterminisme est une promesse du produit (NFR).
 */
public final class MapNodes {

    private MapNodes() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("blueprint", path);
    }

    public static void register(NodeRegistry r) {
        r.register(NodeType.builder(id("map/empty"))
                .category(NodeCategories.MAP_BUILD).pure().generic("K").generic("V")
                .out("map", PinTypes.mapOf(PinTypes.generic("K"), PinTypes.generic("V")))
                .action(ctx -> ctx.out("map", Map.of()))
                .build());

        r.register(NodeType.builder(id("map/put"))
                .category(NodeCategories.MAP_BUILD).pure().generic("K").generic("V")
                .in("map", PinTypes.mapOf(PinTypes.generic("K"), PinTypes.generic("V")))
                .in("key", PinTypes.generic("K"))
                .in("value", PinTypes.generic("V"))
                .out("map", PinTypes.mapOf(PinTypes.generic("K"), PinTypes.generic("V")))
                .action(ctx -> {
                    Map<Object, Object> source = map(ctx.in("map"));
                    Object key = ctx.in("key");
                    // Au plafond, seule une clé DÉJÀ présente peut encore être écrite :
                    // remplacer une valeur ne fait pas grossir la map. Même règle que
                    // list/add (ListNodes.MAX_ELEMENTS), même raison.
                    if (source.size() >= ListNodes.MAX_ELEMENTS && !source.containsKey(key)) {
                        ctx.out("map", Map.copyOf(source));
                        return;
                    }
                    Map<Object, Object> out = new LinkedHashMap<>(source);
                    out.put(key, ctx.in("value"));
                    ctx.out("map", Map.copyOf(out));
                })
                .build());

        r.register(NodeType.builder(id("map/remove"))
                .category(NodeCategories.MAP_BUILD).pure().generic("K").generic("V")
                .in("map", PinTypes.mapOf(PinTypes.generic("K"), PinTypes.generic("V")))
                .in("key", PinTypes.generic("K"))
                .out("map", PinTypes.mapOf(PinTypes.generic("K"), PinTypes.generic("V")))
                .action(ctx -> {
                    Map<Object, Object> out = new LinkedHashMap<>(map(ctx.in("map")));
                    out.remove(ctx.in("key"));
                    ctx.out("map", Map.copyOf(out));
                })
                .build());

        /*
         * « trouvé » à côté de la valeur, comme pour la recherche de joueur (batch 3).
         * Une clé absente rendrait un pin vide qui se propage en silence, et la faute
         * apparaîtrait trois nœuds plus loin — là où plus rien ne l'explique.
         */
        r.register(NodeType.builder(id("map/get"))
                .category(NodeCategories.MAP_QUERY).pure().generic("K").generic("V")
                .in("map", PinTypes.mapOf(PinTypes.generic("K"), PinTypes.generic("V")))
                .in("key", PinTypes.generic("K"))
                .in("fallback", PinTypes.generic("V"))
                .out("value", PinTypes.generic("V"))
                .out("found", PinTypes.BOOL)
                .action(ctx -> {
                    Map<Object, Object> source = map(ctx.in("map"));
                    Object key = ctx.in("key");
                    boolean found = source.containsKey(key);
                    ctx.out("found", found);
                    Object value = found ? source.get(key) : ctx.in("fallback");
                    if (value != null) {
                        ctx.out("value", value);
                    }
                })
                .build());

        r.register(NodeType.builder(id("map/has"))
                .category(NodeCategories.MAP_QUERY).pure().generic("K").generic("V")
                .in("map", PinTypes.mapOf(PinTypes.generic("K"), PinTypes.generic("V")))
                .in("key", PinTypes.generic("K"))
                .out("has", PinTypes.BOOL)
                .action(ctx -> ctx.out("has", map(ctx.in("map")).containsKey(ctx.in("key"))))
                .build());

        r.register(NodeType.builder(id("map/size"))
                .category(NodeCategories.MAP_QUERY).pure().generic("K").generic("V")
                .in("map", PinTypes.mapOf(PinTypes.generic("K"), PinTypes.generic("V")))
                .out("size", PinTypes.INT)
                .action(ctx -> ctx.out("size", map(ctx.in("map")).size()))
                .build());

        r.register(NodeType.builder(id("map/is_empty"))
                .category(NodeCategories.MAP_QUERY).pure().generic("K").generic("V")
                .in("map", PinTypes.mapOf(PinTypes.generic("K"), PinTypes.generic("V")))
                .out("empty", PinTypes.BOOL)
                .action(ctx -> ctx.out("empty", map(ctx.in("map")).isEmpty()))
                .build());

        /* Les deux ponts vers les listes : sans eux, « pour chaque » ne peut pas
         * parcourir un dictionnaire, et un dictionnaire ne servirait qu'à ranger. */
        r.register(NodeType.builder(id("map/keys"))
                .category(NodeCategories.MAP_QUERY).pure().generic("K").generic("V")
                .in("map", PinTypes.mapOf(PinTypes.generic("K"), PinTypes.generic("V")))
                .out("keys", PinTypes.listOf(PinTypes.generic("K")))
                .action(ctx -> ctx.out("keys", List.copyOf(map(ctx.in("map")).keySet())))
                .build());

        r.register(NodeType.builder(id("map/values"))
                .category(NodeCategories.MAP_QUERY).pure().generic("K").generic("V")
                .in("map", PinTypes.mapOf(PinTypes.generic("K"), PinTypes.generic("V")))
                .out("values", PinTypes.listOf(PinTypes.generic("V")))
                .action(ctx -> ctx.out("values", new ArrayList<>(map(ctx.in("map")).values())))
                .build());
    }

    /** Un pin dictionnaire vide vaut le dictionnaire vide, pas null. */
    @SuppressWarnings("unchecked")
    private static Map<Object, Object> map(Object value) {
        return value instanceof Map<?, ?> m ? (Map<Object, Object>) m : Map.of();
    }
}
