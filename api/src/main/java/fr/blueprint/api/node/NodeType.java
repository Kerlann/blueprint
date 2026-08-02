package fr.blueprint.api.node;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.ParameterizedPinType;
import fr.blueprint.api.pin.PinKind;
import fr.blueprint.api.pin.PinType;
import fr.blueprint.api.pin.PinTypes;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Type de nœud : l'unité déclarée par Blueprint et par les mods tiers. Immuable et
 * autodescriptif — le graphe ne stocke que l'identifiant, les pins viennent d'ici
 * (décision AD3 : un mod peut faire évoluer ses nœuds sans casser les graphes).
 *
 * <p>Déclaration type (contrat complet : {@code docs/extension-api.md}) :
 * <pre>{@code
 * NodeType HEAL = NodeType.builder(Identifier.fromNamespaceAndPath("mymod", "heal_player"))
 *     .category(NodeCategories.ENTITY)
 *     .exec()
 *     .in("player", PinTypes.PLAYER)
 *     .in("amount", PinTypes.DOUBLE, 1.0)
 *     .out("healed", PinTypes.BOOL)
 *     .permission(Permission.GAMEPLAY)
 *     .action(ctx -> {
 *         ServerPlayer p = ctx.in("player");
 *         p.heal(ctx.<Double>in("amount").floatValue());
 *         ctx.out("healed", true);
 *     })
 *     .build();
 * }</pre>
 *
 * <p>Toute déclaration incohérente lève au {@code build()} — donc au démarrage du jeu,
 * avec un message nommant le nœud — jamais en cours de partie.
 */
public final class NodeType {

    /** Un pin déclaré : nom, nature, type, et défaut éventuel (entrées seulement). */
    public record PinSpec(String name, PinKind kind, PinType type, @Nullable LiteralValue defaultValue) {
    }

    private final Identifier id;
    private final NodeCategory category;
    private final String titleKey;
    private final String descKey;
    private final List<PinSpec> inputs;
    private final List<PinSpec> outputs;
    private final boolean pure;
    private final boolean entryPoint;
    private final ExecSide side;
    private final Permission permission;
    private final int fuelCost;
    private final boolean deterministic;
    private final NodeAction action;

    private NodeType(Builder b) {
        this.id = b.id;
        this.category = b.category;
        this.titleKey = b.titleKey != null ? b.titleKey
                : "blueprint.node." + b.id.getNamespace() + "." + b.id.getPath() + ".name";
        this.descKey = b.descKey != null ? b.descKey
                : "blueprint.node." + b.id.getNamespace() + "." + b.id.getPath() + ".desc";
        this.inputs = List.copyOf(b.inputs);
        this.outputs = List.copyOf(b.outputs);
        this.pure = b.pure;
        this.entryPoint = b.entryPoint;
        this.side = b.side;
        this.permission = b.permission;
        this.fuelCost = b.fuelCost;
        this.deterministic = b.deterministic;
        this.action = b.action;
    }

    public Identifier id() {
        return id;
    }

    public NodeCategory category() {
        return category;
    }

    public String titleKey() {
        return titleKey;
    }

    public String descKey() {
        return descKey;
    }

    public List<PinSpec> inputs() {
        return inputs;
    }

    public List<PinSpec> outputs() {
        return outputs;
    }

    /** Vrai pour un nœud sans pin exec : évalué à la demande et mémoïsé par étape (FR13). */
    public boolean pure() {
        return pure;
    }

    /** Vrai pour un nœud d'événement — point d'entrée d'exécution du graphe. */
    public boolean entryPoint() {
        return entryPoint;
    }

    public ExecSide side() {
        return side;
    }

    public Permission permission() {
        return permission;
    }

    /** Coût en fuel d'un appel (1 par défaut ; plus pour un nœud coûteux). */
    public int fuelCost() {
        return fuelCost;
    }

    /** Faux si le nœud dépend de l'aléatoire, de l'heure ou d'un état externe. */
    public boolean deterministic() {
        return deterministic;
    }

    public NodeAction action() {
        return action;
    }

    @Override
    public String toString() {
        return id.toString();
    }

    public static Builder builder(Identifier id) {
        return new Builder(id);
    }

    /** Construit un {@link NodeType}. Toute incohérence lève au {@code build()}. */
    public static final class Builder {

        private final Identifier id;
        private NodeCategory category = NodeCategories.MISC;
        private @Nullable String titleKey;
        private @Nullable String descKey;
        private final List<PinSpec> inputs = new ArrayList<>();
        private final List<PinSpec> outputs = new ArrayList<>();
        private final Set<String> genericSlots = new LinkedHashSet<>();
        private boolean pure;
        private boolean entryPoint;
        private boolean hasExec;
        private ExecSide side = ExecSide.SERVER;
        private Permission permission = Permission.SAFE;
        private int fuelCost = 1;
        private boolean deterministic = true;
        private @Nullable NodeAction action;

