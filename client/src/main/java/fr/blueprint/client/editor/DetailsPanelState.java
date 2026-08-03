package fr.blueprint.client.editor;

import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinKind;
import fr.blueprint.api.pin.PinType;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.BlueprintMeta;
import fr.blueprint.core.graph.EditOperation;
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

    public enum Kind { HEADER, INFO, META_AUTHOR, META_DESCRIPTION, META_CAP, LITERAL, WIRED, NOTE }

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

    public DetailsPanelState(Blueprint bp, Function<Identifier, NodeDescriptor> descriptors,
                             Function<EditOperation, Boolean> applier,
                             Function<String, String> translate) {
        this.bp = bp;
        this.descriptors = descriptors;
        this.applier = applier;
        this.translate = translate;
    }

    // ------------------------------------------------------------------- contenu

    public List<Row> rows(Collection<UUID> selection) {
        if (selection.isEmpty()) {
            return blueprintRows();
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
        Node node = bp.node(id);
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

    public void type(String text) {
        if (metaEdit != MetaField.NONE) {
            buffer += text;
        }
    }

    public void backspace() {
        if (metaEdit != MetaField.NONE && !buffer.isEmpty()) {
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

    /** Le plafond de permission borne les nœuds admis : le changer revalide tout. */
    public boolean cyclePermissionCap() {
        BlueprintMeta meta = bp.meta();
        Permission next = CAP_CYCLE.get(
                (CAP_CYCLE.indexOf(meta.permissionCap()) + 1) % CAP_CYCLE.size());
        return applier.apply(new EditOperation.SetMeta(new BlueprintMeta(
                meta.author(), meta.description(), meta.version(), next)));
    }
}
