package fr.blueprint.client.editor;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinKind;
import fr.blueprint.api.pin.PinShape;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.registry.NodeDescriptor;
import net.minecraft.core.Direction;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Rendu d'un nœud depuis son descripteur seul (principe P1 : aucun accès au
 * {@code NodeType} ni au mod fournisseur). Tout se dessine sous la pose 2D translatée
 * puis mise à l'échelle : les coordonnées locales sont en unités monde entières.
 *
 * <p>La forme du pin double sa couleur (NFR11) : ▶ exec, ● data, ◆ objet, ▦ liste.
 */
public final class NodeWidget {

    // Couleurs de la spec UX §12 — le thème JSON rechargeable est la story 5.7.
    private static final int NODE_BACKGROUND = 0xFF2B2D31;
    private static final int NODE_BORDER = 0xFF3A3D42;
    private static final int SELECTED_BORDER = 0xFF7AA2F7;
    private static final int GHOST_COLOR = 0xFFC74A5B;
    private static final int TITLE_COLOR = 0xFFE6E6E6;
    private static final int PIN_LABEL_COLOR = 0xFFABB2BF;

    /** Alpha appliqué à la couleur de catégorie sur l'en-tête. */
    private static final int HEADER_ALPHA = 0x59000000;

    /** Sous ce zoom, plus de titre du tout ; en dessous de 0,5×, boîte + titre seul (UX §3). */
    private static final double TITLE_FADE_ZOOM = 0.35;
    private static final double DETAIL_FADE_ZOOM = 0.5;

    /** Pin à atténuer pendant un tracé de lien (cible incompatible, UX §5). */
    public interface PinDimmer {
        boolean dimmed(String pin, boolean output);
    }

    /** Valeur littérale à afficher pour un pin d'entrée — null = masquée (câblé). */
    public interface LiteralProvider {
        @Nullable LiteralValue literalOf(String pin);
    }

    private static final int LITERAL_COLOR = 0xFF9AA0A8;
    private static final int LITERAL_EDIT_BG = 0xFF141922;
    private static final int LITERAL_EDIT_BORDER = 0xFF7AA2F7;
    private static final int LITERAL_INVALID_BORDER = 0xFFF7768E;
    private static final int BOOL_ON = 0xFF9ECE6A;

    private NodeWidget() {
    }

    public static void render(GuiGraphics g, Font font, NodeGeometry.Box box,
                              @Nullable NodeDescriptor desc, boolean selected,
                              double zoom, double screenX, double screenY,
                              @Nullable PinDimmer dimmer, @Nullable LiteralProvider literals,
                              @Nullable LiteralEditState edit, int outlineColor) {
        int w = (int) Math.round(box.width());
        int h = (int) Math.round(box.height());
        g.pose().pushMatrix();
        g.pose().translate((float) screenX, (float) screenY);
        g.pose().scale((float) zoom, (float) zoom);

        boolean ghost = desc == null;
        if (selected) {
            g.fill(0, 0, w, h, SELECTED_BORDER);
            g.fill(1, 1, w - 1, h - 1, NODE_BACKGROUND);
        } else if (outlineColor != 0) {
            // Nœud fautif : liseré de la couleur de la sévérité (UX §8).
            g.fill(0, 0, w, h, outlineColor);
            g.fill(1, 1, w - 1, h - 1, NODE_BACKGROUND);
        } else if (ghost) {
            g.fill(0, 0, w, h, NODE_BACKGROUND);
            dashedBorder(g, w, h, GHOST_COLOR);
        } else {
            g.fill(0, 0, w, h, NODE_BORDER);
            g.fill(1, 1, w - 1, h - 1, NODE_BACKGROUND);
        }

        if (ghost) {
            renderGhostContent(g, font, box.node().typeId(), w, zoom);
        } else {
            renderContent(g, font, box, desc, w, zoom, dimmer, literals, edit);
        }
        g.pose().popMatrix();
    }

