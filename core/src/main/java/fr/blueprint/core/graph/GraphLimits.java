package fr.blueprint.core.graph;

/** Bornes structurelles d'un blueprint (principe P3 : rien n'est illimité). */
public record GraphLimits(int maxNodes, int maxScreens, int maxElementsPerScreen) {

    /** Valeur par défaut ; configurable serveur (story 9.3, élargie épic 10). */
    public static final GraphLimits DEFAULT = new GraphLimits(1000, 16, 128);

    /** Compatibilité : les appelants qui ne bornent que les nœuds. */
    public GraphLimits(int maxNodes) {
        this(maxNodes, DEFAULT_SCREENS, DEFAULT_ELEMENTS);
    }

    private static final int DEFAULT_SCREENS = 16;
    private static final int DEFAULT_ELEMENTS = 128;
}
