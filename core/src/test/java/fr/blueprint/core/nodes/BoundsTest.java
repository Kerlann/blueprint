package fr.blueprint.core.nodes;

import fr.blueprint.api.node.NodeType;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Les bornes manquantes de la bibliothèque standard (épic 13a).
 *
 * <p>Le travail de bornage du projet était sérieux — {@code MAX_RADIUS}, {@code MAX_RESULTS},
 * {@code MAX_LENGTH}, {@code MAX_PARTS}, {@code MAX_LINES}, chacune commentée avec sa raison.
 * Quatre trous y subsistaient, et tous avaient l'allure d'oublis plutôt que de choix : un
 * nœud plus ancien que la borne de sa famille, deux nœuds jumeaux dont un seul clampait,
 * des collections <i>produites</i> bornées quand les collections <i>construites</i> ne
 * l'étaient pas.
 *
 * <p>Ce qui les rendait sérieux est ailleurs : <b>aucun nœud de la bibliothèque ne déclare
 * de {@code fuelCost}</b>, ils coûtent donc tous 1, et le budget d'un tick en autorise dix
 * mille. Une croissance exponentielle non bornée n'avait ainsi rien qui l'arrête.
 */
class BoundsTest {

    private static final PluginLoader.LoadedRegistries LOADED = PluginLoader.load(List.of(), true);

    private static NodeType node(String path) {
        return LOADED.nodes().get(Identifier.fromNamespaceAndPath("blueprint", path))
                .orElseThrow(() -> new AssertionError("nœud absent du registre : " + path));
    }

    private static String repeat(char c, int length) {
        return String.valueOf(c).repeat(length);
    }

    // ------------------------------------------------------------------ chaînes

    @Test
    void concatNeDepassePasLaLongueurMaximale() {
        String half = repeat('a', TextMathNodes.MAX_LENGTH);
        Map<String, Object> out = FakeNodeRun.run(node("string/concat"),
                Map.of("a", half, "b", half));
        assertEquals(TextMathNodes.MAX_LENGTH, ((String) out.get("result")).length());
    }

    /**
     * Le scénario réel : {@code s = concat(s, s)} dans une boucle.
     *
     * <p>Sans borne, la chaîne double à chaque tour — le gigaoctet en une trentaine
     * d'itérations, pour environ cent quatre-vingts fuel sur les dix mille d'un tick. Ce
     * test ne vérifie pas une longueur, il vérifie que <b>quarante doublements tiennent
     * en mémoire</b> : sans la borne, il ne finirait pas.
     */
    @Test
    void quaranteDoublementsNeFontPasExploserLaMemoire() {
        NodeType concat = node("string/concat");
        String s = "graine";
        for (int i = 0; i < 40; i++) {
            s = (String) FakeNodeRun.run(concat, Map.of("a", s, "b", s)).get("result");
            assertTrue(s.length() <= TextMathNodes.MAX_LENGTH,
                    "doublement " + i + " : " + s.length() + " caractères");
        }
        assertEquals(TextMathNodes.MAX_LENGTH, s.length());
    }

    /**
     * {@code string/replace} coupait <b>après</b> avoir construit le résultat entier.
     *
     * <p>Texte au plafond, motif d'un caractère, remplacement au plafond : le résultat
     * intermédiaire faisait de l'ordre du milliard de caractères — deux gigaoctets — avant
     * d'être ramené à 32 Ko. En un appel, à un fuel. Que ce test <b>finisse</b> est ce
     * qu'il vérifie ; la longueur n'en est que la trace.
     */
    @Test
    void replaceNeConstruitJamaisLeResultatDemesure() {
        String text = repeat('a', TextMathNodes.MAX_LENGTH);
        String replacement = repeat('b', TextMathNodes.MAX_LENGTH);
        Map<String, Object> out = FakeNodeRun.run(node("string/replace"),
                Map.of("text", text, "search", "a", "replacement", replacement));
        assertEquals(TextMathNodes.MAX_LENGTH, ((String) out.get("text")).length());
    }

    /** Le remplacement ordinaire n'est pas altéré par la borne. */
    @Test
    void replaceOrdinaireRendLeMemeResultatQuAvant() {
        Map<String, Object> out = FakeNodeRun.run(node("string/replace"),
                Map.of("text", "le chat et le chien", "search", "le ", "replacement", "un "));
        assertEquals("un chat et un chien", out.get("text"));
    }

    /** Un motif vide reste sans effet — remplacer entre chaque caractère n'aide personne. */
    @Test
    void replaceAvecMotifVideRendLeTexteIntact() {
        Map<String, Object> out = FakeNodeRun.run(node("string/replace"),
                Map.of("text", "intact", "search", "", "replacement", "x"));
        assertEquals("intact", out.get("text"));
    }

    // -------------------------------------------------------------- collections

    @Test
    void listAddSArreteAuPlafond() {
        List<Object> full = new ArrayList<>();
        for (int i = 0; i < ListNodes.MAX_ELEMENTS; i++) {
            full.add(i);
        }
        Map<String, Object> out = FakeNodeRun.run(node("list/add"),
                Map.of("list", List.copyOf(full), "value", 9999));
        assertEquals(ListNodes.MAX_ELEMENTS, ((List<?>) out.get("result")).size());
        assertTrue(!((List<?>) out.get("result")).contains(9999),
                "au plafond, l'ajout ne doit rien ajouter");
    }

    /** En deçà du plafond, l'ajout se comporte exactement comme avant. */
    @Test
    void listAddOrdinaireAjouteToujours() {
        Map<String, Object> out = FakeNodeRun.run(node("list/add"),
                Map.of("list", List.of(1, 2), "value", 3));
        assertEquals(List.of(1, 2, 3), out.get("result"));
    }

    @Test
    void mapPutSArreteAuPlafondMaisRemplaceEncore() {
        Map<Object, Object> full = new LinkedHashMap<>();
        for (int i = 0; i < ListNodes.MAX_ELEMENTS; i++) {
            full.put("k" + i, i);
        }
        NodeType put = node("map/put");

        // Clé nouvelle au plafond : refusée, la map ne grossit pas.
        Map<String, Object> grown = FakeNodeRun.run(put,
                Map.of("map", Map.copyOf(full), "key", "nouvelle", "value", 1));
        assertEquals(ListNodes.MAX_ELEMENTS, ((Map<?, ?>) grown.get("map")).size());

        // Clé DÉJÀ présente : écrite quand même — remplacer ne fait pas grossir, et
        // l'interdire figerait une map pleine, ce qui serait une borne mal placée.
        Map<String, Object> replaced = FakeNodeRun.run(put,
                Map.of("map", Map.copyOf(full), "key", "k0", "value", 42));
        Map<?, ?> after = (Map<?, ?>) replaced.get("map");
        assertEquals(ListNodes.MAX_ELEMENTS, after.size());
        assertEquals(42, after.get("k0"));
    }
}
