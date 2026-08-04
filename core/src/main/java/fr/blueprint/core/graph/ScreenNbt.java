package fr.blueprint.core.graph;

import fr.blueprint.core.graph.screen.Anchor;
import fr.blueprint.core.graph.screen.ElementKind;
import fr.blueprint.core.graph.screen.ElementStyle;
import fr.blueprint.core.graph.screen.Extent;
import fr.blueprint.core.graph.screen.LayoutSpec;
import fr.blueprint.core.graph.screen.Screen;
import fr.blueprint.core.graph.screen.ScreenElement;
import fr.blueprint.core.graph.screen.ScreenText;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sérialisation NBT des écrans (story 10.1, AC3).
 *
 * <p><b>Préservation intégrale.</b> Un élément d'un type que ce serveur ne connaît pas
 * — parce qu'un mod a été retiré, ou parce que le monde vient d'une version plus
 * récente — traverse sauvegarde et chargement <b>sans rien perdre</b>. C'est la même
 * promesse que les nœuds fantômes (FR40), et c'est ce qui a sauvé les graphes en 8.3.
 *
 * <p>Elle se paie au moment de concevoir le format, pas après : un élément inconnu
 * est mis de côté tel quel et ré-émis à l'identique, à sa place dans l'ordre de
 * dessin. Sans cela, ouvrir un monde avec une version antérieure du mod effacerait
 * silencieusement la moitié d'un menu.
 */
public final class ScreenNbt {

    private ScreenNbt() {
    }

    // ------------------------------------------------------------------ écriture

    public static ListTag encode(Blueprint bp) {
        ListTag screens = new ListTag();
        for (Screen screen : bp.screens().values()) {
            screens.add(encodeScreen(screen));
        }
        for (Tag preserved : bp.preservedScreens()) {
            screens.add(preserved.copy());
        }
        return screens;
    }

    /**
     * Un écran seul, pour le fil (story 10.3). Le <b>même</b> encodage que la
     * sauvegarde : un second format divergerait, et un écran s'ouvrirait en jeu
     * autrement qu'il ne se relit du monde.
     */
    public static CompoundTag encodeOne(Screen screen) {
        return encodeScreen(screen);
    }

    /** L'inverse d'{@link #encodeOne} ; {@code null} si le tag est illisible. */
    public static @Nullable Screen decodeOne(CompoundTag tag) {
        return decodeScreen(tag);
    }

    private static CompoundTag encodeScreen(Screen screen) {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", screen.name());
        tag.putBoolean("hud", screen.hud());
        ListTag elements = new ListTag();
        for (ScreenElement element : screen.elements().values()) {
            elements.add(encodeElement(element));
        }
        tag.put("elements", elements);
        if (!screen.styles().isEmpty()) {
            CompoundTag styles = new CompoundTag();
            screen.styles().forEach((styleName, style) -> styles.put(styleName, encodeStyle(style)));
            tag.put("styles", styles);
        }
        return tag;
    }

    private static CompoundTag encodeElement(ScreenElement element) {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", element.name());
        tag.putString("kind", element.kind().name().toLowerCase(Locale.ROOT));
        if (element.parent() != null) {
            tag.putString("parent", element.parent());
        }
        tag.putString("anchor", element.anchor().name().toLowerCase(Locale.ROOT));
        tag.putDouble("x", element.x());
        tag.putDouble("y", element.y());
        tag.put("w", encodeExtent(element.width()));
        tag.put("h", encodeExtent(element.height()));
        if (!element.text().isEmpty()) {
            tag.putString("text", element.text().value());
            tag.putBoolean("translate", element.text().translate());
        }
        if (element.hasTooltip()) {
            tag.putString("tip", element.tooltip().value());
            tag.putBoolean("tipTranslate", element.tooltip().translate());
        }
        if (element.texture() != null) {
            tag.putString("texture", element.texture().toString());
        }
        tag.put("style", encodeStyle(element.style()));
        if (element.followsNamedStyle()) {
            tag.putString("styleName", element.styleName());
        }
        // `arranges() OU scroll()` : un panneau qui ne range pas ses enfants mais qui
        // DÉFILE a bien une disposition à écrire. La condition d'origine ne connaissait
        // que le rangement, et un panneau défilant en absolu serait revenu figé après un
        // aller-retour — sans erreur, sans avertissement, et sans que rien ne rappelle
        // qu'on avait coché la case.
        if (element.layout().arranges() || element.layout().scroll()) {
            tag.put("layout", encodeLayout(element.layout()));
        }
        // Écrite seulement quand elle existe : un écran sans liaison pèse exactement ce
        // qu'il pesait, et un mod antérieur le relit sans rien voir de nouveau.
        if (element.isBound()) {
            tag.put("bind", encodeBinding(element.binding()));
        }
        // Écrits seulement quand ils s'écartent du défaut : un écran des cinq types
        // d'origine pèse exactement ce qu'il pesait.
        if (!element.options().equals(fr.blueprint.core.graph.screen.ElementOptions.NONE)) {
            tag.put("opts", encodeOptions(element.options()));
        }
        tag.putBoolean("visible", element.visible());
        tag.putBoolean("enabled", element.enabled());
        return tag;
    }

