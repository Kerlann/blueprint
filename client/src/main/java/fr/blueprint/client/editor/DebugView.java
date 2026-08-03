package fr.blueprint.client.editor;

import fr.blueprint.core.net.BlueprintPayloads;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ce que l'éditeur sait du débogueur (story 9.1b) : le dernier instantané reçu, rangé
 * pour le rendu. Pur — aucun paquet, aucun écran : le canevas ne fait que lire.
 *
 * <p>Un instantané d'un AUTRE blueprint est ignoré : deux éditeurs ouverts sur deux
 * graphes ne doivent pas se mélanger les valeurs.
 */
public final class DebugView {

    private final Identifier blueprint;
    private boolean debugging;
    private @Nullable UUID pausedAt;
    private Set<UUID> breakpoints = Set.of();
    private Map<UUID, List<String>> values = Map.of();

    public DebugView(Identifier blueprint) {
        this.blueprint = blueprint;
    }

    /** Faux si l'instantané concerne un autre blueprint (il est alors ignoré). */
    public boolean accept(BlueprintPayloads.DebugSnapshot snapshot) {
        if (!snapshot.blueprint().equals(blueprint)) {
            return false;
        }
        debugging = snapshot.debugging();
        pausedAt = snapshot.pausedAt().orElse(null);
        breakpoints = Set.copyOf(snapshot.breakpoints());
        Map<UUID, List<String>> byNode = new LinkedHashMap<>();
        for (BlueprintPayloads.NodeValues node : snapshot.values()) {
            byNode.put(node.node(), List.copyOf(node.lines()));
        }
        values = Map.copyOf(byNode);
        if (!debugging) {
            // Débogage coupé : on n'affiche pas des valeurs figées comme si elles
            // étaient vivantes.
            pausedAt = null;
            breakpoints = Set.of();
            values = Map.of();
        }
        return true;
    }

    public boolean debugging() {
        return debugging;
    }

    public @Nullable UUID pausedAt() {
        return pausedAt;
    }

    public boolean isPaused(UUID node) {
        return node.equals(pausedAt);
    }

    public boolean hasBreakpoint(UUID node) {
        return breakpoints.contains(node);
    }

    public Set<UUID> breakpoints() {
        return breakpoints;
    }

    /** Lignes « pin = valeur » à afficher près du nœud, ou vide. */
    public List<String> valuesOf(UUID node) {
        return values.getOrDefault(node, List.of());
    }

    public int nodesWithValues() {
        return values.size();
    }

    /**
     * Ce nœud a-t-il été traversé ? Le débogueur dépose une entrée pour CHAQUE nœud
     * qui s'exécute — d'où {@code containsKey} et non « la liste est-elle vide » : un
     * nœud sans pin s'exécute aussi, et sa liste est vide.
     */
    public boolean hasRun(UUID node) {
        return values.containsKey(node);
    }

    /** Déconnexion ou fermeture : tout s'efface, rien ne survit à l'écran. */
    public void clear() {
        debugging = false;
        pausedAt = null;
        breakpoints = Set.of();
        values = Map.of();
    }
}
