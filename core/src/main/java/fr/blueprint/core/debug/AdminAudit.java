package fr.blueprint.core.debug;

import fr.blueprint.api.node.Permission;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * Journal d'audit des nœuds {@code ADMIN} (NFR15, story 9.3) : « toute exécution de
 * nœud de niveau ADMIN est journalisée avec blueprint, nœud, acteur et horodatage ».
 *
 * <p>Un nœud {@code ADMIN} exécute des commandes arbitraires ou touche à la gestion du
 * serveur : savoir <b>quel graphe</b> l'a fait tourner, <b>déclenché par qui</b>, est
 * la différence entre un incident explicable et un mystère. Le journal part dans le
 * log serveur (donc dans les sauvegardes de log de l'hébergeur) et les 200 dernières
 * entrées restent en mémoire pour être relues en jeu.
 */
public final class AdminAudit {

    private static final Logger LOG = LoggerFactory.getLogger("blueprint-audit");

    /** Profondeur du journal consultable en jeu. */
    public static final int MEMORY = 200;

    /** Une exécution de nœud ADMIN. {@code actor} est le déclencheur, ou « — ». */
    public record Entry(long epochMillis, Identifier blueprint, UUID node, Identifier type,
                        String actor) {

        @Override
        public String toString() {
            return java.time.Instant.ofEpochMilli(epochMillis) + " " + blueprint + " "
                    + (node == null ? "?" : node.toString().substring(0, 8)) + " " + type
                    + " ← " + actor;
        }
    }

    private static final Deque<Entry> RECENT = new ArrayDeque<>();
    private static volatile boolean enabled = true;

    private AdminAudit() {
    }

    /** Coupé par la configuration serveur ({@code auditAdminNodes}). */
    public static void enabled(boolean value) {
        enabled = value;
    }

    public static boolean enabled() {
        return enabled;
    }

    /**
     * Journalise si — et seulement si — le nœud est de niveau {@code ADMIN}. Le test de
     * permission est fait ici pour que l'appelant (la VM) n'ait qu'une comparaison
     * d'énumération à payer.
     */
    public static void record(Permission permission, Identifier blueprint, UUID node,
                              Identifier type, String actor, long epochMillis) {
        if (!enabled || permission != Permission.ADMIN) {
            return;
        }
        Entry entry = new Entry(epochMillis, blueprint, node, type, actor);
        synchronized (RECENT) {
            RECENT.addLast(entry);
            while (RECENT.size() > MEMORY) {
                RECENT.removeFirst();
            }
        }
        LOG.info("ADMIN {} nœud {} ({}) déclenché par {}", blueprint,
                node == null ? "?" : node, type, actor);
    }

    /** Les entrées récentes, de la plus ancienne à la plus récente. */
    public static List<Entry> recent() {
        synchronized (RECENT) {
            return List.copyOf(RECENT);
        }
    }

    public static void clear() {
        synchronized (RECENT) {
            RECENT.clear();
        }
    }
}
