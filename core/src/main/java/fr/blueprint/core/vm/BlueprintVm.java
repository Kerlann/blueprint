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

    /** Résultat + fuel réellement dépensé — la matière première des statistiques (3.5). */
    public record RunOutcome(ExecResult result, int fuelSpent) {
    }

    public static ExecResult run(Ir ir, ExecutionState state, ExecutionEnvironment env, int fuelBudget) {
        return runMeasured(ir, state, env, fuelBudget).result();
    }

    public static RunOutcome runMeasured(Ir ir, ExecutionState state, ExecutionEnvironment env, int fuelBudget) {
        int spent = 0;
        // Débogage (9.1a) : une lecture de volatile quand personne ne débogue, une
        // recherche par exécution sinon. Rien dans la boucle chaude.
        fr.blueprint.core.debug.DebugSession debug =
                fr.blueprint.core.debug.DebugSessions.active()
                        ? fr.blueprint.core.debug.DebugSessions.of(env.blueprint().id())
                        : null;
        // Profileur (9.2) : même discipline, et indépendant du débogueur.
        fr.blueprint.core.debug.Profiler profiler =
                fr.blueprint.core.debug.Profiler.active()
                        ? fr.blueprint.core.debug.Profiler.of(env.blueprint().id())
                        : null;
        // Types de nœuds et index de pins, résolus UNE fois pour toute l'exécution plutôt
        // qu'à chaque appel (épic 16d/e). L'ancienne ligne allouait un Optional par nœud
        // traversé et refaisait la même recherche dans la même table.
        ResolvedIr resolved = state.resolvedFor(ir, env);
        while (true) {
            int pc = state.pc();
            if (pc < 0 || pc >= ir.instructions().size()) {
                // Fin de sous-chaîne (7.1b) : dépiler l'adresse de retour s'il y en a une.
                Integer resume = state.frames().pollFirst();
                if (resume == null) {
                    return new RunOutcome(ExecResult.DONE, spent);
                }
                state.setPc(resume);
                continue;
            }
            if (spent >= fuelBudget) {
                return new RunOutcome(ExecResult.OUT_OF_FUEL, spent);
            }
            Instruction ins = ir.instructions().get(pc);
            switch (ins) {
                case Instruction.Const c -> {
                    state.slots()[c.slot()] = c.value().value();
                    state.setPc(pc + 1);
                    spent++;
                }
                case Instruction.Call call -> {
                    // Point d'arrêt : on rend la main SANS avancer le pc — la reprise
                    // repassera exactement ici, le nœud n'a pas encore tourné.
                    if (debug != null && debug.pauseBefore(call.source())) {
                        return new RunOutcome(new ExecResult.Suspended(1), spent);
                    }
                    spent += call.fuelCost();
                    ExecResult interrupt = call(state, env, resolved, call, pc, debug, profiler);
                    if (interrupt != null) {
                        return new RunOutcome(interrupt, spent);
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
                case Instruction.CallSub c -> {
                    state.frames().addFirst(pc + 1);
                    state.setPc(c.target());
                    spent++;
                }
                case Instruction.Yield y -> {
                    state.setPc(pc + 1);
                    return new RunOutcome(new ExecResult.Suspended(y.ticks()), spent + 1);
                }
                case Instruction.Return r -> {
                    return new RunOutcome(ExecResult.DONE, spent);
                }
            }
        }
    }

    /**
     * Qui a déclenché l'exécution : le joueur de la charge utile s'il y en a un, sinon
     * l'événement lui-même (un tick serveur n'a pas d'acteur). Jamais d'exception : un
     * audit ne doit pas casser une exécution.
     */
    private static String actorOf(ExecutionEnvironment env) {
        try {
            Object player = env.trigger().output("player");
            if (player != null) {
                return String.valueOf(player);
            }
        } catch (RuntimeException e) {
            // L'événement ne déclare pas de « player » : ce n'est pas une anomalie.
        }
        return env.trigger().eventId().toString();
    }

    /** Exécute un {@code Call} ; retourne un résultat d'interruption, ou null pour continuer. */
    private static ExecResult call(ExecutionState state, ExecutionEnvironment env,
                                   ResolvedIr resolved,
                                   Instruction.Call call, int pc,
                                   @org.jetbrains.annotations.Nullable
                                   fr.blueprint.core.debug.DebugSession debug,
                                   @org.jetbrains.annotations.Nullable
                                   fr.blueprint.core.debug.Profiler profiler) {
        // Résolu à l'entrée du run, pas ici : un type nul reste un type nul, et produit
        // la MÊME faute nommée qu'avant. Résoudre à l'avance n'avance pas l'échec.
        NodeType type = resolved.typeAt(pc);
        if (type == null) {
            return new ExecResult.Faulted(call.source(),
                    "type de nœud irrésoluble : " + call.type() + " (mod retiré ?)");
        }
        // Tampons prêtés par l'exécution, et non deux tables neuves par nœud traversé.
        // Le contexte, lui, reste neuf : c'est lui qui porte la garde anti-fuite d'AC5.
        Map<String, Object> inputs = state.borrowInputs();
        for (Instruction.PinBinding binding : call.inputs()) {
            Object value = state.slots()[binding.slot()];
            if (value != null) {
                inputs.put(binding.pin(), value);
            }
        }
        NodeContextImpl ctx = new NodeContextImpl(type, inputs, env.server(), env.level(),
                env.blueprint(), env.trigger(), env.logger(), debug != null,
                resolved.pinsAt(pc), state.borrowOutputs());
        // NFR15 : un nœud ADMIN est journalisé AVANT de tourner — s'il fait tomber le
        // serveur, la trace doit déjà exister. Coût hors ADMIN : une comparaison d'enum.
        if (type.permission() == fr.blueprint.api.node.Permission.ADMIN) {
            fr.blueprint.core.debug.AdminAudit.record(type.permission(), env.blueprint().id(),
                    call.source(), type.id(), actorOf(env), System.currentTimeMillis());
        }
        long begin = profiler != null ? System.nanoTime() : 0L;
        try {
            NodeContextImpl.invoke(type, ctx);
        } catch (Exception e) {
            env.logger().error("Nœud « {} » en faute", type.id(), e);
            return new ExecResult.Faulted(call.source(),
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
        if (profiler != null) {
            profiler.record(call.source(), call.type(), System.nanoTime() - begin, call.fuelCost());
        }
        if (ctx.failReason() != null) {
            return new ExecResult.Faulted(call.source(), ctx.failReason().getString());
        }
        for (Instruction.PinBinding binding : call.outputs()) {
            state.slots()[binding.slot()] = ctx.outputs().get(binding.pin());
        }
        if (debug != null) {
            // Ce que le nœud a lu (défauts compris) plutôt que ce qui était câblé.
            Map<String, Object> seen = new LinkedHashMap<>(inputs);
            seen.putAll(ctx.reads());
            debug.record(call.source(), seen, ctx.outputs());
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
