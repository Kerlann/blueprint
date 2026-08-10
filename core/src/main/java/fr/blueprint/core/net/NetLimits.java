package fr.blueprint.core.net;

/**
 * Bornes appliquées à TOUT ce qui arrive du réseau (story 6.4, principe P3 : rien
 * n'est illimité). Elles vivent à part du modèle : un graphe local peut être gros,
 * un graphe <i>reçu</i> doit tenir dans ce que le serveur accepte de stocker et de
 * rediffuser.
 */
public record NetLimits(int maxGraphBytes, int maxNodes, int maxLinks, int maxVariables,
                        int maxReplicatedVariables,
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
     *
     * <p>{@code maxReplicatedVariables} est <b>bien plus bas</b> que {@code maxVariables},
     * et c'est le point : une variable ordinaire coûte de la mémoire serveur une fois, une
     * variable répliquée coûte un envoi par joueur qui la regarde, à chaque fois qu'elle
     * change. 256 × le nombre de joueurs × vingt fois par seconde est un budget que
     * personne n'a chiffré ; trente-deux valeurs par graphe suffisent à tous les usages
     * qu'on sait nommer — une barre, un solde, un compteur, un état — et laissent le coût
     * du pire cas explicable.
     */
    public static final NetLimits DEFAULT = new NetLimits(
            256 * 1024, 1_000, 4_000, 256, 32, 256, 4_096, 256,
            16, 128,
            10, 60, 40, 8, 10_000L);
}
