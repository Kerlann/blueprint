package fr.blueprint.core.vm;

import fr.blueprint.core.graph.VarScope;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Où chaque variable est <b>rangée</b>, selon sa portée et son propriétaire.
 *
 * <p>Le rangement vivait en double, dans le magasin mémoire et dans celui de la
 * sauvegarde. Deux copies d'une règle de possession finissent par diverger, et la
 * divergence se voit comme une variable qui a une valeur en jeu et une autre après un
 * redémarrage — la panne la plus difficile à croire.
 *
 * <h2>Des tables imbriquées, pas une clé composée</h2>
 *
 * <p>Une clé {@code "uuid:blueprint:nom"} aurait tenu en une table, mais elle se
 * construirait à <b>chaque accès</b>, dans le chemin le plus chaud de la VM
 * (coding-standards §5, aucune allocation dans {@code step}). Et le nom d'une variable
 * est libre : rien n'empêche d'en appeler une {@code a:b}, ce qui la ferait se confondre
 * avec un préfixe. Deux recherches dans deux tables ne coûtent rien et ne mentent pas.
 */
public final class VarBuckets {

    private final Map<String, Object> world = new HashMap<>();
    private final Map<Identifier, Map<String, Object>> graph = new LinkedHashMap<>();
    private final Map<UUID, Map<String, Object>> sharedPlayer = new LinkedHashMap<>();
    /** Par joueur, puis par blueprint : c'est cette imbrication qui isole les graphes. */
    private final Map<UUID, Map<Identifier, Map<String, Object>>> player = new LinkedHashMap<>();

    /**
     * Le poids estimé des données de chaque joueur, tenu <b>au fil des écritures</b>
     * (NFR14, voir {@link VarQuota}).
     *
     * <p>Incrémental et non recalculé à la demande : le plafond doit se vérifier à chaque
     * écriture, et reparcourir toutes les variables d'un joueur à chaque écriture aurait
     * transformé une borne en ralentissement. Le total se recale par {@link #recount()}
     * après un chargement, seul moment où les tables sont remplies sans passer par
     * {@link #put}.
     */
    private final Map<UUID, Integer> playerBytes = new HashMap<>();

    /**
     * Le casier d'une portée, ou {@code null}.
     *
     * @param create le créer s'il manque — vrai à l'écriture, faux à la lecture. Lire ne
     *               doit pas laisser derrière soi une table vide par joueur croisé.
     */
    public @Nullable Map<String, Object> of(VarScope scope, VarOwner owner, boolean create) {
        return switch (scope) {
            case WORLD -> world;
            case GRAPH -> create
                    ? graph.computeIfAbsent(owner.blueprint(), k -> new LinkedHashMap<>())
                    : graph.get(owner.blueprint());
            case PLAYER_SHARED -> create
                    ? sharedPlayer.computeIfAbsent(owner.player(), k -> new LinkedHashMap<>())
                    : sharedPlayer.get(owner.player());
            case PLAYER -> {
                if (create) {
                    yield player.computeIfAbsent(owner.player(), k -> new LinkedHashMap<>())
                            .computeIfAbsent(owner.blueprint(), k -> new LinkedHashMap<>());
                }
                var byBlueprint = player.get(owner.player());
                yield byBlueprint == null ? null : byBlueprint.get(owner.blueprint());
            }
            case LOCAL -> null;
        };
    }

    /** Cette portée compte-t-elle dans le plafond d'un joueur (NFR14) ? */
    public static boolean chargedToPlayer(VarScope scope) {
        return scope == VarScope.PLAYER || scope == VarScope.PLAYER_SHARED;
    }

