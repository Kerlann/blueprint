package fr.blueprint.client.editor.screen;

import fr.blueprint.client.editor.PanelScroll;
import fr.blueprint.core.graph.screen.ElementKind;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * La colonne de gauche du concepteur, <b>décidée</b> : quelles rangées, à quelle hauteur,
 * et ce que demande un clic. {@code ScreenDesignerWidget} ne fait plus que peindre.
 *
 * <p><b>Pourquoi cette classe existe.</b> L'ordonnée de chaque rangée était calculée
 * <i>deux fois</i> — une fois au dessin, une fois au clic — et les deux calculs avaient
 * divergé : un filet se traçait au milieu du texte « supprimer l'écran » et les deux
 * premiers pixels du titre suivant tombaient dans sa zone cliquable. Le pire est qu'ils
 * tombaient d'accord sur le reste, ce qui rendait le défaut invisible. {@code DesignerPanels}
 * porte déjà le même avertissement pour les largeurs, en citant deux précédents ; celle-ci
 * fait pour la hauteur ce qu'il fait pour la largeur.
 *
 * <p><b>Une seule liste pour trois sections.</b> Écrans, éléments et calques défilent
 * ensemble. Empilées sans défilement, elles se poussaient l'une l'autre hors de la fenêtre :
 * à huit écrans, les calques passaient sous le bord et devenaient <b>inatteignables</b> —
 * alors qu'ils sont le seul moyen de sélectionner un conteneur recouvert par ses enfants.
 *
 * <p><b>Les commandes ne sont plus des données.</b> « + nouvel écran », « renommer » et
 * « supprimer » étaient des lignes de texte au milieu de la liste des écrans, de la même
 * couleur et de la même taille que les noms. Le « + » monte dans l'en-tête et les actions
 * se posent sur la ligne sélectionnée, comme le panneau des variables et celui des fonctions
 * le font depuis toujours.
 */
public final class DesignerPalette {

    public static final int WIDTH = DesignerPanels.PALETTE_WIDTH;
    public static final int ROW = 12;
    /** Hauteur d'un en-tête de section, filet compris. */
    public static final int HEADER = 14;

    private DesignerPalette() {
    }

    /** Ce qu'une rangée est. Le dessin en dépend, le clic aussi. */
    public enum Kind {
        /** Un en-tête de section — « Écrans », « Éléments », « Calques ». */
        SECTION,
        /** Le titre d'un groupe de types, à l'intérieur de la section des éléments. */
        GROUP,
        SCREEN, ELEMENT, LAYER,
        /** Une phrase à la place d'une liste vide : dire quoi faire plutôt que rien. */
        EMPTY
    }

    /**
     * Une rangée <b>placée</b>.
     *
     * @param depth      indentation d'un calque, 0 ailleurs.
     * @param expandable vrai si le calque a des enfants — il porte alors un chevron.
     */
    public record Row(Kind kind, int y, String label, @Nullable String name,
                      @Nullable ElementKind element, int depth,
                      boolean selected, boolean visible, boolean expanded,
                      boolean expandable) {

        static Row of(Kind kind, int y, String label) {
            return new Row(kind, y, label, null, null, 0, false, true, false, false);
        }
    }

    /** Un calque, tel que la vue le connaît : son nom, son parent, sa visibilité. */
    public record Layer(String name, @Nullable String parent, boolean visible) {
    }

    /**
     * Ce que la colonne a besoin de savoir. Des données nues, pas le contrôleur : c'est ce
     * qui permet de vérifier la disposition sans jeu lancé.
     *
     * @param renaming l'écran dont on tape le nouveau nom, ou {@code null}.
     */
    public record Model(List<String> screens, @Nullable String activeScreen,
                        @Nullable String renaming, String renameBuffer, boolean hud,
                        List<Layer> layers, List<String> selectedLayers,
                        List<String> collapsed) {

        public static final Model EMPTY =
                new Model(List.of(), null, null, "", false, List.of(), List.of(), List.of());
    }

    // ------------------------------------------------------------------- disposition

    /**
     * Les groupes de la section « Éléments ».
     *
     * <p>Douze types en une colonne de mots ne se parcourent pas : on les lit tous pour en
     * trouver un. Groupés, on saute au bon tiers. Le partage suit ce que le modèle sait
     * déjà dire d'eux — {@code container()} et {@code interactive()} — plutôt qu'un
     * classement inventé qui se démentirait au premier type ajouté.
     */
    public enum Group {
        CONTAINER, DISPLAY, INTERACTIVE;

