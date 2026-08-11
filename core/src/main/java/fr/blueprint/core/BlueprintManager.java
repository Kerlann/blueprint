package fr.blueprint.core;

import fr.blueprint.core.graph.Blueprint;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Les blueprints d'un serveur. Cycle de vie uniquement — la persistance
 * ({@code SavedData}, rapport de chargement) arrive avec la story 6.1.
 *
 * <p>MODEL-001 : {@code enabled} est un état de cycle de vie serveur (FR20), muté ici
 * et seulement ici — jamais par une {@code EditOperation} ; l'annuler/rétablir de
 * l'éditeur ne le touche pas.
 */
public final class BlueprintManager {

    private static final Map<MinecraftServer, BlueprintManager> BY_SERVER =
            Collections.synchronizedMap(new WeakHashMap<>());

    public static BlueprintManager of(MinecraftServer server) {
        return BY_SERVER.computeIfAbsent(server, BlueprintManager::new);
    }

    /**
     * Le serveur, ou {@code null} pour un gestionnaire de test. Il ne sert qu'à
     * prévenir les joueurs ; tout le cycle de vie fonctionne sans lui, ce qui garde la
     * classe testable headless.
     */
    private final @org.jetbrains.annotations.Nullable MinecraftServer server;
    private final Map<Identifier, Blueprint> blueprints = new LinkedHashMap<>();

    /** Gestionnaire isolé, sans serveur : tests et outillage. */
    public BlueprintManager() {
        this(null);
    }

    private BlueprintManager(@org.jetbrains.annotations.Nullable MinecraftServer server) {
        this.server = server;
    }

    /** Vide si l'identifiant est déjà pris. */
    public Optional<Blueprint> create(Identifier id) {
        if (blueprints.containsKey(id)) {
            return Optional.empty();
        }
        Blueprint bp = new Blueprint(id);
        blueprints.put(id, bp);
        announceList();
        return Optional.of(bp);
    }

    /** Vrai si un blueprint a été supprimé. */
    public boolean delete(Identifier id) {
        boolean removed = blueprints.remove(id) != null;
        if (removed) {
            closeItsScreens(id);
            announceList();
        }
        return removed;
    }

    /**
     * Referme chez tous les joueurs les écrans d'un blueprint qui vient de disparaître
     * ou d'être désactivé (story 10.3).
     *
     * <p>Le laisser affiché donnerait un menu dont chaque clic serait refusé sans que
     * le joueur comprenne pourquoi — et il ne pourrait pas deviner que c'est un
     * administrateur qui vient de couper le graphe derrière lui.
     *
     * <p>La méthode elle-même est plus bas : {@link #closeItsScreens}.
     */

    /**
     * Prévient les clients que la liste a changé.
     *
     * <p>Sans cela, {@code /blueprint-edit} suggérait la liste reçue à la CONNEXION et
     * rien d'autre : créer un blueprint ou charger les exemples ne se voyait pas côté
     * client, et les deux commandes avaient l'air de parler de deux mondes différents.
     */
    private void announceList() {
        // Sous un lot, on note et on se tait : chaque annonce diffuse la liste à tous les
        // joueurs et repose les racines de commandes. Sur « /blueprint enable all » et
        // cinquante blueprints, cinquante annonces feraient cinquante fois le travail de
        // la dernière.
        if (muet > 0) {
            enAttente = true;
            return;
        }
        if (server != null) {
            fr.blueprint.core.net.ServerBlueprintNet.announceList(server);
            // Le même signal sert aux commandes : un blueprint adopté, activé ou désactivé
            // change la liste des noms déclarés. Se brancher ici évite d'avoir à penser
            // aux racines dynamiques à chaque endroit qui modifie un blueprint.
            fr.blueprint.core.BlueprintMod.refreshDynamicCommands(server);
            // Troisième consommateur du même signal (épic 21) : les variables déclarées
            // @replicated ne changent qu'ici — un enregistrement, une création, une
            // suppression. Les relire à chaque écriture aurait mis un parcours de tous les
            // graphes dans le chemin le plus chaud de la VM ; les relire ici les relit
            // quelques fois par heure.
            //
            // Sous un lot, le retour anticipé plus haut nous fait sauter ce rafraîchissement,
            // et c'est sans danger : un lot est une suite synchrone de commandes, aucun tick
            // ne s'exécute entre ses éléments, donc aucune écriture ne peut se produire avec
            // un ensemble périmé.
            fr.blueprint.core.BlueprintMod.refreshReplicatedNames(server);
        }
    }

    /** Profondeur de silence, et une annonce demandée pendant celui-ci. */
    private int muet;
    private boolean enAttente;

    /**
     * Groupe plusieurs changements en <b>une seule</b> annonce.
     *
     * <p>Réentrant : un lot dans un lot ne parle qu'à la sortie du plus extérieur. Si le
     * travail lève, l'annonce part quand même — les changements déjà faits sont réels, et
     * des clients qui les ignorent seraient pires qu'une exception remontée seule.
     */
    public void enLot(Runnable travail) {
        muet++;
        try {
            travail.run();
        } finally {
            muet--;
            if (muet == 0 && enAttente) {
                enAttente = false;
                announceList();
            }
        }
    }

