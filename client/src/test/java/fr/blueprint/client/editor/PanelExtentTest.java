package fr.blueprint.client.editor;

import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.Node;
import fr.blueprint.core.graph.VarScope;
import fr.blueprint.core.graph.Variable;
import fr.blueprint.core.graph.Vec2d;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ce que les deux panneaux flottants occupent réellement, et ce qu'ils avalent.
 *
 * <p>Constaté en jeu : le panneau des variables peignait une colonne noire sur toute la
 * hauteur de l'écran pour un blueprint qui n'en a aucune, et la minimap montrait un cadre
 * de la taille d'une carte pour six points. Les deux amputaient le canevas sans rien
 * apprendre.
 *
 * <p>Le piège de la correction n'est pas le dessin mais le <b>clic</b> : un panneau qui
 * rétrécit à l'écran sans que sa zone cliquable suive laisse une bande invisible qui
 * avale les gestes du canevas. Rien ne l'expliquerait — le coin ne répondrait simplement
 * plus. C'est ce que ce test verrouille.
 */
class PanelExtentTest {

    private static final int HEIGHT = 400;

    private static VariablePanelState panelWith(int variables) {
        Blueprint bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "panneaux"));
        for (int i = 0; i < variables; i++) {
            fr.blueprint.core.graph.GraphLoader.addVariable(bp,
                    new Variable("v" + i, PinTypes.INT, null, VarScope.GRAPH, false));
        }
        return new VariablePanelState(bp, typeId -> null, op -> false);
    }

    private static List<NodeGeometry.Box> boxes(int count) {
        List<NodeGeometry.Box> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add(NodeGeometry.boxOf(new Node(UUID.randomUUID(),
                    Identifier.fromNamespaceAndPath("test", "n"), new Vec2d(i * 300, 0)), null));
        }
        return out;
    }

    // ------------------------------------------------------- panneau des variables

    /**
     * Un blueprint sans variable ne peint pas une colonne sur toute la hauteur. Le
     * panneau prend la place qu'il occupe, et rend le reste au canevas.
     */
    @Test
    void unPanneauVideNePrendPasTouteLaHauteur() {
        int empty = VariablePanel.bottom(panelWith(0), HEIGHT);
        int full = HEIGHT - DiagnosticsPanel.BAR_HEIGHT;

        assertTrue(empty < full / 2, () -> String.format(
                "vide, le panneau descend à %d sur %d disponibles", empty, full));
        assertTrue(empty > ToolbarWidget.HEIGHT + VariablePanel.HEADER_HEIGHT,
                "mais il garde son en-tête et de quoi écrire l'invite");
    }

    @Test
    void ilGrandissAvecSesVariables() {
        int empty = VariablePanel.bottom(panelWith(0), HEIGHT);
        int three = VariablePanel.bottom(panelWith(3), HEIGHT);
        int many = VariablePanel.bottom(panelWith(200), HEIGHT);

        assertTrue(three > empty, "trois variables occupent plus qu'aucune");
        assertTrue(many <= HEIGHT - DiagnosticsPanel.BAR_HEIGHT,
                "et deux cents ne débordent pas de l'écran : le panneau défile");
    }

    /**
     * <b>Le test qui compte.</b> Sous le panneau rétracté, le clic revient au canevas.
     * Sans cela, une bande invisible avalerait les gestes — et rien à l'écran ne dirait
     * pourquoi ce bord ne répond pas.
     */
    @Test
    void souLePanneauRetracteLeClicRevientAuCanevas() {
        VariablePanelState empty = panelWith(0);
        int bottom = VariablePanel.bottom(empty, HEIGHT);

        assertTrue(VariablePanel.contains(10, bottom - 2, empty, HEIGHT),
                "juste au-dessus du bord : c'est encore le panneau");
        assertFalse(VariablePanel.contains(10, bottom + 2, empty, HEIGHT),
                "juste en dessous : le canevas doit recevoir le clic");
        assertFalse(VariablePanel.contains(10, HEIGHT - DiagnosticsPanel.BAR_HEIGHT - 5,
                        empty, HEIGHT),
                "et tout en bas de l'ancienne colonne aussi");
    }

    /** Rempli, le panneau reprend la hauteur dont il a besoin — clic compris. */
    @Test
    void rempliLeClicPorteJusquenBas() {
        VariablePanelState many = panelWith(200);
        assertTrue(VariablePanel.contains(10, HEIGHT - DiagnosticsPanel.BAR_HEIGHT - 5,
                        many, HEIGHT),
                "deux cents variables : le panneau descend jusqu'à la barre du bas");
    }

    // ------------------------------------------------------------------- minimap

    /**
     * La minimap sert à se repérer dans ce qui dépasse de l'écran. Trois nœuds tiennent
     * toujours dans la vue : un cadre en bas à droite ne montrerait alors rien qu'on ne
     * voie déjà, tout en masquant un morceau de canevas.
     */
    @Test
    void laMinimapNapparaitQuAPartirDeQuelquesNoeuds() {
        assertFalse(Minimap.useful(boxes(0)));
        assertFalse(Minimap.useful(boxes(3)));
        assertTrue(Minimap.useful(boxes(Minimap.MIN_NODES)));
        assertTrue(Minimap.useful(boxes(40)));
    }

    /**
     * Masquée, elle n'avale plus les clics. Un cadre invisible qui capte la souris est
     * pire qu'un cadre visible qui ne sert à rien : le second se comprend, le premier
     * passe pour une panne.
     */
    @Test
    void masqueeLaMinimapNavaePlusLesClics() {
        int left = 100;
        int top = 100;

        assertFalse(Minimap.contains(left + 5, top + 5, left, top, boxes(2)),
                "deux nœuds : pas de minimap, donc pas de clic capté");
        assertTrue(Minimap.contains(left + 5, top + 5, left, top, boxes(10)),
                "dix nœuds : elle est là, et elle répond");
        assertFalse(Minimap.contains(left - 5, top + 5, left, top, boxes(10)),
                "hors du cadre, même affichée");
    }

    /**
     * La projection monde ↔ minimap reste réciproque quel que soit le graphe : c'est ce
     * qui fait qu'un clic téléporte la caméra là où on a visé, et non à côté.
     */
    @Test
    void laProjectionResteReciproque() {
        var bounds = NodeGeometry.boundsOf(boxes(12));

        for (double[] point : new double[][]{{0, 0}, {900, 0}, {3300, 0}}) {
            double[] mini = Minimap.toMini(bounds, point[0], point[1]);
            double[] back = Minimap.toWorld(bounds, mini[0], mini[1]);
            assertTrue(Math.abs(back[0] - point[0]) < 1e-6
                            && Math.abs(back[1] - point[1]) < 1e-6,
                    () -> "aller-retour non réciproque pour " + point[0] + ", " + point[1]);
        }
    }
}
