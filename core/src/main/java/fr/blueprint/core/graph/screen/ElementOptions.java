package fr.blueprint.core.graph.screen;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Les réglages <b>propres au type</b> d'un élément riche (story 10.8).
 *
 * <p>Un seul record plutôt qu'un champ par type sur {@link ScreenElement} : les six types
 * riches amènent onze réglages, et onze composants de plus rendraient le constructeur
 * canonique inutilisable. Regroupés, ils sont un seul composant qui vaut
 * {@link #NONE} pour les cinq types d'origine — donc invisible pour eux.
 *
 * <p>Chaque réglage n'a de sens que pour certains types. C'est assumé, et documenté
 * champ par champ : la solution propre — une hiérarchie de sous-types — coûterait un
 * encodage polymorphe en NBT, en BScript et sur le réseau, pour des réglages qui tiennent
 * tous en un nombre ou une chaîne.
 */
public record ElementOptions(String placeholder, int maxLength, InputFilter filter,
                             double min, double max, double step,
                             double rowHeight, @Nullable Identifier entity) {

    /** Ce qu'un champ de saisie accepte. Revérifié côté serveur, jamais cru sur parole. */
    public enum InputFilter {
        /** Tout, dans la limite de la longueur. */
        TEXT,
        /** Chiffres et un signe : le clavier numérique du jeu n'existe pas. */
        INTEGER,
        /** Idem, avec un séparateur décimal. */
        DECIMAL,
        /** Lettres, chiffres, {@code _} et {@code -} : un nom d'identifiant. */
        IDENTIFIER
    }

    /** Aucun réglage : la valeur des cinq types d'origine, et le défaut partout. */
    public static final ElementOptions NONE =
            new ElementOptions("", 64, InputFilter.TEXT, 0, 1, 0, 12, null);

    /** Longueur de saisie maximale acceptée, quel que soit le réglage. */
    public static final int MAX_INPUT_LENGTH = 256;

    /** Hauteur de ligne minimale d'une liste : en dessous, plus rien ne se lit. */
    public static final double MIN_ROW_HEIGHT = 6;

    public ElementOptions {
        placeholder = placeholder == null ? "" : placeholder;
        filter = filter == null ? InputFilter.TEXT : filter;
        maxLength = Math.clamp(maxLength, 1, MAX_INPUT_LENGTH);
        if (!Double.isFinite(min)) {
            min = 0;
        }
        if (!Double.isFinite(max)) {
            max = 1;
        }
        // Une plage nulle diviserait par zéro à chaque image d'un curseur. La refuser
        // ici plutôt qu'au rendu : le rendu tourne soixante fois par seconde et n'a
        // aucun moyen de signaler quoi que ce soit.
        if (max == min) {
            max = min + 1;
        }
        step = Double.isFinite(step) && step > 0 ? step : 0;
        rowHeight = Double.isFinite(rowHeight)
                ? Math.max(MIN_ROW_HEIGHT, rowHeight) : NONE_ROW_HEIGHT;
    }

    private static final double NONE_ROW_HEIGHT = 12;

    public static ElementOptions input(String placeholder, int maxLength, InputFilter filter) {
        return new ElementOptions(placeholder, maxLength, filter, 0, 1, 0, NONE_ROW_HEIGHT, null);
    }

    public static ElementOptions slider(double min, double max, double step) {
        return new ElementOptions("", 64, InputFilter.TEXT, min, max, step,
                NONE_ROW_HEIGHT, null);
    }

    public static ElementOptions list(double rowHeight) {
        return new ElementOptions("", 64, InputFilter.TEXT, 0, 1, 0, rowHeight, null);
    }

    public static ElementOptions entity(@Nullable Identifier type) {
        return new ElementOptions("", 64, InputFilter.TEXT, 0, 1, 0, NONE_ROW_HEIGHT, type);
    }

    public ElementOptions withPlaceholder(String value) {
        return new ElementOptions(value, maxLength, filter, min, max, step, rowHeight, entity);
    }

    public ElementOptions withMaxLength(int value) {
        return new ElementOptions(placeholder, value, filter, min, max, step, rowHeight, entity);
    }

    public ElementOptions withFilter(InputFilter value) {
        return new ElementOptions(placeholder, maxLength, value, min, max, step, rowHeight, entity);
    }

    public ElementOptions withRange(double newMin, double newMax) {
        return new ElementOptions(placeholder, maxLength, filter, newMin, newMax, step,
                rowHeight, entity);
    }

    public ElementOptions withStep(double value) {
        return new ElementOptions(placeholder, maxLength, filter, min, max, value,
                rowHeight, entity);
    }

    public ElementOptions withRowHeight(double value) {
        return new ElementOptions(placeholder, maxLength, filter, min, max, step, value, entity);
    }

    public ElementOptions withEntity(@Nullable Identifier value) {
        return new ElementOptions(placeholder, maxLength, filter, min, max, step,
                rowHeight, value);
    }

    /**
     * Le texte est-il acceptable pour ce champ ?
     *
     * <p><b>La question se pose deux fois</b> : le client l'utilise pour refuser une
     * frappe, le serveur pour refuser un paquet (FR52). Un client modifié qui envoie dix
     * mille caractères dans un champ limité à seize doit être <b>ignoré</b>, pas tronqué
     * en silence — tronquer laisserait croire à une saisie que le joueur n'a pas faite.
     */
    public boolean accepts(String text) {
        if (text == null || text.length() > Math.min(maxLength, MAX_INPUT_LENGTH)) {
            return false;
        }
        return switch (filter) {
            case TEXT -> true;
            case INTEGER -> text.matches("-?\\d*");
            // Un point OU une virgule : le joueur tape le séparateur de sa langue, et
            // lui refuser le sien serait un refus qu'il ne comprendrait pas.
            case DECIMAL -> text.matches("-?\\d*([.,]\\d*)?");
            case IDENTIFIER -> text.matches("[a-zA-Z0-9_\\-]*");
        };
    }

    /**
     * La valeur d'un curseur ramenée dans sa plage et alignée sur son pas.
     *
     * <p>L'alignement se fait ici, une fois, et non au rendu : le curseur dessiné et la
     * valeur envoyée au graphe doivent être la même, sinon le joueur relâche sur 7 et le
     * graphe reçoit 6,83.
     */
    public double snap(double value) {
        double bounded = Math.clamp(value, min, max);
        if (step <= 0) {
            return bounded;
        }
        return Math.clamp(min + Math.round((bounded - min) / step) * step, min, max);
    }

    /** La fraction [0, 1] correspondant à une valeur — la position du curseur. */
    public double fractionOf(double value) {
        return Math.clamp((value - min) / (max - min), 0, 1);
    }

    /** L'inverse : la valeur visée par une position, alignée sur le pas. */
    public double valueAt(double fraction) {
        return snap(min + Math.clamp(fraction, 0, 1) * (max - min));
    }
}
