package fr.blueprint.core.graph;

import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.graph.screen.Anchor;
import fr.blueprint.core.graph.screen.ElementBinding;
import fr.blueprint.core.graph.screen.ElementKind;
import fr.blueprint.core.graph.screen.ElementOptions;
import fr.blueprint.core.graph.screen.ElementStyle;
import fr.blueprint.core.graph.screen.Extent;
import fr.blueprint.core.graph.screen.LayoutSpec;
import fr.blueprint.core.graph.screen.Screen;
import fr.blueprint.core.graph.screen.ScreenElement;
import fr.blueprint.core.graph.screen.ScreenText;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sérialisation des écrans (story 10.1, AC3). Deux exigences : le round-trip est
 * exact, et ce qui ne se comprend pas est <b>préservé</b> plutôt que perdu.
 */
class ScreenNbtTest {

    private static final Identifier ID = Identifier.fromNamespaceAndPath("test", "ecrans");

    /** Les types de pins standard : aucun écran n'en dépend, mais decode l'exige. */
    private static final java.util.function.Function<Identifier, fr.blueprint.api.pin.PinType> TYPES =
            id -> PinTypes.builtin().stream()
                    .filter(type -> type.id().equals(id)).findFirst().orElse(null);

    private static Blueprint withScreens(Screen... screens) {
        Blueprint bp = new Blueprint(ID);
        for (Screen screen : screens) {
            bp.putScreen(screen);
        }
        return bp;
    }

    private static Blueprint roundTrip(Blueprint bp) {
        CompoundTag tag = GraphNbt.encode(bp);
        return GraphNbt.decode(tag, TYPES);
    }

    // ------------------------------------------------------------- round-trip

    @Test
    void unEcranRicheRevientIdentique() {
        ScreenElement panel = new ScreenElement("cadre", ElementKind.PANEL, null,
                Anchor.CENTER, 10, 20,
                Extent.percent(0.8, 100, 400), Extent.of(180),
                ScreenText.key("menu.titre"),
                Identifier.withDefaultNamespace("textures/gui/fond.png"),
                new ElementStyle(0xFF102030, 0xFF405060, 2, 0xFFFFFFFF,
                        0xFF203040, 0xFF001020, 0x40101010, 4,
                        ElementStyle.TextAlign.CENTER), "", LayoutSpec.ABSOLUTE, ElementBinding.NONE, ElementOptions.NONE,
                true, false);
        ScreenElement child = ScreenElement.of("ok", ElementKind.BUTTON, 5, 5, 60, 20)
                .withParent("cadre")
                .withText(ScreenText.literal("Valider"));

        Blueprint before = withScreens(new Screen("menu", false, List.of(panel, child)));
        Blueprint after = roundTrip(before);

        assertEquals(before.screen("menu"), after.screen("menu"));
        assertEquals(panel, after.screen("menu").element("cadre"));
        assertEquals(child, after.screen("menu").element("ok"));
    }

    /**
     * Dispositions, {@code fill}/{@code hug} et table de styles (story 10.10). Tout est
     * additif : ce qui manque à la relecture reprend sa valeur d'avant.
     */
    @Test
    void dispositionsEtStylesNommesSurviventAuRoundTrip() {
        ElementStyle style = new ElementStyle(0xFF102030, 0xFF405060, 2, 0xFFFFFFFF,
                0xFF203040, 0xFF001020, 0x40101010, 4, ElementStyle.TextAlign.CENTER);
        ScreenElement colonne = ScreenElement.of("colonne", ElementKind.PANEL, 0, 0, 200, 160)
                .withLayout(LayoutSpec.column(6)
                        .withMain(LayoutSpec.Distribute.SPACE_BETWEEN)
                        .withCross(LayoutSpec.Cross.STRETCH));
        ScreenElement grille = ScreenElement.of("grille", ElementKind.PANEL, 0, 0, 200, 80)
                .withParent("colonne")
                .withLayout(LayoutSpec.grid(3, 4, 2))
                .resized(Extent.fill(), Extent.hug());
        ScreenElement bouton = ScreenElement.of("acheter", ElementKind.BUTTON, 0, 0, 60, 20)
                .withParent("colonne")
                .resized(new Extent(Extent.Mode.FILL, 2.5, 20, 90), Extent.of(20))
                .withStyleName("bouton");

        Screen before = new Screen("menu", false, List.of(colonne, grille, bouton),
                java.util.Map.of("bouton", style));
        Screen after = roundTrip(withScreens(before)).screen("menu");

        assertEquals(before, after);
        assertEquals(style, after.styleOf(after.element("acheter")));
        assertEquals(LayoutSpec.Cross.STRETCH, after.element("colonne").layout().cross());
        assertEquals(Extent.Mode.HUG, after.element("grille").height().mode());
        assertEquals(2.5, after.element("acheter").width().value(), 1e-9);
    }

