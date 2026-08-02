package fr.blueprint.core.vm;

import fr.blueprint.core.compile.ir.Ir;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * L'ordonnanceur (story 3.5). Vit sur le thread serveur uniquement (fin de tick) —
 * les lancements par événement arrivent déjà par la porte du dispatcher (leçon EVT-001).
 *
 * <ul>
 * <li><b>Lissage</b> : les exécutions prêtes se partagent le budget du tick en
 *     round-robin (une tranche chacune) ; ce qui n'a pas fini reprend au tick suivant.</li>
 * <li><b>Police</b> : un blueprint en dépassement N ticks d'affilée est purgé et son
 *     écouteur notifié ; une faute retire l'exécution et met le blueprint en cause.</li>
 * <li><b>Comptabilité</b> : exécutions, fuel, temps cumulé et pic par blueprint —
 *     la matière de {@code /blueprint profile} (9.2).</li>
 * </ul>
 */
public final class BlueprintScheduler {

    /** Notifications de police — la notification admin (9.3) et l'éditeur (9.1) s'y brancheront. */
    public interface Listener {
        void disabled(Identifier blueprintId, int streakTicks);

        void faulted(Identifier blueprintId, @Nullable UUID node, String message);
    }

    /** Instantané des statistiques d'un blueprint. */
    public record Stats(int runs, int completed, int faults, long fuel, long totalNanos, long peakNanos) {
        static final Stats EMPTY = new Stats(0, 0, 0, 0, 0, 0);
    }

    private static final class Execution {
        final Identifier blueprintId;
        final Ir ir;
        final ExecutionState state;
        final ExecutionEnvironment env;
        int delayTicks;

        Execution(Identifier blueprintId, Ir ir, ExecutionState state,
                  ExecutionEnvironment env, int delayTicks) {
            this.blueprintId = blueprintId;
            this.ir = ir;
            this.state = state;
            this.env = env;
            this.delayTicks = delayTicks;
        }
    }

    private final int maxOverBudgetTicks;
    private final Listener listener;
    private final List<Execution> ready = new ArrayList<>();
    private final List<Execution> delayed = new ArrayList<>();
    private final Map<Identifier, Integer> overBudgetStreak = new HashMap<>();
    private final Map<Identifier, MutableStats> stats = new HashMap<>();

    public BlueprintScheduler(int maxOverBudgetTicks, Listener listener) {
        this.maxOverBudgetTicks = maxOverBudgetTicks;
        this.listener = listener;
    }

    /** Lance une nouvelle exécution (depuis un événement). */
    public void launch(Identifier blueprintId, Ir ir, ExecutionEnvironment env) {
        ready.add(new Execution(blueprintId, ir, ExecutionState.fresh(ir), env, 0));
    }

    /** Reprend une exécution suspendue (chargement du monde, story 6.1). */
    public void resume(SuspendedExecution suspended, Ir ir, ExecutionEnvironment env) {
        Execution execution = new Execution(suspended.blueprintId(), ir, suspended.state(),
                env, suspended.remainingTicks());
        if (execution.delayTicks > 0) {
            delayed.add(execution);
        } else {
            ready.add(execution);
        }
    }

    public int activeCount() {
        return ready.size() + delayed.size();
    }

    public Stats stats(Identifier blueprintId) {
        MutableStats s = stats.get(blueprintId);
        return s == null ? Stats.EMPTY : s.snapshot();
    }

    /**
     * Capture tout l'en-cours SANS vider les files — pour les sauvegardes périodiques
     * du monde (6.1) : sauvegarder ne doit pas arrêter les exécutions.
     */
    public List<SuspendedExecution> captureForSave() {
        List<SuspendedExecution> out = new ArrayList<>();
        for (Execution e : delayed) {
            out.add(capture(e, e.delayTicks));
        }
        for (Execution e : ready) {
            out.add(capture(e, 1));
        }
        return out;
    }

    /** Capture puis vide les files (arrêt définitif). */
    public List<SuspendedExecution> drainForSave() {
        List<SuspendedExecution> out = captureForSave();
        delayed.clear();
        ready.clear();
        return out;
    }

