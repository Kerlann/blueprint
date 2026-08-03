package fr.blueprint.core.vm;

import fr.blueprint.core.graph.VarScope;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Stockage des variables non locales ({@code GRAPH}, {@code WORLD}, {@code PLAYER}).
 * La portée {@code LOCAL} vit dans l'{@link ExecutionState}, jamais ici.
 * La persistance ({@code SavedData}, quota joueur) arrive avec les stories 6.x —
 * l'implémentation mémoire sert la VM et les tests.
 */
public interface VarStore {

    @Nullable Object get(VarScope scope, String name);

    void set(VarScope scope, String name, @Nullable Object value);

    /**
     * Applique les valeurs par défaut déclarées par un blueprint, <b>sans jamais
     * écraser</b> ce qui existe déjà.
     *
     * <p>Sans cela, un {@code var double or = 20} lu avant d'avoir été écrit rendait
     * {@code null}, et le nœud consommateur tombait en faute avec « le pin n'a ni
     * valeur ni défaut ». Le défaut était donc purement décoratif : l'éditeur
     * l'affichait, la sérialisation le transportait, l'exécution l'ignorait.
     *
     * <p>Appelé au lancement de chaque exécution : c'est le seul moment où l'on
     * connaît à la fois les déclarations et le magasin. Le coût est un parcours des
     * variables du graphe, borné à 256 par les plafonds réseau.
     */
    default void seedDefaults(fr.blueprint.core.graph.Blueprint blueprint) {
        for (var variable : blueprint.variables().values()) {
            if (variable.defaultValue() == null || variable.scope() == VarScope.LOCAL) {
                continue;
            }
            if (get(variable.scope(), variable.name()) == null) {
                set(variable.scope(), variable.name(), variable.defaultValue().value());
            }
        }
    }

    static VarStore inMemory() {
        return new VarStore() {
            private final Map<VarScope, Map<String, Object>> store = new EnumMap<>(VarScope.class);

            @Override
            public @Nullable Object get(VarScope scope, String name) {
                Map<String, Object> vars = store.get(scope);
                return vars == null ? null : vars.get(name);
            }

            @Override
            public void set(VarScope scope, String name, @Nullable Object value) {
                store.computeIfAbsent(scope, s -> new HashMap<>()).put(name, value);
            }
        };
    }
}
