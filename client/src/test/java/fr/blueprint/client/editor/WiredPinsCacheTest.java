package fr.blueprint.client.editor;

import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.GraphLimits;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.NodeShape;
import fr.blueprint.core.graph.NodeTypeLookup;
import fr.blueprint.core.graph.Vec2d;
import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.PinKind;
import fr.blueprint.api.pin.PinTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * « Ce pin est-il câblé ? » ne doit pas coûter un balayage de tous les liens.
 *
 * <p>La passe de rendu pose la question pour <b>chaque pin de chaque nœud visible</b>, et
 * trois fois par rangée — au test « faut-il un champ ici », au dessin du littéral, puis au
 * test « la sortie fait-elle face à un champ ». La réponse se cherchait en parcourant tous
 * les liens du graphe, et {@code Blueprint.links()} alloue une enveloppe non modifiable à
 * chaque appel : sur trente nœuds visibles et deux cent cinquante liens, cela faisait de
 * l'ordre de cent mille comparaisons et quelques centaines d'objets <b>par image</b>.
 *
 * <p>Le comble est que la méthode s'annonçait « sans allocation (appelé dans la passe de
 * rendu) ». Elle en allouait une par appel.
 *
 * <h2>Pourquoi une pente et non une durée</h2>
 *
 * <p>Les standards (§7.1) préfèrent « un rapport entre deux mesures prises au même
 * moment » : les deux subissent la même machine, donc leur rapport n'en dépend plus. La
 * propriété qu'on a réellement changée s'énonce en une phrase — <b>quadrupler le nombre de
 * liens ne doit pas quadrupler le coût d'une question qui n'en concerne aucun</b> — et
 * c'est vrai sur n'importe quelle machine, à n'importe quelle charge.
 */
class WiredPinsCacheTest {

    private static final Logger LOGGER = LoggerFactory.getLogger("blueprint-test");

    private static final Identifier TYPE = Identifier.fromNamespaceAndPath("test", "box");
    private static final int PINS = 6;

    private static final NodeShape SHAPE = new NodeShape(
            List.of(new NodeShape.PinDef("exec_in", PinKind.EXEC, PinTypes.EXEC, false),
                    data("in0"), data("in1"), data("in2"), data("in3"), data("in4")),
            List.of(new NodeShape.PinDef("exec_out", PinKind.EXEC, PinTypes.EXEC, false),
                    data("out")),
            false, Permission.SAFE);

    private static NodeShape.PinDef data(String name) {
        return new NodeShape.PinDef(name, PinKind.DATA, PinTypes.INT, false);
    }

    private static final NodeTypeLookup LOOKUP = id -> TYPE.equals(id) ? SHAPE : null;

    /** Un graphe de {@code nodes} nœuds, chacun recevant cinq liens de données du précédent. */
    private static CanvasController controller(int nodes) {
        Blueprint bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "g" + nodes));
        GraphLimits limits = new GraphLimits(nodes + 10);
        List<UUID> ids = new ArrayList<>(nodes);
        for (int i = 0; i < nodes; i++) {
            UUID uuid = UUID.nameUUIDFromBytes(("n" + nodes + "_" + i)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertTrue(new EditOperation.AddNode(uuid, TYPE, new Vec2d(i * 200, 0))
                    .apply(bp, LOOKUP, limits).applied());
            ids.add(uuid);
        }
        for (int i = 1; i < nodes; i++) {
            for (int p = 0; p < 5; p++) {
                assertTrue(new EditOperation.AddLink(
                        new Link(ids.get(i - 1), "out", ids.get(i), "in" + p))
                        .apply(bp, LOOKUP, limits).applied());
            }
        }
        return new CanvasController(bp, LOOKUP, new Camera());
    }

    /** Le nœud le PLUS RÉCENT n'a aucun lien entrant : la question porte toujours à vide. */
    private static UUID lonely(CanvasController c) {
        // Un nœud ajouté après coup, sans aucun lien — le pire cas pour un balayage
        // linéaire, qui doit alors parcourir la totalité avant de répondre « non ».
        UUID uuid = UUID.randomUUID();
        assertTrue(new EditOperation.AddNode(uuid, TYPE, new Vec2d(-500, -500))
                .apply(c.blueprint(), LOOKUP, new GraphLimits(100_000)).applied());
        return uuid;
    }

    private static long round(CanvasController c, UUID node) {
        long begin = System.nanoTime();
        for (int i = 0; i < 2_000; i++) {
            for (int p = 0; p < 5; p++) {
                assertFalse(c.isWired(node, "in" + p));
            }
        }
        return System.nanoTime() - begin;
    }

    /**
     * <b>Le test qui compte.</b> Quadrupler les liens ne quadruple pas le coût.
     *
     * <h2>La marge, mesurée</h2>
     *
     * <p>Voir le journal du test. Sans index, le rapport vaut celui des tailles — quatre ;
     * avec, il tombe vers un. Le seuil est posé à <b>2,0</b>, à mi-chemin, ce qui laisse un
     * facteur deux de chaque côté.
     */
    @Test
    void leCoutNeSuitPasLeNombreDeLiens() {
        CanvasController small = controller(50);
        CanvasController large = controller(200);
        UUID smallNode = lonely(small);
        UUID largeNode = lonely(large);

        for (int i = 0; i < 5; i++) {
            round(small, smallNode);
            round(large, largeNode);
        }
        long bestSmall = Long.MAX_VALUE;
        long bestLarge = Long.MAX_VALUE;
        for (int i = 0; i < 20; i++) {
            bestSmall = Math.min(bestSmall, round(small, smallNode));
            bestLarge = Math.min(bestLarge, round(large, largeNode));
        }

        double ratio = (double) bestLarge / Math.max(1, bestSmall);
        LOGGER.info("isWired : {} µs à {} liens, {} µs à {} liens — rapport {}",
                bestSmall / 1000, small.blueprint().links().size(),
                bestLarge / 1000, large.blueprint().links().size(),
                String.format(Locale.ROOT, "%.2f", ratio));

        assertTrue(bestSmall > 0 && bestLarge > 0, "mesure nulle : le banc ne mesure rien");
        assertTrue(ratio < 2.0, String.format(Locale.ROOT,
                "quadrupler les liens a multiplié le coût par %.2f — le balayage linéaire"
                        + " est probablement revenu (index de pins câblés ignoré ?)", ratio));
    }

    /** Et la réponse reste juste, index ou pas. */
    @Test
    void lIndexRepondCommeLeBalayage() {
        CanvasController c = controller(20);
        List<UUID> nodes = new ArrayList<>(c.blueprint().nodes().keySet());
        for (UUID node : nodes) {
            for (int p = 0; p < 5; p++) {
                String pin = "in" + p;
                boolean expected = c.blueprint().links().stream()
                        .anyMatch(l -> l.toNode().equals(node) && l.toPin().equals(pin));
                assertEquals(expected, c.isWired(node, pin),
                        "désaccord sur " + node + "/" + pin);
            }
        }
    }

    /** Un lien ajouté après coup se voit : le cache suit la révision du graphe. */
    @Test
    void leCacheSuitLesEditions() {
        CanvasController c = controller(10);
        UUID node = lonely(c);
        assertFalse(c.isWired(node, "in0"));

        UUID source = c.blueprint().nodes().keySet().iterator().next();
        assertTrue(new EditOperation.AddLink(new Link(source, "out", node, "in0"))
                .apply(c.blueprint(), LOOKUP, new GraphLimits(100_000)).applied());

        assertTrue(c.isWired(node, "in0"),
                "le cache n'a pas vu le lien ajouté — invalidation par révision cassée");
    }
}
