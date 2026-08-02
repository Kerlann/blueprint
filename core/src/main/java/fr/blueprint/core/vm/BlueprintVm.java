package fr.blueprint.core.vm;

import fr.blueprint.api.node.NodeType;
import fr.blueprint.core.compile.ir.Instruction;
import fr.blueprint.core.compile.ir.Ir;
import fr.blueprint.core.graph.VarScope;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * La machine virtuelle (story 3.3). Boucle d'interprétation bornée par le fuel :
 * chaque instruction coûte 1, un {@code Call} coûte le {@code fuelCost} du nœud.
 * Budget épuisé → {@code OUT_OF_FUEL}, état préservé, reprise au prochain run — une
 * boucle infinie ne fige jamais le serveur (NFR4). Aucune exception ne s'échappe :
 * une action qui lève, une faute déclarée ou un pin sans valeur → {@code FAULTED}
 * nommant le nœud (coding-standards §1.5).
 */
public final class BlueprintVm {

    private BlueprintVm() {
    }

    public static ExecResult run(Ir ir, ExecutionState state, ExecutionEnvironment env, int fuelBudget) {
        int spent = 0;
        while (true) {
            int pc = state.pc();
            if (pc < 0 || pc >= ir.instructions().size()) {
                return ExecResult.DONE;
            }
            if (spent >= fuelBudget) {
                return ExecResult.OUT_OF_FUEL;
            }
            Instruction ins = ir.instructions().get(pc);
            switch (ins) {
                case Instruction.Const c -> {
                    state.slots()[c.slot()] = c.value().value();
                    state.setPc(pc + 1);
                    spent++;
                }
                case Instruction.Call call -> {
                    spent += call.fuelCost();
                    ExecResult interrupt = call(ir, state, env, call, pc);
                    if (interrupt != null) {
                        return interrupt;
                    }
                }
                case Instruction.Jmp j -> {
                    state.setPc(j.target());
                    spent++;
                }
                case Instruction.JmpIf j -> {
                    Object condition = state.slots()[j.conditionSlot()];
                    state.setPc(condition instanceof Boolean b && b ? pc + 1 : j.elseTarget());
                    spent++;
                }
                case Instruction.LoadVar l -> {
                    state.slots()[l.slot()] = l.scope() == VarScope.LOCAL
                            ? state.locals().get(l.name())
                            : env.vars().get(l.scope(), l.name());
                    state.setPc(pc + 1);
                    spent++;
                }
                case Instruction.StoreVar s -> {
                    Object value = state.slots()[s.slot()];
                    if (s.scope() == VarScope.LOCAL) {
                        state.locals().put(s.name(), value);
                    } else {
                        env.vars().set(s.scope(), s.name(), value);
                    }
                    state.setPc(pc + 1);
                    spent++;
                }
                case Instruction.Yield y -> {
                    state.setPc(pc + 1);
                    return new ExecResult.Suspended(y.ticks());
                }
                case Instruction.Return r -> {
                    return ExecResult.DONE;
                }
            }
        }
    }

    /** Exécute un {@code Call} ; retourne un résultat d'interruption, ou null pour continuer. */
    private static ExecResult call(Ir ir, ExecutionState state, ExecutionEnvironment env,
                                   Instruction.Call call, int pc) {
        NodeType type = env.nodeResolver().apply(call.type());
        if (type == null) {
            return new ExecResult.Faulted(call.source(),
                    "type de nœud irrésoluble : " + call.type() + " (mod retiré ?)");
        }
        Map<String, Object> inputs = new LinkedHashMap<>();
        for (Instruction.PinBinding binding : call.inputs()) {
            Object value = state.slots()[binding.slot()];
            if (value != null) {
                inputs.put(binding.pin(), value);
            }
        }
        NodeContextImpl ctx = new NodeContextImpl(type, inputs, env.server(), env.level(),
                env.blueprint(), env.trigger(), env.logger());
        try {
            NodeContextImpl.invoke(type, ctx);
        } catch (Exception e) {
            env.logger().error("Nœud « {} » en faute", type.id(), e);
            return new ExecResult.Faulted(call.source(),
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
        if (ctx.failReason() != null) {
            return new ExecResult.Faulted(call.source(), ctx.failReason().getString());
        }
        for (Instruction.PinBinding binding : call.outputs()) {
            state.slots()[binding.slot()] = ctx.outputs().get(binding.pin());
        }
        // Le Call est la table de sauts : cible choisie par l'action, sinon la première
        // déclarée ; cible négative = fin ; nœud pur = enchaînement linéaire.
        int next;
        if (call.execTargets().isEmpty()) {
            next = call.pure() ? pc + 1 : -1;
        } else {
            String chosen = ctx.chosenExec();
            if (chosen == null) {
                chosen = call.execTargets().keySet().iterator().next();
            }
            Integer target = call.execTargets().get(chosen);
            next = target == null ? -1 : target;
        }
        state.setPc(next);
        if (ctx.suspendTicks() > 0) {
            return new ExecResult.Suspended(ctx.suspendTicks());
        }
        return null;
    }
}
