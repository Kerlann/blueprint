package fr.blueprint.core.net;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Limitation de taux par joueur (story 6.4, AC1) : seau à jetons, horloge injectée —
 * testable sans attendre. Un joueur qui inonde le serveur voit ses paquets tomber ;
 * les autres ne le sentent pas.
 *
 * <p>Volontairement sans purge périodique : {@link #forget} est appelé à la
 * déconnexion, et la table est bornée par le nombre de joueurs connectés.
 */
public final class RateLimiter {

    private final int capacity;
    private final long windowMillis;
    private final LongSupplier clock;
    private final Map<UUID, Bucket> buckets = new HashMap<>();

    /**
     * @param capacity     jetons disponibles par fenêtre
     * @param windowMillis durée de rechargement complet
     */
    public RateLimiter(int capacity, long windowMillis, LongSupplier clock) {
        this.capacity = Math.max(1, capacity);
        this.windowMillis = Math.max(1, windowMillis);
        this.clock = clock;
    }

    private static final class Bucket {
        double tokens;
        long lastMillis;

        Bucket(double tokens, long lastMillis) {
            this.tokens = tokens;
            this.lastMillis = lastMillis;
        }
    }

    /** Consomme un jeton ; faux si le joueur a dépassé son quota. */
    public synchronized boolean allow(UUID player) {
        Bucket bucket = refilled(player);
        if (bucket.tokens < 1.0) {
            return false;
        }
        bucket.tokens -= 1.0;
        return true;
    }

    /** Jetons disponibles à cet instant, rechargement compris (diagnostic et tests). */
    public synchronized double tokens(UUID player) {
        return refilled(player).tokens;
    }

    private Bucket refilled(UUID player) {
        long now = clock.getAsLong();
        Bucket bucket = buckets.computeIfAbsent(player, k -> new Bucket(capacity, now));
        double refill = (now - bucket.lastMillis) * (double) capacity / windowMillis;
        if (refill > 0) {
            bucket.tokens = Math.min(capacity, bucket.tokens + refill);
            bucket.lastMillis = now;
        }
        return bucket;
    }

    public synchronized void forget(UUID player) {
        buckets.remove(player);
    }

    public synchronized int tracked() {
        return buckets.size();
    }
}
