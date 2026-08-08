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
    /** Fond de la ligne active : le liseré du panneau des variables, adouci. */
    private static final int SELECTED_ROW = 0xFF2F3A55;

    /**
     * L'abscisse de la colonne des valeurs, dans le panneau de droite.
     *
     * <p>Une seule source : le libellé s'arrête ici, la valeur commence ici. Les deux la
     * calculaient séparément, et le libellé se peignait par-dessus la valeur.
     */
    private static final int VALUE_LEFT = 52;
    private static final int INVALID = 0xFFF7768E;

    /** Le fond d'un champ de saisie : plus sombre que le panneau, donc creux. */
    private static final int FIELD_TROUGH = 0xFF121316;
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

    /**
     * Ce sur quoi cadrer : les éléments posés, ou la fenêtre garantie s'il n'y en a pas.
     *
     * <p>320×180 quand l'écran est vide, parce que c'est ce qu'on dessine quand on ne
     * dessine rien encore — et non 1920×1080, qui est la place disponible et non le sujet.
     *
     * @return {@code [gauche, haut, largeur, hauteur]}, en unités.
     */
    private double[] contentBounds() {
        var rects = controller.rects();
        if (rects.isEmpty()) {
            return new double[] {0, 0, Screen.SAFE_WIDTH, Screen.SAFE_HEIGHT};
        }
        double left = Double.MAX_VALUE;
        double topUnits = Double.MAX_VALUE;
        double right = -Double.MAX_VALUE;
        double bottom = -Double.MAX_VALUE;
        for (var rect : rects.values()) {
            left = Math.min(left, rect.x());
            topUnits = Math.min(topUnits, rect.y());
            right = Math.max(right, rect.x() + rect.width());
            bottom = Math.max(bottom, rect.y() + rect.height());
        }
        return new double[] {left, topUnits, right - left, bottom - topUnits};
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
            double[] box = contentBounds();
            camera.fitInto(areaWidth, areaHeight, box[0], box[1], box[2], box[3]);
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
        ScreenElement selected = screen == null || controller.selection().size() != 1 ? null
                : screen.element(controller.selection().ids().iterator().next());
        // Changer d'élément referme la liste des variables : la laisser ouverte donnerait
        // un panneau différent selon ce qu'on a fait juste avant.
        if (selected != properties.element()
                && (selected == null || properties.element() == null
                        || !selected.name().equals(properties.element().name()))) {
            choosingVariable = false;
        }
        properties.select(selected);
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
            // AU-DESSUS des pastilles, pas dessus : à height − ROW le message se peignait
            // à deux pixels de la barre et la recouvrait, si bien qu'un refus effaçait les
            // commandes de zoom au moment précis où l'on cherchait à comprendre.
            g.drawString(font, message, panels.canvasLeft() + 4, height - ROW * 2 - 2,
                    INVALID, false);
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
        // Les six alignements, en toutes lettres et cliquables. Ils n'existaient qu'au
        // PAVÉ NUMÉRIQUE — inaccessibles sur un portable qui n'en a pas, et le fichier
        // l'admettait : « les six touches d'alignement, en particulier, ne se devinent
        // pas ». Six glyphes de trois pixels valent mieux que six touches introuvables ;
        // les raccourcis restent, ils ne sont plus le seul chemin.
        x += 6;
        for (var align : ScreenCanvasController.Align.values()) {
            String glyph = alignGlyph(align);
            int w = font.width(glyph) + 5;
            chips.add(new BarChip(glyph, x, w, false, () -> align(align)));
            x += w;
        }
        x += 6;
        // Un raccourci qu'on ne peut pas découvrir n'existe que pour celui qui l'a écrit.
        String help = I18n.get("blueprint.designer.help");
        chips.add(new BarChip(help, x, font.width(help) + 8, helpVisible,
                () -> helpVisible = !helpVisible));
        return List.copyOf(chips);
    }

    /**
     * Le signe d'un alignement. Des <b>flèches et des barres</b>, pas des mots : « Aligner
     * à gauche » dans une barre qui porte déjà quatorze commandes ne laisserait de place
     * pour rien d'autre, et un pictogramme d'alignement se lit sans traduction.
     */
    private static String alignGlyph(ScreenCanvasController.Align align) {
        return switch (align) {
            case LEFT -> "⊢";
            case CENTER_X -> "⊹";
            case RIGHT -> "⊣";
            case TOP -> "⊤";
            case CENTER_Y -> "⊸";
            case BOTTOM -> "⊥";
        };
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

        // Les diagnostics de l'écran, à GAUCHE de la ligne des repères, qui est alignée à
        // droite et laisse la place libre. Ils ne s'affichaient que dans l'onglet Graphe —
        // le code le note déjà à propos du seul cas qu'il avait traité, le cerne de
        // débordement : « c'est ici, au moment du geste, que l'information sert ; après
        // coup, elle arrive sous forme de rapport de bug d'un joueur en GUI scale 4 ».
        String issue = firstIssue();
        if (issue != null) {
            g.drawString(font, font.plainSubstrByWidth(issue,
                            right - panels.canvasLeft() - font.width(readout) - 12),
                    panels.canvasLeft() + 4, y - ROW, INVALID, false);
        }

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

    /**
     * Le premier reproche que le validateur fait à cet écran, ou {@code null}.
     *
     * <p>Un seul, et le premier : la barre n'a qu'une ligne, et un auteur qui en corrige
     * un voit apparaître le suivant. Une liste dépliable serait un panneau de plus dans
     * une fenêtre qui en compte déjà trois.
     *
     * <p>La validation est <b>débouncée</b> par {@link fr.blueprint.client.editor.DiagnosticsState},
     * comme dans l'onglet Graphe. La relancer à chaque image referait le défaut que la
     * 10.14 a corrigé — une passe de disposition rejouée huit fois par image.
     */
    private @Nullable String firstIssue() {
        if (screenIssues.shouldValidate()) {
            screenIssues.accept(fr.blueprint.core.graph.GraphValidator
                    .validate(session.blueprint(), lookup,
                            fr.blueprint.client.net.BlueprintNet.limits()).diagnostics());
        }
        String name = controller.screenName();
        for (var diagnostic : screenIssues.report()) {
            if (concerns(diagnostic, name)) {
                return I18n.get(diagnostic.translationKey(), diagnostic.args().toArray());
            }
        }
        return null;
    }

    /** Ce diagnostic parle-t-il de l'écran ouvert ? */
    private static boolean concerns(fr.blueprint.core.graph.Diagnostic diagnostic, String name) {
        return switch (diagnostic.target()) {
            case fr.blueprint.core.graph.Diagnostic.Target.ElementTarget t ->
                    t.screen().equals(name);
            case fr.blueprint.core.graph.Diagnostic.Target.ScreenTarget t ->
                    t.screen().equals(name);
            default -> false;
        };
    }

    private final fr.blueprint.client.editor.DiagnosticsState screenIssues =
            new fr.blueprint.client.editor.DiagnosticsState(System::currentTimeMillis);

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
        // La barre ne s'étend pas sous les colonnes : elle commence où commence le
        // canevas et s'arrête où il finit. Elle absorbait le clic sur TOUTE la largeur,
        // ce qui rendait morte la dernière rangée de la palette et des propriétés — une
        // bande de douze pixels où plus rien ne répondait, sans que rien ne le dise.
        if (my < y || my >= y + ROW
                || mx < panels.canvasLeft() || mx >= width - panels.propertiesWidth()) {
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
        // Tout ce qui décide vit dans DesignerPalette ; ici il ne reste que la peinture.
        // Les ordonnées se calculaient des deux côtés, et les deux calculs avaient divergé
        // à un endroit tout en tombant d'accord partout ailleurs — ce qui rendait le
        // défaut invisible. Une seule source, comme DesignerPanels le fait des largeurs.
        DesignerPalette.Model model = paletteModel();
        for (DesignerPalette.Row row : DesignerPalette.rows(model, top, height,
                paletteScroll.offset(DesignerPalette.contentRows(model),
                        DesignerPalette.visibleRows(top, height)))) {
            paintRow(g, font, row);
        }
    }

    /** Ce que la colonne a besoin de savoir, relu à chaque image. */
    private DesignerPalette.Model paletteModel() {
        Screen screen = controller.screen();
        List<DesignerPalette.Layer> layers = new java.util.ArrayList<>();
        if (screen != null) {
            for (ScreenElement element : screen.elements().values()) {
                layers.add(new DesignerPalette.Layer(element.name(), element.parent(),
                        element.visible()));
            }
        }
        return new DesignerPalette.Model(
                List.copyOf(session.blueprint().screens().keySet()), controller.screenName(),
                renamingScreen, screenBuffer, screen != null && screen.hud(),
                layers, List.copyOf(controller.selection().ids()),
                List.copyOf(collapsedLayers));
    }

    private void paintRow(GuiGraphics g, Font font, DesignerPalette.Row row) {
        int w = DesignerPalette.WIDTH;
        switch (row.kind()) {
            case SECTION -> {
                g.fill(0, row.y(), w - 1, row.y() + 1,
                        "".equals(dropTarget) && row.label().endsWith(".layers")
                                ? SELECTED : PANEL_BORDER);
                g.drawString(font, font.plainSubstrByWidth(
                                I18n.get(row.label()), w - 18),
                        4, row.y() + 3, DIM_TEXT, false);
                if ("blueprint.designer.screens".equals(row.label())) {
                    g.drawString(font, "+", w - 12, row.y() + 3, TEXT, false);
                }
            }
            case GROUP -> g.drawString(font, font.plainSubstrByWidth(
                            I18n.get(row.label()), w - 8),
                    4, row.y() + 2, DIM_TEXT, false);
            case EMPTY -> g.drawString(font, font.plainSubstrByWidth(
                            I18n.get(row.label()), w - 8),
                    4, row.y() + 2, DIM_TEXT, false);
            case SCREEN -> {
                if (row.selected()) {
                    g.fill(0, row.y(), w - 1, row.y() + DesignerPalette.ROW, SELECTED_ROW);
                }
                int room = row.selected() ? w - 40 : w - 8;
                g.drawString(font, font.plainSubstrByWidth(row.label(), room), 4, row.y() + 2,
                        row.selected() ? SELECTED : TEXT, false);
                if (row.selected()) {
                    // Les trois actions de la ligne active, au même endroit que celles du
                    // panneau des variables et de celui des fonctions.
                    g.drawString(font, hudMark(), w - 32, row.y() + 2, DIM_TEXT, false);
                    g.drawString(font, "✎", w - 22, row.y() + 2, DIM_TEXT, false);
                    g.drawString(font, "×", w - 12, row.y() + 2, DIM_TEXT, false);
                }
            }
            case ELEMENT -> {
                ElementKind kind = row.element();
                kindGlyph(g, kind, 8, row.y() + DesignerPalette.ROW / 2, kindColor(kind));
                g.drawString(font, font.plainSubstrByWidth(I18n.get(kindKey(kind)), w - 20),
                        16, row.y() + 2, TEXT, false);
            }
            case LAYER -> {
                if (row.selected()) {
                    g.fill(0, row.y(), w - 1, row.y() + DesignerPalette.ROW, SELECTED_ROW);
                }
                // La cible d'un reparentage en cours : un liseré, pas un fond. Elle se
                // superpose à la sélection sans l'effacer — celle qu'on traîne EST la
                // sélection, et perdre son repère au milieu du geste serait déroutant.
                if (row.name() != null && row.name().equals(dropTarget)) {
                    g.fill(0, row.y(), w - 1, row.y() + 1, SELECTED);
                    g.fill(0, row.y() + DesignerPalette.ROW - 1, w - 1,
                            row.y() + DesignerPalette.ROW, SELECTED);
                }
                if (row.expandable()) {
                    g.drawString(font, row.expanded() ? "▾" : "▸",
                            2 + row.depth() * 4, row.y() + 2, DIM_TEXT, false);
                }
                // L'œil dit d'un coup ce qui est masqué — sans lui, un élément invisible
                // à la conception se confond avec un élément absent.
                g.drawString(font, row.visible() ? "◉" : "○", DesignerPalette.eyeX(row),
                        row.y() + 2, row.visible() ? TEXT : DIM_TEXT, false);
                int nameX = DesignerPalette.nameX(row);
                g.drawString(font, font.plainSubstrByWidth(row.label(), w - nameX - 4),
                        nameX, row.y() + 2, row.selected() ? SELECTED : TEXT, false);
            }
            default -> {
            }
        }
    }

    /** « 1.5 » et non « 1.5000 » : le facteur part dans un libellé lu par un humain. */
    private static String trimScale(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value)
                : String.valueOf(value);
    }

    private String hudMark() {
        Screen screen = controller.screen();
        return screen != null && screen.hud() ? "H" : "M";
    }

    /**
     * La teinte d'un type, par famille — conteneur, affichage, interactif.
     *
     * <p>Le même partage que les groupes de la colonne, et la même raison : ce que le
     * modèle sait déjà dire d'un type vaut mieux qu'un classement inventé.
     */
    private static int kindColor(ElementKind kind) {
        return switch (DesignerPalette.groupOf(kind)) {
            case CONTAINER -> 0xFF7DCFFF;
            case DISPLAY -> 0xFFE5C07B;
            case INTERACTIVE -> 0xFF9ECE6A;
        };
    }

    /**
     * Un pictogramme de 7×7 par <b>type</b>, sur le patron de {@code NodeWidget.categoryGlyph}.
     *
     * <p>Douze mots en colonne se lisent tous pour en trouver un. Une forme se reconnaît de
     * plus loin qu'un nom, et c'est ce que le canevas de nœuds fait de ses catégories
     * depuis la 5.13.
     *
     * <p>Mais un pictogramme par <i>famille</i> n'en était pas un : cinq types d'affichage
     * portaient le même, et la colonne se relisait mot à mot comme avant. La teinte dit la
     * famille — c'est ce qu'une couleur sait faire —, la forme dit l'élément.
     *
     * <p>Le {@code switch} est exhaustif : un treizième type ne compilera pas tant qu'on ne
     * lui aura pas dessiné le sien.
     */
    private static void kindGlyph(GuiGraphics g, ElementKind kind, int cx, int cy, int color) {
        switch (kind) {
            case PANEL -> {                  // un cadre évidé
                g.fill(cx - 3, cy - 3, cx + 4, cy + 4, color);
                g.fill(cx - 2, cy - 2, cx + 3, cy + 3, PANEL_BACKGROUND);
            }
            case LABEL -> {                  // trois lignes de texte, la dernière courte
                for (int dy = -2; dy <= 2; dy += 2) {
                    g.fill(cx - 3, cy + dy, cx + (dy == 2 ? 1 : 4), cy + dy + 1, color);
                }
            }
            case IMAGE -> {                  // un cadre, et une montagne dedans
                g.fill(cx - 3, cy - 3, cx + 4, cy + 4, color);
                g.fill(cx - 2, cy - 2, cx + 3, cy + 3, PANEL_BACKGROUND);
                g.fill(cx - 2, cy + 1, cx + 3, cy + 3, color);
                g.fill(cx - 1, cy, cx + 2, cy + 1, color);
            }
            case PROGRESS -> {               // une jauge remplie aux deux tiers
                g.fill(cx - 3, cy - 2, cx + 4, cy + 3, color);
                g.fill(cx - 2, cy - 1, cx + 3, cy + 2, PANEL_BACKGROUND);
                g.fill(cx - 2, cy - 1, cx + 1, cy + 2, color);
            }
            case SLOT -> {                   // la case d'inventaire : un bord épais
                g.fill(cx - 3, cy - 3, cx + 4, cy + 4, color);
                g.fill(cx - 1, cy - 1, cx + 2, cy + 2, PANEL_BACKGROUND);
            }
            case ENTITY_PREVIEW -> {         // une tête et des épaules
                g.fill(cx - 1, cy - 3, cx + 2, cy, color);
                g.fill(cx - 3, cy + 1, cx + 4, cy + 4, color);
            }
            case BUTTON -> {                 // une touche, et son ombre portée
                g.fill(cx - 3, cy - 2, cx + 3, cy + 2, color);
                g.fill(cx - 2, cy + 2, cx + 4, cy + 3, color);
            }
            case INPUT -> {                  // un curseur sur une ligne de saisie
                g.fill(cx - 3, cy + 2, cx + 4, cy + 3, color);
                g.fill(cx - 1, cy - 3, cx, cy + 2, color);
                g.fill(cx - 2, cy - 3, cx + 1, cy - 2, color);
            }
            case TOGGLE -> {                 // une pastille poussée à droite
                g.fill(cx - 3, cy - 2, cx + 4, cy + 3, color);
                g.fill(cx - 2, cy - 1, cx + 1, cy + 2, PANEL_BACKGROUND);
            }
            case SLIDER -> {                 // un rail, et sa poignée au milieu
                g.fill(cx - 3, cy, cx + 4, cy + 1, color);
                g.fill(cx - 1, cy - 3, cx + 2, cy + 4, color);
            }
            case LIST -> {                   // des rangées séparées, toute la largeur
                for (int dy = -3; dy <= 2; dy += 3) {
                    g.fill(cx - 3, cy + dy, cx + 4, cy + dy + 2, color);
                }
            }
            case DROPDOWN -> {               // une boîte, et le chevron qui la déroule
                g.fill(cx - 3, cy - 3, cx + 4, cy - 1, color);
                for (int i = 0; i < 3; i++) {
                    g.fill(cx - 2 + i, cy + i, cx + 3 - i, cy + i + 1, color);
                }
            }
        }
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
    /**
     * @param line la rangée de la pastille dans son groupe : 0 sauf quand une énumération
     *             trop large passe à la ligne plutôt que de couper ses mots.
     */
    private record Chip(String label, int x, int width, boolean active, Runnable onClick,
                        int line) {

        Chip(String label, int x, int width, boolean active, Runnable onClick) {
            this(label, x, width, active, onClick, 0);
        }
    }

    private record Row(int y, String label, java.util.List<Chip> chips,
                       ElementPropertiesState.@Nullable Field field, @Nullable String value,
                       ElementPropertiesState.@Nullable Section section) {

        Row(int y, String label, java.util.List<Chip> chips,
            ElementPropertiesState.@Nullable Field field, @Nullable String value) {
            this(y, label, chips, field, value, null);
        }
    }

    /**
     * Les sections repliées. Un état d'affichage : rien à enregistrer.
     *
     * <p>Toutes ouvertes au départ. Replier par défaut ferait chercher où sont passés les
     * réglages qu'on voyait la veille ; les laisser ouvertes donne le panneau d'avant, en
     * mieux rangé, et le repli devient un choix plutôt qu'une découverte.
     */
    private final java.util.Set<ElementPropertiesState.Section> collapsedSections =
            java.util.EnumSet.noneOf(ElementPropertiesState.Section.class);

    /**
     * Poser une rangée et rendre le y de la suivante.
     *
     * <p>Une rangée dont les pastilles passent à la ligne en occupe deux : avancer d'une
     * seule ferait peindre la suivante par-dessus. Le seul moyen sûr est que personne
     * n'écrive { y += ROW} après une rangée à pastilles.
     */
    private static int addRow(java.util.List<Row> rows, Row row) {
        rows.add(row);
        return row.y() + rowHeight(row);
    }

    /**
     * La hauteur d'une rangée : une de plus par ligne de pastilles supplémentaire.
     *
     * <p>Le rendu, le clic ET l'avance du curseur la lisent <b>ici</b>. Trois arithmétiques
     * séparées, c'est le piège qui a déjà mordu ce panneau : ce qui se dessine et ce qui se
     * clique cessent alors de désigner la même chose.
     */
    private static int rowHeight(Row row) {
        int lines = 1;
        for (Chip chip : row.chips()) {
            lines = Math.max(lines, chip.line() + 1);
        }
        return lines * ROW;
    }

    /** Un en-tête de section : son chevron, son titre, et rien d'autre. */
    private Row sectionRow(int y, ElementPropertiesState.Section section) {
        String mark = collapsedSections.contains(section) ? "▸ " : "▾ ";
        return new Row(y, mark + I18n.get(section.key()), java.util.List.of(),
                null, null, section);
    }

    /**
     * Rien de sélectionné : le panneau décrit l'<b>écran</b>, pas le vide.
     *
     * <p>Il affichait « Sélectionnez un élément » et rien d'autre — une colonne de cent
     * vingt-huit pixels réservée à une phrase. Or il y a quelque chose à dire : l'écran
     * qu'on est en train de dessiner, sa nature et sa taille. C'est le précédent du panneau
     * de détails du graphe, qui décrit le blueprint quand aucun nœud n'est choisi.
     *
     * <p>La nature — modal ou HUD — se règle ici plutôt que dans la colonne de gauche, où
     * elle tenait dans une lettre. « Modal (Échap ferme) » se lit ; « M » se devine.
     */
    private java.util.List<Row> screenRows(Screen screen) {
        java.util.List<Row> rows = new java.util.ArrayList<>();
        int y = top + 3;
        rows.add(new Row(y, I18n.get("blueprint.designer.screen"), java.util.List.of(),
                null, screen.name()));
        y += ROW;
        rows.add(new Row(y, "", java.util.List.of(new Chip(
                I18n.get(screen.hud() ? "blueprint.designer.screen_hud"
                        : "blueprint.designer.screen_modal"),
                4, PROPERTIES_WIDTH - 8, screen.hud(), () -> {
                    if (!controller.toggleHud()) {
                        reportRefusal();
                    }
                })), null, null));
        y += ROW;
        rows.add(new Row(y, I18n.get("blueprint.designer.elements"), java.util.List.of(),
                null, String.valueOf(screen.elements().size())));
        return rows;
    }

    /**
     * Les rangées <b>visibles</b> du panneau, décalées du défilement.
     *
     * <p>Le panneau ne défilait ni ne se découpait : au-delà de la hauteur de la fenêtre,
     * les rangées étaient peintes dehors et les clics les atteignaient quand même. Un
     * élément lié dans un blueprint à quelques variables passait trente rangées sans
     * peine — le cas normal, pas le cas extrême.
     */
    private java.util.List<Row> visiblePropertyRows() {
        java.util.List<Row> all = propertyRows();
        int visible = Math.max(1, (height - top - ROW * 2) / ROW);
        int offset = propertiesScroll.offset(all.size(), visible) * ROW;
        java.util.List<Row> out = new java.util.ArrayList<>();
        for (Row row : all) {
            int y = row.y() - offset;
            if (y < top || y + ROW > height - ROW) {
                continue;
            }
            out.add(new Row(y, row.label(), row.chips(), row.field(), row.value()));
        }
        return out;
    }

    private final fr.blueprint.client.editor.PanelScroll propertiesScroll =
            new fr.blueprint.client.editor.PanelScroll();

    private java.util.List<Row> propertyRows() {
        ScreenElement element = properties.element();
        Screen screen = controller.screen();
        if (screen == null) {
            return java.util.List.of();
        }
        if (element == null) {
            return screenRows(screen);
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

        ElementPropertiesState.Section open = null;
        for (ElementPropertiesState.Field field : ElementPropertiesState.Field.values()) {
            if (!ElementPropertiesState.applies(element, field, arranged)) {
                continue;
            }
            // L'en-tête de section, posé au premier champ qui l'habite : une section sans
            // champ applicable ne s'annonce donc jamais. Un titre suivi de rien serait la
            // même promesse creuse qu'un champ sans effet.
            ElementPropertiesState.Section section = ElementPropertiesState.sectionOf(field);
            // Les champs de disposition attendent leur bloc, plus bas : les émettre ici
            // les séparerait du défilement et du mode, qui sont la même décision.
            if (section == ElementPropertiesState.Section.LAYOUT) {
                continue;
            }
            if (section != open) {
                open = section;
                rows.add(sectionRow(y, section));
                y += ROW;
                if (collapsedSections.contains(section)) {
                    continue;
                }
            } else if (collapsedSections.contains(section)) {
                continue;
            }
            // Chaque axe de taille porte ses quatre modes : les taper en texte demandait
            // de connaître une syntaxe qu'aucun panneau n'affichait.
            boolean size = field == ElementPropertiesState.Field.WIDTH
                    || field == ElementPropertiesState.Field.HEIGHT;
            boolean editing = properties.isEditing(field);
            // La valeur d'un axe en mode « Ajuster » ne veut rien dire : la taille vient
            // des enfants. sizeValueMatters existait et n'était appelé nulle part, si bien
            // qu'on pouvait taper un nombre sans effet — ce que le panneau reproche
            // ailleurs aux champs sans objet.
            String value = size && !properties.sizeValueMatters(
                    field == ElementPropertiesState.Field.WIDTH)
                    ? null
                    : editing ? properties.buffer() + "_" : properties.valueOf(field);
            if (ElementPropertiesState.needsOwnLine(field)) {
                // Le libellé seul, puis la valeur sur toute la largeur. Sur une seule
                // ligne il lui restait soixante-douze pixels — une douzaine de
                // caractères — et l'auteur éditait un nom qu'il ne pouvait pas lire.
                rows.add(new Row(y, I18n.get(fieldKey(field)), java.util.List.of(),
                        null, null));
                y += ROW;
                rows.add(new Row(y, "", java.util.List.of(), field, value));
            } else {
                rows.add(new Row(y, I18n.get(fieldKey(field)), java.util.List.of(),
                        field, value));
            }
            y += ROW;
            if (size) {
                // Les quatre modes sur LEUR rangée, pleine largeur. Sur la même ligne que
                // le libellé, la dernière finissait à 123 sur un panneau de 128 et la
                // valeur se peignait à 126 : elle était de fait invisible.
                Row modes = new Row(y, "",
                        sizeModeChips(element, field == ElementPropertiesState.Field.WIDTH),
                        null, null);
                rows.add(modes);
                y += rowHeight(modes);
            }
        }

        // Le retour à la ligne. Une case et non un champ : c'est un oui/non, et le taper
        // demanderait de connaître une syntaxe qu'aucun panneau n'affiche.
        // Le retour à la ligne et la taille du texte continuent l'APPARENCE : ils sont de
        // la même famille que la couleur et la marge, et leur donner un en-tête à eux
        // couperait la section en deux pour deux rangées.
        boolean appearanceOpen =
                !collapsedSections.contains(ElementPropertiesState.Section.APPEARANCE);
        boolean wraps = element.style().wrap();
        // Les deux clés sont passées EN DUR à I18n.get, et non choisies avant l'appel :
        // le détecteur de clés mortes lit les sources, et une clé calculée lui paraît
        // inutilisée — il l'aurait signalée à chaque build (leçon du même test en 9.4).
        String wrapLabel = wraps ? I18n.get("blueprint.designer.wrap.on")
                : I18n.get("blueprint.designer.wrap.off");
        if (appearanceOpen) {
            rows.add(new Row(y, I18n.get("blueprint.designer.wrap"), java.util.List.of(
                    new Chip(wrapLabel, 52, 24, wraps,
                            () -> apply(element.styled(element.style().withWrap(!wraps))))),
                    null, null));
            y += ROW;
        }

        // La taille du texte : des pastilles et non un champ. La police de Minecraft
        // n'existe qu'à une taille et s'agrandit par un facteur ; proposer de taper un
        // nombre laisserait croire à un réglage continu, alors qu'à ×1,3 les traits de la
        // police tombent entre deux pixels et le texte devient flou.
        if (appearanceOpen && ElementPropertiesState.showsAnyText(element.kind())) {
            double current = element.style().textScale();
            java.util.List<Chip> scales = new java.util.ArrayList<>();
            int step = (PROPERTIES_WIDTH - 8) / fr.blueprint.core.graph.screen.ElementStyle.SCALES.length;
            int cx = 4;
            for (double option : fr.blueprint.core.graph.screen.ElementStyle.SCALES) {
                double chosen = option;
                scales.add(new Chip("×" + trimScale(option), cx, step - 1,
                        Math.abs(current - option) < 1e-6,
                        () -> apply(element.styled(element.style().withTextScale(chosen)))));
                cx += step;
            }
            rows.add(new Row(y, I18n.get("blueprint.designer.text_scale"),
                    java.util.List.of(), null, null));
            y += ROW;
            rows.add(new Row(y, "", scales, null, null));
            y += ROW;
        }

        if (element.kind().container()) {
            rows.add(sectionRow(y, ElementPropertiesState.Section.LAYOUT));
            y += ROW;
        }
        if (element.kind().container()
                && !collapsedSections.contains(ElementPropertiesState.Section.LAYOUT)) {
            // Le défilement d'abord : c'est une propriété du conteneur lui-même, pas de
            // la façon dont il range, et elle vaut aussi en disposition absolue.
            y = addRow(rows, new Row(y, I18n.get("blueprint.designer.scroll"),
                    enumChips(fr.blueprint.core.graph.screen.LayoutSpec.Scroll.values(),
                            element.layout().scroll(), "blueprint.designer.scroll.",
                            axis -> apply(element.withLayout(
                                    element.layout().withScroll(axis)))), null, null));
            y = addRow(rows, new Row(y, I18n.get("blueprint.designer.layout"),
                    enumChips(fr.blueprint.core.graph.screen.LayoutSpec.Mode.values(),
                            element.layout().mode(), "blueprint.designer.layout.",
                            mode -> apply(properties.setLayoutMode(mode))), null, null));
            if (element.arranges()) {
                y = addRow(rows, new Row(y, I18n.get("blueprint.designer.main"),
                        enumChips(fr.blueprint.core.graph.screen.LayoutSpec.Distribute.values(),
                                element.layout().main(), "blueprint.designer.main.",
                                main -> apply(properties.setLayoutMain(main))), null, null));
                y = addRow(rows, new Row(y, I18n.get("blueprint.designer.cross"),
                        enumChips(fr.blueprint.core.graph.screen.LayoutSpec.Cross.values(),
                                element.layout().cross(), "blueprint.designer.cross.",
                                cross -> apply(properties.setLayoutCross(cross))), null, null));
            }
            for (var field : java.util.List.of(ElementPropertiesState.Field.GAP,
                    ElementPropertiesState.Field.CROSS_GAP,
                    ElementPropertiesState.Field.COLUMNS)) {
                if (!ElementPropertiesState.applies(element, field, arranged)) {
                    continue;
                }
                rows.add(new Row(y, I18n.get(fieldKey(field)), java.util.List.of(), field,
                        properties.isEditing(field) ? properties.buffer() + "_"
                                : properties.valueOf(field)));
                y += ROW;
            }
        }

        // Les réglages propres au type (10.8). Chacun n'apparaît que là où il agit : un
        // « pas » sur une étiquette ou une « hauteur de ligne » sur un bouton seraient
        // des champs qu'on remplit sans que rien ne change.
        java.util.List<ElementPropertiesState.Field> richFields = optionFieldsOf(element.kind());
        if (!richFields.isEmpty()) {
            rows.add(sectionRow(y, ElementPropertiesState.Section.OPTIONS));
            y += ROW;
        }
        if (!richFields.isEmpty()
                && !collapsedSections.contains(ElementPropertiesState.Section.OPTIONS)) {
            if (element.kind() == ElementKind.INPUT) {
                y = addRow(rows, new Row(y, I18n.get("blueprint.designer.filter"),
                        enumChips(fr.blueprint.core.graph.screen.ElementOptions.InputFilter.values(),
                                element.options().filter(), "blueprint.designer.filter.",
                                filter -> apply(properties.setFilter(filter))), null, null));
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
        //
        // Repliée par DÉFAUT quand l'élément n'est lié à rien, et c'est le seul repli par
        // défaut du panneau : cette section pose une rangée par variable du blueprint, si
        // bien qu'à dix variables elle enfouissait les styles sous dix lignes identiques
        // pour un réglage dont la plupart des éléments n'ont pas besoin.
        rows.add(sectionRow(y, ElementPropertiesState.Section.BINDING));
        y += ROW;
        if (!element.isBound()
                && collapsedSections.contains(ElementPropertiesState.Section.BINDING)) {
            return closeWithStyles(rows, y, element, screen);
        }
        if (collapsedSections.contains(ElementPropertiesState.Section.BINDING)) {
            return closeWithStyles(rows, y, element, screen);
        }
        // Ce à quoi l'élément est lié, sur UNE rangée — et la liste seulement pendant
        // qu'on choisit. Elle était dépliée en permanence, à raison d'une rangée par
        // variable du blueprint : à quarante variables, quarante lignes identiques au
        // milieu du panneau, pour un réglage qu'on touche une fois.
        String bound = element.binding().variable();
        rows.add(new Row(y, I18n.get("blueprint.designer.bind"), java.util.List.of(
                new Chip(bound.isEmpty() ? I18n.get("blueprint.designer.bind.none") : bound,
                        52, PROPERTIES_WIDTH - 56, choosingVariable,
                        () -> choosingVariable = !choosingVariable)), null, null));
        y += ROW;
        if (choosingVariable) {
            // « Aucune » d'abord : délier est le geste qu'on cherche quand on rouvre la
            // liste sur un élément déjà lié, et le faire chercher au bout de quarante
            // lignes serait le punir d'avoir choisi.
            rows.add(new Row(y, "", java.util.List.of(
                    new Chip(I18n.get("blueprint.designer.bind.none"), 4, PROPERTIES_WIDTH - 8,
                            !element.isBound(), () -> {
                                apply(properties.bindTo(""));
                                choosingVariable = false;
                            })), null, null));
            y += ROW;
            for (var variable : session.blueprint().variables().values()) {
                // Filtré par ce que la CIBLE sait lire. Une barre nourrie d'une chaîne
                // reste vide pour toujours, et proposer ce choix revient à proposer une
                // panne. Celle qui est déjà liée reste listée même si elle ne convient
                // plus : la cacher empêcherait de voir ce qui est réglé, et de le défaire.
                boolean fits = ElementPropertiesState.acceptsVariable(
                        element.binding().target(), variable.type());
                if (!fits && !variable.name().equals(bound)) {
                    continue;
                }
                String pick = variable.name();
                rows.add(new Row(y, "", java.util.List.of(
                        new Chip(pick, 4, PROPERTIES_WIDTH - 8, pick.equals(bound),
                                () -> {
                                    apply(properties.bindTo(pick));
                                    choosingVariable = false;
                                })), null, null));
                y += ROW;
            }
        }
        if (element.isBound()) {
            int[] cursor = {y};
            enumRows(rows, cursor, "blueprint.designer.bind.target",
                    fr.blueprint.core.graph.screen.ElementBinding.Target.values(),
                    element.binding().target(), "blueprint.designer.bind.",
                    target -> apply(properties.bindTarget(target)));
            y = cursor[0];
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

        return closeWithStyles(rows, y, element, screen);
    }

    /**
     * La dernière section : les styles nommés.
     *
     * <p>Extraite pour que la liaison puisse se replier sans sauter par-dessus — un
     * { return} au milieu d'une méthode de trois cents lignes aurait fait disparaître
     * les styles avec elle.
     */
    private java.util.List<Row> closeWithStyles(java.util.List<Row> rows, int y,
                                                ScreenElement element, Screen screen) {
        rows.add(sectionRow(y, ElementPropertiesState.Section.STYLES));
        y += ROW;
        if (collapsedSections.contains(ElementPropertiesState.Section.STYLES)) {
            return rows;
        }
        rows.add(new Row(y, "", java.util.List.of(
                new Chip(I18n.get("blueprint.designer.styles.create"), 4, PROPERTIES_WIDTH - 8,
                        false, controller::createStyleFromSelection)), null, null));
        y += ROW;
        if (screen.styles().isEmpty()) {
            rows.add(new Row(y, I18n.get("blueprint.designer.styles.none"),
                    java.util.List.of(), null, null));
            // La rangée existe, elle doit consommer sa hauteur. Sans ce pas, la première
            // rangée qui la suivrait se peindrait par-dessus — invisible aujourd'hui
            // parce que la liste est vide dans ce cas précis, et faux dès qu'on ajoutera
            // une ligne derrière.
            y += ROW;
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
        var modes = fr.blueprint.core.graph.screen.Extent.Mode.values();
        int step = (PROPERTIES_WIDTH - 8) / modes.length;
        java.util.List<Chip> chips = new java.util.ArrayList<>(modes.length);
        int x = 4;
        for (var mode : modes) {
            String label = I18n.get("blueprint.designer.size."
                    + mode.name().toLowerCase(java.util.Locale.ROOT));
            chips.add(new Chip(label, x, step - 1, current == mode,
                    () -> apply(properties.setSizeMode(horizontal, mode))));
            x += step;
        }
        return chips;
    }

    /**
     * Les pastilles d'une énumération, <b>tenant dans le panneau</b>.
     *
     * <p>Le pas était figé à 18 px depuis {@code x = 52} : à cinq valeurs, la dernière
     * courait jusqu'à 141 sur un panneau de 128. Les deux dernières cibles de liaison —
     * « Activé » et « Visible » — n'étaient donc ni lisibles ni cliquables, alors que le
     * modèle les propose depuis la 10.7. Un réglage qu'on ne peut pas atteindre n'existe
     * pas.
     *
     * <p>Au-delà de trois valeurs, la rangée abandonne son libellé et les pastilles
     * occupent toute la largeur : mieux vaut une ligne de plus qu'une pastille dehors.
     *
     * <p>Et si elles ne tiennent toujours pas, elles <b>passent à la ligne</b>. À cinq
     * valeurs sur cent quarante-quatre pixels, chacune disposait de vingt-sept pixels et
     * « Enabled » se lisait « Activ », « Detach » se lisait « Detac » : des mots coupés au
     * milieu, qu'on devine au lieu de les lire. Un mot tronqué ne se comprend pas plus
     * vite qu'un mot absent.
     */
    private <E extends Enum<E>> java.util.List<Chip> enumChips(
            E[] values, E current, String keyPrefix, java.util.function.Consumer<E> onPick) {
        int left = values.length > 3 ? 4 : VALUE_LEFT;
        int room = PROPERTIES_WIDTH - left - 4;
        java.util.List<String> labels = new java.util.ArrayList<>(values.length);
        int longest = 0;
        for (E value : values) {
            String label = I18n.get(keyPrefix + value.name().toLowerCase(java.util.Locale.ROOT));
            labels.add(label);
            longest = Math.max(longest, label.length());
        }
        // La largeur d'un caractère est ESTIMÉE, comme NodeGeometry estime celle d'une
        // pastille de variable : mesurer demanderait la police, et rendrait cette décision
        // invérifiable sans client. Une estimation large vaut mieux qu'un mot coupé.
        int perRow = ElementPropertiesState.chipsPerRow(room, longest, values.length);
        int step = room / perRow;
        java.util.List<Chip> chips = new java.util.ArrayList<>(values.length);
        for (int i = 0; i < values.length; i++) {
            E value = values[i];
            chips.add(new Chip(labels.get(i), left + (i % perRow) * step, step - 1,
                    current == value, () -> onPick.accept(value), i / perRow));
        }
        return chips;
    }

    /**
     * Une rangée d'énumération : le libellé au-dessus quand les pastilles prennent toute
     * la largeur, sur la même ligne sinon.
     */
    private <E extends Enum<E>> void enumRows(java.util.List<Row> rows, int[] y,
                                              String labelKey, E[] values, E current,
                                              String keyPrefix,
                                              java.util.function.Consumer<E> onPick) {
        java.util.List<Chip> chips = enumChips(values, current, keyPrefix, onPick);
        Row row;
        if (values.length > 3) {
            rows.add(new Row(y[0], I18n.get(labelKey), java.util.List.of(), null, null));
            y[0] += ROW;
            row = new Row(y[0], "", chips, null, null);
        } else {
            row = new Row(y[0], I18n.get(labelKey), chips, null, null);
        }
        rows.add(row);
        // La hauteur RÉELLE de la rangée : une énumération qui passe à la ligne en occupe
        // plusieurs, et avancer d'une seule ferait peindre la suivante par-dessus.
        y[0] += rowHeight(row);
    }

    /**
     * Un champ n'est montré que s'il agit. Un x/y sur un enfant rangé par son conteneur
     * s'écrirait sans rien changer à l'écran, et {@code colonnes} n'existe qu'en grille.
     */
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
        for (Row row : visiblePropertyRows()) {
            // Un en-tête de section : un filet et un titre clair, la même grammaire que la
            // colonne de gauche. Sans eux le panneau était une coulée de vingt-cinq
            // rangées où rien ne disait où un sujet finissait.
            if (row.section() != null) {
                g.fill(left, row.y() - 2, width - 1, row.y() - 1, PANEL_BORDER);
                g.drawString(font, font.plainSubstrByWidth(row.label(), PROPERTIES_WIDTH - 8),
                        left + 4, row.y(), TEXT, false);
                continue;
            }
            if (!row.label().isEmpty()) {
                // Le libellé s'arrête AVANT la colonne des valeurs. Il avait droit à toute
                // la largeur du panneau, si bien qu'un nom long se peignait par-dessus sa
                // propre valeur : « Background » et « #C0141519 » donnaient
                // « Backgrou#C0141519 », un mot qui n'existe pas et une couleur qu'on ne
                // sait plus lire.
                int room = row.value() == null && row.chips().isEmpty()
                        ? PROPERTIES_WIDTH - 8    // seul sur sa ligne : toute la place
                        : VALUE_LEFT - 6;
                g.drawString(font, font.plainSubstrByWidth(row.label(), room),
                        left + 4, row.y(), DIM_TEXT, false);
            }
            for (Chip chip : row.chips()) {
                int x = left + chip.x();
                int cy = row.y() + chip.line() * ROW;
                g.fill(x, cy - 1, x + chip.width(), cy + ROW - 3,
                        chip.active() ? SELECTED : PANEL_BORDER);
                if (!chip.label().isEmpty()) {
                    g.drawString(font,
                            font.plainSubstrByWidth(chip.label(), chip.width() - 2),
                            x + 1, cy, chip.active() ? PANEL_BACKGROUND : TEXT, false);
                }
            }
            if (row.value() != null) {
                boolean editing = properties.isEditing(row.field());
                int color = editing
                        ? (properties.valid(this::nameFree) ? SELECTED : INVALID) : TEXT;
                // Une valeur sans libellé occupe TOUTE la largeur : c'est la rangée que
                // needsOwnLine réserve aux textes, dont le nom, la texture et le format.
                int valueX = !row.label().isEmpty() || !row.chips().isEmpty()
                        ? (row.chips().isEmpty() ? VALUE_LEFT
                                : row.chips().getLast().x() + row.chips().getLast().width() + 3)
                        : 6;
                // Un champ vide doit se VOIR comme un champ. « Texte » et « Infobulle »
                // ne peignaient rien tant qu'ils étaient vides : l'auteur voyait un
                // libellé suivi de blanc, sans rien qui dise qu'on peut cliquer et taper.
                // C'est le retour que le canevas de nœuds a déjà reçu sur ses littéraux,
                // et la même réponse — un creux, comme ScreenPainter.Depth.SUNKEN.
                if (row.field() != null) {
                    int fx = left + valueX - 2;
                    int fy = row.y() - 1;
                    g.fill(fx, fy, left + PROPERTIES_WIDTH - 4, fy + ROW - 2,
                            editing ? SELECTED : PANEL_BORDER);
                    g.fill(fx + 1, fy + 1, left + PROPERTIES_WIDTH - 5, fy + ROW - 3,
                            FIELD_TROUGH);
                }
                g.drawString(font, font.plainSubstrByWidth(row.value(),
                                PROPERTIES_WIDTH - valueX - 6),
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
            return clickPalette(mx, my);
        }
        if (panels.inProperties(mx, width)) {
            return clickProperties(mx, my);
        }
        // La zone, pas seulement la surface : la surface ignore où elle est découpée, si
        // bien qu'un clic dans la bande de douze pixels du haut — celle qui appartient à la
        // barre — atteignait un élément qu'on ne voyait pas à cet endroit.
        if (my < areaTop || !surface.contains(mx, my)) {
            return false;
        }
        commitEdit();
        controller.press(surface.toDesignX(mx), surface.toDesignY(my),
                e.hasShiftDown());
        return true;
    }

    /**
     * Le clic dans la colonne — <b>décidé</b> par {@link DesignerPalette}, appliqué ici.
     *
     * <p>L'abscisse arrive enfin jusqu'ici : sans elle, l'œil des calques était dessiné et
     * inerte, et le clic sélectionnait au lieu de basculer la visibilité, contre ce que le
     * commentaire de la méthode annonçait.
     */
    private boolean clickPalette(double mx, double my) {
        DesignerPalette.Model model = paletteModel();
        int scroll = paletteScroll.offset(DesignerPalette.contentRows(model),
                DesignerPalette.visibleRows(top, height));
        DesignerPalette.Click click =
                DesignerPalette.clickAt(model, top, height, scroll, mx, my);
        switch (click.hit()) {
            case SCREEN_ADD -> addScreen();
            case SCREEN_SELECT -> controller.setScreenName(click.name());
            case SCREEN_MODE -> {
                if (!controller.toggleHud()) {
                    reportRefusal();
                }
            }
            case SCREEN_RENAME -> {
                renamingScreen = controller.screenName();
                screenBuffer = controller.screenName();
            }
            case SCREEN_DELETE -> controller.removeCurrentScreen();
            case ELEMENT_ADD -> {
                if (controller.addElement(click.element(), dropX(), dropY()) == null) {
                    reportRefusal();
                }
            }
            case LAYER_SELECT -> {
                controller.selection().selectAll(List.of(click.name()), false);
                // Le clic sélectionne ; s'il se prolonge en glisser, il reparente. Armer
                // ici plutôt qu'au premier mouvement garde le geste continu — attendre un
                // seuil ferait rater les déplacements courts, qui sont les plus fréquents
                // dans un arbre où deux lignes voisines sont à douze pixels.
                draggedLayer = click.name();
                dropTarget = null;
            }
            case LAYER_VISIBILITY -> toggleLayerVisibility(click.name());
            case LAYER_EXPAND -> {
                if (!collapsedLayers.remove(click.name())) {
                    collapsedLayers.add(click.name());
                }
            }
            default -> {
            }
        }
        return true;
    }

    /**
     * Masque ou révèle un seul calque, sans toucher à la sélection.
     *
     * <p>{@code Ctrl+H} bascule la sélection entière ; l'œil vise une ligne. Passer par la
     * sélection ferait de la visibilité un geste qui déplace le curseur de travail.
     */
    private void toggleLayerVisibility(String name) {
        Screen screen = controller.screen();
        ScreenElement element = screen == null ? null : screen.element(name);
        if (element == null) {
            return;
        }
        if (!controller.setElement(element.withVisible(!element.visible()))) {
            reportRefusal();
        }
    }

    /**
     * Applique le reparentage lâché dans l'arbre.
     *
     * <p>Aucune opération neuve : {@code SetElement} porte l'élément entier, et
     * {@code ScreenRules.checkPlacement} refuse déjà les cycles, les parents absents et les
     * parents qui ne sont pas des conteneurs. Le modèle savait reparenter depuis toujours —
     * ce qui manquait était un geste pour le lui demander.
     */
    private void applyReparent() {
        Screen screen = controller.screen();
        ScreenElement element = screen == null || draggedLayer == null
                ? null : screen.element(draggedLayer);
        if (element == null || dropTarget == null) {
            return;
        }
        String parent = dropTarget.isEmpty() ? null : dropTarget;
        if (java.util.Objects.equals(element.parent(), parent)) {
            return;   // lâché sur son parent actuel : rien à faire, et rien à annuler
        }
        if (!controller.setElement(element.withParent(parent))) {
            reportRefusal();
        }
    }

    /**
     * La liste des variables est-elle ouverte ?
     *
     * <p>Refermée dès qu'on choisit, et remise à zéro quand la sélection change : rouvrir
     * un autre élément sur une liste dépliée donnerait un panneau différent selon ce qu'on
     * a fait juste avant.
     */
    private boolean choosingVariable;

    /** Le calque qu'on traîne dans l'arbre, et le parent que le curseur désigne. */
    private @Nullable String draggedLayer;
    private @Nullable String dropTarget;

    /** Les calques repliés, par nom. Un état d'affichage : rien à enregistrer. */
    private final java.util.Set<String> collapsedLayers = new java.util.LinkedHashSet<>();
    private final fr.blueprint.client.editor.PanelScroll paletteScroll =
            new fr.blueprint.client.editor.PanelScroll();

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
        for (Row row : visiblePropertyRows()) {
            if (my < row.y() - 1 || my >= row.y() - 1 + rowHeight(row)) {
                continue;
            }
            for (Chip chip : row.chips()) {
                // La ligne de la pastille compte : une énumération qui passe à la ligne
                // pose sa seconde rangée douze pixels plus bas, et cliquer la première
                // colonne y déclencherait la valeur du dessus.
                if (my >= row.y() - 1 + chip.line() * ROW
                        && my < row.y() + ROW - 1 + chip.line() * ROW
                        && local >= chip.x() && local < chip.x() + chip.width()) {
                    chip.onClick().run();
                    reportRefusal();
                    return true;
                }
            }
            if (row.section() != null) {
                // Un en-tête se replie. Toute sa largeur répond, pas seulement le
                // chevron : viser six pixels sur un panneau qu'on trouve déjà petit
                // serait une plaisanterie.
                if (!collapsedSections.remove(row.section())) {
                    collapsedSections.add(row.section());
                }
                return true;
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
        if (draggedLayer != null) {
            // On suit le curseur pour dessiner la cible ; rien n'est appliqué avant de
            // lâcher. Reparenter en cours de route ferait sauter l'arbre sous la main.
            dropTarget = DesignerPalette.dropTarget(paletteModel(), top, height,
                    paletteScroll.offset(DesignerPalette.contentRows(paletteModel()),
                            DesignerPalette.visibleRows(top, height)),
                    draggedLayer, e.y());
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
        if (draggedLayer != null) {
            applyReparent();
            draggedLayer = null;
            dropTarget = null;
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
        // Au-dessus de la COLONNE, la molette la fait défiler. Elle zoomait le canevas :
        // les trois sections s'empilaient sans défiler, et le seul geste qu'on essaie
        // au-dessus d'une liste trop longue agissait ailleurs.
        if (panels.propertiesOpen() && panels.inProperties(mx, width)) {
            propertiesScroll.scrollBy(amount > 0 ? -1 : 1, propertyRows().size(),
                    Math.max(1, (height - top - ROW * 2) / ROW));
            return true;
        }
        if (panels.paletteOpen() && panels.inPalette(mx)) {
            DesignerPalette.Model model = paletteModel();
            paletteScroll.scrollBy(amount > 0 ? -1 : 1, DesignerPalette.contentRows(model),
                    DesignerPalette.visibleRows(top, height));
            return true;
        }
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
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> commitEdit();
            default -> {
                return false;
            }
        }
        return true;
    }

    /**
     * Valide la saisie en cours — par Entrée, ou en <b>partant ailleurs</b>.
     *
     * <p>Un clic sur le canevas jetait la frappe. On tapait une largeur, on cliquait
     * l'élément pour la voir prendre, et la valeur disparaissait sans un mot : c'est la
     * perte silencieuse que le principe U2 interdit. Partir d'un champ vaut désormais le
     * valider, comme dans tout formulaire — et une valeur invalide reste refusée par
     * {@code commit}, qui garde le champ ouvert.
     */
    private void commitEdit() {
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
