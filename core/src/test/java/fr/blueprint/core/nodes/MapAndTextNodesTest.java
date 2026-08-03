package fr.blueprint.core.nodes;

import fr.blueprint.api.node.NodeType;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dictionnaires, chaînes et maths (batch 6). Le type {@code map} existait depuis la
 * story 1.2 sans qu'aucun nœud ne l'utilise ; les chaînes se concaténaient sans
 * jamais se découper.
 */
class MapAndTextNodesTest {

    private static final PluginLoader.LoadedRegistries REGISTRIES =
            PluginLoader.load(List.of(), true);

    private static NodeType node(String path) {
        NodeType type = REGISTRIES.nodes()
                .get(Identifier.fromNamespaceAndPath("blueprint", path)).orElse(null);
        assertNotNull(type, "nœud absent du registre : " + path);
        return type;
    }

    private static Map<String, Object> run(String path, Map<String, Object> inputs) {
        return FakeNodeRun.run(node(path), inputs);
    }

    // ------------------------------------------------------------- dictionnaires

    @Test
    void placerLireEtRetirer() {
        Map<String, Object> made = run("map/put",
                Map.of("map", Map.of(), "key", "a", "value", 1));
        assertEquals(Map.of("a", 1), made.get("map"));

        Map<String, Object> got = run("map/get",
                Map.of("map", Map.of("a", 1), "key", "a", "fallback", 0));
        assertEquals(1, got.get("value"));
        assertEquals(true, got.get("found"));

        assertEquals(Map.of(), run("map/remove",
                Map.of("map", Map.of("a", 1), "key", "a")).get("map"));
    }

    /**
     * Une clé absente rend le repli ET dit « non trouvé ». Sans ce drapeau, on ne
     * distingue pas « la clé vaut zéro » de « la clé n'existe pas ».
     */
    @Test
    void uneCleAbsenteSeDistingueDUneValeurNulle() {
        Map<String, Object> got = run("map/get",
                Map.of("map", Map.of("a", 0), "key", "z", "fallback", -1));
        assertEquals(-1, got.get("value"));
        assertEquals(false, got.get("found"));

        Map<String, Object> zero = run("map/get",
                Map.of("map", Map.of("a", 0), "key", "a", "fallback", -1));
        assertEquals(0, zero.get("value"));
        assertEquals(true, zero.get("found"), "la clé EXISTE, même si sa valeur est zéro");
    }

    /**
     * Immuable, comme les listes. Un nœud pur est mémoïsé et sa sortie lue par
     * plusieurs consommateurs : muter en place ferait dépendre le résultat de l'ordre
     * d'évaluation.
     */
    @Test
    void placerNeModifiePasLeDictionnaireDOrigine() {
        Map<Object, Object> original = new LinkedHashMap<>();
        original.put("a", 1);
        run("map/put", Map.of("map", original, "key", "b", "value", 2));
        assertEquals(Map.of("a", 1), original, "l'entrée d'origine est intacte");
    }

    @Test
    void clesEtValeursSortentEnListesParcourables() {
        Map<Object, Object> source = new LinkedHashMap<>();
        source.put("x", 10);
        source.put("y", 20);
        assertEquals(List.of("x", "y"), run("map/keys", Map.of("map", source)).get("keys"));
        assertEquals(List.of(10, 20), run("map/values", Map.of("map", source)).get("values"));
    }

    @Test
    void tailleEtVacuite() {
        assertEquals(0, run("map/size", Map.of("map", Map.of())).get("size"));
        assertEquals(true, run("map/is_empty", Map.of("map", Map.of())).get("empty"));
        assertEquals(2, run("map/size", Map.of("map", Map.of("a", 1, "b", 2))).get("size"));
        assertEquals(true, run("map/has", Map.of("map", Map.of("a", 1), "key", "a")).get("has"));
    }

    // -------------------------------------------------------------------- chaînes

    @Test
    void decouperEtAssembler() {
        assertEquals(List.of("don", "pierre", "64"), run("string/split",
                Map.of("text", "don pierre 64", "separator", " ")).get("parts"));
        assertEquals("a-b-c", run("string/join",
                Map.of("parts", List.of("a", "b", "c"), "separator", "-")).get("text"));
    }

    /**
     * Découper sur le vide rendrait un caractère par élément et ferait exploser la
     * liste sur un texte long : le texte entier vaut mieux qu'une bombe silencieuse.
     */
    @Test
    void decouperSurLeVideRendLeTexteEntier() {
        assertEquals(List.of("abc"), run("string/split",
                Map.of("text", "abc", "separator", "")).get("parts"));
    }

    @Test
    void unSeparateurAbsentRendUnSeulMorceau() {
        assertEquals(List.of("abc"), run("string/split",
                Map.of("text", "abc", "separator", ",")).get("parts"));
    }

    /**
     * Les bornes d'extraction sont RAMENÉES : un index calculé par un graphe sort
     * facilement, et une exception pour « à partir du caractère 50 » sur un mot de
     * trois lettres n'aide personne.
     */
    @Test
    void extraireHorsBornesNeLevePas() {
        assertEquals("", run("string/substring",
                Map.of("text", "abc", "from", 50, "length", 5)).get("text"));
        assertEquals("abc", run("string/substring",
                Map.of("text", "abc", "from", 0, "length", 999)).get("text"));
        assertEquals("bc", run("string/substring",
                Map.of("text", "abc", "from", 1, "length", 2)).get("text"));
        assertEquals("", run("string/substring",
                Map.of("text", "abc", "from", 1, "length", -5)).get("text"),
                "une longueur négative rend le vide, elle ne remonte pas dans le texte");
    }

