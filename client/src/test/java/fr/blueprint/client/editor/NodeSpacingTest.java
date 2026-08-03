package fr.blueprint.client.editor;

import fr.blueprint.core.script.AutoLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La géométrie des nœuds et l'espacement de la mise en page automatique doivent rester
 * d'accord.
 *
 * <p>Ils vivent dans deux modules qui ne peuvent pas se référencer : la géométrie est
 * cliente (elle sert au dessin), la mise en page est dans {@code core} (elle sert aussi
 * au parseur BScript, sans client). Le lien ne peut donc pas être une constante partagée
 * — il est tenu ici.
 *
 * <p>Sans ce test, agrandir les nœuds sans toucher à {@link AutoLayout} produirait des
 * graphes dont les nœuds se chevauchent dès l'import d'un {@code .bp} sans positions. Le
 * défaut ne se verrait ni à la compilation ni dans aucun test de comportement : seulement
 * à l'œil, sur un graphe importé, une fois la cause oubliée.
 */
class NodeSpacingTest {

    /** Un nœud raisonnablement chargé : huit rangées, ce qu'ont les plus gros nœuds. */
    private static final int TALL_NODE_ROWS = 8;

    private static double heightOf(int rows) {
        return NodeGeometry.TITLE_HEIGHT + rows * NodeGeometry.ROW_HEIGHT;
    }

    @Test
    void lEspacementVerticalLaissePasserUnNoeudChargé() {
        double tallest = heightOf(TALL_NODE_ROWS);

        assertTrue(AutoLayout.ROW > tallest, () -> String.format(
                "un nœud de %d rangées mesure %.0f de haut, mais AutoLayout.ROW vaut %.0f : "
                        + "deux nœuds empilés se toucheraient", TALL_NODE_ROWS, tallest,
                AutoLayout.ROW));
        assertTrue(AutoLayout.ROW - tallest >= 24,
                "il faut de l'air entre deux nœuds, pas seulement l'absence de collision");
    }

    @Test
    void lEspacementHorizontalLaissePasserUnNoeudEtSesFils() {
        assertTrue(AutoLayout.COLUMN > NodeGeometry.WIDTH, () -> String.format(
                "un nœud fait %.0f de large, la colonne %.0f : ils se recouvriraient",
                NodeGeometry.WIDTH, AutoLayout.COLUMN));
        // Les fils passent ENTRE les colonnes : sans marge, ils longeraient les nœuds et
        // on ne saurait plus lequel part d'où.
        assertTrue(AutoLayout.COLUMN - NodeGeometry.WIDTH >= 80,
                "il faut de la place pour les fils entre deux colonnes");
        assertTrue(AutoLayout.PURE_SHIFT > NodeGeometry.WIDTH,
                "un nœud pur décalé chevaucherait la colonne suivante");
    }

    // -------------------------------------------------- l'intérieur du nœud

    /**
     * <b>Le test qui compte.</b> Le libellé d'entrée, le champ et le libellé de sortie
     * partagent la même rangée. Ils étaient bornés par des fractions choisies à l'œil,
     * justes à 140 de large et fausses dès qu'on élargissait — le libellé recouvrait le
     * champ, et c'est arrivé au premier essai d'élargissement.
     */
    @Test
    void leLibelleEtLeChampNeSeRecouvrentJamais() {
        double w = NodeGeometry.WIDTH;
        double fieldLeft = w * NodeGeometry.LITERAL_LEFT;
        double fieldRight = w * NodeGeometry.LITERAL_RIGHT;

        double inputEnd = NodeGeometry.PIN_INSET + NodeGeometry.LABEL_GAP
                + NodeGeometry.inputLabelWidth(w, true);
        assertTrue(inputEnd <= fieldLeft, () -> String.format(
                "le libellé d'entrée finit à %.1f, le champ commence à %.1f",
                inputEnd, fieldLeft));

        double outputStart = w - NodeGeometry.PIN_INSET - NodeGeometry.LABEL_GAP
                - NodeGeometry.outputLabelWidth(w, true);
        assertTrue(outputStart >= fieldRight, () -> String.format(
                "le libellé de sortie commence à %.1f, le champ finit à %.1f",
                outputStart, fieldRight));
    }