        /**
         * La clé de traduction, écrite <b>en toutes lettres</b>.
         *
         * <p>La composer depuis le nom de l'énumération l'aurait rendue invisible au
         * contrôle des clés mortes, qui lit les sources. Une clé qu'aucun test ne relie à
         * son usage est une clé qu'on oublie de traduire, puis qu'on oublie de supprimer.
         */
        public String key() {
            return switch (this) {
                case CONTAINER -> "blueprint.designer.group.container";
                case DISPLAY -> "blueprint.designer.group.display";
                case INTERACTIVE -> "blueprint.designer.group.interactive";
            };
        }
    }

    public static Group groupOf(ElementKind kind) {
        if (kind.container()) {
            return Group.CONTAINER;
        }
        return kind.interactive() ? Group.INTERACTIVE : Group.DISPLAY;
    }

    /**
     * Toutes les rangées, dans l'ordre, avant défilement — l'ordonnée de la première est 0.
     *
     * <p>Le décalage du défilement est appliqué par {@link #rows}, pas ici : la hauteur
     * totale doit se connaître avant de savoir ce qui tient à l'écran.
     */
    public static List<Row> content(Model model) {
        List<Row> out = new ArrayList<>();
        int y = 0;

        out.add(Row.of(Kind.SECTION, y, "blueprint.designer.screens"));
        y += HEADER;
        if (model.screens().isEmpty()) {
            out.add(Row.of(Kind.EMPTY, y, "blueprint.designer.screens.empty"));
            y += ROW;
        }
        for (String name : model.screens()) {
            boolean active = name.equals(model.activeScreen());
            String label = name.equals(model.renaming())
                    ? model.renameBuffer() + "_" : name;
            out.add(new Row(Kind.SCREEN, y, label, name, null, 0, active, true, false, false));
            y += ROW;
        }

        out.add(Row.of(Kind.SECTION, y, "blueprint.designer.elements"));
        y += HEADER;
        for (Group group : Group.values()) {
            List<ElementKind> kinds = kindsOf(group);
            if (kinds.isEmpty()) {
                continue;
            }
            out.add(Row.of(Kind.GROUP, y, group.key()));
            y += ROW;
            for (ElementKind kind : kinds) {
                out.add(new Row(Kind.ELEMENT, y, "", null, kind, 0, false, true, false, false));
                y += ROW;
            }
        }

        out.add(Row.of(Kind.SECTION, y, "blueprint.designer.layers"));
        y += HEADER;
        List<Row> layers = layerRows(model, y);
        if (layers.isEmpty()) {
            out.add(Row.of(Kind.EMPTY, y, "blueprint.designer.layers.empty"));
        } else {
            out.addAll(layers);
        }
        return List.copyOf(out);
    }

    private static List<ElementKind> kindsOf(Group group) {
        List<ElementKind> out = new ArrayList<>();
        for (ElementKind kind : ElementKind.values()) {
            if (groupOf(kind) == group) {
                out.add(kind);
            }
        }
        return out;
    }

    /**
     * Les calques, <b>en arbre</b> : chaque enfant sous son parent, du dessus vers le
     * dessous.
     *
     * <p>La liste précédente se contentait d'inverser l'ordre d'insertion en indentant selon
     * la profondeur. Le commentaire promettait « chaque enfant sous son parent » et ce
     * n'était vrai que si l'ordre d'insertion s'y prêtait : poser un enfant après avoir posé
     * autre chose suffisait à le séparer de son parent, avec une indentation qui ne
     * désignait plus rien.
     *
     * <p>Un parent replié cache sa descendance entière, pas seulement ses enfants directs.
     */
    private static List<Row> layerRows(Model model, int startY) {
        List<Row> out = new ArrayList<>();
        appendChildren(model, null, 0, startY, out);
        return out;
    }

