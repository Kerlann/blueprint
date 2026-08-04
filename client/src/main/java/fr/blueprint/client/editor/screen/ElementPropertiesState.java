package fr.blueprint.client.editor.screen;

import fr.blueprint.core.graph.screen.Anchor;
import fr.blueprint.core.graph.screen.ElementStyle;
import fr.blueprint.core.graph.screen.Extent;
import fr.blueprint.core.graph.screen.ScreenElement;
import fr.blueprint.core.graph.screen.ScreenText;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Le panneau de propriétés de l'élément sélectionné (story 10.2, AC7) : nom, position,
 * taille, ancre, couleurs, texture, texte.
 *
 * <p>État pur, testable sans client. Chaque champ garde son <b>texte en cours de
 * frappe</b> à côté de la valeur du modèle : sans cela, taper « -1 » serait impossible —
 * le « - » seul ne se convertit pas en nombre, la conversion échouerait, et le champ
 * reviendrait à sa valeur d'avant à chaque caractère.
 *
 * <p>Le nom est vérifié <b>pendant la frappe</b> (AC7) : un doublon doit se voir avant
 * de valider, pas après. Un champ invalide n'est jamais écrit dans le modèle — l'auteur
 * corrige ou annule, il ne casse rien en chemin.
 */
public final class ElementPropertiesState {

    /** Les champs éditables, dans l'ordre du panneau. */
    public enum Field {
        NAME, X, Y, WIDTH, HEIGHT, TEXT, TEXTURE,
        BACKGROUND, BORDER, TEXT_COLOR, HOVER, PADDING,
        /** Réglages de disposition d'un conteneur (story 10.10). */
        GAP, CROSS_GAP, COLUMNS,
        /** Liaison de données (story 10.7). */
        BIND_FORMAT, BIND_DECIMALS, BIND_MIN, BIND_MAX,
        /** Réglages propres aux éléments riches (story 10.8). */
        PLACEHOLDER, MAX_LENGTH, OPT_MIN, OPT_MAX, STEP, ROW_HEIGHT, ENTITY
    }

    private @Nullable ScreenElement element;
    private @Nullable Field editing;
    private String buffer = "";

    /**
     * Recharge le panneau depuis l'élément sélectionné. Une frappe en cours sur le
     * MÊME élément est préservée : revalider le graphe (débouncé) ne doit pas effacer
     * ce que l'auteur est en train de taper.
     */
    public void select(@Nullable ScreenElement selected) {
        if (selected == null) {
            element = null;
            cancel();
            return;
        }
        if (element == null || !element.name().equals(selected.name())) {
            cancel();
        }
        element = selected;
    }

    public @Nullable ScreenElement element() {
        return element;
    }

    public @Nullable Field editing() {
        return editing;
    }

    public String buffer() {
        return buffer;
    }

    public boolean isEditing(Field field) {
        return editing == field;
    }

    /** Ouvre un champ à la frappe, pré-rempli avec sa valeur courante. */
    public void beginEdit(Field field) {
        if (element == null) {
            return;
        }
        editing = field;
        buffer = valueOf(field);
    }

    public void cancel() {
        editing = null;
        buffer = "";
    }

    public void type(char c) {
        if (editing != null) {
            buffer += c;
        }
    }

    public void backspace() {
        if (editing != null && !buffer.isEmpty()) {
            buffer = buffer.substring(0, buffer.length() - 1);
        }
    }

    /** La valeur affichée d'un champ, telle qu'elle apparaît dans le panneau. */
    public String valueOf(Field field) {
        if (element == null) {
            return "";
        }
        return switch (field) {
            case NAME -> element.name();
            case X -> number(element.x());
            case Y -> number(element.y());
            case WIDTH -> extent(element.width());
            case HEIGHT -> extent(element.height());
            case TEXT -> element.text().value();
            // L'écriture COURTE d'un pack : « ma_boutique/fond », pas
            // « blueprint:pack/ma_boutique/fond ». C'est celle que l'auteur tape.
            case TEXTURE -> element.texture() == null ? ""
                    : fr.blueprint.core.graph.screen.PackRef.reference(element.texture());
            case BACKGROUND -> hex(element.style().background());
            case BORDER -> hex(element.style().border());
            case TEXT_COLOR -> hex(element.style().textColor());
            case HOVER -> hex(element.style().hoverBackground());
            case PADDING -> String.valueOf(element.style().padding());
            case GAP -> number(element.layout().gap());
            case CROSS_GAP -> number(element.layout().crossGap());
            case COLUMNS -> String.valueOf(element.layout().columns());
            case BIND_FORMAT -> element.binding().format();
            case BIND_DECIMALS -> String.valueOf(element.binding().decimals());
            case BIND_MIN -> number(element.binding().min());
            case BIND_MAX -> number(element.binding().max());
            case PLACEHOLDER -> element.options().placeholder();
            case MAX_LENGTH -> String.valueOf(element.options().maxLength());
            case OPT_MIN -> number(element.options().min());
            case OPT_MAX -> number(element.options().max());
            case STEP -> number(element.options().step());
            case ROW_HEIGHT -> number(element.options().rowHeight());
            case ENTITY -> element.options().entity() == null ? ""
                    : element.options().entity().toString();
        };
    }

