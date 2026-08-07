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
