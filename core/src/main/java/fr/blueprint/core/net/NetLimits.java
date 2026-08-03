package fr.blueprint.core.net;

/**
 * Bornes appliquées à TOUT ce qui arrive du réseau (story 6.4, principe P3 : rien
 * n'est illimité). Elles vivent à part du modèle : un graphe local peut être gros,
 * un graphe <i>reçu</i> doit tenir dans ce que le serveur accepte de stocker et de
 * rediffuser.
 */
public record NetLimits(int maxGraphBytes, int maxNodes, int maxLinks, int maxVariables,
                        int maxComments, int maxTextLength, int maxGhosts,
                        int maxScreens, int maxElementsPerScreen,
                        int savesPerWindow, int requestsPerWindow, int clicksPerWindow,
                        int opensPerWindow, long windowMillis) {

    /**
     * Valeurs par défaut. Le graphe compressé plafonne bien en dessous de la borne de
     * décodage ({@link GraphSync#MAX_BYTES}) : celle-ci protège le décodeur, celle-ci
     * protège la sauvegarde du monde.
     *
     * <p>Les plafonds d'écrans reprennent ceux du modèle ({@code GraphLimits.DEFAULT}) :
     * ce qu'un auteur ne peut pas construire localement, un client ne doit pas pouvoir
     * l'envoyer.
     */
    public static final NetLimits DEFAULT = new NetLimits(
            256 * 1024, 1_000, 4_000, 256, 256, 4_096, 256,
            16, 128,
            10, 60, 40, 8, 10_000L);
}
