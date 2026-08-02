package fr.blueprint.client.editor;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * La sélection courante du canevas. Logique pure : les règles (Shift additif, clic
 * sur un nœud déjà sélectionné qui préserve le groupe pour le glisser) sont testées
 * headless.
 */
public final class SelectionModel {

    private final Set<UUID> selected = new LinkedHashSet<>();

    /**
     * Clic sur un nœud (ou le vide si null). Additif (Shift) : bascule le nœud.
     * Sinon : sélectionne seul — sauf s'il est déjà sélectionné, auquel cas la
     * sélection est conservée telle quelle pour permettre de glisser le groupe.
     */
    public void click(@Nullable UUID node, boolean additive) {
        if (node == null) {
            if (!additive) {
                selected.clear();
            }
            return;
        }
        if (additive) {
            if (!selected.remove(node)) {
                selected.add(node);
            }
        } else if (!selected.contains(node)) {
            selected.clear();
            selected.add(node);
        }
    }

    /** Sélection par rectangle : remplace ou ajoute selon {@code additive}. */
    public void selectAll(Collection<UUID> nodes, boolean additive) {
        if (!additive) {
            selected.clear();
        }
        selected.addAll(nodes);
    }

    public boolean isSelected(UUID node) {
        return selected.contains(node);
    }

    public boolean isEmpty() {
        return selected.isEmpty();
    }

    public int size() {
        return selected.size();
    }

    /** Vue non modifiable, dans l'ordre de sélection. */
    public Set<UUID> ids() {
        return Collections.unmodifiableSet(selected);
    }

    public void clear() {
        selected.clear();
    }
}
