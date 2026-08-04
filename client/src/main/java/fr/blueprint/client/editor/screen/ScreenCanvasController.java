package fr.blueprint.client.editor.screen;

import fr.blueprint.client.editor.SelectionModel;
import fr.blueprint.client.editor.history.UndoStack;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.Diagnostic;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.NodeTypeLookup;
import fr.blueprint.core.graph.ScreenOps;
import fr.blueprint.core.graph.screen.ElementKind;
import fr.blueprint.core.graph.screen.Screen;
import fr.blueprint.core.graph.screen.ScreenElement;
import fr.blueprint.core.graph.screen.ScreenLayout;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * La logique d'interaction du concepteur d'écrans (story 10.2) : hit-test, sélection,
 * déplacement avec guides, redimensionnement par poignées, ordre de superposition.
 *
 * <p>Le pendant de {@code CanvasController}, et volontairement <b>pas</b> une copie de
 * celui-ci : il partage {@link SelectionModel}, {@link UndoStack} et les
 * {@code EditOperation}. Deux moteurs séparés divergeraient au premier correctif.
 *
 * <p>Travaille en <b>unités d'interface</b> dans la surface de conception 320×180 — la
 * plus petite fenêtre réellement rencontrée. Concevoir dans le pire cas est ce qui
 * garantit que le résultat tient partout ; le widget ne fait que convertir écran ↔
 * unités et dessiner.
 */
public final class ScreenCanvasController {

    /**
     * {@code REORDER} est le geste des enfants <b>rangés</b> par leur conteneur : leur
     * place ne s'écrit pas, elle se déduit de leur rang. Y glisser écrirait un x/y sans
     * le moindre effet visible — et un geste sans effet est ce qui fait douter d'un
     * outil plus sûrement qu'un geste refusé (story 10.10).
     */
    public enum Gesture { NONE, MOVE, RUBBER, RESIZE, REORDER }

    /** Les huit poignées, dans le sens horaire depuis le coin haut-gauche. */
    public enum Handle {
        NW(0, 0), N(0.5, 0), NE(1, 0), E(1, 0.5), SE(1, 1), S(0.5, 1), SW(0, 1), W(0, 0.5);

        private final double fx;
        private final double fy;

        Handle(double fx, double fy) {
            this.fx = fx;
            this.fy = fy;
        }

        public double fractionX() {
            return fx;
        }

        public double fractionY() {
            return fy;
        }

        boolean movesLeft() {
            return fx == 0;
        }

        boolean movesTop() {
            return fy == 0;
        }

        boolean freeX() {
            return fx != 0.5;
        }

        boolean freeY() {
            return fy != 0.5;
        }
    }

    /**
     * Demi-côté d'une poignée, <b>en pixels</b>. Assez grand pour se saisir sans loupe.
     *
     * <p>Il valait 2,5 <i>unités</i>, ce qui ne veut dire quelque chose qu'à 1:1. Depuis
     * que le concepteur zoome, une poignée exprimée en unités mesure 0,6 pixel au zoom le
     * plus large — elle devient <b>insaisissable</b> — et vingt pixels au plus serré, où
     * elle recouvre l'élément qu'elle sert à redimensionner. Une cible de souris se mesure
     * là où la souris vit : à l'écran.
     */
    public static final double HANDLE_PIXELS = 2.5;

    /** La taille de la poignée à 1:1, c'est-à-dire quand un pixel vaut une unité. */
    public static final double HANDLE_RADIUS = HANDLE_PIXELS;

    /** Pas de la grille, en unités. Deux, pas seize : un écran fait 320 de large. */
    public static final double GRID_STEP = 2;

    private final Blueprint blueprint;
    private final NodeTypeLookup lookup;
    private final UndoStack history;
    private final SelectionModel<String> selection = new SelectionModel<>();

    private String screenName;
    /**
     * La taille du canevas, EN UNITÉS — celle de la fenêtre qu'on simule.
     *
     * <p>Elle valait 320×180 en dur, c'est-à-dire la plus petite fenêtre possible.
     * On concevait donc toujours dans le pire cas, sans jamais voir ce que les ancres
     * et les pourcentages donnent à la taille réelle du joueur — or c'est exactement
     * ce qu'ils servent à exprimer. La zone garantie reste dessinée à l'intérieur, et
     * reste la référence de la validation : ce sont deux choses distinctes, que le
     * même nombre masquait.
     */
    private double viewportWidth = Screen.SAFE_WIDTH;
    private double viewportHeight = Screen.SAFE_HEIGHT;
    /**
     * Combien d'unités vaut un pixel de l'écran, au zoom courant.
     *
     * <p>C'est une propriété de la VUE, comme la taille du canevas simulée juste au-dessus,
     * et elle vit ici pour la même raison : les tolérances de geste — poignées, accroche —
     * s'expriment en pixels et doivent être converties au même endroit pour tout le monde.
     * Deux conversions séparées donneraient une poignée qui se dessine à un endroit et se
     * saisit à un autre, précisément le défaut que {@link DesignSurface} existe pour
     * empêcher.
     */
    private double unitsPerPixel = 1;
    private boolean snapEnabled = true;
    private @Nullable Runnable onMutation;
    private @Nullable Diagnostic lastRefusal;

    private Gesture gesture = Gesture.NONE;
    private @Nullable Handle activeHandle;
    /** Rectangles au moment de la presse : le glisser part toujours de l'origine. */
    private final Map<String, ScreenLayout.Rect> grabbed = new HashMap<>();
    /** L'élément réellement saisi : celui que les guides suivent. */
    private @Nullable String primary;
    private double grabX;
    private double grabY;
    /** Shift au moment de la presse : un rectangle élastique additif AJOUTE. */
    private boolean rubberAdditive;
    private double rubberStartX;
    private double rubberStartY;
    private double rubberEndX;
    private double rubberEndY;
    private List<AlignmentGuides.Guide> guides = List.of();

    public ScreenCanvasController(Blueprint blueprint, NodeTypeLookup lookup,
                                  UndoStack history, String screenName) {
        this.blueprint = blueprint;
        this.lookup = lookup;
        this.history = history;
        this.screenName = screenName;
    }

    // ----------------------------------------------------------------- lecture

    public Blueprint blueprint() {
        return blueprint;
    }

    public String screenName() {
        return screenName;
    }

    public void setScreenName(String name) {
        if (!name.equals(screenName)) {
            screenName = name;
            selection.clear();
            cancelGesture();
        }
    }

    /** L'écran courant, ou {@code null} s'il a disparu (supprimé, annulé). */
    public @Nullable Screen screen() {
        return blueprint.screen(screenName);
    }

    public SelectionModel<String> selection() {
        return selection;
    }

    public Gesture gesture() {
        return gesture;
    }

    public List<AlignmentGuides.Guide> guides() {
        return guides;
    }