    private static int appendChildren(Model model, @Nullable String parent, int depth,
                                      int y, List<Row> out) {
        // Du dessus vers le dessous : l'ordre de dessin lu à l'envers, comme dans tout
        // éditeur graphique — ce qu'on voit en premier est en haut de la liste.
        List<Layer> children = new ArrayList<>();
        for (Layer layer : model.layers()) {
            if (java.util.Objects.equals(layer.parent(), parent)) {
                children.add(layer);
            }
        }
        java.util.Collections.reverse(children);
        for (Layer layer : children) {
            boolean hasChildren = model.layers().stream()
                    .anyMatch(l -> layer.name().equals(l.parent()));
            boolean expanded = !model.collapsed().contains(layer.name());
            out.add(new Row(Kind.LAYER, y, layer.name(), layer.name(), null, depth,
                    model.selectedLayers().contains(layer.name()), layer.visible(),
                    expanded, hasChildren));
            y += ROW;
            if (hasChildren && expanded) {
                y = appendChildren(model, layer.name(), depth + 1, y, out);
            }
        }
        return y;
    }

    // ---------------------------------------------------------------------- affichage

    /** Nombre de rangées de 12 px qui tiennent sous la barre d'outils. */
    public static int visibleRows(int top, int height) {
        return Math.max(1, (height - top - 4) / ROW);
    }

    /** La hauteur totale du contenu, en rangées — l'unité du défilement. */
    public static int contentRows(Model model) {
        List<Row> rows = content(model);
        return rows.isEmpty() ? 0 : (rows.get(rows.size() - 1).y() + ROW) / ROW + 1;
    }

    /**
     * Les rangées visibles, ordonnées à l'écran.
     *
     * <p>{@code scroll} compte en rangées, comme {@link PanelScroll} le veut ; il se
     * traduit en pixels par la hauteur de rangée. Une rangée partiellement visible est
     * gardée : la couper la rendrait invisible et pourtant cliquable.
     */
    public static List<Row> rows(Model model, int top, int height, int scroll) {
        int offset = PanelScroll.clamp(scroll, contentRows(model), visibleRows(top, height))
                * ROW;
        List<Row> out = new ArrayList<>();
        for (Row row : content(model)) {
            int y = row.y() - offset + top;
            if (y + ROW <= top || y >= height) {
                continue;
            }
            out.add(new Row(row.kind(), y, row.label(), row.name(), row.element(),
                    row.depth(), row.selected(), row.visible(), row.expanded(),
                    row.expandable()));
        }
        return List.copyOf(out);
    }

    // --------------------------------------------------------------------------- clic

    /** Ce qu'un clic dans la colonne demande. */
    public enum Hit {
        NONE, SCREEN_ADD, SCREEN_SELECT, SCREEN_MODE, SCREEN_RENAME, SCREEN_DELETE,
        ELEMENT_ADD, LAYER_SELECT, LAYER_VISIBILITY, LAYER_EXPAND
    }

    /** Un clic <b>décidé</b> : ce qu'il demande, et sur quoi. */
    public record Click(Hit hit, @Nullable String name, @Nullable ElementKind element) {

        static final Click NONE = new Click(Hit.NONE, null, null);
    }

    /** Le « + » de l'en-tête « Écrans ». */
    public static boolean plusAt(Row header, double mx, double my) {
        return header.kind() == Kind.SECTION && "blueprint.designer.screens".equals(header.label())
                && mx >= WIDTH - 14 && my >= header.y() && my < header.y() + HEADER;
    }

    /**
     * Ce que ce clic demande, sans rien appliquer.
     *
     * <p>Décider et agir sont séparés parce que la décision porte les pièges — l'œil qui
     * bascule au lieu de sélectionner, les trois actions qui n'existent que sur la ligne
     * active, le chevron qui replie sans changer la sélection — et qu'aucun d'eux ne se
     * vérifie derrière une fenêtre.
     */
    public static Click clickAt(Model model, int top, int height, int scroll,
                                double mx, double my) {
        for (Row row : rows(model, top, height, scroll)) {
            if (my < row.y() || my >= row.y() + rowHeight(row)) {
                continue;
            }
            return decide(row, mx);
        }
        return Click.NONE;
    }

    private static int rowHeight(Row row) {
        return row.kind() == Kind.SECTION ? HEADER : ROW;
    }

