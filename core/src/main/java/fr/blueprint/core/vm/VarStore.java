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
