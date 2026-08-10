package fr.blueprint.client.editor;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinType;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.Node;
import fr.blueprint.core.graph.NodeShape;
import fr.blueprint.core.graph.NodeTypeLookup;
import fr.blueprint.core.graph.VarNodes;
import fr.blueprint.core.graph.VarScope;
import fr.blueprint.core.graph.Variable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * État du panneau des variables (story 5.5). Pur : les mutations passent par
 * l'applicateur injecté (le contrôleur → pile d'annulation), le rendu vit dans
 * {@link VariablePanel}. Le retypage qui casserait des liens exige une
 * confirmation (FR10, U2) : premier clic = avertissement, second = application.
 */
public final class VariablePanelState {

    /**
     * Les types qu'une variable peut prendre, dans l'ordre où on les propose.
     *
     * <p><b>Le critère est la persistance</b>, pas la disponibilité. Un type de pin
     * existe pour bien d'autres choses — {@code itemstack}, {@code text},
     * {@code blockstate} — mais leur codec réclame les registres du jeu, que le format
     * plat de {@code VarStorage} n'a pas. Les proposer donnerait des variables dont la
     * valeur disparaît à chaque sauvegarde du monde : une case à cocher qui promet ce
     * qu'elle ne tient pas est pire que son absence.
     *
     * <p>Les types de <b>référence vivante</b> ({@code player}, {@code entity}) sont
     * exclus pour une autre raison : ils n'ont pas de littéral, donc pas de valeur par
     * défaut à donner à la variable, et une entité rangée dans une portée monde
     * désignerait un objet détruit dès la déconnexion.
     *
     * <p>BScript, lui, accepte déjà n'importe quel type enregistré — {@code var vec3 p},
     * {@code var list<vec3> chemin}. Cette liste ne borne que ce que l'éditeur propose
     * au clic.
     */
    public static final List<PinType> TYPES = List.of(
            PinTypes.DOUBLE, PinTypes.INT, PinTypes.LONG, PinTypes.BOOL, PinTypes.STRING,
            PinTypes.VEC3, PinTypes.BLOCKPOS, PinTypes.DIRECTION);

    /**
     * L'ordre du cycle va du <b>plus étroit au plus large</b> : graphe, joueur, joueur
     * partagé, monde, puis local à part. Cliquer plusieurs fois élargit la portée, ce qui
     * est le sens dans lequel on hésite — on commence chez soi et on ouvre si besoin,
     * jamais l'inverse.
     */
    public static final List<VarScope> SCOPE_CYCLE = List.of(VarScope.GRAPH, VarScope.PLAYER,
            VarScope.PLAYER_SHARED, VarScope.WORLD, VarScope.LOCAL);

    private final Blueprint bp;
    private final NodeTypeLookup lookup;
    private final Function<EditOperation, Boolean> applier;
    private final Runnable beginGesture;
    private final Runnable endGesture;

    private @Nullable String selected;
    private @Nullable String renaming;
    private String renameBuffer = "";
    /** Retypage en attente de confirmation : nom + type visé + liens cassés. */
    private @Nullable String pendingRetypeName;
    private @Nullable PinType pendingRetypeType;
    private int pendingBreaks;

    public VariablePanelState(Blueprint bp, NodeTypeLookup lookup,
                              Function<EditOperation, Boolean> applier) {
        this(bp, lookup, applier, () -> { }, () -> { });
    }

    public VariablePanelState(Blueprint bp, NodeTypeLookup lookup,
                              Function<EditOperation, Boolean> applier,
                              Runnable beginGesture, Runnable endGesture) {
        this.bp = bp;
        this.lookup = lookup;
        this.applier = applier;
        this.beginGesture = beginGesture;
        this.endGesture = endGesture;
    }

    /** Les variables, portée puis nom — l'ordre d'affichage du panneau. */
    public List<Variable> rows() {
        List<Variable> rows = new ArrayList<>(bp.variables().values());
        rows.sort(Comparator.comparing((Variable v) -> v.scope().ordinal())
                .thenComparing(Variable::name));
        return rows;
    }

