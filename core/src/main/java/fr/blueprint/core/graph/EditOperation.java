package fr.blueprint.core.graph;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Opération d'édition réversible — l'unique porte de mutation d'un {@link Blueprint}
 * (AC1). La même mécanique sert trois usages : l'édition locale, l'annuler/rétablir
 * (story 5.6) et les patchs réseau (story 6.3). Toutes les implémentations sont ici,
 * scellées : un {@code switch} exhaustif côté réseau ne peut pas en oublier une.
 *
 * <p>{@code apply} refuse avec un {@link Diagnostic} — jamais d'exception pour une
 * erreur d'utilisateur — et, en cas de succès, retourne l'opération <b>inverse</b>,
 * calculée pendant l'application (l'état d'avant est capturé au bon moment).
 * L'encodage réseau ({@code encode()}) arrive avec les codecs de la story 1.4.
 */
public sealed interface EditOperation {

    /** Refus (le graphe n'a pas bougé) ou succès porteur de l'inverse. */
    record Result(@Nullable Diagnostic refusal, @Nullable EditOperation inverse) {
        public static Result refused(Diagnostic d) {
            return new Result(d, null);
        }

        static Result ok(EditOperation inverse) {
            return new Result(null, inverse);
        }

        public boolean applied() {
            return refusal == null;
        }
    }

    Result apply(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits);

    default Result apply(Blueprint bp, NodeTypeLookup lookup) {
        return apply(bp, lookup, GraphLimits.DEFAULT);
    }

    // ------------------------------------------------------------------ nœuds

    record AddNode(UUID uuid, Identifier typeId, Vec2d position) implements EditOperation {
        @Override
        public Result apply(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
            if (bp.node(uuid) != null) {
                return Result.refused(Diagnostic.error(DiagnosticCode.DUPLICATE_NODE,
                        Diagnostic.node(uuid), uuid.toString()));
            }
            if (bp.nodes().size() >= limits.maxNodes()) {
                return Result.refused(Diagnostic.error(DiagnosticCode.NODE_LIMIT_EXCEEDED,
                        Diagnostic.graph(), bp.nodes().size() + 1, limits.maxNodes()));
            }
            bp.putNode(new Node(uuid, typeId, position));
            bp.bumpRevision();
            return Result.ok(new RemoveNode(uuid));
        }
    }

    record RemoveNode(UUID uuid) implements EditOperation {
        @Override
        public Result apply(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
            Node node = bp.node(uuid);
            if (node == null) {
                return Result.refused(Diagnostic.error(DiagnosticCode.NODE_NOT_FOUND,
                        Diagnostic.node(uuid), uuid.toString()));
            }
            List<Link> severed = bp.linksTouching(uuid);
            severed.forEach(bp::dropLink);
            bp.dropNode(uuid);
            bp.bumpRevision();
            return Result.ok(new RestoreNode(node, Set.copyOf(severed)));
        }
    }

    /** Inverse de {@link RemoveNode} : repose le nœud et ses liens tels quels. */
    record RestoreNode(Node snapshot, Set<Link> links) implements EditOperation {
        @Override
        public Result apply(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
            if (bp.node(snapshot.uuid()) != null) {
                return Result.refused(Diagnostic.error(DiagnosticCode.DUPLICATE_NODE,
                        Diagnostic.node(snapshot.uuid()), snapshot.uuid().toString()));
            }
            bp.putNode(snapshot.copy());
            links.forEach(bp::putLink);
            bp.bumpRevision();
            return Result.ok(new RemoveNode(snapshot.uuid()));
        }
    }

    record MoveNode(UUID uuid, Vec2d to) implements EditOperation {
        @Override
        public Result apply(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
            Node node = bp.node(uuid);
            if (node == null) {
                return Result.refused(Diagnostic.error(DiagnosticCode.NODE_NOT_FOUND,
                        Diagnostic.node(uuid), uuid.toString()));
            }
            Vec2d before = node.position();
            node.moveTo(to);
            bp.bumpRevision();
            return Result.ok(new MoveNode(uuid, before));
        }
    }