    /**
     * Le contenu tapé est-il acceptable ? Sert au retour <b>en direct</b> : le champ
     * vire au rouge pendant la frappe, et non au moment de valider.
     *
     * <p>Le nom demande un contrôle d'unicité, que seul l'appelant peut faire ; d'où
     * le prédicat, plutôt qu'un accès à l'écran depuis un état qui doit rester pur.
     */
    public boolean valid(java.util.function.Predicate<String> nameAvailable) {
        if (editing == null || element == null) {
            return true;
        }
        return switch (editing) {
            case NAME -> nameAvailable.test(buffer.trim());
            case X, Y, PADDING, GAP, CROSS_GAP, COLUMNS,
                 BIND_DECIMALS, BIND_MIN, BIND_MAX,
                 MAX_LENGTH, OPT_MIN, OPT_MAX, STEP, ROW_HEIGHT -> parseNumber(buffer) != null;
            case WIDTH, HEIGHT -> parseExtent(buffer, Extent.of(0)) != null;
            case TEXTURE -> buffer.isBlank()
                    || fr.blueprint.core.graph.screen.PackRef.texture(buffer) != null;
            case BACKGROUND, BORDER, TEXT_COLOR, HOVER -> parseHex(buffer) != null;
            case TEXT, BIND_FORMAT, PLACEHOLDER -> true;
            case ENTITY -> buffer.isBlank() || Identifier.tryParse(buffer.trim()) != null;
        };
    }

    /**
     * L'élément modifié, ou {@code null} si la frappe est inutilisable. Le champ reste
     * alors ouvert : rien n'est écrit, et l'auteur voit encore ce qu'il a tapé.
     */
    public @Nullable ScreenElement commit(java.util.function.Predicate<String> nameAvailable) {
        if (element == null || editing == null || !valid(nameAvailable)) {
            return null;
        }
        ScreenElement out = switch (editing) {
            case NAME -> element;   // le renommage passe par une opération à part
            case X -> element.movedTo(parseNumber(buffer), element.y());
            case Y -> element.movedTo(element.x(), parseNumber(buffer));
            case WIDTH -> element.resized(parseExtent(buffer, element.width()), element.height());
            case HEIGHT -> element.resized(element.width(), parseExtent(buffer, element.height()));
            case TEXT -> element.withText(buffer.startsWith("#")
                    ? ScreenText.key(buffer.substring(1)) : ScreenText.literal(buffer));
            case TEXTURE -> element.withTexture(buffer.isBlank() ? null
                    : fr.blueprint.core.graph.screen.PackRef.texture(buffer));
            case BACKGROUND -> element.styled(withBackground(element.style(), parseHex(buffer)));
            case BORDER -> element.styled(withBorder(element.style(), parseHex(buffer)));
            case TEXT_COLOR -> element.styled(withTextColor(element.style(), parseHex(buffer)));
            case HOVER -> element.styled(withHover(element.style(), parseHex(buffer)));
            case PADDING -> element.styled(withPadding(element.style(),
                    Math.max(0, (int) (double) parseNumber(buffer))));
            case GAP -> element.withLayout(element.layout().withGap(parseNumber(buffer)));
            case CROSS_GAP ->
                    element.withLayout(element.layout().withCrossGap(parseNumber(buffer)));
            case COLUMNS -> element.withLayout(
                    element.layout().withColumns((int) (double) parseNumber(buffer)));
            case BIND_FORMAT -> element.withBinding(element.binding().withFormat(buffer));
            case BIND_DECIMALS -> element.withBinding(
                    element.binding().withDecimals((int) (double) parseNumber(buffer)));
            case BIND_MIN -> element.withBinding(element.binding()
                    .withRange(parseNumber(buffer), element.binding().max()));
            case BIND_MAX -> element.withBinding(element.binding()
                    .withRange(element.binding().min(), parseNumber(buffer)));
            case PLACEHOLDER -> element.withOptions(element.options().withPlaceholder(buffer));
            case MAX_LENGTH -> element.withOptions(
                    element.options().withMaxLength((int) (double) parseNumber(buffer)));
            case OPT_MIN -> element.withOptions(element.options()
                    .withRange(parseNumber(buffer), element.options().max()));
            case OPT_MAX -> element.withOptions(element.options()
                    .withRange(element.options().min(), parseNumber(buffer)));
            case STEP -> element.withOptions(element.options().withStep(parseNumber(buffer)));
            case ROW_HEIGHT -> element.withOptions(
                    element.options().withRowHeight(parseNumber(buffer)));
            case ENTITY -> element.withOptions(element.options().withEntity(
                    buffer.isBlank() ? null : Identifier.tryParse(buffer.trim())));
        };
        cancel();
        return out;
    }

