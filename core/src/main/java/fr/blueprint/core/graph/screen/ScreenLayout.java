package fr.blueprint.core.graph.screen;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * La géométrie d'un élément, une fois la fenêtre connue (story 10.1).
 *
 * <p><b>La seule résolution du produit.</b> {@link Extent#resolve} donne une longueur ;
 * il manquait ce qui la rend utilisable : la place du parent et l'ancre. Sans elle,
 * chaque appelant refaisait le calcul en supposant l'élément à la racine et ancré en
 * haut-gauche — vrai pour un élément sur deux, faux pour tous les autres. Le concepteur
 * (10.2), le rendu (10.3) et le contrôle de placement partagent donc cette classe, comme
 * la géométrie du clic sur un fil est partagée avec son tracé depuis la story 5.12.
 *
 * <p>Résistante aux cycles de parenté : le validateur les refuse, mais une sauvegarde
 * réparée peut en contenir, et un rendu qui boucle vaut pire qu'un rendu approximatif.
 */
public final class ScreenLayout {

    /** Profondeur maximale d'imbrication de conteneurs qui s'ajustent. */
    private static final int MAX_HUG_DEPTH = 16;

    private ScreenLayout() {
    }

    /** Le rectangle d'un élément dans la fenêtre, en unités d'interface. */
    public record Rect(double x, double y, double width, double height) {

        public double right() {
            return x + width;
        }

        public double bottom() {
            return y + height;
        }

        public boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
    }

    /**
     * Résout le rectangle de {@code element} dans {@code screen}, pour une fenêtre de
     * {@code viewportWidth} × {@code viewportHeight} unités.
     *
     * <p>L'élément n'a pas besoin d'appartenir déjà à l'écran : c'est ce qui permet de
     * contrôler le placement d'un élément <i>avant</i> de le poser. Seuls ses ancêtres
     * sont cherchés dans l'écran.
     */
    public static Rect resolve(Screen screen, ScreenElement element,
                               double viewportWidth, double viewportHeight) {
        // Remonter d'abord, redescendre ensuite : le rectangle d'un enfant se calcule
        // dans celui de son parent, qui n'est connu qu'une fois la racine atteinte.
        Deque<ScreenElement> chain = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        ScreenElement cursor = element;
        while (cursor != null && seen.add(cursor.name())) {
            chain.push(cursor);
            cursor = cursor.parent() == null ? null : screen.element(cursor.parent());
        }

        Rect rect = new Rect(0, 0, viewportWidth, viewportHeight);
        for (ScreenElement step : chain) {
            rect = within(rect, step);
        }
        return rect;
    }

    // --------------------------------------------------- la passe de disposition

    /**
     * Résout <b>tout l'écran</b> d'un coup, et rend le rectangle de chaque élément.
     *
     * <p>C'est le changement de fond apporté par les conteneurs. {@link #resolve} remonte
     * la chaîne des parents : cela suffisait tant que chaque élément portait sa position.
     * Dès qu'un conteneur <b>range</b> ses enfants, la place d'un enfant dépend de ses
     * <b>frères</b> — de leur nombre, de leur taille, de leur poids. Il faut donc une
     * passe descendante sur l'arbre entier.
     *
     * <p>Effet de bord favorable : une passe par image au lieu d'une remontée de chaîne
     * par élément. C'est <i>moins</i> cher qu'avant, pas plus.
     *
     * <p>Un cycle de parenté — refusé à l'édition, mais possible dans une sauvegarde
     * réparée — laisse simplement ses éléments hors de la table plutôt que de faire
     * boucler le rendu.
     */
    public static Map<String, Rect> solve(Screen screen,
                                          double viewportWidth, double viewportHeight) {
        return solve(screen, viewportWidth, viewportHeight, container -> 0);
    }

    /**
     * De combien un conteneur défilant est remonté, <b>en unités</b>.
     *
     * <p>C'est une donnée d'EXÉCUTION : deux joueurs devant le même menu n'ont pas la même
     * position de lecture. Elle entre donc dans la passe par ce paramètre plutôt que de
     * vivre dans le modèle, où elle voyagerait dans la sauvegarde et dans l'export texte.
     */
    @FunctionalInterface
    public interface Scrolls {
        double of(String container);
    }

    /**
     * La même passe, avec le défilement des conteneurs.
     *
     * <p><b>Le décalage est appliqué ICI</b>, dans la passe unique, et non par le rendu
     * après coup. C'est la seule façon d'être sûr que le clic et le dessin s'accordent :
     * un décalage appliqué de deux côtés finit toujours par diverger, et le symptôme —
     * cliquer sur une ligne et en activer une autre — est le plus déroutant qui soit,
     * puisque tout a l'air juste. C'est la leçon que ce projet a apprise deux fois : au
     * tracé des fils (5.12), et au concepteur qui peignait à 320×180 pendant que le clic
     * résolvait ailleurs (10.11).
     */
    public static Map<String, Rect> solve(Screen screen, double viewportWidth,
                                          double viewportHeight, Scrolls scrolls) {
        Map<String, Rect> out = new HashMap<>();
        arrange(screen, null, new Rect(0, 0, viewportWidth, viewportHeight),
                LayoutSpec.ABSOLUTE, out, new HashSet<>(), scrolls);
        return out;
    }

    /**
     * Place les enfants de {@code parent} dans {@code area}, puis descend dans chacun.
     * {@code guard} coupe les cycles : un élément déjà traité ne l'est pas deux fois.
     */
    private static void arrange(Screen screen, @Nullable String parent, Rect area,
                                LayoutSpec layout, Map<String, Rect> out, Set<String> guard,
                                Scrolls scrolls) {
        List<ScreenElement> children = childrenIn(screen, parent);
        if (children.isEmpty()) {
            return;
        }
        List<Rect> rects = layout.arranges()
                ? arrangeChildren(screen, area, layout, children)
                : absoluteChildren(screen, area, children);

        // Les enfants sont d'abord posés comme si rien ne défilait, PUIS remontés d'un
        // bloc. Les poser directement décalés obligerait chaque mode de disposition à
        // connaître le défilement — quatre endroits au lieu d'un.
        double shift = parent != null && layout.scroll()
                ? Math.max(0, finite(scrolls.of(parent))) : 0;

        for (int i = 0; i < children.size(); i++) {
            ScreenElement child = children.get(i);
            if (!guard.add(child.name())) {
                continue;
            }
            Rect rect = rects.get(i);
            if (shift != 0) {
                rect = new Rect(rect.x(), rect.y() - shift, rect.width(), rect.height());
            }
            out.put(child.name(), rect);
            arrange(screen, child.name(), rect,
                    child.kind().container() ? child.layout() : LayoutSpec.ABSOLUTE,
                    out, guard, scrolls);
        }
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0;
    }

    /**
     * De combien ce conteneur peut encore défiler, au-delà de ce qu'il montre.
     *
     * <p>Se déduit des rectangles <b>déjà résolus</b> : le décalage courant y est déjà
     * appliqué, on le rend donc au contenu pour retrouver sa hauteur réelle. Une seconde
     * passe à décalage nul donnerait le même nombre pour le double du coût, à chaque
     * image et pour chaque panneau.
     *
     * <p>Zéro quand tout tient : il n'y a alors rien à faire défiler, et un curseur de
     * défilement qui apparaît sur un contenu complet ment sur ce qui reste à lire.
     */
    public static double scrollRange(Screen screen, ScreenElement container,
                                     Map<String, Rect> placed, double currentScroll) {
        Rect frame = placed.get(container.name());
        if (frame == null || !container.scrolls()) {
            return 0;
        }
        double bottom = Double.NEGATIVE_INFINITY;
        for (ScreenElement child : screen.childrenOf(container.name())) {
            Rect rect = placed.get(child.name());
            if (rect != null) {
                bottom = Math.max(bottom, rect.bottom());
            }
        }
        if (bottom == Double.NEGATIVE_INFINITY) {
            return 0;
        }
        return Math.max(0, bottom + Math.max(0, finite(currentScroll)) - frame.bottom());
    }

    /**
     * Le rectangle auquel cet élément est <b>découpé</b>, ou {@code null} s'il ne l'est
     * pas.
     *
     * <p>C'est l'intersection des cadres de tous ses ancêtres défilants — plusieurs, car
     * un panneau défilant peut en contenir un autre. Le dessin y découpe, et le hit-test
     * y refuse le clic : sans ce second usage, on cliquerait une ligne <b>sortie du
     * cadre</b>, invisible, et le graphe recevrait une action que rien à l'écran
     * n'expliquait.
     */
    public static @Nullable Rect clipOf(Screen screen, ScreenElement element,
                                        Map<String, Rect> placed) {
        Rect clip = null;
        Set<String> seen = new HashSet<>();
        seen.add(element.name());
        String cursor = element.parent();
        while (cursor != null && seen.add(cursor)) {
            ScreenElement ancestor = screen.element(cursor);
            if (ancestor == null) {
                break;
            }
            if (ancestor.scrolls()) {
                Rect frame = placed.get(ancestor.name());
                clip = frame == null ? clip : intersect(clip, frame);
            }
            cursor = ancestor.parent();
        }
        return clip;
    }

    private static Rect intersect(@Nullable Rect a, Rect b) {
        if (a == null) {
            return b;
        }
        double x = Math.max(a.x(), b.x());
        double y = Math.max(a.y(), b.y());
        return new Rect(x, y, Math.max(0, Math.min(a.right(), b.right()) - x),
                Math.max(0, Math.min(a.bottom(), b.bottom()) - y));
    }

    /**
     * Les enfants d'un conteneur — et, <b>à la racine</b>, les orphelins avec eux.
     *
     * <p>Un élément dont le parent a disparu (supprimé, reçu du réseau, relu d'une
     * sauvegarde antérieure) n'est l'enfant de personne. La passe descendante ne l'aurait
     * donc jamais placé : il devenait <b>invisible</b>, là où la remontée par élément le
     * faisait retomber sur l'écran. Le validateur signale bien la référence morte, mais
     * l'auteur l'aurait d'abord constatée par un menu amputé — et aurait cherché la cause
     * dans le dessin plutôt que dans la parenté.
     */
    private static List<ScreenElement> childrenIn(Screen screen, @Nullable String parent) {
        List<ScreenElement> children = screen.childrenOf(parent);
        if (parent != null) {
            return children;
        }
        List<ScreenElement> out = null;
        for (ScreenElement element : screen.elements().values()) {
            if (element.parent() != null && screen.element(element.parent()) == null) {
                if (out == null) {
                    out = new ArrayList<>(children);
                }
                out.add(element);
            }
        }
        return out == null ? children : List.copyOf(out);
    }

    /** Chaque enfant se place lui-même, par son ancre — le comportement historique. */
    private static List<Rect> absoluteChildren(Screen screen, Rect area,
                                               List<ScreenElement> children) {
        List<Rect> out = new ArrayList<>(children.size());
        for (ScreenElement child : children) {
            out.add(within(area, child, measured(screen, child, area)));
        }
        return out;
    }

    /**
     * La taille d'un élément, {@code null} composante par composante quand sa taille
     * ordinaire suffit. Seul {@code HUG} en produit une : le conteneur épouse alors ce
     * que ses enfants occupent réellement.
     *
     * <p>Cette mesure est <b>ascendante</b>, à rebours du reste de la passe : il faut
     * connaître le contenu avant de connaître le cadre. C'est pourquoi elle est refusée
     * hors d'un conteneur — mesurer un texte demanderait la police, qui n'existe que côté
     * client, alors que la même passe tourne côté serveur pour la validation.
     */
    private static double[] measured(Screen screen, ScreenElement element, Rect available) {
        return measured(screen, element, available, 0);
    }

    /**
     * {@code depth} coupe les cycles. Le validateur les refuse, mais une sauvegarde
     * réparée peut en contenir : deux conteneurs qui s'ajustent l'un à l'autre se
     * mesureraient sans fin, et un rendu qui boucle fige le client là où un rectangle
     * approximatif ne coûte rien.
     */
    private static double[] measured(Screen screen, ScreenElement element, Rect available,
                                     int depth) {
        if (depth > MAX_HUG_DEPTH) {
            return null;
        }
        boolean hugWidth = element.width().mode() == Extent.Mode.HUG;
        boolean hugHeight = element.height().mode() == Extent.Mode.HUG;
        if (!hugWidth && !hugHeight || !element.kind().container()) {
            return null;
        }
        // On mesure le contenu dans la place DISPONIBLE : un pourcentage d'enfant a
        // besoin d'une base, et celle du parent n'existe pas encore.
        List<ScreenElement> children = screen.childrenOf(element.name());
        double contentWidth = 0;
        double contentHeight = 0;
        if (!children.isEmpty()) {
            LayoutSpec layout = element.layout();
            if (layout.arranges() && layout.mode() != LayoutSpec.Mode.GRID) {
                boolean vertical = layout.vertical();
                double gaps = layout.gap() * (children.size() - 1);
                double along = gaps;
                double across = 0;
                for (ScreenElement child : children) {
                    double[] size = contentSize(screen, child, available, depth + 1);
                    along += vertical ? size[1] : size[0];
                    across = Math.max(across, vertical ? size[0] : size[1]);
                }
                contentWidth = vertical ? across : along;
                contentHeight = vertical ? along : across;
            } else {
                // Absolu ou grille : le contenu est ce que le plus lointain enfant atteint.
                for (ScreenElement child : children) {
                    double[] size = contentSize(screen, child, available, depth + 1);
                    contentWidth = Math.max(contentWidth, Math.abs(child.x()) + size[0]);
                    contentHeight = Math.max(contentHeight, Math.abs(child.y()) + size[1]);
                }
            }
        }
        return new double[]{
                hugWidth ? element.width().clamp(contentWidth) : -1,
                hugHeight ? element.height().clamp(contentHeight) : -1};
    }

    /**
     * La place qu'un enfant occupe, pour mesurer son parent. Un {@code FILL} n'en réclame
     * aucune : dans un conteneur qui s'ajuste, il n'y a rien à se partager — la place est
     * précisément ce qu'on est en train de calculer.
     */
    private static double[] contentSize(Screen screen, ScreenElement child, Rect available,
                                        int depth) {
        double[] hug = measured(screen, child, available, depth);
        double width = child.width().mode() == Extent.Mode.FILL ? 0
                : hug != null && hug[0] >= 0 ? hug[0] : child.width().resolve(available.width());
        double height = child.height().mode() == Extent.Mode.FILL ? 0
                : hug != null && hug[1] >= 0 ? hug[1] : child.height().resolve(available.height());
        return new double[]{width, height};
    }

    /**
     * Le conteneur range : mesurer, répartir ce qui reste, poser en séquence.
     *
     * <p>Trois temps, et l'ordre compte. On mesure d'abord ce qui ne dépend de rien
     * (fixe, pourcentage) ; on partage ensuite ce qui <b>reste</b> entre les
     * {@code FILL}, au prorata de leur poids ; on pose enfin, espacement et répartition
     * compris. Mesurer après avoir posé donnerait une place négative dès que les enfants
     * débordent.
     */
    private static List<Rect> arrangeChildren(Screen screen, Rect area, LayoutSpec layout,
                                              List<ScreenElement> children) {
        if (layout.mode() == LayoutSpec.Mode.GRID) {
            return grid(area, layout, children);
        }
        boolean vertical = layout.vertical();
        double mainSpace = vertical ? area.height() : area.width();
        double crossSpace = vertical ? area.width() : area.height();
        double gaps = layout.gap() * Math.max(0, children.size() - 1);

        // Un enfant qui s'ajuste à SON contenu doit être mesuré avant d'être placé :
        // sa taille ne vient ni du parent ni de ses frères.
        double[][] hugs = new double[children.size()][];
        for (int i = 0; i < children.size(); i++) {
            hugs[i] = measured(screen, children.get(i), area);
        }

        double fixedTotal = 0;
        double weightTotal = 0;
        double[] mainSizes = new double[children.size()];
        for (int i = 0; i < children.size(); i++) {
            Extent main = vertical ? children.get(i).height() : children.get(i).width();
            double hug = hugs[i] == null ? -1 : hugs[i][vertical ? 1 : 0];
            if (main.mode() == Extent.Mode.FILL) {
                weightTotal += main.value();
                mainSizes[i] = -1;   // décidé au second temps
            } else {
                mainSizes[i] = hug >= 0 ? hug : main.resolve(mainSpace);
                fixedTotal += mainSizes[i];
            }
        }

        double free = Math.max(0, mainSpace - gaps - fixedTotal);
        for (int i = 0; i < children.size(); i++) {
            if (mainSizes[i] < 0) {
                Extent main = vertical ? children.get(i).height() : children.get(i).width();
                mainSizes[i] = main.clamp(free * main.value() / weightTotal);
            }
        }

        double used = gaps;
        for (double size : mainSizes) {
            used += size;
        }
        double cursor = switch (layout.main()) {
            case START, SPACE_BETWEEN -> 0;
            case CENTER -> (mainSpace - used) / 2;
            case END -> mainSpace - used;
        };
        // SPACE_BETWEEN répartit le reste ENTRE les enfants : les extrêmes touchent les
        // bords, ce qu'aucun espacement fixe ne donne quand le conteneur change de taille.
        double extraGap = layout.main() == LayoutSpec.Distribute.SPACE_BETWEEN
                && children.size() > 1
                ? Math.max(0, mainSpace - used) / (children.size() - 1) : 0;

        List<Rect> out = new ArrayList<>(children.size());
        for (int i = 0; i < children.size(); i++) {
            ScreenElement child = children.get(i);
            Extent crossExtent = vertical ? child.width() : child.height();
            double crossHug = hugs[i] == null ? -1 : hugs[i][vertical ? 0 : 1];
            // Un enfant qui s'ajuste garde sa mesure : l'étirer contredirait ce qu'on
            // vient de lui demander.
            boolean stretched = crossHug < 0
                    && (layout.cross() == LayoutSpec.Cross.STRETCH
                        || crossExtent.mode() == Extent.Mode.FILL);
            double crossSize = crossHug >= 0 ? crossHug
                    : stretched ? crossExtent.clamp(crossSpace)
                    : crossExtent.resolve(crossSpace);
            double crossOffset = switch (layout.cross()) {
                case START, STRETCH -> 0;
                case CENTER -> (crossSpace - crossSize) / 2;
                case END -> crossSpace - crossSize;
            };
            out.add(vertical
                    ? new Rect(area.x() + crossOffset, area.y() + cursor, crossSize, mainSizes[i])
                    : new Rect(area.x() + cursor, area.y() + crossOffset, mainSizes[i], crossSize));
            cursor += mainSizes[i] + layout.gap() + extraGap;
        }
        return out;
    }

    /**
     * Une grille : {@code columns} colonnes de largeur égale, autant de rangées qu'il
     * faut. La hauteur d'une rangée est celle de son plus grand enfant — sinon un élément
     * haut mordrait sur la rangée suivante.
     */
    private static List<Rect> grid(Rect area, LayoutSpec layout, List<ScreenElement> children) {
        int columns = Math.max(1, layout.columns());
        double cellWidth = Math.max(0,
                (area.width() - layout.gap() * (columns - 1)) / columns);

        int rows = (children.size() + columns - 1) / columns;
        double[] rowHeights = new double[rows];
        for (int i = 0; i < children.size(); i++) {
            Extent height = children.get(i).height();
            double resolved = height.mode() == Extent.Mode.FILL
                    ? ScreenElement.MIN_SIZE : height.resolve(area.height());
            rowHeights[i / columns] = Math.max(rowHeights[i / columns], resolved);
        }

        List<Rect> out = new ArrayList<>(children.size());
        double y = area.y();
        for (int i = 0; i < children.size(); i++) {
            int column = i % columns;
            if (column == 0 && i > 0) {
                y += rowHeights[i / columns - 1] + layout.crossGap();
            }
            Extent width = children.get(i).width();
            double cell = width.mode() == Extent.Mode.FIXED
                    ? Math.min(cellWidth, width.resolve(cellWidth)) : cellWidth;
            out.add(new Rect(area.x() + column * (cellWidth + layout.gap()), y,
                    cell, rowHeights[i / columns]));
        }
        return out;
    }

    /** Le rectangle du parent de {@code element}, ou la fenêtre s'il est à la racine. */
    public static Rect parentRect(Screen screen, ScreenElement element,
                                  double viewportWidth, double viewportHeight) {
        ScreenElement parent = element.parent() == null ? null : screen.element(element.parent());
        return parent == null
                ? new Rect(0, 0, viewportWidth, viewportHeight)
                : resolve(screen, parent, viewportWidth, viewportHeight);
    }

    /**
     * L'<b>inverse</b> de {@link #within} : l'élément à écrire pour qu'il occupe
     * {@code target} dans ce parent. C'est ce que produit un glisser ou une poignée de
     * redimensionnement — la souris donne un rectangle, le modèle veut un décalage
     * depuis l'ancre.
     *
     * <p>Le couple aller/retour vit ici, dans le même fichier : séparés, l'un des deux
     * finirait par oublier l'ancre, et l'élément sauterait au relâchement de la souris.
     */
    public static ScreenElement placedIn(Rect parent, ScreenElement element, Rect target) {
        Extent width = fit(element.width(), target.width(), parent.width());
        Extent height = fit(element.height(), target.height(), parent.height());
        // Largeur EFFECTIVE après bornes : c'est elle qui décale le rectangle, pas
        // celle qu'on visait. Utiliser la cible ferait dériver un élément borné à
        // chaque glisser.
        double effectiveW = width.resolve(parent.width());
        double effectiveH = height.resolve(parent.height());
        double x = target.x() - parent.x() - parent.width() * element.anchor().fractionX()
                + effectiveW * element.anchor().fractionX();
        double y = target.y() - parent.y() - parent.height() * element.anchor().fractionY()
                + effectiveH * element.anchor().fractionY();
        return element.movedTo(x, y).resized(width, height);
    }

    /**
     * Une longueur qui vaut {@code resolved} en gardant la <b>nature</b> de
     * {@code template}. Une taille exprimée en pourcentage le reste : la convertir en
     * unités fixes au premier redimensionnement détruirait sans le dire l'adaptation
     * que l'auteur avait choisie, et son menu cesserait de suivre la fenêtre.
     */
    public static Extent fit(Extent template, double resolved, double parentSize) {
        if (!template.relative()) {
            return Extent.of(resolved);
        }
        if (parentSize <= 0) {
            return template;
        }
        return Extent.percent(resolved / parentSize, template.min(), template.max());
    }

    /** Le rectangle de {@code element} dans celui de son parent. */
    public static Rect within(Rect parent, ScreenElement element) {
        return within(parent, element, null);
    }

    /**
     * La même chose, avec une taille déjà mesurée quand l'élément s'ajuste à son contenu.
     * {@code measured[i] < 0} signifie « la taille ordinaire suffit sur cet axe ».
     */
    private static Rect within(Rect parent, ScreenElement element,
                               double @Nullable [] measured) {
        double width = measured != null && measured[0] >= 0
                ? measured[0] : element.width().resolve(parent.width());
        double height = measured != null && measured[1] >= 0
                ? measured[1] : element.height().resolve(parent.height());
        // L'ancre sert deux fois : elle place le point de référence dans le parent, et
        // elle dit quel point de l'élément vient s'y poser. Un élément centré doit
        // reculer d'une demi-largeur, sinon « centré » signifierait « à droite du
        // centre » — l'erreur classique, et invisible tant qu'on ne teste qu'à gauche.
        double anchorX = parent.x() + parent.width() * element.anchor().fractionX();
        double anchorY = parent.y() + parent.height() * element.anchor().fractionY();
        return new Rect(anchorX - width * element.anchor().fractionX() + element.x(),
                anchorY - height * element.anchor().fractionY() + element.y(),
                width, height);
    }
}
