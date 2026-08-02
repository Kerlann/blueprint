package fr.blueprint.client.editor.history;

import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.NodeTypeLookup;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Pile d'annulation (story 5.6a). Une entrée = les inverses d'un geste complet
 * (un glisser de 40 images = 40 {@code MoveNode} = UNE entrée), appliqués en ordre
 * inverse au undo. Le redo est gratuit : rejouer un inverse rend l'inverse de
 * l'inverse via {@code Result.inverse()}.
 *
 * <p>Pur : le rejeu passe exclusivement par {@code EditOperation.apply} — jamais de
 * mutation directe (coding-standards §3).
 */
public final class UndoStack {

    /** ≥ 50 exigés par le PRD ; au-delà, les plus anciennes entrées tombent. */
    public static final int MAX_ENTRIES = 50;

    private final Deque<List<EditOperation>> undo = new ArrayDeque<>();
    private final Deque<List<EditOperation>> redo = new ArrayDeque<>();
    private List<EditOperation> gesture;

    // -------------------------------------------------------------------- gestes

    /** Ouvre un geste : tout inverse enregistré jusqu'à {@link #endGesture} n'en fait qu'un. */
    public void beginGesture() {
        if (gesture == null) {
            gesture = new ArrayList<>();
        }
    }

    /** Ferme le geste courant ; sans mutation enregistrée, aucune entrée n'est créée. */
    public void endGesture() {
        if (gesture != null && !gesture.isEmpty()) {
            push(undo, gesture);
        }
        gesture = null;
    }

    /**
     * Enregistre l'inverse d'une mutation appliquée. Hors geste, l'inverse forme sa
     * propre entrée. Toute nouvelle action purge la branche redo.
     */
    public void record(EditOperation inverse) {
        redo.clear();
        if (gesture != null) {
            gesture.add(inverse);
        } else {
            List<EditOperation> single = new ArrayList<>(1);
            single.add(inverse);
            push(undo, single);
        }
    }

    private static void push(Deque<List<EditOperation>> stack, List<EditOperation> entry) {
        stack.addFirst(entry);
        while (stack.size() > MAX_ENTRIES) {
            stack.removeLast();
        }
    }

    // --------------------------------------------------------------- undo / redo

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public int undoDepth() {
        return undo.size();
    }

    /** Annule la dernière entrée. Termine d'abord tout geste en cours. */
    public boolean undo(Blueprint bp, NodeTypeLookup lookup) {
        endGesture();
        return replay(undo, redo, bp, lookup);
    }

    public boolean redo(Blueprint bp, NodeTypeLookup lookup) {
        endGesture();
        return replay(redo, undo, bp, lookup);
    }

    /**
     * Rejoue une entrée de {@code from} (inverses en ordre inverse d'enregistrement)
     * et empile ce que le rejeu rend dans {@code to} — la symétrie undo↔redo.
     */
    private static boolean replay(Deque<List<EditOperation>> from, Deque<List<EditOperation>> to,
                                  Blueprint bp, NodeTypeLookup lookup) {
        List<EditOperation> entry = from.pollFirst();
        if (entry == null) {
            return false;
        }
        List<EditOperation> reverse = new ArrayList<>(entry.size());
        for (int i = entry.size() - 1; i >= 0; i--) {
            EditOperation.Result result = entry.get(i).apply(bp, lookup);
            if (result.applied() && result.inverse() != null) {
                reverse.add(result.inverse());
            }
        }
        if (!reverse.isEmpty()) {
            // addFirst sans purge du redo : replay alimente l'autre pile, il ne crée pas d'action.
            to.addFirst(reverse);
        }
        return true;
    }
}
