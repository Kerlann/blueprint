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

    public boolean snapEnabled() {
        return snapEnabled;
    }

    public void toggleSnap() {
        snapEnabled = !snapEnabled;
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
                : ScreenLayout.resolve(screen, target, Screen.SAFE_WIDTH, Screen.SAFE_HEIGHT);
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
            if (ScreenLayout.resolve(screen, element, Screen.SAFE_WIDTH, Screen.SAFE_HEIGHT)
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
                            Screen.SAFE_WIDTH, Screen.SAFE_HEIGHT);
                    if (rect.x() < band.right() && rect.right() > band.x()
                            && rect.y() < band.bottom() && rect.bottom() > band.y()) {
                        caught.add(element.name());
                    }
                }
                selection.selectAll(caught, false);
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
                        Screen.SAFE_WIDTH, Screen.SAFE_HEIGHT));
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
                        Screen.SAFE_WIDTH, Screen.SAFE_HEIGHT));
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
                Screen.SAFE_WIDTH, Screen.SAFE_HEIGHT);
        double width = Math.min(target.width(), parent.width());
        double height = Math.min(target.height(), parent.height());
        double x = Math.clamp(target.x(), parent.x(), parent.right() - width);
        double y = Math.clamp(target.y(), parent.y(), parent.bottom() - height);
        ScreenElement placed = ScreenLayout.placedIn(parent, element,
                new ScreenLayout.Rect(x, y, width, height));
        applyTracked(new ScreenOps.SetElement(screenName, placed));
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
                    && ScreenLayout.resolve(screen, element, Screen.SAFE_WIDTH, Screen.SAFE_HEIGHT)
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
