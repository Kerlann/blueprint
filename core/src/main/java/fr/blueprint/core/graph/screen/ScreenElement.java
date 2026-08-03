package fr.blueprint.core.graph.screen;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Un élément d'écran (story 10.1).
 *
 * <p>Immuable, comme tout le modèle : les modifications passent par des
 * {@code EditOperation} réversibles, ce qui donne d'un coup l'annuler/rétablir de
 * l'éditeur et les patchs réseau.
 *
 * @param name    <b>l'identité</b> de l'élément. C'est par lui que le graphe le
 *                désigne (FR47) — un auteur écrit {@code bouton_acheter}, jamais un
 *                UUID. Unique dans son écran, stable au déplacement.
 * @param kind    le type ; décide de ce qui se dessine et de ce qui se clique
 * @param parent  le conteneur, ou {@code null} à la racine. Masquer un parent masque
 *                toute sa page — c'est ce qui rend un menu à onglets praticable.
 * @param anchor  le coin de référence dans le parent
 * @param x       décalage horizontal depuis l'ancre, en unités
 * @param y       décalage vertical depuis l'ancre, en unités
 * @param width   largeur, fixe ou relative au parent
 * @param height  hauteur, fixe ou relative au parent
 * @param text    libellé, littéral ou clé de traduction
 * @param texture texture d'un {@code IMAGE}, ou {@code null}
 * @param style   apparence par état, quand aucun style nommé n'est suivi
 * @param styleName nom d'un style de l'écran, ou vide pour le style en ligne
 * @param layout  comment ce conteneur range ses enfants ; ignoré sur un non-conteneur
 * @param visible visibilité initiale ; le graphe peut la changer (10.4)
 * @param enabled activation initiale d'un élément interactif
 */
public record ScreenElement(String name, ElementKind kind, @Nullable String parent,
                            Anchor anchor, double x, double y,
                            Extent width, Extent height,
                            ScreenText text, @Nullable Identifier texture,
                            ElementStyle style, String styleName, LayoutSpec layout,
                            boolean visible, boolean enabled) {

    /** Taille minimale d'un élément, en unités : en dessous, il ne se clique plus. */
    public static final double MIN_SIZE = 4;

    public ScreenElement {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("un élément doit avoir un nom");
        }
        if (kind == null) {
            throw new IllegalArgumentException("élément « " + name + " » sans type");
        }
        if (anchor == null) {
            anchor = Anchor.TOP_LEFT;
        }
        if (width == null || height == null) {
            throw new IllegalArgumentException("élément « " + name + " » sans taille");
        }
        if (text == null) {
            text = ScreenText.EMPTY;
        }
        if (style == null) {
            style = ElementStyle.DEFAULT;
        }
        if (styleName == null) {
            styleName = "";
        }
        if (layout == null) {
            layout = LayoutSpec.ABSOLUTE;
        }
    }

    /** Vrai si cet élément suit un style nommé de l'écran plutôt que le sien. */
    public boolean followsNamedStyle() {
        return !styleName.isEmpty();
    }

    /** Vrai si ce conteneur range ses enfants — un non-conteneur ne range jamais rien. */
    public boolean arranges() {
        return kind.container() && layout.arranges();
    }

    /** Un élément neuf, aux valeurs par défaut : ce que pose le concepteur. */
    public static ScreenElement of(String name, ElementKind kind, double x, double y,
                                   double width, double height) {
        return new ScreenElement(name, kind, null, Anchor.TOP_LEFT, x, y,
                Extent.of(width), Extent.of(height), ScreenText.EMPTY, null,
                ElementStyle.DEFAULT, "", LayoutSpec.ABSOLUTE, true, true);
    }

    public ScreenElement withParent(@Nullable String newParent) {
        return new ScreenElement(name, kind, newParent, anchor, x, y, width, height,
                text, texture, style, styleName, layout, visible, enabled);
    }

    public ScreenElement withAnchor(Anchor newAnchor) {
        return new ScreenElement(name, kind, parent, newAnchor, x, y, width, height,
                text, texture, style, styleName, layout, visible, enabled);
    }

    public ScreenElement movedTo(double newX, double newY) {
        return new ScreenElement(name, kind, parent, anchor, newX, newY, width, height,
                text, texture, style, styleName, layout, visible, enabled);
    }

    public ScreenElement resized(Extent newWidth, Extent newHeight) {
        return new ScreenElement(name, kind, parent, anchor, x, y, newWidth, newHeight,
                text, texture, style, styleName, layout, visible, enabled);
    }

    public ScreenElement renamed(String newName) {
        return new ScreenElement(newName, kind, parent, anchor, x, y, width, height,
                text, texture, style, styleName, layout, visible, enabled);
    }

    public ScreenElement styled(ElementStyle newStyle) {
        return new ScreenElement(name, kind, parent, anchor, x, y, width, height,
                text, texture, newStyle, styleName, layout, visible, enabled);
    }

    public ScreenElement withText(ScreenText newText) {
        return new ScreenElement(name, kind, parent, anchor, x, y, width, height,
                newText, texture, style, styleName, layout, visible, enabled);
    }

    public ScreenElement withTexture(@Nullable Identifier newTexture) {
        return new ScreenElement(name, kind, parent, anchor, x, y, width, height,
                text, newTexture, style, styleName, layout, visible, enabled);
    }

    /** Suit un style nommé de l'écran ; une chaîne vide repasse au style en ligne. */
    public ScreenElement withStyleName(String newStyleName) {
        return new ScreenElement(name, kind, parent, anchor, x, y, width, height,
                text, texture, style, newStyleName, layout, visible, enabled);
    }

    /** Change la disposition — n'a d'effet visible que sur un conteneur. */
    public ScreenElement withLayout(LayoutSpec newLayout) {
        return new ScreenElement(name, kind, parent, anchor, x, y, width, height,
                text, texture, style, styleName, newLayout, visible, enabled);
    }

    public ScreenElement withVisible(boolean newVisible) {
        return new ScreenElement(name, kind, parent, anchor, x, y, width, height,
                text, texture, style, styleName, layout, newVisible, enabled);
    }

    public ScreenElement withEnabled(boolean newEnabled) {
        return new ScreenElement(name, kind, parent, anchor, x, y, width, height,
                text, texture, style, styleName, layout, visible, newEnabled);
    }
}