    private void closeItsScreens(Identifier id) {
        if (server != null) {
            fr.blueprint.core.net.ServerBlueprintNet.closeScreensOf(server, id);
        }
    }

    /** Adopte un blueprint existant (import, démo) ; faux si l'identifiant est pris. */
    public boolean adopt(Blueprint blueprint) {
        boolean added = blueprints.putIfAbsent(blueprint.id(), blueprint) == null;
        if (added) {
            announceList();
        }
        return added;
    }

    public Optional<Blueprint> get(Identifier id) {
        return Optional.ofNullable(blueprints.get(id));
    }

    /**
     * Existence seule, sans {@link Optional}.
     *
     * <p>{@code get(id).isEmpty()} alloue une enveloppe par appel. C'est sans importance
     * partout — sauf sur le chemin d'émission des événements, qui purge son cache d'entrées
     * à chaque émission : certains événements partent à chaque coup porté sur le serveur,
     * pas une fois par tick.
     */
    public boolean contains(Identifier id) {
        return blueprints.containsKey(id);
    }

    /** Verdict d'un enregistrement sous verrou optimiste (story 6.3). */
    public enum SaveOutcome {
        /** Instantané adopté, révision incrémentée. */
        SAVED,
        /** Le serveur a bougé depuis l'ouverture : rien n'est écrasé. */
        CONFLICT,
        /** Plus aucun blueprint sous cet identifiant. */
        UNKNOWN
    }

    /** Verdict et révision COURANTE du serveur — le client se recale dessus. */
    public record SaveResult(SaveOutcome outcome, int revision) {
    }

    /**
     * Enregistre un instantané sous verrou optimiste : {@code baseRevision} est la
     * révision servie à l'ouverture. Si elle a bougé, rien n'est écrit — le travail
     * de l'autre éditeur survit, et le client reçoit de quoi se recaler (AC3/AC4).
     *
     * <p>MODEL-001 : {@code enabled} est un état de cycle de vie serveur — l'instantané
     * ne le transporte pas, il hérite de celui en place.
     */
    public SaveResult save(Blueprint snapshot, int baseRevision) {
        Blueprint current = blueprints.get(snapshot.id());
        if (current == null) {
            return new SaveResult(SaveOutcome.UNKNOWN, -1);
        }
        if (current.revision() != baseRevision) {
            return new SaveResult(SaveOutcome.CONFLICT, current.revision());
        }
        snapshot.setEnabled(current.enabled());
        snapshot.adoptRevision(baseRevision + 1);
        blueprints.put(snapshot.id(), snapshot);
        mirror(snapshot);
        // Les écrans ouverts pointent maintenant une version qui n'existe plus telle
        // quelle (10.4, AC5d) : leur renvoyer la description à jour, ou les fermer si
        // l'écran a disparu. Un menu fantôme dont les clics visent des éléments
        // supprimés serait refusé sans que le joueur comprenne pourquoi.
        if (server != null) {
            fr.blueprint.core.net.ServerBlueprintNet.refreshScreensOf(server, snapshot);
        }
        return new SaveResult(SaveOutcome.SAVED, snapshot.revision());
    }

    /** Vrai si le blueprint existe (l'état est appliqué), faux sinon. */
    public boolean setEnabled(Identifier id, boolean enabled) {
        Blueprint bp = blueprints.get(id);
        if (bp == null) {
            return false;
        }
        bp.setEnabled(enabled);
        if (!enabled) {
            closeItsScreens(id);
        }
        // Activer change la liste des commandes déclarées — elle ne compte que les
        // blueprints ACTIFS. Sans ce signal, un blueprint importé puis activé n'obtenait
        // sa racine qu'au prochain événement qui touchait le gestionnaire, ou jamais :
        // /home restait inconnu alors que le graphe, lui, écoutait bien.
        announceList();
        return true;
    }

    /**
     * Ce qui reflète un enregistrement sur le disque, ou {@code null} — personne.
     *
     * <p>Injecté plutôt qu'appelé en dur : écrire un fichier demande le dossier du jeu,
     * donc {@code FabricLoader}, qui n'existe pas dans un test headless. Le manager n'a
     * pas à connaître le disque pour être vérifiable.
     */
    private @Nullable java.util.function.Consumer<Blueprint> mirror;

    public void mirrorWith(@Nullable java.util.function.Consumer<Blueprint> mirror) {
        this.mirror = mirror;
    }

    /**
     * Reflète un enregistrement dans {@code blueprint/exports/}.
     *
     * <p><b>Au mieux</b>, jamais au prix de l'enregistrement : la vérité est dans la
     * sauvegarde du monde, le fichier n'en est qu'un reflet. Un disque plein ou en lecture
     * seule doit faire perdre le reflet, pas le travail — d'où le {@code catch} large, qui
     * serait injustifiable partout ailleurs.
     */
    private void mirror(Blueprint snapshot) {
        if (mirror == null) {
            return;
        }
        try {
            mirror.accept(snapshot);
        } catch (RuntimeException e) {
            BlueprintMod.LOGGER.warn("Reflet de « {} » sur le disque impossible ;"
                    + " l'enregistrement, lui, a bien eu lieu", snapshot.id(), e);
        }
    }

    public Collection<Blueprint> all() {
        return Collections.unmodifiableCollection(blueprints.values());
    }
}