    record SetLiteral(UUID uuid, String pin, @Nullable LiteralValue value) implements EditOperation {
        @Override
        public Result apply(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
            Node node = bp.node(uuid);
            if (node == null) {
                return Result.refused(Diagnostic.error(DiagnosticCode.NODE_NOT_FOUND,
                        Diagnostic.node(uuid), uuid.toString()));
            }
            NodeShape shape = lookup.shape(node.typeId());
            if (shape != null && value != null) {
                NodeShape.PinDef def = shape.input(pin);
                if (def == null) {
                    return Result.refused(Diagnostic.error(DiagnosticCode.PIN_NOT_FOUND,
                            Diagnostic.node(uuid), pin));
                }
                // Contrôle profond des éléments (TYPE-001) en plus de l'assignabilité.
                if (!def.type().isAssignableFrom(value.type()) || !Literals.matches(value)) {
                    return Result.refused(Diagnostic.error(DiagnosticCode.TYPE_MISMATCH,
                            Diagnostic.node(uuid), pin, def.type().toString(), value.type().toString()));
                }
            }
            LiteralValue before = node.literal(pin);
            node.setLiteral(pin, value);
            bp.bumpRevision();
            return Result.ok(new SetLiteral(uuid, pin, before));
        }
    }

    record SetConfig(UUID uuid, CompoundTag config) implements EditOperation {
        @Override
        public Result apply(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
            Node node = bp.node(uuid);
            if (node == null) {
                return Result.refused(Diagnostic.error(DiagnosticCode.NODE_NOT_FOUND,
                        Diagnostic.node(uuid), uuid.toString()));
            }
            CompoundTag before = node.config();
            node.setConfig(config);
            bp.bumpRevision();
            return Result.ok(new SetConfig(uuid, before));
        }
    }

    // ------------------------------------------------------------------ liens

    record AddLink(Link link) implements EditOperation {
        @Override
        public Result apply(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
            Diagnostic refusal = GraphValidator.canLink(bp, lookup, link);
            if (refusal != null) {
                return Result.refused(refusal);
            }
            bp.putLink(link);
            bp.bumpRevision();
            return Result.ok(new RemoveLink(link));
        }
    }

    record RemoveLink(Link link) implements EditOperation {
        @Override
        public Result apply(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
            if (!bp.links().contains(link)) {
                return Result.refused(Diagnostic.error(DiagnosticCode.LINK_NOT_FOUND,
                        Diagnostic.link(link)));
            }
            bp.dropLink(link);
            bp.bumpRevision();
            return Result.ok(new RestoreLink(link));
        }
    }

    /**
     * Inverse de {@link RemoveLink} : repose le lien sans revalidation — il était
     * présent, donc valide ; revalider pourrait refuser un rétablissement légitime
     * au milieu d'une séquence d'annulations.
     */
    record RestoreLink(Link link) implements EditOperation {
        @Override
        public Result apply(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
            if (bp.links().contains(link)) {
                return Result.refused(Diagnostic.error(DiagnosticCode.DUPLICATE_LINK,
                        Diagnostic.link(link)));
            }
            bp.putLink(link);
            bp.bumpRevision();
            return Result.ok(new RemoveLink(link));
        }
    }

    // -------------------------------------------------------------- variables

    record AddVariable(Variable variable) implements EditOperation {
        @Override
        public Result apply(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
            if (bp.variables().containsKey(variable.name())) {
                return Result.refused(Diagnostic.error(DiagnosticCode.DUPLICATE_VARIABLE,
                        Diagnostic.variable(variable.name()), variable.name()));
            }
            bp.putVariable(variable);
            bp.bumpRevision();
            return Result.ok(new RemoveVariable(variable.name()));
        }
    }

    record RemoveVariable(String name) implements EditOperation {
        @Override
        public Result apply(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
            Variable before = bp.variables().get(name);
            if (before == null) {
                return Result.refused(Diagnostic.error(DiagnosticCode.VARIABLE_NOT_FOUND,
                        Diagnostic.variable(name), name));
            }
            bp.dropVariable(name);
            bp.bumpRevision();
            return Result.ok(new AddVariable(before));
        }
    }