    public @Nullable String selected() {
        return selected;
    }

    public void select(@Nullable String name) {
        selected = name;
        clearPending();
    }

    // ------------------------------------------------------------------- création

    /** Crée `varN` (double, portée GRAPH, défaut 0) et la sélectionne. */
    public @Nullable String create() {
        int n = 1;
        while (bp.variables().containsKey("var" + n)) {
            n++;
        }
        String name = "var" + n;
        boolean ok = applier.apply(new EditOperation.AddVariable(new Variable(name,
                PinTypes.DOUBLE, LiteralValue.of(PinTypes.DOUBLE, 0.0), VarScope.GRAPH, false)));
        if (!ok) {
            return null;
        }
        selected = name;
        return name;
    }

    public boolean delete(String name) {
        clearPending();
        boolean ok = applier.apply(new EditOperation.RemoveVariable(name));
        if (ok && name.equals(selected)) {
            selected = null;
        }
        return ok;
    }

    // ------------------------------------------------------------------ renommage

    public boolean isRenaming() {
        return renaming != null;
    }

    public @Nullable String renamingVar() {
        return renaming;
    }

    public String renameBuffer() {
        return renameBuffer;
    }

    public void openRename(String name) {
        renaming = name;
        renameBuffer = name;
        clearPending();
    }

    public void type(String text) {
        if (renaming != null) {
            renameBuffer += text;
        }
    }

    public void backspace() {
        if (renaming != null && !renameBuffer.isEmpty()) {
            renameBuffer = renameBuffer.substring(0, renameBuffer.length() - 1);
        }
    }

    public void cancelRename() {
        renaming = null;
        renameBuffer = "";
    }

    /** Applique le renommage ; les nœuds Get/Set liés suivent — UN SEUL geste d'annulation (QA 5.5). */
    public boolean commitRename() {
        if (renaming == null || renameBuffer.isBlank() || renameBuffer.equals(renaming)) {
            cancelRename();
            return false;
        }
        String from = renaming;
        String to = renameBuffer.trim();
        beginGesture.run();
        try {
            if (!applier.apply(new EditOperation.RenameVariable(from, to))) {
                return false; // doublon : le champ reste ouvert
            }
            // Reponter les nœuds liés — sans ça ils pointeraient un nom mort (FR10).
            for (Node node : List.copyOf(bp.nodes().values())) {
                if (VarNodes.isVarNode(node.typeId()) && from.equals(VarNodes.boundName(node))) {
                    applier.apply(new EditOperation.SetLiteral(node.uuid(), VarNodes.VAR_PIN,
                            LiteralValue.of(PinTypes.STRING, to)));
                }
            }
        } finally {
            endGesture.run();
        }
        if (from.equals(selected)) {
            selected = to;
        }
        cancelRename();
        return true;
    }

    // ------------------------------------------------------- type, portée (cycles)

    /**
     * Passe au type suivant de la liste. Reste le geste au clavier et le repli si le
     * menu ne s'ouvre pas ; le clic sur [T], lui, ouvre {@link TypeMenuState}.
     */
    public boolean cycleType(String name) {
        Variable variable = bp.variables().get(name);
        if (variable == null) {
            return false;
        }
        int index = TYPES.indexOf(variable.type());
        // Un type absent de la liste — posé par BScript, qui accepte tout le registre —
        // donne index = −1, donc le suivant est le premier. C'est le bon comportement :
        // on n'a pas de position dans un cycle dont on ne fait pas partie.
        return retypeTo(name, TYPES.get((index + 1 + TYPES.size()) % TYPES.size()));
    }

