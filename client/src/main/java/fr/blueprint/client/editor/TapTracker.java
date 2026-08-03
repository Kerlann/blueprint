package fr.blueprint.client.editor;

/**
 * Résout le conflit Espace-pan vs Espace-palette (UX §3 vs §6, story 5.4b) :
 * pressé-relâché sans servir au pan = un tap → la palette s'ouvre au relâchement ;
 * la moindre utilisation pour le pan désarme le tap. Pur.
 */
public final class TapTracker {

    private boolean down;
    private boolean used;

    public void press() {
        if (!down) {
            down = true;
            used = false;
        }
    }

    /** La touche vient de servir (pan démarré) : ce n'est plus un tap. */
    public void use() {
        if (down) {
            used = true;
        }
    }

    public boolean isDown() {
        return down;
    }

    /** Relâchement ; vrai si c'était un tap propre. */
    public boolean release() {
        boolean tap = down && !used;
        down = false;
        used = false;
        return tap;
    }
}
