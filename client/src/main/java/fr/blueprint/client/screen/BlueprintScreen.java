package fr.blueprint.client.screen;

import fr.blueprint.core.graph.screen.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * L'écran d'un blueprint, ouvert en jeu (story 10.3).
 *
 * <p><b>Générique</b> : il ne connaît aucun menu en particulier. Il reçoit la
 * description venue du serveur et la compose — pas une ligne de code par écran, et le
 * <b>même</b> {@link ScreenPainter} que le concepteur (10.2). Deux moteurs de dessin
 * donneraient deux résultats, et l'auteur découvrirait l'écart une fois en partie.
 *
 * <p>L'adaptation ne se contente pas de recentrer : les éléments sont résolus contre la
 * taille RÉELLE de la fenêtre en unités d'interface, ancres et pourcentages compris.
 * Un menu conçu une fois tient donc de 320×180 (1280×720 en <i>GUI scale</i> 4) à
 * 960×540 (3840×2160 en <i>scale</i> 4) — les deux extrêmes réels.
 */
public class BlueprintScreen extends net.minecraft.client.gui.screens.Screen {

    private final Identifier blueprint;
    private final int instance;
    private final TextureCache textures = new TextureCache();
    private final Runnable onClosed;
    private final java.util.function.Consumer<String> onClick;

    /**
     * Le modèle courant. <b>Non final</b> : les modificateurs du graphe (10.4) le
     * remplacent par une version modifiée. Un écran immuable qu'on reconstruit à chaque
     * mise à jour, plutôt qu'un état mutable à côté — ainsi le rendu, le hit-test et le
     * serveur parlent tous du même objet.
     */
    private Screen model;
    private @Nullable String pressed;
    /** La navigation au clavier (10.6, AC3) : Tab parcourt, Entrée active. */
    private final ScreenFocus focus = new ScreenFocus();

    public BlueprintScreen(Identifier blueprint, Screen model, int instance,
                           Runnable onClosed, java.util.function.Consumer<String> onClick) {
        // Le titre du lecteur d'écran : sans lui, l'accessibilité annonce « écran ».
        super(Component.translatable("blueprint.screen.title", model.name()));
        this.blueprint = blueprint;
        this.model = model;
        this.instance = instance;
        this.onClosed = onClosed;
        this.onClick = onClick;
    }

    public Identifier blueprintId() {
        return blueprint;
    }

    public Screen model() {
        return model;
    }

    public int instance() {
        return instance;
    }

    /**
     * Applique les modifications reçues du serveur.
     *
     * <p>Celles qui portent un autre numéro d'instance sont <b>jetées</b> : entre
     * l'envoi et l'arrivée, le joueur a pu refermer l'écran et en rouvrir un autre, et
     * la mise à jour s'appliquerait alors au mauvais bouton, sans la moindre erreur.
     */
    public void apply(int fromInstance,
                      java.util.List<fr.blueprint.core.graph.screen.ScreenUpdate> updates) {
        if (fromInstance != instance) {
            return;
        }
        for (var update : updates) {
            if (!update.screen().isEmpty() && !update.screen().equals(model.name())) {
                continue;   // elle vise un HUD, ou un écran qui n'est plus le nôtre
            }
            var element = model.element(update.element());
            if (element == null) {
                continue; // l'écran a changé sous nos pieds : on ignore, on ne lève pas
            }
            model = model.replacing(update.element(), switch (update.kind()) {
                case TEXT -> element.withText(update.screenText());
                case TOOLTIP -> element.withTooltip(update.screenText());
                case STYLE -> element.withStyleName(update.text());
                case TEXTURE -> element.withTexture(update.textureId());
                case VISIBLE -> element.withVisible(update.flag());
                case ENABLED -> element.withEnabled(update.flag());
                case PROGRESS -> element;   // la valeur vit à part : voir progress()
                // Idem pour les éléments riches (10.8) : données d'exécution, pas
                // propriétés du blueprint.
                case LINES, ITEM, VALUE, SCROLL, SCROLL_X -> element;
            });
            switch (update.kind()) {
                case PROGRESS -> progress.put(update.element(), update.number());
                case LINES -> {
                    lines.put(update.element(), update.linesValue());
                    // Les lignes ont changé : un décalage gardé pointerait dans le vide,
                    // et le joueur verrait une liste vide qu'il devrait remonter à la
                    // main pour découvrir qu'elle est pleine.
                    scroll.remove(update.element());
                }
                case SCROLL -> panelScroll.put(update.element(), update.number());
                case SCROLL_X -> panelScrollX.put(update.element(), update.number());
                case ITEM -> items.put(update.element(), itemOf(update));
                case VALUE -> {
                    values.put(update.element(), update.number());
                    checks.put(update.element(), update.flag());
                    if (!update.text().isEmpty()) {
                        inputs.put(update.element(), update.text());
                    }
                }
                default -> { }
            }
        }
        // Le modèle a changé : la mise en page gardée ne vaut plus. C'est le SEUL autre
        // événement que le redimensionnement qui puisse la rendre fausse.
        layout = null;
    }

