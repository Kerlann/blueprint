package fr.blueprint.client.screen;

import fr.blueprint.core.graph.screen.Screen;
import fr.blueprint.core.graph.screen.ScreenElement;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * La navigation au clavier dans un écran ouvert (story 10.6, AC3).
 *
 * <p>Un menu qu'on ne peut pas parcourir au clavier exclut des joueurs d'une
 * fonctionnalité de <b>jeu</b>, pas d'un outil d'auteur. L'épic 9 avait laissé le
 * câblage au clavier pour plus tard, faute d'une spécification cohérente ; ici l'excuse
 * ne tient pas — c'est le joueur qui est devant l'écran, pas l'auteur.
 *
 * <p>État et parcours <b>purs</b>, hors de la classe d'écran de Minecraft : c'est ce qui
 * les rend vérifiables sans client. L'ordre de tabulation est celui du <b>dessin</b>,
 * comme le hit-test à la souris : deux ordres différents feraient que la touche
 * {@code Tab} et le clic ne désignent pas la même chose, et l'écart ne se verrait
 * qu'en jeu.
 */
public final class ScreenFocus {

    private @Nullable String focused;

    /** L'élément ciblé, ou {@code null} si le clavier n'a pas encore servi. */
    public @Nullable String focused() {
        return focused;
    }

    public void clear() {
        focused = null;
    }

    /**
     * Fixe le focus, à condition que l'élément soit atteignable. Sert au survol de la
     * souris : passer de la souris au clavier ne doit pas repartir du premier bouton.
     */
    public void focus(Screen screen, @Nullable String element) {
        focused = element != null && reachable(screen).contains(element) ? element : null;
    }

    /**
     * Les éléments qu'on peut atteindre au clavier, dans l'ordre de dessin : cliquables,
     * actifs, et visibles — <b>chaîne de parenté comprise</b>. Un bouton dans un onglet
     * masqué ne doit pas recevoir le focus : le joueur taperait sur Entrée sans rien voir
     * se passer, et croirait le menu bloqué.
     */
    public static List<String> reachable(Screen screen) {
        List<String> out = new ArrayList<>();
        for (ScreenElement element : screen.elements().values()) {
            if (element.kind().interactive() && element.enabled()
                    && ScreenPainter.visible(screen, element, ScreenPainter.Visuals.NONE)) {
                out.add(element.name());
            }
        }
        return List.copyOf(out);
    }

    /**
     * Avance d'un cran ({@code delta} = +1) ou recule (−1), en bouclant.
     *
     * <p>Rend l'élément désormais ciblé, ou {@code null} si l'écran n'a rien de
     * cliquable — auquel cas {@code Tab} ne doit pas être consommé : le joueur s'attend
     * alors à ce que la touche fasse ce qu'elle fait ailleurs.
     */
    public @Nullable String move(Screen screen, int delta) {
        List<String> order = reachable(screen);
        if (order.isEmpty()) {
            focused = null;
            return null;
        }
        int index = focused == null ? -1 : order.indexOf(focused);
        if (index < 0) {
            // Jamais ciblé, ou l'élément a disparu depuis : on repart du bord d'où l'on
            // vient. Repartir toujours du premier ferait remonter le focus en haut du
            // menu à chaque Maj+Tab, c'est-à-dire l'inverse de ce qui est demandé.
            focused = delta >= 0 ? order.getFirst() : order.getLast();
            return focused;
        }
        focused = order.get(Math.floorMod(index + delta, order.size()));
        return focused;
    }

    /**
     * L'élément à activer, ou {@code null} si rien n'est ciblé.
     *
     * <p>Revérifie l'atteignabilité : entre la tabulation et la validation, le serveur a
     * pu désactiver le bouton (10.4). L'activer quand même enverrait un clic que le
     * serveur refuserait — et le joueur ne saurait pas pourquoi.
     */
    public @Nullable String activate(Screen screen) {
        return focused != null && reachable(screen).contains(focused) ? focused : null;
    }
}
