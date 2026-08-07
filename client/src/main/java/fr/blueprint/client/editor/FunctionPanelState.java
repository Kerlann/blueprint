package fr.blueprint.client.editor;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.BlueprintFunction;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.FuncNodes;
import fr.blueprint.core.graph.FunctionOps;
import fr.blueprint.core.graph.Vec2d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * État du panneau des fonctions (story 20.2). Pur : les mutations passent par
 * l'applicateur injecté — donc par la pile d'annulation — et le rendu vit dans
 * {@link FunctionPanel}.
 *
 * <p>Le jumeau de {@link VariablePanelState}, et volontairement : deux panneaux qui
 * listent, créent, renomment et suppriment doivent se manipuler de la même façon. Un
 * auteur qui a appris l'un connaît l'autre.
 */
public final class FunctionPanelState {

    private final Blueprint bp;
    private final Function<EditOperation, Boolean> applier;
    private final Runnable beginGesture;
    private final Runnable endGesture;

    private @Nullable String selected;
    private @Nullable String renaming;
    private String renameBuffer = "";
    /** Renommage en attente : nombre d'appels que le nom mort laisserait derrière. */
    private @Nullable String pendingRenameFrom;
    private int pendingBreaks;

    /**
     * Le corps que le canevas montre — la vérité vit dans {@code GraphView}, pas ici.
     *
     * <p>Le panneau ne fait que la <b>lire</b> pour marquer la ligne correspondante. La
     * dupliquer donnerait deux réponses à « quel graphe est ouvert », et la mauvaise
     * gagnerait un jour : un {@code Ctrl+Z} qui referme un corps ne passe pas par le
     * panneau.
     */
    private final java.util.function.Supplier<@Nullable String> openBody;

    public FunctionPanelState(Blueprint bp, Function<EditOperation, Boolean> applier) {
        this(bp, applier, () -> { }, () -> { }, () -> null);
    }

    public FunctionPanelState(Blueprint bp, Function<EditOperation, Boolean> applier,
                              Runnable beginGesture, Runnable endGesture,
                              java.util.function.Supplier<@Nullable String> openBody) {
        this.bp = bp;
        this.applier = applier;
        this.beginGesture = beginGesture;
        this.endGesture = endGesture;
        this.openBody = openBody;
    }

    /** Le corps actuellement ouvert dans le canevas, ou {@code null}. */
    public @Nullable String openBody() {
        return openBody.get();
    }

    /**
     * La fonction que l'onglet doit ouvrir en y arrivant : la sélectionnée, sinon la
     * première ; {@code null} si le blueprint n'en a aucune.
     *
     * <p>L'onglet Fonctions montre <b>une fonction</b>. Y arriver et voir le graphe qu'on
     * vient de quitter est le pire des affichages : rien ne distingue à l'œil « je n'ai pas
     * encore ouvert de corps » de « ce corps contient déjà tout ça », et les nœuds posés
     * tombent dans le graphe sous une étiquette qui annonce l'inverse.
     */
    public @Nullable String bodyToOpen() {
        if (selected != null && bp.functions().containsKey(selected)) {
            return selected;
        }
        List<BlueprintFunction> all = rows();
        return all.isEmpty() ? null : all.get(0).name();
    }

