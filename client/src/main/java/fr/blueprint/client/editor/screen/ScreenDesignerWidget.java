package fr.blueprint.client.editor.screen;

import fr.blueprint.client.editor.EditorSession;
import fr.blueprint.client.editor.history.UndoStack;
import fr.blueprint.client.screen.ScreenPainter;
import fr.blueprint.core.graph.Diagnostic;
import fr.blueprint.core.graph.NodeTypeLookup;
import fr.blueprint.core.graph.ScreenOps;
import fr.blueprint.core.graph.screen.ElementKind;
import fr.blueprint.core.graph.screen.Screen;
import fr.blueprint.core.graph.screen.ScreenElement;
import fr.blueprint.core.graph.screen.ScreenLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Le concepteur d'écrans (story 10.2) : la palette à gauche, la surface de conception au
 * centre, les propriétés à droite.
 *
 * <p>Le widget ne décide rien. Il convertit pixels ↔ unités par {@link DesignSurface},
 * délègue tous les gestes à {@link ScreenCanvasController} et toute la peinture à
 * {@link ScreenPainter} — celui-là même que le rendu en jeu utilisera (10.3). Ce qui
 * reste ici est ce qui ne se teste pas sans client : le chrome.
 */
public final class ScreenDesignerWidget {

    public static final int PALETTE_WIDTH = 76;
    public static final int PROPERTIES_WIDTH = 128;
    private static final int ROW = 12;

    private static final int PANEL_BACKGROUND = 0xF01A1B1E;
    private static final int PANEL_BORDER = 0xFF3A3D42;
    private static final int TEXT = 0xFFD5D8DC;
    private static final int DIM_TEXT = 0xFF8A909A;
    private static final int SELECTED = 0xFF7AA2F7;
    private static final int INVALID = 0xFFF7768E;
    private static final int SURFACE_BACKGROUND = 0xFF101114;
    private static final int SAFE_BORDER = 0xFF4A4F58;
    private static final int GUIDE = 0xFFE0AF68;
    private static final int OVERFLOW = 0xFFE0AF68;
    private static final int HANDLE = 0xFFE6E6E6;
    private static final int RUBBER_FILL = 0x337AA2F7;

    private final EditorSession session;
    private final NodeTypeLookup lookup;
    private final ScreenCanvasController controller;
    private final ElementPropertiesState properties = new ElementPropertiesState();

    private int top;
    private int width;
    private int height;
    private DesignSurface surface = new DesignSurface(0, 0, 1, Screen.SAFE_WIDTH, Screen.SAFE_HEIGHT);
    private @Nullable String message;

    public ScreenDesignerWidget(EditorSession session, NodeTypeLookup lookup, UndoStack history) {
        this.session = session;
        this.lookup = lookup;
        String first = session.blueprint().screens().keySet().stream().findFirst().orElse("");
        this.controller = new ScreenCanvasController(session.blueprint(), lookup, history, first);
    }

    public ScreenCanvasController controller() {
        return controller;
    }

    public void setBounds(int top, int width, int height) {
        this.top = top;
        this.width = width;
        this.height = height;
        this.surface = DesignSurface.fit(PALETTE_WIDTH, top + ROW,
                Math.max(1, width - PALETTE_WIDTH - PROPERTIES_WIDTH),
                Math.max(1, height - top - ROW * 3),
                (int) controller.viewportWidth(), (int) controller.viewportHeight());
    }

    // ------------------------------------------------------------------- rendu

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        playerViewportWidth = g.guiWidth();
        playerViewportHeight = g.guiHeight();
        Screen screen = controller.screen();
        properties.select(screen == null || controller.selection().size() != 1 ? null
                : screen.element(controller.selection().ids().iterator().next()));