    record RenameVariable(String from, String to) implements EditOperation {
        @Override
        public Result apply(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
            Variable variable = bp.variables().get(from);
            if (variable == null) {
                return Result.refused(Diagnostic.error(DiagnosticCode.VARIABLE_NOT_FOUND,
                        Diagnostic.variable(from), from));
            }
            if (bp.variables().containsKey(to)) {
                return Result.refused(Diagnostic.error(DiagnosticCode.DUPLICATE_VARIABLE,
                        Diagnostic.variable(to), to));
            }
            bp.dropVariable(from);
            bp.putVariable(new Variable(to, variable.type(), variable.defaultValue(),
                    variable.scope(), variable.replicated()));
            bp.bumpRevision();
            return Result.ok(new RenameVariable(to, from));
        }
    }

    /**
     * Retypage. Le retypage incompatible des liens des nœuds Get/Set arrivera avec
     * eux (story 2.x, FR10) ; ici la valeur par défaut doit correspondre au nouveau type.
     */
    record RetypeVariable(String name, PinType newType,
                          @Nullable LiteralValue newDefault) implements EditOperation {
        @Override
        public Result apply(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
            Variable before = bp.variables().get(name);
            if (before == null) {
                return Result.refused(Diagnostic.error(DiagnosticCode.VARIABLE_NOT_FOUND,
                        Diagnostic.variable(name), name));
            }
            if (newDefault != null && (newDefault.type() != newType || !Literals.matches(newDefault))) {
                return Result.refused(Diagnostic.error(DiagnosticCode.TYPE_MISMATCH,
                        Diagnostic.variable(name), name, newType.toString(), newDefault.type().toString()));
            }
            bp.putVariable(new Variable(name, newType, newDefault, before.scope(), before.replicated()));
            bp.bumpRevision();
            return Result.ok(new RetypeVariable(name, before.type(), before.defaultValue()));
        }
    }

    record SetScope(String name, VarScope scope) implements EditOperation {
        @Override
        public Result apply(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
            Variable before = bp.variables().get(name);
            if (before == null) {
                return Result.refused(Diagnostic.error(DiagnosticCode.VARIABLE_NOT_FOUND,
                        Diagnostic.variable(name), name));
            }
            bp.putVariable(new Variable(name, before.type(), before.defaultValue(), scope, before.replicated()));
            bp.bumpRevision();
            return Result.ok(new SetScope(name, before.scope()));
        }
    }

    // ------------------------------------------------------------ métadonnées

    /** Remplace les métadonnées (auteur, description, version, plafond) — story 5.10. */
    record SetMeta(BlueprintMeta meta) implements EditOperation {
        @Override
        public Result apply(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
            BlueprintMeta before = bp.meta();
            bp.setMeta(meta);
            bp.bumpRevision();
            return Result.ok(new SetMeta(before));
        }
    }

    // ------------------------------------------------------------ commentaires

    record AddComment(CommentBox comment) implements EditOperation {
        @Override
        public Result apply(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
            if (bp.comment(comment.uuid()) != null) {
                return Result.refused(Diagnostic.error(DiagnosticCode.DUPLICATE_COMMENT,
                        Diagnostic.node(comment.uuid()), comment.uuid().toString()));
            }
            bp.putComment(comment);
            bp.bumpRevision();
            return Result.ok(new RemoveComment(comment.uuid()));
        }
    }

    record RemoveComment(UUID uuid) implements EditOperation {
        @Override
        public Result apply(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
            CommentBox before = bp.comment(uuid);
            if (before == null) {
                return Result.refused(Diagnostic.error(DiagnosticCode.COMMENT_NOT_FOUND,
                        Diagnostic.node(uuid), uuid.toString()));
            }
            bp.dropComment(uuid);
            bp.bumpRevision();
            return Result.ok(new AddComment(before));
        }
    }

    /** Édition en bloc d'un commentaire (texte, position, taille, couleur). */
    record EditComment(CommentBox updated) implements EditOperation {
        @Override
        public Result apply(Blueprint bp, NodeTypeLookup lookup, GraphLimits limits) {
            CommentBox before = bp.comment(updated.uuid());
            if (before == null) {
                return Result.refused(Diagnostic.error(DiagnosticCode.COMMENT_NOT_FOUND,
                        Diagnostic.node(updated.uuid()), updated.uuid().toString()));
            }
            bp.putComment(updated);
            bp.bumpRevision();
            return Result.ok(new EditComment(before));
        }
    }
}
