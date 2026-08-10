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
     *
     * <p><b>Contrat « déclaré d'abord »</b> : le premier argument est toujours le type
     * <b>déclaré par le nœud</b> (celui qui peut contenir des jokers), le second le type
     * <b>concret apporté par le câblage</b>. L'appel n'est pas symétrique :
     * {@code unify(ANY, INT)} lie le joker, {@code unify(INT, ANY)} échoue — un type
     * concret déclaré n'apprend rien d'un joker entrant, la résolution appartient au
     * nœud qui déclare le joker.
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
            // Parenté, pas identité. Un emplacement lié à « player » que l'on retrouve
            // face à « entity » n'est pas un conflit : un joueur EST une entité, et le
            // lien lui-même a déjà été accepté par isAssignableFrom — c'est lui qui fait
            // autorité sur la compatibilité, pas cette résolution, dont le seul travail
            // est de lier les emplacements de façon cohérente.
            //
            // L'identité refusait donc ce que la règle des liens autorise : parcourir la
            // liste de query/players pour en lire le nom était impossible, faute d'un
            // nœud « nom de joueur » — entity/name est le seul, et il prend une entité.
            //
            // Deux types sans parenté restent refusés : « int » puis « string » n'ont pas
            // de sens commun, et c'est le vrai conflit que ce contrôle existe pour voir.
            return bound == concrete
                    || concrete.isAssignableFrom(bound)
                    || bound.isAssignableFrom(concrete);
        }
        // Un joker du côté CONCRET n'apprend rien non plus — exactement la même raison que
        // « deux jokers » ci-dessus, et le cas manquait.
        //
        // C'est ce qui rendait les collections inutilisables : la sortie d'un nœud de
        // variable est « any », donc relier une variable à « map<K, V> » levait un
        // GENERIC_CONFLICT. Aucune table, aucune liste ne pouvait donc être rangée dans une
        // variable — et sans rangement, une collection ne survit pas à la fin de
        // l'exécution. Les scalaires n'étaient épargnés que par raccroc : leur pin déclaré
        // ne contient aucun générique, donc le contrôle ne s'exécutait même pas.
        //
        // Ce que l'on perd est réel et assumé : « any » efface le type: ranger une liste
        // dans une variable relue comme table ne se voit plus à la validation, et faute au
        // premier nœud qui la lit. C'est le compromis déjà consenti pour toutes les
        // variables scalaires depuis toujours.
        if (concrete instanceof GenericPinType) {
            return declared.kind() == PinKind.DATA;
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
