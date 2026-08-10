package fr.blueprint.core.storage;

import com.mojang.serialization.Codec;
import fr.blueprint.core.graph.VarScope;
import fr.blueprint.core.vm.VarOwner;
import fr.blueprint.core.vm.VarStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les variables de blueprint dans la sauvegarde du monde.
 *
 * <p>Elles n'y étaient pas. {@link VarStore#inMemory()} servait la VM et les tests, et
 * {@code BlueprintMod.varsOf} le câblait tel quel : le prénom qu'un joueur venait de
 * choisir disparaissait au redémarrage du serveur, ce qui vide de son sens la portée
 * {@code PLAYER} — « persistante par joueur », dit sa déclaration.
 *
 * <h2>Ce qui se persiste, et ce qui ne se persiste pas</h2>
 *
 * <p>Une valeur de variable n'a pas de type à l'écriture : {@code StoreVar} porte une
 * portée, un nom et un slot, pas de {@code PinType}. L'encodage se fait donc sur le type
 * <b>Java</b> de la valeur, avec une étiquette pour le relire — chaînes, nombres,
 * booléens, listes et tables de ceux-là, plus les trois types de géométrie (vecteur,
 * position de bloc, direction), qui s'écrivent sans rien demander aux registres du jeu.
 *
 * <p>Le reste — une pile d'objets, une entité, un état de bloc dans une variable — n'est
 * <b>pas</b> écrit, et le journal le dit en nommant la variable. Deviner le type déclaré
 * en fouillant les blueprints aurait été possible, mais deux graphes peuvent déclarer le
 * même nom de variable joueur avec deux types différents : le devinement aurait alors
 * rendu la valeur d'un type à un graphe qui en attend un autre, ce qui est pire que de ne
 * rien rendre du tout.
 */
public final class VarStorage extends SavedData implements VarStore {

    public static final SavedDataType<VarStorage> TYPE = new SavedDataType<>(
            "blueprint_vars", VarStorage::new, codec(), null);

    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("blueprint-vars");

    /** Le MÊME rangement que le magasin mémoire : une seule règle de possession. */
    private final fr.blueprint.core.vm.VarBuckets buckets =
            new fr.blueprint.core.vm.VarBuckets();

    // ------------------------------------------------------------------ le magasin

    @Override
    public @Nullable Object get(VarScope scope, VarOwner owner, String name) {
        if (!VarStore.owns(scope, owner)) {
            return null;
        }
        Map<String, Object> bucket = buckets.of(scope, owner, false);
        return bucket == null ? null : bucket.get(name);
    }

    @Override
    public boolean set(VarScope scope, VarOwner owner, String name, @Nullable Object value) {
        // Un propriétaire manquant rend VRAI : la VM a déjà fauté avant d'arriver ici, et
        // rendre faux ferait remonter le message du plafond à la place du bon.
        if (!VarStore.owns(scope, owner)) {
            return true;
        }
        // La réplication (épic 21). Le test d'ensemble vide passe AVANT tout le reste : sur un
        // serveur sans variable répliquée — l'immense majorité — cette ligne coûte une lecture
        // de champ et une branche, et rien d'autre. C'est ce qui répond à l'objection de la
        // story 10.7 contre l'instrumentation du magasin, plutôt que de la contourner : le
        // coût n'est pas imposé à toute exécution, il est payé par qui réplique.
        if (replicated.isEmpty() || !replicated.covers(scope, owner.blueprint(), name)) {
            return buckets.put(scope, owner, name, value);
        }
        // Relire l'ancienne valeur AVANT d'écrire, pour ne marquer que ce qui change
        // réellement. C'est la leçon de ScreenSessions appliquée à la source : « à chaque
        // tick, écris l'or » ne doit rien envoyer si l'or ne bouge pas, et comparer ici est
        // le seul endroit où la comparaison est exacte plutôt qu'approchée par une empreinte.
        Object before = get(scope, owner, name);
        if (!buckets.put(scope, owner, name, value)) {
            return false;
        }
        if (java.util.Objects.equals(before, value)) {
            return true;
        }
        if (!dirty.mark(scope, owner, name)) {
            LOGGER.warn("Carnet des valeurs répliquées plein : « {} » attendra le prochain "
                    + "changement. Un graphe écrit probablement plus de valeurs répliquées "
                    + "par tick que le protocole n'en porte.", name);
        }
        return true;
    }

    /**
     * Ce qui est {@code @replicated} sur ce serveur, relu quand le gestionnaire mute et jamais
     * dans un chemin par tick. Remplacé et non modifié : la lecture n'a pas à se synchroniser.
     */
    private volatile fr.blueprint.core.vm.ReplicatedNames replicated =
            fr.blueprint.core.vm.ReplicatedNames.NONE;

