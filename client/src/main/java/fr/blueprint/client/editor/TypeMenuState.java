package fr.blueprint.client.editor;

import fr.blueprint.api.pin.PinType;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Le choix du type d'une variable.
 *
 * <p>Il se faisait par <b>cycle</b> : le bouton [T] passait au type suivant, sur cinq.
 * C'était tenable à cinq. Ça ne l'est plus — la liste des types proposés s'est élargie
 * aux vecteurs, positions de bloc et directions, et parcourir huit crans pour revenir au
 * précédent qu'on vient de dépasser n'est pas un choix, c'est une pénitence. Surtout : un
 * cycle ne montre jamais ce qui est disponible, donc personne ne découvrait qu'une
 * variable pouvait être un vecteur.
 *
 * <p>Pur, comme {@link ContextMenuState} : la géométrie et l'état vivent ici, le dessin
 * dans {@link TypeMenuPopup}, l'application dans
 * {@link VariablePanelState#retypeTo(String, PinType)}.
 */
public final class TypeMenuState {

    public static final int ROW_HEIGHT = 11;

    private boolean open;
    private int x;
    private int y;
    private String variable = "";
    private List<PinType> types = List.of();
    private @Nullable PinType current;
    private int hovered = -1;

    // ------------------------------------------------------------------ ouverture

    /** Ouvre le menu pour {@code variable}, ancré en haut à gauche sur (x, y). */
    public void open(String variable, @Nullable PinType current, List<PinType> types,
                     int x, int y) {
        this.open = true;
        this.variable = variable;
        this.current = current;
        this.types = List.copyOf(types);
        this.x = x;
        this.y = y;
        this.hovered = -1;
    }

    public void close() {
        open = false;
        hovered = -1;
    }

    public boolean isOpen() {
        return open;
    }

    public String variable() {
        return variable;
    }

    public List<PinType> types() {
        return types;
    }

    public @Nullable PinType current() {
        return current;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int hovered() {
        return hovered;
    }

    // ------------------------------------------------------------------ géométrie

    public int height() {
        return types.size() * ROW_HEIGHT + 2;
    }

    public int rowTop(int index) {
        return y + 1 + index * ROW_HEIGHT;
    }

    /**
     * Ramène le menu dans l'écran.
     *
     * <p>Une variable sélectionnée en bas du panneau ouvrirait sinon son menu sous le bord
     * inférieur : les derniers types seraient hors de vue, et ce sont justement les
     * nouveaux.
     */
    public void clampToScreen(int width, int screenW, int screenH) {
        x = Math.max(0, Math.min(x, screenW - width));
        y = Math.max(0, Math.min(y, screenH - height()));
    }

    /** Indice de la ligne sous la souris, ou −1. */
    public int rowAt(double mx, double my, int width) {
        if (!open || mx < x || mx >= x + width || my < y + 1) {
            return -1;
        }
        int index = (int) ((my - y - 1) / ROW_HEIGHT);
        return index >= 0 && index < types.size() ? index : -1;
    }

    public void hover(double mx, double my, int width) {
        hovered = rowAt(mx, my, width);
    }

    /**
     * Le type sous la souris, ou {@code null} si le clic est tombé à côté.
     *
     * <p>Ne ferme pas : c'est l'appelant qui décide, parce qu'un retypage refusé faute de
     * confirmation doit laisser le menu ouvert — sinon il faudrait le rouvrir pour
     * confirmer, et l'avertissement aurait disparu entre-temps.
     */
    public @Nullable PinType choose(double mx, double my, int width) {
        int index = rowAt(mx, my, width);
        return index < 0 ? null : types.get(index);
    }
}