    /**
     * Un écran écrit par une version antérieure n'avait pas de champ {@code mode} : le
     * booléen {@code rel} décidait seul du fixe ou du pourcentage. Le relire comme un
     * {@code FIXED} rendrait tout menu déjà enregistré minuscule — c'est le genre de
     * régression qu'un joueur découvre en ouvrant un menu qui marchait hier.
     */
    @Test
    void unEcranDAvantLesModesSeRelitCorrectement() {
        CompoundTag ancien = new CompoundTag();
        ancien.putDouble("v", 0.75);
        ancien.putBoolean("rel", true);
        ancien.putDouble("min", 100);
        ancien.putDouble("max", 400);

        Extent relu = ScreenNbt.decodeExtent(ancien);
        assertEquals(Extent.Mode.PERCENT, relu.mode(), "rel=true sans mode : un pourcentage");
        assertEquals(0.75, relu.value(), 1e-9);
        assertEquals(100, relu.min(), 1e-9);

        CompoundTag fixe = new CompoundTag();
        fixe.putDouble("v", 80);
        assertEquals(Extent.Mode.FIXED, ScreenNbt.decodeExtent(fixe).mode());
    }

    /** Une liaison survit à la sauvegarde ; son absence reste l'absence de liaison. */
    @Test
    void lesLiaisonsSurviventAuRoundTrip() {
        ScreenElement or = ScreenElement.of("or", ElementKind.LABEL, 0, 0, 80, 12)
                .withBinding(ElementBinding.text("argent", "Or : %s").withDecimals(2));
        ScreenElement muet = ScreenElement.of("muet", ElementKind.LABEL, 0, 0, 80, 12);

        Screen after = roundTrip(withScreens(
                new Screen("menu", false, List.of(or, muet)))).screen("menu");

        assertEquals(or, after.element("or"));
        assertEquals("argent", after.element("or").binding().variable());
        assertEquals(2, after.element("or").binding().decimals());
        assertFalse(after.element("muet").isBound(), "pas de liaison inventée");
    }

    @Test
    void lOrdreDeDessinSurvitAuRoundTrip() {
        Blueprint before = withScreens(new Screen("menu", false, List.of(
                ScreenElement.of("a", ElementKind.LABEL, 0, 0, 10, 10),
                ScreenElement.of("b", ElementKind.LABEL, 0, 0, 10, 10),
                ScreenElement.of("c", ElementKind.LABEL, 0, 0, 10, 10))));

        assertEquals(List.of("a", "b", "c"),
                List.copyOf(roundTrip(before).screen("menu").elements().keySet()));
    }

    @Test
    void leDrapeauHudSurvit() {
        Blueprint before = withScreens(new Screen("barre", true, List.of()));
        assertTrue(roundTrip(before).screen("barre").hud());
    }

    @Test
    void plusieursEcransSurvivent() {
        Blueprint after = roundTrip(withScreens(
                Screen.empty("a"), Screen.empty("b"), Screen.empty("c")));
        assertEquals(3, after.screens().size());
        assertEquals(List.of("a", "b", "c"), List.copyOf(after.screens().keySet()));
    }

    @Test
    void unBlueprintSansEcranNeGagneRien() {
        Blueprint after = roundTrip(new Blueprint(ID));
        assertTrue(after.screens().isEmpty());
        assertFalse(after.hasPreservedScreens());
    }

    // ---------------------------------------------------- préservation intégrale