    /**
     * Le remplissage des barres, par élément. Hors du modèle à dessein : c'est une
     * valeur d'exécution, propre à ce joueur et à cette ouverture, pas une propriété du
     * blueprint. La mettre dans {@code ScreenElement} la ferait voyager dans la
     * sauvegarde et l'export texte, où elle n'a rien à faire.
     */
    private final java.util.Map<String, Double> progress = new java.util.HashMap<>();

    // Les valeurs des éléments riches (10.8), hors du modèle pour la même raison que le
    // remplissage des barres : ce sont des données d'exécution, propres à ce joueur et à
    // cette ouverture.
    private final java.util.Map<String, java.util.List<String>> lines = new java.util.HashMap<>();
    private final java.util.Map<String, Integer> scroll = new java.util.HashMap<>();

    /**
     * De combien chaque panneau défilant est remonté, en unités (story 10.13).
     *
     * <p>Hors du modèle à dessein, comme le remplissage des barres et le texte des
     * champs : deux joueurs devant le même menu n'ont pas la même position de lecture, et
     * l'écrire dans le blueprint la ferait voyager dans la sauvegarde et dans l'export.
     */
    private final java.util.Map<String, Double> panelScroll = new java.util.HashMap<>();

    /** Le même, sur l'axe horizontal. */
    private final java.util.Map<String, Double> panelScrollX = new java.util.HashMap<>();
    private final java.util.Map<String, String> inputs = new java.util.HashMap<>();
    private final java.util.Map<String, Double> values = new java.util.HashMap<>();
    private final java.util.Map<String, Boolean> checks = new java.util.HashMap<>();
    private final java.util.Map<String, net.minecraft.world.item.ItemStack> items =
            new java.util.HashMap<>();
    /** Le champ de saisie actif : une seule frappe à la fois, comme partout. */
    private @Nullable String editing;

