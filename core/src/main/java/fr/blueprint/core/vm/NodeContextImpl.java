package fr.blueprint.core.vm;

import fr.blueprint.api.event.TriggerContext;
import fr.blueprint.api.node.BlueprintHandle;
import fr.blueprint.api.node.NodeContext;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.pin.PinKind;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Contexte concret d'un appel de nœud (story 2.3). Une instance vit le temps d'UN
 * appel d'action : la VM la crée avec les entrées évaluées, exécute l'action, lit les
 * sorties, puis l'invalide — la conserver dans un champ et la rappeler lève (garde
 * anti-fuite, AC5). Toute mauvaise utilisation par un mod (pin inconnu, mauvais type)
 * est une erreur de développeur : exception immédiate nommant le nœud et le pin.
 */
public final class NodeContextImpl implements NodeContext {

    private final NodeType type;
    private final Map<String, Object> inputs;
    private final Map<String, Object> outputs = new LinkedHashMap<>();
    private final @Nullable MinecraftServer server;
    private final @Nullable ServerLevel level;
    private final BlueprintHandle blueprint;
    private final TriggerContext trigger;
    private final Logger logger;

    /** Valeurs réellement lues par le nœud — alimenté seulement en débogage (9.1a). */
    private final @Nullable Map<String, Object> reads;

    private @Nullable String chosenExec;
    private int suspendTicks = -1;
    private @Nullable Component failReason;
    private boolean valid = true;

    public NodeContextImpl(NodeType type, Map<String, Object> inputs,
                           @Nullable MinecraftServer server, @Nullable ServerLevel level,
                           BlueprintHandle blueprint, TriggerContext trigger, Logger logger) {
        this(type, inputs, server, level, blueprint, trigger, logger, false);
    }

    /** {@code recordReads} : conserver ce que le nœud lit, pour le débogueur (9.1a). */
    public NodeContextImpl(NodeType type, Map<String, Object> inputs,
                           @Nullable MinecraftServer server, @Nullable ServerLevel level,
                           BlueprintHandle blueprint, TriggerContext trigger, Logger logger,
                           boolean recordReads) {
        this.reads = recordReads ? new LinkedHashMap<>() : null;
        this.type = type;
        this.inputs = Map.copyOf(inputs);
        this.server = server;
        this.level = level;
        this.blueprint = blueprint;
        this.trigger = trigger;
        this.logger = logger;
    }

    /**
     * Exécute l'action du nœud puis invalide le contexte, quelle que soit l'issue.
     * La capture d'exception → {@code FAULTED} est le travail de la VM (story 3.3) :
     * ici l'exception traverse telle quelle.
     */
    public static NodeContextImpl invoke(NodeType type, NodeContextImpl ctx) throws Exception {
        try {
            type.action().run(ctx);
        } finally {
            ctx.valid = false;
        }
        return ctx;
    }

    // ------------------------------------------------------------ face NodeAction

    @Override
    @SuppressWarnings("unchecked")
    public <T> T in(String pin) {
        checkValid();
        NodeType.PinSpec spec = findPin(type.inputs(), pin, "entrée");
        Object value = inputs.get(pin);
        if (value == null && spec.defaultValue() != null) {
            value = spec.defaultValue().value();
        }
        if (value == null && spec.kind() == PinKind.DATA) {
            // Repli sur le défaut du type (int → 0, string → ""…), puis CTX-001 :
            // ni valeur ni défaut = faute nommée, jamais un null silencieux.
            var typeDefault = spec.type().defaultValue();
            if (typeDefault != null) {
                value = typeDefault.value();
            } else {
                throw new IllegalStateException(dev("le pin « " + pin
                        + " » n'a ni valeur ni défaut — câblage manquant ou producteur non exécuté"));
            }
        }
        if (reads != null) {
            // Ce que le nœud a VU (défaut de pin ou de type compris) : c'est cette
            // valeur-là qui intéresse le débogueur, pas seulement ce qui était câblé.
            reads.put(pin, value);
        }
        if (value != null && spec.kind() == PinKind.DATA
                && !spec.type().javaType().isInstance(value)) {
            // Adaptation numérique (AC3, 7.x) : la coercition int → double est validée au
            // câblage, la valeur runtime reste un Integer — on l'adapte ici plutôt que
            // d'exiger des instructions de conversion dans l'IR.
            Class<?> expected = spec.type().javaType();
            if (value instanceof Number number) {
                if (expected == Double.class) {
                    return (T) Double.valueOf(number.doubleValue());
                }
                if (expected == Long.class) {
                    return (T) Long.valueOf(number.longValue());
                }
                if (expected == Integer.class) {
                    return (T) Integer.valueOf(number.intValue());
                }
            }
            throw new IllegalStateException(dev("le pin « " + pin + " » porte un "
                    + value.getClass().getSimpleName() + " au lieu de "
                    + spec.type().javaType().getSimpleName()));
        }
        return (T) value;
    }

