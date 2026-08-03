package fr.blueprint.client.browser;

import fr.blueprint.client.net.BlueprintNet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Le navigateur de blueprints — ce que F6 ouvre.
 *
 * <p>Avant, F6 ouvrait <b>une démo</b>, et travailler sur un vrai graphe demandait de
 * taper {@code /blueprint-edit <identifiant complet>} de mémoire. Le navigateur montre
 * ce qui existe, sous forme de dossiers, et donne les trois gestes qui manquaient :
 * ouvrir, créer, importer.
 *
 * <p>Tout l'état vit dans {@link BrowserState}, qui est pur et testé. Ce qui reste ici
 * est le dessin et la frappe.
 */
public final class BlueprintBrowserScreen extends Screen {

    private static final int ROW = 12;
    private static final int MARGIN = 20;
    private static final int PANEL = 0xE0141519;
    private static final int BORDER = 0xFF3A3D42;
    private static final int TEXT = 0xFFD5D8DC;
    private static final int DIM = 0xFF8A909A;
    private static final int SELECTED = 0xFF7AA2F7;
    private static final int FOLDER_COLOR = 0xFFE0AF68;
    private static final int INVALID = 0xFFF7768E;

    private final BrowserState state = new BrowserState();

    /** Ce qu'on est en train de taper : le filtre, ou le nom d'un blueprint à créer. */
    private enum Field { NONE, FILTER, CREATE }

    private Field editing = Field.NONE;
    private String buffer = "";
    private @Nullable String message;
    private int scroll;

    public BlueprintBrowserScreen() {
        super(Component.translatable("blueprint.browser.title"));
    }

    @Override
    protected void init() {
        refresh();
    }

    /** Redemande au serveur ce qu'il a — la liste peut avoir changé pendant qu'on jouait. */
    private void refresh() {
        BlueprintNet.requestList(false);
        BlueprintNet.requestFiles();
    }

    private int listTop() {
        return MARGIN + ROW * 3;
    }

    private int visibleRows() {
        return Math.max(1, (height - listTop() - MARGIN - ROW * 2) / ROW);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        state.setBlueprints(BlueprintNet.known());
        state.setFiles(BlueprintNet.files());

        int left = MARGIN;
        int right = width - MARGIN;
        g.fill(left - 4, MARGIN - 4, right + 4, height - MARGIN + 4, PANEL);
        g.fill(left - 4, MARGIN - 4, right + 4, MARGIN - 3, BORDER);
        g.fill(left - 4, height - MARGIN + 3, right + 4, height - MARGIN + 4, BORDER);

        g.drawString(font, I18n.get("blueprint.browser.title"), left, MARGIN, TEXT, false);
        // La ligne d'état dit d'emblée si l'on peut écrire : découvrir qu'on est en
        // lecture seule au moment d'enregistrer serait une perte de travail.
        g.drawString(font, BlueprintNet.writable()
                        ? I18n.get("blueprint.browser.writable")
                        : I18n.get("blueprint.browser.readonly"),
                right - font.width(BlueprintNet.writable()
                        ? I18n.get("blueprint.browser.writable")
                        : I18n.get("blueprint.browser.readonly")),
                MARGIN, BlueprintNet.writable() ? DIM : INVALID, false);

        String filterLabel = editing == Field.FILTER ? state.filter() + "_" : state.filter();
        g.drawString(font, I18n.get("blueprint.browser.filter",
                        filterLabel.isEmpty() ? "…" : filterLabel),
                left, MARGIN + ROW, editing == Field.FILTER ? SELECTED : DIM, false);

        List<BrowserState.Row> rows = state.rows();
        scroll = Math.clamp(scroll, 0, Math.max(0, rows.size() - visibleRows()));
        int y = listTop();
        for (int i = scroll; i < rows.size() && i < scroll + visibleRows(); i++) {
            BrowserState.Row row = rows.get(i);
            boolean selected = row.path().equals(state.selected());
            String prefix = switch (row.kind()) {
                case FOLDER -> state.isCollapsed(row.path()) ? "▸ " : "▾ ";
                case BLUEPRINT -> "• ";
                case FILE -> "↓ ";
            };
            int color = selected ? SELECTED
                    : row.kind() == BrowserState.Kind.FOLDER ? FOLDER_COLOR : TEXT;
            g.drawString(font, prefix + row.label(), left + row.depth() * 10, y, color, false);
            y += ROW;
        }
        if (rows.isEmpty()) {
            g.drawString(font, I18n.get("blueprint.browser.empty"), left, listTop(), DIM, false);
        }

        int actionsY = height - MARGIN - ROW;
        String create = editing == Field.CREATE
                ? I18n.get("blueprint.browser.creating", buffer + "_")
                : I18n.get("blueprint.browser.create");
        g.drawString(font, create, left, actionsY,
                editing == Field.CREATE ? SELECTED : DIM, false);
        g.drawString(font, I18n.get("blueprint.browser.hint"), left, actionsY - ROW, DIM, false);
        if (message != null) {
            g.drawString(font, message, left, actionsY + ROW - 2, INVALID, false);
        }
    }