    @Test
    void remplacerEtNettoyer() {
        assertEquals("a_b", run("string/replace",
                Map.of("text", "a-b", "search", "-", "replacement", "_")).get("text"));
        assertEquals("a-b", run("string/replace",
                Map.of("text", "a-b", "search", "", "replacement", "X")).get("text"),
                "remplacer le vide insérerait entre chaque caractère : sans effet");
        assertEquals("abc", run("string/trim", Map.of("text", "  abc \n")).get("text"));
    }

    @Test
    void commenceEtFinitPar() {
        assertEquals(true, run("string/starts_with",
                Map.of("text", "minecraft:stone", "prefix", "minecraft:")).get("result"));
        assertEquals(true, run("string/ends_with",
                Map.of("text", "minecraft:stone", "suffix", "stone")).get("result"));
        assertEquals(false, run("string/ends_with",
                Map.of("text", "minecraft:stone", "suffix", "dirt")).get("result"));
    }

    /**
     * Un texte qui ne se convertit pas rend « non valide » plutôt qu'une faute : une
     * saisie de joueur est faillible par nature, et le graphe doit pouvoir répondre.
     */
    @Test
    void texteVersNombreDitSiCaAMarche() {
        Map<String, Object> ok = run("convert/to_number", Map.of("text", " 42.5 "));
        assertEquals(42.5, ok.get("value"));
        assertEquals(true, ok.get("valid"));

        Map<String, Object> ko = run("convert/to_number", Map.of("text", "soixante"));
        assertEquals(0.0, ko.get("value"));
        assertEquals(false, ko.get("valid"), "et surtout : pas de faute d'exécution");
    }

    // ---------------------------------------------------------------------- maths

    @Test
    void fonctionsNumeriques() {
        assertEquals(3.0, run("math/sqrt", Map.of("value", 9.0)).get("result"));
        assertEquals(2.0, run("math/floor", Map.of("value", 2.7)).get("result"));
        assertEquals(3.0, run("math/ceil", Map.of("value", 2.1)).get("result"));
        assertEquals(-1.0, run("math/sign", Map.of("value", -5.0)).get("result"));
        assertEquals(8.0, run("math/pow", Map.of("base", 2.0, "exponent", 3.0)).get("result"));
    }

    /**
     * Un NaN empoisonne tout ce qu'il touche sans lever la moindre erreur. Une racine
     * de négatif doit se DIRE — sinon la valeur traverse dix nœuds et l'auteur cherche
     * son origine à l'autre bout du graphe.
     */
    @Test
    void laRacineDunNegatifFauteAuLieuDeProduireUnNaN() {
        var context = FakeNodeRun.invoke(node("math/sqrt"), Map.of("value", -4.0));
        assertNotNull(context.failReason(), "elle doit fauter");
        assertNull(context.outputs().get("result"), "et ne rien produire");
    }

    /**
     * Des bornes inversées sont RÉORDONNÉES. {@code Math.clamp} lèverait, et l'auteur
     * d'un graphe ne saurait pas d'où vient l'exception.
     */
    @Test
    void bornerAccepteDesBornesInversees() {
        assertEquals(5.0, run("math/clamp",
                Map.of("value", 5.0, "min", 0.0, "max", 10.0)).get("result"));
        assertEquals(10.0, run("math/clamp",
                Map.of("value", 50.0, "min", 0.0, "max", 10.0)).get("result"));
        assertEquals(10.0, run("math/clamp",
                Map.of("value", 50.0, "min", 10.0, "max", 0.0)).get("result"),
                "bornes à l'envers : réordonnées, pas d'exception");
    }

    @Test
    void interpoler() {
        assertEquals(0.0, run("math/lerp",
                Map.of("from", 0.0, "to", 100.0, "t", 0.0)).get("result"));
        assertEquals(50.0, run("math/lerp",
                Map.of("from", 0.0, "to", 100.0, "t", 0.5)).get("result"));
        assertEquals(100.0, run("math/lerp",
                Map.of("from", 0.0, "to", 100.0, "t", 1.0)).get("result"));
    }

    @Test
    void trigonometrie() {
        assertEquals(0.0, (Double) run("math/sin", Map.of("value", 0.0)).get("result"), 1e-9);
        assertEquals(1.0, (Double) run("math/cos", Map.of("value", 0.0)).get("result"), 1e-9);
        assertEquals(Math.PI / 4, (Double) run("math/atan2",
                Map.of("y", 1.0, "x", 1.0)).get("angle"), 1e-9);
    }

    @Test
    void tousCesNoeudsSontPurs() {
        for (String path : List.of("map/empty", "map/put", "map/get", "map/keys",
                "string/split", "string/join", "string/substring", "convert/to_number",
                "math/sqrt", "math/clamp", "math/lerp", "math/atan2")) {
            assertTrue(node(path).pure(), path + " devrait être pur");
        }
    }
}
