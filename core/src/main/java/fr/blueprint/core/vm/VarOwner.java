package fr.blueprint.core.vm;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * À qui appartient une variable non locale.
 *
 * <p>Le {@link fr.blueprint.core.graph.VarScope} dit <b>quelle sorte</b> de propriétaire :
 * {@code GRAPH} appartient à un blueprint, {@code PLAYER} à un joueur, {@code WORLD} à
 * personne. Ce record porte les deux identités à la fois, parce qu'une exécution connaît
 * toujours son blueprint et parfois son joueur, et que la portée décide laquelle compte.
 *
 * <p><b>Pourquoi un record plutôt que deux paramètres de plus.</b> Il est construit
 * <b>une fois</b> par lancement, dans la fabrique d'environnement, et relu à chaque
 * {@code LoadVar} : passer l'identifiant et l'UUID à chaque accès aurait été gratuit en
 * allocation mais aurait allongé quatre signatures et deux implémentations. Le construire
 * dans la boucle de la VM, en revanche, aurait violé la règle « aucune allocation dans
 * {@code step} » (coding-standards §5) — d'où sa place dans l'environnement, pas dans
 * l'instruction.
 *
 * @param blueprint le graphe qui s'exécute ; jamais nul hors des tests.
 * @param player    le joueur qui a déclenché, ou <b>nul</b> — {@code server_tick} n'en a
 *                  aucun, et c'est précisément le cas qui doit fauter plutôt que de
 *                  ranger la valeur dans un panier commun.
 */
public record VarOwner(@Nullable Identifier blueprint, @Nullable UUID player) {

    /** Aucun propriétaire : ce que voient les tests qui n'exercent que {@code WORLD}. */
    public static final VarOwner NONE = new VarOwner(null, null);

    /** Le même propriétaire, mais pour un autre joueur — ce que font {@code var/*_for}. */
    public VarOwner forPlayer(@Nullable UUID other) {
        return new VarOwner(blueprint, other);
    }
}