    /**
     * <b>Le test qui compte.</b> Un type d'élément inconnu — mod retiré, monde venu
     * d'une version plus récente — ne doit RIEN perdre. Charger l'écran amputé serait
     * pire : l'auteur enregistrerait par-dessus sans voir ce qui manque, et la perte
     * deviendrait définitive.
     */
    @Test
    void unTypeDElementInconnuPreserveLEcranEntier() {
        CompoundTag root = GraphNbt.encode(withScreens(new Screen("menu", false, List.of(
                ScreenElement.of("connu", ElementKind.LABEL, 0, 0, 40, 10)))));

        // Un élément d'un type que cette version ne connaît pas.
        CompoundTag exotic = new CompoundTag();
        exotic.putString("name", "carte_3d");
        exotic.putString("kind", "hologramme");
        exotic.putDouble("x", 5);
        exotic.putDouble("y", 5);
        ((CompoundTag) root.getListOrEmpty("screens").getFirst())
                .getListOrEmpty("elements").add(exotic);

        Blueprint reloaded = GraphNbt.decode(root, TYPES);
        assertNull(reloaded.screen("menu"),
                "l'écran n'est PAS chargé amputé de son élément inconnu");
        assertTrue(reloaded.hasPreservedScreens(), "il est mis de côté en entier");

        // Et il ressort tel quel : le mod revenu, l'écran est intact.
        CompoundTag again = GraphNbt.encode(reloaded);
        ListTag screens = again.getListOrEmpty("screens");
        assertEquals(1, screens.size());
        assertEquals(2, ((CompoundTag) screens.getFirst()).getListOrEmpty("elements").size(),
                "les DEUX éléments sont ressortis, le connu comme l'inconnu");
    }

    @Test
    void unEcranSansNomEstPreserveBrut() {
        CompoundTag root = GraphNbt.encode(new Blueprint(ID));
        CompoundTag anonymous = new CompoundTag();
        anonymous.put("elements", new ListTag());
        root.getListOrEmpty("screens").add(anonymous);

        Blueprint reloaded = GraphNbt.decode(root, TYPES);
        assertTrue(reloaded.screens().isEmpty());
        assertTrue(reloaded.hasPreservedScreens());
    }

    /** Ce qui est préservé traverse aussi la copie et compte dans la comparaison. */
    @Test
    void lesEcransPreservesSuiventLaCopie() {
        CompoundTag root = GraphNbt.encode(new Blueprint(ID));
        CompoundTag broken = new CompoundTag();
        root.getListOrEmpty("screens").add(broken);

        Blueprint reloaded = GraphNbt.decode(root, TYPES);
        Blueprint copy = reloaded.copy();
        assertTrue(copy.hasPreservedScreens());
        assertTrue(reloaded.contentEquals(copy));
    }

    // -------------------------------------------------------------- robustesse

    /**
     * Un champ abîmé ne doit pas empêcher le monde de charger : on répare vers une
     * valeur sûre. Un écran à moitié lu vaut mieux qu'une sauvegarde qu'on refuse.
     */
    @Test
    void unChampAbimeEstRepareplutotQueLeve() {
        CompoundTag root = GraphNbt.encode(withScreens(new Screen("menu", false, List.of(
                ScreenElement.of("x", ElementKind.LABEL, 0, 0, 40, 10)))));
        CompoundTag element = (CompoundTag) ((CompoundTag) root.getListOrEmpty("screens")
                .getFirst()).getListOrEmpty("elements").getFirst();

        // Bornes croisées, valeur non finie, ancre inconnue : tout ce qu'un fichier
        // corrompu ou édité à la main peut produire.
        CompoundTag width = element.getCompoundOrEmpty("w").copy();
        width.putDouble("min", 100);
        width.putDouble("max", 10);
        width.putDouble("v", Double.NaN);
        element.put("w", width);
        element.putString("anchor", "nord_ouest");

        Blueprint reloaded = GraphNbt.decode(root, TYPES);
        ScreenElement back = reloaded.screen("menu").element("x");
        assertNotNull(back, "l'écran charge quand même");
        assertEquals(Anchor.TOP_LEFT, back.anchor(), "ancre inconnue → coin haut-gauche");
        assertTrue(Double.isFinite(back.width().resolve(320)), "et la largeur reste finie");
    }

    @Test
    void unStyleAbsentRetombeSurLeDefaut() {
        CompoundTag root = GraphNbt.encode(withScreens(new Screen("menu", false, List.of(
                ScreenElement.of("x", ElementKind.LABEL, 0, 0, 40, 10)))));
        ((CompoundTag) ((CompoundTag) root.getListOrEmpty("screens").getFirst())
                .getListOrEmpty("elements").getFirst()).put("style", new CompoundTag());

        Blueprint reloaded = GraphNbt.decode(root, TYPES);
        assertEquals(ElementStyle.DEFAULT, reloaded.screen("menu").element("x").style());
    }
}