    /** Les valeurs répliquées changées depuis le dernier envoi (vidé en fin de tick). */
    private final fr.blueprint.core.vm.VarDirty dirty = new fr.blueprint.core.vm.VarDirty();

    /**
     * Prend acte des déclarations courantes.
     *
     * <p>Appelée quand le gestionnaire de blueprints mute. Ce n'est pas le magasin qui décide
     * quand : il ne connaît pas les graphes, et aller les lire lui-même l'aurait fait dépendre
     * du gestionnaire — dans le mauvais sens.
     */
    public void replicating(fr.blueprint.core.vm.ReplicatedNames names) {
        this.replicated = names;
    }

    /** Le carnet des changements, pour l'envoi de fin de tick (story 21.4). */
    public fr.blueprint.core.vm.VarDirty dirty() {
        return dirty;
    }

    /**
     * Tout ce qui est répliqué et qui concerne ce joueur, pour son arrivée (story 21.4).
     *
     * <p>Rend des <b>désignations</b> et non des valeurs encodées : le magasin ne connaît pas
     * le protocole, et l'y faire dépendre aurait inversé le sens de la dépendance —
     * {@code core/net} lit {@code core/storage}, jamais l'inverse. C'est aussi ce qui permet à
     * l'envoi d'arrivée et à l'envoi de fin de tick de partager le même encodage.
     *
     * <p>Un parcours de tous les casiers, mais seulement à la connexion d'un joueur : c'est le
     * moment où le client ne sait rien, et le seul où le carnet des marques — qui répond à
     * « qu'est-ce qui a changé » — ne peut rien dire.
     */
    public List<fr.blueprint.core.vm.VarDirty.Mark> replicatedMarks(UUID player) {
        if (replicated.isEmpty()) {
            return List.of();
        }
        List<fr.blueprint.core.vm.VarDirty.Mark> out = new java.util.ArrayList<>();
        collect(out, VarScope.WORLD, null, null, buckets.world());
        buckets.graph().forEach((id, bucket) ->
                collect(out, VarScope.GRAPH, null, id, bucket));
        Map<String, Object> shared = buckets.sharedPlayer().get(player);
        if (shared != null) {
            collect(out, VarScope.PLAYER_SHARED, player, null, shared);
        }
        Map<Identifier, Map<String, Object>> byBlueprint = buckets.player().get(player);
        if (byBlueprint != null) {
            byBlueprint.forEach((id, bucket) ->
                    collect(out, VarScope.PLAYER, player, id, bucket));
        }
        return List.copyOf(out);
    }

    private void collect(List<fr.blueprint.core.vm.VarDirty.Mark> out, VarScope scope,
                         @Nullable UUID player, @Nullable Identifier blueprint,
                         Map<String, Object> bucket) {
        for (String name : bucket.keySet()) {
            if (replicated.covers(scope, blueprint, name)) {
                out.add(new fr.blueprint.core.vm.VarDirty.Mark(scope, player, blueprint, name));
            }
        }
    }

    /**
     * Efface les données d'un joueur, et le <b>dit dans le journal</b>. Une suppression
     * irréversible qui ne laisse aucune trace est indistinguable d'une perte de données :
     * six mois plus tard, personne ne peut répondre à « qui a effacé ma progression ? ».
     */
    @Override
    public int forget(UUID player) {
        int freed = buckets.forget(player);
        LOGGER.info("Variables joueur de {} effacées — {} octets libérés", player, freed);
        return freed;
    }

    @Override
    public int playerBytes(UUID player) {
        return buckets.playerBytesOf(player);
    }

    @Override
    public boolean isDirty() {
        // Comme BlueprintStorage : données vivantes, on laisse Minecraft écrire à chaque
        // sauvegarde du monde. Suivre les écritures une à une coûterait un drapeau posé
        // dans le chemin le plus chaud de la VM pour économiser une écriture toutes les
        // cinq minutes.
        return true;
    }

    // -------------------------------------------------------------- la sérialisation

    private static Codec<VarStorage> codec() {
        return CompoundTag.CODEC.xmap(VarStorage::fromTag, VarStorage::toTag);
    }