    private static CompoundTag encodeOptions(
            fr.blueprint.core.graph.screen.ElementOptions o) {
        CompoundTag tag = new CompoundTag();
        tag.putString("placeholder", o.placeholder());
        tag.putInt("maxLength", o.maxLength());
        tag.putString("filter", o.filter().name().toLowerCase(Locale.ROOT));
        tag.putDouble("min", o.min());
        tag.putDouble("max", o.max());
        tag.putDouble("step", o.step());
        tag.putDouble("rowHeight", o.rowHeight());
        if (o.entity() != null) {
            tag.putString("entity", o.entity().toString());
        }
        return tag;
    }

    /**
     * Les réglages de type (10.8). Absents — tout écran d'avant — valent {@code NONE} :
     * c'est ce qui fait qu'un fichier enregistré ne change pas de sens en changeant de
     * version du mod.
     */
    private static fr.blueprint.core.graph.screen.ElementOptions decodeOptions(CompoundTag tag) {
        if (tag.isEmpty()) {
            return fr.blueprint.core.graph.screen.ElementOptions.NONE;
        }
        var none = fr.blueprint.core.graph.screen.ElementOptions.NONE;
        String entity = tag.getStringOr("entity", "");
        return new fr.blueprint.core.graph.screen.ElementOptions(
                tag.getStringOr("placeholder", ""),
                tag.getIntOr("maxLength", none.maxLength()),
                enumOr(fr.blueprint.core.graph.screen.ElementOptions.InputFilter.class,
                        tag.getStringOr("filter", ""), none.filter()),
                tag.getDoubleOr("min", none.min()),
                tag.getDoubleOr("max", none.max()),
                tag.getDoubleOr("step", none.step()),
                tag.getDoubleOr("rowHeight", none.rowHeight()),
                entity.isEmpty() ? null : Identifier.tryParse(entity));
    }

    private static CompoundTag encodeBinding(fr.blueprint.core.graph.screen.ElementBinding b) {
        CompoundTag tag = new CompoundTag();
        tag.putString("var", b.variable());
        tag.putString("target", b.target().name().toLowerCase(java.util.Locale.ROOT));
        tag.putString("format", b.format());
        tag.putDouble("min", b.min());
        tag.putDouble("max", b.max());
        tag.putInt("decimals", b.decimals());
        return tag;
    }

    /**
     * La liaison (10.7). Absente — tout écran d'avant — vaut « aucune liaison » : c'est
     * ce qui fait qu'un fichier enregistré ne change pas de sens en changeant de version.
     */
    private static fr.blueprint.core.graph.screen.ElementBinding decodeBinding(CompoundTag tag) {
        if (tag.isEmpty()) {
            return fr.blueprint.core.graph.screen.ElementBinding.NONE;
        }
        return new fr.blueprint.core.graph.screen.ElementBinding(
                tag.getStringOr("var", ""),
                enumOr(fr.blueprint.core.graph.screen.ElementBinding.Target.class,
                        tag.getStringOr("target", ""),
                        fr.blueprint.core.graph.screen.ElementBinding.Target.TEXT),
                tag.getStringOr("format", fr.blueprint.core.graph.screen.ElementBinding.PLACEHOLDER),
                tag.getDoubleOr("min", 0), tag.getDoubleOr("max", 1),
                tag.getIntOr("decimals", 0));
    }

    private static CompoundTag encodeLayout(LayoutSpec layout) {
        CompoundTag tag = new CompoundTag();
        tag.putString("mode", layout.mode().name().toLowerCase(Locale.ROOT));
        tag.putDouble("gap", layout.gap());
        tag.putDouble("crossGap", layout.crossGap());
        tag.putInt("columns", layout.columns());
        tag.putString("main", layout.main().name().toLowerCase(Locale.ROOT));
        tag.putString("cross", layout.cross().name().toLowerCase(Locale.ROOT));
        // Écrit seulement quand il est vrai : un conteneur qui ne défile pas pèse
        // exactement ce qu'il pesait, et une version antérieure le relit sans rien voir.
        if (layout.scroll()) {
            tag.putBoolean("scroll", true);
        }
        return tag;
    }