    /**
     * Les tailles de fenêtre réellement rencontrées, en unités d'interface.
     *
     * <p>Ce ne sont pas des chiffres ronds choisis au hasard : ce sont les résolutions
     * courantes divisées par leur <i>GUI scale</i>. 1280×720 en <i>scale</i> 4 donne
     * 320×180 ; 1920×1080 en <i>scale</i> 3 donne 640×360 ; en <i>scale</i> 2, 960×540.
     * Un menu doit tenir dans <b>tous</b>, et c'est en basculant entre eux qu'on le voit.
     */
    public enum Viewport {
        SMALL(Screen.SAFE_WIDTH, Screen.SAFE_HEIGHT),
        MEDIUM(480, 270),
        LARGE(640, 360),
        HUGE(960, 540),
        WIDE(1280, 720),
        /** 1080p en échelle 1, ou un écran 4K en échelle 2 — la plus grande rencontrée. */
        FULL(1920, 1080);

        /**
         * Ce sur quoi le <b>concepteur</b> s'ouvre. Ce n'est pas le défaut du modèle —
         * celui-là reste 320×180, la fenêtre garantie, et c'est lui que le validateur
         * mesure. C'est le défaut de l'<i>outil</i> : on dessine au large, puis on
         * vérifie en réduisant.
         *
         * <p>Conçu au large, un menu ne tient chez personne d'autre si ses éléments
         * restent ancrés en haut à gauche — c'est pourquoi l'ancre automatique de
         * {@code addElement} n'est pas un agrément mais la contrepartie obligatoire de
         * ce défaut.
         */
        public static final Viewport DESIGN_DEFAULT = FULL;

        private final int width;
        private final int height;

        Viewport(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }
    }

    public double viewportWidth() {
        return viewportWidth;
    }

    public double viewportHeight() {
        return viewportHeight;
    }

    /**
     * Change la taille du canevas. Rien n'est modifié dans le blueprint : c'est une
     * <b>simulation</b>. Un élément ancré à droite se déplace à l'écran, mais son
     * décalage écrit ne bouge pas — c'est précisément ce qu'on veut voir.
     */
    public void setViewport(double width, double height) {
        this.viewportWidth = Math.max(Screen.SAFE_WIDTH, width);
        this.viewportHeight = Math.max(Screen.SAFE_HEIGHT, height);
    }

    public void setViewport(Viewport viewport) {
        setViewport(viewport.width(), viewport.height());
    }

    /** Le préréglage courant, ou {@code null} si le canevas suit l'écran du joueur. */
    public @Nullable Viewport viewportPreset() {
        for (Viewport viewport : Viewport.values()) {
            if (viewport.width() == viewportWidth && viewport.height() == viewportHeight) {
                return viewport;
            }
        }
        return null;
    }

    /**
     * Dit au contrôleur de combien la vue grossit, pour que ses tolérances de geste
     * restent constantes <b>à l'écran</b>. Appelé par le widget, en un seul endroit.
     */
    public void setUnitsPerPixel(double units) {
        this.unitsPerPixel = Math.max(1e-6, units);
    }

    public double unitsPerPixel() {
        return unitsPerPixel;
    }

    /** Le demi-côté d'une poignée en unités, au zoom courant. */
    public double handleRadius() {
        return HANDLE_PIXELS * unitsPerPixel;
    }

    /** La distance d'accroche en unités, au zoom courant. */
    public double snapTolerance() {
        return AlignmentGuides.SNAP_PIXELS * unitsPerPixel;
    }

    public boolean snapEnabled() {
        return snapEnabled;
    }

    public void toggleSnap() {
        snapEnabled = !snapEnabled;
    }

    /**
     * Enregistre l'inverse d'une opération appliquée en dehors du contrôleur — la
     * création d'un écran, par exemple. Sans cela, elle échapperait au Ctrl+Z partagé.
     */
    public void historyRecord(EditOperation inverse) {
        history.record(inverse);
    }

    public void setOnMutation(@Nullable Runnable onMutation) {
        this.onMutation = onMutation;
    }

    /** Le dernier refus, consommé : l'appelant l'affiche une fois puis l'oublie. */
    public @Nullable Diagnostic takeRefusal() {
        Diagnostic refusal = lastRefusal;
        lastRefusal = null;
        return refusal;
    }

    /**
     * Les rectangles de tout l'écran, par la <b>même passe que le rendu</b>. Deux
     * chemins de résolution divergeraient au premier conteneur qui range ses enfants,
     * et le concepteur montrerait un menu que le jeu ne dessine pas (story 10.10).
     */
    public java.util.Map<String, ScreenLayout.Rect> rects() {
        Screen screen = screen();
        return screen == null ? java.util.Map.of()
                : ScreenLayout.solve(screen, viewportWidth, viewportHeight);
    }

    public ScreenLayout.@Nullable Rect rectOf(String element) {
        return rects().get(element);
    }

    /**
     * L'élément sous le point, ou {@code null}. Parcourt à l'envers de l'ordre de
     * dessin : c'est celui du dessus qu'on veut saisir, pas celui qu'il recouvre.
     */
    public @Nullable String hitTest(double x, double y) {
        Screen screen = screen();
        if (screen == null) {
            return null;
        }
        var rects = rects();
        List<ScreenElement> elements = List.copyOf(screen.elements().values());
        for (int i = elements.size() - 1; i >= 0; i--) {
            ScreenElement element = elements.get(i);
            ScreenLayout.Rect rect = rects.get(element.name());
            if (rect != null && rect.contains(x, y)) {
                return element.name();
            }
        }
        return null;
    }

    /**
     * La poignée sous le point, quand un seul élément est sélectionné. Les poignées
     * priment sur le corps : sans cela, un élément plus petit que ses poignées ne
     * pourrait plus être redimensionné.
     */
    public @Nullable Handle handleAt(double x, double y) {
        if (selection.size() != 1) {
            return null;
        }
        String only = selection.ids().iterator().next();
        ScreenLayout.Rect rect = rectOf(only);
        if (rect == null) {
            return null;
        }
        double radius = handleRadius();
        for (Handle handle : operableHandles(only)) {
            double hx = rect.x() + rect.width() * handle.fractionX();
            double hy = rect.y() + rect.height() * handle.fractionY();
            if (Math.abs(x - hx) <= radius && Math.abs(y - hy) <= radius) {
                return handle;
            }
        }
        return null;
    }

    public ScreenLayout.Rect rubberBand() {
        return new ScreenLayout.Rect(Math.min(rubberStartX, rubberEndX),
                Math.min(rubberStartY, rubberEndY),
                Math.abs(rubberEndX - rubberStartX), Math.abs(rubberEndY - rubberStartY));
    }

    // ------------------------------------------------------------------ gestes

