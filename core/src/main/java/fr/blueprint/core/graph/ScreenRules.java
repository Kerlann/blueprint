package fr.blueprint.core.graph;

import fr.blueprint.core.graph.screen.Extent;
import fr.blueprint.core.graph.screen.Screen;
import fr.blueprint.core.graph.screen.ScreenElement;
import fr.blueprint.core.graph.screen.ScreenLayout;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Les règles de placement d'un élément d'écran — <b>la source de vérité unique</b>,
 * comme {@code GraphValidator.canLink} l'est pour le câblage (story 10.1, AC5).
 *
 * <p>Les opérations d'édition et le validateur l'appellent tous deux. Deux jeux de
 * règles divergeraient : l'éditeur laisserait poser ce que le validateur refuse
 * ensuite, ou l'inverse — et l'auteur ne saurait pas lequel croire.
 */
public final class ScreenRules {

    private ScreenRules() {
    }

    /**
     * Vérifie qu'un élément peut être posé ou modifié dans cet écran. Rend le
     * diagnostic qui l'interdit, ou {@code null} si tout va bien.
     */
    public static @Nullable Diagnostic checkPlacement(String screenName, Screen screen,
                                                      ScreenElement element,
                                                      GraphLimits limits) {
        // « S'ajuster au contenu » suppose de mesurer ce contenu. Un conteneur mesure
        // ses enfants, ce que la passe de disposition sait faire des deux côtés. Un
        // libellé, lui, exigerait de mesurer la police — une mesure qui n'existe que
        // côté client, alors que cette règle tourne aussi côté serveur. Deux mesures
        // divergentes reproduiraient exactement le défaut que la passe existe pour
        // empêcher (story 10.10).
        if (!element.kind().container()
                && (element.width().mode() == Extent.Mode.HUG
                    || element.height().mode() == Extent.Mode.HUG)) {
            return Diagnostic.error(DiagnosticCode.ELEMENT_HUG_NOT_CONTAINER,
                    Diagnostic.element(screenName, element.name()),
                    element.name(), element.kind().name());
        }

        // Taille : sous le minimum, un élément ne se clique plus et ne se rattrape
        // plus dans le concepteur — il devient un piège. Mesurée dans la place réelle
        // du parent, et pas dans celle de l'écran : « 50 % » d'un panneau de 40 unités
        // fait 20, pas 160, et l'approximation laissait passer des éléments minuscules
        // dès qu'ils étaient imbriqués.
        //
        // Sauf si le parent RANGE ses enfants : la taille d'un enfant rangé se décide
        // entre frères, et {@code resolve} — qui ne connaît que le parent — en donnerait
        // une valeur fictive. Interdire un élément d'après une taille qu'il n'aura pas
        // rendrait impossible de poser le deuxième enfant d'une colonne serrée. Ce cas
        // est repris sur la disposition entière par {@link #squeezed}.
        if (!arrangedByParent(screen, element)) {
            ScreenLayout.Rect rect = ScreenLayout.resolve(screen, element,
                    Screen.SAFE_WIDTH, Screen.SAFE_HEIGHT);
            if (rect.width() < ScreenElement.MIN_SIZE
                    || rect.height() < ScreenElement.MIN_SIZE) {
                return Diagnostic.error(DiagnosticCode.ELEMENT_TOO_SMALL,
                        Diagnostic.element(screenName, element.name()),
                        element.name(), (int) ScreenElement.MIN_SIZE);
            }
        }

        // Le texte agrandi ne tient plus dans son élément.
        //
        // Ne contrôle que la HAUTEUR, jamais la largeur, et c'est délibéré. La hauteur
        // d'une ligne est exacte — neuf pixels, quel que soit le contenu — donc le
        // reproche est sûr : s'il se déclenche, le texte est vraiment coupé. La largeur,
        // elle, dépend des caractères, et le texte peut être une CLÉ de traduction : la
        // mesurer ici donnerait un avertissement juste en français et faux dans les vingt
        // autres langues du serveur. Mieux vaut un contrôle qui se tait sur ce qu'il ne
        // sait pas — c'est déjà le parti de NodeGeometry, qui estime la largeur d'une
        // pastille plutôt que de réclamer la police.
        //
        // Un avertissement et non une erreur : un texte coupé est laid, il n'empêche pas
        // le menu de fonctionner, et refuser d'exécuter pour cela serait disproportionné.
        if (showsText(element) && !arrangedByParent(screen, element)) {
            ScreenLayout.Rect rect = ScreenLayout.resolve(screen, element,
                    Screen.SAFE_WIDTH, Screen.SAFE_HEIGHT);
            double lineHeight = TEXT_LINE_HEIGHT * element.style().textScale();
            double room = rect.height() - element.style().padding() * 2.0;
            if (lineHeight > room) {
                return Diagnostic.warning(DiagnosticCode.ELEMENT_TEXT_TOO_TALL,
                        Diagnostic.element(screenName, element.name()),
                        element.name(), trim(element.style().textScale()));
            }
        }

        // Un HUD ne capte pas la souris (story 10.9) : un bouton y serait un leurre.
        if (screen.hud() && element.kind().interactive()) {
            return Diagnostic.error(DiagnosticCode.INTERACTIVE_IN_HUD,
                    Diagnostic.element(screenName, element.name()),
                    element.name(), element.kind().name());
        }

        String parent = element.parent();
        if (parent == null) {
            return null;
        }
        if (parent.equals(element.name())) {
            return Diagnostic.error(DiagnosticCode.ELEMENT_PARENT_CYCLE,
                    Diagnostic.element(screenName, element.name()), element.name());
        }
        ScreenElement container = screen.element(parent);
        if (container == null) {
            return Diagnostic.error(DiagnosticCode.ELEMENT_PARENT_NOT_FOUND,
                    Diagnostic.element(screenName, element.name()), element.name(), parent);
        }
        if (!container.kind().container()) {
            return Diagnostic.error(DiagnosticCode.ELEMENT_NOT_CONTAINER,
                    Diagnostic.element(screenName, element.name()), parent,
                    container.kind().name());
        }
        // Un cycle de parenté rendrait toute la branche invisible et impossible à
        // atteindre : ni le rendu ni le concepteur ne sauraient par où commencer.
        if (createsCycle(screen, element)) {
            return Diagnostic.error(DiagnosticCode.ELEMENT_PARENT_CYCLE,
                    Diagnostic.element(screenName, element.name()), element.name());
        }
        return null;
    }

