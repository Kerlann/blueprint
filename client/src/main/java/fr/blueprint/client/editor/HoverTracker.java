package fr.blueprint.client.editor;

/**
 * Temporisation du survol. Une infobulle qui suit le curseur en temps réel clignote et
 * masque justement ce qu'on cherche à atteindre ; on attend donc que la souris se pose.
 *
 * <p>Pur (l'horloge est fournie), donc testable sans écran.
 */
public final class HoverTracker {

    /** Assez court pour ne pas se faire attendre, assez long pour ne pas clignoter. */
    public static final long DELAY_MS = 350;

    private double x = Double.NaN;
    private double y = Double.NaN;
    private long since;

    /** @return vrai si la souris est immobile depuis {@link #DELAY_MS}. */
    public boolean settled(double mouseX, double mouseY, long nowMs) {
        if (mouseX != x || mouseY != y) {
            x = mouseX;
            y = mouseY;
            since = nowMs;
            return false;
        }
        return nowMs - since >= DELAY_MS;
    }

    /** Repart de zéro : un clic ou un geste annule le survol en cours. */
    public void reset() {
        x = Double.NaN;
        y = Double.NaN;
    }
}
