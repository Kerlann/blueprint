package fr.blueprint.core.net;

import fr.blueprint.core.storage.VarStorage;
import fr.blueprint.core.vm.VarDirty;
import fr.blueprint.core.vm.VarOwner;
import fr.blueprint.core.graph.VarValueNbt;
import fr.blueprint.platform.Platform;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * L'envoi des valeurs répliquées, une trame par joueur et par tick (épic 21, story 21.4).
 *
 * <h2>Pas de table de ce qui est affiché, contrairement aux écrans</h2>
 *
 * <p>{@code ScreenSessions} tient une table par joueur de ce que son client montre, pour
 * n'envoyer que les différences. Ici, cette table n'existe pas — et son absence est un choix,
 * pas un oubli : <b>le carnet des marques EST la différence</b>. {@code VarStorage} ne marque
 * que les écritures qui changent réellement la valeur, le carnet dédoublonne, et il se vide à
 * chaque tick. Une seconde mémoire de ce que chaque client a reçu serait la « signature par
 * liaison, mémorisée par spectateur » que la story 10.7 a écrite puis retirée : deux tables à
 * garder d'accord, et la première divergence se voit comme un écran figé sur une vieille
 * valeur sans que rien ne l'explique.
 *
 * <p>Ce que cette absence coûte : un joueur qui arrive n'a rien reçu, donc il faut lui envoyer
 * l'état complet une fois — {@link #greet}. C'est un cas, nommé, et non une table à tenir.
 *
 * <h2>Qui reçoit quoi</h2>
 *
 * <table>
 *   <tr><th>Portée</th><th>Destinataires</th></tr>
 *   <tr><td>{@code WORLD}, {@code GRAPH}</td><td>tous les joueurs connectés</td></tr>
 *   <tr><td>{@code PLAYER}, {@code PLAYER_SHARED}</td><td><b>le propriétaire seul</b></td></tr>
 * </table>
 *
 * <p>La seconde ligne est une frontière de sécurité et non une optimisation : envoyer à tous la
 * réputation ou le solde de chacun serait une divulgation que rien dans le modèle actuel ne
 * produit. Elle s'adosse à {@code VarOwner}, la règle de possession déjà unique.
 *
 * <p>La première pourrait être plus fine — n'envoyer qu'aux joueurs affichant un écran qui s'y
 * lie. Elle ne l'est pas, parce que la finesse demanderait de connaître les liaisons de tous
 * les écrans de tous les blueprints, y compris ceux d'un autre graphe que celui qui écrit :
 * {@code WORLD} est partagée, donc n'importe quel écran peut la lire. Le coût de ne pas être
 * fin est borné et calculable : au pire 32 valeurs par graphe, une trame par joueur et par
 * tick, et seulement quand une valeur change.
 */
public final class VarReplication {

    private VarReplication() {
    }

    /**
     * Vide le carnet et envoie. Appelé en fin de tick, après l'ordonnanceur — les valeurs
     * écrites par les graphes de ce tick partent donc dans ce tick, pas au suivant.
     *
     * @return le nombre de trames envoyées (diagnostic et tests)
     */
    public static int flush(MinecraftServer server) {
        if (!(fr.blueprint.core.BlueprintMod.varsOf(server) instanceof VarStorage storage)) {
            return 0;
        }
        VarDirty dirty = storage.dirty();
        // Sortie au plus tôt : sur un serveur qui ne réplique rien, une fin de tick coûte
        // cette comparaison et pas un octet de plus.
        if (dirty.isEmpty()) {
            return 0;
        }
        List<VarDirty.Mark> marks = dirty.drain();
        // Par joueur, parce que c'est l'unité d'envoi. Une valeur de portée monde entre dans
        // la liste de chacun ; une valeur de joueur, dans celle d'un seul.
        Map<UUID, List<BlueprintPayloads.VarValue>> perPlayer = new LinkedHashMap<>();
        // Résolue UNE fois, hors de la boucle : c'est une fin de tick, et la règle de
        // coding-standards §5 sur les chemins par tick n'admet pas d'exception au motif que
        // la liste est courte — c'est ainsi qu'on en accumule vingt.
        List<UUID> connected = connected(server.getPlayerList().getPlayers());
        for (VarDirty.Mark mark : marks) {
            // Une marque devient UNE entrée par blueprint qui déclare le nom : le client range
            // par (blueprint, nom), et une variable de portée monde déclarée par deux graphes
            // doit se trouver sous les deux, sinon les écrans du second n'y lisent rien.
            for (BlueprintPayloads.VarValue wire : encode(storage, mark)) {
                for (UUID recipient : recipientsOf(mark, connected)) {
                    perPlayer.computeIfAbsent(recipient, p -> new ArrayList<>()).add(wire);
                }
            }
        }
        return send(server, perPlayer);
    }

