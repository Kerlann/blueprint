package fr.blueprint.api.annotation;

import fr.blueprint.api.node.Permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Déclare un nœud Blueprint à partir d'une <b>méthode statique</b> (story 8.1) : les
 * pins se déduisent de la signature, le corps reste du Java ordinaire.
 *
 * <pre>{@code
 * @BlueprintNode(value = "mymod:mana/drain", category = "player", permission = Permission.GAMEPLAY)
 * public static void drain(ServerPlayer player, @In(def = "10") int amount) {
 *     ManaAPI.drain(player, amount);
 * }
 *
 * @BlueprintNode(value = "mymod:mana/of", pure = true)
 * @Out("mana")
 * public static int manaOf(ServerPlayer player) {
 *     return ManaAPI.of(player);
 * }
 * }</pre>
 *
 * <p>Une méthode {@code void} devient un nœud d'exécution ({@code exec_in}/{@code exec_out}) ;
 * une méthode qui retourne une valeur gagne un pin de sortie (nommé par {@link Out},
 * « result » par défaut) et devient <i>pure</i> si {@code pure = true}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface BlueprintNode {

    /** Identifiant complet du nœud : {@code "monmod:chemin/du/noeud"}. */
    String value();

    /** Catégorie de palette ({@code "player"}, {@code "world"}, …). */
    String category() default "misc";

    /** Nœud pur (aucun pin exec) : évalué à la demande, sans effet de bord. */
    boolean pure() default false;

    Permission permission() default Permission.SAFE;

    int fuelCost() default 1;

    /** Faux si deux appels identiques peuvent différer (aléatoire, heure…). */
    boolean deterministic() default true;

    /** Clé de traduction du titre ; vide = clé standard dérivée de l'identifiant. */
    String titleKey() default "";

    String descKey() default "";
}
