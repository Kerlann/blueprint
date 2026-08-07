package fr.blueprint.client.editor;

import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinKind;
import fr.blueprint.api.pin.PinType;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.BlueprintFunction;
import fr.blueprint.core.graph.BlueprintMeta;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.FunctionOps;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.Node;
import fr.blueprint.core.registry.NodeDescriptor;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * État et contenu du panneau de détails (story 5.10, le « Details » d'Unreal).
 * Pur : produit une liste de lignes typées que le widget dessine et route ; les
 * mutations (métadonnées) passent par l'applicateur injecté. Les traductions sont
 * injectées aussi — testable sans Minecraft.
 */
public final class DetailsPanelState {

    public enum Kind {
        HEADER, INFO, META_AUTHOR, META_DESCRIPTION, META_CAP, LITERAL, WIRED, NOTE,
        /** Un paramètre d'entrée de la fonction ouverte (story 20.2, AC4). */
        PARAM_IN,
        /** Un résultat de la fonction ouverte. */
        PARAM_OUT,
        /** Le « + » qui ajoute un paramètre, respectivement en entrée et en sortie. */
        PARAM_ADD_IN, PARAM_ADD_OUT
    }

    public enum MetaField { NONE, AUTHOR, DESCRIPTION }

    /** Une ligne du panneau ; {@code node}/{@code pin}/{@code type} selon le kind. */
    public record Row(Kind kind, String label, String value,
                      @Nullable UUID node, @Nullable String pin, @Nullable PinType type) {

        static Row info(String label, String value) {
            return new Row(Kind.INFO, label, value, null, null, null);
        }
    }

    private static final List<Permission> CAP_CYCLE = List.of(Permission.SAFE,
            Permission.GAMEPLAY, Permission.WORLD, Permission.ADMIN);

    private final Blueprint bp;
    private final Function<Identifier, NodeDescriptor> descriptors;
    private final Function<EditOperation, Boolean> applier;
    private final Function<String, String> translate;

    private MetaField metaEdit = MetaField.NONE;
    private String buffer = "";

    /**
     * Le graphe édité : les nœuds y sont cherchés, et le corps ouvert y est lu.
     *
     * <p>Sans elle, le panneau interrogeait {@code bp.node(id)} — le graphe principal. Un
     * nœud sélectionné dans un corps n'y existe pas : le panneau restait vide, et rien
     * n'expliquait pourquoi les détails avaient disparu.
     */
    private @Nullable GraphView view;

    /** Renommage d'un paramètre en cours : son indice et son côté. */
    private int paramEdit = -1;
    private boolean paramEditOutput;

    public DetailsPanelState(Blueprint bp, Function<Identifier, NodeDescriptor> descriptors,
                             Function<EditOperation, Boolean> applier,
                             Function<String, String> translate) {
        this.bp = bp;
        this.descriptors = descriptors;
        this.applier = applier;
        this.translate = translate;
    }

    /** Branche le panneau sur le graphe que le canevas montre (story 20.2). */
    public void follow(GraphView view) {
        this.view = view;
    }

    private @Nullable Node lookupNode(UUID id) {
        return view == null ? bp.node(id) : view.node(id);
    }

    private @Nullable BlueprintFunction openFunction() {
        String name = view == null ? null : view.function();
        return name == null ? null : bp.function(name);
    }

    // ------------------------------------------------------------------- contenu

    public List<Row> rows(Collection<UUID> selection) {
        if (selection.isEmpty()) {
            // Rien de sélectionné : le panneau décrit ce qu'on édite. Dans un corps, c'est
            // la SIGNATURE — la seule chose de la fonction qui ne se pose pas sur la toile,
            // et qu'il faudrait sinon écrire en BScript pour la changer.
            BlueprintFunction open = openFunction();
            return open == null ? blueprintRows() : signatureRows(open);
        }
        if (selection.size() > 1) {
            List<Row> rows = new ArrayList<>();
            rows.add(new Row(Kind.HEADER, translate.apply("blueprint.editor.details.multi"),
                    String.valueOf(selection.size()), null, null, null));
            rows.add(new Row(Kind.NOTE, "", translate.apply("blueprint.editor.details.multi_hint"),
                    null, null, null));
            return rows;
        }
        UUID id = selection.iterator().next();
        Node node = lookupNode(id);
        if (node == null) {
            return List.of();
        }
        NodeDescriptor desc = descriptors.apply(node.typeId());
        return desc == null ? ghostRows(node) : nodeRows(node, desc);
    }

    private List<Row> blueprintRows() {
        BlueprintMeta meta = bp.meta();
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(Kind.HEADER, translate.apply("blueprint.editor.details.blueprint"),
                bp.id().toString(), null, null, null));
        rows.add(new Row(Kind.META_AUTHOR, translate.apply("blueprint.editor.details.author"),
                metaEdit == MetaField.AUTHOR ? buffer + "_" : meta.author(), null, null, null));
        rows.add(new Row(Kind.META_DESCRIPTION, translate.apply("blueprint.editor.details.description"),
                metaEdit == MetaField.DESCRIPTION ? buffer + "_" : meta.description(), null, null, null));
        rows.add(Row.info(translate.apply("blueprint.editor.details.version"), meta.version()));
        rows.add(new Row(Kind.META_CAP, translate.apply("blueprint.editor.details.cap"),
                meta.permissionCap().name(), null, null, null));
        rows.add(Row.info(translate.apply("blueprint.editor.details.nodes"),
                bp.nodes().size() + " / " + bp.links().size() + " / " + bp.variables().size()));
        return rows;
    }

    /**
     * La signature de la fonction ouverte, éditable ligne à ligne (AC4).
     *
     * <p>Le vocabulaire est celui du panneau des variables — un clic sur le type le fait
     * tourner, un « × » retire, un double-clic renomme, un « + » ajoute — parce qu'un auteur
     * qui a appris l'un ne doit pas réapprendre l'autre.
     */
    private List<Row> signatureRows(BlueprintFunction function) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(Kind.HEADER, translate.apply("blueprint.editor.details.function"),
                function.name(), null, null, null));
        for (int i = 0; i < function.inputs().size(); i++) {
            rows.add(paramRow(Kind.PARAM_IN, function.inputs().get(i), i, false));
        }
        rows.add(new Row(Kind.PARAM_ADD_IN,
                translate.apply("blueprint.editor.details.add_param"), "+", null, null, null));
        for (int i = 0; i < function.outputs().size(); i++) {
            rows.add(paramRow(Kind.PARAM_OUT, function.outputs().get(i), i, true));
        }
        rows.add(new Row(Kind.PARAM_ADD_OUT,
                translate.apply("blueprint.editor.details.add_result"), "+", null, null, null));
        rows.add(new Row(Kind.NOTE, "",
                translate.apply("blueprint.editor.details.signature_hint"), null, null, null));
        return rows;
    }

    private Row paramRow(Kind kind, BlueprintFunction.Param param, int index, boolean output) {
        String label = paramEdit == index && paramEditOutput == output
                ? buffer + "_" : param.name();
        // Le rang voyage dans le pin : c'est lui que le clic renvoie, et chercher le
        // paramètre par son nom échouerait pendant qu'on est en train de le renommer.
        return new Row(kind, label, translate.apply(param.type().translationKey()),
                null, String.valueOf(index), param.type());
    }

    private List<Row> nodeRows(Node node, NodeDescriptor desc) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(Kind.HEADER, translate.apply(desc.titleKey()),
                node.typeId().toString(), null, null, null));
        rows.add(Row.info(translate.apply("blueprint.editor.details.category"), desc.category()));
        rows.add(Row.info(translate.apply("blueprint.editor.details.provider"),
                node.typeId().getNamespace()));
        rows.add(Row.info(translate.apply("blueprint.editor.details.permission"),
                desc.permission().name() + (desc.pure() ? " · pur" : "") + " · " + desc.fuelCost()));
        String description = translate.apply(desc.descKey());
        if (!description.equals(desc.descKey())) {
            rows.add(new Row(Kind.NOTE, "", description, null, null, null));
        }
        for (int i = 0; i < desc.inputs().size(); i++) {
            NodeDescriptor.PinDescriptor pin = desc.inputs().get(i);
            if (pin.kind() != PinKind.DATA) {
                continue;
            }
            Link wired = firstInto(node.uuid(), pin.name());
            if (wired != null) {
                Node source = bp.node(wired.fromNode());
                NodeDescriptor sourceDesc = source == null ? null : descriptors.apply(source.typeId());
                String title = sourceDesc != null ? translate.apply(sourceDesc.titleKey())
                        : source != null ? source.typeId().toString() : "?";
                rows.add(new Row(Kind.WIRED, pin.name(), "← " + title,
                        wired.fromNode(), pin.name(), pin.type()));
            } else {
                LiteralValue value = node.literal(pin.name());
                if (value == null) {
                    value = pin.defaultValue();
                }
                rows.add(new Row(Kind.LITERAL, pin.name(),
                        LiteralEditState.display(pin.type(), value),
                        node.uuid(), pin.name(), pin.type()));
            }
        }
        return rows;
    }

    private List<Row> ghostRows(Node node) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(Kind.HEADER, node.typeId().toString(), "", null, null, null));
        rows.add(Row.info(translate.apply("blueprint.editor.details.provider"),
                node.typeId().getNamespace()));
        rows.add(new Row(Kind.NOTE, "", translate.apply("blueprint.editor.ghost"),
                null, null, null));
        node.literals().forEach((pin, value) -> rows.add(Row.info(pin,
                LiteralEditState.display(value.type(), value))));
        return rows;
    }

    private @Nullable Link firstInto(UUID node, String pin) {
        for (Link link : bp.links()) {
            if (link.toNode().equals(node) && link.toPin().equals(pin)) {
                return link;
            }
        }
        return null;
    }

    // ------------------------------------------------------- édition des métadonnées

    public boolean isEditingMeta() {
        return metaEdit != MetaField.NONE;
    }

    public void openMetaEdit(MetaField field) {
        metaEdit = field;
        buffer = field == MetaField.AUTHOR ? bp.meta().author() : bp.meta().description();
    }

    /** La frappe sert aux deux champs — métadonnées et nom de paramètre. */
    public void type(String text) {
        if (metaEdit != MetaField.NONE || paramEdit >= 0) {
            buffer += text;
        }
    }

    public void backspace() {
        if ((metaEdit != MetaField.NONE || paramEdit >= 0) && !buffer.isEmpty()) {
            buffer = buffer.substring(0, buffer.length() - 1);
        }
    }

    public void cancelMetaEdit() {
        metaEdit = MetaField.NONE;
        buffer = "";
    }

    public boolean commitMetaEdit() {
        if (metaEdit == MetaField.NONE) {
            return false;
        }
        BlueprintMeta meta = bp.meta();
        BlueprintMeta next = metaEdit == MetaField.AUTHOR
                ? new BlueprintMeta(buffer, meta.description(), meta.version(), meta.permissionCap())
                : new BlueprintMeta(meta.author(), buffer, meta.version(), meta.permissionCap());
        cancelMetaEdit();
        return applier.apply(new EditOperation.SetMeta(next));
    }

    // ------------------------------------------------------- édition de la signature

    /**
     * Les types qu'un paramètre peut prendre, dans l'ordre du cycle.
     *
     * <p>Les mêmes que ceux d'une variable, et la même liste courte : faire tourner
     * dix-huit types à raison d'un clic chacun n'est pas un choix, c'est une roulette. Un
     * type absent d'ici s'écrit en BScript — comme pour les variables.
     */
    private static final List<PinType> PARAM_TYPES = List.of(
            PinTypes.DOUBLE, PinTypes.INT, PinTypes.BOOL, PinTypes.STRING,
            PinTypes.VEC3, PinTypes.BLOCKPOS, PinTypes.ENTITY, PinTypes.ITEMSTACK);

    /** Ajoute un paramètre nommé {@code pN} au bout du côté demandé. */
    public boolean addParam(boolean output) {
        BlueprintFunction f = openFunction();
        if (f == null) {
            return false;
        }
        List<BlueprintFunction.Param> side =
                new ArrayList<>(output ? f.outputs() : f.inputs());
        int n = 1;
        while (nameTaken(f, "p" + n)) {
            n++;
        }
        side.add(new BlueprintFunction.Param("p" + n, PinTypes.DOUBLE));
        return applySignature(f, output ? f.inputs() : side, output ? side : f.outputs());
    }

    public boolean removeParam(int index, boolean output) {
        BlueprintFunction f = openFunction();
        List<BlueprintFunction.Param> side = f == null ? null
                : new ArrayList<>(output ? f.outputs() : f.inputs());
        if (side == null || index < 0 || index >= side.size()) {
            return false;
        }
        cancelParamEdit();
        side.remove(index);
        return applySignature(f, output ? f.inputs() : side, output ? side : f.outputs());
    }

    /**
     * Fait tourner le type d'un paramètre.
     *
     * <p>Rien n'est réparé au passage : un lien devenu incompatible reste, et le validateur
     * le dit. Le corriger en douce serait la mutation cachée que ce projet évite partout —
     * et l'auteur qui vient de retyper est précisément celui qui saura quoi en faire.
     */
    public boolean cycleParamType(int index, boolean output) {
        BlueprintFunction f = openFunction();
        List<BlueprintFunction.Param> side = f == null ? null
                : new ArrayList<>(output ? f.outputs() : f.inputs());
        if (side == null || index < 0 || index >= side.size()) {
            return false;
        }
        BlueprintFunction.Param param = side.get(index);
        int at = PARAM_TYPES.indexOf(param.type());
        PinType next = PARAM_TYPES.get((at + 1) % PARAM_TYPES.size());
        side.set(index, new BlueprintFunction.Param(param.name(), next));
        return applySignature(f, output ? f.inputs() : side, output ? side : f.outputs());
    }

    public boolean isEditingParam() {
        return paramEdit >= 0;
    }

    public void openParamEdit(int index, boolean output) {
        BlueprintFunction f = openFunction();
        List<BlueprintFunction.Param> side = f == null ? List.of()
                : output ? f.outputs() : f.inputs();
        if (index < 0 || index >= side.size()) {
            return;
        }
        paramEdit = index;
        paramEditOutput = output;
        buffer = side.get(index).name();
    }

    public void cancelParamEdit() {
        paramEdit = -1;
        buffer = "";
    }

    /** Applique le renommage, ou l'abandonne si le nom est vide ou déjà pris. */
    public boolean commitParamEdit() {
        BlueprintFunction f = openFunction();
        if (paramEdit < 0 || f == null) {
            cancelParamEdit();
            return false;
        }
        List<BlueprintFunction.Param> side =
                new ArrayList<>(paramEditOutput ? f.outputs() : f.inputs());
        String name = buffer.trim();
        BlueprintFunction.Param param = side.get(paramEdit);
        if (name.isEmpty() || (!name.equals(param.name()) && nameTaken(f, name))) {
            cancelParamEdit();
            return false;
        }
        side.set(paramEdit, new BlueprintFunction.Param(name, param.type()));
        boolean output = paramEditOutput;
        cancelParamEdit();
        return applySignature(f, output ? f.inputs() : side, output ? side : f.outputs());
    }

    /**
     * Un nom de paramètre est un nom de <b>pin</b> : les deux côtés partagent l'espace.
     *
     * <p>Une entrée et une sortie du même nom donneraient deux pins homonymes sur le nœud
     * d'appel, dont l'un ne serait plus jamais désigné par un lien.
     */
    private static boolean nameTaken(BlueprintFunction f, String name) {
        return f.inputs().stream().anyMatch(p -> p.name().equals(name))
                || f.outputs().stream().anyMatch(p -> p.name().equals(name));
    }

    private boolean applySignature(BlueprintFunction f,
                                   List<BlueprintFunction.Param> inputs,
                                   List<BlueprintFunction.Param> outputs) {
        return applier.apply(new FunctionOps.SetSignature(f.name(),
                List.copyOf(inputs), List.copyOf(outputs)));
    }

    /** Le plafond de permission borne les nœuds admis : le changer revalide tout. */
    public boolean cyclePermissionCap() {
        BlueprintMeta meta = bp.meta();
        Permission next = CAP_CYCLE.get(
                (CAP_CYCLE.indexOf(meta.permissionCap()) + 1) % CAP_CYCLE.size());
        return applier.apply(new EditOperation.SetMeta(new BlueprintMeta(
                meta.author(), meta.description(), meta.version(), next)));
    }
}
