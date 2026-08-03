package fr.blueprint.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Nomme un pin d'entrée et, éventuellement, sa valeur par défaut (story 8.1).
 *
 * <p>Sans cette annotation, le pin porte le nom du paramètre — <b>à condition</b> que
 * le mod compile avec {@code -parameters}. Sinon la déclaration est refusée avec un
 * message qui le dit : mieux vaut un refus clair que des pins {@code arg0}, {@code arg1}
 * gravés dans les graphes des joueurs.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface In {

    /** Nom du pin ; vide = nom du paramètre. */
    String value() default "";

    /**
     * Valeur par défaut, écrite comme dans un fichier de config : {@code "10"},
     * {@code "3.5"}, {@code "true"}, {@code "bonjour"}. Prise en charge pour les types
     * de base (int, long, double, booléen, texte) ; vide = pas de défaut, le pin devra
     * être câblé.
     */
    String def() default "";
}
