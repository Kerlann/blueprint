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
 * {@link ScreenPainter} — celui-là même que le rendu en jeu utilise (10.3). Ce qui
 * reste ici est ce qui ne se teste pas sans client : le chrome.
 *
 * <p>La vue vit dans {@link DesignCamera} et la place des panneaux dans
 * {@link DesignerPanels}, pour la même raison : ce sont des calculs que le rendu et le
 * clic doivent lire au même endroit.
 */
public final class ScreenDesignerWidget {

    /** Largeur de la palette dépliée — voir {@link DesignerPanels}. */
    public static final int PALETTE_WIDTH = DesignerPanels.PALETTE_WIDTH;

    public static final int PROPERTIES_WIDTH = DesignerPanels.PROPERTIES_WIDTH;

    /** Place utile d'un libellé de palette, marges comprises. */
    private static final int PALETTE_LABEL = PALETTE_WIDTH - 12;

    /**
     * Écart entre deux sections de la palette, filet compris. UNE constante, lue par le
     * rendu ET par le clic : les deux recalculaient leurs ordonnées chacun de leur côté,
     * et tout espace ajouté d'un seul côté aurait décalé les clics d'une ligne — le
     * genre de défaut qu'on attribue à sa propre maladresse avant de le soupçonner.
     */
    private static final int SECTION_GAP = 10;
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
    /** Assez visible pour se compter, assez discret pour ne pas concurrencer le menu. */
    private static final int GRID = 0x22FFFFFF;
    private static final int OVERFLOW = 0xFFE0AF68;
    private static final int HANDLE = 0xFFE6E6E6;
    private static final int RUBBER_FILL = 0x337AA2F7;

    private final EditorSession session;
    private final NodeTypeLookup lookup;
    private final ScreenCanvasController controller;
    private final ElementPropertiesState properties = new ElementPropertiesState();
    private final DesignCamera camera = new DesignCamera();

    private DesignerPanels panels = DesignerPanels.OPEN;
    private int top;
    private int width;
    private int height;
    /** La zone de dessin en pixels, une fois les panneaux et les barres retirés. */
    private int areaLeft;
    private int areaTop;
    private int areaWidth = 1;
    private int areaHeight = 1;
    private DesignSurface surface =
            new DesignSurface(0, 0, 1, Screen.SAFE_WIDTH, Screen.SAFE_HEIGHT);
    /** Recadrer à la prochaine image : à l'ouverture, au changement de taille, sur F. */
    private boolean needsFit = true;
    private boolean panning;
    private boolean spaceDown;
    /** Maj tenue : elle vise l'axe horizontal d'un panneau défilant. */
    private boolean shiftHeld;
    private double mouseX;
    private double mouseY;
    private @Nullable String message;

    public ScreenDesignerWidget(EditorSession session, NodeTypeLookup lookup, UndoStack history) {
        this.session = session;
        this.lookup = lookup;
        String first = session.blueprint().screens().keySet().stream().findFirst().orElse("");
        this.controller = new ScreenCanvasController(session.blueprint(), lookup, history, first);
        // Le concepteur s'ouvre au large, et CADRÉ. Ouvrir grand sans cadrer reproduirait
        // exactement le défaut qu'on corrige : un canevas immense dont on ne voit qu'un
        // coin, sans savoir de quel côté chercher le reste.
        this.controller.setViewport(ScreenCanvasController.Viewport.DESIGN_DEFAULT);
    }

    public ScreenCanvasController controller() {
        return controller;
    }

    public void setBounds(int top, int width, int height) {
        this.top = top;
        this.width = width;
        this.height = height;
        this.areaLeft = panels.canvasLeft();
        this.areaTop = top + ROW;
        this.areaWidth = panels.canvasWidth(width);
        this.areaHeight = Math.max(1, height - areaTop - ROW * 2);

        int unitsWidth = (int) controller.viewportWidth();
        int unitsHeight = (int) controller.viewportHeight();
        if (needsFit) {
            camera.fit(areaWidth, areaHeight, unitsWidth, unitsHeight);
            needsFit = false;
        } else {
            // Borné à chaque image, et pas seulement au relâchement : la fenêtre du jeu
            // peut changer de taille pendant qu'on ne touche à rien, et le canevas se
            // retrouverait hors de la zone sans qu'aucun geste ne l'ait déplacé.
            camera.clampInto(areaWidth, areaHeight, unitsWidth, unitsHeight);
        }
        this.surface = DesignSurface.of(camera, areaLeft, areaTop, unitsWidth, unitsHeight);
        // Les tolérances de geste du contrôleur suivent le zoom, en UN seul endroit.
        controller.setUnitsPerPixel(1 / surface.zoom());
    }