    /**
     * Écrit une valeur, ou refuse parce que le joueur a atteint son plafond.
     *
     * <p>Le passage par cette méthode plutôt que par {@link #of} suivi d'un {@code put}
     * n'est pas cosmétique : le total par joueur ne peut être juste que si <b>toutes</b> les
     * écritures passent au même endroit. Les casiers restent exposés pour la sérialisation,
     * qui les remplit en bloc et appelle {@link #recount()}.
     *
     * @return faux si — et seulement si — le plafond refuse l'écriture. Rien n'est alors
     *         modifié, ni le casier ni le total.
     */
    public boolean put(VarScope scope, VarOwner owner, String name, @Nullable Object value) {
        if (!chargedToPlayer(scope)) {
            Map<String, Object> bucket = of(scope, owner, true);
            if (bucket != null) {
                bucket.put(name, value);
            }
            return true;
        }
        UUID uuid = owner.player();
        Map<String, Object> existing = of(scope, owner, false);
        // Le coût de ce qu'on remplace n'est retiré que si l'entrée existait : une entrée
        // absente ne pèse rien, et la compter ferait dériver le total vers le bas à chaque
        // première écriture.
        int before = existing != null && existing.containsKey(name)
                ? VarQuota.entrySize(name, existing.get(name))
                : 0;
        int after = VarQuota.entrySize(name, value);
        int total = playerBytes.getOrDefault(uuid, 0) + after - before;
        // Seule une écriture qui FAIT GROSSIR peut être refusée. Sans cette condition, un
        // joueur déjà au-delà du plafond — parce qu'un monde a été écrit avant qu'il
        // n'existe — ne pourrait plus rien écrire, pas même pour se réduire.
        if (after > before && total > VarQuota.MAX_PLAYER_BYTES) {
            return false;
        }
        Map<String, Object> bucket = of(scope, owner, true);
        if (bucket == null) {
            return true;
        }
        bucket.put(name, value);
        playerBytes.put(uuid, Math.max(0, total));
        return true;
    }

    /**
     * Efface toutes les données d'un joueur — les deux portées joueur (NFR14, « les données
     * d'un joueur sont supprimables »).
     *
     * <p>{@code GRAPH} et {@code WORLD} ne sont pas touchés : ce ne sont pas les données de
     * ce joueur, et un effacement qui emporterait le score du monde parce qu'un joueur a
     * demandé le sien serait une panne bien pire que celle qu'on répare.
     *
     * @return le poids libéré, en octets estimés
     */
    public int forget(UUID uuid) {
        int freed = playerBytes.getOrDefault(uuid, 0);
        player.remove(uuid);
        sharedPlayer.remove(uuid);
        playerBytes.remove(uuid);
        return freed;
    }

    /** Le poids estimé des données de ce joueur, en octets (diagnostic et tests). */
    public int playerBytesOf(UUID uuid) {
        return playerBytes.getOrDefault(uuid, 0);
    }

    /**
     * Recompte les totaux par joueur. Appelé après un chargement, parce que la
     * désérialisation remplit les casiers directement — et un total resté à zéro ferait
     * croire tous les joueurs vides jusqu'à leur première écriture.
     */
    public void recount() {
        playerBytes.clear();
        player.forEach((uuid, byBlueprint) -> {
            int total = 0;
            for (Map<String, Object> bucket : byBlueprint.values()) {
                total += weigh(bucket);
            }
            playerBytes.merge(uuid, total, Integer::sum);
        });
        sharedPlayer.forEach((uuid, bucket) ->
                playerBytes.merge(uuid, weigh(bucket), Integer::sum));
    }

    private static int weigh(Map<String, Object> bucket) {
        int total = 0;
        for (var entry : bucket.entrySet()) {
            total += VarQuota.entrySize(entry.getKey(), entry.getValue());
        }
        return total;
    }

    // ------------------------------------------------- accès pour la sérialisation

    public Map<String, Object> world() {
        return world;
    }

    public Map<Identifier, Map<String, Object>> graph() {
        return graph;
    }

    public Map<UUID, Map<String, Object>> sharedPlayer() {
        return sharedPlayer;
    }

    public Map<UUID, Map<Identifier, Map<String, Object>>> player() {
        return player;
    }
}