    // ------------------------------------------------------------------- souris

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        double my = event.y();
        if (my >= MARGIN + ROW && my < MARGIN + ROW * 2) {
            editing = Field.FILTER;
            buffer = state.filter();
            return true;
        }
        int actionsY = height - MARGIN - ROW;
        if (my >= actionsY && my < actionsY + ROW) {
            editing = Field.CREATE;
            buffer = "";
            return true;
        }
        if (my >= listTop()) {
            int index = scroll + (int) ((my - listTop()) / ROW);
            List<BrowserState.Row> rows = state.rows();
            if (index >= 0 && index < rows.size()) {
                editing = Field.NONE;
                // Simple clic : sélectionner, ou replier un dossier. Double-clic :
                // ouvrir — et « Entrée » fait la même chose, parce qu'un double-clic
                // n'est pas toujours facile à réussir.
                boolean openable = state.click(rows.get(index));
                if (openable && doubled) {
                    open(rows.get(index));
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        scroll -= (int) Math.signum(vAmount);
        return true;
    }

    // ------------------------------------------------------------------ clavier

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (editing != Field.NONE) {
            return editKey(event);
        }
        return switch (event.key()) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                BrowserState.Row row = state.selectedRow();
                if (row != null) {
                    open(row);
                }
                yield true;
            }
            case GLFW.GLFW_KEY_F5 -> {
                refresh();
                yield true;
            }
            case GLFW.GLFW_KEY_N -> {
                if (event.hasControlDown()) {
                    editing = Field.CREATE;
                    buffer = "";
                }
                yield true;
            }
            case GLFW.GLFW_KEY_F -> {
                if (event.hasControlDown()) {
                    editing = Field.FILTER;
                    buffer = state.filter();
                }
                yield true;
            }
            case GLFW.GLFW_KEY_TAB -> {
                state.toggleFiles();
                yield true;
            }
            default -> super.keyPressed(event);
        };
    }

    private boolean editKey(KeyEvent event) {
        switch (event.key()) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                editing = Field.NONE;
                buffer = "";
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!buffer.isEmpty()) {
                    buffer = buffer.substring(0, buffer.length() - 1);
                    if (editing == Field.FILTER) {
                        state.setFilter(buffer);
                    }
                }
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (editing == Field.CREATE) {
                    create();
                }
                editing = Field.NONE;
            }
            default -> {
                return true;   // la frappe est captée : pas de raccourci de jeu derrière
            }
        }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (editing == Field.NONE) {
            return false;
        }
        buffer += (char) event.codepoint();
        if (editing == Field.FILTER) {
            state.setFilter(buffer);
        }
        return true;
    }

    // ------------------------------------------------------------------ actions

    private void open(BrowserState.Row row) {
        message = null;
        if (row.blueprint() != null) {
            BlueprintNet.requestOpen(row.blueprint());
        } else if (row.file() != null) {
            BlueprintNet.requestImport(row.file());
        }
    }

    private void create() {
        Identifier id = BrowserState.parseId(buffer);
        if (id == null) {
            message = I18n.get("blueprint.browser.bad_id", buffer);
            return;
        }
        message = null;
        BlueprintNet.requestCreate(id);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
