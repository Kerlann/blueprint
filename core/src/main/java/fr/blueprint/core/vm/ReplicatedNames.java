package fr.blueprint.core.vm;

import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.VarScope;
import fr.blueprint.core.graph.Variable;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Quelles variables sont {@code @replicated}, résolu <b>une fois</b> et non à chaque écriture
 * (épic 21, story 21.3).
 *
 * <h2>Pourquoi cette classe existe</h2>
 *
 * <p>La story 10.7 a explicitement écarté l'instrumentation de {@code VarStore} : « ça touche
 * le chemin chaud de <b>toute</b> exécution, y compris des blueprints qui n'ont jamais ouvert
 * d'écran ». L'objection est juste, et elle porte sur un coût <i>imposé à tous</i>. Elle tombe
 * si le coût est nul pour qui ne réplique pas — et c'est ce que cette classe rend possible :
 * {@link #isEmpty()} est une lecture de champ, et un serveur sans aucune variable répliquée
 * n'exécute jamais rien d'autre. L'AC2 de 10.7 reste vrai au mot près : il n'y a pas de
 * balayage par tick, il y a une marque à l'écriture.
 *
 * <h2>Pourquoi le nom seul ne suffit pas</h2>
 *
 * <p>{@code WORLD} et {@code PLAYER_SHARED} sont partagées <b>entre</b> blueprints
 * ({@code VarBuckets}). Le blueprint A peut déclarer {@code or @world @replicated} pendant que
 * B écrit {@code or @world} sans le drapeau : B ne sait pas qu'il faut marquer, et pourtant
 * c'est bien la valeur que les clients regardent qu'il vient de changer. Ces deux portées se
 * jugent donc sur un ensemble <b>global</b>, alimenté par n'importe quel blueprint.
 *
 * <p>{@code GRAPH} et {@code PLAYER}, à l'inverse, sont isolées par blueprint : seule la
 * déclaration du blueprint qui écrit compte. Les traiter globalement aurait fait répliquer le
 * {@code score} de A parce que B en déclare un du même nom — deux variables qui n'ont rien à
 * voir, et que cette isolation existe précisément pour séparer.
 *
 * <p>{@code LOCAL} n'apparaît nulle part : elle ne survit pas à l'exécution qui l'écrit, et le
 * validateur refuse déjà le drapeau dessus.
 */
public final class ReplicatedNames {

    /** Aucune variable répliquée — le cas de l'immense majorité des serveurs. */
    public static final ReplicatedNames NONE =
            new ReplicatedNames(Set.of(), Map.of());

    /** Noms répliqués de portée {@code WORLD} ou {@code PLAYER_SHARED}, tous graphes confondus. */
    private final Set<String> shared;

    /** Noms répliqués de portée {@code GRAPH} ou {@code PLAYER}, par blueprint déclarant. */
    private final Map<Identifier, Set<String>> isolated;

    private ReplicatedNames(Set<String> shared, Map<Identifier, Set<String>> isolated) {
        this.shared = shared;
        this.isolated = isolated;
    }

    /**
     * Relit les déclarations de tous les blueprints.
     *
     * <p>Appelée quand le gestionnaire mute — création, suppression, enregistrement,
     * activation — et jamais dans un chemin par tick. Le résultat est immuable et
     * <b>remplacé</b> plutôt que modifié, pour que la lecture n'ait pas à se synchroniser.
     */
    public static ReplicatedNames of(Collection<Blueprint> all) {
        Set<String> shared = new HashSet<>();
        Map<Identifier, Set<String>> isolated = new HashMap<>();
        for (Blueprint bp : all) {
            for (Variable variable : bp.variables().values()) {
                if (!variable.replicated() || variable.scope() == VarScope.LOCAL) {
                    continue;
                }
                if (isShared(variable.scope())) {
                    shared.add(variable.name());
                } else {
                    isolated.computeIfAbsent(bp.id(), k -> new HashSet<>())
                            .add(variable.name());
                }
            }
        }
        if (shared.isEmpty() && isolated.isEmpty()) {
            return NONE;   // la même instance : le cas courant ne coûte pas une allocation
        }
        Map<Identifier, Set<String>> frozen = new HashMap<>();
        isolated.forEach((id, names) -> frozen.put(id, Set.copyOf(names)));
        return new ReplicatedNames(Set.copyOf(shared), Map.copyOf(frozen));
    }

    private static boolean isShared(VarScope scope) {
        return scope == VarScope.WORLD || scope == VarScope.PLAYER_SHARED;
    }

    /**
     * Rien n'est répliqué sur ce serveur.
     *
     * <p>Testé <b>avant</b> tout le reste dans le chemin d'écriture : c'est ce qui rend le
     * coût nul pour les graphes qui ne répliquent pas, et donc ce qui répond à l'objection de
     * la story 10.7 au lieu de la contourner.
     */
    public boolean isEmpty() {
        return shared.isEmpty() && isolated.isEmpty();
    }

    /**
     * Cette écriture change-t-elle une valeur que des clients regardent ?
     *
     * @param blueprint le blueprint qui écrit — nécessaire pour les portées isolées, ignoré
     *                  pour les portées partagées
     */
    public boolean covers(VarScope scope, @Nullable Identifier blueprint, String name) {
        if (scope == VarScope.LOCAL) {
            return false;
        }
        if (isShared(scope)) {
            return shared.contains(name);
        }
        Set<String> names = blueprint == null ? null : isolated.get(blueprint);
        return names != null && names.contains(name);
    }
}