    // ------------------------------------------------- manipulation directe (10.10)

    /**
     * Pose l'ancre par sa case dans la grille 3×3. Elle se faisait défiler d'un clic
     * parmi neuf valeurs, à l'aveugle : il fallait jusqu'à huit clics pour atteindre
     * celle qu'on voulait, sans jamais voir laquelle venait ensuite.
     */
    public @Nullable ScreenElement setAnchor(int column, int row) {
        if (element == null || column < 0 || column > 2 || row < 0 || row > 2) {
            return null;
        }
        return element.withAnchor(Anchor.of(column, row));
    }

    /** La case de la grille où l'ancre courante s'allume, {@code [colonne, ligne]}. */
    public int[] anchorCell() {
        return element == null ? new int[]{0, 0} : element.anchor().cell();
    }

    /**
     * Change le <b>mode</b> d'une taille en gardant ses bornes. Les quatre modes se
     * choisissent par quatre boutons : les taper en texte demandait de connaître une
     * syntaxe qu'aucun panneau n'affiche.
     */
    public @Nullable ScreenElement setSizeMode(boolean horizontal, Extent.Mode mode) {
        if (element == null) {
            return null;
        }
        Extent current = horizontal ? element.width() : element.height();
        // Une valeur qui n'a pas de sens dans le nouveau mode est remplacée par celle
        // qui en a une : reprendre 80 comme fraction donnerait 8000 % de son parent.
        double value = switch (mode) {
            case FIXED -> current.mode() == Extent.Mode.FIXED ? current.value()
                    : Math.max(ScreenElement.MIN_SIZE, current.min());
            case PERCENT -> current.mode() == Extent.Mode.PERCENT ? current.value() : 1;
            case FILL -> current.mode() == Extent.Mode.FILL ? current.value() : 1;
            case HUG -> 0;
        };
        Extent next = new Extent(mode, value, current.min(), current.max());
        return horizontal ? element.resized(next, element.height())
                : element.resized(element.width(), next);
    }

    /** Un champ de valeur n'a de sens que si le mode en consomme une. */
    public boolean sizeValueMatters(boolean horizontal) {
        if (element == null) {
            return false;
        }
        return (horizontal ? element.width() : element.height()).mode() != Extent.Mode.HUG;
    }

    public @Nullable ScreenElement setLayoutMode(
            fr.blueprint.core.graph.screen.LayoutSpec.Mode mode) {
        return element == null ? null : element.withLayout(element.layout().withMode(mode));
    }

    public @Nullable ScreenElement setLayoutMain(
            fr.blueprint.core.graph.screen.LayoutSpec.Distribute main) {
        return element == null ? null : element.withLayout(element.layout().withMain(main));
    }

    public @Nullable ScreenElement setLayoutCross(
            fr.blueprint.core.graph.screen.LayoutSpec.Cross cross) {
        return element == null ? null : element.withLayout(element.layout().withCross(cross));
    }

    /** Change ce qu'un champ de saisie accepte (10.8). */
    public @Nullable ScreenElement setFilter(
            fr.blueprint.core.graph.screen.ElementOptions.InputFilter filter) {
        return element == null ? null
                : element.withOptions(element.options().withFilter(filter));
    }

    /**
     * Lie l'élément à une variable, ou l'en détache par un nom vide. La variable se
     * <b>choisit</b> dans la liste du blueprint : la taper laisserait passer une faute de
     * frappe que seul le validateur signalerait, une fois le geste oublié.
     */
    public @Nullable ScreenElement bindTo(String variable) {
        if (element == null) {
            return null;
        }
        return element.withBinding(variable.isEmpty()
                ? fr.blueprint.core.graph.screen.ElementBinding.NONE
                : element.binding().withVariable(variable));
    }

    public @Nullable ScreenElement bindTarget(
            fr.blueprint.core.graph.screen.ElementBinding.Target target) {
        return element == null ? null
                : element.withBinding(element.binding().withTarget(target));
    }

