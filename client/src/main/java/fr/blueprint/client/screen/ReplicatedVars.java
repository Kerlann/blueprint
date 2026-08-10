package fr.blueprint.client.screen;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Ce que le serveur a dit des variables {@code @replicated}, côté client (épic 21, story 21.5).
 *
 * <h2>En lecture seule, et ce n'est pas une convention</h2>
 *
 * <p>Rien ici n'écrit vers le serveur, et il n'existe aucun paquet montant pour le faire.
 * FR52 : « le serveur ne fait jamais confiance à ce qu'un client déclare ». Ce cache donne au
 * client de quoi <b>afficher</b> une valeur sans aller-retour, jamais de quoi décider ce qu'elle
 * vaut. Un client modifié qui écrirait dedans ne tromperait que son propre écran.
 *
 * <p>Le client ne connaît toujours pas les <i>variables</i> — leur portée, leur type déclaré,
 * leur valeur par défaut. Il connaît des valeurs nommées que le serveur lui a envoyées, ce qui
 * est très différent et suffit à peindre.
 *
 * <h2>Rangé par blueprint</h2>
 *
 * <p>Et non par portée, parce qu'une liaison d'écran ne nomme qu'une variable : c'est la
 * déclaration du blueprint qui dit sa portée, et cette déclaration n'arrive jamais ici. Un nom
 * est unique dans un blueprint, donc {@code (blueprint, nom)} désigne exactement une valeur —
 * et le serveur envoie sous ce couple, quitte à envoyer deux fois une variable de portée monde
 * que deux graphes déclarent.
 */
public final class ReplicatedVars {

    private ReplicatedVars() {
    }

    /** Par blueprint, puis par nom. Écrit par le fil de réseau, lu par celui de rendu. */
    private static final Map<Identifier, Map<String, Object>> VALUES = new HashMap<>();

    /**
     * Applique ce que le serveur envoie.
     *
     * <p>Une valeur {@code null} <b>efface</b> l'entrée plutôt que de ranger un null : le
     * lecteur ne distingue pas « absente » de « présente et nulle », et les deux doivent donner
     * le même repli.
     */
    public static synchronized void put(Identifier blueprint, String name,
                                        @Nullable Object value) {
        if (value == null) {
            Map<String, Object> bucket = VALUES.get(blueprint);
            if (bucket != null) {
                bucket.remove(name);
                if (bucket.isEmpty()) {
                    VALUES.remove(blueprint);
                }
            }
            return;
        }
        VALUES.computeIfAbsent(blueprint, k -> new HashMap<>()).put(name, value);
    }

    /** La valeur, ou {@code null} si le serveur n'en a jamais envoyé. */
    public static synchronized @Nullable Object get(Identifier blueprint, String name) {
        Map<String, Object> bucket = VALUES.get(blueprint);
        return bucket == null ? null : bucket.get(name);
    }

    /**
     * Le cache d'un blueprint, sous la forme que {@code ScreenBindings} attend.
     *
     * <p>Une fonction et non une copie de la table : c'est ce qui permet d'appeler <b>le même
     * code de rendu</b> que le serveur, celui qui décide du format, des décimales et des bornes
     * d'une barre. Deux implémentations auraient divergé sur la première valeur limite, et la
     * divergence se serait vue au pixel.
     */
    public static java.util.function.Function<String, Object> lookup(Identifier blueprint) {
        return name -> get(blueprint, name);
    }

    /** Le client quitte un serveur : ces valeurs étaient les siennes. */
    public static synchronized void clear() {
        VALUES.clear();
    }

    /** Nombre de blueprints pour lesquels une valeur est connue (diagnostic et tests). */
    public static synchronized int trackedBlueprints() {
        return VALUES.size();
    }
}
