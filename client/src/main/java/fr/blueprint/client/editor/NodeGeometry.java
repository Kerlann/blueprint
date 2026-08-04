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

    /**
     * Les dimensions d'un nœud, <b>élargies</b>. À 140 × 12, un champ de valeur mesurait
     * trente pixels — moins de six caractères — et deux rangées voisines se touchaient,
     * si bien qu'on ne savait plus quel champ appartenait à quelle entrée.
     *
     * <p>Tout ce qui se place à l'intérieur se calcule désormais à partir de ces nombres,
     * jamais en fractions choisies à l'œil. C'est ce qui permet de les changer sans que
     * les libellés recouvrent les champs — ce qui est arrivé au premier essai.
     */
    public static final double WIDTH = 200;
    public static final double TITLE_HEIGHT = 22;
    public static final double ROW_HEIGHT = 16;

    /** Distance du centre d'un pin au bord vertical de son nœud. */
    public static final double PIN_INSET = 8;

    /** Espace entre un pin et le début de son libellé. */
    public static final double LABEL_GAP = 8;

    /**
     * Marge verticale d'un champ dans sa rangée. Sans elle, deux champs de rangées
     * voisines se touchaient bord à bord et formaient une colonne continue : l'auteur ne
     * voyait plus où finissait l'un et où commençait l'autre.
     */
    public static final double FIELD_INSET_Y = 3;

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

    /**
     * Largeur estimée d'un caractère, pour ajuster une pastille à son nom.
     *
     * <p>Une <b>estimation</b>, et c'est délibéré : mesurer le texte demanderait la
     * police, donc rendrait cette classe — celle que le rendu <i>et</i> le clic
     * partagent — invérifiable sans client. Un nom trop long est tronqué au dessin ; la
     * boîte, elle, reste la même des deux côtés, ce qui est la seule chose qui compte.
     */
    private static final double CHAR_WIDTH = 6;

    /** Marge autour du nom d'une pastille : le pin de sortie et l'air autour. */
    private static final double PILL_PADDING = 34;

    public static final double PILL_MIN_WIDTH = 70;

    /** Retrait vertical d'une pastille, au-dessus et au-dessous de sa rangée. */
    private static final double PILL_INSET_Y = 3;

    /**
     * Une <b>pastille</b> : la lecture d'une variable, à la manière d'Unreal.
     *
     * <p>Ni en-tête ni titre — juste le nom et sa sortie. Un nœud de lecture n'a rien à
     * dire de plus, et lui donner le châssis complet d'un nœud d'action revenait à
     * annoncer une machinerie là où il n'y a qu'une valeur. Dans un graphe qui en compte
     * vingt, c'est la moitié de la surface qui ne servait à rien.
     */
    public static boolean isPill(Node node) {
        return fr.blueprint.core.graph.VarNodes.GET.equals(node.typeId());
    }

    public static Box boxOf(Node node, @Nullable NodeShape shape) {
        if (isPill(node)) {
            var literal = node.literal("var");
            String name = literal != null && literal.value() instanceof String s ? s : "";
            double width = Math.clamp(PILL_PADDING + name.length() * CHAR_WIDTH,
                    PILL_MIN_WIDTH, WIDTH);
            return new Box(node, node.position().x(), node.position().y(),
                    width, ROW_HEIGHT + 2 * PILL_INSET_Y, shape == null);
        }
        int rows = shape == null
                ? GHOST_ROWS
                : Math.max(1, Math.max(shape.inputs().size(), shape.outputs().size()));
        return new Box(node, node.position().x(), node.position().y(),
                WIDTH, TITLE_HEIGHT + rows * ROW_HEIGHT, shape == null);
    }

    /**
     * Décalage vertical avant la première rangée de <b>cette</b> boîte : la hauteur de
     * l'en-tête pour un nœud ordinaire, le simple retrait d'une pastille — qui n'en a pas.
     *
     * <p>Tout ce qui place quelque chose dans un nœud passe par ici plutôt que par la
     * constante. C'est la règle que ce projet a apprise à ses dépens : quand le dessin et
     * le clic calculent séparément la même coordonnée, ils finissent par diverger, et
     * tout <i>a l'air</i> juste.
     */
    public static double titleHeight(Box box) {
        return isPill(box.node()) ? PILL_INSET_Y : TITLE_HEIGHT;
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
        // titleHeight(box) et non la constante : une pastille n'a pas de bandeau, et son
        // pin doit tomber au milieu de sa seule rangée. Le dessin lit la même fonction —
        // c'est la règle que ce projet a apprise à ses dépens.
        return box.y() + titleHeight(box) + row * ROW_HEIGHT + ROW_HEIGHT / 2;
    }

    /** Bord gauche de la zone littérale, en fraction de la largeur du nœud. */
    public static final double LITERAL_LEFT = 0.34;

    /**
     * Bord droit de la zone littérale quand la rangée porte AUSSI une sortie.
     *
     * <p>La contrainte réelle n'a jamais été ce nombre mais le libellé de sortie, aligné
     * à droite. On la tenait en gardant le champ étroit — d'où trente pixels utilisables,
     * la première plainte du terrain sur l'éditeur. Les libellés sont désormais
     * <b>bornés par le champ</b> ({@link #inputLabelWidth}, {@link #outputLabelWidth}) :
     * c'est le champ qui décide, et il peut donc s'élargir sans rien recouvrir.
     */
    public static final double LITERAL_RIGHT = 0.66;

    /** Bord droit quand la rangée ne porte AUCUN pin de sortie : rien à chevaucher. */
    public static final double LITERAL_WIDE_RIGHT = 0.92;

    /**
     * Place disponible pour le libellé d'une entrée, en unités de monde.
     *
     * <p>Calculée depuis le bord du champ quand la rangée en porte un, et non par une
     * fraction fixe : {@code w / 2 - 14} tenait par coïncidence à 140 de large et
     * recouvrait le champ dès qu'on élargissait le nœud.
     */
    public static double inputLabelWidth(double nodeWidth, boolean rowHasLiteral) {
        return Math.max(8, labelSplitLeft(nodeWidth, rowHasLiteral) - PIN_INSET - LABEL_GAP);
    }

    /** Idem pour une sortie, alignée à droite : c'est son bord GAUCHE que le champ borne. */
    public static double outputLabelWidth(double nodeWidth, boolean rowHasLiteral) {
        return Math.max(8, nodeWidth - PIN_INSET - LABEL_GAP
                - labelSplitRight(nodeWidth, rowHasLiteral));
    }

    /**
     * Largeur d'une case à cocher, bord compris. Un booléen ne se saisit pas : il se
     * clique. Son « champ » n'occupe donc que ce carré, et le libellé peut s'étendre
     * jusque-là.
     *
     * <p>Ce n'est pas un détail : les noms de pins les plus longs du registre sont
     * justement des booléens ({@code through_fluids}, {@code thundering}). Les traiter
     * comme des champs de saisie leur laissait sept caractères sur quatorze.
     */
    public static final double CHECKBOX_WIDTH = 12;

    /** Où le libellé d'entrée doit s'arrêter : au champ, ou au milieu de la rangée. */
    private static double labelSplitLeft(double nodeWidth, boolean rowHasLiteral) {
        return rowHasLiteral ? nodeWidth * LITERAL_LEFT - 4
                : nodeWidth / 2 - LABEL_GAP / 2;
    }

    /**
     * Place pour le libellé d'une entrée dont la valeur est une <b>case à cocher</b> :
     * tout l'espace jusqu'à la case, qui reste calée sur le bord droit de la zone.
     */
    public static double checkboxLabelWidth(double nodeWidth, boolean rowHasOutput) {
        double right = nodeWidth * (rowHasOutput ? LITERAL_RIGHT : LITERAL_WIDE_RIGHT)
                - CHECKBOX_WIDTH - 4;
        return Math.max(8, right - PIN_INSET - LABEL_GAP);
    }

    /**
     * Où le libellé de sortie peut commencer. Sans champ, les deux libellés se partagent
     * la rangée avec {@link #LABEL_GAP} au milieu — un partage à 55 % / 45 %, écrit sans
     * y penser au premier essai, les faisait se chevaucher de dix-huit pixels.
     */
    private static double labelSplitRight(double nodeWidth, boolean rowHasLiteral) {
        return rowHasLiteral ? nodeWidth * LITERAL_RIGHT + 4
                : nodeWidth / 2 + LABEL_GAP / 2;
    }

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
        // Le champ ne remplit plus toute la rangée : la marge est ce qui le détache de
        // celui du dessus, et ce qui fait qu'on voit à quelle entrée il appartient.
        double top = box.y() + TITLE_HEIGHT + row * ROW_HEIGHT + FIELD_INSET_Y;
        return new Camera.Rect(box.x() + box.width() * LITERAL_LEFT, top,
                box.x() + box.width() * right, top + ROW_HEIGHT - 2 * FIELD_INSET_Y);
    }

    /** Boîte monde d'un nœud ; {@code ghost} = type inconnu du registre. */
    public record Box(Node node, double x, double y, double width, double height, boolean ghost) {

        public boolean contains(double wx, double wy) {
            return wx >= x && wx < x + width && wy >= y && wy < y + height;
        }
    }
}