    /**
     * Donne à {@code name} le type demandé.
     *
     * <p>Si des liens en deviendraient incompatibles, le premier appel arme
     * l'avertissement ({@link #pendingBreaks}) et n'applique <b>rien</b> ; le second, avec
     * le même couple nom/type, applique (FR10, U2). Choisir dans un menu ne dispense pas
     * de cette confirmation — c'est le nombre de liens cassés qui la justifie, pas la
     * façon dont le type a été désigné.
     */
    public boolean retypeTo(String name, PinType target) {
        Variable variable = bp.variables().get(name);
        if (variable == null || target.equals(variable.type())) {
            return false;   // reposer le type courant n'est pas une modification
        }
        int breaks = breakingLinks(name, target);
        if (breaks > 0 && !(name.equals(pendingRetypeName) && target == pendingRetypeType)) {
            pendingRetypeName = name;
            pendingRetypeType = target;
            pendingBreaks = breaks;
            return false; // avertissement affiché, rien d'appliqué (U2)
        }
        clearPending();
        // Le défaut de l'ancien type ne survit pas : on repart du défaut du nouveau.
        return applier.apply(new EditOperation.RetypeVariable(name, target, target.defaultValue()));
    }

    /**
     * Pose ou retire {@code @replicated} (épic 21) : la valeur est envoyée aux clients en
     * lecture seule.
     *
     * <p>Une bascule et non un cycle, contrairement à la portée : il n'y a que deux états,
     * et l'opération <b>refuse</b> ce qui ne pourrait pas voyager — une portée locale, un
     * type sans codec réseau. Le refus remonte donc tel quel, et le panneau ne se retrouve
     * jamais à afficher un drapeau que le modèle n'a pas pris.
     */
    public boolean toggleReplicated(String name) {
        Variable variable = bp.variables().get(name);
        if (variable == null) {
            return false;
        }
        clearPending();
        return applier.apply(
                new EditOperation.SetReplicated(name, !variable.replicated()));
    }

    public boolean cycleScope(String name) {
        Variable variable = bp.variables().get(name);
        if (variable == null) {
            return false;
        }
        clearPending();
        int index = SCOPE_CYCLE.indexOf(variable.scope());
        VarScope next = SCOPE_CYCLE.get((index + 1 + SCOPE_CYCLE.size()) % SCOPE_CYCLE.size());
        return applier.apply(new EditOperation.SetScope(name, next));
    }

    public int pendingBreaks() {
        return pendingRetypeName != null ? pendingBreaks : 0;
    }

    public @Nullable String pendingRetypeName() {
        return pendingRetypeName;
    }

    private void clearPending() {
        pendingRetypeName = null;
        pendingRetypeType = null;
        pendingBreaks = 0;
    }

    /**
     * Combien de liens deviendraient incompatibles si {@code name} passait à
     * {@code newType} ? Sémantique variable (les pins des nœuds var sont `any` :
     * `canLink` ne peut pas trancher, l'assignabilité réelle si).
     */
    public int breakingLinks(String name, PinType newType) {
        int breaks = 0;
        for (Node node : bp.nodes().values()) {
            if (!VarNodes.isVarNode(node.typeId()) || !name.equals(VarNodes.boundName(node))) {
                continue;
            }
            boolean isGet = VarNodes.GET.equals(node.typeId());
            List<Link> links = isGet
                    ? bp.linksFrom(node.uuid(), "value")
                    : bp.linksInto(node.uuid(), "value");
            for (Link link : links) {
                NodeShape.PinDef other = isGet
                        ? pinDef(link.toNode(), link.toPin(), false)
                        : pinDef(link.fromNode(), link.fromPin(), true);
                if (other == null) {
                    continue;
                }
                boolean stillOk = isGet
                        ? other.type().isAssignableFrom(newType)
                        : newType.isAssignableFrom(other.type());
                if (!stillOk) {
                    breaks++;
                }
            }
        }
        return breaks;
    }

    private NodeShape.@Nullable PinDef pinDef(java.util.UUID node, String pin, boolean output) {
        Node n = bp.node(node);
        NodeShape shape = n == null ? null : lookup.shape(bp, n);
        if (shape == null) {
            return null;
        }
        return output ? shape.output(pin) : shape.input(pin);
    }
}