    /** Les fonctions, par nom — l'ordre d'affichage, et celui de l'export texte. */
    public List<BlueprintFunction> rows() {
        List<BlueprintFunction> rows = new ArrayList<>(bp.functions().values());
        rows.sort(Comparator.comparing(BlueprintFunction::name));
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

    /**
     * Crée {@code fonctionN}, <b>avec ses deux bords</b>, et la sélectionne.
     *
     * <p>Les bords ne sont pas décoratifs : sans {@code func/param}, un corps n'a pas
     * d'entrée et aucun appel ne peut l'atteindre ; sans {@code func/result}, il ne rend
     * rien. Les poser à la création évite qu'un auteur se retrouve devant une toile vide
     * sans savoir ce qu'il faut y mettre — et la palette ne les propose pas, parce qu'un
     * second {@code func/param} n'aurait aucun sens.
     *
     * <p>Un seul geste d'annulation pour les trois opérations : créer une fonction et
     * découvrir qu'il faut appuyer trois fois sur {@code Ctrl+Z} serait une surprise.
     */
    public @Nullable String create() {
        int n = 1;
        while (bp.functions().containsKey("fonction" + n)) {
            n++;
        }
        String name = "fonction" + n;
        beginGesture.run();
        try {
            if (!applier.apply(new FunctionOps.AddFunction(
                    BlueprintFunction.of(name, List.of(), List.of())))) {
                return null;
            }
            edge(name, FuncNodes.PARAM, -160, 0);
            edge(name, FuncNodes.RESULT, 160, 0);
        } finally {
            endGesture.run();
        }
        selected = name;
        return name;
    }

    private void edge(String function, net.minecraft.resources.Identifier typeId,
                      double x, double y) {
        UUID uuid = UUID.randomUUID();
        applier.apply(new FunctionOps.AddNodeIn(function, uuid, typeId, new Vec2d(x, y)));
        applier.apply(new FunctionOps.SetLiteralIn(function, uuid, FuncNodes.FUNCTION_PIN,
                LiteralValue.of(PinTypes.STRING, function)));
    }

    public boolean delete(String name) {
        clearPending();
        boolean ok = applier.apply(new FunctionOps.RemoveFunction(name));
        if (ok && name.equals(selected)) {
            selected = null;
        }
        return ok;
    }

    // ------------------------------------------------------------------ renommage

    public boolean isRenaming() {
        return renaming != null;
    }

    public @Nullable String renamingFunction() {
        return renaming;
    }

    public String renameBuffer() {
        return renameBuffer;
    }

    /** Le nombre d'appels qu'un renommage laisserait pointer dans le vide. */
    public int pendingBreaks() {
        return pendingBreaks;
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
        clearPending();
    }

    /**
     * Applique le renommage — <b>en prévenant d'abord</b> si des appels y perdent leur
     * cible (AC8).
     *
     * <p>Le premier appel arme l'avertissement, le second applique : la même mécanique que
     * le retypage d'une variable, et pour la même raison. Renommer ne réécrit pas les
     * littéraux des appels — c'est le précédent de {@code RenameVariable}, et le corriger
     * en douce serait la mutation cachée que ce projet évite partout.
     *
     * @return vrai si le renommage a été appliqué.
     */
    public boolean commitRename() {
        if (renaming == null || renameBuffer.isBlank() || renameBuffer.equals(renaming)) {
            cancelRename();
            return false;
        }
        String from = renaming;
        String to = renameBuffer.trim();
        int breaks = callsTo(from);
        if (breaks > 0 && !from.equals(pendingRenameFrom)) {
            pendingRenameFrom = from;
            pendingBreaks = breaks;
            return false;   // avertissement affiché, rien d'appliqué
        }
        if (!applier.apply(new FunctionOps.RenameFunction(from, to))) {
            return false;   // doublon : le champ reste ouvert
        }
        if (from.equals(selected)) {
            selected = to;
        }
        cancelRename();
        return true;
    }

    /**
     * Les nœuds d'appel visant cette fonction — dans le graphe <b>et dans les corps</b>.
     *
     * <p>Une fonction peut en appeler une autre : compter les seuls appels du graphe
     * principal annoncerait « aucun appel cassé » à un auteur dont le renommage va casser
     * ceux d'un corps.
     */
    public int callsTo(String function) {
        int count = 0;
        for (var node : bp.nodes().values()) {
            if (function.equals(FuncNodes.boundName(node))
                    && FuncNodes.isCall(node.typeId())) {
                count++;
            }
        }
        for (BlueprintFunction body : bp.functions().values()) {
            for (var node : body.nodes().values()) {
                if (function.equals(FuncNodes.boundName(node))
                        && FuncNodes.isCall(node.typeId())) {
                    count++;
                }
            }
        }
        return count;
    }

    private void clearPending() {
        pendingRenameFrom = null;
        pendingBreaks = 0;
    }
}