    private static SuspendedExecution capture(Execution e, int ticks) {
        // Correction QA SCHED-001 : la charge utile du déclencheur fait partie de la
        // capture (AC1 3.4) — sans elle, un nœud lisant trigger().output(...) après
        // reprise trouverait le vide.
        Map<String, Object> triggerValues =
                e.env.trigger() instanceof fr.blueprint.core.event.TriggerContextImpl impl
                        ? impl.values() : Map.of();
        return new SuspendedExecution(e.blueprintId, e.ir.revision(), e.ir.entryNode(),
                ticks, e.state, e.env.trigger().eventId(), triggerValues);
    }

    /** Un tick serveur : échéances, round-robin budgété, police des dépassements. */
    public void tick(int globalFuelBudget) {
        // 1. Échéances des suspendues.
        Iterator<Execution> it = delayed.iterator();
        while (it.hasNext()) {
            Execution e = it.next();
            if (--e.delayTicks <= 0) {
                it.remove();
                ready.add(e);
            }
        }
        if (ready.isEmpty()) {
            overBudgetStreak.clear();
            return;
        }

        // 2. Round-robin : une tranche de budget par exécution prête (lissage AC2).
        int slice = Math.max(1, globalFuelBudget / ready.size());
        Set<Identifier> overBudgetNow = new HashSet<>();
        List<Execution> snapshot = new ArrayList<>(ready);
        for (Execution e : snapshot) {
            if (!ready.contains(e)) {
                continue;   // purgée par la faute d'une exécution sœur plus tôt dans ce tick
            }
            long begin = System.nanoTime();
            BlueprintVm.RunOutcome outcome = BlueprintVm.runMeasured(e.ir, e.state, e.env, slice);
            statsOf(e.blueprintId).record(outcome.fuelSpent(), System.nanoTime() - begin);
            switch (outcome.result()) {
                case ExecResult.Done done -> {
                    ready.remove(e);
                    statsOf(e.blueprintId).completed++;
                }
                case ExecResult.Suspended suspended -> {
                    ready.remove(e);
                    e.delayTicks = suspended.ticks();
                    delayed.add(e);
                }
                case ExecResult.OutOfFuel outOfFuel -> overBudgetNow.add(e.blueprintId);
                case ExecResult.Faulted faulted -> {
                    // Correction QA SCHED-002 : la faute désactive le blueprint (écouteur) —
                    // ses exécutions sœurs sont donc purgées aussi, prêtes ET différées.
                    ready.removeIf(other -> other.blueprintId.equals(e.blueprintId));
                    delayed.removeIf(other -> other.blueprintId.equals(e.blueprintId));
                    statsOf(e.blueprintId).faults++;
                    listener.faulted(e.blueprintId, faulted.node(), faulted.message());
                }
            }
        }

        // 3. Police des streaks : N ticks d'affilée en dépassement → purge + notification.
        for (Identifier blueprintId : overBudgetNow) {
            int streak = overBudgetStreak.merge(blueprintId, 1, Integer::sum);
            if (streak >= maxOverBudgetTicks) {
                ready.removeIf(e -> e.blueprintId.equals(blueprintId));
                delayed.removeIf(e -> e.blueprintId.equals(blueprintId));
                overBudgetStreak.remove(blueprintId);
                listener.disabled(blueprintId, streak);
            }
        }
        // Un tick sain remet le compteur à zéro.
        overBudgetStreak.keySet().removeIf(id -> !overBudgetNow.contains(id));
    }

    private MutableStats statsOf(Identifier blueprintId) {
        return stats.computeIfAbsent(blueprintId, k -> new MutableStats());
    }

    private static final class MutableStats {
        int runs;
        int completed;
        int faults;
        long fuel;
        long totalNanos;
        long peakNanos;

        void record(int fuelSpent, long nanos) {
            runs++;
            fuel += fuelSpent;
            totalNanos += nanos;
            peakNanos = Math.max(peakNanos, nanos);
        }

        Stats snapshot() {
            return new Stats(runs, completed, faults, fuel, totalNanos, peakNanos);
        }
    }
}