    private static void renderContent(GuiGraphics g, Font font, NodeGeometry.Box box,
                                      NodeDescriptor desc, int w, double zoom,
                                      @Nullable PinDimmer dimmer, @Nullable LiteralProvider literals,
                                      @Nullable LiteralEditState edit) {
        g.fill(1, 1, w - 1, (int) NodeGeometry.TITLE_HEIGHT,
                (categoryColor(desc.category()) & 0x00FFFFFF) | HEADER_ALPHA);
        if (zoom < TITLE_FADE_ZOOM) {
            return;
        }
        String title = font.plainSubstrByWidth(I18n.get(desc.titleKey()), w - 12);
        g.drawString(font, title, 6, 5, TITLE_COLOR, false);

        if (zoom < DETAIL_FADE_ZOOM) {
            return;
        }
        for (int i = 0; i < desc.inputs().size(); i++) {
            NodeDescriptor.PinDescriptor pin = desc.inputs().get(i);
            int cy = rowCenterY(i);
            boolean dim = dimmer != null && dimmer.dimmed(pin.name(), false);
            drawPin(g, pin.type().shape(), dim(pin.type().color(), dim),
                    (int) NodeGeometry.PIN_INSET, cy);
            String label = font.plainSubstrByWidth(pin.name(), w / 2 - 14);
            g.drawString(font, label, (int) NodeGeometry.PIN_INSET + 7, cy - 4,
                    dim(PIN_LABEL_COLOR, dim), false);
            if (pin.kind() == PinKind.DATA && literals != null) {
                renderLiteral(g, font, box, w, i, pin, literals, edit);
            }
        }
        for (int i = 0; i < desc.outputs().size(); i++) {
            NodeDescriptor.PinDescriptor pin = desc.outputs().get(i);
            int cy = rowCenterY(i);
            int cx = w - (int) NodeGeometry.PIN_INSET;
            boolean dim = dimmer != null && dimmer.dimmed(pin.name(), true);
            drawPin(g, pin.type().shape(), dim(pin.type().color(), dim), cx, cy);
            String label = font.plainSubstrByWidth(pin.name(), w / 2 - 14);
            g.drawString(font, label, cx - 7 - font.width(label), cy - 4,
                    dim(PIN_LABEL_COLOR, dim), false);
        }
    }

    /** Cible incompatible pendant un tracé : ~30 % d'opacité (UX §5). */
    private static int dim(int color, boolean dim) {
        return dim ? (color & 0x00FFFFFF) | 0x4D000000 : color;
    }

    /**
     * Valeur littérale d'un pin d'entrée data (5.2b) : texte, case bool, ou champ
     * d'édition actif. Coordonnées locales (pose déjà à l'échelle).
     */
    private static void renderLiteral(GuiGraphics g, Font font, NodeGeometry.Box box,
                                      int w, int row, NodeDescriptor.PinDescriptor pin,
                                      LiteralProvider literals, @Nullable LiteralEditState edit) {
        int x1 = (int) (w * NodeGeometry.LITERAL_LEFT);
        int x2 = (int) (w * NodeGeometry.LITERAL_RIGHT);
        int top = (int) (NodeGeometry.TITLE_HEIGHT + row * NodeGeometry.ROW_HEIGHT);
        int cy = top + (int) NodeGeometry.ROW_HEIGHT / 2;

        boolean editing = edit != null && edit.isOpen()
                && box.node().uuid().equals(edit.node()) && pin.name().equals(edit.pin());
        if (editing) {
            g.fill(x1 - 1, top, x2 + 1, top + (int) NodeGeometry.ROW_HEIGHT, LITERAL_EDIT_BG);
            int border = edit.isValid() ? LITERAL_EDIT_BORDER : LITERAL_INVALID_BORDER;
            g.fill(x1 - 1, top, x2 + 1, top + 1, border);
            g.fill(x1 - 1, top + (int) NodeGeometry.ROW_HEIGHT - 1, x2 + 1,
                    top + (int) NodeGeometry.ROW_HEIGHT, border);
            String shown = edit.mode() == LiteralEditState.Mode.ENUM
                    ? "‹ " + edit.options().get(edit.optionIndex()).getName() + " ›"
                    : edit.text() + "_";
            // Garder la fin visible : c'est là qu'on tape.
            while (font.width(shown) > x2 - x1 - 4 && shown.length() > 1) {
                shown = shown.substring(1);
            }
            g.drawString(font, shown, x1 + 2, cy - 4, TITLE_COLOR, false);
            return;
        }

        LiteralValue value = literals.literalOf(pin.name());
        if (value == null) {
            return; // câblé, ou rien à montrer
        }
        if (pin.type() == PinTypes.BOOL) {
            boolean on = value.value() instanceof Boolean b && b;
            g.fill(x2 - 8, cy - 4, x2, cy + 4, NODE_BORDER);
            g.fill(x2 - 7, cy - 3, x2 - 1, cy + 3, on ? BOOL_ON : NODE_BACKGROUND);
            return;
        }
        String text = LiteralEditState.display(pin.type(), value);
        if (text.isEmpty()) {
            return;
        }
        text = font.plainSubstrByWidth(text, x2 - x1 - 2);
        g.drawString(font, text, x2 - font.width(text), cy - 4, LITERAL_COLOR, false);
    }

