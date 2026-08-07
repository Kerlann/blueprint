package fr.blueprint.core.graph;

import fr.blueprint.api.node.Permission;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ce que le validateur doit voir dans un corps de fonction (story 20.1).
 *
 * <p>Le test qui compte est le premier. Les autres protègent des règles ordinaires ; celui-là
 * protège d'une <b>faille</b> : sans lui, une fonction serait une machine à blanchir les
 * permissions.
 */
class FunctionValidationTest {

    private static final PluginLoader.LoadedRegistries LOADED = PluginLoader.load(List.of(), true);

    private static Node node(String seed, String path) {
        return new Node(UUID.nameUUIDFromBytes(seed.getBytes()),
                Identifier.fromNamespaceAndPath("blueprint", path), new Vec2d(0, 0));
    }

    /** Un blueprint plafonné à {@code cap}, portant une fonction dont le corps est donné. */
    private static Blueprint with(Permission cap, String name, Node... body) {
        Blueprint bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "f"),
                new BlueprintMeta("", "", "1.0.0", cap));
        Map<UUID, Node> nodes = new LinkedHashMap<>();
        for (Node n : body) {
            nodes.put(n.uuid(), n);
        }
        GraphLoader.addFunction(bp, BlueprintFunction.of(name, List.of(), List.of())
                .withBody(nodes, new LinkedHashSet<>()));
        return bp;
    }

    private static List<DiagnosticCode> codes(Blueprint bp) {
        return GraphValidator.validate(bp, LOADED.nodes()).diagnostics().stream()
                .map(Diagnostic::code).toList();
    }

    /**
     * <b>La faille.</b> Un nœud {@code ADMIN} caché dans un corps de fonction, dans un
     * blueprint plafonné à {@code GAMEPLAY}.
     *
     * <p>Le validateur ne parcourait que le graphe principal. Une fonction aurait donc
     * permis d'exécuter du {@code ADMIN} depuis un graphe qui n'y a pas droit — sans
     * avertissement, sans trace, et sans que l'audit des nœuds {@code ADMIN} n'en sache
     * rien.
     */
    @Test
    void unNoeudAdminCacheDansUnCorpsNEchappePasAuPlafond() {
        Blueprint bp = with(Permission.GAMEPLAY, "tricher",
                node("admin", "world/set_block"));

        // Précaution : si ce nœud cessait d'être ADMIN, le test deviendrait vacueux sans
        // que rien ne le dise. On vérifie donc que le cas testé existe encore.
        var shape = LOADED.nodes().shape(
                Identifier.fromNamespaceAndPath("blueprint", "world/set_block"));
        assertTrue(!shape.permission().allowedUnder(Permission.GAMEPLAY),
                "ce test suppose un nœud AU-DESSUS de GAMEPLAY — celui-ci ne l'est plus");

        assertTrue(codes(bp).contains(DiagnosticCode.PERMISSION_EXCEEDED),
                "un corps de fonction n'est pas un angle mort du plafond de permission");
    }

    /** Une fonction s'appelle, elle ne se déclenche pas (AC2). */
    @Test
    void unEvenementDansUnCorpsEstRefuse() {
        Blueprint bp = with(Permission.GAMEPLAY, "impossible",
                node("evt", "event/player_join"));

        assertTrue(codes(bp).contains(DiagnosticCode.EVENT_IN_FUNCTION),
                "un point d'entrée dans un corps n'aurait aucun appelant, et ses slots "
                        + "appartiendraient à un cadre qui n'existe pas");
    }

    /** Un appel qui ne désigne rien se dit, et ne se devine pas (AC6). */
    @Test
    void unAppelVersUneFonctionAbsenteEstUneErreur() {
        Blueprint bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "f"));
        UUID uuid = UUID.randomUUID();
        assertTrue(new EditOperation.AddNode(uuid, FuncNodes.CALL, new Vec2d(0, 0))
                .apply(bp, LOADED.nodes()).applied());
        assertTrue(new EditOperation.SetLiteral(uuid, FuncNodes.FUNCTION_PIN,
                LiteralValue.of(PinTypes.STRING, "disparue"))
                .apply(bp, LOADED.nodes()).applied());

        assertTrue(codes(bp).contains(DiagnosticCode.FUNCTION_NOT_FOUND));
    }

    /** La récursion directe est refusée, et le message nomme le cycle (AC7). */
    @Test
    void uneFonctionQuiSAppelleElleMemeEstRefusee() {
        Blueprint bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "f"));
        GraphLoader.addFunction(bp, BlueprintFunction.of("boucle", List.of(), List.of()));
        Node appel = node("self", "func/call");
        GraphLoader.setLiteral(appel, FuncNodes.FUNCTION_PIN,
                LiteralValue.of(PinTypes.STRING, "boucle"));
        GraphLoader.addFunction(bp, bp.function("boucle")
                .withBody(Map.of(appel.uuid(), appel), Set.of()));

        var recursion = GraphValidator.validate(bp, LOADED.nodes()).diagnostics().stream()
                .filter(d -> d.code() == DiagnosticCode.FUNCTION_RECURSION).findFirst();

        assertTrue(recursion.isPresent(), "la récursion directe doit être refusée");
        assertEquals("boucle → boucle", recursion.get().args().get(0),
                "le message doit nommer le cycle, pas seulement l'annoncer");
    }

    /** La récursion mutuelle aussi — c'est celle qu'on ne voit pas en relisant. */
    @Test
    void deuxFonctionsQuiSAppellentMutuellementSontRefusees() {
        Blueprint bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "f"));
        for (String name : List.of("a", "b")) {
            GraphLoader.addFunction(bp, BlueprintFunction.of(name, List.of(), List.of()));
        }
        for (String[] pair : List.of(new String[]{"a", "b"}, new String[]{"b", "a"})) {
            Node appel = node(pair[0] + "->" + pair[1], "func/call");
            GraphLoader.setLiteral(appel, FuncNodes.FUNCTION_PIN,
                    LiteralValue.of(PinTypes.STRING, pair[1]));
            GraphLoader.addFunction(bp, bp.function(pair[0])
                    .withBody(Map.of(appel.uuid(), appel), Set.of()));
        }

        assertTrue(codes(bp).contains(DiagnosticCode.FUNCTION_RECURSION));
    }

    /** Et un corps ordinaire ne laisse rien derrière lui. */
    @Test
    void unCorpsCorrectNeProduitAucunDiagnostic() {
        Blueprint bp = with(Permission.GAMEPLAY, "additionner", node("add", "math/add"));

        assertTrue(GraphValidator.validate(bp, LOADED.nodes()).errors().isEmpty(),
                "un corps correct ne doit rien laisser à corriger : "
                        + GraphValidator.validate(bp, LOADED.nodes()).errors());
    }
}
