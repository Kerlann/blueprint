package fr.blueprint.core.debug;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Débogage d'un blueprint (story 9.1a) : points d'arrêt, pas-à-pas, dernières valeurs
 * vues sur les pins, trace des nœuds parcourus. Pur — aucune dépendance à la VM ni au
 * réseau : la VM ne fait que <i>demander</i> s'il faut s'arrêter, puis <i>déposer</i>
 * ce qu'elle a vu.
 *
 * <p>Les valeurs sont <b>rendues en texte à l'instant où elles passent</b> et jamais
 * retenues telles quelles : garder une référence vivante (entité, ItemStack, monde) dans
 * une session de débogage la ferait survivre au déchargement du monde.
 */
public final class DebugSession {

    /** Longueur maximale d'une valeur affichée — un texte de 4 Kio n'aide personne. */
    public static final int MAX_VALUE_LENGTH = 64;
    /** Profondeur de la trace d'exécution conservée. */
    public static final int TRACE_LENGTH = 64;

    private final Identifier blueprint;
    private final Set<UUID> breakpoints = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Map<String, String>> values = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> hits = new ConcurrentHashMap<>();
    private final Deque<UUID> trace = new ArrayDeque<>();

    /** Vrai si le prochain nœud doit s'arrêter, quel qu'il soit (pas-à-pas). */
    private volatile boolean stepArmed;
    private volatile @Nullable UUID pausedAt;

    public DebugSession(Identifier blueprint) {
        this.blueprint = blueprint;
    }

    public Identifier blueprint() {
        return blueprint;
    }

    // ------------------------------------------------------------------ arrêts

    public void breakOn(UUID node) {
        breakpoints.add(node);
    }

    public boolean unbreak(UUID node) {
        return breakpoints.remove(node);
    }

    public void clearBreakpoints() {
        breakpoints.clear();
    }

    public Set<UUID> breakpoints() {
        return Set.copyOf(breakpoints);
    }

    /**
     * Faut-il s'arrêter AVANT d'exécuter ce nœud ? Appelé par la VM à chaque nœud tant
     * qu'une session est ouverte.
     *
     * <p>Trois états se croisent ici. <b>Déjà en pause</b> : on le reste, tick après
     * tick, jusqu'à {@code step} ou {@code continue} — sinon le nœud repartirait tout
     * seul au tick suivant. <b>Nœud tout juste relâché</b> : il passe, une fois — sans
     * ça, un point d'arrêt se rearmerait sur lui-même et rien n'avancerait jamais.
     * <b>Sinon</b> : point d'arrêt ou pas-à-pas armé.
     */
    public boolean pauseBefore(UUID node) {
        if (pausedAt != null) {
            return true;
        }
        if (node.equals(released)) {
            released = null;
            return false;
        }
        if (stepArmed || breakpoints.contains(node)) {
            stepArmed = false;
            pausedAt = node;
            return true;
        }
        return false;
    }

    /** Nœud relâché par la dernière reprise : il doit pouvoir s'exécuter une fois. */
    private volatile @Nullable UUID released;

    /** Le nœud sur lequel l'exécution est arrêtée, ou null si elle court. */
    public @Nullable UUID pausedAt() {
        return pausedAt;
    }

    public boolean paused() {
        return pausedAt != null;
    }

    /** Reprendre : le nœud en attente s'exécutera, puis l'exécution continue. */
    public void resume() {
        stepArmed = false;
        released = pausedAt;
        pausedAt = null;
    }

    /**
     * Un pas : le nœud en attente s'exécute, puis on s'arrête au suivant. Sans pause en
     * cours, revient à « s'arrêter au prochain nœud rencontré ».
     */
    public void step() {
        released = pausedAt;
        pausedAt = null;
        stepArmed = true;
    }

    public boolean stepping() {
        return stepArmed;
    }

    // ------------------------------------------------------------------ valeurs

    /**
     * Dépose ce qui vient de traverser un nœud. Les entrées et les sorties partagent la
     * même table, préfixées {@code →} et {@code ←} : c'est ce qu'on veut lire d'un coup
     * d'œil sur un nœud.
     */
    public void record(UUID node, Map<String, Object> inputs, Map<String, Object> outputs) {
        Map<String, String> rendered = new LinkedHashMap<>();
        inputs.forEach((pin, value) -> rendered.put("→" + pin, render(value)));
        outputs.forEach((pin, value) -> rendered.put("←" + pin, render(value)));
        values.put(node, Map.copyOf(rendered));
        hits.merge(node, 1, Integer::sum);
        synchronized (trace) {
            trace.addLast(node);
            while (trace.size() > TRACE_LENGTH) {
                trace.removeFirst();
            }
        }
    }

    /** Texte borné : jamais la valeur elle-même, jamais plus de {@value #MAX_VALUE_LENGTH} caractères. */
    public static String render(@Nullable Object value) {
        String text = String.valueOf(value);
        if (text.length() <= MAX_VALUE_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_VALUE_LENGTH - 1) + "…";
    }

    public Map<String, String> valuesOf(UUID node) {
        return values.getOrDefault(node, Map.of());
    }

    public Map<UUID, Map<String, String>> allValues() {
        return Map.copyOf(values);
    }

    public int hits(UUID node) {
        return hits.getOrDefault(node, 0);
    }

    /** Derniers nœuds parcourus, du plus ancien au plus récent. */
    public List<UUID> trace() {
        synchronized (trace) {
            return List.copyOf(trace);
        }
    }

    /**
     * Résolution d'un nœud par <b>préfixe d'UUID</b> parmi ceux que la session connaît
     * (trace, points d'arrêt, valeurs). Taper un UUID complet dans le chat est
     * impraticable ; 8 caractères suffisent, et un préfixe ambigu est <b>refusé</b>
     * plutôt que deviné — poser un point d'arrêt sur le mauvais nœud ferait perdre
     * plus de temps que d'en taper deux de plus.
     */
    public record Match(@Nullable UUID node, boolean ambiguous) {

        public boolean found() {
            return node != null;
        }
    }

    public Match resolve(String prefix) {
        java.util.Set<UUID> candidates = new java.util.LinkedHashSet<>(trace());
        candidates.addAll(breakpoints);
        candidates.addAll(values.keySet());
        UUID found = null;
        for (UUID candidate : candidates) {
            if (candidate.toString().startsWith(prefix)) {
                if (found != null) {
                    return new Match(null, true);
                }
                found = candidate;
            }
        }
        if (found != null) {
            return new Match(found, false);
        }
        // Un UUID complet reste accepté : l'éditeur, lui, les connaît tous.
        try {
            return new Match(UUID.fromString(prefix), false);
        } catch (IllegalArgumentException e) {
            return new Match(null, false);
        }
    }

    public void clearValues() {
        values.clear();
        hits.clear();
        synchronized (trace) {
            trace.clear();
        }
    }
}
