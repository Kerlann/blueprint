package fr.blueprint.client.editor;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * La sélection courante d'un canevas. Logique pure : les règles (Shift additif, clic
 * sur un élément déjà sélectionné qui préserve le groupe pour le glisser) sont testées
 * headless.
 *
 * <p>Générique sur l'identité, parce qu'il y a deux canevas et deux identités : un nœud
 * se désigne par son {@code UUID}, un élément d'écran par son <b>nom</b> (story 10.1,
 * AC2). Recopier cette classe pour le second donnerait deux moteurs de sélection qui
 * divergeraient au premier correctif — le piège que l'épic 5 a déjà rencontré avec le
 * harnais de test des nœuds.
 */
public final class SelectionModel<T> {

    private final Set<T> selected = new LinkedHashSet<>();

    /**
     * Clic sur un élément (ou le vide si null). Additif (Shift) : bascule l'élément.
     * Sinon : sélectionne seul — sauf s'il est déjà sélectionné, auquel cas la
     * sélection est conservée telle quelle pour permettre de glisser le groupe.
     */
    public void click(@Nullable T id, boolean additive) {
        if (id == null) {
            if (!additive) {
                selected.clear();
            }
            return;
        }
        if (additive) {
            if (!selected.remove(id)) {
                selected.add(id);
            }
        } else if (!selected.contains(id)) {
            selected.clear();
            selected.add(id);
        }
    }

    /** Sélection par rectangle : remplace ou ajoute selon {@code additive}. */
    public void selectAll(Collection<T> ids, boolean additive) {
        if (!additive) {
            selected.clear();
        }
        selected.addAll(ids);
    }

    public boolean isSelected(T id) {
        return selected.contains(id);
    }

    public boolean isEmpty() {
        return selected.isEmpty();
    }

    public int size() {
        return selected.size();
    }

    /** Vue non modifiable, dans l'ordre de sélection. */
    public Set<T> ids() {
        return Collections.unmodifiableSet(selected);
    }

    /**
     * Retire une identité disparue. Un élément supprimé ou renommé qui resterait
     * sélectionné ferait porter les actions suivantes sur un nom qui n'existe plus.
     */
    public void remove(T id) {
        selected.remove(id);
    }

    public void clear() {
        selected.clear();
    }
}
