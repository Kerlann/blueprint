package fr.blueprint.core.graph;

import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;

import java.util.UUID;
import java.util.function.Function;

/**
 * Sérialisation NBT du graphe. Deux règles absolues :
 *
 * <ul>
 * <li><b>Le chargement ne refuse rien</b> (principe P4) : reconstruction par mutation
 *     directe du modèle, jamais par {@link EditOperation} — une op refuserait un graphe
 *     fantôme, or un mod retiré ne doit rien détruire.</li>
 * <li><b>Ce qui ne se résout pas se conserve</b> : un littéral ou une variable dont le
 *     type de pin est irrésoluble est gardé en NBT brut et ré-émis tel quel ; le mod
 *     réinstallé retrouve tout.</li>
 * </ul>
 */
public final class GraphNbt {

    private GraphNbt() {
    }

    // ------------------------------------------------------------------ encode

    public static CompoundTag encode(Blueprint bp) {
        CompoundTag root = new CompoundTag();
        root.putInt("schema", SchemaMigrations.CURRENT);
        root.putString("id", bp.id().toString());
        root.putBoolean("enabled", bp.enabled());
        root.putInt("revision", bp.revision());

        CompoundTag meta = new CompoundTag();
        meta.putString("author", bp.meta().author());
        meta.putString("description", bp.meta().description());
        meta.putString("version", bp.meta().version());
        meta.putString("permission", bp.meta().permissionCap().name());
        root.put("meta", meta);

        ListTag nodes = new ListTag();
        for (Node node : bp.nodes().values()) {
            nodes.add(encodeNode(node));
        }
        root.put("nodes", nodes);

        ListTag links = new ListTag();
        for (Link link : bp.links()) {
            CompoundTag l = new CompoundTag();
            l.putString("from", link.fromNode().toString());
            l.putString("fromPin", link.fromPin());
            l.putString("to", link.toNode().toString());
            l.putString("toPin", link.toPin());
            links.add(l);
        }
        root.put("links", links);

        ListTag variables = new ListTag();
        for (Variable variable : bp.variables().values()) {
            variables.add(encodeVariable(variable));
        }
        // Les variables au type irrésoluble repartent telles qu'elles sont arrivées.
        for (Tag preserved : bp.preservedVariables()) {
            variables.add(preserved.copy());
        }
        root.put("variables", variables);

        ListTag comments = new ListTag();
        for (CommentBox box : bp.comments()) {
            CompoundTag c = new CompoundTag();
            c.putString("uuid", box.uuid().toString());
            c.putString("text", box.text());
            c.putDouble("x", box.position().x());
            c.putDouble("y", box.position().y());
            c.putDouble("w", box.size().x());
            c.putDouble("h", box.size().y());
            c.putInt("color", box.color());
            comments.add(c);
        }
        root.put("screens", ScreenNbt.encode(bp));
        root.put("comments", comments);
        return root;
    }

