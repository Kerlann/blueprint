package fr.blueprint.core.graph.screen;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Une modification d'un élément d'un écran <b>ouvert</b> (story 10.4, AC3).
 *
 * <p>L'élément est désigné par son <b>nom</b>, jamais par un index : un index se décale
 * dès qu'on ajoute un élément dans le concepteur, et le graphe se mettrait à modifier le
 * mauvais bouton <i>sans aucune erreur</i>. Le nom est l'identité (10.1, AC2) — le
 * renommer casse visiblement, ce qui vaut infiniment mieux que casser en silence.
 *
 * <p>Un seul record pour les cinq modificateurs, et pas cinq classes : c'est ce qui
 * permet de les <b>comparer</b> (AC3c, on n'envoie que ce qui a changé), de les
 * <b>regrouper</b> par clé dans un tick (AC3b), et de n'écrire qu'un codec réseau.
 * Les champs non pertinents pour un type sont ignorés.
 *
 * @param element l'élément visé
 * @param kind    ce qui change
 * @param text    {@code TEXT} : le libellé ou la clé — {@code TEXTURE} : l'identifiant,
 *                vide pour retirer la texture
 * @param flag    {@code TEXT} : vrai si {@code text} est une clé de traduction —
 *                {@code VISIBLE}/{@code ENABLED} : la valeur
 * @param number  {@code PROGRESS} : la valeur, de 0 à 1
 */
public record ScreenUpdate(String screen, String element, Kind kind, String text,
                           boolean flag, double number) {

    /** Les cinq modificateurs. Leur ordre n'est jamais transmis : seul le nom l'est. */
    public enum Kind {
        TEXT, TEXTURE, VISIBLE, ENABLED, PROGRESS,
        /** Les lignes d'une liste, jointes par des sauts de ligne (story 10.8). */
        LINES,
        /** L'objet d'un emplacement : identifiant dans le texte, quantité dans le nombre. */
        ITEM,
        /** La valeur d'un curseur, ou le texte d'un champ de saisie. */
        VALUE,
        /** Ce que le survol explique — texte, et {@code flag} = clé de traduction. */
        TOOLTIP,
        /**
         * Le style NOMMÉ que suit l'élément ; texte vide = retour à son style en ligne.
         *
         * <p>C'est ce qui rend un menu à onglets possible sans dupliquer les éléments :
         * « onglet actif » et « onglet inactif » sont décrits une fois dans le concepteur,
         * et le graphe bascule les six boutons. Envoyer les neuf couleurs à la place
         * aurait demandé un nœud à neuf entrées, et rien n'aurait garanti que deux onglets
         * « actifs » se ressemblent.
         */
        STYLE,
        /**
         * La position de lecture d'un panneau défilant, en unités (story 10.13).
         *
         * <p>Indispensable et non décoratif : quand le graphe change ce qu'un panneau
         * contient, le décalage gardé pointe dans le vide et le joueur voit une page
         * blanche qu'il devrait remonter à la main pour découvrir qu'elle est pleine.
         * C'est exactement le défaut que les listes ont connu en 10.8.
         */
        SCROLL
    }

    public ScreenUpdate {
        if (screen == null) {
            screen = "";
        }
        if (element == null || element.isBlank()) {
            throw new IllegalArgumentException("une modification vise un élément nommé");
        }
        if (kind == null) {
            throw new IllegalArgumentException("modification sans type : " + element);
        }
        if (text == null) {
            text = "";
        }
        // Une barre hors de [0, 1] se dessinerait au-delà de son cadre. Borner ici
        // plutôt qu'au rendu : le client ne doit pas avoir à se méfier du serveur, et
        // le serveur ne doit pas envoyer ce qu'il sait faux.
        if (kind == Kind.PROGRESS) {
            number = Double.isFinite(number) ? Math.clamp(number, 0, 1) : 0;
        }
    }

    public static ScreenUpdate text(String screen, String element, ScreenText value) {
        return new ScreenUpdate(screen, element, Kind.TEXT, value.value(), value.translate(), 0);
    }

    public static ScreenUpdate tooltip(String screen, String element, ScreenText value) {
        return new ScreenUpdate(screen, element, Kind.TOOLTIP, value.value(),
                value.translate(), 0);
    }

    /** Repositionne un panneau défilant ; zéro le ramène en haut. */
    public static ScreenUpdate scroll(String screen, String element, double offset) {
        return new ScreenUpdate(screen, element, Kind.SCROLL, "", false,
                Double.isFinite(offset) ? Math.max(0, offset) : 0);
    }

    /** Fait suivre à l'élément un style nommé de l'écran ; vide = son style en ligne. */
    public static ScreenUpdate style(String screen, String element, String styleName) {
        return new ScreenUpdate(screen, element, Kind.STYLE, styleName, false, 0);
    }

    public static ScreenUpdate texture(String screen, String element, @Nullable Identifier value) {
        return new ScreenUpdate(screen, element, Kind.TEXTURE,
                value == null ? "" : value.toString(), false, 0);
    }

    public static ScreenUpdate visible(String screen, String element, boolean value) {
        return new ScreenUpdate(screen, element, Kind.VISIBLE, "", value, 0);
    }

    public static ScreenUpdate enabled(String screen, String element, boolean value) {
        return new ScreenUpdate(screen, element, Kind.ENABLED, "", value, 0);
    }

    public static ScreenUpdate progress(String screen, String element, double value) {
        return new ScreenUpdate(screen, element, Kind.PROGRESS, "", false, value);
    }

    /**
     * Les lignes d'une liste. Elles voyagent <b>jointes par des sauts de ligne</b> dans
     * le champ texte : un second champ de type liste sur le fil aurait demandé son propre
     * encodage, alors qu'une ligne de liste ne contient jamais de saut de ligne — elle
     * ne s'afficherait pas sur une ligne si c'était le cas.
     */
    public static ScreenUpdate lines(String screen, String element,
                                     java.util.List<String> values) {
        return new ScreenUpdate(screen, element, Kind.LINES,
                String.join("\n", values), false, values.size());
    }

    /** Les lignes portées par cette modification ; vide si ce n'en est pas une. */
    public java.util.List<String> linesValue() {
        if (kind != Kind.LINES || text.isEmpty()) {
            return java.util.List.of();
        }
        return java.util.List.of(text.split("\n", -1));
    }

    public static ScreenUpdate item(String screen, String element,
                                    @Nullable Identifier id, int count) {
        return new ScreenUpdate(screen, element, Kind.ITEM,
                id == null ? "" : id.toString(), false, count);
    }

    public static ScreenUpdate value(String screen, String element, double number,
                                     boolean checked, String text) {
        return new ScreenUpdate(screen, element, Kind.VALUE, text, checked, number);
    }

    /**
     * La clé de regroupement : deux modifications de même nature sur le même élément
     * se remplacent. Sans elle, écrire deux fois l'or dans un tick en enverrait deux.
     */
    public String key() {
        return screen + ' ' + element + ' ' + kind.name();
    }

    /** La texture demandée, ou {@code null} si l'ordre est de l'enlever ou est invalide. */
    public @Nullable Identifier textureId() {
        return text.isEmpty() ? null : Identifier.tryParse(text);
    }

    public ScreenText screenText() {
        return new ScreenText(text, flag);
    }
}
