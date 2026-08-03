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

    public enum Gesture { NONE, MOVE, RUBBER, RESIZE }

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

    /** Demi-côté d'une poignée, en unités. Assez grand pour se saisir sans loupe. */
    public static final double HANDLE_RADIUS = 2.5;

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
        HUGE(960, 540);

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

    public ScreenLayout.@Nullable Rect rectOf(String element) {
        Screen screen = screen();
        ScreenElement target = screen == null ? null : screen.element(element);
        return target == null ? null
                : ScreenLayout.resolve(screen, target, viewportWidth, viewportHeight);
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
        List<ScreenElement> elements = List.copyOf(screen.elements().values());
        for (int i = elements.size() - 1; i >= 0; i--) {
            ScreenElement element = elements.get(i);
            if (ScreenLayout.resolve(screen, element, viewportWidth, viewportHeight)
                    .contains(x, y)) {
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
        ScreenLayout.Rect rect = rectOf(selection.ids().iterator().next());
        if (rect == null) {
            return null;
        }
        for (Handle handle : Handle.values()) {
            double hx = rect.x() + rect.width() * handle.fractionX();
            double hy = rect.y() + rect.height() * handle.fractionY();
            if (Math.abs(x - hx) <= HANDLE_RADIUS && Math.abs(y - hy) <= HANDLE_RADIUS) {
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
        gesture = Gesture.MOVE;
        primary = topmostUnselectedAncestorOr(screen, hit);
        snapshotSelection(screen);
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
                for (ScreenElement element : screen.elements().values()) {
                    ScreenLayout.Rect rect = ScreenLayout.resolve(screen, element,
                            viewportWidth, viewportHeight);
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
        for (String name : movable()) {
            ScreenElement element = screen.element(name);
            if (element != null) {
                grabbed.put(name, ScreenLayout.resolve(screen, element,
                        viewportWidth, viewportHeight));
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
        AlignmentGuides.Result snapped = AlignmentGuides.snap(wanted, neighbours(screen));
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

    private List<ScreenLayout.Rect> neighbours(Screen screen) {
        List<ScreenLayout.Rect> out = new ArrayList<>();
        for (ScreenElement element : screen.elements().values()) {
            if (!selection.isSelected(element.name())) {
                out.add(ScreenLayout.resolve(screen, element,
                        viewportWidth, viewportHeight));
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
        place(screen, element, new ScreenLayout.Rect(left, top, right - left, bottom - top));
    }

    /**
     * Écrit un rectangle sur un élément, <b>confiné dans son parent</b> (AC4). Un
     * enfant qui déborde de son cadre est invisible en jeu — le parent découpe — et
     * l'auteur ne comprendrait pas pourquoi son bouton a disparu.
     */
    private void place(Screen screen, ScreenElement element, ScreenLayout.Rect target) {
        ScreenLayout.Rect parent = ScreenLayout.parentRect(screen, element,
                viewportWidth, viewportHeight);
        ScreenLayout.Rect bounded = element.parent() == null ? target : confine(parent, target);
        ScreenElement placed = ScreenLayout.placedIn(parent, element, bounded);
        applyTracked(new ScreenOps.SetElement(screenName, placed));
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
        double width = kind.container() ? 100 : 60;
        double height = kind == ElementKind.LABEL ? 10 : 20;
        ScreenElement element = ScreenElement.of(name, kind, snap(x), snap(y), width, height)
                .withParent(hitContainer(screen, x, y));

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

    /** Le conteneur le plus haut sous le point : poser dans un panneau y rattache. */
    private @Nullable String hitContainer(Screen screen, double x, double y) {
        List<ScreenElement> elements = List.copyOf(screen.elements().values());
        for (int i = elements.size() - 1; i >= 0; i--) {
            ScreenElement element = elements.get(i);
            if (element.kind().container()
                    && ScreenLayout.resolve(screen, element, viewportWidth, viewportHeight)
                    .contains(x, y)) {
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
            for (String name : movable()) {
                ScreenElement element = screen.element(name);
                if (element != null) {
                    any |= applyTracked(new ScreenOps.SetElement(screenName,
                            element.movedTo(element.x() + dx, element.y() + dy)));
                }
            }
        } finally {
            history.endGesture();
        }
        return any;
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
        if (screen == null || selection.size() < 2) {
            return false;
        }
        List<String> targets = List.copyOf(movable());
        ScreenLayout.Rect bounds = boundsOf(screen, targets);
        if (bounds == null) {
            return false;
        }
        boolean any = false;
        history.beginGesture();
        try {
            for (String name : targets) {
                ScreenElement element = screen.element(name);
                if (element == null) {
                    continue;
                }
                ScreenLayout.Rect rect = ScreenLayout.resolve(screen, element,
                        viewportWidth, viewportHeight);
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
        for (String name : targets) {
            ScreenElement element = screen.element(name);
            if (element != null) {
                rects.put(name, ScreenLayout.resolve(screen, element,
                        viewportWidth, viewportHeight));
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
        for (String name : names) {
            ScreenElement element = screen.element(name);
            if (element == null) {
                continue;
            }
            ScreenLayout.Rect rect = ScreenLayout.resolve(screen, element,
                    viewportWidth, viewportHeight);
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