    // ------------------------------------------------------------------- rendu

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        playerViewportWidth = g.guiWidth();
        playerViewportHeight = g.guiHeight();
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        Screen screen = controller.screen();
        properties.select(screen == null || controller.selection().size() != 1 ? null
                : screen.element(controller.selection().ids().iterator().next()));
        // Un élément sélectionné mais sorti d'un panneau défilant serait dessiné nulle
        // part : introuvable à la souris, impossible à déplacer ou à régler. Le ramener
        // sous les yeux est ce qui rend le découpage supportable en conception.
        controller.revealSelection();

        g.fill(0, top, width, height, SURFACE_BACKGROUND);
        renderSurface(g, font, screen);
        renderPalette(g, font);
        renderProperties(g, font);
        renderViewportBar(g, font);
        renderHoverTooltip(g, font, screen);
        renderHelp(g, font);
        if (message != null) {
            g.drawString(font, message, panels.canvasLeft() + 4, height - ROW, INVALID, false);
        }
    }

    /**
     * L'infobulle de l'élément survolé, telle que le joueur la verra.
     *
     * <p>Écrire une infobulle sans la voir revient à l'écrire à l'aveugle : on ne peut
     * juger ni sa longueur, ni si la clé de traduction existe. Elle est montrée pendant
     * la conception, mais <b>pas pendant un geste</b> — une infobulle qui suit la souris
     * en plein déplacement masque précisément ce qu'on est en train de placer.
     */
    private void renderHoverTooltip(GuiGraphics g, Font font, @Nullable Screen screen) {
        if (screen == null || controller.gesture() != ScreenCanvasController.Gesture.NONE
                || !surface.contains(mouseX, mouseY)) {
            return;
        }
        String hovered = controller.hitTest(surface.toDesignX(mouseX), surface.toDesignY(mouseY));
        ScreenElement element = hovered == null ? null : screen.element(hovered);
        if (element == null || !element.hasTooltip()) {
            return;
        }
        g.setTooltipForNextFrame(font, element.tooltip().translate()
                        ? net.minecraft.network.chat.Component.translatable(
                                element.tooltip().value())
                        : net.minecraft.network.chat.Component.literal(element.tooltip().value()),
                (int) mouseX, (int) mouseY);
    }

    /**
     * La liste des raccourcis, à la touche F1.
     *
     * <p>Le concepteur en porte une vingtaine — alignement au pavé numérique, repli des
     * panneaux, cadrage, accroche, ordre de superposition — et ne les écrivait <b>nulle
     * part</b>. Un raccourci qu'on ne peut pas découvrir n'existe que pour celui qui l'a
     * écrit ; les six touches d'alignement, en particulier, ne se devinent pas.
     */
    private void renderHelp(GuiGraphics g, Font font) {
        if (!helpVisible) {
            return;
        }
        List<String> lines = List.of(
                "molette : zoom      milieu / Espace+clic : déplacer la vue",
                "F : cadrer          Ctrl+0 : 1:1          + / − : un cran",
                "Tab : replier les panneaux                G : grille",
                "flèches : décaler d'une unité (Maj : dix)",
                "pavé num. 4/5/6 : gauche / centre / droite",
                "pavé num. 8/0/2 : haut / milieu / bas",
                "pavé num. + / − : répartir (3 éléments et plus)",
                "un seul élément sélectionné : l'alignement se fait sur son PARENT",
                "Ctrl+D dupliquer   Ctrl+C/V copier-coller   Ctrl+A tout",
                "Ctrl+H masquer     Suppr supprimer          Pg↑/Pg↓ ordre",
                "Ctrl+S enregistrer                          F1 : fermer cette aide");
        int boxWidth = 0;
        for (String line : lines) {
            boxWidth = Math.max(boxWidth, font.width(line));
        }
        int left = panels.canvasLeft() + 8;
        int topPx = top + ROW * 2;
        g.fill(left - 4, topPx - 4, left + boxWidth + 4, topPx + lines.size() * ROW + 2,
                0xF01A1B1E);
        g.fill(left - 4, topPx - 4, left + boxWidth + 4, topPx - 3, PANEL_BORDER);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), left, topPx + i * ROW, TEXT, false);
        }
    }

    private boolean helpVisible;

    private void renderSurface(GuiGraphics g, Font font, @Nullable Screen screen) {
        // Le découpage suit la ZONE, pas le canevas : zoomé, le canevas déborde largement
        // de la place disponible, et sans cela il peindrait par-dessus les panneaux.
        g.enableScissor(areaLeft, areaTop, areaLeft + areaWidth, areaTop + areaHeight);

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
            g.disableScissor();
            return;
        }

        renderGrid(g);
        paintScreen(g, font, screen);
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
     * L'écran, peint <b>exactement comme en jeu</b> puis mis à l'échelle par la matrice.
     *
     * <p>Le concepteur passait au peintre un facteur entier, que celui-ci multipliait sur
     * les boîtes, les bordures et les marges — mais <b>pas sur le texte</b>, resté à sa
     * taille de police. Au facteur 2, une étiquette montrait donc deux fois plus de
     * caractères qu'en jeu, et l'auteur ne voyait pas la troncature qui l'attendait.
     * C'était sans conséquence tant que le facteur dépassait rarement 2 ; avec un zoom
     * jusqu'à 8, cela aurait rendu l'aperçu mensonger.
     *
     * <p>En appelant le peintre avec les paramètres du jeu — origine nulle, facteur 1 —
     * et en laissant la matrice grossir le tout, l'aperçu redevient fidèle par
     * construction. Le peintre, lui, n'est pas touché : c'est le fichier partagé avec le
     * rendu en partie, et le laisser tranquille est ce qui garantit qu'ils ne divergent
     * pas.
     *
     * <p><b>Et le dessin lit la table du contrôleur</b>, celle-là même que le hit-test
     * interroge. Le concepteur passait au peintre les dimensions de la fenêtre
     * <i>garantie</i> — 320×180 en dur — pendant que le clic, lui, résolvait à la taille
     * simulée : dès qu'on quittait le préréglage le plus petit, on cliquait à côté de ce
     * qu'on voyait, et les ancres comme les pourcentages étaient dessinés faux. Partager
     * les mêmes paramètres n'aurait pas suffi ; partager la même table le garantit.
     */
    private void paintScreen(GuiGraphics g, Font font, Screen screen) {
        g.pose().pushMatrix();
        g.pose().translate((float) surface.originX(), (float) surface.originY());
        g.pose().scale((float) surface.zoom(), (float) surface.zoom());
        // forceVisible : en conception, un élément masqué doit rester manipulable —
        // sinon le rendre invisible reviendrait à le perdre.
        ScreenPainter.paint(g, font, screen, controller.rects(), 0, 0, 1,
                new ScreenPainter.Visuals() {
                    @Override
                    public boolean forceVisible(String element) {
                        return true;
                    }

                    @Override
                    public fr.blueprint.core.graph.screen.ScreenText preview(
                            ScreenElement element) {
                        return previewOf(element);
                    }

                    /** Les curseurs de défilement se dessinent ici comme en jeu (10.13). */
                    @Override
                    public double panelScroll(String element) {
                        return controller.scrollOf(element);
                    }

                    @Override
                    public double panelScrollX(String element) {
                        return controller.scrollXOf(element);
                    }
                });
        g.pose().popMatrix();
    }

    /**
     * Ce qu'un élément lié montrera, avec la valeur PAR DÉFAUT de sa variable.
     *
     * <p>Le défaut et non un exemple inventé : c'est exactement ce que le joueur verra à
     * l'ouverture, puisque le serveur retombe dessus quand la variable n'a pas encore été
     * écrite (10.7). Le concepteur montre donc le premier état réel du menu, pas une
     * approximation.
     *
     * <p>Sans cela, un élément lié apparaissait vide — une ligne blanche dont rien ne
     * disait qu'elle afficherait quelque chose, et dont on ne pouvait juger ni la place
     * ni le format.
     */
    private fr.blueprint.core.graph.screen.ScreenText previewOf(ScreenElement element) {
        if (!element.isBound()
                || element.binding().target()
                        != fr.blueprint.core.graph.screen.ElementBinding.Target.TEXT) {
            return null;
        }
        var variable = session.blueprint().variables().get(element.binding().variable());
        Object value = variable == null || variable.defaultValue() == null
                ? null : variable.defaultValue().value();
        return fr.blueprint.core.net.ScreenBindings.previewText(element.binding(), value);
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
        var guaranteed = ScreenLayout.solve(screen, Screen.SAFE_WIDTH, Screen.SAFE_HEIGHT);
        // Le cerne se DESSINE là où l'élément est sur le canevas courant, alors qu'il se
        // DÉCIDE sur la fenêtre garantie. Les deux tables sont donc nécessaires : le
        // rectangle de la garantie servait aux deux, si bien qu'à 960×540 l'orange
        // apparaissait à un endroit sans rapport avec l'élément qu'il désignait.
        var current = controller.rects();
        for (ScreenElement element : screen.elements().values()) {
            ScreenLayout.Rect rect = guaranteed.get(element.name());
            if (rect == null || !fr.blueprint.core.graph.ScreenRules.outsideSafeArea(rect)) {
                continue;
            }
            outline(g, current.get(element.name()), OVERFLOW);
        }
    }

    private void renderSelection(GuiGraphics g, Screen screen) {
        // Une seule passe pour toute la sélection : rectOf en résout une par élément, et
        // vingt éléments sélectionnés en auraient donc lancé vingt par image.
        var placed = controller.rects();
        for (String name : controller.selection().ids()) {
            outline(g, placed.get(name), SELECTED);
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

    /**
     * Un liseré d'UN pixel, quel que soit le zoom — d'où le tracé hors de la matrice.
     * À l'intérieur, il grossirait avec le reste : huit pixels d'épaisseur au zoom le
     * plus serré, soit un cadre qui masque ce qu'il désigne.
     */
    private void outline(GuiGraphics g, ScreenLayout.@Nullable Rect rect, int colour) {
        if (rect == null) {
            return;
        }
        int left = surface.toScreenX(rect.x());
        int topPx = surface.toScreenY(rect.y());
        int right = surface.toScreenX(rect.right());
        int bottom = surface.toScreenY(rect.bottom());
        g.fill(left, topPx, right, topPx + 1, colour);
        g.fill(left, bottom - 1, right, bottom, colour);
        g.fill(left, topPx, left + 1, bottom, colour);
        g.fill(right - 1, topPx, right, bottom, colour);
    }

    /**
     * La grille d'accroche, <b>visible</b>.
     *
     * <p>L'accroche existait depuis la 10.2 et se basculait à la touche G, mais rien ne
     * la montrait : les éléments sautaient de deux en deux sans qu'on sache pourquoi, et
     * l'auteur qui trouvait le pas trop grossier n'avait aucun moyen de deviner qu'une
     * grille l'expliquait — ni qu'une touche l'éteignait.
     *
     * <p>Un trait tous les huit crans seulement : à deux unités de pas et au zoom serré,
     * dessiner chaque cran remplirait le canevas d'un damier qui masquerait le menu. Sous
     * quatre pixels d'écart, la grille disparaît — elle ne servirait qu'à griser le fond.
     */
    private void renderGrid(GuiGraphics g) {
        if (!controller.snapEnabled()) {
            return;
        }
        double step = ScreenCanvasController.GRID_STEP * 8;
        if (surface.toPixels(step) < 4) {
            return;
        }
        int units = (int) controller.viewportWidth();
        int tall = (int) controller.viewportHeight();
        for (double x = step; x < units; x += step) {
            int px = surface.toScreenX(x);
            g.fill(px, surface.top(), px + 1, surface.bottom(), GRID);
        }
        for (double y = step; y < tall; y += step) {
            int py = surface.toScreenY(y);
            g.fill(surface.left(), py, surface.right(), py + 1, GRID);
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

    // ------------------------------------------------------------- barre du bas

    /**
     * Une case cliquable de la barre du bas. Le rendu et le clic <b>partagent la même
     * liste</b> : chacun recalculait ses abscisses de son côté, et le moindre libellé
     * traduit plus long décalait les clics d'une case sans que rien ne le dise.
     */
    private record BarChip(String label, int x, int width, boolean active, Runnable onClick) {
    }

    /**
     * Le sélecteur de taille de fenêtre, les commandes de zoom et les repères.
     *
     * <p>Une ancre et un pourcentage ne veulent rien dire tant qu'on ne les voit pas
     * bouger. Concevoir toujours à 320×180 revenait à écrire une mise en page adaptative
     * sans jamais redimensionner la fenêtre : on découvrait le résultat en jeu, chez
     * quelqu'un d'autre.
     *
     * <p>Les préréglages ne portent que leur <b>largeur</b> : six tailles écrites en
     * entier n'entrent pas dans une fenêtre de 640, et la hauteur s'en déduit — elles sont
     * toutes en 16:9. La taille courante, elle, est écrite en entier dans les repères.
     */
    private List<BarChip> barChips(Font font) {
        List<BarChip> chips = new java.util.ArrayList<>();
        int x = panels.canvasLeft() + 4
                + font.width(I18n.get("blueprint.designer.viewport")) + 6;
        var current = controller.viewportPreset();
        for (var viewport : ScreenCanvasController.Viewport.values()) {
            String label = String.valueOf(viewport.width());
            int w = font.width(label) + 6;
            chips.add(new BarChip(label, x, w, viewport == current,
                    () -> setViewport(viewport)));
            x += w;
        }
        String mine = I18n.get("blueprint.designer.viewport_mine");
        int mineWidth = font.width(mine) + 8;
        chips.add(new BarChip(mine, x, mineWidth, current == null,
                () -> setViewport(playerViewportWidth, playerViewportHeight)));
        x += mineWidth + 6;

        // Les commandes de zoom. Elles doublent la molette plutôt que de la remplacer :
        // la molette se découvre en essayant, un bouton se découvre en regardant.
        chips.add(new BarChip("−", x, font.width("−") + 6, false, () -> zoomBy(-1)));
        x += font.width("−") + 6;
        String percent = Math.round(surface.zoom() * 100) + "%";
        chips.add(new BarChip(percent, x, font.width(percent) + 6, false,
                () -> zoomTo(DesignCamera.ONE_TO_ONE)));
        x += font.width(percent) + 6;
        chips.add(new BarChip("+", x, font.width("+") + 6, false, () -> zoomBy(1)));
        x += font.width("+") + 8;
        String frame = I18n.get("blueprint.designer.zoom_fit");
        chips.add(new BarChip(frame, x, font.width(frame) + 8, false, () -> needsFit = true));
        x += font.width(frame) + 8;
        // Un raccourci qu'on ne peut pas découvrir n'existe que pour celui qui l'a écrit.
        String help = I18n.get("blueprint.designer.help");
        chips.add(new BarChip(help, x, font.width(help) + 8, helpVisible,
                () -> helpVisible = !helpVisible));
        return List.copyOf(chips);
    }

    private void renderViewportBar(GuiGraphics g, Font font) {
        int y = height - ROW - 2;
        g.drawString(font, I18n.get("blueprint.designer.viewport"),
                panels.canvasLeft() + 4, y, DIM_TEXT, false);
        for (BarChip chip : barChips(font)) {
            g.drawString(font, chip.label(), chip.x(), y, chip.active() ? SELECTED : TEXT, false);
        }

        // Les repères : où est le curseur, et ce que mesure la sélection. Poser une
        // valeur précise se faisait à l'aveugle, ou en ouvrant le panneau de droite.
        int right = width - panels.propertiesWidth() - 4;
        String readout = readout();
        g.drawString(font, readout, Math.max(panels.canvasLeft() + 4,
                right - font.width(readout)), y - ROW, DIM_TEXT, false);

        // Les packs dont l'écran dépend (10.5, AC5), déduits de ses textures. En rouge
        // ceux qui ne sont PAS installés ici : l'auteur voit ainsi, en concevant, ce que
        // verra celui à qui il donnera son menu sans le dossier qui va avec.
        Screen screen = controller.screen();
        if (screen != null && !screen.requiredPacks().isEmpty()) {
            var installed = fr.blueprint.client.pack.PackTextures.packs().keySet();
            int px = panels.canvasLeft() + 4;
            int py = y - ROW * 2;   // au-dessus des repères, qui occupent la ligne y − ROW
            String label = I18n.get("blueprint.designer.packs", "");
            g.drawString(font, label, px, py, DIM_TEXT, false);
            px += font.width(label) + 2;
            for (String pack : screen.requiredPacks()) {
                g.drawString(font, pack, px, py, installed.contains(pack) ? TEXT : INVALID, false);
                px += font.width(pack) + 6;
            }
        }
    }

    /** La taille simulée, la position du curseur, et le rectangle de la sélection. */
    private String readout() {
        StringBuilder out = new StringBuilder();
        out.append((int) controller.viewportWidth()).append('×')
                .append((int) controller.viewportHeight());
        if (surface.contains(mouseX, mouseY)) {
            out.append("   ").append((int) Math.floor(surface.toDesignX(mouseX)))
                    .append(", ").append((int) Math.floor(surface.toDesignY(mouseY)));
        }
        if (controller.selection().size() == 1) {
            ScreenLayout.Rect rect =
                    controller.rects().get(controller.selection().ids().iterator().next());
            if (rect != null) {
                out.append("   ").append(Math.round(rect.width())).append('×')
                        .append(Math.round(rect.height()));
            }
        }
        return out.toString();
    }

    /** Le clic dans la barre du bas ; faux si le point est ailleurs. */
    private boolean clickViewport(double mx, double my, Font font) {
        int y = height - ROW - 2;
        if (my < y || my >= y + ROW) {
            return false;
        }
        for (BarChip chip : barChips(font)) {
            if (mx >= chip.x() && mx < chip.x() + chip.width()) {
                chip.onClick().run();
                return true;
            }
        }
        return true;   // la barre absorbe le clic : rien derrière elle
    }

    private void setViewport(ScreenCanvasController.Viewport viewport) {
        controller.setViewport(viewport);
        needsFit = true;   // changer de fenêtre sans recadrer laisserait hors de la vue
    }

    private void setViewport(double w, double h) {
        controller.setViewport(w, h);
        needsFit = true;
    }

    /** Le zoom pivote sur le curseur s'il est sur la zone, sinon sur son centre. */
    private void zoomBy(int steps) {
        double[] pivot = pivot();
        camera.zoomBy(steps, pivot[0], pivot[1]);
    }

    private void zoomTo(double target) {
        double[] pivot = pivot();
        camera.zoomTo(target, pivot[0], pivot[1]);
    }

    private double[] pivot() {
        boolean onArea = mouseX >= areaLeft && mouseX < areaLeft + areaWidth
                && mouseY >= areaTop && mouseY < areaTop + areaHeight;
        return onArea
                ? new double[]{mouseX - areaLeft, mouseY - areaTop}
                : new double[]{areaWidth / 2.0, areaHeight / 2.0};
    }

    /** La taille réelle de la fenêtre du joueur, en unités — relevée à chaque image. */
    private int playerViewportWidth = Screen.SAFE_WIDTH;
    private int playerViewportHeight = Screen.SAFE_HEIGHT;

    // ------------------------------------------------------------------ palette

    private void renderPalette(GuiGraphics g, Font font) {
        int panelWidth = panels.paletteWidth();
        g.fill(0, top, panelWidth, height, PANEL_BACKGROUND);
        g.fill(panelWidth - 1, top, panelWidth, height, PANEL_BORDER);
        // Le chevron : replié, un panneau qui ne laisse aucune prise ne se rouvre qu'en
        // devinant qu'un raccourci existe.
        g.drawString(font, panels.paletteOpen() ? "‹" : "›",
                panelWidth - 5, top + 3, DIM_TEXT, false);
        if (!panels.paletteOpen()) {
            return;
        }

        int y = top + 3;
        g.drawString(font, font.plainSubstrByWidth(I18n.get("blueprint.designer.screens"), PALETTE_LABEL), 4, y, DIM_TEXT, false);
        y += ROW;
        for (String name : session.blueprint().screens().keySet()) {
            boolean active = name.equals(controller.screenName());
            g.drawString(font, font.plainSubstrByWidth(name, PALETTE_LABEL), 6, y,
                    active ? SELECTED : TEXT, false);
            y += ROW;
        }
        g.drawString(font, font.plainSubstrByWidth(I18n.get("blueprint.designer.add_screen"), PALETTE_LABEL), 6, y, DIM_TEXT, false);
        y += ROW;
        // Les actions de l'écran COURANT, sous sa liste : renommer, supprimer, et le
        // passage modal ↔ HUD, qui n'existaient nulle part — on pouvait créer un écran
        // et plus jamais le défaire.
        Screen current = controller.screen();
        boolean hud = current != null && current.hud();
        String mode = hud ? I18n.get("blueprint.designer.screen_hud")
                : I18n.get("blueprint.designer.screen_modal");
        g.drawString(font, font.plainSubstrByWidth(mode, PALETTE_LABEL), 6, y,
                hud ? SELECTED : DIM_TEXT, false);
        y += ROW;
        g.drawString(font, font.plainSubstrByWidth(I18n.get("blueprint.designer.rename_screen"), PALETTE_LABEL), 6, y, DIM_TEXT, false);
        y += ROW;
        g.drawString(font, font.plainSubstrByWidth(I18n.get("blueprint.designer.remove_screen"), PALETTE_LABEL), 6, y, DIM_TEXT, false);
        y = separator(g, y);

        g.drawString(font, font.plainSubstrByWidth(I18n.get("blueprint.designer.elements"),
                PALETTE_LABEL), 4, y, DIM_TEXT, false);
        y += ROW;
        for (ElementKind kind : ElementKind.values()) {
            g.drawString(font, font.plainSubstrByWidth(I18n.get(kindKey(kind)),
                    PALETTE_LABEL), 6, y, TEXT, false);
            y += ROW;
        }
        renderLayers(g, font, separator(g, y));
    }

    /**
     * Un filet entre deux sections de la palette. Elles se suivaient sans respiration :
     * « Screens », « Elements » et « Layers » formaient une seule colonne de mots qu'il
     * fallait lire en entier pour trouver où l'une finissait.
     */
    private int separator(GuiGraphics g, int y) {
        g.fill(4, y + SECTION_GAP / 2, PALETTE_WIDTH - 5, y + SECTION_GAP / 2 + 1, PANEL_BORDER);
        return y + SECTION_GAP;
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
        g.drawString(font, font.plainSubstrByWidth(I18n.get("blueprint.designer.layers"), PALETTE_LABEL), 4, startY, DIM_TEXT, false);
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

    // -------------------------------------------------------------- propriétés

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

        // Le retour à la ligne. Une case et non un champ : c'est un oui/non, et le taper
        // demanderait de connaître une syntaxe qu'aucun panneau n'affiche.
        boolean wraps = element.style().wrap();
        // Les deux clés sont passées EN DUR à I18n.get, et non choisies avant l'appel :
        // le détecteur de clés mortes lit les sources, et une clé calculée lui paraît
        // inutilisée — il l'aurait signalée à chaque build (leçon du même test en 9.4).
        String wrapLabel = wraps ? I18n.get("blueprint.designer.wrap.on")
                : I18n.get("blueprint.designer.wrap.off");
        rows.add(new Row(y, I18n.get("blueprint.designer.wrap"), java.util.List.of(
                new Chip(wrapLabel, 52, 24, wraps,
                        () -> apply(element.styled(element.style().withWrap(!wraps))))),
                null, null));
        y += ROW;

        if (element.kind().container()) {
            // Le défilement d'abord : c'est une propriété du conteneur lui-même, pas de
            // la façon dont il range, et elle vaut aussi en disposition absolue.
            rows.add(new Row(y, I18n.get("blueprint.designer.scroll"),
                    enumChips(fr.blueprint.core.graph.screen.LayoutSpec.Scroll.values(),
                            element.layout().scroll(), "blueprint.designer.scroll.",
                            axis -> apply(element.withLayout(
                                    element.layout().withScroll(axis)))), null, null));
            y += ROW;
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
        int left = width - panels.propertiesWidth();
        g.fill(left, top, width, height, PANEL_BACKGROUND);
        g.fill(left, top, left + 1, height, PANEL_BORDER);
        g.drawString(font, panels.propertiesOpen() ? "›" : "‹", left + 1, top + 3,
                DIM_TEXT, false);
        if (!panels.propertiesOpen()) {
            return;
        }

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
        // Le bouton du milieu, ou Espace + gauche : le déplacement de vue, exactement
        // comme dans l'onglet Graphe. Un seul jeu de réflexes pour les deux.
        if (e.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE
                || (spaceDown && e.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
            panning = true;
            return true;
        }
        if (clickViewport(mx, my, net.minecraft.client.Minecraft.getInstance().font)) {
            return true;
        }
        if (panels.onPaletteToggle(mx)) {
            panels = panels.withPalette(!panels.paletteOpen());
            return true;
        }
        if (panels.onPropertiesToggle(mx, width)) {
            panels = panels.withProperties(!panels.propertiesOpen());
            return true;
        }
        if (panels.inPalette(mx)) {
            return clickPalette(my);
        }
        if (panels.inProperties(mx, width)) {
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
        y += SECTION_GAP + ROW;
        for (ElementKind kind : ElementKind.values()) {
            if (my >= y && my < y + ROW) {
                if (controller.addElement(kind, dropX(), dropY()) == null) {
                    reportRefusal();
                }
                return true;
            }
            y += ROW;
        }
        y += SECTION_GAP + ROW;
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

    /**
     * Où poser un élément : le centre de ce qu'on <b>voit</b>.
     *
     * <p>C'était le centre des 320×180 garantis, en dur, quelle que soit la taille
     * simulée. Sur un canevas de 1920×1080, l'élément atterrissait donc au douzième
     * supérieur gauche — et zoomé sur un coin, hors de la vue, ce qui donne l'impression
     * que le clic n'a rien fait.
     */
    private double dropX() {
        return camera.toUnitX(areaWidth / 2.0);
    }

    private double dropY() {
        return camera.toUnitY(areaHeight / 2.0);
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
        double local = mx - (width - panels.propertiesWidth());
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
        if (panning) {
            camera.panByScreen(dx, dy);
            return true;
        }
        if (controller.gesture() == ScreenCanvasController.Gesture.NONE) {
            return false;
        }
        controller.drag(surface.toDesignX(e.x()), surface.toDesignY(e.y()));
        return true;
    }

    public boolean mouseReleased(MouseButtonEvent e) {
        if (panning) {
            panning = false;
            return true;
        }
        if (controller.gesture() == ScreenCanvasController.Gesture.NONE) {
            return false;
        }
        controller.release();
        reportRefusal();
        return true;
    }

    /**
     * La molette zoome, pivot sous le curseur. Elle n'arrivait pas jusqu'ici : l'écran
     * de l'éditeur ne la routait que vers le canevas de nœuds.
     */
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (amount == 0 || my < top) {
            return false;
        }
        this.mouseX = mx;
        this.mouseY = my;
        // Au-dessus d'un panneau DÉFILANT, la molette le fait défiler — c'est le geste
        // qu'on essaie, et zoomer à la place donnerait l'impression que le panneau n'en
        // est pas un. Ailleurs, elle zoome. Le zoom reste atteignable partout dans la
        // marge, et les boutons de la barre du bas ne dépendent pas du survol.
        if (surface.contains(mx, my)) {
            String panel = controller.scrollableAt(surface.toDesignX(mx), surface.toDesignY(my));
            Screen current = controller.screen();
            ScreenElement container = panel == null || current == null
                    ? null : current.element(panel);
            // La MÊME règle d'axe qu'en jeu : Maj vise l'horizontal, mais un panneau à un
            // seul axe répond sans qu'on ait à le savoir.
            if (container != null && controller.scrollBy(panel,
                    -Math.signum(amount) * PANEL_SCROLL_STEP,
                    ScreenLayout.scrollVertical(container, shiftHeld))) {
                return true;
            }
        }
        zoomBy(amount > 0 ? 1 : -1);
        return true;
    }

    /** Un cran de molette dans un panneau, en unités : trois lignes de texte. */
    private static final double PANEL_SCROLL_STEP = 27;

    private void reportRefusal() {
        Diagnostic refusal = controller.takeRefusal();
        message = refusal == null ? null
                : I18n.get(refusal.translationKey(), refusal.args().toArray());
    }

    // ------------------------------------------------------------------ clavier

    public boolean keyPressed(KeyEvent e) {
        if (e.key() == GLFW.GLFW_KEY_LEFT_SHIFT || e.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            shiftHeld = true;
            return false;   // Maj ne fait rien seule : elle qualifie les autres gestes
        }
        if (e.key() == GLFW.GLFW_KEY_SPACE && renamingScreen == null
                && properties.editing() == null) {
            spaceDown = true;
            return true;
        }
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
            case GLFW.GLFW_KEY_F1 -> {
                helpVisible = !helpVisible;
                yield true;
            }
            // Le cadrage et les crans de zoom, aux mêmes touches que l'onglet Graphe.
            case GLFW.GLFW_KEY_F -> {
                needsFit = true;
                yield true;
            }
            case GLFW.GLFW_KEY_EQUAL -> {
                zoomBy(1);
                yield true;
            }
            case GLFW.GLFW_KEY_MINUS -> {
                zoomBy(-1);
                yield true;
            }
            case GLFW.GLFW_KEY_0 -> {
                if (!e.hasControlDown()) {
                    yield false;
                }
                zoomTo(DesignCamera.ONE_TO_ONE);
                yield true;
            }
            // Replier les deux panneaux : un tiers de la largeur rendu au canevas, le
            // temps de placer, et retrouvé d'une même touche pour régler.
            case GLFW.GLFW_KEY_TAB -> {
                panels = panels.toggledBoth();
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

    /** Sans cela, Espace resterait enfoncé pour toujours et le clic ne poserait plus rien. */
    public boolean keyReleased(KeyEvent e) {
        if (e.key() == GLFW.GLFW_KEY_LEFT_SHIFT || e.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            shiftHeld = false;
            return false;
        }
        if (e.key() == GLFW.GLFW_KEY_SPACE) {
            spaceDown = false;
            return true;
        }
        return false;
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
