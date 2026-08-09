package fr.blueprint.client.editor.screen;

import fr.blueprint.core.graph.screen.Screen;
import fr.blueprint.core.graph.screen.ScreenElement;
import fr.blueprint.core.graph.screen.ScreenLayout;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Changer de parent sans changer de place.
 *
 * <p>Le glisser dans l'arbre des calques déplace un élément dans la <b>hiérarchie</b>.
 * Rien dans ce geste ne dit qu'on veut le déplacer sur l'<b>écran</b> — et pourtant c'est
 * ce qui arrivait : la position d'un enfant est relative à son parent, si bien que donner
 * un élément à un conteneur posé en (40, 30) le décalait de quarante pixels à droite et
 * trente vers le bas, d'un coup, au moment du relâchement.
 *
 * <p>Un saut au relâchement est le pire moment pour en faire un. L'auteur vient de viser
 * une cible dans une colonne étroite ; s'il découvre que ce qu'il a visé a bougé ailleurs,
 * il ne sait plus si c'est son geste qui a raté ou l'outil qui a mal compris.
 *
 * <p>Décision pure, hors du widget : elle se vérifie sans fenêtre, ce qui est précisément
 * la moitié du geste 1.10 qu'un œil vérifie mal — on regarde si le calque a changé de
 * branche, on ne compte pas les pixels.
 */
public final class ScreenDesignerReparent {

    private ScreenDesignerReparent() {
    }

    /**
     * La taille de référence du calcul de conversion.
     *
     * <p>N'importe quelle taille conviendrait pour un parent en coordonnées fixes ; celle
     * de la zone garantie est choisie parce qu'un parent en <b>pourcentage</b> se résout
     * différemment selon la fenêtre, et que c'est la seule dimension dont le concepteur
     * garantisse quelque chose.
     */
    private static final int REFERENCE_WIDTH = 320;
    private static final int REFERENCE_HEIGHT = 180;

    /**
     * L'élément tel qu'il doit être écrit pour rejoindre {@code newParent} sans bouger.
     *
     * <p>La conversion passe par la place <b>réellement occupée</b>, pas par une
     * soustraction des origines : l'ancre décide du point du parent auquel les coordonnées
     * se rapportent, et une soustraction ne serait juste que pour le coin haut-gauche.
     *
     * <p>Un parent qui <b>range</b> ses enfants ne reçoit rien : une colonne place ce
     * qu'elle contient, et lui écrire un x serait un nombre qui n'agit sur rien.
     *
     * @param newParent le nom du nouveau parent, ou {@code null} pour la racine
     */
    public static ScreenElement adopted(Screen screen, ScreenElement element,
                                        @Nullable String newParent) {
        ScreenElement moved = element.withParent(newParent);
        if (arranges(screen, newParent)) {
            // Rangé par son parent : les coordonnées ne servent plus, et les laisser
            // traîner ferait réapparaître une vieille position le jour où on repasse le
            // conteneur en disposition libre.
            return moved.movedTo(0, 0);
        }
        Map<String, ScreenLayout.Rect> before =
                ScreenLayout.solve(screen, REFERENCE_WIDTH, REFERENCE_HEIGHT);
        ScreenLayout.Rect was = before.get(element.name());
        if (was == null) {
            return moved;   // jamais placé : rien à préserver
        }
        Map<String, ScreenLayout.Rect> after =
                ScreenLayout.solve(screen.with(moved), REFERENCE_WIDTH, REFERENCE_HEIGHT);
        ScreenLayout.Rect now = after.get(element.name());
        if (now == null) {
            return moved;
        }
        // L'écart entre où il finirait et où il était : c'est exactement ce qu'il faut
        // retrancher aux coordonnées écrites, quelle que soit l'ancre, quel que soit le
        // mode de taille. Mesurer le déplacement plutôt que le calculer, c'est laisser la
        // passe de disposition rester la seule à savoir comment un enfant se place.
        return moved.movedTo(moved.x() - (now.x() - was.x()),
                moved.y() - (now.y() - was.y()));
    }

    /** Ce parent place-t-il lui-même ses enfants ? */
    private static boolean arranges(Screen screen, @Nullable String parent) {
        if (parent == null) {
            return false;
        }
        ScreenElement container = screen.element(parent);
        return container != null && container.arranges();
    }
}