    private static Click decide(Row row, double mx) {
        switch (row.kind()) {
            case SECTION -> {
                if ("blueprint.designer.screens".equals(row.label()) && mx >= WIDTH - 14) {
                    return new Click(Hit.SCREEN_ADD, null, null);
                }
                return Click.NONE;
            }
            case SCREEN -> {
                // Les trois actions n'apparaissent que sur la ligne active : cliquer leur
                // emplacement sur une autre ligne la sélectionne, il ne la supprime pas.
                if (row.selected()) {
                    if (mx >= WIDTH - 34 && mx < WIDTH - 24) {
                        return new Click(Hit.SCREEN_MODE, row.name(), null);
                    }
                    if (mx >= WIDTH - 24 && mx < WIDTH - 14) {
                        return new Click(Hit.SCREEN_RENAME, row.name(), null);
                    }
                    if (mx >= WIDTH - 14 && mx < WIDTH - 4) {
                        return new Click(Hit.SCREEN_DELETE, row.name(), null);
                    }
                }
                return new Click(Hit.SCREEN_SELECT, row.name(), null);
            }
            case ELEMENT -> {
                return new Click(Hit.ELEMENT_ADD, null, row.element());
            }
            case LAYER -> {
                // Le chevron d'abord, puis l'œil, puis le nom. L'œil était dessiné et
                // inerte : le clic sélectionnait, faute de recevoir l'abscisse.
                double chevron = 2 + row.depth() * 4.0;
                if (row.expandable() && mx >= chevron && mx < chevron + 6) {
                    return new Click(Hit.LAYER_EXPAND, row.name(), null);
                }
                if (mx >= eyeX(row) && mx < eyeX(row) + 8) {
                    return new Click(Hit.LAYER_VISIBILITY, row.name(), null);
                }
                return new Click(Hit.LAYER_SELECT, row.name(), null);
            }
            default -> {
                return Click.NONE;
            }
        }
    }

    // -------------------------------------------------------------------- reparentage

    /**
     * Le <b>parent</b> que ce point désigne pour un calque qu'on traîne, ou {@code null} si
     * rien de valable ne s'y trouve. La chaîne vide veut dire « à la racine de l'écran ».
     *
     * <p>Reparenter était impossible : le glisser sur le canevas confine dans le parent
     * existant, le panneau n'a pas de champ, et le seul contournement était de supprimer
     * l'élément puis de le recréer — au centre de la vue, pas sous le curseur. Le modèle,
     * lui, savait faire depuis toujours : {@code SetElement} porte l'élément entier et
     * {@code ScreenRules.checkPlacement} refuse déjà les cycles, les parents absents et les
     * parents qui ne sont pas des conteneurs. Il manquait le geste.
     *
     * <p>On ne propose que ce que le modèle accepterait : ni soi-même, ni sa propre
     * descendance. Montrer une cible que l'opération refusera ensuite est le genre de
     * promesse qui fait douter d'un outil.
     */
    public static @Nullable String dropTarget(Model model, int top, int height, int scroll,
                                              String dragged, double my) {
        Row row = rowAt(model, top, height, scroll, my);
        if (row == null) {
            return null;
        }
        // L'en-tête « Calques » est la cible « racine » : c'est là qu'on lâche pour sortir
        // un élément de son conteneur, et il est juste au-dessus de la liste.
        if (row.kind() == Kind.SECTION && "blueprint.designer.layers".equals(row.label())) {
            return "";
        }
        if (row.kind() != Kind.LAYER || row.name() == null || row.name().equals(dragged)) {
            return null;
        }
        return descendsFrom(model, row.name(), dragged) ? null : row.name();
    }

    /** {@code name} est-il {@code ancestor} ou l'un de ses descendants ? */
    private static boolean descendsFrom(Model model, String name, String ancestor) {
        String cursor = name;
        java.util.Set<String> seen = new java.util.HashSet<>();
        while (cursor != null && seen.add(cursor)) {
            if (cursor.equals(ancestor)) {
                return true;
            }
            cursor = parentOf(model, cursor);
        }
        return false;
    }

    private static @Nullable String parentOf(Model model, String name) {
        for (Layer layer : model.layers()) {
            if (layer.name().equals(name)) {
                return layer.parent();
            }
        }
        return null;
    }

    /** La rangée sous l'ordonnée donnée, ou {@code null}. */
    public static @Nullable Row rowAt(Model model, int top, int height, int scroll, double my) {
        for (Row row : rows(model, top, height, scroll)) {
            if (my >= row.y() && my < row.y() + rowHeight(row)) {
                return row;
            }
        }
        return null;
    }

    /** L'abscisse de l'œil d'un calque — le dessin et le clic la lisent ici. */
    public static int eyeX(Row row) {
        return 8 + row.depth() * 4;
    }

    /** L'abscisse du nom d'un calque, après son chevron et son œil. */
    public static int nameX(Row row) {
        return eyeX(row) + 10;
    }
}