    private static void renderGhostContent(GuiGraphics g, Font font, Identifier typeId,
                                           int w, double zoom) {
        if (zoom < TITLE_FADE_ZOOM) {
            return;
        }
        String title = font.plainSubstrByWidth(typeId.toString(), w - 12);
        g.drawString(font, title, 6, 5, GHOST_COLOR, false);
        if (zoom < DETAIL_FADE_ZOOM) {
            return;
        }
        String message = font.plainSubstrByWidth(
                I18n.get("blueprint.editor.ghost", typeId.getNamespace()), w - 12);
        g.drawString(font, message, 6, (int) NodeGeometry.TITLE_HEIGHT + 4,
                PIN_LABEL_COLOR, false);
    }

    private static int rowCenterY(int row) {
        return (int) (NodeGeometry.TITLE_HEIGHT + row * NodeGeometry.ROW_HEIGHT
                + NodeGeometry.ROW_HEIGHT / 2);
    }

    // ------------------------------------------------------------------- formes

    private static void drawPin(GuiGraphics g, PinShape shape, int color, int cx, int cy) {
        switch (shape) {
            case EXEC -> {
                for (int dx = 0; dx <= 4; dx++) {
                    int hh = Math.max(0, 4 - dx);
                    g.fill(cx - 2 + dx, cy - hh, cx - 1 + dx, cy + hh + 1, color);
                }
            }
            case CIRCLE -> {
                int[] hw = {1, 2, 3, 3, 3, 2, 1};
                for (int dy = -3; dy <= 3; dy++) {
                    int h = hw[dy + 3];
                    g.fill(cx - h, cy + dy, cx + h + 1, cy + dy + 1, color);
                }
            }
            case DIAMOND -> {
                for (int dy = -3; dy <= 3; dy++) {
                    int h = 3 - Math.abs(dy);
                    g.fill(cx - h, cy + dy, cx + h + 1, cy + dy + 1, color);
                }
            }
            case ARRAY -> g.fill(cx - 3, cy - 3, cx + 4, cy + 4, color);
            case MAP -> {
                g.fill(cx - 3, cy - 3, cx + 4, cy + 4, color);
                g.fill(cx - 1, cy - 1, cx + 2, cy + 2, NODE_BACKGROUND);
            }
        }
    }

    private static void dashedBorder(GuiGraphics g, int w, int h, int color) {
        for (int x = 0; x < w; x += 7) {
            int x2 = Math.min(x + 4, w);
            g.fill(x, 0, x2, 1, color);
            g.fill(x, h - 1, x2, h, color);
        }
        for (int y = 0; y < h; y += 7) {
            int y2 = Math.min(y + 4, h);
            g.fill(0, y, 1, y2, color);
            g.fill(w - 1, y, w, y2, color);
        }
    }

    /** Teintes d'en-tête par catégorie (UX §12, complétées pour les catégories standard). */
    static int categoryColor(String category) {
        return switch (category) {
            case "flow" -> 0xFF8A8F98;
            case "math" -> 0xFF7DCFFF;
            case "logic" -> 0xFF56B6C2;
            case "string", "text" -> 0xFFE5C07B;
            case "list", "struct" -> 0xFFABB2BF;
            case "world" -> 0xFF9ECE6A;
            case "entity" -> 0xFFE0AF68;
            case "player" -> 0xFFBB9AF7;
            case "item" -> 0xFFD19A66;
            case "debug" -> 0xFFF7768E;
            case "event" -> 0xFFE06C75;
            default -> 0xFF8A8F98;
        };
    }
}