    public void press(double x, double y, boolean additive) {
        Screen screen = screen();
        if (screen == null) {
            return;
        }
        history.beginGesture();
        grabX = x;
        grabY = y;
        guides = List.of();

        Handle handle = handleAt(x, y);
        if (handle != null && !additive) {
            activeHandle = handle;
            gesture = Gesture.RESIZE;
            primary = selection.ids().iterator().next();
            snapshotSelection(screen);
            return;
        }

        String hit = hitTest(x, y);
        selection.click(hit, additive);
        if (hit == null) {
            gesture = Gesture.RUBBER;
            rubberAdditive = additive;
            rubberStartX = x;
            rubberStartY = y;
            rubberEndX = x;
            rubberEndY = y;
            // Aucune mutation ne suivra : refermer tout de suite évite que le geste
            // reste ouvert et avale les éditions suivantes dans la même annulation.
            history.endGesture();
            return;
        }
        primary = topmostUnselectedAncestorOr(screen, hit);
        gesture = arrangedByParent(screen, primary) ? Gesture.REORDER : Gesture.MOVE;
        snapshotSelection(screen);
    }

    /** Le parent de cet élément range-t-il ses enfants lui-même ? */
    private boolean arrangedByParent(Screen screen, @Nullable String name) {
        ScreenElement element = name == null ? null : screen.element(name);
        if (element == null || element.parent() == null) {
            return false;
        }
        ScreenElement container = screen.element(element.parent());
        return container != null && container.arranges();
    }

    /**
     * Les poignées qui ont un effet sur cet élément. Une poignée de largeur sur un
     * enfant {@code fill} d'une colonne ne ferait rien : la montrer quand même et la
     * laisser inerte est pire que ne pas la montrer — l'auteur croit son outil cassé.
     */
    public java.util.Set<Handle> operableHandles(String name) {
        Screen screen = screen();
        ScreenElement element = screen == null ? null : screen.element(name);
        if (element == null) {
            return java.util.Set.of();
        }
        boolean freeX = sizeIsWritable(screen, element, true);
        boolean freeY = sizeIsWritable(screen, element, false);
        java.util.Set<Handle> out = new LinkedHashSet<>();
        for (Handle handle : Handle.values()) {
            if ((handle.freeX() && freeX) || (handle.freeY() && freeY)) {
                out.add(handle);
            }
        }
        return out;
    }

    /**
     * Redimensionner cet élément sur cet axe écrit-il quelque chose de visible ? Non si
     * sa taille est décidée par son conteneur ({@code fill} sur l'axe rangé, étirement
     * sur l'axe transverse) ou par son propre contenu ({@code hug}).
     */
    private boolean sizeIsWritable(Screen screen, ScreenElement element, boolean horizontal) {
        var extent = horizontal ? element.width() : element.height();
        if (extent.mode() == fr.blueprint.core.graph.screen.Extent.Mode.FILL
                || extent.mode() == fr.blueprint.core.graph.screen.Extent.Mode.HUG) {
            return false;
        }
        ScreenElement container = element.parent() == null ? null
                : screen.element(element.parent());
        if (container == null || !container.arranges()) {
            return true;
        }
        // Sur l'axe transverse d'un conteneur en étirement, la taille écrite est ignorée.
        boolean crossAxis = container.layout().vertical() == horizontal;
        return !(crossAxis && container.layout().cross()
                == fr.blueprint.core.graph.screen.LayoutSpec.Cross.STRETCH);
    }

    public void drag(double x, double y) {
        Screen screen = screen();
        if (screen == null) {
            return;
        }
        switch (gesture) {
            case RUBBER -> {
                rubberEndX = x;
                rubberEndY = y;
            }
            case MOVE -> moveTo(screen, x - grabX, y - grabY);
            case REORDER -> reorderTo(screen, x, y);
            case RESIZE -> resizeTo(screen, x, y);
            case NONE -> { }
        }
    }

    public void release() {
        if (gesture == Gesture.RUBBER) {
            Screen screen = screen();
            if (screen != null) {
                ScreenLayout.Rect band = rubberBand();
                List<String> caught = new ArrayList<>();
                var placed = rects();
                for (ScreenElement element : screen.elements().values()) {
                    ScreenLayout.Rect rect = placed.get(element.name());
                    if (rect == null) {
                        continue;
                    }
                    if (rect.x() < band.right() && rect.right() > band.x()
                            && rect.y() < band.bottom() && rect.bottom() > band.y()) {
                        caught.add(element.name());
                    }
                }
                selection.selectAll(caught, rubberAdditive);
            }
        } else if (gesture != Gesture.NONE) {
            history.endGesture();
        }
        gesture = Gesture.NONE;
        activeHandle = null;
        grabbed.clear();
        guides = List.of();
    }

    private void cancelGesture() {
        if (gesture != Gesture.NONE && gesture != Gesture.RUBBER) {
            history.endGesture();
        }
        gesture = Gesture.NONE;
        activeHandle = null;
        grabbed.clear();
        guides = List.of();
    }

    private void snapshotSelection(Screen screen) {
        grabbed.clear();
        var placed = rects();
        for (String name : movable()) {
            ScreenLayout.Rect rect = placed.get(name);
            if (rect != null) {
                grabbed.put(name, rect);
            }
        }
    }

