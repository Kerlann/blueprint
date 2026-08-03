package fr.blueprint.core.vm;

import fr.blueprint.core.compile.ir.Ir;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * État d'une exécution en cours : compteur de programme, slots, variables locales.
 * Survit entre deux {@code run} (fuel épuisé, suspension) — sa sérialisation pour
 * traverser un redémarrage est la story 3.4.
 */
public final class ExecutionState {

    private int pc;
    private final Object[] slots;
    private final Map<String, Object> locals = new HashMap<>();
    /** Adresses de retour des sous-chaînes (flux structuré 7.1b) ; persistée. */
    private final Deque<Integer> frames = new ArrayDeque<>();

    private ExecutionState(int slotCount) {
        this.slots = new Object[slotCount];
    }

    public static ExecutionState fresh(Ir ir) {
        return new ExecutionState(ir.slotCount());
    }

    /** Reconstruction depuis la persistance (ExecutionNbt, même paquet). */
    static ExecutionState ofSize(int slotCount) {
        return new ExecutionState(slotCount);
    }

    public int slotCount() {
        return slots.length;
    }

    public int pc() {
        return pc;
    }

    void setPc(int pc) {
        this.pc = pc;
    }

    Object[] slots() {
        return slots;
    }

    Map<String, Object> locals() {
        return locals;
    }

    Deque<Integer> frames() {
        return frames;
    }
}