    private static CompoundTag encodeNode(Node node) {
        CompoundTag n = new CompoundTag();
        n.putString("uuid", node.uuid().toString());
        n.putString("type", node.typeId().toString());
        n.putDouble("x", node.position().x());
        n.putDouble("y", node.position().y());
        // UNE seule lecture : node.config() rend une copie profonde (Node.config()), et en
        // appeler deux en construisait deux — dont une intégralement jetée par le test.
        net.minecraft.nbt.CompoundTag config = node.config();
        if (!config.isEmpty()) {
            n.put("config", config);
        }
        ListTag literals = new ListTag();
        node.literals().forEach((pin, literal) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("pin", pin);
            entry.put("type", PinTypeNbt.encode(literal.type()));
            entry.put("value", literal.encode(NbtOps.INSTANCE).getOrThrow(
                    message -> new IllegalStateException("Littéral inencodable sur « " + pin + " » : " + message)));
            literals.add(entry);
        });
        for (Tag preserved : node.preservedLiterals()) {
            literals.add(preserved.copy());
        }
        if (!literals.isEmpty()) {
            n.put("literals", literals);
        }
        return n;
    }

    private static CompoundTag encodeVariable(Variable variable) {
        CompoundTag v = new CompoundTag();
        v.putString("name", variable.name());
        v.put("type", PinTypeNbt.encode(variable.type()));
        v.putString("scope", variable.scope().name());
        v.putBoolean("replicated", variable.replicated());
        if (variable.defaultValue() != null) {
            v.put("default", variable.defaultValue().encode(NbtOps.INSTANCE).getOrThrow(
                    message -> new IllegalStateException("Défaut inencodable de « " + variable.name() + " » : " + message)));
        }
        return v;
    }

    // ------------------------------------------------------------------ decode

    public static Blueprint decode(CompoundTag rawRoot, Function<Identifier, PinType> types) {
        return decode(rawRoot, types, SchemaMigrations.DEFAULT);
    }

    public static Blueprint decode(CompoundTag rawRoot, Function<Identifier, PinType> types,
                                   SchemaMigrations migrations) {
        CompoundTag root = migrations.migrate(rawRoot);

        Identifier id = Identifier.tryParse(root.getStringOr("id", ""));
        if (id == null) {
            throw new IllegalArgumentException("Blueprint sans identifiant valide : "
                    + root.getStringOr("id", "<absent>"));
        }
        CompoundTag meta = compound(root, "meta");
        Blueprint bp = new Blueprint(id, new BlueprintMeta(
                meta.getStringOr("author", ""),
                meta.getStringOr("description", ""),
                meta.getStringOr("version", "1.0.0"),
                permission(meta.getStringOr("permission", ""))));
        bp.setEnabled(root.getBooleanOr("enabled", true));
        bp.setRevision(root.getIntOr("revision", 0));

        for (Tag tag : list(root, "nodes")) {
            if (tag instanceof CompoundTag n) {
                decodeNode(bp, n, types);
            }
        }
        for (Tag tag : list(root, "links")) {
            if (tag instanceof CompoundTag l) {
                UUID from = uuid(l.getStringOr("from", ""));
                UUID to = uuid(l.getStringOr("to", ""));
                if (from != null && to != null) {
                    bp.putLink(new Link(from, l.getStringOr("fromPin", ""), to, l.getStringOr("toPin", "")));
                }
            }
        }
        ListTag preservedVariables = new ListTag();
        for (Tag tag : list(root, "variables")) {
            if (tag instanceof CompoundTag v && !decodeVariable(bp, v, types)) {
                preservedVariables.add(v.copy());
            }
        }
        bp.setPreservedVariables(preservedVariables);

        net.minecraft.nbt.ListTag preservedScreens = new net.minecraft.nbt.ListTag();
        ScreenNbt.decode(bp, list(root, "screens"), preservedScreens);
        bp.setPreservedScreens(preservedScreens);

        for (Tag tag : list(root, "comments")) {
            if (tag instanceof CompoundTag c) {
                UUID commentId = uuid(c.getStringOr("uuid", ""));
                if (commentId != null) {
                    bp.putComment(new CommentBox(commentId,
                            c.getStringOr("text", ""),
                            new Vec2d(c.getDoubleOr("x", 0), c.getDoubleOr("y", 0)),
                            new Vec2d(c.getDoubleOr("w", 0), c.getDoubleOr("h", 0)),
                            c.getIntOr("color", 0)));
                }
            }
        }
        return bp;
    }

    private static void decodeNode(Blueprint bp, CompoundTag n, Function<Identifier, PinType> types) {
        UUID nodeId = uuid(n.getStringOr("uuid", ""));
        Identifier typeId = Identifier.tryParse(n.getStringOr("type", ""));
        if (nodeId == null || typeId == null) {
            return; // entrée corrompue : signalée par le rapport de chargement (story 6.1)
        }
        Node node = new Node(nodeId, typeId,
                new Vec2d(n.getDoubleOr("x", 0), n.getDoubleOr("y", 0)));
        if (n.get("config") instanceof CompoundTag config) {
            node.setConfig(config);
        }
        ListTag preserved = new ListTag();
        for (Tag tag : list(n, "literals")) {
            if (!(tag instanceof CompoundTag entry)) {
                continue;
            }
            PinType type = PinTypeNbt.decode(entry.get("type"), types);
            LiteralValue literal = type == null ? null
                    : LiteralValue.decode(type, NbtOps.INSTANCE, entry.get("value"))
                            .result().orElse(null);
            if (literal == null) {
                preserved.add(entry.copy());   // type ou valeur irrésoluble : on garde tout
            } else {
                node.setLiteral(entry.getStringOr("pin", ""), literal);
            }
        }
        node.setPreservedLiterals(preserved);
        bp.putNode(node);
    }

    /** Faux si la variable n'a pas pu être résolue (à préserver en brut). */
    private static boolean decodeVariable(Blueprint bp, CompoundTag v, Function<Identifier, PinType> types) {
        PinType type = PinTypeNbt.decode(v.get("type"), types);
        if (type == null) {
            return false;
        }
        String name = v.getStringOr("name", "");
        LiteralValue defaultValue = null;
        Tag defaultTag = v.get("default");
        if (defaultTag != null) {
            defaultValue = LiteralValue.decode(type, NbtOps.INSTANCE, defaultTag).result().orElse(null);
            if (defaultValue == null) {
                return false;
            }
        }
        bp.putVariable(new Variable(name, type, defaultValue, scope(v.getStringOr("scope", "")),
                v.getBooleanOr("replicated", false)));
        return true;
    }

    // ------------------------------------------------------------------ util

    private static ListTag list(CompoundTag tag, String key) {
        return tag.get(key) instanceof ListTag l ? l : new ListTag();
    }

    private static CompoundTag compound(CompoundTag tag, String key) {
        return tag.get(key) instanceof CompoundTag c ? c : new CompoundTag();
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Permission permission(String name) {
        try {
            return Permission.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Permission.GAMEPLAY;
        }
    }

    private static VarScope scope(String name) {
        try {
            return VarScope.valueOf(name);
        } catch (IllegalArgumentException e) {
            return VarScope.GRAPH;
        }
    }
}
