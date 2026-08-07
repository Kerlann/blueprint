package fr.blueprint.core.graph;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Une fonction survit à la sauvegarde du monde (story 20.1, AC1).
 *
 * <p>Sans cette passe, une fonction vivait en mémoire et disparaissait au rechargement — le
 * corps entier, ses nœuds, ses liens. C'est la panne la plus coûteuse d'un modèle : elle ne
 * se voit qu'après un redémarrage, et ce qu'elle emporte ne se retrouve nulle part.
 */
class FunctionNbtTest {

    private static final PluginLoader.LoadedRegistries LOADED = PluginLoader.load(List.of(), true);
    private static final Identifier BP = Identifier.fromNamespaceAndPath("test", "nbt");

    private static Node node(String seed, String path) {
        return new Node(UUID.nameUUIDFromBytes(seed.getBytes()),
                Identifier.fromNamespaceAndPath("blueprint", path), new Vec2d(3, 4));
    }

    private static Blueprint withFunction() {
        Blueprint bp = new Blueprint(BP);
        Node param = node("p", "func/param");
        Node add = node("add", "math/add");
        Node result = node("r", "func/result");
        for (Node n : List.of(param, result)) {
            GraphLoader.setLiteral(n, FuncNodes.FUNCTION_PIN,
                    LiteralValue.of(PinTypes.STRING, "doubler"));
        }
        GraphLoader.setLiteral(add, "b", LiteralValue.of(PinTypes.DOUBLE, 2.0));

        Map<UUID, Node> nodes = new LinkedHashMap<>();
        for (Node n : List.of(param, add, result)) {
            nodes.put(n.uuid(), n);
        }
        Set<Link> links = new LinkedHashSet<>(List.of(
                new Link(param.uuid(), "exec_out", result.uuid(), "exec_in"),
                new Link(param.uuid(), "n", add.uuid(), "a"),
                new Link(add.uuid(), "result", result.uuid(), "resultat")));

        GraphLoader.addFunction(bp, BlueprintFunction.of("doubler",
                        List.of(new BlueprintFunction.Param("n", PinTypes.DOUBLE)),
                        List.of(new BlueprintFunction.Param("resultat", PinTypes.DOUBLE)))
                .withBody(nodes, links));
        return bp;
    }

    private static Blueprint roundTrip(Blueprint bp) {
        return GraphNbt.decode(GraphNbt.encode(bp),
                id -> LOADED.pinTypes().get(id).orElse(null));
    }

    /** Signature, corps, liens et littéraux : tout revient. */
    @Test
    void uneFonctionSurvitAuTour() {
        BlueprintFunction after = roundTrip(withFunction()).function("doubler");

        assertNotNull(after, "la fonction a disparu à la sauvegarde");
        assertEquals(1, after.inputs().size());
        assertEquals(PinTypes.DOUBLE, after.input("n").type(),
                "le TYPE d'un paramètre doit revenir : sans lui, la forme d'appel change "
                        + "et tous les liens deviennent faux");
        assertEquals(PinTypes.DOUBLE, after.output("resultat").type());
        assertEquals(3, after.nodes().size(), "le corps entier doit revenir");
        assertEquals(3, after.links().size());

        var add = after.nodes().values().stream()
                .filter(n -> n.typeId().getPath().equals("math/add")).findFirst().orElseThrow();
        assertEquals(2.0, add.literal("b").value(),
                "un littéral perdu au rechargement est la panne qui se remarque le plus tard");
    }

    /**
     * <b>Les nœuds d'un corps ne fuient pas dans le graphe principal.</b>
     *
     * <p>Le décodage passe par un blueprint jetable, parce que {@code decodeNode} sait poser
     * un nœud dans un {@code Blueprint}. Lui donner le vrai ferait entrer les nœuds du corps
     * dans la réserve principale — exactement ce que le modèle sépare, et le validateur les
     * verrait alors comme des nœuds orphelins du graphe.
     */
    @Test
    void leCorpsNeFuitPasDansLeGraphePrincipal() {
        Blueprint after = roundTrip(withFunction());

        assertTrue(after.nodes().isEmpty(),
                "les nœuds du corps sont remontés dans le graphe principal : " + after.nodes());
        assertEquals(3, after.function("doubler").nodes().size());
    }

    /**
     * Et le graphe entier reste égal à lui-même, fonctions comprises.
     *
     * <p>Par {@code contentEquals} et non {@code equals} : ni {@code Blueprint} ni
     * {@code Node} n'ont d'égalité générée. C'est aussi ce qui a rendu ce test rouge à sa
     * première écriture — comparer deux graphes par {@code equals} compare des identités,
     * donc échoue toujours, y compris quand tout est juste.
     */
    @Test
    void leBlueprintResteEgalALuiMeme() {
        Blueprint before = withFunction();
        assertTrue(before.contentEquals(roundTrip(before)),
                "l'égalité de contenu doit inclure les fonctions, sinon un graphe qui les "
                        + "perd passerait pour identique");
    }

    /** Et elle échoue bien quand une fonction manque : la garantie n'est pas creuse. */
    @Test
    void deuxGraphesQuiDifferentParUneFonctionNeSontPasEgaux() {
        Blueprint avec = withFunction();
        Blueprint sans = new Blueprint(BP);

        assertFalse(avec.contentEquals(sans),
                "un graphe qui a perdu ses fonctions ne doit pas passer pour identique");
    }
}
