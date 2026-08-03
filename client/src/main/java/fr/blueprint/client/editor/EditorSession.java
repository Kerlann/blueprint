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

    /**
     * Pousse un instantané vers le serveur sous verrou optimiste : {@code baseRevision}
     * est la révision servie à l'ouverture (ou au dernier enregistrement accepté).
     * Le retour dit seulement que l'envoi est parti — le verdict arrive plus tard par
     * {@link #saveAccepted(int)} ou {@link #saveRefused(int)}.
     */
    public interface SaveHandler {
        boolean save(Blueprint snapshot, int baseRevision);
    }

    private final Blueprint blueprint;
    private final @Nullable SaveHandler saveHandler;
    private @Nullable Runnable testHandler;
    private int savedRevision;
    /** Révision connue du serveur — jamais choisie par le client (6.3). */
    private int serverRevision;
    private boolean writable = true;

    private EditorSession(Blueprint blueprint, @Nullable SaveHandler saveHandler) {
        this.blueprint = blueprint;
        this.saveHandler = saveHandler;
        this.savedRevision = blueprint.revision();
        this.serverRevision = blueprint.revision();
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
        return saveHandler != null && writable;
    }

    /** Ouverture en lecture seule : le serveur refuse l'écriture à ce joueur (6.3). */
    public void setWritable(boolean writable) {
        this.writable = writable;
    }

    /** Vrai pour un VRAI blueprint que le serveur refuse de laisser modifier. */
    public boolean readOnly() {
        return saveHandler != null && !writable;
    }

    public int serverRevision() {
        return serverRevision;
    }

    /** Des éditions non enregistrées existent-elles ? (comparaison de révisions) */
    public boolean dirty() {
        // Lecture seule : rien à enregistrer, donc jamais de « modifications non
        // enregistrées » — sinon la fermeture bouclerait sur une confirmation dont
        // le bouton Enregistrer ne peut pas aboutir.
        return savable() && blueprint.revision() != savedRevision;
    }

    /**
     * Enregistre : un {@code copy()} indépendant part vers le serveur — les éditions
     * qui suivent ne mutent jamais l'objet adopté.
     */
    public boolean save() {
        if (!savable()) {
            return false;
        }
        int sent = blueprint.revision();
        if (saveHandler.save(blueprint.copy(), serverRevision)) {
            savedRevision = sent;
            return true;
        }
        return false;
    }

    /**
     * Verdict positif du serveur : l'indicateur ● ne réapparaît pas et le verrou
     * optimiste se recale sur la révision que le serveur vient d'attribuer.
     */
    public void saveAccepted(int newServerRevision) {
        this.serverRevision = newServerRevision;
    }

    /**
     * Verdict négatif (conflit, refus, blueprint disparu) : le travail local reste
     * INTACT — seul l'indicateur « non enregistré » revient, et la base du verrou se
     * recale sur la révision courante du serveur. Un second Ctrl+S, en connaissance
     * de cause, écrase alors la version du serveur (une vraie fusion : v1.1).
     */
    public void saveRefused(int currentServerRevision) {
        if (currentServerRevision >= 0) {
            this.serverRevision = currentServerRevision;
        }
        this.savedRevision = -1;
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