    private static net.minecraft.world.item.ItemStack itemOf(
            fr.blueprint.core.graph.screen.ScreenUpdate update) {
        var id = fr.blueprint.core.graph.screen.PackRef.texture(update.text());
        if (update.text().isEmpty() || id == null) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id);
        return item.map(value -> new net.minecraft.world.item.ItemStack(value,
                Math.max(1, (int) update.number()))).orElse(
                        net.minecraft.world.item.ItemStack.EMPTY);
    }

    /** Ce que le peintre a besoin de savoir : textures absentes, survol, pression. */
    protected ScreenPainter.Visuals visuals(int mouseX, int mouseY) {
        String hovered = elementAt(mouseX, mouseY);
        return new ScreenPainter.Visuals() {
            @Override
            public boolean textureMissing(Identifier texture) {
                return textures.missing(texture);
            }

            @Override
            public String missingPack(Identifier texture) {
                return textures.missingPack(texture);
            }

            @Override
            public boolean hovered(String element) {
                // Le focus clavier se montre COMME un survol : le joueur qui tabule doit
                // voir où il est, et inventer un second état visuel pour dire la même
                // chose aurait demandé à l'auteur de le styler séparément.
                return element.equals(hovered) || element.equals(focus.focused());
            }

            @Override
            public boolean pressed(String element) {
                return element.equals(pressed);
            }

            @Override
            public double progress(String element) {
                return progress.getOrDefault(element, 0.0);
            }

            @Override
            public double panelScroll(String element) {
                return panelScroll.getOrDefault(element, 0.0);
            }

            @Override
            public java.util.List<String> lines(String element) {
                return lines.getOrDefault(element, java.util.List.of());
            }

            @Override
            public int scroll(String element) {
                return scroll.getOrDefault(element, 0);
            }

            @Override
            public String input(String element) {
                return inputs.getOrDefault(element, "");
            }

            @Override
            public boolean focused(String element) {
                return element.equals(editing);
            }

            @Override
            public boolean checked(String element) {
                return checks.getOrDefault(element, false);
            }

            @Override
            public double value(String element) {
                return values.getOrDefault(element, 0.0);
            }

            @Override
            public net.minecraft.world.item.ItemStack item(String element) {
                return items.get(element);
            }
        };
    }

    /**
     * L'élément cliquable sous le point, ou {@code null}. Parcourt à l'envers de
     * l'ordre de dessin : c'est celui du dessus qui reçoit le clic, comme partout.
     */
    private @Nullable String elementAt(double mouseX, double mouseY) {
        // La MÊME passe que le dessin : un bouton rangé par son conteneur n'est pas là
        // où sa position écrite le dirait, et un hit-test qui la lirait quand même
        // donnerait un menu dont les boutons se cliquent à côté d'eux-mêmes.
        var placed = layout();
        var elements = java.util.List.copyOf(model.elements().values());
        for (int i = elements.size() - 1; i >= 0; i--) {
            var element = elements.get(i);
            if (!element.kind().interactive() || !element.enabled()
                    || !ScreenPainter.visible(model, element, ScreenPainter.Visuals.NONE)) {
                continue;
            }
            var rect = placed.get(element.name());
            if (rect != null && rect.contains(mouseX, mouseY)
                    && insideItsClip(element, placed, mouseX, mouseY)) {
                return element.name();
            }
        }
        return null;
    }

    /**
     * Le point est-il dans le cadre qui découpe cet élément ?
     *
     * <p><b>Le contrôle qui compte</b> pour un panneau défilant. Sans lui, un bouton sorti
     * du cadre — invisible, découpé au dessin — resterait <b>cliquable</b> : le joueur
     * viserait le menu en dessous, activerait ce qu'il ne voit pas, et le graphe recevrait
     * une action que rien à l'écran n'expliquerait. C'est le pire mode de panne d'une
     * interface : celui où tout a l'air juste.
     */
    private boolean insideItsClip(fr.blueprint.core.graph.screen.ScreenElement element,
            java.util.Map<String, fr.blueprint.core.graph.screen.ScreenLayout.Rect> placed,
            double mouseX, double mouseY) {
        var clip = fr.blueprint.core.graph.screen.ScreenLayout.clipOf(model, element, placed);
        return clip == null || clip.contains(mouseX, mouseY);
    }

    /**
     * {@code Tab} parcourt les éléments cliquables, {@code Entrée} et {@code Espace}
     * activent (10.6, AC3). {@code Échap} ferme, comme tout écran du jeu.
     *
     * <p>Rien n'est consommé quand l'écran n'a aucun élément atteignable : le joueur
     * s'attend alors à ce que la touche fasse ce qu'elle fait partout ailleurs.
     */
    /** Une interaction porteuse de valeur, remontée telle quelle au serveur (10.8). */
    public record Interaction(String element, int index, String text, double number,
                              boolean flag) {
    }

    /** Prévenu quand un élément riche produit une valeur ; {@code null} hors du jeu. */
    private @Nullable java.util.function.Consumer<Interaction> onValue;

    public void setOnValue(java.util.function.Consumer<Interaction> consumer) {
        this.onValue = consumer;
    }

    /** L'indice de la ligne sous le curseur, découpage et défilement compris. */
    private int listIndexAt(fr.blueprint.core.graph.screen.ScreenElement element,
                            fr.blueprint.core.graph.screen.ScreenLayout.@Nullable Rect rect,
                            double mouseY) {
        if (rect == null) {
            return -1;
        }
        int inset = element.style().padding();
        var view = viewOf(element, rect);
        return view.indexAt(mouseY - rect.y() - inset, element.options().rowHeight());
    }

    private fr.blueprint.core.graph.screen.ListView viewOf(
            fr.blueprint.core.graph.screen.ScreenElement element,
            fr.blueprint.core.graph.screen.ScreenLayout.Rect rect) {
        int inset = element.style().padding();
        return new fr.blueprint.core.graph.screen.ListView(
                lines.getOrDefault(element.name(), java.util.List.of()).size(),
                fr.blueprint.core.graph.screen.ListView.rowsThatFit(
                        rect.height() - 2 * inset, element.options().rowHeight()),
                scroll.getOrDefault(element.name(), 0));
    }

    private double sliderValueAt(fr.blueprint.core.graph.screen.ScreenElement element,
                                 fr.blueprint.core.graph.screen.ScreenLayout.@Nullable Rect rect,
                                 double mouseX) {
        if (rect == null || rect.width() <= 0) {
            return element.options().min();
        }
        int inset = Math.max(2, element.style().padding());
        double fraction = (mouseX - rect.x() - inset) / Math.max(1, rect.width() - 2 * inset);
        return element.options().valueAt(fraction);
    }

    /** La molette fait défiler la liste sous le curseur, puis le panneau qui la contient. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        String name = elementAt(mouseX, mouseY);
        var element = name == null ? null : model.element(name);
        if (element != null
                && element.kind() == fr.blueprint.core.graph.screen.ElementKind.LIST) {
            var rect = layout().get(name);
            if (rect != null) {
                var scrolled = viewOf(element, rect).scrolledBy((int) -Math.signum(vAmount));
                scroll.put(name, scrolled.offset());
                return true;
            }
        }
        // Une LISTE d'abord, le panneau qui la porte ensuite : sinon la molette ferait
        // glisser la page entière alors qu'on voulait parcourir la liste qu'on regarde.
        // La molette ne porte pas ses modificateurs : on interroge la fenêtre plutôt que
        // de tenir un drapeau à jour dans keyPressed/keyReleased, qui reste enfoncé pour
        // toujours dès qu'on relâche Maj hors de l'écran.
        if (scrollPanelAt(mouseX, mouseY, vAmount, shiftDown())) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
    }

    /**
     * Fait défiler ce qu'il faut pour que cet élément soit visible.
     *
     * <p>Ne fait rien s'il l'est déjà : faire défiler « pour rien » à chaque déplacement
     * du focus ferait sauter la page sous les yeux du joueur qui parcourt le menu.
     */
    private void reveal(String name) {
        var element = model.element(name);
        if (element == null) {
            return;
        }
        var placed = layout();
        var rect = placed.get(name);
        var clip = fr.blueprint.core.graph.screen.ScreenLayout.clipOf(model, element, placed);
        String panel = fr.blueprint.core.graph.screen.ScreenLayout.scrollerOf(model, element);
        if (rect == null || clip == null || panel == null) {
            return;
        }
        // Les deux axes : un élément peut être sorti par le bas ET par la droite, et ne
        // le ramener que d'un côté le laisserait invisible pour l'autre.
        double dy = fr.blueprint.core.graph.screen.ScreenLayout.revealDelta(clip, rect);
        if (dy != 0) {
            scrollPanel(panel, dy, true);
        }
        double dx = fr.blueprint.core.graph.screen.ScreenLayout.revealDeltaX(clip, rect);
        if (dx != 0) {
            scrollPanel(panel, dx, false);
        }
    }

    /**
     * Le clavier fait défiler : {@code Page↑}/{@code Page↓} d'un écran,
     * {@code Origine}/{@code Fin} aux extrémités.
     *
     * <p>Sans lui, un joueur au clavier — ou une manette — ne pouvait atteindre le bas
     * d'une page qu'en tabulant d'élément en élément, et rien du tout si la page ne
     * contenait que du texte : il n'y avait alors aucun élément à atteindre.
     *
     * <p>Le panneau visé est celui du <b>focus</b>, faute de curseur pour désigner. À
     * défaut de focus, le seul panneau défilant de l'écran : c'est le cas courant, et
     * exiger un focus préalable pour lire une page serait une marche de trop.
     */
    private boolean scrollByKey(int key, boolean control) {
        String panel = focusedPanel();
        if (panel == null) {
            return false;
        }
        var frame = layout().get(panel);
        double page = frame == null ? SCROLL_STEP : Math.max(SCROLL_STEP, frame.height() - 9);
        return switch (key) {
            case org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP -> scrollPanel(panel, -page, true);
            case org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN -> scrollPanel(panel, page, true);
            // Origine et Fin ramènent les DEUX axes : « le début », c'est le coin, pas la
            // première ligne d'une colonne dont on serait encore décalé à droite.
            case org.lwjgl.glfw.GLFW.GLFW_KEY_HOME ->
                    scrollPanel(panel, Double.NEGATIVE_INFINITY, true)
                            | scrollPanel(panel, Double.NEGATIVE_INFINITY, false);
            case org.lwjgl.glfw.GLFW.GLFW_KEY_END -> scrollPanel(panel, Double.MAX_VALUE, true);
            // Les flèches restent au jeu SAUF avec Ctrl : sans cela, un menu ouvert
            // avalerait les touches de déplacement dès qu'il contient un panneau.
            case org.lwjgl.glfw.GLFW.GLFW_KEY_UP ->
                    control && scrollPanel(panel, -SCROLL_STEP, true);
            case org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN ->
                    control && scrollPanel(panel, SCROLL_STEP, true);
            case org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT ->
                    control && scrollPanel(panel, -SCROLL_STEP, false);
            case org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT ->
                    control && scrollPanel(panel, SCROLL_STEP, false);
            default -> false;
        };
    }

    /** Le panneau que le clavier vise : celui du focus, sinon le seul de l'écran. */
    private @Nullable String focusedPanel() {
        String focused = focus.focused();
        var element = focused == null ? null : model.element(focused);
        if (element != null) {
            String panel = fr.blueprint.core.graph.screen.ScreenLayout.scrollerOf(model, element);
            if (panel != null) {
                return panel;
            }
        }
        String only = null;
        for (var candidate : model.elements().values()) {
            if (!candidate.scrolls()) {
                continue;
            }
            if (only != null) {
                return null;   // plusieurs : rien ne dit lequel, et deviner serait pire
            }
            only = candidate.name();
        }
        return only;
    }

    /** Applique un delta borné à un panneau, sur l'axe demandé ; vrai s'il a bougé. */
    private boolean scrollPanel(String panel, double delta, boolean vertical) {
        var element = model.element(panel);
        var placed = layout();
        if (element == null || placed.get(panel) == null) {
            return false;
        }
        var axis = vertical ? panelScroll : panelScrollX;
        double current = axis.getOrDefault(panel, 0.0);
        double range = rangeOf(element, placed, current, vertical);
        double next = Math.clamp(current + delta, 0, range);
        if (next == current) {
            return false;
        }
        axis.put(panel, next);
        layout = null;
        return true;
    }

    /** La plage d'un axe. Un seul point d'entrée : les deux se lisent partout ensemble. */
    private double rangeOf(fr.blueprint.core.graph.screen.ScreenElement element,
            java.util.Map<String, fr.blueprint.core.graph.screen.ScreenLayout.Rect> placed,
            double current, boolean vertical) {
        return vertical
                ? fr.blueprint.core.graph.screen.ScreenLayout.scrollRange(
                        model, element, placed, current)
                : fr.blueprint.core.graph.screen.ScreenLayout.scrollRangeX(
                        model, element, placed, current);
    }

    /** Les deux curseurs d'un panneau, au décalage courant. */
    private fr.blueprint.core.graph.screen.ScreenLayout.ScrollBars barsOf(
            fr.blueprint.core.graph.screen.ScreenElement element,
            java.util.Map<String, fr.blueprint.core.graph.screen.ScreenLayout.Rect> placed) {
        var frame = placed.get(element.name());
        double offsetY = panelScroll.getOrDefault(element.name(), 0.0);
        double offsetX = panelScrollX.getOrDefault(element.name(), 0.0);
        return fr.blueprint.core.graph.screen.ScreenLayout.scrollBarsOf(frame,
                rangeOf(element, placed, offsetY, true),
                rangeOf(element, placed, offsetX, false),
                offsetY, offsetX, model.styleOf(element).padding());
    }

    /** Le panneau dont on tient le curseur, sur quel axe, et où on l'a saisi. */
    private @Nullable String dragging;
    private boolean draggingVertical;
    private double dragGrab;

    /**
     * Attrape le curseur de défilement sous le point, s'il y en a un.
     *
     * <p>Cliquer <b>ailleurs sur la glissière</b> saute directement à cet endroit, comme
     * dans toute barre du jeu : c'est ce qui permet d'atteindre le bas d'une longue page
     * sans traverser tout le contenu à la molette.
     */
    private boolean grabScrollBar(double mouseX, double mouseY) {
        var placed = layout();
        var elements = java.util.List.copyOf(model.elements().values());
        for (int i = elements.size() - 1; i >= 0; i--) {
            var element = elements.get(i);
            if (!element.scrolls()
                    || !ScreenPainter.visible(model, element, ScreenPainter.Visuals.NONE)) {
                continue;
            }
            var frame = placed.get(element.name());
            if (frame == null) {
                continue;
            }
            var bars = barsOf(element, placed);
            for (var bar : new fr.blueprint.core.graph.screen.ScreenLayout.ScrollBar[]{
                    bars.vertical(), bars.horizontal()}) {
                if (bar == null || !bar.track().contains(mouseX, mouseY)) {
                    continue;
                }
                double along = bar.vertical() ? mouseY : mouseX;
                dragging = element.name();
                draggingVertical = bar.vertical();
                if (bar.thumb().contains(mouseX, mouseY)) {
                    dragGrab = along - bar.thumbStart();
                } else {
                    // Hors du curseur : on l'amène sous le doigt, centré, et le glisser
                    // continue de là. Sauter puis exiger un second geste serait deux clics
                    // pour ce que toute barre fait en un.
                    dragGrab = (bar.vertical() ? bar.thumb().height() : bar.thumb().width()) / 2;
                    moveThumbTo(along, bar, rangeOf(element, placed,
                            (bar.vertical() ? panelScroll : panelScrollX)
                                    .getOrDefault(element.name(), 0.0), bar.vertical()));
                }
                return true;
            }
        }
        return false;
    }

    private void moveThumbTo(double along,
                             fr.blueprint.core.graph.screen.ScreenLayout.ScrollBar bar,
                             double range) {
        var axis = bar.vertical() ? panelScroll : panelScrollX;
        double next = bar.offsetFor(along - dragGrab, range);
        if (next != axis.getOrDefault(dragging, 0.0)) {
            axis.put(dragging, next);
            layout = null;
        }
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event,
                                double dragX, double dragY) {
        if (dragging == null) {
            return super.mouseDragged(event, dragX, dragY);
        }
        var element = model.element(dragging);
        var placed = layout();
        if (element == null || placed.get(dragging) == null) {
            dragging = null;
            return true;
        }
        var bars = barsOf(element, placed);
        var bar = draggingVertical ? bars.vertical() : bars.horizontal();
        if (bar != null) {
            double current = (draggingVertical ? panelScroll : panelScrollX)
                    .getOrDefault(dragging, 0.0);
            moveThumbTo(draggingVertical ? event.y() : event.x(), bar,
                    rangeOf(element, placed, current, draggingVertical));
        }
        return true;
    }

    /** Ramène chaque décalage dans sa plage ; vrai si l'un d'eux a bougé. */
    private boolean clampScroll(
            java.util.Map<String, fr.blueprint.core.graph.screen.ScreenLayout.Rect> placed) {
        boolean changed = false;
        for (boolean vertical : new boolean[]{true, false}) {
            for (var entry : (vertical ? panelScroll : panelScrollX).entrySet()) {
                var container = model.element(entry.getKey());
                if (container == null) {
                    continue;
                }
                double range = rangeOf(container, placed, entry.getValue(), vertical);
                double clamped = Math.clamp(entry.getValue(), 0, range);
                if (clamped != entry.getValue()) {
                    entry.setValue(clamped);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static boolean shiftDown() {
        var window = net.minecraft.client.Minecraft.getInstance().getWindow();
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                        window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)
                || com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                        window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    /** Un cran de molette, en unités : trois lignes de texte, comme partout dans le jeu. */
    private static final double SCROLL_STEP = 3 * 9;

    /**
     * Fait défiler le panneau défilant le plus <b>intérieur</b> sous le curseur.
     *
     * <p>Le plus intérieur, parce qu'un panneau défilant peut en contenir un autre : c'est
     * celui qu'on regarde qui doit bouger, comme dans toute page imbriquée. Le parcours se
     * fait donc à l'envers de l'ordre de dessin, et le premier trouvé gagne.
     */
    private boolean scrollPanelAt(double mouseX, double mouseY, double amount,
                                  boolean horizontal) {
        var placed = layout();
        var elements = java.util.List.copyOf(model.elements().values());
        for (int i = elements.size() - 1; i >= 0; i--) {
            var element = elements.get(i);
            if (!element.scrolls()
                    || !ScreenPainter.visible(model, element, ScreenPainter.Visuals.NONE)) {
                continue;
            }
            var rect = placed.get(element.name());
            if (rect == null || !rect.contains(mouseX, mouseY)) {
                continue;
            }
            boolean vertical = fr.blueprint.core.graph.screen.ScreenLayout.scrollVertical(
                    element, horizontal);
            double current = (vertical ? panelScroll : panelScrollX)
                    .getOrDefault(element.name(), 0.0);
            if (rangeOf(element, placed, current, vertical) <= 0) {
                // Tout tient : on rend la molette à ce qui est derrière plutôt que de
                // l'avaler. Un panneau qui absorbe un geste sans rien faire donne
                // l'impression d'un menu bloqué.
                continue;
            }
            scrollPanel(element.name(), -Math.signum(amount) * SCROLL_STEP, vertical);
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        var element = editing == null ? null : model.element(editing);
        if (element == null) {
            return super.charTyped(event);
        }
        String next = inputs.getOrDefault(editing, "") + event.codepointAsString();
        // Le MÊME filtre que le serveur appliquera : refuser ici évite au joueur de
        // taper quelque chose qui sera silencieusement rejeté à l'envoi.
        if (element.options().accepts(next)) {
            inputs.put(editing, next);
            sendInput(editing, next, false);
        }
        return true;
    }

    private void sendInput(String element, String text, boolean submitted) {
        if (onValue != null) {
            onValue.accept(new Interaction(element, 0, text, 0, submitted));
        }
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = event.key();
        if (editing != null) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
                String current = inputs.getOrDefault(editing, "");
                if (!current.isEmpty()) {
                    String next = current.substring(0, current.length() - 1);
                    inputs.put(editing, next);
                    sendInput(editing, next, false);
                }
                return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                    || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                sendInput(editing, inputs.getOrDefault(editing, ""), true);
                editing = null;
                return true;
            }
            // Échap RELÂCHE le champ avant de fermer l'écran : sortir du menu d'un coup
            // ferait perdre la saisie sans que le joueur ait voulu quitter.
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                editing = null;
                return true;
            }
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
            String reached = focus.move(model, event.hasShiftDown() ? -1 : 1);
            // Le focus peut atterrir DANS un panneau défilant, sur un élément sorti du
            // cadre : il serait alors invisible, et le joueur au clavier tabulerait à
            // l'aveugle jusqu'à ce qu'un élément réapparaisse. Le ramener est ce qui
            // rend le parcours au clavier utilisable dans une longue page.
            if (reached != null) {
                reveal(reached);
            }
            return reached != null;
        }
        if (scrollByKey(key, event.hasControlDown())) {
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER
                || key == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE) {
            String element = focus.activate(model);
            if (element != null) {
                onClick.accept(element);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,
                                boolean doubled) {
        // Le curseur de défilement AVANT tout le reste : il est dessiné par-dessus le
        // panneau, donc il doit aussi recevoir le clic par-dessus ce qu'il recouvre.
        if (grabScrollBar(event.x(), event.y())) {
            return true;
        }
        String element = elementAt(event.x(), event.y());
        // Cliquer déplace le focus : passer de la souris au clavier ne doit pas
        // renvoyer au premier bouton du menu.
        focus.focus(model, element);
        if (element == null) {
            return super.mouseClicked(event, doubled);
        }
        pressed = element;
        return true;
    }

    /**
     * Le clic part au <b>relâchement</b>, et seulement si la souris est restée sur le
     * même élément. C'est ce que fait tout bouton du jeu, et ce qui permet d'annuler un
     * clic commencé par erreur en glissant à côté avant de lâcher.
     */
    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (dragging != null) {
            dragging = null;
            return true;
        }
        String was = pressed;
        pressed = null;
        if (was == null || !was.equals(elementAt(event.x(), event.y()))) {
            return super.mouseReleased(event);
        }
        var element = model.element(was);
        if (element != null && element.kind().interactive() && element.enabled()) {
            playClick();
        }
        if (element != null && onValue != null) {
            var rect = layout().get(was);
            switch (element.kind()) {
                case LIST -> {
                    int index = listIndexAt(element, rect, event.y());
                    if (index >= 0) {
                        onValue.accept(new Interaction(was, index, "", 0, false));
                    }
                    return true;
                }
                case TOGGLE -> {
                    boolean next = !checks.getOrDefault(was, false);
                    checks.put(was, next);
                    onValue.accept(new Interaction(was, 0, "", next ? 1 : 0, next));
                    return true;
                }
                case SLIDER -> {
                    double picked = sliderValueAt(element, rect, event.x());
                    values.put(was, picked);
                    onValue.accept(new Interaction(was, 0, "", picked, picked != 0));
                    return true;
                }
                case INPUT -> {
                    // Le focus se prend au clic, comme tout champ : c'est aussi ce qui
                    // fait qu'un seul reçoit les frappes.
                    editing = was;
                    return true;
                }
                default -> {
                }
            }
        }
        editing = null;
        onClick.accept(was);
        return true;
    }

    /**
     * Le déclic d'un bouton du jeu (story 10.12).
     *
     * <p>Un menu de blueprint ne faisait <b>aucun bruit</b>. Chaque bouton de Minecraft
     * en fait un, et son absence ne se lit pas comme un choix : elle se lit comme un clic
     * qui n'a pas été pris en compte. Le joueur reclique, et le graphe reçoit deux fois
     * ce qu'il devait recevoir une.
     *
     * <p>Réservé aux éléments <b>interactifs et activés</b> : un bouton grisé qui claque
     * dirait le contraire de ce qu'il montre.
     */
    private void playClick() {
        var client = net.minecraft.client.Minecraft.getInstance();
        client.getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // width/height sont déjà en unités d'interface : c'est la place réelle, et
        // c'est contre elle que se résolvent les pourcentages et les ancres.
        ScreenPainter.paint(graphics, font, model, layout(), 0, 0, 1,
                visuals(mouseX, mouseY));
        renderTooltip(graphics, mouseX, mouseY);
    }

    /**
     * Ce que le survol explique (story 10.12).
     *
     * <p>Dessiné <b>après</b> l'écran et par le mécanisme du jeu : une infobulle peinte
     * dans la passe des éléments passerait sous ceux dessinés plus tard, et une infobulle
     * près du bord droit sortirait de la fenêtre — {@code setTooltipForNextFrame} sait la
     * replier, ce qu'il aurait fallu réécrire ici pour un résultat moins bon.
     *
     * <p>Un élément <b>désactivé</b> garde son infobulle : c'est même là qu'elle sert le
     * plus, puisque c'est le moment où le joueur se demande pourquoi il ne peut pas
     * cliquer. Un élément masqué, lui, n'en a pas — il n'est pas sous le curseur.
     */
    private void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        // PAS elementAt : celui-là ne rend que les éléments interactifs ET activés, parce
        // que c'est ce dont le CLIC a besoin. Une infobulle, non — une étiquette et une
        // image en portent une, et un bouton grisé est précisément l'endroit où elle sert
        // le plus, puisque c'est là que le joueur se demande pourquoi il ne peut pas
        // cliquer. Réutiliser le hit-test du clic aurait rendu l'infobulle muette dans
        // les trois cas où on la cherche.
        var placed = layout();
        var elements = java.util.List.copyOf(model.elements().values());
        for (int i = elements.size() - 1; i >= 0; i--) {
            var element = elements.get(i);
            if (!element.hasTooltip()
                    || !ScreenPainter.visible(model, element, ScreenPainter.Visuals.NONE)) {
                continue;
            }
            var rect = placed.get(element.name());
            if (rect != null && rect.contains(mouseX, mouseY)
                    && insideItsClip(element, placed, mouseX, mouseY)) {
                graphics.setTooltipForNextFrame(font, componentOf(element.tooltip()),
                        mouseX, mouseY);
                return;
            }
        }
    }

    private static net.minecraft.network.chat.Component componentOf(
            fr.blueprint.core.graph.screen.ScreenText text) {
        return text.translate()
                ? net.minecraft.network.chat.Component.translatable(text.value())
                : net.minecraft.network.chat.Component.literal(text.value());
    }

    /**
     * La mise en page résolue, <b>gardée entre deux images</b> (story 10.7, AC2b).
     *
     * <p>Redessiner est inévitable à chaque image ; <b>recalculer</b> ne l'est pas. Un
     * menu ouvert et immobile refaisait la passe de disposition soixante fois par seconde
     * pour un résultat identique. Deux choses seulement la rendent fausse : un paquet
     * reçu, qui change le modèle, et un redimensionnement de la fenêtre — les deux
     * invalident ici, et rien d'autre n'a besoin d'y penser.
     *
     * <p>Le même arbitrage que le cache d'infobulle de la 5.12, où recalculer le survol à
     * chaque image coûtait des centaines de milliers de racines carrées par seconde pour
     * un texte qui ne changeait pas.
     */
    private java.util.Map<String, fr.blueprint.core.graph.screen.ScreenLayout.Rect> layout() {
        if (layout == null || layoutWidth != width || layoutHeight != height) {
            layout = solved();
            // Le contenu a pu RÉTRÉCIR — une ligne retirée, un élément masqué par le
            // graphe — et le panneau resterait alors défilé au-delà de sa fin, montrant
            // du vide que rien ne permettrait de comprendre. Une seule reprise suffit :
            // borner un décalage ne peut qu'allonger ce qui reste, jamais le raccourcir.
            if (clampScroll(layout)) {
                layout = solved();
            }
            layoutWidth = width;
            layoutHeight = height;
        }
        return layout;
    }

    /** La passe, alimentée par les décalages des deux axes. */
    private java.util.Map<String, fr.blueprint.core.graph.screen.ScreenLayout.Rect> solved() {
        return fr.blueprint.core.graph.screen.ScreenLayout.solve(model, width, height,
                new fr.blueprint.core.graph.screen.ScreenLayout.Scrolls() {
                    @Override
                    public double of(String container) {
                        return panelScroll.getOrDefault(container, 0.0);
                    }

                    @Override
                    public double xOf(String container) {
                        return panelScrollX.getOrDefault(container, 0.0);
                    }
                });
    }

    private @Nullable java.util.Map<String,
            fr.blueprint.core.graph.screen.ScreenLayout.Rect> layout;
    private int layoutWidth = -1;
    private int layoutHeight = -1;

    /**
     * Le jeu ne se met PAS en pause, comme devant un coffre : le graphe continue de
     * tourner côté serveur pendant que le menu est ouvert, et un solo figé donnerait
     * un affichage qui ne se rafraîchit plus.
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Prévenir le serveur depuis {@code removed} et non {@code onClose} : le premier est
     * appelé sur <b>tous</b> les chemins de fermeture — Échap, mais aussi la mort du
     * joueur, le retour au menu principal, ou un autre écran qui prend la place. Le
     * second ne l'est que sur Échap.
     *
     * <p>La différence n'est pas théorique : le serveur croirait l'écran encore ouvert
     * après une mort, et accepterait un clic dessus venu d'un client modifié (FR52).
     */
    @Override
    public void removed() {
        onClosed.run();
        super.removed();
    }
}