    @Override
    public void out(String pin, Object value) {
        checkValid();
        Objects.requireNonNull(value, "value");
        NodeType.PinSpec spec = findPin(type.outputs(), pin, "sortie");
        if (spec.kind() != PinKind.DATA) {
            throw new IllegalStateException(dev("« " + pin + " » est un pin exec — utiliser ctx.exec()"));
        }
        if (!spec.type().javaType().isInstance(value)) {
            throw new IllegalStateException(dev("valeur " + value.getClass().getSimpleName()
                    + " incompatible avec le pin « " + pin + " » ("
                    + spec.type().javaType().getSimpleName() + ")"));
        }
        outputs.put(pin, value);
    }

    /** Dernier appel gagnant : un nœud peut recalculer sa branche avant de rendre la main. */
    @Override
    public void exec(String pin) {
        checkValid();
        NodeType.PinSpec spec = findPin(type.outputs(), pin, "sortie");
        if (spec.kind() != PinKind.EXEC) {
            throw new IllegalStateException(dev("« " + pin + " » n'est pas un pin exec"));
        }
        chosenExec = pin;
    }

    @Override
    public void suspend(int ticks) {
        checkValid();
        if (ticks < 1) {
            throw new IllegalStateException(dev("suspend(" + ticks + ") : durée ≥ 1 tick requise"));
        }
        suspendTicks = ticks;
    }

    @Override
    public void fail(Component reason) {
        checkValid();
        this.failReason = Objects.requireNonNull(reason, "reason");
    }

    @Override
    public MinecraftServer server() {
        checkValid();
        if (server == null) {
            throw new IllegalStateException(dev("pas de serveur dans ce contexte (évaluation hors monde)"));
        }
        return server;
    }

    @Override
    public ServerLevel level() {
        checkValid();
        if (level == null) {
            throw new IllegalStateException(dev("pas de monde dans ce contexte (évaluation hors monde)"));
        }
        return level;
    }

    @Override
    public BlueprintHandle blueprint() {
        checkValid();
        return blueprint;
    }

    @Override
    public TriggerContext trigger() {
        checkValid();
        return trigger;
    }

    @Override
    public Logger logger() {
        checkValid();
        return logger;
    }

    // ------------------------------------------------------------------ face VM

    /** Sorties écrites par l'action (lisibles après invalidation — c'est la VM qui lit). */
    public Map<String, Object> outputs() {
        return outputs;
    }

    /** Entrées effectivement lues, ou vide hors débogage (9.1a). */
    public Map<String, Object> reads() {
        return reads == null ? Map.of() : reads;
    }

    /** Branche exec choisie, ou null (la VM prend alors la première sortie exec). */
    public @Nullable String chosenExec() {
        return chosenExec;
    }

    /** Durée de suspension demandée, ou -1. */
    public int suspendTicks() {
        return suspendTicks;
    }

    public @Nullable Component failReason() {
        return failReason;
    }

    // ------------------------------------------------------------------ interne

    private void checkValid() {
        if (!valid) {
            throw new IllegalStateException(dev(
                    "contexte utilisé hors de l'appel de l'action — ne jamais le conserver dans un champ"));
        }
    }

    private NodeType.PinSpec findPin(java.util.List<NodeType.PinSpec> pins, String name, String sideName) {
        for (NodeType.PinSpec pin : pins) {
            if (pin.name().equals(name)) {
                return pin;
            }
        }
        throw new IllegalStateException(dev("pas de pin d'" + sideName + " nommé « " + name + " »"));
    }

    private String dev(String message) {
        return "Nœud « " + type.id() + " » : " + message;
    }
}
