package fr.blueprint.client.editor;

import fr.blueprint.core.graph.Link;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Menu contextuel (story 5.13). C'est le geste central de l'éditeur d'Unreal : tout y
 * vit. Ici, le clic droit ouvrait la palette <b>partout</b>, y compris sur un nœud ou
 * un pin — le geste existait mais donnait toujours la même chose, et les actions
 * (dupliquer, casser les liens, promouvoir en variable) n'étaient accessibles qu'au
 * clavier, donc invisibles.
 *
 * <p>Pur : la cible et les entrées sont calculées à l'ouverture, l'exécution est
 * rendue au widget. Le canevas vide garde la palette — c'est aussi ce que fait Unreal.
 */
public final class ContextMenuState {

    /** Hauteur d'une ligne, et d'un séparateur. */
    public static final int ROW_HEIGHT = 11;
    public static final int SEPARATOR_HEIGHT = 4;

    public enum Action {
        /** Nœud. */
        DUPLICATE, DELETE_NODE, BREAK_NODE_LINKS, COMMENT_SELECTION, ALIGN_SELECTION,
        /** Pin. */
        BREAK_PIN_LINKS, RESET_LITERAL, PROMOTE_TO_VARIABLE,
        /** Lien. */
        DELETE_LINK
    }

    /**
     * Une entrée. {@code enabled} à faux la grise plutôt que de la masquer : voir
     * qu'une action existe mais ne s'applique pas ici en apprend plus que de ne rien
     * voir du tout (U2, comme les nœuds au-dessus du plafond de permission).
     */
    public record Item(Action action, String labelKey, @Nullable String shortcut,
                       boolean enabled, boolean separatorBefore) {

        public Item(Action action, String labelKey, @Nullable String shortcut, boolean enabled) {
            this(action, labelKey, shortcut, enabled, false);
        }
    }

    /** Ce que le menu vise. Le widget en a besoin pour exécuter l'action choisie. */
    public record Target(@Nullable UUID node, @Nullable String pin, @Nullable Link link) {
    }

    private boolean open;
    private double x;
    private double y;
    private List<Item> items = List.of();
    private Target target = new Target(null, null, null);
    private int hovered = -1;

    // ------------------------------------------------------------------ ouverture

    /**
     * Menu d'un nœud. {@code selectionSize} décide des actions de groupe : aligner
     * n'a pas de sens sur un seul nœud, et le dire vaut mieux que de le cacher.
     */
    public void openForNode(double sx, double sy, UUID node, boolean hasLinks, int selectionSize) {
        List<Item> out = new ArrayList<>();
        out.add(new Item(Action.DUPLICATE, "blueprint.editor.menu.duplicate", "Ctrl+D", true));
        out.add(new Item(Action.DELETE_NODE, "blueprint.editor.menu.delete", "Suppr", true));
        out.add(new Item(Action.BREAK_NODE_LINKS, "blueprint.editor.menu.break_links",
                null, hasLinks, true));
        out.add(new Item(Action.COMMENT_SELECTION, "blueprint.editor.menu.comment", "C", true, true));
        out.add(new Item(Action.ALIGN_SELECTION, "blueprint.editor.menu.align", "Q",
                selectionSize >= 2));
        show(sx, sy, out, new Target(node, null, null));
    }

    /**
     * Menu d'un pin. Un pin exec n'a ni valeur ni variable à promouvoir : les entrées
     * restent visibles mais grisées.
     */
    public void openForPin(double sx, double sy, UUID node, String pin,
                           boolean wired, boolean hasLiteral, boolean promotable) {
        List<Item> out = new ArrayList<>();
        out.add(new Item(Action.BREAK_PIN_LINKS, "blueprint.editor.menu.break_pin_links",
                "Alt+clic", wired));
        out.add(new Item(Action.RESET_LITERAL, "blueprint.editor.menu.reset_literal",
                null, hasLiteral));
        out.add(new Item(Action.PROMOTE_TO_VARIABLE, "blueprint.editor.menu.promote",
                null, promotable, true));
        show(sx, sy, out, new Target(node, pin, null));
    }

    public void openForLink(double sx, double sy, Link link) {
        show(sx, sy, List.of(new Item(Action.DELETE_LINK, "blueprint.editor.menu.delete_link",
                "Suppr", true)), new Target(null, null, link));
    }

    private void show(double sx, double sy, List<Item> entries, Target on) {
        open = true;
        x = sx;
        y = sy;
        items = List.copyOf(entries);
        target = on;
        hovered = -1;
    }

    public void close() {
        open = false;
        hovered = -1;
    }

    // -------------------------------------------------------------------- lecture

    public boolean isOpen() {
        return open;
    }

    public List<Item> items() {
        return items;
    }

    public Target target() {
        return target;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public int hovered() {
        return hovered;
    }

    // ---------------------------------------------------------------- géométrie

    /** Hauteur totale, séparateurs compris. */
    public int height() {
        int h = 2;
        for (Item item : items) {
            h += item.separatorBefore() ? SEPARATOR_HEIGHT : 0;
            h += ROW_HEIGHT;
        }
        return h + 2;
    }

    /**
     * L'entrée sous le point, ou −1. Les séparateurs comptent dans la hauteur : les
     * ignorer décalerait le clic d'une ligne à partir du premier d'entre eux.
     */
    public int itemAt(double sx, double sy, int width) {
        if (!open || sx < x || sx >= x + width) {
            return -1;
        }
        double top = y + 2;
        for (int i = 0; i < items.size(); i++) {
            top += items.get(i).separatorBefore() ? SEPARATOR_HEIGHT : 0;
            if (sy >= top && sy < top + ROW_HEIGHT) {
                return i;
            }
            top += ROW_HEIGHT;
        }
        return -1;
    }

    /** Ordonnée du haut d'une entrée, pour le rendu. Même arithmétique que le clic. */
    public int rowTop(int index) {
        double top = y + 2;
        for (int i = 0; i < items.size() && i < index; i++) {
            top += items.get(i).separatorBefore() ? SEPARATOR_HEIGHT : 0;
            top += ROW_HEIGHT;
        }
        top += index < items.size() && items.get(index).separatorBefore() ? SEPARATOR_HEIGHT : 0;
        return (int) top;
    }

    public void hover(double sx, double sy, int width) {
        hovered = itemAt(sx, sy, width);
    }

    /** L'action choisie, ou null (ligne grisée, séparateur, hors menu). */
    public @Nullable Action choose(double sx, double sy, int width) {
        int index = itemAt(sx, sy, width);
        if (index < 0 || !items.get(index).enabled()) {
            return null;
        }
        return items.get(index).action();
    }

    /**
     * Recale le menu pour qu'il tienne à l'écran ; à appeler une fois, juste après
     * l'ouverture. Il <b>déplace</b> le menu au lieu de rendre une position à part :
     * un rendu recalé et un clic resté sur la position brute, et l'on choisit une
     * ligne pour une autre — le genre de bug qu'on met une heure à croire.
     */
    public void clampToScreen(int width, int screenWidth, int screenHeight) {
        if (x + width > screenWidth) {
            x = Math.max(0, x - width);
        }
        int h = height();
        if (y + h > screenHeight) {
            y = Math.max(0, screenHeight - h);
        }
    }
}
