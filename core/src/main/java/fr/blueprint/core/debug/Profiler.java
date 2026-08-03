package fr.blueprint.core.debug;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Profileur par nœud (story 9.2). Compte les passages, le temps et le fuel de chaque
 * nœud d'un blueprint — de quoi répondre à « lequel de mes nœuds coûte cher ? », que
 * les statistiques par blueprint de l'ordonnanceur ne disent pas.
 *
 * <p>Même discipline que le débogueur : <b>coût nul quand personne ne profile</b>
 * ({@link #active()} est un {@code volatile boolean} lu une fois par exécution), et
 * rien de vivant n'est retenu — un identifiant de nœud, un identifiant de type, trois
 * compteurs.
 */
public final class Profiler {

    private static final Map<Identifier, Profiler> PROFILERS = new ConcurrentHashMap<>();
    private static volatile boolean active;

    public static boolean active() {
        return active;
    }

    public static @Nullable Profiler of(Identifier blueprint) {
        return active ? PROFILERS.get(blueprint) : null;
    }

    public static Profiler enable(Identifier blueprint) {
        Profiler profiler = PROFILERS.computeIfAbsent(blueprint, id -> new Profiler());
        active = true;
        return profiler;
    }

    public static boolean disable(Identifier blueprint) {
        boolean removed = PROFILERS.remove(blueprint) != null;
        active = !PROFILERS.isEmpty();
        return removed;
    }

    public static void disableAll() {
        PROFILERS.clear();
        active = false;
    }

    // ------------------------------------------------------------------ mesures

    /** Coût cumulé d'un nœud. {@code nanos} est le temps passé DANS son action. */
    public record NodeCost(UUID node, Identifier type, long calls, long nanos, long fuel) {

        public long averageNanos() {
            return calls == 0 ? 0 : nanos / calls;
        }
    }

    private static final class Counter {
        final Identifier type;
        final AtomicLong calls = new AtomicLong();
        final AtomicLong nanos = new AtomicLong();
        final AtomicLong fuel = new AtomicLong();

        Counter(Identifier type) {
            this.type = type;
        }
    }

    private final Map<UUID, Counter> counters = new ConcurrentHashMap<>();

    public void record(@Nullable UUID node, Identifier type, long nanos, int fuel) {
        if (node == null) {
            return;   // nœud synthétisé par le compilateur : rien à attribuer
        }
        Counter counter = counters.computeIfAbsent(node, k -> new Counter(type));
        counter.calls.incrementAndGet();
        counter.nanos.addAndGet(nanos);
        counter.fuel.addAndGet(fuel);
    }

    /** Les {@code n} nœuds les plus coûteux en temps, du plus cher au moins cher. */
    public List<NodeCost> top(int n) {
        List<NodeCost> all = new ArrayList<>(counters.size());
        counters.forEach((node, counter) -> all.add(new NodeCost(node, counter.type,
                counter.calls.get(), counter.nanos.get(), counter.fuel.get())));
        // Départage par identifiant : deux nœuds à égalité de temps ne doivent pas
        // changer de place d'un affichage à l'autre.
        all.sort(Comparator.comparingLong(NodeCost::nanos).reversed()
                .thenComparing(cost -> cost.node().toString()));
        return all.size() <= n ? List.copyOf(all) : List.copyOf(all.subList(0, n));
    }

    public long totalNanos() {
        return counters.values().stream().mapToLong(c -> c.nanos.get()).sum();
    }

    public long totalCalls() {
        return counters.values().stream().mapToLong(c -> c.calls.get()).sum();
    }

    public int nodeCount() {
        return counters.size();
    }

    public void reset() {
        counters.clear();
    }

    // ------------------------------------------------------------------ rapport

    /**
     * Rapport texte, tel qu'il part dans le chat et dans le fichier d'export : une
     * ligne d'en-tête puis une ligne par nœud, avec sa part du temps total.
     */
    public String report(Identifier blueprint, int top) {
        StringBuilder text = new StringBuilder();
        long total = totalNanos();
        text.append("Blueprint ").append(blueprint).append(" — ")
                .append(totalCalls()).append(" appel(s) sur ").append(nodeCount())
                .append(" nœud(s), ").append(micros(total)).append(" µs au total\n");
        int rank = 1;
        for (NodeCost cost : top(top)) {
            // Locale.ROOT : un rapport se lit et se compare, la virgule décimale du
            // français y ferait varier le texte selon la machine.
            text.append(String.format(java.util.Locale.ROOT,
                    "%2d. %-8s %-34s %6d× %10s µs  %4.1f%%  fuel %d%n",
                    rank++, cost.node().toString().substring(0, 8), cost.type(),
                    cost.calls(), micros(cost.nanos()),
                    total == 0 ? 0.0 : 100.0 * cost.nanos() / total, cost.fuel()));
        }
        return text.toString();
    }

    private static String micros(long nanos) {
        return String.valueOf(nanos / 1_000);
    }
}
