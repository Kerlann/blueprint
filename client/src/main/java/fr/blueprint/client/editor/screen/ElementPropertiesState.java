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
        BACKGROUND, BORDER, TEXT_COLOR, HOVER, PADDING
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
            case TEXTURE -> element.texture() == null ? "" : element.texture().toString();
            case BACKGROUND -> hex(element.style().background());
            case BORDER -> hex(element.style().border());
            case TEXT_COLOR -> hex(element.style().textColor());
            case HOVER -> hex(element.style().hoverBackground());
            case PADDING -> String.valueOf(element.style().padding());
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
            case X, Y, PADDING -> parseNumber(buffer) != null;
            case WIDTH, HEIGHT -> parseExtent(buffer, Extent.of(0)) != null;
            case TEXTURE -> buffer.isBlank() || Identifier.tryParse(buffer.trim()) != null;
            case BACKGROUND, BORDER, TEXT_COLOR, HOVER -> parseHex(buffer) != null;
            case TEXT -> true;
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
            case TEXTURE -> element.withTexture(
                    buffer.isBlank() ? null : Identifier.tryParse(buffer.trim()));
            case BACKGROUND -> element.styled(withBackground(element.style(), parseHex(buffer)));
            case BORDER -> element.styled(withBorder(element.style(), parseHex(buffer)));
            case TEXT_COLOR -> element.styled(withTextColor(element.style(), parseHex(buffer)));
            case HOVER -> element.styled(withHover(element.style(), parseHex(buffer)));
            case PADDING -> element.styled(withPadding(element.style(),
                    Math.max(0, (int) (double) parseNumber(buffer))));
        };
        cancel();
        return out;
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
        return new ScreenElement(element.name(), element.kind(), element.parent(),
                values.get(index), element.x(), element.y(), element.width(), element.height(),
                element.text(), element.texture(), element.style(),
                element.visible(), element.enabled());
    }

    // ------------------------------------------------------------------ formats

    private static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    /** {@code 80} ou {@code 50%} — la même écriture qu'en BScript, pas une seconde. */
    private static String extent(Extent value) {
        return value.relative()
                ? number(java.math.BigDecimal.valueOf(value.value()).movePointRight(2)
                .stripTrailingZeros().doubleValue()) + "%"
                : number(value.value());
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

    private static @Nullable Extent parseExtent(String text, Extent template) {
        String trimmed = text.trim();
        boolean relative = trimmed.endsWith("%");
        Double value = parseNumber(relative ? trimmed.substring(0, trimmed.length() - 1) : trimmed);
        if (value == null) {
            return null;
        }
        try {
            return relative
                    ? Extent.percent(java.math.BigDecimal.valueOf(value).movePointLeft(2)
                    .doubleValue(), template.min(), template.max())
                    : Extent.of(value);
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