        private Builder(Identifier id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public Builder category(NodeCategory category) {
            this.category = category;
            return this;
        }

        public Builder titleKey(String key) {
            this.titleKey = key;
            return this;
        }

        public Builder descKey(String key) {
            this.descKey = key;
            return this;
        }

        /** Ajoute les pins {@code exec_in} et {@code exec_out} standard. */
        public Builder exec() {
            return execIn("exec_in").execOut("exec_out");
        }

        public Builder execIn(String name) {
            hasExec = true;
            inputs.add(new PinSpec(name, PinKind.EXEC, PinTypes.EXEC, null));
            return this;
        }

        /** Sortie d'exécution supplémentaire nommée (nœuds de flux à branches). */
        public Builder execOut(String name) {
            hasExec = true;
            outputs.add(new PinSpec(name, PinKind.EXEC, PinTypes.EXEC, null));
            return this;
        }

        /** Aucun pin exec : nœud pur, évalué à la demande. */
        public Builder pure() {
            this.pure = true;
            return this;
        }

        /**
         * Marque un point d'entrée d'exécution. Réservé à la synthèse des nœuds
         * d'événement par Blueprint — les mods déclarent des {@code EventType},
         * jamais des points d'entrée directs.
         */
        public Builder entryPoint() {
            this.entryPoint = true;
            return this;
        }

        /** Entrée sans valeur par défaut : devra être câblée (sauf défaut du type). */
        public Builder in(String name, PinType type) {
            inputs.add(new PinSpec(name, PinKind.DATA, type, null));
            return this;
        }

        /** Entrée avec littéral par défaut ; un défaut du mauvais type lève au build. */
        public Builder in(String name, PinType type, Object defaultValue) {
            LiteralValue literal;
            try {
                literal = LiteralValue.of(type, defaultValue);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(
                        "Nœud « " + id + " », pin « " + name + " » : " + e.getMessage(), e);
            }
            inputs.add(new PinSpec(name, PinKind.DATA, type, literal));
            return this;
        }

        public Builder out(String name, PinType type) {
            outputs.add(new PinSpec(name, PinKind.DATA, type, null));
            return this;
        }

        /** Déclare un groupe de jokers ; tout pin utilisant {@code PinTypes.generic(name)} doit s'y référer. */
        public Builder generic(String name) {
            genericSlots.add(name);
            return this;
        }

        public Builder side(ExecSide side) {
            this.side = side;
            return this;
        }

        public Builder permission(Permission permission) {
            this.permission = permission;
            return this;
        }

        public Builder fuelCost(int fuelCost) {
            this.fuelCost = fuelCost;
            return this;
        }

        public Builder deterministic(boolean deterministic) {
            this.deterministic = deterministic;
            return this;
        }

        public Builder action(NodeAction action) {
            this.action = action;
            return this;
        }

        public NodeType build() {
            if (action == null) {
                throw new IllegalStateException("Nœud « " + id + " » : action manquante");
            }
            if (pure && hasExec) {
                throw new IllegalStateException(
                        "Nœud « " + id + " » : pure() est incompatible avec des pins exec");
            }
            if (!pure && !hasExec) {
                throw new IllegalStateException(
                        "Nœud « " + id + " » : déclarer soit pure(), soit exec()/execIn()/execOut()");
            }
            checkUniqueNames(inputs, "entrée");
            checkUniqueNames(outputs, "sortie");
            checkGenericsDeclared();
            if (fuelCost < 1) {
                throw new IllegalStateException("Nœud « " + id + " » : fuelCost doit être ≥ 1");
            }
            return new NodeType(this);
        }

        private void checkUniqueNames(List<PinSpec> pins, String sideName) {
            Set<String> seen = new HashSet<>();
            for (PinSpec pin : pins) {
                if (!seen.add(pin.name())) {
                    throw new IllegalStateException("Nœud « " + id + " » : deux pins d'"
                            + sideName + " nommés « " + pin.name() + " »");
                }
            }
        }

        // Un joker utilisé sans être déclaré par generic() = erreur de déclaration :
        // sans groupe, la résolution ne saurait pas relier les pins entre eux.
        private void checkGenericsDeclared() {
            for (List<PinSpec> pins : List.of(inputs, outputs)) {
                for (PinSpec pin : pins) {
                    for (String slot : slotsOf(pin.type())) {
                        if (!"any".equals(slot) && !genericSlots.contains(slot)) {
                            throw new IllegalStateException("Nœud « " + id + " » : le pin « "
                                    + pin.name() + " » utilise le joker « " + slot
                                    + " » sans le déclarer via generic(\"" + slot + "\")");
                        }
                    }
                }
            }
        }

        private static Set<String> slotsOf(PinType type) {
            Set<String> slots = new HashSet<>();
            collectSlots(type, slots);
            return slots;
        }

        private static void collectSlots(PinType type, Set<String> out) {
            if (type.isGeneric() && type.genericName() != null) {
                out.add(type.genericName());
            }
            if (type instanceof ParameterizedPinType p) {
                for (PinType arg : p.args()) {
                    collectSlots(arg, out);
                }
            }
        }
    }
}
