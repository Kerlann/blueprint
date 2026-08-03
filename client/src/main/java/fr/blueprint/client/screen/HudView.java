package fr.blueprint.client.screen;

import fr.blueprint.core.graph.screen.Screen;
import fr.blueprint.core.graph.screen.ScreenUpdate;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Les HUD affichés chez ce client (story 10.9).
 *
 * <p><b>Un HUD n'est pas un écran.</b> Un {@code Screen} de Minecraft capte la souris,
 * fige le jeu et se ferme par {@code Échap} ; c'est ce qu'il faut pour une boutique et
 * exactement ce qu'il ne faut pas pour une barre de mana. Confondre les deux — ce que
 * faisait la 10.4 faute d'avoir cette classe — donnait un joueur figé sur place dès
 * qu'on voulait lui montrer son or.
 *
 * <p>Plusieurs HUD coexistent : ils ne captent rien, donc deux HUD sont deux dessins au
 * même endroit, comme la barre de vie et la barre d'expérience du jeu.
 *
 * <p>État pur, testable sans client : le dessin vit dans {@code BlueprintHud}.
 */
public final class HudView {

    /** Les HUD affichés, dans l'ordre d'apparition — c'est l'ordre de dessin. */
    private final Map<String, Screen> shown = new LinkedHashMap<>();
    /** Le remplissage des barres, par écran puis par élément (valeur d'exécution). */
    private final Map<String, Map<String, Double>> progress = new LinkedHashMap<>();
    private boolean hidden;

    /** Affiche un HUD, ou remplace sa description si elle a changé. */
    public void show(Screen screen) {
        shown.put(screen.name(), screen);
    }

    public void hide(String screen) {
        shown.remove(screen);
        progress.remove(screen);
    }

    public void hideAll() {
        shown.clear();
        progress.clear();
    }

    /**
     * La bascule du joueur — la <b>garde de sécurité</b> de l'AC5.
     *
     * <p>Un écran modal a toujours {@code Échap}. Un HUD n'a rien : un graphe fautif
     * affichant un panneau opaque plein écran laisserait le joueur sans recours, voyant
     * son monde caché sans que rien de ce qu'il tape ne le retire. La touche existe
     * donc dès cette story, pas « plus tard si besoin ».
     */
    public void toggleHidden() {
        hidden = !hidden;
    }

    public boolean hidden() {
        return hidden;
    }

    /** Ce qu'il faut dessiner : rien si le joueur a tout masqué. */
    public List<Screen> visible() {
        return hidden ? List.of() : List.copyOf(shown.values());
    }

    public @Nullable Screen get(String screen) {
        return shown.get(screen);
    }

    public int size() {
        return shown.size();
    }

    /**
     * Applique les modifications qui visent un HUD affiché. Celles qui visent autre
     * chose — l'écran modal, un HUD retiré entre-temps — sont <b>ignorées</b> : le
     * client n'invente pas de destinataire.
     *
     * @return le nombre de modifications réellement appliquées
     */
    public int apply(List<ScreenUpdate> updates) {
        int applied = 0;
        for (ScreenUpdate update : updates) {
            Screen screen = shown.get(update.screen());
            if (screen == null) {
                continue;
            }
            var element = screen.element(update.element());
            if (element == null) {
                continue;
            }
            shown.put(screen.name(), screen.replacing(update.element(),
                    switch (update.kind()) {
                        case TEXT -> element.withText(update.screenText());
                        case TEXTURE -> element.withTexture(update.textureId());
                        case VISIBLE -> element.withVisible(update.flag());
                        case ENABLED -> element.withEnabled(update.flag());
                        case PROGRESS -> element;
                        // Les valeurs des éléments riches (10.8) vivent à part, comme
                        // le remplissage des barres : ce sont des données d'exécution,
                        // propres à ce joueur et à cette ouverture, pas des propriétés
                        // du blueprint. Les écrire dans le modèle les ferait voyager
                        // dans la sauvegarde et l'export texte.
                        case LINES, ITEM, VALUE -> element;
                    }));
            if (update.kind() == ScreenUpdate.Kind.PROGRESS) {
                progress.computeIfAbsent(update.screen(), s -> new LinkedHashMap<>())
                        .put(update.element(), update.number());
            }
            applied++;
        }
        return applied;
    }

    public double progressOf(String screen, String element) {
        return progress.getOrDefault(screen, Map.of()).getOrDefault(element, 0.0);
    }

    /** Une déconnexion ne laisse pas un HUD du serveur précédent à l'écran. */
    public void clear() {
        hideAll();
        hidden = false;
    }
}
