package fr.blueprint.core.datapack;

import fr.blueprint.api.node.NodeCategory;
import fr.blueprint.api.node.NodeContext;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.node.Permission;
import fr.blueprint.api.registry.NodeRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Transforme une {@link CompositeDefinition} en {@link NodeType} exécutable (story 8.2).
 * Le corps est <b>interprété</b> : chaque étape appelle l'action d'un nœud déjà
 * enregistré, avec un contexte qui expose ses liaisons et délègue le reste au contexte
 * de l'appelant.
 *
 * <p>Ce qu'un composite ne fait PAS, et le dit franchement plutôt que de faire à moitié :
 * pas de branche d'exécution (une étape à plusieurs sorties exec est refusée à la
 * construction), pas de suspension (l'étape qui tente {@code suspend} met le blueprint
 * en faute avec un message clair), pas de récursion (un composite ne peut pas s'appeler).
 */
public final class CompositeNode {

    private CompositeNode() {
    }

    /** Construit le type, ou rend les raisons du refus. */
    public record Built(@Nullable NodeType type, List<String> errors) {
    }

    /**
     * Résolution d'un nœud appelé. Volontairement une fonction et non un registre figé :
     * un composite peut appeler un composite du même rechargement, et l'exécution doit
     * voir la version COURANTE (QA DP-002) — sinon un {@code /reload} laisserait des
     * appels vers l'ancienne définition.
     */
    @FunctionalInterface
    public interface Resolver {
        @Nullable NodeType resolve(Identifier id);
    }

    public static Built build(CompositeDefinition definition, NodeRegistry registry) {
        return build(definition, id -> registry.get(id).orElse(null));
    }

    public static Built build(CompositeDefinition definition, Resolver resolver) {
        List<String> errors = new ArrayList<>();
        Permission effective = definition.permission();

        for (int i = 0; i < definition.steps().size(); i++) {
            CompositeDefinition.Step step = definition.steps().get(i);
            NodeType type = resolver.resolve(step.node());
            if (type == null) {
                errors.add("étape " + i + " : nœud « " + step.node() + " » inconnu");
                continue;
            }
            if (type.id().equals(definition.id())) {
                errors.add("étape " + i + " : un composite ne peut pas s'appeler lui-même");
                continue;
            }
            if (type.entryPoint()) {
                errors.add("étape " + i + " : « " + step.node()
                        + " » est un point d'entrée, il ne s'appelle pas");
                continue;
            }
            long execOuts = type.outputs().stream()
                    .filter(pin -> pin.kind() == fr.blueprint.api.pin.PinKind.EXEC).count();
            if (execOuts > 1) {
                errors.add("étape " + i + " : « " + step.node()
                        + " » a plusieurs sorties d'exécution — les branches ne sont pas "
                        + "représentables dans un composite (utiliser un blueprint)");
                continue;
            }
            // La permission du composite est celle de son étape la plus exigeante :
            // composer des nœuds ne doit pas servir à contourner un plafond.
            if (type.permission().ordinal() > effective.ordinal()) {
                effective = type.permission();
            }
            // Chaque entrée liée doit exister sur le nœud appelé.
            for (String argument : step.args().keySet()) {
                if (type.inputs().stream().noneMatch(pin -> pin.name().equals(argument))) {
                    errors.add("étape " + i + " : « " + step.node() + " » n'a pas d'entrée « "
                            + argument + " »");
                }
            }
        }

        if (!effective.allowedUnder(CompositeDefinition.MAX_PERMISSION)) {
            errors.add("les étapes exigent la permission " + effective + ", au-delà du plafond "
                    + CompositeDefinition.MAX_PERMISSION + " des datapacks");
        }
        for (CompositeDefinition.Pin pin : definition.inputs()) {
            if (pin.defaultValue() == null) {
                continue;
            }
            try {
                fr.blueprint.api.pin.LiteralValue.of(pin.type(), pin.defaultValue());
            } catch (RuntimeException e) {
                errors.add("pin « " + pin.name() + " » : valeur par défaut incompatible avec "
                        + pin.type().id());
            }
        }
        if (!errors.isEmpty()) {
            return new Built(null, errors);
        }

        NodeType.Builder builder = NodeType.builder(definition.id())
                .category(new NodeCategory(definition.category()))
                .exec()
                .permission(effective)
                .fuelCost(definition.fuelCost());
        if (definition.titleKey() != null) {
            builder.titleKey(definition.titleKey());
        }
        if (definition.descKey() != null) {
            builder.descKey(definition.descKey());
        }
        for (CompositeDefinition.Pin pin : definition.inputs()) {
            if (pin.defaultValue() == null) {
                builder.in(pin.name(), pin.type());
            } else {
                builder.in(pin.name(), pin.type(), pin.defaultValue());
            }
        }
        for (CompositeDefinition.Pin pin : definition.outputs()) {
            builder.out(pin.name(), pin.type());
        }

        try {
            return new Built(builder.action(ctx -> run(definition, resolver, ctx)).build(), List.of());
        } catch (RuntimeException e) {
            return new Built(null, List.of(e.getMessage()));
        }
    }

    private static void run(CompositeDefinition definition, Resolver resolver, NodeContext outer)
            throws Exception {
        List<Map<String, Object>> stepOutputs = new ArrayList<>(definition.steps().size());
        for (int i = 0; i < definition.steps().size(); i++) {
            CompositeDefinition.Step step = definition.steps().get(i);
            // Résolu MAINTENANT : après un /reload, l'appel doit viser la version
            // courante du nœud, pas celle capturée à la construction (QA DP-002).
            NodeType called = resolver.resolve(step.node());
            if (called == null) {
                throw new IllegalStateException("Composite « " + definition.id()
                        + " » : le nœud « " + step.node() + " » de l'étape " + i
                        + " n'existe plus");
            }
            Map<String, Object> inputs = new HashMap<>();
            for (Map.Entry<String, CompositeDefinition.Binding> entry : step.args().entrySet()) {
                inputs.put(entry.getKey(), resolve(entry.getValue(), outer, stepOutputs));
            }
            // Les entrées non liées prennent le défaut déclaré par le nœud appelé.
            for (NodeType.PinSpec pin : called.inputs()) {
                if (!inputs.containsKey(pin.name()) && pin.defaultValue() != null) {
                    inputs.put(pin.name(), pin.defaultValue().value());
                }
            }
            StepContext context = new StepContext(outer, inputs, definition.id(), step.node());
            called.action().run(context);
            stepOutputs.add(context.outputs);
        }
        for (Map.Entry<String, CompositeDefinition.Binding> entry : definition.returns().entrySet()) {
            outer.out(entry.getKey(), resolve(entry.getValue(), outer, stepOutputs));
        }
    }

    private static Object resolve(CompositeDefinition.Binding binding, NodeContext outer,
                                  List<Map<String, Object>> stepOutputs) {
        if (binding instanceof CompositeDefinition.Binding.Constant constant) {
            return constant.value();
        }
        if (binding instanceof CompositeDefinition.Binding.FromPin fromPin) {
            return outer.in(fromPin.pin());
        }
        CompositeDefinition.Binding.FromStep fromStep = (CompositeDefinition.Binding.FromStep) binding;
        Map<String, Object> outputs = stepOutputs.get(fromStep.step());
        if (!outputs.containsKey(fromStep.pin())) {
            throw new IllegalStateException("l'étape " + fromStep.step()
                    + " n'a pas écrit la sortie « " + fromStep.pin() + " »");
        }
        return outputs.get(fromStep.pin());
    }

    /**
     * Contexte d'une étape : ses entrées viennent des liaisons, ses sorties sont
     * capturées, tout le reste (serveur, monde, déclencheur, journal, faute) est celui
     * du composite — une étape parle donc au joueur avec la voix du nœud appelant.
     */
    private static final class StepContext implements NodeContext {

        private final NodeContext outer;
        private final Map<String, Object> inputs;
        private final Map<String, Object> outputs = new HashMap<>();
        private final Identifier composite;
        private final Identifier step;

        StepContext(NodeContext outer, Map<String, Object> inputs, Identifier composite,
                    Identifier step) {
            this.outer = outer;
            this.inputs = inputs;
            this.composite = composite;
            this.step = step;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T in(String pin) {
            if (!inputs.containsKey(pin)) {
                throw new IllegalStateException("Composite « " + composite + " » : l'étape « "
                        + step + " » lit l'entrée « " + pin + " », qu'aucune liaison n'alimente");
            }
            return (T) inputs.get(pin);
        }

        @Override
        public void out(String pin, Object value) {
            outputs.put(pin, value);
        }

        @Override
        public void exec(String pin) {
            // Une seule sortie exec par étape (vérifié à la construction) : le choix
            // n'a pas de sens ici, l'ignorer vaut mieux que faire semblant de brancher.
        }

        @Override
        public void suspend(int ticks) {
            throw new IllegalStateException("Composite « " + composite + " » : l'étape « " + step
                    + " » veut suspendre l'exécution, ce qu'un nœud de datapack ne sait pas faire");
        }

        @Override
        public void fail(Component reason) {
            outer.fail(reason);
        }

        @Override
        public MinecraftServer server() {
            return outer.server();
        }

        @Override
        public ServerLevel level() {
            return outer.level();
        }

        @Override
        public fr.blueprint.api.node.BlueprintHandle blueprint() {
            return outer.blueprint();
        }

        @Override
        public fr.blueprint.api.event.TriggerContext trigger() {
            return outer.trigger();
        }

        @Override
        public org.slf4j.Logger logger() {
            return outer.logger();
        }
    }
}
