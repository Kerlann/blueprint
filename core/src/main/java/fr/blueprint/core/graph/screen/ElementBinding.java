package fr.blueprint.core.graph.screen;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Ce qu'un élément <b>montre</b> d'une variable du blueprint (story 10.7).
 *
 * <p>Les modificateurs de la 10.4 suffisent, et ils demandent que l'auteur pense à
 * appeler le bon nœud à chaque endroit du graphe où la valeur bouge. Pour une valeur
 * affichée en permanence — de l'or, un score, une vie — c'est exactement l'erreur qu'on
 * oublie : dans neuf branches sur dix on y pense, dans la dixième non, et l'écran ment.
 *
 * <p>La liaison inverse la charge : l'élément <b>déclare</b> ce qu'il montre, et un seul
 * {@code gui/refresh} remplace les cinq {@code gui/set_text} qu'il aurait fallu écrire.
 *
 * <p>Le <b>format</b> en fait partie, il n'en est pas un raffinement. Sans lui, lier une
 * étiquette à {@code argent} afficherait « 1234.0 » ; l'auteur mettrait alors un
 * {@code convert/to_string}, un {@code string/concat}, et finirait par appeler
 * {@code gui/set_text} — c'est-à-dire par revenir au cas qu'on voulait éviter.
 */
public record ElementBinding(String variable, Target target, String format,
                             double min, double max, int decimals) {

    /** Ce que la valeur pilote sur l'élément. */
    public enum Target {
        /** Le texte de l'élément, à travers {@link #format}. */
        TEXT,
        /** Le remplissage d'une barre, ramené dans [0, 1] par {@link #min}/{@link #max}. */
        PROGRESS,
        /** L'image, désignée par la valeur elle-même (texte). */
        TEXTURE,
        /** L'élément est-il actif ? La valeur est lue comme un booléen. */
        ENABLED,
        /** L'élément est-il visible ? Idem. */
        VISIBLE
    }

    /** Pas de liaison — la valeur par défaut, et celle de tout écran existant. */
    public static final ElementBinding NONE = new ElementBinding("", Target.TEXT, "%s", 0, 1, 0);

    /** Le marqueur remplacé par la valeur dans un format. */
    public static final String PLACEHOLDER = "%s";

    /** Nombre maximal de décimales : au-delà, on affiche du bruit de virgule flottante. */
    public static final int MAX_DECIMALS = 6;

    public ElementBinding {
        variable = variable == null ? "" : variable.trim();
        target = target == null ? Target.TEXT : target;
        format = format == null || format.isEmpty() ? PLACEHOLDER : format;
        decimals = Math.clamp(decimals, 0, MAX_DECIMALS);
        if (!Double.isFinite(min)) {
            min = 0;
        }
        if (!Double.isFinite(max)) {
            max = 1;
        }
        // Une plage nulle diviserait par zéro à chaque rendu de barre. La refuser à la
        // construction plutôt qu'au rendu : le rendu tourne soixante fois par seconde et
        // n'a aucun moyen de signaler quoi que ce soit.
        if (max == min) {
            max = min + 1;
        }
    }

    public static ElementBinding text(String variable, String format) {
        return new ElementBinding(variable, Target.TEXT, format, 0, 1, 0);
    }

    public static ElementBinding progress(String variable, double min, double max) {
        return new ElementBinding(variable, Target.PROGRESS, PLACEHOLDER, min, max, 0);
    }

    /** Une liaison sans nom de variable n'en est pas une : c'est l'absence de liaison. */
    public boolean bound() {
        return !variable.isEmpty();
    }

    public ElementBinding withVariable(String newVariable) {
        return new ElementBinding(newVariable, target, format, min, max, decimals);
    }

    public ElementBinding withTarget(Target newTarget) {
        return new ElementBinding(variable, newTarget, format, min, max, decimals);
    }

    public ElementBinding withFormat(String newFormat) {
        return new ElementBinding(variable, target, newFormat, min, max, decimals);
    }

    public ElementBinding withRange(double newMin, double newMax) {
        return new ElementBinding(variable, target, format, newMin, newMax, decimals);
    }

    public ElementBinding withDecimals(int newDecimals) {
        return new ElementBinding(variable, target, format, min, max, newDecimals);
    }

    /**
     * Le texte à afficher pour cette valeur.
     *
     * <p>Un {@code null} donne une chaîne vide et non « null » : une variable jamais
     * écrite est une situation ordinaire au premier affichage, pas une anomalie à
     * étaler sur le menu du joueur.
     */
    public String renderText(@Nullable Object value) {
        return format.replace(PLACEHOLDER, plain(value));
    }

    /** La valeur brute, sans format — décimales appliquées si c'est un nombre. */
    public String plain(@Nullable Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number number) {
            return decimals == 0 && number.doubleValue() == Math.rint(number.doubleValue())
                    ? String.valueOf(number.longValue())
                    : String.format(Locale.ROOT, "%." + decimals + "f", number.doubleValue());
        }
        return String.valueOf(value);
    }

    /**
     * Le remplissage d'une barre, dans [0, 1]. Hors bornes, la barre se cale sur le bord
     * plutôt que de déborder : une vie négative ne doit pas dessiner à gauche du cadre.
     */
    public double renderProgress(@Nullable Object value) {
        double raw = value instanceof Number number ? number.doubleValue() : 0;
        return Math.clamp((raw - min) / (max - min), 0, 1);
    }

    /**
     * La valeur lue comme un booléen. Un nombre est vrai s'il n'est pas nul, un texte
     * s'il n'est ni vide ni « false » — la lecture qu'un auteur attend sans avoir à
     * l'apprendre.
     */
    public boolean renderFlag(@Nullable Object value) {
        return switch (value) {
            case null -> false;
            case Boolean flag -> flag;
            case Number number -> number.doubleValue() != 0;
            case String text -> !text.isEmpty() && !text.equalsIgnoreCase("false");
            default -> true;
        };
    }
}
