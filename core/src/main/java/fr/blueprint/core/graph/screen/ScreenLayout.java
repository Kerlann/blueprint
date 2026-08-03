package fr.blueprint.core.graph.screen;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * La géométrie d'un élément, une fois la fenêtre connue (story 10.1).
 *
 * <p><b>La seule résolution du produit.</b> {@link Extent#resolve} donne une longueur ;
 * il manquait ce qui la rend utilisable : la place du parent et l'ancre. Sans elle,
 * chaque appelant refaisait le calcul en supposant l'élément à la racine et ancré en
 * haut-gauche — vrai pour un élément sur deux, faux pour tous les autres. Le concepteur
 * (10.2), le rendu (10.3) et le contrôle de placement partagent donc cette classe, comme
 * la géométrie du clic sur un fil est partagée avec son tracé depuis la story 5.12.
 *
 * <p>Résistante aux cycles de parenté : le validateur les refuse, mais une sauvegarde
 * réparée peut en contenir, et un rendu qui boucle vaut pire qu'un rendu approximatif.
 */
public final class ScreenLayout {

    private ScreenLayout() {
    }

    /** Le rectangle d'un élément dans la fenêtre, en unités d'interface. */
    public record Rect(double x, double y, double width, double height) {

        public double right() {
            return x + width;
        }

        public double bottom() {
            return y + height;
        }

        public boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
    }

    /**
     * Résout le rectangle de {@code element} dans {@code screen}, pour une fenêtre de
     * {@code viewportWidth} × {@code viewportHeight} unités.
     *
     * <p>L'élément n'a pas besoin d'appartenir déjà à l'écran : c'est ce qui permet de
     * contrôler le placement d'un élément <i>avant</i> de le poser. Seuls ses ancêtres
     * sont cherchés dans l'écran.
     */
    public static Rect resolve(Screen screen, ScreenElement element,
                               double viewportWidth, double viewportHeight) {
        // Remonter d'abord, redescendre ensuite : le rectangle d'un enfant se calcule
        // dans celui de son parent, qui n'est connu qu'une fois la racine atteinte.
        Deque<ScreenElement> chain = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        ScreenElement cursor = element;
        while (cursor != null && seen.add(cursor.name())) {
            chain.push(cursor);
            cursor = cursor.parent() == null ? null : screen.element(cursor.parent());
        }

        Rect rect = new Rect(0, 0, viewportWidth, viewportHeight);
        for (ScreenElement step : chain) {
            rect = within(rect, step);
        }
        return rect;
    }

    /** Le rectangle du parent de {@code element}, ou la fenêtre s'il est à la racine. */
    public static Rect parentRect(Screen screen, ScreenElement element,
                                  double viewportWidth, double viewportHeight) {
        ScreenElement parent = element.parent() == null ? null : screen.element(element.parent());
        return parent == null
                ? new Rect(0, 0, viewportWidth, viewportHeight)
                : resolve(screen, parent, viewportWidth, viewportHeight);
    }

    /**
     * L'<b>inverse</b> de {@link #within} : l'élément à écrire pour qu'il occupe
     * {@code target} dans ce parent. C'est ce que produit un glisser ou une poignée de
     * redimensionnement — la souris donne un rectangle, le modèle veut un décalage
     * depuis l'ancre.
     *
     * <p>Le couple aller/retour vit ici, dans le même fichier : séparés, l'un des deux
     * finirait par oublier l'ancre, et l'élément sauterait au relâchement de la souris.
     */
    public static ScreenElement placedIn(Rect parent, ScreenElement element, Rect target) {
        Extent width = fit(element.width(), target.width(), parent.width());
        Extent height = fit(element.height(), target.height(), parent.height());
        // Largeur EFFECTIVE après bornes : c'est elle qui décale le rectangle, pas
        // celle qu'on visait. Utiliser la cible ferait dériver un élément borné à
        // chaque glisser.
        double effectiveW = width.resolve(parent.width());
        double effectiveH = height.resolve(parent.height());
        double x = target.x() - parent.x() - parent.width() * element.anchor().fractionX()
                + effectiveW * element.anchor().fractionX();
        double y = target.y() - parent.y() - parent.height() * element.anchor().fractionY()
                + effectiveH * element.anchor().fractionY();
        return element.movedTo(x, y).resized(width, height);
    }

    /**
     * Une longueur qui vaut {@code resolved} en gardant la <b>nature</b> de
     * {@code template}. Une taille exprimée en pourcentage le reste : la convertir en
     * unités fixes au premier redimensionnement détruirait sans le dire l'adaptation
     * que l'auteur avait choisie, et son menu cesserait de suivre la fenêtre.
     */
    public static Extent fit(Extent template, double resolved, double parentSize) {
        if (!template.relative()) {
            return Extent.of(resolved);
        }
        if (parentSize <= 0) {
            return template;
        }
        return Extent.percent(resolved / parentSize, template.min(), template.max());
    }

    /** Le rectangle de {@code element} dans celui de son parent. */
    public static Rect within(Rect parent, ScreenElement element) {
        double width = element.width().resolve(parent.width());
        double height = element.height().resolve(parent.height());
        // L'ancre sert deux fois : elle place le point de référence dans le parent, et
        // elle dit quel point de l'élément vient s'y poser. Un élément centré doit
        // reculer d'une demi-largeur, sinon « centré » signifierait « à droite du
        // centre » — l'erreur classique, et invisible tant qu'on ne teste qu'à gauche.
        double anchorX = parent.x() + parent.width() * element.anchor().fractionX();
        double anchorY = parent.y() + parent.height() * element.anchor().fractionY();
        return new Rect(anchorX - width * element.anchor().fractionX() + element.x(),
                anchorY - height * element.anchor().fractionY() + element.y(),
                width, height);
    }
}