    private static VarStorage fromTag(CompoundTag root) {
        VarStorage storage = new VarStorage();
        readBucket(root.getCompoundOrEmpty("world"), storage.buckets.world());

        CompoundTag graphs = root.getCompoundOrEmpty("graph");
        for (String key : graphs.keySet()) {
            Identifier id = Identifier.tryParse(key);
            if (id == null) {
                continue;   // un identifiant illisible ne doit pas faire tomber le monde
            }
            Map<String, Object> bucket = new HashMap<>();
            readBucket(graphs.getCompoundOrEmpty(key), bucket);
            storage.buckets.graph().put(id, bucket);
        }

        CompoundTag shared = root.getCompoundOrEmpty("playerShared");
        for (String key : shared.keySet()) {
            UUID uuid = uuidOrNull(key);
            if (uuid == null) {
                continue;
            }
            Map<String, Object> bucket = new HashMap<>();
            readBucket(shared.getCompoundOrEmpty(key), bucket);
            storage.buckets.sharedPlayer().put(uuid, bucket);
        }

        // Par joueur, PUIS par blueprint. Un monde écrit avant cette imbrication a des
        // valeurs à plat sous « player » : elles ne se relisent pas, et les défauts
        // déclarés reprennent la main au premier lancement. C'est assumé — le format
        // datait du jour même, aucun serveur n'a pu s'en servir.
        CompoundTag players = root.getCompoundOrEmpty("player");
        for (String key : players.keySet()) {
            UUID uuid = uuidOrNull(key);
            if (uuid == null) {
                continue;
            }
            CompoundTag byBlueprint = players.getCompoundOrEmpty(key);
            for (String owner : byBlueprint.keySet()) {
                Identifier id = Identifier.tryParse(owner);
                if (id == null) {
                    continue;
                }
                Map<String, Object> bucket = new HashMap<>();
                readBucket(byBlueprint.getCompoundOrEmpty(owner), bucket);
                storage.buckets.player()
                        .computeIfAbsent(uuid, k -> new java.util.LinkedHashMap<>())
                        .put(id, bucket);
            }
        }
        // Les casiers ont été remplis directement, sans passer par le chemin qui tient les
        // totaux : sans ce recompte, tous les joueurs repartiraient à zéro octet et le
        // plafond ne s'appliquerait qu'aux données écrites depuis le dernier démarrage.
        storage.buckets.recount();
        return storage;
    }

    private static @Nullable UUID uuidOrNull(String key) {
        try {
            return UUID.fromString(key);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private CompoundTag toTag() {
        CompoundTag root = new CompoundTag();
        root.put("world", writeBucket(buckets.world()));

        CompoundTag graphs = new CompoundTag();
        buckets.graph().forEach((id, bucket) -> graphs.put(id.toString(), writeBucket(bucket)));
        root.put("graph", graphs);

        CompoundTag shared = new CompoundTag();
        buckets.sharedPlayer().forEach((uuid, bucket) ->
                shared.put(uuid.toString(), writeBucket(bucket)));
        root.put("playerShared", shared);

        CompoundTag players = new CompoundTag();
        buckets.player().forEach((uuid, byBlueprint) -> {
            CompoundTag owners = new CompoundTag();
            byBlueprint.forEach((id, bucket) -> owners.put(id.toString(), writeBucket(bucket)));
            players.put(uuid.toString(), owners);
        });
        root.put("player", players);
        return root;
    }

    private static void readBucket(CompoundTag tag, Map<String, Object> into) {
        for (String name : tag.keySet()) {
            Object value = decode(tag.get(name));
            if (value != null) {
                into.put(name, value);
            }
        }
    }

    private static CompoundTag writeBucket(Map<String, Object> bucket) {
        CompoundTag tag = new CompoundTag();
        bucket.forEach((name, value) -> {
            Tag encoded = encode(value);
            if (encoded != null) {
                tag.put(name, encoded);
            } else if (value != null) {
                LOGGER.warn("Variable « {} » non persistée : le type {} ne s'écrit pas dans "
                        + "la sauvegarde. Elle garde sa valeur jusqu'au redémarrage.",
                        name, value.getClass().getSimpleName());
            }
        });
        return tag;
    }

    /**
     * Le format étiqueté vit dans {@link fr.blueprint.core.vm.VarValueNbt} et non ici.
     *
     * <p>Il y est parti quand la réplication (épic 21) en a eu besoin : les valeurs qui
     * voyagent vers un client sont exactement celles qui survivent à un redémarrage, et deux
     * exemplaires de cette règle auraient fini par diverger. La divergence se serait vue
     * comme une variable {@code @replicated} que le validateur accepte et que la sauvegarde
     * laisse tomber en silence.
     *
     * <p>Ces deux méthodes restent pour que les appelants d'ici lisent court.
     *
     * @return null si le type ne se persiste pas.
     */
    private static @Nullable Tag encode(@Nullable Object value) {
        return fr.blueprint.core.vm.VarValueNbt.encode(value);
    }

    private static @Nullable Object decode(@Nullable Tag tag) {
        return fr.blueprint.core.vm.VarValueNbt.decode(tag);
    }
}