    /**
     * Fait suivre un style nommé à l'élément, ou l'en détache par un nom vide. Détacher
     * ne recopie <b>pas</b> le style nommé dans l'élément : celui-ci retrouve le sien,
     * qui n'a jamais cessé d'être là.
     */
    public @Nullable ScreenElement useStyle(String styleName) {
        return element == null ? null : element.withStyleName(styleName);
    }

    /** Le nom validé, quand c'est lui qu'on éditait — le renommage est une opération. */
    public @Nullable String pendingName() {
        return editing == Field.NAME ? buffer.trim() : null;
    }

    /** Fait tourner l'ancre : neuf valeurs se choisissent au clic, pas à la frappe. */
    public @Nullable ScreenElement cycleAnchor(int delta) {
        if (element == null) {
            return null;
        }
        List<Anchor> values = List.of(Anchor.values());
        int index = Math.floorMod(values.indexOf(element.anchor()) + delta, values.size());
        return element.withAnchor(values.get(index));
    }

    // ------------------------------------------------------------------ formats

    private static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    /** {@code 80}, {@code 50%}, {@code fill}… — la même écriture qu'en BScript, pas une seconde. */
    private static String extent(Extent value) {
        return switch (value.mode()) {
            case FIXED -> number(value.value());
            case PERCENT -> number(java.math.BigDecimal.valueOf(value.value()).movePointRight(2)
                    .stripTrailingZeros().doubleValue()) + "%";
            case FILL -> value.value() == 1 ? "fill" : "fill:" + number(value.value());
            case HUG -> "hug";
        };
    }

    private static String hex(int argb) {
        return String.format("#%08X", argb);
    }

    private static @Nullable Double parseNumber(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * La même écriture qu'en BScript : {@code 80}, {@code 50%}, {@code fill},
     * {@code fill:2}, {@code hug}. Les bornes du modèle sont conservées — elles ne se
     * tapent pas ici, et les perdre à chaque frappe rendrait tout menu borné intenable.
     */
    private static @Nullable Extent parseExtent(String text, Extent template) {
        String trimmed = text.trim().toLowerCase(java.util.Locale.ROOT);
        Extent.Mode mode;
        Double value;
        if (trimmed.equals("hug")) {
            mode = Extent.Mode.HUG;
            value = 0d;
        } else if (trimmed.equals("fill")) {
            mode = Extent.Mode.FILL;
            value = 1d;
        } else if (trimmed.startsWith("fill:")) {
            mode = Extent.Mode.FILL;
            value = parseNumber(trimmed.substring(5));
        } else if (trimmed.endsWith("%")) {
            mode = Extent.Mode.PERCENT;
            Double percent = parseNumber(trimmed.substring(0, trimmed.length() - 1));
            value = percent == null ? null
                    : java.math.BigDecimal.valueOf(percent).movePointLeft(2).doubleValue();
        } else {
            mode = Extent.Mode.FIXED;
            value = parseNumber(trimmed);
        }
        if (value == null) {
            return null;
        }
        try {
            return new Extent(mode, value, template.min(), template.max());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static @Nullable Integer parseHex(String text) {
        String trimmed = text.trim().replace("#", "");
        if (trimmed.isEmpty() || trimmed.length() > 8) {
            return null;
        }
        try {
            return (int) Long.parseLong(trimmed, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Les records n'ont pas de « with » : neuf champs, quatre variantes utiles ici.

    private static ElementStyle withBackground(ElementStyle s, int value) {
        return new ElementStyle(value, s.border(), s.borderWidth(), s.textColor(),
                s.hoverBackground(), s.pressedBackground(), s.disabledBackground(),
                s.padding(), s.align());
    }

    private static ElementStyle withBorder(ElementStyle s, int value) {
        return new ElementStyle(s.background(), value, s.borderWidth(), s.textColor(),
                s.hoverBackground(), s.pressedBackground(), s.disabledBackground(),
                s.padding(), s.align());
    }

    private static ElementStyle withTextColor(ElementStyle s, int value) {
        return new ElementStyle(s.background(), s.border(), s.borderWidth(), value,
                s.hoverBackground(), s.pressedBackground(), s.disabledBackground(),
                s.padding(), s.align());
    }

    private static ElementStyle withHover(ElementStyle s, int value) {
        return new ElementStyle(s.background(), s.border(), s.borderWidth(), s.textColor(),
                value, s.pressedBackground(), s.disabledBackground(), s.padding(), s.align());
    }

    private static ElementStyle withPadding(ElementStyle s, int value) {
        return new ElementStyle(s.background(), s.border(), s.borderWidth(), s.textColor(),
                s.hoverBackground(), s.pressedBackground(), s.disabledBackground(),
                value, s.align());
    }
}