    private static CompoundTag encodeExtent(Extent extent) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("v", extent.value());
        tag.putString("mode", extent.mode().name().toLowerCase(Locale.ROOT));
        // « rel » reste écrit : une version antérieure du mod relit alors les tailles
        // fixes et les pourcentages correctement, au lieu de tout croire fixe.
        tag.putBoolean("rel", extent.relative());
        tag.putDouble("min", extent.min());
        tag.putDouble("max", extent.max());
        return tag;
    }

    private static CompoundTag encodeStyle(ElementStyle style) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("bg", style.background());
        tag.putInt("border", style.border());
        tag.putInt("borderWidth", style.borderWidth());
        tag.putInt("text", style.textColor());
        tag.putInt("hover", style.hoverBackground());
        tag.putInt("pressed", style.pressedBackground());
        tag.putInt("disabled", style.disabledBackground());
        tag.putInt("padding", style.padding());
        tag.putString("align", style.align().name().toLowerCase(Locale.ROOT));
        // Écrit seulement quand il est vrai : un style qui ne renvoie pas à la ligne pèse
        // exactement ce qu'il pesait, et une version antérieure le relit sans rien voir.
        if (style.wrap()) {
            tag.putBoolean("wrap", true);
        }
        return tag;
    }

    // ------------------------------------------------------------------ lecture

    /**
     * Relit les écrans. Ce qui ne se comprend pas est <b>mis de côté</b> dans
     * {@code preserved} et ré-émis tel quel à la prochaine écriture.
     */
    public static void decode(Blueprint bp, ListTag screens, ListTag preserved) {
        for (Tag tag : screens) {
            if (!(tag instanceof CompoundTag screenTag)) {
                continue;
            }
            Screen screen = decodeScreen(screenTag);
            if (screen == null) {
                preserved.add(screenTag.copy());
            } else {
                bp.putScreen(screen);
            }
        }
    }

    private static @Nullable Screen decodeScreen(CompoundTag tag) {
        String name = tag.getStringOr("name", "");
        if (name.isBlank()) {
            return null; // un écran sans nom n'est pas adressable : préservé brut
        }
        List<ScreenElement> elements = new ArrayList<>();
        for (Tag child : tag.getListOrEmpty("elements")) {
            if (!(child instanceof CompoundTag elementTag)) {
                return null;
            }
            ScreenElement element = decodeElement(elementTag);
            if (element == null) {
                // UN SEUL élément illisible et l'écran ENTIER est préservé brut.
                // Le charger amputé serait pire que ne pas le charger : l'auteur
                // enregistrerait par-dessus sans voir ce qui manque, et la perte
                // deviendrait définitive. Le mod revenu, l'écran est intact.
                return null;
            }
            elements.add(element);
        }
        java.util.Map<String, ElementStyle> styles = new java.util.LinkedHashMap<>();
        CompoundTag styleTag = tag.getCompoundOrEmpty("styles");
        for (String styleName : styleTag.keySet()) {
            styles.put(styleName, decodeStyle(styleTag.getCompoundOrEmpty(styleName)));
        }
        return new Screen(name, tag.getBooleanOr("hud", false), elements, styles);
    }

    private static @Nullable ScreenElement decodeElement(CompoundTag tag) {
        String name = tag.getStringOr("name", "");
        ElementKind kind = kindOf(tag.getStringOr("kind", ""));
        if (name.isBlank() || kind == null) {
            return null;
        }
        String parent = tag.getStringOr("parent", "");
        ScreenText text = tag.getStringOr("text", "").isEmpty()
                ? ScreenText.EMPTY
                : new ScreenText(tag.getStringOr("text", ""),
                        tag.getBooleanOr("translate", false));
        ScreenText tooltip = tag.getStringOr("tip", "").isEmpty()
                ? ScreenText.EMPTY
                : new ScreenText(tag.getStringOr("tip", ""),
                        tag.getBooleanOr("tipTranslate", false));
        Identifier texture = tag.getStringOr("texture", "").isEmpty()
                ? null : Identifier.tryParse(tag.getStringOr("texture", ""));
        return new ScreenElement(name, kind,
                parent.isEmpty() ? null : parent,
                anchorOf(tag.getStringOr("anchor", "")),
                tag.getDoubleOr("x", 0), tag.getDoubleOr("y", 0),
                decodeExtent(tag.getCompoundOrEmpty("w")),
                decodeExtent(tag.getCompoundOrEmpty("h")),
                text, tooltip, texture,
                decodeStyle(tag.getCompoundOrEmpty("style")),
                tag.getStringOr("styleName", ""),
                decodeLayout(tag.getCompoundOrEmpty("layout")),
                decodeBinding(tag.getCompoundOrEmpty("bind")),
                decodeOptions(tag.getCompoundOrEmpty("opts")),
                tag.getBooleanOr("visible", true),
                tag.getBooleanOr("enabled", true));
    }

    /**
     * La disposition d'un conteneur (10.10). Absente — tous les écrans d'avant — vaut
     * {@code ABSOLUTE}, c'est-à-dire le comportement historique : chaque enfant se place
     * lui-même. C'est ce qui fait qu'aucun fichier enregistré ne change de sens.
     */
    private static LayoutSpec decodeLayout(CompoundTag tag) {
        if (tag.isEmpty()) {
            return LayoutSpec.ABSOLUTE;
        }
        return new LayoutSpec(
                enumOr(LayoutSpec.Mode.class, tag.getStringOr("mode", ""),
                        LayoutSpec.Mode.ABSOLUTE),
                tag.getDoubleOr("gap", 0), tag.getDoubleOr("crossGap", 0),
                tag.getIntOr("columns", 1),
                enumOr(LayoutSpec.Distribute.class, tag.getStringOr("main", ""),
                        LayoutSpec.Distribute.START),
                enumOr(LayoutSpec.Cross.class, tag.getStringOr("cross", ""),
                        LayoutSpec.Cross.START),
                tag.getBooleanOr("scroll", false));
    }

    /** Une valeur d'énumération inconnue retombe sur le défaut plutôt que de lever. */
    private static <E extends Enum<E>> E enumOr(Class<E> type, String raw, E fallback) {
        for (E value : type.getEnumConstants()) {
            if (value.name().equalsIgnoreCase(raw)) {
                return value;
            }
        }
        return fallback;
    }

    /**
     * Une longueur illisible retombe sur une taille minimale plutôt que de lever : un
     * écran à moitié lu vaut mieux qu'un monde qui refuse de charger.
     */
    /** Visible pour que la rétrocompatibilité des tailles soit testable directement. */
    static Extent decodeExtent(CompoundTag tag) {
        double min = Math.max(0, tag.getDoubleOr("min", 0));
        double max = Math.max(0, tag.getDoubleOr("max", 0));
        if (max > 0 && max < min) {
            max = min; // bornes croisées à l'écriture : on répare plutôt que de lever
        }
        double value = tag.getDoubleOr("v", ScreenElement.MIN_SIZE);
        if (!Double.isFinite(value)) {
            value = ScreenElement.MIN_SIZE;
        }
        // Le mode a remplacé le drapeau « rel » (10.10). Un fichier d'avant n'a pas de
        // « mode » : on le déduit du drapeau, et la longueur garde exactement le sens
        // qu'elle avait. C'est ce qui fait qu'aucun écran enregistré ne bouge.
        Extent.Mode mode = tag.getStringOr("mode", "").isEmpty()
                ? (tag.getBooleanOr("rel", false) ? Extent.Mode.PERCENT : Extent.Mode.FIXED)
                : enumOr(Extent.Mode.class, tag.getStringOr("mode", ""), Extent.Mode.FIXED);
        return new Extent(mode, value, min, max);
    }

    private static ElementStyle decodeStyle(CompoundTag tag) {
        if (tag.isEmpty()) {
            return ElementStyle.DEFAULT;
        }
        return new ElementStyle(
                tag.getIntOr("bg", ElementStyle.DEFAULT.background()),
                tag.getIntOr("border", ElementStyle.DEFAULT.border()),
                Math.max(0, tag.getIntOr("borderWidth", ElementStyle.DEFAULT.borderWidth())),
                tag.getIntOr("text", ElementStyle.DEFAULT.textColor()),
                tag.getIntOr("hover", 0), tag.getIntOr("pressed", 0),
                tag.getIntOr("disabled", 0),
                Math.max(0, tag.getIntOr("padding", ElementStyle.DEFAULT.padding())),
                alignOf(tag.getStringOr("align", "")),
                tag.getBooleanOr("wrap", false));
    }

    private static @Nullable ElementKind kindOf(String raw) {
        for (ElementKind kind : ElementKind.values()) {
            if (kind.name().equalsIgnoreCase(raw)) {
                return kind;
            }
        }
        return null;
    }

    private static Anchor anchorOf(String raw) {
        for (Anchor anchor : Anchor.values()) {
            if (anchor.name().equalsIgnoreCase(raw)) {
                return anchor;
            }
        }
        return Anchor.TOP_LEFT;
    }

    private static ElementStyle.TextAlign alignOf(String raw) {
        for (ElementStyle.TextAlign align : ElementStyle.TextAlign.values()) {
            if (align.name().equalsIgnoreCase(raw)) {
                return align;
            }
        }
        return ElementStyle.TextAlign.LEFT;
    }
}
