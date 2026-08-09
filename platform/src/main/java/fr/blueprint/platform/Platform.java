package fr.blueprint.platform;

import fr.blueprint.platform.client.ClientPlatform;
import fr.blueprint.platform.net.ClientNetwork;
import fr.blueprint.platform.net.ServerNetwork;

import java.util.ServiceLoader;

/**
 * Ce que le code commun demande à la plateforme — et <b>seulement</b> ce sens-là.
 *
 * <p>La frontière entre Blueprint et son chargeur de mods se traverse dans les deux sens,
 * et les deux ne se traitent pas pareil :
 *
 * <ul>
 *   <li><b>Le code commun appelle la plateforme</b> — il a besoin de quelque chose et ne
 *       sait pas qui le fournit : les chemins du jeu, la liste des mods présents. C'est
 *       ce que cette classe résout, par {@link ServiceLoader}.</li>
 *   <li><b>La plateforme appelle le code commun</b> — les événements du monde,
 *       l'initialisation, les commandes. Là il n'y a rien à découvrir : le module du
 *       chargeur est le point d'entrée, donc c'est lui qui pousse. Un simple appel
 *       suffit, et {@code Platform} n'a pas à en connaître l'existence.</li>
 * </ul>
 *
 * <p>Confondre les deux mènerait à réimplémenter ici le système d'événements du chargeur.
 * Ce n'est pas nécessaire : {@code BlueprintEvents} joue déjà ce rôle côté commun.
 *
 * <p>{@link ServiceLoader} plutôt qu'un mécanisme du chargeur : il est dans le JDK et se
 * comporte à l'identique partout. Un {@code fabric.mod.json} n'a d'équivalent nulle part
 * ailleurs.
 */
public final class Platform {

    private static PlatformPaths paths;
    private static PlatformMods mods;
    private static PlatformRegistrar registrar;
    private static ServerNetwork serverNetwork;
    private static ClientNetwork clientNetwork;
    private static ClientPlatform client;

    private Platform() {
    }

    /** Où le jeu range ses fichiers. */
    public static synchronized PlatformPaths paths() {
        if (paths == null) {
            paths = require(PlatformPaths.class, "les chemins du jeu");
        }
        return paths;
    }

    /** Ce que le chargeur sait des mods présents. */
    public static synchronized PlatformMods mods() {
        if (mods == null) {
            mods = require(PlatformMods.class, "la liste des mods");
        }
        return mods;
    }

    /** Quand les registres du jeu acceptent du contenu neuf. */
    public static synchronized PlatformRegistrar registrar() {
        if (registrar == null) {
            registrar = require(PlatformRegistrar.class, "la fenêtre d'enregistrement");
        }
        return registrar;
    }

    /** Déclarer, envoyer et recevoir les paquets, côté serveur. */
    public static synchronized ServerNetwork serverNetwork() {
        if (serverNetwork == null) {
            serverNetwork = require(ServerNetwork.class, "le réseau serveur");
        }
        return serverNetwork;
    }

    /**
     * Le réseau côté client.
     *
     * <p>À n'appeler <b>que</b> depuis le module client : l'implémentation touche des
     * classes absentes d'un serveur dédié, et {@link ServiceLoader} ne l'instancie qu'ici.
     * Un serveur qui l'appellerait échouerait au chargement de classe — ce qui est le bon
     * comportement, mais mérite d'être su.
     */
    public static synchronized ClientNetwork clientNetwork() {
        if (clientNetwork == null) {
            clientNetwork = require(ClientNetwork.class, "le réseau client");
        }
        return clientNetwork;
    }

    /**
     * Touches et couches de HUD.
     *
     * <p>Même réserve que {@link #clientNetwork()} : réservé au module client, parce que
     * l'implémentation touche des classes absentes d'un serveur dédié.
     */
    public static synchronized ClientPlatform client() {
        if (client == null) {
            client = require(ClientPlatform.class, "les touches et le HUD");
        }
        return client;
    }

    /**
     * Pose une implémentation à la main — <b>pour les tests</b>, et rien d'autre.
     *
     * <p>Les tests de {@code core} tournent sans jeu lancé (coding-standards §7) : aucun
     * module de chargeur n'est sur leur chemin de classes, donc aucun service à trouver.
     * Sans cette porte, tout test touchant à un chemin devrait démarrer Minecraft.
     */
    public static synchronized void install(PlatformPaths implementation) {
        paths = implementation;
    }

    /** Voir {@link #install(PlatformPaths)}. */
    public static synchronized void install(PlatformMods implementation) {
        mods = implementation;
    }

    /**
     * Le service, ou une panne qui se lit.
     *
     * <p>Le chargeur de classes est celui de {@code Platform}, pas celui du fil courant :
     * dans un jeu moddé, le second n'est pas forcément celui qui voit les mods, et une
     * recherche vide y ressemblerait à une absence d'implémentation.
     */
    private static <T> T require(Class<T> service, String what) {
        var found = ServiceLoader.load(service, Platform.class.getClassLoader()).iterator();
        if (!found.hasNext()) {
            // Ce cas ne se produit que sur un JAR mal construit : le module du chargeur
            // n'a pas été inclus, ou son META-INF/services a été perdu au remapping. Le
            // dire en toutes lettres évite une chasse à l'initialisation manquante.
            throw new IllegalStateException(
                    "Aucune implémentation de " + service.getSimpleName() + " (" + what + ") : "
                            + "le module du chargeur (fabric, neoforge) est absent du chemin de "
                            + "classes, ou son META-INF/services n'a pas survécu à la construction");
        }
        T implementation = found.next();
        if (found.hasNext()) {
            // Deux chargeurs dans le même JAR : le premier trouvé gagnerait en silence,
            // et l'ordre de ServiceLoader n'est pas spécifié — donc pas reproductible.
            throw new IllegalStateException(
                    "Deux implémentations de " + service.getSimpleName() + " sur le chemin de "
                            + "classes : " + implementation.getClass().getName() + " et "
                            + found.next().getClass().getName());
        }
        return implementation;
    }
}