    /**
     * Ce qu'un joueur reçoit en arrivant : <b>tout</b> ce qui le concerne.
     *
     * <p>Sans cet envoi, un client ne connaîtrait que les valeurs changées depuis sa
     * connexion, et un écran lié à une variable stable — un prénom choisi la semaine
     * dernière — n'afficherait jamais rien. Le carnet des marques répond à « qu'est-ce qui a
     * changé », jamais à « qu'est-ce qui existe ».
     */
    public static int greet(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null
                || !(fr.blueprint.core.BlueprintMod.varsOf(server) instanceof VarStorage storage)) {
            return 0;
        }
        List<BlueprintPayloads.VarValue> values = new ArrayList<>();
        for (VarDirty.Mark mark : storage.replicatedMarks(player.getUUID())) {
            values.addAll(encode(storage, mark));
        }
        if (values.isEmpty()) {
            return 0;
        }
        return send(server, Map.of(player.getUUID(), values));
    }

    /**
     * Relit la valeur et l'encode. Relue maintenant plutôt que gardée dans la marque : un
     * graphe qui écrit trois fois dans le tick doit envoyer la dernière valeur, et la relecture
     * la donne sans avoir à choisir.
     *
     * @return null si la valeur ne s'encode pas — ce que le validateur interdit déjà, donc un
     *         cas qui ne devrait pas arriver et qu'on ne fait pas tomber pour autant
     */
    private static List<BlueprintPayloads.VarValue> encode(VarStorage storage,
                                                           VarDirty.Mark mark) {
        var declaring = storage.replicating()
                .declaringBlueprints(mark.scope(), mark.blueprint(), mark.name());
        if (declaring.isEmpty()) {
            return List.of();
        }
        VarOwner owner = new VarOwner(mark.blueprint(), mark.player());
        Object value = storage.get(mark.scope(), owner, mark.name());
        CompoundTag wrapper = new CompoundTag();
        if (value != null) {
            Tag encoded = VarValueNbt.encode(value);
            if (encoded == null) {
                // Interdit par le validateur, donc un cas qui ne devrait pas arriver — et
                // qu'on ne fait pas tomber pour autant.
                return List.of();
            }
            wrapper.put("v", encoded);
        }
        // Un compound VIDE dit « effacée » : c'est le seul cas où la variable n'a plus de
        // valeur, et le client n'a donc qu'une forme à reconnaître au lieu de deux.
        List<BlueprintPayloads.VarValue> out = new ArrayList<>(declaring.size());
        for (var blueprint : declaring) {
            out.add(new BlueprintPayloads.VarValue(blueprint, mark.name(), wrapper));
        }
        return out;
    }

    /** Découpe et envoie. Un client qui ne connaît pas le canal est simplement sauté. */
    private static int send(MinecraftServer server,
                            Map<UUID, List<BlueprintPayloads.VarValue>> perPlayer) {
        var network = Platform.serverNetwork();
        int frames = 0;
        for (var entry : perPlayer.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null
                    || !network.canSend(player, BlueprintPayloads.VarValues.TYPE)) {
                continue;
            }
            // Découpé et non tronqué, pour la raison qui a déjà coûté une correction à la
            // trame des écrans : une valeur jetée en silence laisse un élément sur une donnée
            // périmée que le graphe croit avoir changée. Le garde réseau borne un GRAPHE à 32
            // valeurs, mais plusieurs graphes peuvent marquer dans le même tick.
            List<BlueprintPayloads.VarValue> values = entry.getValue();
            for (int from = 0; from < values.size(); from += BlueprintPayloads.MAX_VALUES) {
                int to = Math.min(from + BlueprintPayloads.MAX_VALUES, values.size());
                network.send(player, new BlueprintPayloads.VarValues(
                        List.copyOf(values.subList(from, to))));
                frames++;
            }
        }
        return frames;
    }

    /**
     * Qui doit recevoir cette valeur.
     *
     * <p><b>La frontière de sécurité de tout l'épic tient dans ces trois lignes</b>, et c'est
     * pourquoi elles sont une méthode nommée et testée plutôt qu'un {@code if} au milieu de
     * l'envoi : une valeur qui appartient à un joueur ne part que chez lui. Envoyer à tous la
     * réputation, le solde ou le prénom de chacun serait une divulgation que rien dans le
     * modèle actuel ne produit, et qu'une réplication naïve introduirait d'un trait.
     *
     * <p>Le propriétaire est déjà porté par la marque, normalisée par portée : {@code WORLD} et
     * {@code GRAPH} n'ont pas de joueur, les deux portées joueur en ont un. Il n'y a donc pas
     * de second endroit où se tromper sur ce qui appartient à qui.
     */
    static List<UUID> recipientsOf(VarDirty.Mark mark, List<UUID> connected) {
        return mark.player() == null ? connected : List.of(mark.player());
    }

    private static List<UUID> connected(List<ServerPlayer> players) {
        List<UUID> out = new ArrayList<>(players.size());
        for (ServerPlayer player : players) {
            out.add(player.getUUID());
        }
        return out;
    }
}