    /** Sans champ sur la rangée, les deux libellés se partagent la largeur sans se toucher. */
    @Test
    void deuxLibellesSansChampNeSeTouchentPas() {
        double w = NodeGeometry.WIDTH;
        double inputEnd = NodeGeometry.PIN_INSET + NodeGeometry.LABEL_GAP
                + NodeGeometry.inputLabelWidth(w, false);
        double outputStart = w - NodeGeometry.PIN_INSET - NodeGeometry.LABEL_GAP
                - NodeGeometry.outputLabelWidth(w, false);

        assertTrue(inputEnd <= outputStart, () -> String.format(
                "entrée jusqu'à %.1f, sortie dès %.1f", inputEnd, outputStart));
    }

    /**
     * Un champ doit montrer plus de cinq caractères. C'est la plainte exacte du terrain :
     * à 140 de large, la zone utile faisait trente pixels — on ne pouvait pas relire ce
     * qu'on venait de taper.
     */
    @Test
    void unChampMontrePlusQueQuelquesCaracteres() {
        double narrow = NodeGeometry.WIDTH
                * (NodeGeometry.LITERAL_RIGHT - NodeGeometry.LITERAL_LEFT);
        double wide = NodeGeometry.WIDTH
                * (NodeGeometry.LITERAL_WIDE_RIGHT - NodeGeometry.LITERAL_LEFT);

        // ~6 px par caractère dans la police du jeu : cinquante pixels ≈ huit caractères.
        assertTrue(narrow >= 50, () -> "champ étroit : " + Math.round(narrow) + " px");
        assertTrue(wide >= 95, () -> "champ large : " + Math.round(wide) + " px");
    }

    /**
     * Un libellé doit rester lisible. Élargir le nœud pour rétrécir les libellés serait
     * un mauvais marché : les noms de pins sont ce qui dit à quoi sert chaque entrée.
     */
    @Test
    void unLibelleGardeLaPlaceDeSeLire() {
        double w = NodeGeometry.WIDTH;
        // ~6 px par caractère : quarante pixels ≈ sept caractères, la longueur médiane
        // d'un nom de pin du registre.
        assertTrue(NodeGeometry.inputLabelWidth(w, true) >= 40,
                () -> "entrée avec champ : " + Math.round(NodeGeometry.inputLabelWidth(w, true)));
        assertTrue(NodeGeometry.outputLabelWidth(w, true) >= 40,
                () -> "sortie avec champ : " + Math.round(NodeGeometry.outputLabelWidth(w, true)));

        // Les noms les PLUS longs du registre sont des booléens (« through_fluids »,
        // quatorze caractères). Leur case ne prend que douze pixels : le libellé doit
        // donc disposer de bien plus que dans le cas d'un champ de saisie.
        double checkbox = NodeGeometry.checkboxLabelWidth(w, true);
        assertTrue(checkbox >= 84, () -> "libellé de booléen : " + Math.round(checkbox)
                + " px, il en faut ~84 pour « through_fluids »");
        assertTrue(checkbox > NodeGeometry.inputLabelWidth(w, true),
                "une case laisse forcément plus de place qu'un champ de saisie");
    }

    /**
     * Un champ ne remplit pas sa rangée : la marge est ce qui le détache de celui du
     * dessus. Sans elle, les champs formaient une colonne continue et on ne voyait plus
     * lequel appartenait à quelle entrée.
     */
    @Test
    void unChampNeTouchePasCeluiDeLaRangeeVoisine() {
        var box = new NodeGeometry.Box(null, 0, 0, NodeGeometry.WIDTH, 100, false);
        var first = NodeGeometry.literalZone(box, 0);
        var second = NodeGeometry.literalZone(box, 1);

        assertTrue(second.top() - first.bottom() >= 4, () -> String.format(
                "seulement %.0f px entre deux champs voisins",
                second.top() - first.bottom()));
        assertEquals(NodeGeometry.ROW_HEIGHT - 2 * NodeGeometry.FIELD_INSET_Y,
                first.bottom() - first.top(), 1e-9, "hauteur du champ");
        assertTrue(first.bottom() - first.top() >= 9,
                "un champ doit rester plus haut qu'une ligne de texte");
    }
}
