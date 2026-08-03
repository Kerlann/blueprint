package fr.blueprint.client.editor;

import fr.blueprint.core.graph.Node;
import fr.blueprint.core.graph.Vec2d;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Alignement de la sélection (touche Q, story 5.7). L'axe dominant décide : une
 * sélection plus haute que large devient une colonne, l'inverse une rangée. Logique
 * pure jusque-là couverte seulement de biais, par un cas du contrôleur.
 */
class AlignActionsTest {

    private static final Identifier TYPE = Identifier.fromNamespaceAndPath("test", "node");

    /** Boîte de dimensions fixes : seul son coin compte pour l'alignement. */
    private static NodeGeometry.Box boxAt(double x, double y) {
        Node node = new Node(UUID.randomUUID(), TYPE, new Vec2d(x, y));
        return new NodeGeometry.Box(node, x, y, 100, 40, false);
    }

    @Test
    void seulNeSAlignePasAvecPersonne() {
        assertTrue(AlignActions.align(List.of()).isEmpty());
        assertTrue(AlignActions.align(List.of(boxAt(0, 0))).isEmpty(),
                "un seul nœud : rien à aligner, et surtout rien à déplacer");
    }

    @Test
    void uneSelectionHauteDevientUneColonne() {
        // 40 de large, 400 de haut : l'axe vertical domine.
        NodeGeometry.Box haut = boxAt(30, 0);
        NodeGeometry.Box bas = boxAt(70, 400);
        Map<UUID, Vec2d> moves = AlignActions.align(List.of(bas, haut));

        assertEquals(2, moves.size());
        assertEquals(new Vec2d(30, 0), moves.get(haut.node().uuid()),
                "aligné sur le bord gauche de la boîte englobante");
        assertEquals(new Vec2d(30, 40 + AlignActions.GAP), moves.get(bas.node().uuid()),
                "et distribué d'une hauteur de nœud plus l'écart");
    }

    @Test
    void uneSelectionLargeDevientUneRangee() {
        NodeGeometry.Box gauche = boxAt(0, 15);
        NodeGeometry.Box droite = boxAt(500, 40);
        Map<UUID, Vec2d> moves = AlignActions.align(List.of(droite, gauche));

        assertEquals(new Vec2d(0, 15), moves.get(gauche.node().uuid()));
        assertEquals(new Vec2d(100 + AlignActions.GAP, 15), moves.get(droite.node().uuid()));
    }

    /**
     * L'ordre d'ENTRÉE ne doit rien changer : la sélection est un ensemble, et deux
     * appels sur la même sélection donneraient sinon deux dispositions différentes.
     */
    @Test
    void lOrdreDEntreeNeChangeRien() {
        NodeGeometry.Box a = boxAt(0, 0);
        NodeGeometry.Box b = boxAt(200, 10);
        NodeGeometry.Box c = boxAt(400, 5);

        assertEquals(AlignActions.align(List.of(a, b, c)),
                AlignActions.align(List.of(c, a, b)));
    }

    /**
     * Boîte englobante carrée : le cas limite doit trancher d'un côté, pas dépendre
     * de l'ordre de comparaison. La spécification dit colonne (>=).
     */
    @Test
    void uneSelectionCarreeDevientUneColonne() {
        // Boîtes de 100 × 40 : englobante carrée de 300 quand dy = dx + 60.
        Map<UUID, Vec2d> moves = AlignActions.align(List.of(boxAt(0, 0), boxAt(200, 260)));
        // Colonne : les deux abscisses sont égales.
        List<Double> xs = moves.values().stream().map(Vec2d::x).distinct().toList();
        assertEquals(1, xs.size(), "une colonne, donc une seule abscisse");
    }

    /** Aligner une sélection déjà alignée ne la bouge pas : l'opération est stable. */
    @Test
    void alignerDeuxFoisDonneLeMemeResultat() {
        List<NodeGeometry.Box> selection = List.of(boxAt(0, 0), boxAt(300, 8), boxAt(600, 3));
        Map<UUID, Vec2d> once = AlignActions.align(selection);

        List<NodeGeometry.Box> moved = selection.stream()
                .map(box -> {
                    Vec2d to = once.get(box.node().uuid());
                    return new NodeGeometry.Box(box.node(), to.x(), to.y(),
                            box.width(), box.height(), false);
                })
                .toList();
        Map<UUID, Vec2d> twice = AlignActions.align(moved);

        assertEquals(once, twice, "sinon Q déplacerait les nœuds à chaque pression");
    }
}
