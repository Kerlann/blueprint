package fr.blueprint.core.graph.screen;

/**
 * Un texte d'écran : littéral, ou <b>clé de traduction</b> (story 10.1, AC1e).
 *
 * <p>NFR10 exige que toute chaîne visible soit traduisible. Un libellé figé dans la
 * langue de l'auteur du graphe interdirait tout serveur international — et
 * l'utilisateur d'un serveur ne choisit pas la langue de celui qui a écrit le menu.
 *
 * @param value      le texte, ou la clé si {@code translate}
 * @param translate  vrai si {@code value} est une clé de langue
 */
public record ScreenText(String value, boolean translate) {

    public static final ScreenText EMPTY = new ScreenText("", false);

    public ScreenText {
        if (value == null) {
            value = "";
        }
    }

    public static ScreenText literal(String text) {
        return new ScreenText(text, false);
    }

    public static ScreenText key(String translationKey) {
        return new ScreenText(translationKey, true);
    }

    public boolean isEmpty() {
        return value.isEmpty();
    }
}
