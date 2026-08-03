package fr.blueprint.client.editor;

import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.Node;
import fr.blueprint.core.graph.NodeShape;
import fr.blueprint.core.graph.NodeTypeLookup;
import fr.blueprint.core.graph.Vec2d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Boîtes monde des nœuds d'un blueprint, dérivées de leur forme (les nœuds n'ont pas
 * de taille persistée). Suffisant pour le culling et les boîtes de niveau de détail
 * de la story 5.1 ; la 5.2 raffinera avec les littéraux inline.
 *
 * <p>Le calcul est mis en cache et invalidé par la révision du blueprint : la passe
 * de rendu ne reconstruit rien tant que le graphe ne change pas (coding-standards §5).
 */
public final class NodeGeometry {

    public static final double WIDTH = 140;
    public static final double TITLE_HEIGHT = 18;
    public static final double ROW_HEIGHT = 12;

    /** Distance du centre d'un pin au bord vertical de son nœud. */
    public static final double PIN_INSET = 7;

    /** Rangées présumées d'un nœud fantôme (forme inconnue). */
    private static final int GHOST_ROWS = 3;

    private final List<Box> boxes = new ArrayList<>();
    private int revision = -1;

    /** La liste retournée est réutilisée d'un appel à l'autre : ne pas la conserver. */
    public List<Box> boxes(Blueprint bp, NodeTypeLookup lookup) {
        if (bp.revision() != revision) {
            revision = bp.revision();
            boxes.clear();
            for (Node node : bp.nodes().values()) {
                boxes.add(boxOf(node, lookup.shape(node.typeId())));
            }
        }
        return boxes;
    }

    public static Box boxOf(Node node, @Nullable NodeShape shape) {
        int rows = shape == null
                ? GHOST_ROWS
                : Math.max(1, Math.max(shape.inputs().size(), shape.outputs().size()));
        return new Box(node, node.position().x(), node.position().y(),
                WIDTH, TITLE_HEIGHT + rows * ROW_HEIGHT, shape == null);
    }

    /** Boîte englobante d'un ensemble de boîtes — vide → rectangle nul à l'origine. */
    public static Camera.Rect boundsOf(List<Box> boxes) {
        if (boxes.isEmpty()) {
            return new Camera.Rect(0, 0, 0, 0);
        }
        double left = Double.MAX_VALUE;
        double top = Double.MAX_VALUE;
        double right = -Double.MAX_VALUE;
        double bottom = -Double.MAX_VALUE;
        for (int i = 0; i < boxes.size(); i++) {
            Box b = boxes.get(i);
            left = Math.min(left, b.x());
            top = Math.min(top, b.y());
            right = Math.max(right, b.x() + b.width());
            bottom = Math.max(bottom, b.y() + b.height());
        }
        return new Camera.Rect(left, top, right, bottom);
    }

    /**
     * Centre monde du pin d'entrée de la rangée {@code row} — les entrées vivent sur
     * le bord gauche, sous le titre. Sert au rendu et au câblage (5.3).
     */
    public static Vec2d inputPinCenter(Box box, int row) {
        return new Vec2d(box.x() + PIN_INSET, rowCenterY(box, row));
    }

    /** Centre monde du pin de sortie de la rangée {@code row}, sur le bord droit. */
    public static Vec2d outputPinCenter(Box box, int row) {
        return new Vec2d(box.x() + box.width() - PIN_INSET, rowCenterY(box, row));
    }

    private static double rowCenterY(Box box, int row) {
        return box.y() + TITLE_HEIGHT + row * ROW_HEIGHT + ROW_HEIGHT / 2;
    }

    /** Bord gauche de la zone littérale, en fraction de la largeur du nœud. */
    public static final double LITERAL_LEFT = 0.38;
    /**
     * Bord droit de la zone littérale. 0,60 et pas plus : un label de sortie
     * right-aligned peut commencer dès 0,62·largeur (QA 5.2b — chevauchement réel
     * sur math/add rangée 0 : entrée « a » + sortie « result »).
     */
    public static final double LITERAL_RIGHT = 0.60;

    /** Bord droit quand la rangée ne porte AUCUN pin de sortie : rien à chevaucher. */
    public static final double LITERAL_WIDE_RIGHT = 0.88;

    /**
     * Zone cliquable/rendue de la valeur littérale d'un pin d'entrée (5.2b) —
     * entre le label d'entrée et la colonne des sorties.
     */
    public static Camera.Rect literalZone(Box box, int row) {
        return literalZone(box, row, true);
    }

    /**
     * {@code rowHasOutput} : une rangée SANS pin de sortie n'a rien à sa droite, le champ
     * peut donc s'étendre presque jusqu'au bord. Sur un nœud de 140 px, la zone étroite
     * imposée par le risque de chevauchement (QA 5.2b) ne montrait que cinq caractères —
     * première plainte du terrain sur l'éditeur.
     */
    public static Camera.Rect literalZone(Box box, int row, boolean rowHasOutput) {
        double right = rowHasOutput ? LITERAL_RIGHT : LITERAL_WIDE_RIGHT;
        double top = box.y() + TITLE_HEIGHT + row * ROW_HEIGHT;
        return new Camera.Rect(box.x() + box.width() * LITERAL_LEFT, top,
                box.x() + box.width() * right, top + ROW_HEIGHT);
    }

    /** Boîte monde d'un nœud ; {@code ghost} = type inconnu du registre. */
    public record Box(Node node, double x, double y, double width, double height, boolean ghost) {

        public boolean contains(double wx, double wy) {
            return wx >= x && wx < x + width && wy >= y && wy < y + height;
        }
    }
}
