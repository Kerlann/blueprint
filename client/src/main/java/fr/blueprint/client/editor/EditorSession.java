package fr.blueprint.client.editor;

import fr.blueprint.core.graph.Blueprint;
import org.jetbrains.annotations.Nullable;

/**
 * La session d'édition (story 5.9) : l'éditeur travaille toujours sur une COPIE du
 * blueprint ; {@code Ctrl+S} pousse un instantané indépendant vers le serveur via le
 * {@code SaveHandler}. L'indicateur « non enregistré » compare les révisions — pas de
 * comparaison de contenu à chaque image.
 */
public final class EditorSession {

    /** Applique un instantané côté serveur (adopt sur le thread serveur). */
    public interface SaveHandler {
        boolean save(Blueprint snapshot);
    }

    private final Blueprint blueprint;
    private final @Nullable SaveHandler saveHandler;
    private @Nullable Runnable testHandler;
    private int savedRevision;

    private EditorSession(Blueprint blueprint, @Nullable SaveHandler saveHandler) {
        this.blueprint = blueprint;
        this.saveHandler = saveHandler;
        this.savedRevision = blueprint.revision();
    }

    /** Session jetable (démo) : jamais sale, jamais enregistrable. */
    public static EditorSession scratch(Blueprint blueprint) {
        return new EditorSession(blueprint, null);
    }

    /** Session sur une copie d'un blueprint réel. */
    public static EditorSession of(Blueprint copy, SaveHandler saveHandler) {
        return new EditorSession(copy, saveHandler);
    }

    public Blueprint blueprint() {
        return blueprint;
    }

    public boolean savable() {
        return saveHandler != null;
    }

    /** Des éditions non enregistrées existent-elles ? (comparaison de révisions) */
    public boolean dirty() {
        return saveHandler != null && blueprint.revision() != savedRevision;
    }

    /**
     * Enregistre : un {@code copy()} indépendant part vers le serveur — les éditions
     * qui suivent ne mutent jamais l'objet adopté.
     */
    public boolean save() {
        if (saveHandler == null) {
            return false;
        }
        if (saveHandler.save(blueprint.copy())) {
            savedRevision = blueprint.revision();
            return true;
        }
        return false;
    }

    /** Action du bouton Tester après l'enregistrement (activer côté serveur, 5.6b). */
    public void setTestHandler(@Nullable Runnable testHandler) {
        this.testHandler = testHandler;
    }

    /** Tester = enregistrer puis activer ; refusé sans enregistrement possible. */
    public boolean test() {
        if (!save()) {
            return false;
        }
        if (testHandler != null) {
            testHandler.run();
        }
        return true;
    }
}
