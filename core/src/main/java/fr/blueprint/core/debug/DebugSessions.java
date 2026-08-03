package fr.blueprint.core.debug;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Les sessions de débogage ouvertes (story 9.1a). Le point d'entrée de la VM.
 *
 * <p><b>Coût nul quand personne ne débogue</b> (AC) : {@link #active()} est une simple
 * lecture de {@code volatile boolean}, consultée une fois par exécution — aucune table
 * n'est interrogée, aucun objet alloué. Ce n'est qu'une fois une session ouverte que la
 * VM paie une recherche par exécution.
 */
public final class DebugSessions {

    private DebugSessions() {
    }

    private static final Map<Identifier, DebugSession> SESSIONS = new ConcurrentHashMap<>();
    private static volatile boolean active;

    public static boolean active() {
        return active;
    }

    /** La session de ce blueprint, ou null — à n'appeler qu'après {@link #active()}. */
    public static @Nullable DebugSession of(Identifier blueprint) {
        return active ? SESSIONS.get(blueprint) : null;
    }

    /** Ouvre (ou retrouve) la session d'un blueprint. */
    public static DebugSession open(Identifier blueprint) {
        DebugSession session = SESSIONS.computeIfAbsent(blueprint, DebugSession::new);
        active = true;
        return session;
    }

    /**
     * Ferme la session. L'exécution éventuellement arrêtée repart : sinon un blueprint
     * resterait figé pour toujours parce qu'un joueur a fermé son débogueur.
     */
    public static boolean close(Identifier blueprint) {
        DebugSession session = SESSIONS.remove(blueprint);
        if (session != null) {
            session.resume();
        }
        active = !SESSIONS.isEmpty();
        return session != null;
    }

    public static void closeAll() {
        SESSIONS.values().forEach(DebugSession::resume);
        SESSIONS.clear();
        active = false;
    }

    public static Collection<DebugSession> all() {
        return SESSIONS.values();
    }
}
