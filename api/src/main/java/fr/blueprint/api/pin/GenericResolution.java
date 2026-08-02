package fr.blueprint.api.pin;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Résolution des jokers d'un nœud. Une instance vit le temps d'un nœud : tous les
 * pins partageant un même emplacement générique ({@code T}, {@code any}) sont liés
 * par la même résolution — résoudre l'un résout les autres.
 *
 * <pre>{@code
 * var r = new GenericResolution();
 * r.unify(PinTypes.listOf(PinTypes.generic("T")), PinTypes.listOf(PinTypes.INT)); // T := int
 * r.resolve(PinTypes.generic("T"));  // → PinTypes.INT
 * }</pre>
 */
public final class GenericResolution {

    private final Map<String, PinType> bindings = new HashMap<>();

    /** Le type concret lié à un emplacement, ou null s'il est encore libre. */
    public @Nullable PinType binding(String slotName) {
        return bindings.get(slotName);
    }

    /**
     * Tente d'unifier un type déclaré (pouvant contenir des jokers) avec un type
     * concret issu du câblage. Retourne faux en cas de conflit — un emplacement
     * déjà lié à un autre type — sans modifier les liaisons existantes.
     */
    public boolean unify(PinType declared, PinType concrete) {
        Map<String, PinType> attempt = new HashMap<>(bindings);
        if (!unifyInto(declared, concrete, attempt)) {
            return false;
        }
        bindings.clear();
        bindings.putAll(attempt);
        return true;
    }

    private static boolean unifyInto(PinType declared, PinType concrete, Map<String, PinType> out) {
        if (declared == concrete) {
            return true;
        }
        if (declared instanceof GenericPinType slot) {
            if (concrete instanceof GenericPinType) {
                return true; // deux jokers : rien à apprendre
            }
            if (concrete.kind() != PinKind.DATA) {
                return false;
            }
            PinType bound = out.get(slot.name());
            if (bound == null) {
                out.put(slot.name(), concrete);
                return true;
            }
            return bound == concrete;
        }
        if (declared instanceof ParameterizedPinType dp && concrete instanceof ParameterizedPinType cp
                && dp.container() == cp.container() && dp.args().size() == cp.args().size()) {
            for (int i = 0; i < dp.args().size(); i++) {
                if (!unifyInto(dp.args().get(i), cp.args().get(i), out)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /** Substitue les emplacements liés dans {@code declared} ; les libres restent tels quels. */
    public PinType resolve(PinType declared) {
        if (declared instanceof GenericPinType slot) {
            PinType bound = bindings.get(slot.name());
            return bound != null ? bound : declared;
        }
        if (declared instanceof ParameterizedPinType p) {
            List<PinType> resolved = new ArrayList<>(p.args().size());
            boolean changed = false;
            for (PinType arg : p.args()) {
                PinType r = resolve(arg);
                changed |= r != arg;
                resolved.add(r);
            }
            if (!changed) {
                return declared;
            }
            return p.container() == ParameterizedPinType.Container.LIST
                    ? PinTypes.listOf(resolved.get(0))
                    : PinTypes.mapOf(resolved.get(0), resolved.get(1));
        }
        return declared;
    }

    /** Vrai si {@code declared} ne contient plus aucun joker une fois résolu. */
    public boolean isFullyResolved(PinType declared) {
        PinType r = resolve(declared);
        if (r instanceof GenericPinType) {
            return false;
        }
        if (r instanceof ParameterizedPinType p) {
            for (PinType arg : p.args()) {
                if (!isFullyResolved(arg)) {
                    return false;
                }
            }
        }
        return true;
    }
}