    /**
     * Les éléments de la sélection qu'il faut réellement déplacer : ceux dont aucun
     * ancêtre n'est lui aussi sélectionné.
     *
     * <p>Un enfant est positionné <b>dans</b> son parent : déplacer les deux appliquerait
     * le décalage deux fois, et l'enfant s'échapperait de son cadre à chaque glisser —
     * d'autant plus vite qu'on répète le geste.
     */
    private Set<String> movable() {
        Screen screen = screen();
        if (screen == null) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String name : selection.ids()) {
            if (!hasSelectedAncestor(screen, name)) {
                out.add(name);
            }
        }
        return out;
    }

    /**
     * L'ancêtre sélectionné le plus haut de {@code name}, ou {@code name} lui-même.
     * Saisir un enfant dont le parent est aussi sélectionné doit déplacer le parent :
     * c'est lui qui porte le groupe.
     */
    private @Nullable String topmostUnselectedAncestorOr(Screen screen, @Nullable String name) {
        if (name == null) {
            return null;
        }
        String best = name;
        Set<String> seen = new java.util.HashSet<>();
        seen.add(name);
        ScreenElement element = screen.element(name);
        String cursor = element == null ? null : element.parent();
        while (cursor != null && seen.add(cursor)) {
            if (selection.isSelected(cursor)) {
                best = cursor;
            }
            ScreenElement up = screen.element(cursor);
            cursor = up == null ? null : up.parent();
        }
        return best;
    }

    private boolean hasSelectedAncestor(Screen screen, String name) {
        Set<String> seen = new java.util.HashSet<>();
        seen.add(name);
        ScreenElement element = screen.element(name);
        String cursor = element == null ? null : element.parent();
        while (cursor != null && seen.add(cursor)) {
            if (selection.isSelected(cursor)) {
                return true;
            }
            ScreenElement up = screen.element(cursor);
            cursor = up == null ? null : up.parent();
        }
        return false;
    }

    // ------------------------------------------------------------ déplacement

    private void moveTo(Screen screen, double rawDx, double rawDy) {
        // Les guides suivent l'élément SAISI, pas le premier de la sélection : c'est
        // celui que l'auteur regarde, et voir la ligne apparaître sur un autre membre
        // du groupe ne lui apprendrait rien.
        ScreenLayout.Rect origin = primary == null ? null : grabbed.get(primary);
        if (origin == null) {
            origin = grabbed.values().stream().findFirst().orElse(null);
        }
        if (origin == null) {
            return;
        }
        ScreenLayout.Rect wanted = new ScreenLayout.Rect(
                snap(origin.x() + rawDx), snap(origin.y() + rawDy),
                origin.width(), origin.height());

        // Les guides s'alignent sur ce qui NE bouge pas : inclure un élément déplacé
        // ferait s'accrocher la sélection à elle-même, donc ne plus bouger du tout.
        AlignmentGuides.Result snapped =
                AlignmentGuides.snap(wanted, neighbours(screen), snapTolerance());
        guides = snapped.guides();
        double dx = snapped.rect().x() - origin.x();
        double dy = snapped.rect().y() - origin.y();

        for (Map.Entry<String, ScreenLayout.Rect> entry : grabbed.entrySet()) {
            ScreenElement element = screen.element(entry.getKey());
            if (element == null) {
                continue;
            }
            ScreenLayout.Rect from = entry.getValue();
            place(screen, element, new ScreenLayout.Rect(
                    from.x() + dx, from.y() + dy, from.width(), from.height()));
        }
    }

    /**
     * Glisser un enfant rangé change son <b>rang</b> parmi ses frères, et le conteneur
     * les repose tous. Le rang est appliqué pendant le glisser, pas au relâchement :
     * l'élément se range sous le curseur à mesure, et l'auteur voit le résultat au lieu
     * d'une ligne d'insertion qui promet quelque chose.
     */
    private void reorderTo(Screen screen, double x, double y) {
        if (primary == null) {
            return;
        }
        ScreenElement element = screen.element(primary);
        if (element == null || element.parent() == null) {
            return;
        }
        ScreenElement container = screen.element(element.parent());
        if (container == null || !container.arranges()) {
            return;
        }
        // Les frères dans l'ordre de dessin, avec leur index GLOBAL : c'est celui-là
        // que ReorderElement manipule, et les frères n'y sont pas forcément contigus.
        List<String> order = List.copyOf(screen.elements().keySet());
        List<Integer> siblings = new ArrayList<>();
        int from = -1;
        for (int i = 0; i < order.size(); i++) {
            ScreenElement candidate = screen.element(order.get(i));
            if (candidate != null && element.parent().equals(candidate.parent())) {
                if (order.get(i).equals(primary)) {
                    from = siblings.size();
                }
                siblings.add(i);
            }
        }
        if (from < 0 || siblings.size() < 2) {
            return;
        }

        var placed = rects();
        boolean vertical = container.layout().vertical();
        double point = vertical ? y : x;
        int target = 0;
        for (int rank = 0; rank < siblings.size(); rank++) {
            if (rank == from) {
                continue;
            }
            ScreenLayout.Rect rect = placed.get(order.get(siblings.get(rank)));
            if (rect == null) {
                continue;
            }
            double centre = vertical ? rect.y() + rect.height() / 2
                    : rect.x() + rect.width() / 2;
            if (point > centre) {
                target++;
            }
        }
        if (target != from) {
            applyTracked(new ScreenOps.ReorderElement(screenName, primary,
                    siblings.get(target) - siblings.get(from)));
        }
    }

    private List<ScreenLayout.Rect> neighbours(Screen screen) {
        List<ScreenLayout.Rect> out = new ArrayList<>();
        var placed = rects();
        for (ScreenElement element : screen.elements().values()) {
            ScreenLayout.Rect rect = placed.get(element.name());
            if (rect != null && !selection.isSelected(element.name())) {
                out.add(rect);
            }
        }
        return out;
    }

    // ------------------------------------------------------- redimensionnement

    private void resizeTo(Screen screen, double x, double y) {
        if (activeHandle == null || grabbed.size() != 1) {
            return;
        }
        Map.Entry<String, ScreenLayout.Rect> only = grabbed.entrySet().iterator().next();
        ScreenElement element = screen.element(only.getKey());
        if (element == null) {
            return;
        }
        ScreenLayout.Rect from = only.getValue();
        double left = from.x();
        double top = from.y();
        double right = from.right();
        double bottom = from.bottom();

        if (activeHandle.freeX()) {
            if (activeHandle.movesLeft()) {
                left = Math.min(snap(x), right - ScreenElement.MIN_SIZE);
            } else {
                right = Math.max(snap(x), left + ScreenElement.MIN_SIZE);
            }
        }
        if (activeHandle.freeY()) {
            if (activeHandle.movesTop()) {
                top = Math.min(snap(y), bottom - ScreenElement.MIN_SIZE);
            } else {
                bottom = Math.max(snap(y), top + ScreenElement.MIN_SIZE);
            }
        }

        // Les guides valaient pour le déplacement seulement — alors que caler un bord
        // sur celui du voisin est justement ce qu'on cherche en redimensionnant. Le
        // rectangle est accroché puis les bords LIBRES seuls en reprennent la valeur :
        // sans cela, tirer la poignée est reculerait aussi le bord ouest.
        ScreenLayout.Rect wanted = new ScreenLayout.Rect(left, top, right - left, bottom - top);
        AlignmentGuides.Result snapped = AlignmentGuides.snapEdges(wanted, neighbours(screen),
                activeHandle.freeX() && activeHandle.movesLeft(),
                activeHandle.freeX() && !activeHandle.movesLeft(),
                activeHandle.freeY() && activeHandle.movesTop(),
                activeHandle.freeY() && !activeHandle.movesTop(),
                snapTolerance());
        guides = snapped.guides();
        place(screen, element, snapped.rect());
    }

    /**
     * Écrit un rectangle sur un élément, <b>confiné dans son parent</b> (AC4). Un
     * enfant qui déborde de son cadre est invisible en jeu — le parent découpe — et
     * l'auteur ne comprendrait pas pourquoi son bouton a disparu.
     */
    private boolean place(Screen screen, ScreenElement element, ScreenLayout.Rect target) {
        ScreenLayout.Rect parent = ScreenLayout.parentRect(screen, element,
                viewportWidth, viewportHeight);
        ScreenLayout.Rect bounded = element.parent() == null ? target : confine(parent, target);
        ScreenElement placed = ScreenLayout.placedIn(parent, element, bounded);
        return applyTracked(new ScreenOps.SetElement(screenName, placed));
    }

    /**
     * Confine un enfant dans son parent : sortir du cadre le rendrait invisible en jeu,
     * et l'auteur ne comprendrait pas où son bouton est passé.
     *
     * <p>À la <b>racine</b>, en revanche, rien n'est confiné. Le modèle considère qu'un
     * élément débordant des 320×180 garantis reste valide — c'est un simple
     * avertissement (ELEMENT_OUTSIDE_SAFE_AREA), pas une erreur. Le concepteur l'a
     * d'abord bloqué, et cela rendait impossible ce que le validateur autorise : un menu
     * qui vise volontairement les grandes fenêtres, ce que les bornes des {@code Extent}
     * existent précisément pour permettre.
     */
    private static ScreenLayout.Rect confine(ScreenLayout.Rect parent, ScreenLayout.Rect target) {
        double width = Math.min(target.width(), parent.width());
        double height = Math.min(target.height(), parent.height());
        return new ScreenLayout.Rect(
                Math.clamp(target.x(), parent.x(), parent.right() - width),
                Math.clamp(target.y(), parent.y(), parent.bottom() - height),
                width, height);
    }

    private double snap(double value) {
        return snapEnabled ? Math.round(value / GRID_STEP) * GRID_STEP : value;
    }

    // ------------------------------------------------------------------ actions

    /**
     * Pose un élément neuf. Le nom est dérivé du type et suffixé jusqu'à être libre :
     * demander un nom avant de pouvoir poser quoi que ce soit couperait le geste.
     */
    public @Nullable String addElement(ElementKind kind, double x, double y) {
        Screen screen = screen();
        if (screen == null) {
            return null;
        }
        String name = freshName(screen.elements().keySet(),
                kind.name().toLowerCase(java.util.Locale.ROOT));
        String parent = hitContainer(screen, x, y);
        ScreenLayout.Rect area = parent == null || rects().get(parent) == null
                ? new ScreenLayout.Rect(0, 0, viewportWidth, viewportHeight)
                : rects().get(parent);

        fr.blueprint.core.graph.screen.Extent width = defaultWidth(kind);
        fr.blueprint.core.graph.screen.Extent height = defaultHeight(kind);
        // Le point donné est le CENTRE du dépôt, et non le coin : on pose « là », et un
        // élément dont seul le coin obéit au geste paraît toujours décalé.
        ScreenLayout.Rect target = new ScreenLayout.Rect(
                snap(x - width.resolve(area.width()) / 2),
                snap(y - height.resolve(area.height()) / 2),
                width.resolve(area.width()), height.resolve(area.height()));

        ScreenElement element = ScreenLayout.placedIn(area,
                ScreenElement.of(name, kind, 0, 0, 1, 1)
                        .resized(width, height)
                        .withParent(parent)
                        .withAnchor(anchorFor(area, target)),
                target);

        history.beginGesture();
        try {
            if (!applyTracked(new ScreenOps.AddElement(screenName, element))) {
                return null;
            }
        } finally {
            history.endGesture();
        }
        selection.selectAll(List.of(name), false);
        return name;
    }

    /**
     * L'ancre d'un élément qu'on vient de poser : celle du <b>tiers</b> où il atterrit.
     *
     * <p>Tout élément neuf naissait ancré en haut à gauche ({@code ScreenElement.of}), ce
     * qui ne se remarquait pas tant qu'on dessinait sur 320×180 — l'ancre y change peu de
     * chose. Sur un canevas de 1920×1080, un bouton posé à droite s'écrit « à 1600 du bord
     * gauche » : chez un joueur en 480×270, il est purement <b>hors écran</b>. L'auteur ne
     * l'apprenait qu'à l'ouverture du menu, chez quelqu'un d'autre.
     *
     * <p>Ancré sur le tiers où il a été posé, il reste où l'auteur l'a mis à toutes les
     * tailles de fenêtre. C'est ce que les ancres existent pour faire ; elles n'étaient
     * simplement jamais posées à la création.
     */
    static fr.blueprint.core.graph.screen.Anchor anchorFor(ScreenLayout.Rect area,
                                                           ScreenLayout.Rect target) {
        return fr.blueprint.core.graph.screen.Anchor.of(
                third(target.x() + target.width() / 2, area.x(), area.width()),
                third(target.y() + target.height() / 2, area.y(), area.height()));
    }

    private static int third(double value, double start, double length) {
        if (length <= 0) {
            return 0;
        }
        double fraction = (value - start) / length;
        return fraction < 1.0 / 3 ? 0 : (fraction < 2.0 / 3 ? 1 : 2);
    }

    /**
     * La largeur d'un élément neuf.
     *
     * <p>Un <b>conteneur</b> naît en pourcentage : un panneau de fond doit épouser la
     * fenêtre, et le poser en unités fixes sur un canevas de 1920 donnerait un cadre
     * quatre fois trop grand chez la plupart des joueurs. Il naissait à 100 unités de
     * large sur 20 de haut — une lamelle, quelle que soit la taille visée.
     *
     * <p>Tout le reste naît en <b>unités fixes</b>, et c'est délibéré : la police de
     * Minecraft mesure 9 unités, toujours. Un bouton proportionnel au canevas serait
     * illisible en 320×180 — son texte ne rapetisse pas avec lui. Ce partage n'est pas un
     * compromis, c'est la conséquence directe d'une police de taille fixe.
     */
    private static fr.blueprint.core.graph.screen.Extent defaultWidth(ElementKind kind) {
        return kind.container()
                ? fr.blueprint.core.graph.screen.Extent.percent(0.4, 80, 0)
                : fr.blueprint.core.graph.screen.Extent.of(60);
    }

    private static fr.blueprint.core.graph.screen.Extent defaultHeight(ElementKind kind) {
        if (kind.container()) {
            return fr.blueprint.core.graph.screen.Extent.percent(0.35, 40, 0);
        }
        return fr.blueprint.core.graph.screen.Extent.of(kind == ElementKind.LABEL ? 10 : 20);
    }

    /** Le conteneur le plus haut sous le point : poser dans un panneau y rattache. */
    private @Nullable String hitContainer(Screen screen, double x, double y) {
        var placed = rects();
        List<ScreenElement> elements = List.copyOf(screen.elements().values());
        for (int i = elements.size() - 1; i >= 0; i--) {
            ScreenElement element = elements.get(i);
            ScreenLayout.Rect rect = placed.get(element.name());
            if (element.kind().container() && rect != null && rect.contains(x, y)) {
                return element.name();
            }
        }
        return null;
    }

    private static String freshName(Set<String> taken, String base) {
        if (!taken.contains(base)) {
            return base;
        }
        for (int i = 2; ; i++) {
            String candidate = base + "_" + i;
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }
    }

    /** Profondeur d'imbrication : elle sert à copier les parents avant leurs enfants. */
    private static int depthOf(Screen screen, String name) {
        int depth = 0;
        Set<String> seen = new java.util.HashSet<>();
        seen.add(name);
        ScreenElement element = screen.element(name);
        String cursor = element == null ? null : element.parent();
        while (cursor != null && seen.add(cursor)) {
            depth++;
            ScreenElement up = screen.element(cursor);
            cursor = up == null ? null : up.parent();
        }
        return depth;
    }

    public boolean deleteSelection() {
        Screen screen = screen();
        if (screen == null || selection.isEmpty()) {
            return false;
        }
        boolean any = false;
        history.beginGesture();
        try {
            // Les racines d'abord : supprimer un conteneur emporte ses descendants, et
            // viser ensuite un enfant déjà parti ferait échouer l'opération pour rien.
            for (String name : movable()) {
                any |= applyTracked(new ScreenOps.RemoveElement(screenName, name));
            }
        } finally {
            history.endGesture();
        }
        if (any) {
            selection.clear();
        }
        return any;
    }

    /** Duplique la sélection, décalée, avec les mêmes parents. */
    public boolean duplicateSelection() {
        Screen screen = screen();
        if (screen == null || selection.isEmpty()) {
            return false;
        }
        // Les noms des copies sont réservés AVANT d'écrire quoi que ce soit : un enfant
        // dupliqué avec son parent doit pointer vers la COPIE du parent. Le rattacher à
        // l'original donnerait deux enfants dans le cadre d'origine et un cadre copié
        // vide — le contraire de ce qu'on voit à l'écran au moment du geste.
        // Du moins profond au plus profond : `AddElement` refuse un enfant dont le
        // parent n'existe pas encore, et la sélection n'a aucune raison d'être ordonnée.
        List<String> sources = new ArrayList<>(selection.ids());
        sources.sort(java.util.Comparator.comparingInt(name -> depthOf(screen, name)));
        Map<String, String> renames = new java.util.LinkedHashMap<>();
        Set<String> taken = new java.util.HashSet<>(screen.elements().keySet());
        for (String name : sources) {
            String fresh = freshName(taken, name);
            taken.add(fresh);
            renames.put(name, fresh);
        }

        List<String> created = new ArrayList<>();
        history.beginGesture();
        try {
            for (String name : sources) {
                ScreenElement source = screen.element(name);
                if (source == null) {
                    continue;
                }
                String newParent = renames.getOrDefault(source.parent(), source.parent());
                ScreenElement copy = source.renamed(renames.get(name))
                        .withParent(newParent)
                        // Un enfant suit son parent copié : le décaler aussi doublerait
                        // l'écart et sortirait le groupe de sa forme.
                        .movedTo(renames.containsKey(source.parent()) ? source.x()
                                        : source.x() + GRID_STEP * 2,
                                renames.containsKey(source.parent()) ? source.y()
                                        : source.y() + GRID_STEP * 2);
                if (applyTracked(new ScreenOps.AddElement(screenName, copy))) {
                    created.add(copy.name());
                }
            }
        } finally {
            history.endGesture();
        }
        if (created.isEmpty()) {
            return false;
        }
        selection.selectAll(created, false);
        return true;
    }

    /** Monte ({@code +1}) ou descend ({@code -1}) la sélection dans l'ordre de dessin. */
    public boolean reorderSelection(int delta) {
        if (screen() == null || selection.isEmpty()) {
            return false;
        }
        boolean any = false;
        history.beginGesture();
        try {
            for (String name : selection.ids()) {
                any |= applyTracked(new ScreenOps.ReorderElement(screenName, name, delta));
            }
        } finally {
            history.endGesture();
        }
        return any;
    }

    /**
     * Le nom est-il libre ? Sert au contrôle <b>en direct</b> du panneau de propriétés
     * (AC7) : un doublon doit se voir pendant la frappe, pas au moment de valider.
     */
    // ------------------------------------------------------ styles nommés (10.10)

    /** Les styles de l'écran courant, dans leur ordre de création. */
    public Map<String, fr.blueprint.core.graph.screen.ElementStyle> styles() {
        Screen screen = screen();
        return screen == null ? Map.of() : screen.styles();
    }

    /**
     * Crée un style nommé à partir du <b>premier élément sélectionné</b>, et y attache
     * toute la sélection. C'est le geste qui rend les styles réels plutôt que théoriques :
     * on peint un bouton comme on le veut, puis on dit « les autres comme celui-là ».
     */
    public @Nullable String createStyleFromSelection() {
        Screen screen = screen();
        if (screen == null || selection.isEmpty()) {
            return null;
        }
        ScreenElement source = screen.element(selection.ids().iterator().next());
        if (source == null) {
            return null;
        }
        String name = freshName(screen.styles().keySet(), "style");
        history.beginGesture();
        try {
            if (!applyTracked(new ScreenOps.SetScreen(
                    screen.withStyle(name, screen.styleOf(source))))) {
                return null;
            }
            applyStyleToSelection(name);
        } finally {
            history.endGesture();
        }
        return name;
    }

    /** Attache un style nommé à toute la sélection ; un nom vide l'en détache. */
    public boolean applyStyleToSelection(String styleName) {
        Screen screen = screen();
        if (screen == null || selection.isEmpty()) {
            return false;
        }
        boolean any = false;
        history.beginGesture();
        try {
            for (String name : selection.ids()) {
                ScreenElement element = screen.element(name);
                if (element != null && !styleName.equals(element.styleName())) {
                    any |= applyTracked(new ScreenOps.SetElement(screenName,
                            element.withStyleName(styleName)));
                }
            }
        } finally {
            history.endGesture();
        }
        return any;
    }

    /** Redéfinit un style nommé : tous ceux qui le suivent changent d'un coup. */
    public boolean putStyle(String styleName, fr.blueprint.core.graph.screen.ElementStyle style) {
        Screen screen = screen();
        return screen != null
                && applyTracked(new ScreenOps.SetScreen(screen.withStyle(styleName, style)));
    }

    public boolean nameAvailable(String candidate, String current) {
        Screen screen = screen();
        if (screen == null || candidate == null || candidate.isBlank()) {
            return false;
        }
        return candidate.equals(current) || screen.element(candidate) == null;
    }

    public boolean rename(String from, String to) {
        history.beginGesture();
        try {
            if (!applyTracked(new ScreenOps.RenameElement(screenName, from, to))) {
                return false;
            }
        } finally {
            history.endGesture();
        }
        if (selection.isSelected(from)) {
            selection.remove(from);
            selection.selectAll(List.of(to), true);
        }
        return true;
    }

    /** Modifie un élément (panneau de propriétés) : style, texte, texture, ancre. */
    public boolean setElement(ScreenElement element) {
        history.beginGesture();
        try {
            return applyTracked(new ScreenOps.SetElement(screenName, element));
        } finally {
            history.endGesture();
        }
    }

    // ------------------------------------------------------- précision et cadrage

    /**
     * Décale la sélection au clavier. La souris ne descend pas sous le pixel de la
     * surface ; les flèches, si — et c'est le seul moyen de poser un élément
     * <b>exactement</b> à 3 unités du bord.
     */
    public boolean nudgeSelection(double dx, double dy) {
        Screen screen = screen();
        if (screen == null || selection.isEmpty()) {
            return false;
        }
        boolean any = false;
        history.beginGesture();
        try {
            var placed = rects();
            for (String name : movable()) {
                ScreenElement element = screen.element(name);
                ScreenLayout.Rect rect = placed.get(name);
                if (element == null || rect == null) {
                    continue;
                }
                // Un enfant rangé n'a pas de position à décaler : ses frères la lui
                // donnent. Les flèches le font changer de RANG, comme le glisser.
                if (arrangedByParent(screen, name)) {
                    any |= nudgeRank(screen, element, dx, dy);
                    continue;
                }
                // Par le MÊME chemin que la souris : le raccourci direct par
                // {@code movedTo} ne confinait pas dans le parent, si bien qu'une
                // flèche maintenue faisait sortir un bouton de son cadre là où le
                // glisser l'en empêchait. Deux gestes, un seul résultat attendu.
                any |= place(screen, element, new ScreenLayout.Rect(
                        rect.x() + dx, rect.y() + dy, rect.width(), rect.height()));
            }
        } finally {
            history.endGesture();
        }
        return any;
    }

    /** Une flèche sur un enfant rangé le monte ou le descend d'un cran. */
    private boolean nudgeRank(Screen screen, ScreenElement element, double dx, double dy) {
        ScreenElement container = screen.element(element.parent());
        if (container == null) {
            return false;
        }
        double along = container.layout().vertical() ? dy : dx;
        if (along == 0) {
            return false;
        }
        return applyTracked(new ScreenOps.ReorderElement(screenName, element.name(),
                along > 0 ? 1 : -1));
    }

    /** Les six alignements, sur le rectangle englobant de la sélection. */
    public enum Align { LEFT, CENTER_X, RIGHT, TOP, CENTER_Y, BOTTOM }

    /**
     * Aligne la sélection. Sur un graphe, deux nœuds mal alignés restent lisibles ; sur
     * un écran, deux unités d'écart <b>se voient</b> — et régler ça à la main, élément
     * par élément, est exactement ce qui décourage de soigner un menu.
     *
     * <p>Le rectangle englobant sert de référence, comme dans tout éditeur : aligner à
     * gauche colle tout le monde au plus à gauche, pas à une valeur arbitraire.
     */
    public boolean alignSelection(Align align) {
        Screen screen = screen();
        if (screen == null || selection.isEmpty()) {
            return false;
        }
        List<String> targets = List.copyOf(movable());
        // Un SEUL élément s'aligne sur son PARENT — l'écran s'il n'en a pas.
        //
        // Aligner un élément sur lui-même ne fait rien, et c'est ce que le concepteur
        // faisait : les six raccourcis d'alignement étaient muets tant qu'on n'avait pas
        // sélectionné deux éléments. Or « centre ce bouton dans son cadre » est le geste
        // le plus courant d'une mise en page, et il fallait le calculer à la main.
        ScreenLayout.Rect bounds = targets.size() == 1
                ? ScreenLayout.parentRect(screen, screen.element(targets.getFirst()),
                        viewportWidth, viewportHeight)
                : boundsOf(screen, targets);
        if (bounds == null) {
            return false;
        }
        boolean any = false;
        history.beginGesture();
        try {
            var placed = rects();
            for (String name : targets) {
                ScreenElement element = screen.element(name);
                ScreenLayout.Rect rect = placed.get(name);
                if (element == null || rect == null) {
                    continue;
                }
                double x = switch (align) {
                    case LEFT -> bounds.x();
                    case CENTER_X -> bounds.x() + (bounds.width() - rect.width()) / 2;
                    case RIGHT -> bounds.right() - rect.width();
                    default -> rect.x();
                };
                double y = switch (align) {
                    case TOP -> bounds.y();
                    case CENTER_Y -> bounds.y() + (bounds.height() - rect.height()) / 2;
                    case BOTTOM -> bounds.bottom() - rect.height();
                    default -> rect.y();
                };
                if (x != rect.x() || y != rect.y()) {
                    place(screen, element,
                            new ScreenLayout.Rect(x, y, rect.width(), rect.height()));
                    any = true;
                }
            }
        } finally {
            history.endGesture();
        }
        return any;
    }

    /**
     * Répartit la sélection à intervalles égaux entre ses deux extrêmes. Trois boutons
     * empilés « à peu près » se voient ; les espacer à la main demande de calculer, ce
     * qu'un concepteur ne devrait jamais avoir à faire.
     */
    public boolean distributeSelection(boolean vertical) {
        Screen screen = screen();
        if (screen == null || selection.size() < 3) {
            return false;
        }
        List<String> targets = new ArrayList<>(movable());
        if (targets.size() < 3) {
            return false;
        }
        Map<String, ScreenLayout.Rect> rects = new HashMap<>();
        var placed = rects();
        for (String name : targets) {
            ScreenElement element = screen.element(name);
            if (element != null && placed.containsKey(name)) {
                rects.put(name, placed.get(name));
            }
        }
        targets.removeIf(name -> !rects.containsKey(name));
        if (targets.size() < 3) {
            return false;
        }
        targets.sort(java.util.Comparator.comparingDouble(
                name -> vertical ? rects.get(name).y() : rects.get(name).x()));

        ScreenLayout.Rect first = rects.get(targets.getFirst());
        ScreenLayout.Rect last = rects.get(targets.getLast());
        double start = vertical ? first.y() : first.x();
        double end = vertical ? last.y() : last.x();
        double step = (end - start) / (targets.size() - 1);

        history.beginGesture();
        try {
            for (int i = 1; i < targets.size() - 1; i++) {
                ScreenElement element = screen.element(targets.get(i));
                ScreenLayout.Rect rect = rects.get(targets.get(i));
                if (element == null) {
                    continue;
                }
                double at = start + step * i;
                place(screen, element, vertical
                        ? new ScreenLayout.Rect(rect.x(), at, rect.width(), rect.height())
                        : new ScreenLayout.Rect(at, rect.y(), rect.width(), rect.height()));
            }
        } finally {
            history.endGesture();
        }
        return true;
    }

    /** Le rectangle englobant d'un groupe, ou {@code null} si rien n'est résolvable. */
    private ScreenLayout.@Nullable Rect boundsOf(Screen screen, List<String> names) {
        double left = Double.MAX_VALUE;
        double top = Double.MAX_VALUE;
        double right = -Double.MAX_VALUE;
        double bottom = -Double.MAX_VALUE;
        boolean any = false;
        var placed = rects();
        for (String name : names) {
            ScreenLayout.Rect rect = placed.get(name);
            if (rect == null) {
                continue;
            }
            left = Math.min(left, rect.x());
            top = Math.min(top, rect.y());
            right = Math.max(right, rect.right());
            bottom = Math.max(bottom, rect.bottom());
            any = true;
        }
        return any ? new ScreenLayout.Rect(left, top, right - left, bottom - top) : null;
    }

    // --------------------------------------------------------- visibilité groupée

    /** Bascule la visibilité (ou l'activation) de toute la sélection d'un coup. */
    public boolean toggleSelection(boolean visibility) {
        Screen screen = screen();
        if (screen == null || selection.isEmpty()) {
            return false;
        }
        // La cible commune vient du PREMIER élément : sans elle, une sélection mixte
        // basculerait chacun de son côté et rien ne changerait à l'écran.
        ScreenElement first = screen.element(selection.ids().iterator().next());
        if (first == null) {
            return false;
        }
        boolean target = visibility ? !first.visible() : !first.enabled();
        boolean any = false;
        history.beginGesture();
        try {
            for (String name : selection.ids()) {
                ScreenElement element = screen.element(name);
                if (element == null) {
                    continue;
                }
                any |= applyTracked(new ScreenOps.SetElement(screenName,
                        visibility ? element.withVisible(target) : element.withEnabled(target)));
            }
        } finally {
            history.endGesture();
        }
        return any;
    }

    // ------------------------------------------------------------ presse-papiers

    /** Ce qui a été copié, et de quel écran — le collage recrée des noms libres. */
    private static List<ScreenElement> clipboard = List.of();

    public boolean copySelection() {
        Screen screen = screen();
        if (screen == null || selection.isEmpty()) {
            return false;
        }
        List<ScreenElement> copied = new ArrayList<>();
        for (String name : selection.ids()) {
            ScreenElement element = screen.element(name);
            if (element != null) {
                copied.add(element);
            }
        }
        clipboard = List.copyOf(copied);
        return !clipboard.isEmpty();
    }

    /**
     * Colle dans l'écran COURANT — y compris un autre que celui d'origine, ce qui est
     * tout l'intérêt : recomposer une page à partir d'une autre sans tout redessiner.
     *
     * <p>Un élément dont le parent n'existe pas dans l'écran d'arrivée est collé à la
     * racine plutôt que refusé : perdre le geste vaudrait moins que d'avoir à le
     * rattacher soi-même.
     */
    public boolean paste() {
        Screen screen = screen();
        if (screen == null || clipboard.isEmpty()) {
            return false;
        }
        Set<String> taken = new java.util.HashSet<>(screen.elements().keySet());
        Map<String, String> renames = new java.util.LinkedHashMap<>();
        for (ScreenElement source : clipboard) {
            String fresh = freshName(taken, source.name());
            taken.add(fresh);
            renames.put(source.name(), fresh);
        }
        List<String> created = new ArrayList<>();
        history.beginGesture();
        try {
            for (ScreenElement source : clipboard) {
                String parent = renames.containsKey(source.parent())
                        ? renames.get(source.parent())
                        : (source.parent() != null && screen().element(source.parent()) == null
                        ? null : source.parent());
                ScreenElement copy = source.renamed(renames.get(source.name()))
                        .withParent(parent)
                        .movedTo(source.x() + GRID_STEP, source.y() + GRID_STEP);
                if (applyTracked(new ScreenOps.AddElement(screenName, copy))) {
                    created.add(copy.name());
                }
            }
        } finally {
            history.endGesture();
        }
        if (created.isEmpty()) {
            return false;
        }
        selection.selectAll(created, false);
        return true;
    }

    // ------------------------------------------------------------ gestion d'écran

    /** Crée un écran et bascule dessus. Rend son nom, ou {@code null} si refusé. */
    public @Nullable String addScreen(String base) {
        Set<String> taken = new java.util.HashSet<>(blueprint.screens().keySet());
        String name = freshName(taken, base);
        history.beginGesture();
        try {
            if (!applyTracked(new ScreenOps.AddScreen(Screen.empty(name)))) {
                return null;
            }
        } finally {
            history.endGesture();
        }
        setScreenName(name);
        return name;
    }

    /** Supprime l'écran courant et bascule sur ce qu'il reste. */
    public boolean removeCurrentScreen() {
        if (screen() == null) {
            return false;
        }
        history.beginGesture();
        try {
            if (!applyTracked(new ScreenOps.RemoveScreen(screenName))) {
                return false;
            }
        } finally {
            history.endGesture();
        }
        setScreenName(blueprint.screens().keySet().stream().findFirst().orElse(""));
        return true;
    }

    /**
     * Modal ↔ HUD. Le passage en HUD peut être <b>refusé</b> : un HUD ne capte pas la
     * souris, donc un écran qui contient des boutons y serait un leurre — et c'est
     * `ScreenRules` qui le dit, pas ce contrôleur.
     */
    public boolean toggleHud() {
        Screen screen = screen();
        if (screen == null) {
            return false;
        }
        Screen flipped = screen.withHud(!screen.hud());
        for (ScreenElement element : flipped.elements().values()) {
            Diagnostic refusal = fr.blueprint.core.graph.ScreenRules.checkPlacement(
                    screenName, flipped, element,
                    fr.blueprint.core.graph.GraphLimits.DEFAULT);
            if (refusal != null) {
                lastRefusal = refusal;
                return false;
            }
        }
        history.beginGesture();
        try {
            return applyTracked(new ScreenOps.SetScreen(flipped));
        } finally {
            history.endGesture();
        }
    }

    /** Renomme l'écran courant : un écran est désigné par son nom dans `gui/open`. */
    public boolean renameCurrentScreen(String to) {
        Screen screen = screen();
        if (screen == null || to == null || to.isBlank() || blueprint.screen(to) != null) {
            return false;
        }
        history.beginGesture();
        try {
            // Ajouter le nouveau AVANT de retirer l'ancien : si l'ajout est refusé
            // (plafond atteint), l'écran d'origine est toujours là.
            if (!applyTracked(new ScreenOps.AddScreen(screen.renamed(to)))) {
                return false;
            }
            applyTracked(new ScreenOps.RemoveScreen(screenName));
        } finally {
            history.endGesture();
        }
        setScreenName(to);
        return true;
    }

    private boolean applyTracked(EditOperation op) {
        EditOperation.Result result = op.apply(blueprint, lookup);
        if (result.applied()) {
            if (result.inverse() != null) {
                history.record(result.inverse());
            }
            if (onMutation != null) {
                onMutation.run();
            }
        } else {
            lastRefusal = result.refusal();
        }
        return result.applied();
    }
}