        g.fill(0, top, width, height, SURFACE_BACKGROUND);
        renderSurface(g, font, screen);
        renderPalette(g, font);
        renderProperties(g, font);
        renderViewportBar(g, font);
        if (message != null) {
            g.drawString(font, message, PALETTE_WIDTH + 4, height - ROW, INVALID, false);
        }
    }

    private void renderSurface(GuiGraphics g, Font font, @Nullable Screen screen) {
        // La marge est plus sombre que la zone garantie : on voit d'un coup d'œil ce qui
        // déborde, sans avoir à lire le cadre.
        g.fill(surface.outerLeft(), surface.outerTop(), surface.outerRight(),
                surface.outerBottom(), 0xFF16171A);
        g.fill(surface.left(), surface.top(), surface.right(), surface.bottom(), 0xFF202227);
        // La bordure marque le CANEVAS — la fenêtre qu'on simule. Ce qui ne tiendrait
        // pas dans les 320×180 garantis est signalé autrement, élément par élément :
        // la garantie dépend de l'ancre, pas d'un rectangle qu'on pourrait dessiner.
        g.fill(surface.left() - 1, surface.top() - 1, surface.right() + 1, surface.top(),
                SAFE_BORDER);
        g.fill(surface.left() - 1, surface.bottom(), surface.right() + 1,
                surface.bottom() + 1, SAFE_BORDER);
        g.fill(surface.left() - 1, surface.top(), surface.left(), surface.bottom(), SAFE_BORDER);
        g.fill(surface.right(), surface.top(), surface.right() + 1, surface.bottom(), SAFE_BORDER);

        if (screen == null) {
            g.drawString(font, I18n.get("blueprint.designer.no_screen"),
                    surface.left() + 8, surface.top() + 8, DIM_TEXT, false);
            return;
        }

        g.enableScissor(surface.outerLeft(), surface.outerTop(),
                surface.outerRight(), surface.outerBottom());
        // forceVisible : en conception, un élément masqué doit rester manipulable —
        // sinon le rendre invisible reviendrait à le perdre.
        ScreenPainter.paint(g, font, screen, surface.left(), surface.top(), surface.scale(),
                Screen.SAFE_WIDTH, Screen.SAFE_HEIGHT, new ScreenPainter.Visuals() {
                    @Override
                    public boolean forceVisible(String element) {
                        return true;
                    }
                });
        renderOverflow(g, screen);
        renderSelection(g, screen);
        renderGuides(g);
        if (controller.gesture() == ScreenCanvasController.Gesture.RUBBER) {
            ScreenLayout.Rect band = controller.rubberBand();
            g.fill(surface.toScreenX(band.x()), surface.toScreenY(band.y()),
                    surface.toScreenX(band.right()), surface.toScreenY(band.bottom()),
                    RUBBER_FILL);
        }
        g.disableScissor();
    }

    /**
     * Cerne d'orange ce qui sort des 320×180 garantis (AC3b).
     *
     * <p>Le validateur produit bien l'avertissement, mais il ne s'affiche que dans le
     * panneau de diagnostics de l'onglet Graphe : l'auteur qui dessine un menu ne le
     * verrait jamais. Or c'est ici, au moment du geste, que l'information sert — après
     * coup, elle arrive sous forme de rapport de bug d'un joueur en <i>GUI scale</i> 4.
     */
    private void renderOverflow(GuiGraphics g, Screen screen) {
        // La zone garantie se mesure toujours à 320×180, quelle que soit la taille
        // simulée du canevas : c'est la fenêtre du joueur le moins bien loti.
        var placed = ScreenLayout.solve(screen, Screen.SAFE_WIDTH, Screen.SAFE_HEIGHT);
        for (ScreenElement element : screen.elements().values()) {
            ScreenLayout.Rect rect = placed.get(element.name());
            if (rect == null || !fr.blueprint.core.graph.ScreenRules.outsideSafeArea(rect)) {
                continue;
            }
            int left = surface.toScreenX(rect.x());
            int topPx = surface.toScreenY(rect.y());
            int right = surface.toScreenX(rect.right());
            int bottom = surface.toScreenY(rect.bottom());
            g.fill(left, topPx, right, topPx + 1, OVERFLOW);
            g.fill(left, bottom - 1, right, bottom, OVERFLOW);
            g.fill(left, topPx, left + 1, bottom, OVERFLOW);
            g.fill(right - 1, topPx, right, bottom, OVERFLOW);
        }
    }

    private void renderSelection(GuiGraphics g, Screen screen) {
        // Une seule passe pour toute la sélection : rectOf en résout une par élément, et
        // vingt éléments sélectionnés en auraient donc lancé vingt par image.
        var placed = controller.rects();
        for (String name : controller.selection().ids()) {
            ScreenLayout.Rect rect = placed.get(name);
            if (rect == null) {
                continue;
            }
            int left = surface.toScreenX(rect.x());
            int topPx = surface.toScreenY(rect.y());
            int right = surface.toScreenX(rect.right());
            int bottom = surface.toScreenY(rect.bottom());
            g.fill(left, topPx, right, topPx + 1, SELECTED);
            g.fill(left, bottom - 1, right, bottom, SELECTED);
            g.fill(left, topPx, left + 1, bottom, SELECTED);
            g.fill(right - 1, topPx, right, bottom, SELECTED);
        }
        if (controller.selection().size() != 1) {
            return;
        }
        String only = controller.selection().ids().iterator().next();
        ScreenLayout.Rect rect = placed.get(only);
        if (rect == null) {
            return;
        }
        // Seules les poignées qui AGISSENT sont dessinées : sur un enfant rangé par son
        // conteneur, tirer une largeur ne changerait rien, et une poignée inerte fait
        // croire à un outil cassé.
        for (ScreenCanvasController.Handle handle : controller.operableHandles(only)) {
            int hx = surface.toScreenX(rect.x() + rect.width() * handle.fractionX());
            int hy = surface.toScreenY(rect.y() + rect.height() * handle.fractionY());
            g.fill(hx - 2, hy - 2, hx + 2, hy + 2, HANDLE);
        }
    }

    private void renderGuides(GuiGraphics g) {
        for (AlignmentGuides.Guide guide : controller.guides()) {
            if (guide.vertical()) {
                int x = surface.toScreenX(guide.position());
                g.fill(x, surface.toScreenY(guide.from()), x + 1,
                        surface.toScreenY(guide.to()), GUIDE);
            } else {
                int y = surface.toScreenY(guide.position());
                g.fill(surface.toScreenX(guide.from()), y,
                        surface.toScreenX(guide.to()), y + 1, GUIDE);
            }
        }
    }

    /**
     * Le sélecteur de taille de fenêtre — le cœur de ce qui manquait.
     *
     * <p>Une ancre et un pourcentage ne veulent rien dire tant qu'on ne les voit pas
     * bouger. Concevoir toujours à 320×180 revenait à écrire une mise en page adaptative
     * sans jamais redimensionner la fenêtre : on découvrait le résultat en jeu, chez
     * quelqu'un d'autre.
     */
    private void renderViewportBar(GuiGraphics g, Font font) {
        int y = height - ROW - 2;
        int x = PALETTE_WIDTH + 4;
        g.drawString(font, I18n.get("blueprint.designer.viewport"), x, y, DIM_TEXT, false);
        x += font.width(I18n.get("blueprint.designer.viewport")) + 6;
        var current = controller.viewportPreset();
        for (var viewport : ScreenCanvasController.Viewport.values()) {
            String label = viewport.width() + "×" + viewport.height();
            g.drawString(font, label, x, y, viewport == current ? SELECTED : TEXT, false);
            x += font.width(label) + 8;
        }
        // « écran » : la taille réelle du joueur, celle qu'il a sous les yeux.
        g.drawString(font, I18n.get("blueprint.designer.viewport_mine"), x, y,
                current == null ? SELECTED : TEXT, false);

        // Les packs dont l'écran dépend (10.5, AC5), déduits de ses textures. En rouge
        // ceux qui ne sont PAS installés ici : l'auteur voit ainsi, en concevant, ce que
        // verra celui à qui il donnera son menu sans le dossier qui va avec.
        Screen screen = controller.screen();
        if (screen != null && !screen.requiredPacks().isEmpty()) {
            var installed = fr.blueprint.client.pack.PackTextures.packs().keySet();
            int px = PALETTE_WIDTH + 4;
            int py = y - ROW;
            String label = I18n.get("blueprint.designer.packs", "");
            g.drawString(font, label, px, py, DIM_TEXT, false);
            px += font.width(label) + 2;
            for (String pack : screen.requiredPacks()) {
                g.drawString(font, pack, px, py, installed.contains(pack) ? TEXT : INVALID, false);
                px += font.width(pack) + 6;
            }
        }
    }

    /** Le clic dans la barre de tailles ; faux si le point est ailleurs. */
    private boolean clickViewport(double mx, double my, Font font) {
        int y = height - ROW - 2;
        if (my < y || my >= y + ROW) {
            return false;
        }
        int x = PALETTE_WIDTH + 4 + font.width(I18n.get("blueprint.designer.viewport")) + 6;
        for (var viewport : ScreenCanvasController.Viewport.values()) {
            int w = font.width(viewport.width() + "×" + viewport.height()) + 8;
            if (mx >= x && mx < x + w) {
                controller.setViewport(viewport);
                return true;
            }
            x += w;
        }
        int w = font.width(I18n.get("blueprint.designer.viewport_mine"));
        if (mx >= x && mx < x + w) {
            controller.setViewport(playerViewportWidth, playerViewportHeight);
            return true;
        }
        return true;   // la barre absorbe le clic : rien derrière elle
    }

    /** La taille réelle de la fenêtre du joueur, en unités — relevée à chaque image. */
    private int playerViewportWidth = Screen.SAFE_WIDTH;
    private int playerViewportHeight = Screen.SAFE_HEIGHT;

    private void renderPalette(GuiGraphics g, Font font) {
        g.fill(0, top, PALETTE_WIDTH, height, PANEL_BACKGROUND);
        g.fill(PALETTE_WIDTH - 1, top, PALETTE_WIDTH, height, PANEL_BORDER);

        int y = top + 3;
        g.drawString(font, I18n.get("blueprint.designer.screens"), 4, y, DIM_TEXT, false);
        y += ROW;
        for (String name : session.blueprint().screens().keySet()) {
            boolean active = name.equals(controller.screenName());
            g.drawString(font, font.plainSubstrByWidth(name, PALETTE_WIDTH - 8), 6, y,
                    active ? SELECTED : TEXT, false);
            y += ROW;
        }
        g.drawString(font, I18n.get("blueprint.designer.add_screen"), 6, y, DIM_TEXT, false);
        y += ROW;
        // Les actions de l'écran COURANT, sous sa liste : renommer, supprimer, et le
        // passage modal ↔ HUD, qui n'existaient nulle part — on pouvait créer un écran
        // et plus jamais le défaire.
        Screen current = controller.screen();
        boolean hud = current != null && current.hud();
        String mode = hud ? I18n.get("blueprint.designer.screen_hud")
                : I18n.get("blueprint.designer.screen_modal");
        g.drawString(font, mode, 6, y, hud ? SELECTED : DIM_TEXT, false);
        y += ROW;
        g.drawString(font, I18n.get("blueprint.designer.rename_screen"), 6, y, DIM_TEXT, false);
        y += ROW;
        g.drawString(font, I18n.get("blueprint.designer.remove_screen"), 6, y, DIM_TEXT, false);
        y += ROW + 4;

        g.drawString(font, I18n.get("blueprint.designer.elements"), 4, y, DIM_TEXT, false);
        y += ROW;
        for (ElementKind kind : ElementKind.values()) {
            g.drawString(font, I18n.get(kindKey(kind)), 6, y, TEXT, false);
            y += ROW;
        }
        y += 4;
        renderLayers(g, font, y);
    }

    /**
     * La liste des éléments, du <b>dessus vers le dessous</b> — l'ordre de dessin lu à
     * l'envers, comme dans tout éditeur graphique.
     *
     * <p>C'est la seule façon d'atteindre un parent entièrement recouvert par ses
     * enfants : sur le canevas, cliquer dessus attrape l'enfant, toujours. Sans cette
     * liste, un panneau de fond devenait injoignable dès qu'on avait posé quelque chose
     * par-dessus — et il n'y avait plus aucun moyen de le déplacer ni de le supprimer.
     */
    private void renderLayers(GuiGraphics g, Font font, int startY) {
        Screen screen = controller.screen();
        if (screen == null) {
            return;
        }
        g.drawString(font, I18n.get("blueprint.designer.layers"), 4, startY, DIM_TEXT, false);
        int y = startY + ROW;
        for (String name : layerOrder(screen)) {
            if (y + ROW > height) {
                break;   // le panneau ne déborde pas : le reste s'atteint au canevas
            }
            ScreenElement element = screen.element(name);
            boolean selected = controller.selection().isSelected(name);
            int depth = depthOf(screen, name);
            // L'œil dit d'un coup ce qui est masqué — sans lui, un élément invisible
            // à la conception se confond avec un élément absent.
            g.drawString(font, element.visible() ? "◉" : "○", 4, y,
                    element.visible() ? TEXT : DIM_TEXT, false);
            g.drawString(font, font.plainSubstrByWidth(name, PALETTE_WIDTH - 18 - depth * 4),
                    14 + depth * 4, y, selected ? SELECTED : TEXT, false);
            y += ROW;
        }
    }

    /** Du dessus vers le dessous, chaque enfant sous son parent. */
    private static List<String> layerOrder(Screen screen) {
        List<String> out = new java.util.ArrayList<>(screen.elements().keySet());
        java.util.Collections.reverse(out);
        return out;
    }

    private static int depthOf(Screen screen, String name) {
        int depth = 0;
        java.util.Set<String> seen = new java.util.HashSet<>();
        seen.add(name);
        ScreenElement element = screen.element(name);
        String cursor = element == null ? null : element.parent();
        while (cursor != null && seen.add(cursor) && depth < 4) {
            depth++;
            ScreenElement up = screen.element(cursor);
            cursor = up == null ? null : up.parent();
        }
        return depth;
    }

    private static String kindKey(ElementKind kind) {
        return "blueprint.designer.kind." + kind.name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Une zone cliquable du panneau. Le rendu et le clic <b>partagent la même liste</b> :
     * les deux se recalculaient chacun leurs ordonnées, et toute rangée ajoutée au milieu
     * décalait les clics d'un cran sans que rien ne le dise (story 10.10).
     */
    private record Chip(String label, int x, int width, boolean active, Runnable onClick) {
    }

    private record Row(int y, String label, java.util.List<Chip> chips,
                       ElementPropertiesState.@Nullable Field field, @Nullable String value) {
    }

    private java.util.List<Row> propertyRows() {
        ScreenElement element = properties.element();
        Screen screen = controller.screen();
        if (element == null || screen == null) {
            return java.util.List.of();
        }
        java.util.List<Row> rows = new java.util.ArrayList<>();
        int y = top + 3;
        rows.add(new Row(y, I18n.get(kindKey(element.kind())), java.util.List.of(), null, null));
        y += ROW;

        // L'ancre : une grille 3×3 cliquable. Elle se faisait défiler d'un clic parmi
        // neuf valeurs à l'aveugle — jusqu'à huit clics pour atteindre celle qu'on veut.
        // Inutile si le parent range ses enfants : leur place ne vient pas de l'ancre.
        boolean arranged = arrangedByParent(screen, element);
        if (!arranged) {
            int[] cell = properties.anchorCell();
            for (int row = 0; row < 3; row++) {
                java.util.List<Chip> chips = new java.util.ArrayList<>(3);
                for (int column = 0; column < 3; column++) {
                    int c = column;
                    int r = row;
                    chips.add(new Chip("", 52 + column * 11, 10,
                            cell[0] == column && cell[1] == row,
                            () -> apply(properties.setAnchor(c, r))));
                }
                rows.add(new Row(y, row == 0 ? I18n.get("blueprint.designer.anchor") : "",
                        chips, null, null));
                y += ROW;
            }
        }

        for (ElementPropertiesState.Field field : ElementPropertiesState.Field.values()) {
            if (!fieldApplies(element, field, arranged)) {
                continue;
            }
            // Chaque axe de taille porte ses quatre modes : les taper en texte demandait
            // de connaître une syntaxe qu'aucun panneau n'affichait.
            java.util.List<Chip> chips = java.util.List.of();
            if (field == ElementPropertiesState.Field.WIDTH
                    || field == ElementPropertiesState.Field.HEIGHT) {
                boolean horizontal = field == ElementPropertiesState.Field.WIDTH;
                chips = sizeModeChips(element, horizontal);
            }
            boolean editing = properties.isEditing(field);
            rows.add(new Row(y, I18n.get(fieldKey(field)), chips, field,
                    editing ? properties.buffer() + "_" : properties.valueOf(field)));
            y += ROW;
        }

        if (element.kind().container()) {
            rows.add(new Row(y, I18n.get("blueprint.designer.layout"),
                    enumChips(fr.blueprint.core.graph.screen.LayoutSpec.Mode.values(),
                            element.layout().mode(), "blueprint.designer.layout.",
                            mode -> apply(properties.setLayoutMode(mode))), null, null));
            y += ROW;
            if (element.arranges()) {
                rows.add(new Row(y, I18n.get("blueprint.designer.main"),
                        enumChips(fr.blueprint.core.graph.screen.LayoutSpec.Distribute.values(),
                                element.layout().main(), "blueprint.designer.main.",
                                main -> apply(properties.setLayoutMain(main))), null, null));
                y += ROW;
                rows.add(new Row(y, I18n.get("blueprint.designer.cross"),
                        enumChips(fr.blueprint.core.graph.screen.LayoutSpec.Cross.values(),
                                element.layout().cross(), "blueprint.designer.cross.",
                                cross -> apply(properties.setLayoutCross(cross))), null, null));
                y += ROW;
            }
        }

        // Les réglages propres au type (10.8). Chacun n'apparaît que là où il agit : un
        // « pas » sur une étiquette ou une « hauteur de ligne » sur un bouton seraient
        // des champs qu'on remplit sans que rien ne change.
        java.util.List<ElementPropertiesState.Field> richFields = optionFieldsOf(element.kind());
        if (!richFields.isEmpty()) {
            y += 2;
            if (element.kind() == ElementKind.INPUT) {
                rows.add(new Row(y, I18n.get("blueprint.designer.filter"),
                        enumChips(fr.blueprint.core.graph.screen.ElementOptions.InputFilter.values(),
                                element.options().filter(), "blueprint.designer.filter.",
                                filter -> apply(properties.setFilter(filter))), null, null));
                y += ROW;
            }
            for (var field : richFields) {
                rows.add(new Row(y, I18n.get(fieldKey(field)), java.util.List.of(), field,
                        properties.isEditing(field) ? properties.buffer() + "_"
                                : properties.valueOf(field)));
                y += ROW;
            }
        }

        // La liaison (10.7). La variable se CHOISIT : la taper laisserait passer une
        // faute de frappe que seul le validateur signalerait, une fois le geste oublié.
        y += 2;
        rows.add(new Row(y, I18n.get("blueprint.designer.bind"), java.util.List.of(
                new Chip(I18n.get("blueprint.designer.bind.none"), 52, 24,
                        !element.isBound(), () -> apply(properties.bindTo("")))), null, null));
        y += ROW;
        for (String variable : session.blueprint().variables().keySet()) {
            String bound = variable;
            rows.add(new Row(y, "", java.util.List.of(
                    new Chip(variable, 4, PROPERTIES_WIDTH - 8,
                            variable.equals(element.binding().variable()),
                            () -> apply(properties.bindTo(bound)))), null, null));
            y += ROW;
        }
        if (element.isBound()) {
            rows.add(new Row(y, I18n.get("blueprint.designer.bind.target"),
                    enumChips(fr.blueprint.core.graph.screen.ElementBinding.Target.values(),
                            element.binding().target(), "blueprint.designer.bind.",
                            target -> apply(properties.bindTarget(target))), null, null));
            y += ROW;
            for (var field : java.util.List.of(
                    ElementPropertiesState.Field.BIND_FORMAT,
                    ElementPropertiesState.Field.BIND_DECIMALS,
                    ElementPropertiesState.Field.BIND_MIN,
                    ElementPropertiesState.Field.BIND_MAX)) {
                if (!bindFieldApplies(element, field)) {
                    continue;
                }
                rows.add(new Row(y, I18n.get(fieldKey(field)), java.util.List.of(), field,
                        properties.isEditing(field) ? properties.buffer() + "_"
                                : properties.valueOf(field)));
                y += ROW;
            }
        }

        y += 2;
        rows.add(new Row(y, I18n.get("blueprint.designer.styles"), java.util.List.of(
                new Chip(I18n.get("blueprint.designer.styles.create"), 4, PROPERTIES_WIDTH - 8,
                        false, controller::createStyleFromSelection)), null, null));
        y += ROW;
        if (screen.styles().isEmpty()) {
            rows.add(new Row(y, I18n.get("blueprint.designer.styles.none"),
                    java.util.List.of(), null, null));
        }
        for (String styleName : screen.styles().keySet()) {
            rows.add(new Row(y, "", java.util.List.of(
                    new Chip(styleName, 4, PROPERTIES_WIDTH - 40,
                            styleName.equals(element.styleName()),
                            () -> controller.applyStyleToSelection(styleName)),
                    new Chip(I18n.get("blueprint.designer.styles.detach"),
                            PROPERTIES_WIDTH - 34, 30, false,
                            () -> controller.applyStyleToSelection(""))), null, null));
            y += ROW;
        }
        return rows;
    }

    /** Les quatre modes de taille d'un axe, celui en cours mis en avant. */
    private java.util.List<Chip> sizeModeChips(ScreenElement element, boolean horizontal) {
        var current = (horizontal ? element.width() : element.height()).mode();
        java.util.List<Chip> chips = new java.util.ArrayList<>(4);
        int x = 52;
        for (var mode : fr.blueprint.core.graph.screen.Extent.Mode.values()) {
            String label = I18n.get("blueprint.designer.size."
                    + mode.name().toLowerCase(java.util.Locale.ROOT));
            chips.add(new Chip(label, x, 17, current == mode,
                    () -> apply(properties.setSizeMode(horizontal, mode))));
            x += 18;
        }
        return chips;
    }

    private <E extends Enum<E>> java.util.List<Chip> enumChips(
            E[] values, E current, String keyPrefix, java.util.function.Consumer<E> onPick) {
        java.util.List<Chip> chips = new java.util.ArrayList<>(values.length);
        int x = 52;
        for (E value : values) {
            String label = I18n.get(keyPrefix + value.name().toLowerCase(java.util.Locale.ROOT));
            chips.add(new Chip(label, x, 17, current == value, () -> onPick.accept(value)));
            x += 18;
        }
        return chips;
    }

    /**
     * Un champ n'est montré que s'il agit. Un x/y sur un enfant rangé par son conteneur
     * s'écrirait sans rien changer à l'écran, et {@code colonnes} n'existe qu'en grille.
     */
    private static boolean fieldApplies(ScreenElement element,
                                        ElementPropertiesState.Field field, boolean arranged) {
        return switch (field) {
            case X, Y -> !arranged;
            case GAP, CROSS_GAP -> element.arranges();
            case COLUMNS -> element.layout().mode()
                    == fr.blueprint.core.graph.screen.LayoutSpec.Mode.GRID;
            default -> true;
        };
    }

    /**
     * Un réglage de liaison n'est montré que s'il agit : le format ne veut rien dire pour
     * une case à cocher, les bornes n'existent que pour une barre.
     */
    private static boolean bindFieldApplies(ScreenElement element,
                                            ElementPropertiesState.Field field) {
        var target = element.binding().target();
        return switch (field) {
            case BIND_FORMAT, BIND_DECIMALS ->
                    target == fr.blueprint.core.graph.screen.ElementBinding.Target.TEXT;
            case BIND_MIN, BIND_MAX ->
                    target == fr.blueprint.core.graph.screen.ElementBinding.Target.PROGRESS;
            default -> true;
        };
    }

    /**
     * Les réglages qui ont un sens pour ce type d'élément (10.8).
     *
     * <p>Montrer les onze à chaque fois aurait été plus court à écrire et pénible à
     * utiliser : l'auteur devrait deviner lesquels agissent, et un champ rempli sans
     * effet est exactement ce qui fait douter d'un outil.
     */
    private static java.util.List<ElementPropertiesState.Field> optionFieldsOf(ElementKind kind) {
        return switch (kind) {
            case INPUT -> java.util.List.of(ElementPropertiesState.Field.PLACEHOLDER,
                    ElementPropertiesState.Field.MAX_LENGTH);
            case SLIDER -> java.util.List.of(ElementPropertiesState.Field.OPT_MIN,
                    ElementPropertiesState.Field.OPT_MAX,
                    ElementPropertiesState.Field.STEP);
            case LIST -> java.util.List.of(ElementPropertiesState.Field.ROW_HEIGHT);
            case ENTITY_PREVIEW -> java.util.List.of(ElementPropertiesState.Field.ENTITY);
            default -> java.util.List.of();
        };
    }

    private static boolean arrangedByParent(Screen screen, ScreenElement element) {
        ScreenElement container = element.parent() == null ? null
                : screen.element(element.parent());
        return container != null && container.arranges();
    }

    private void renderProperties(GuiGraphics g, Font font) {
        int left = width - PROPERTIES_WIDTH;
        g.fill(left, top, width, height, PANEL_BACKGROUND);
        g.fill(left, top, left + 1, height, PANEL_BORDER);

        if (properties.element() == null) {
            g.drawString(font, I18n.get("blueprint.designer.no_selection"), left + 4, top + 4,
                    DIM_TEXT, false);
            return;
        }
        for (Row row : propertyRows()) {
            if (!row.label().isEmpty()) {
                g.drawString(font, font.plainSubstrByWidth(row.label(), PROPERTIES_WIDTH - 8),
                        left + 4, row.y(), DIM_TEXT, false);
            }
            for (Chip chip : row.chips()) {
                int x = left + chip.x();
                g.fill(x, row.y() - 1, x + chip.width(), row.y() + ROW - 3,
                        chip.active() ? SELECTED : PANEL_BORDER);
                if (!chip.label().isEmpty()) {
                    g.drawString(font,
                            font.plainSubstrByWidth(chip.label(), chip.width() - 2),
                            x + 1, row.y(), chip.active() ? PANEL_BACKGROUND : TEXT, false);
                }
            }
            if (row.value() != null) {
                boolean editing = properties.isEditing(row.field());
                int color = editing
                        ? (properties.valid(this::nameFree) ? SELECTED : INVALID) : TEXT;
                int valueX = row.chips().isEmpty() ? 52
                        : row.chips().getLast().x() + row.chips().getLast().width() + 3;
                g.drawString(font, font.plainSubstrByWidth(row.value(),
                                PROPERTIES_WIDTH - valueX - 4),
                        left + valueX, row.y(), color, false);
            }
        }
    }

    private static String fieldKey(ElementPropertiesState.Field field) {
        return "blueprint.designer.field." + field.name().toLowerCase(java.util.Locale.ROOT);
    }

    private boolean nameFree(String candidate) {
        ScreenElement element = properties.element();
        return controller.nameAvailable(candidate, element == null ? null : element.name());
    }

    // ------------------------------------------------------------------ souris

    public boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        double mx = e.x();
        double my = e.y();
        if (my < top) {
            return false;
        }
        message = null;
        if (clickViewport(mx, my, net.minecraft.client.Minecraft.getInstance().font)) {
            return true;
        }
        if (mx < PALETTE_WIDTH) {
            return clickPalette(my);
        }
        if (mx >= width - PROPERTIES_WIDTH) {
            return clickProperties(mx, my);
        }
        if (!surface.contains(mx, my)) {
            return false;
        }
        properties.cancel();
        controller.press(surface.toDesignX(mx), surface.toDesignY(my),
                e.hasShiftDown());
        return true;
    }

    private boolean clickPalette(double my) {
        int y = top + 3 + ROW;
        for (String name : List.copyOf(session.blueprint().screens().keySet())) {
            if (my >= y && my < y + ROW) {
                controller.setScreenName(name);
                return true;
            }
            y += ROW;
        }
        if (my >= y && my < y + ROW) {
            addScreen();
            return true;
        }
        y += ROW;
        if (my >= y && my < y + ROW) {
            if (!controller.toggleHud()) {
                reportRefusal();
            }
            return true;
        }
        y += ROW;
        if (my >= y && my < y + ROW) {
            // Le renommage se fait dans le champ du panneau de droite, réutilisé : un
            // second éditeur de texte donnerait deux comportements de frappe à retenir.
            renamingScreen = controller.screenName();
            screenBuffer = controller.screenName();
            return true;
        }
        y += ROW;
        if (my >= y && my < y + ROW) {
            controller.removeCurrentScreen();
            return true;
        }
        y += ROW + 4 + ROW;
        for (ElementKind kind : ElementKind.values()) {
            if (my >= y && my < y + ROW) {
                // Posé au centre de la surface : l'auteur le traîne ensuite où il veut,
                // plutôt que de deviner un point de dépôt qu'il n'a pas indiqué.
                if (controller.addElement(kind, Screen.SAFE_WIDTH / 2.0,
                        Screen.SAFE_HEIGHT / 2.0) == null) {
                    reportRefusal();
                }
                return true;
            }
            y += ROW;
        }
        y += 4 + ROW;
        // La liste des calques : sélectionner, et basculer la visibilité par l'œil.
        Screen screen = controller.screen();
        if (screen != null) {
            for (String name : layerOrder(screen)) {
                if (my >= y && my < y + ROW) {
                    controller.selection().selectAll(List.of(name), false);
                    return true;
                }
                y += ROW;
            }
        }
        return true;
    }

    /** L'écran en cours de renommage, et ce qui est tapé. */
    private @Nullable String renamingScreen;
    private String screenBuffer = "";

    private void addScreen() {
        String base = I18n.get("blueprint.designer.new_screen_name");
        String name = base;
        for (int i = 2; session.blueprint().screen(name) != null; i++) {
            name = base + "_" + i;
        }
        var result = new ScreenOps.AddScreen(Screen.empty(name))
                .apply(session.blueprint(), lookup);
        if (result.applied()) {
            if (result.inverse() != null) {
                controller.historyRecord(result.inverse());
            }
            controller.setScreenName(name);
        } else if (result.refusal() != null) {
            message = I18n.get(result.refusal().translationKey(),
                    result.refusal().args().toArray());
        }
    }

    private boolean clickProperties(double mx, double my) {
        if (properties.element() == null) {
            return true;
        }
        double local = mx - (width - PROPERTIES_WIDTH);
        for (Row row : propertyRows()) {
            if (my < row.y() - 1 || my >= row.y() + ROW - 1) {
                continue;
            }
            for (Chip chip : row.chips()) {
                if (local >= chip.x() && local < chip.x() + chip.width()) {
                    chip.onClick().run();
                    reportRefusal();
                    return true;
                }
            }
            if (row.field() != null) {
                properties.beginEdit(row.field());
            }
            return true;
        }
        return true;
    }

    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (controller.gesture() == ScreenCanvasController.Gesture.NONE) {
            return false;
        }
        controller.drag(surface.toDesignX(e.x()), surface.toDesignY(e.y()));
        return true;
    }

    public boolean mouseReleased(MouseButtonEvent e) {
        if (controller.gesture() == ScreenCanvasController.Gesture.NONE) {
            return false;
        }
        controller.release();
        reportRefusal();
        return true;
    }

    private void reportRefusal() {
        Diagnostic refusal = controller.takeRefusal();
        message = refusal == null ? null
                : I18n.get(refusal.translationKey(), refusal.args().toArray());
    }

    // ------------------------------------------------------------------ clavier

    public boolean keyPressed(KeyEvent e) {
        if (renamingScreen != null) {
            return screenNameKey(e);
        }
        if (properties.editing() != null) {
            return editKey(e);
        }
        // Les flèches : le seul moyen de poser un élément à l'unité près. Shift passe
        // à dix — un cran de grille visible, pour traverser sans marteler la touche.
        double step = e.hasShiftDown() ? 10 : 1;
        return switch (e.key()) {
            case GLFW.GLFW_KEY_LEFT -> nudge(-step, 0);
            case GLFW.GLFW_KEY_RIGHT -> nudge(step, 0);
            case GLFW.GLFW_KEY_UP -> nudge(0, -step);
            case GLFW.GLFW_KEY_DOWN -> nudge(0, step);
            case GLFW.GLFW_KEY_DELETE -> {
                controller.deleteSelection();
                yield true;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                // Échap désélectionne AVANT de fermer l'éditeur : sinon on perdrait sa
                // fenêtre pour avoir voulu annuler une sélection.
                if (controller.selection().isEmpty()) {
                    yield false;
                }
                controller.selection().clear();
                yield true;
            }
            case GLFW.GLFW_KEY_D -> control(e, controller::duplicateSelection);
            case GLFW.GLFW_KEY_C -> control(e, controller::copySelection);
            case GLFW.GLFW_KEY_V -> control(e, controller::paste);
            case GLFW.GLFW_KEY_A -> control(e, () -> {
                Screen screen = controller.screen();
                if (screen == null) {
                    return false;
                }
                controller.selection().selectAll(List.copyOf(screen.elements().keySet()), false);
                return true;
            });
            case GLFW.GLFW_KEY_H -> control(e, () -> controller.toggleSelection(true));
            case GLFW.GLFW_KEY_G -> {
                controller.toggleSnap();
                yield true;
            }
            case GLFW.GLFW_KEY_PAGE_UP -> {
                controller.reorderSelection(1);
                yield true;
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                controller.reorderSelection(-1);
                yield true;
            }
            // Alignement au pavé numérique : la disposition des touches DESSINE
            // l'alignement obtenu, ce qu'aucun raccourci alphabétique ne fait.
            case GLFW.GLFW_KEY_KP_4 -> align(ScreenCanvasController.Align.LEFT);
            case GLFW.GLFW_KEY_KP_5 -> align(ScreenCanvasController.Align.CENTER_X);
            case GLFW.GLFW_KEY_KP_6 -> align(ScreenCanvasController.Align.RIGHT);
            case GLFW.GLFW_KEY_KP_8 -> align(ScreenCanvasController.Align.TOP);
            case GLFW.GLFW_KEY_KP_2 -> align(ScreenCanvasController.Align.BOTTOM);
            case GLFW.GLFW_KEY_KP_0 -> align(ScreenCanvasController.Align.CENTER_Y);
            case GLFW.GLFW_KEY_KP_ADD -> {
                controller.distributeSelection(true);
                yield true;
            }
            case GLFW.GLFW_KEY_KP_SUBTRACT -> {
                controller.distributeSelection(false);
                yield true;
            }
            default -> false;
        };
    }

    private boolean nudge(double dx, double dy) {
        if (controller.selection().isEmpty()) {
            return false;
        }
        controller.nudgeSelection(dx, dy);
        reportRefusal();
        return true;
    }

    private boolean align(ScreenCanvasController.Align align) {
        controller.alignSelection(align);
        return true;
    }

    /** N'agit que si Ctrl est enfoncé — sinon la touche reste libre pour le jeu. */
    private boolean control(KeyEvent e, java.util.function.BooleanSupplier action) {
        if (!e.hasControlDown()) {
            return false;
        }
        action.getAsBoolean();
        reportRefusal();
        return true;
    }

    private boolean editKey(KeyEvent e) {
        switch (e.key()) {
            case GLFW.GLFW_KEY_ESCAPE -> properties.cancel();
            case GLFW.GLFW_KEY_BACKSPACE -> properties.backspace();
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                String rename = properties.pendingName();
                ScreenElement element = properties.element();
                ScreenElement edited = properties.commit(this::nameFree);
                if (rename != null && element != null) {
                    if (!controller.rename(element.name(), rename)) {
                        reportRefusal();
                    }
                } else {
                    apply(edited);
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void apply(@Nullable ScreenElement edited) {
        if (edited != null && !controller.setElement(edited)) {
            reportRefusal();
        }
    }

    public boolean charTyped(CharacterEvent e) {
        if (renamingScreen != null) {
            screenBuffer += (char) e.codepoint();
            return true;
        }
        if (properties.editing() == null) {
            return false;
        }
        properties.type((char) e.codepoint());
        return true;
    }

    /** La frappe du nom d'écran : mêmes touches que le panneau de propriétés. */
    private boolean screenNameKey(KeyEvent e) {
        switch (e.key()) {
            case GLFW.GLFW_KEY_ESCAPE -> renamingScreen = null;
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!screenBuffer.isEmpty()) {
                    screenBuffer = screenBuffer.substring(0, screenBuffer.length() - 1);
                }
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (!controller.renameCurrentScreen(screenBuffer.trim())) {
                    message = I18n.get("blueprint.designer.bad_screen_name", screenBuffer);
                }
                renamingScreen = null;
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