    /** Le parent de cet élément range-t-il ses enfants ? */
    /**
     * La hauteur d'une ligne de texte, en unités d'écran.
     *
     * <p>Neuf : la police de Minecraft mesure huit pixels plus un d'interligne, et une
     * unité d'écran vaut un pixel d'interface. Écrite ici plutôt que lue de la police
     * parce que le validateur tourne aussi <b>côté serveur</b>, où aucune police n'existe.
     */
    private static final double TEXT_LINE_HEIGHT = 9;

    /** Les types qui affichent des mots — les seuls que la taille du texte concerne. */
    private static boolean showsText(ScreenElement element) {
        return switch (element.kind()) {
            case LABEL, BUTTON, INPUT, TOGGLE, DROPDOWN -> !element.text().value().isEmpty();
            case PANEL, IMAGE, PROGRESS, SLOT, SLIDER, LIST, ENTITY_PREVIEW -> false;
        };
    }

    /** « 1.5 » et non « 1.5000000 » : le nombre part dans un message lu par un humain. */
    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value)
                : String.valueOf(value);
    }

    private static boolean arrangedByParent(Screen screen, ScreenElement element) {
        if (element.parent() == null) {
            return false;
        }
        ScreenElement container = screen.element(element.parent());
        return container != null && container.arranges();
    }

    /** Remonter la chaîne de parenté depuis l'élément : y revenir = cycle. */
    private static boolean createsCycle(Screen screen, ScreenElement element) {
        Set<String> seen = new HashSet<>();
        seen.add(element.name());
        String cursor = element.parent();
        while (cursor != null) {
            if (!seen.add(cursor)) {
                return true;
            }
            ScreenElement up = screen.element(cursor);
            cursor = up == null ? null : up.parent();
        }
        return false;
    }

    /**
     * Un élément déborde-t-il de la zone garantie ? <b>Avertissement</b>, jamais
     * erreur : un menu conçu large reste valide, mais son auteur doit apprendre à la
     * conception qu'une partie de ses joueurs ne le verra pas entier — plutôt que par
     * un rapport de bug.
     *
     * <p>Sur le rectangle RÉSOLU par la passe de disposition, jamais sur x/y bruts :
     * ceux-ci sont un décalage depuis l'ancre, dans le parent. Les lire comme des
     * coordonnées d'écran signalait tout élément centré au décalage négatif — un faux
     * avertissement qui apprend vite à ignorer les vrais.
     */
    public static boolean outsideSafeArea(ScreenLayout.Rect rect) {
        return rect.x() < 0 || rect.y() < 0
                || rect.right() > Screen.SAFE_WIDTH || rect.bottom() > Screen.SAFE_HEIGHT;
    }

    /**
     * Un élément a-t-il été <b>écrasé</b> par la disposition de son parent ? Une part
     * {@code FILL} ne prend que ce qui reste : quand rien ne reste, elle tombe à zéro.
     * L'élément est toujours là, toujours valide, et invisible — le genre de panne qui
     * fait chercher l'erreur dans le graphe alors qu'elle est dans le cadre.
     *
     * <p>Avertissement et non erreur : la fenêtre du joueur est le plus souvent plus
     * grande que la zone garantie, où la place peut suffire.
     */
    public static boolean squeezed(ScreenLayout.Rect rect) {
        return rect.width() < ScreenElement.MIN_SIZE || rect.height() < ScreenElement.MIN_SIZE;
    }

    /**
     * Le style nommé que suit cet élément est-il introuvable ? Avertissement : l'élément
     * retombe sur son style en ligne et reste dessiné. Un style renommé ne doit pas
     * rendre invalide un écran qui marchait.
     */
    public static boolean styleNotFound(Screen screen, ScreenElement element) {
        return element.followsNamedStyle() && !screen.styles().containsKey(element.styleName());
    }

    /**
     * Ce conteneur défile-t-il alors que sa hauteur s'ajuste à ses enfants ?
     *
     * <p>Il grandit avec son contenu, donc rien ne dépasse jamais, donc il ne défile
     * <b>jamais</b>. La case est cochée, le panneau a l'air correct, et la molette ne fait
     * rien — sans ce mot, l'auteur en conclurait que le défilement est cassé plutôt que
     * mal réglé. Même famille que les poignées inopérantes de la 10.10 : un contrôle qui
     * ne peut rien faire doit le dire.
     *
     * <p>Avertissement et non erreur : l'écran reste parfaitement affichable, et refuser
     * l'enregistrement pour un réglage sans effet serait disproportionné.
     */
    public static boolean scrollsButHugs(ScreenElement element) {
        var hug = fr.blueprint.core.graph.screen.Extent.Mode.HUG;
        return (element.scrollsVertically() && element.height().mode() == hug)
                || (element.scrollsHorizontally() && element.width().mode() == hug);
    }
}
