package fr.blueprint.core.graph.screen;

import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.GraphNbt;
import fr.blueprint.core.graph.ScreenOps;
import fr.blueprint.core.registry.PluginLoader;
import fr.blueprint.core.script.ScriptGenerator;
import fr.blueprint.core.script.ScriptParser;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ce qu'un menu complet demandait et n'avait pas : une infobulle, et du texte qui revient
 * à la ligne (story 10.12).
 *
 * <p>Sans infobulle, un menu doit tout dire dans ses libellés, qui n'en ont pas la place ;
 * sans retour à la ligne, un paragraphe se découpe à la main en autant d'étiquettes
 * empilées, qu'il faut repositionner à chaque retouche — et qu'une traduction plus longue
 * que l'original casse sans que l'auteur le voie.
 */
class ScreenTooltipAndWrapTest {

    private static final PluginLoader.LoadedRegistries LOADED = PluginLoader.load(List.of(), true);

    private static ScreenElement bouton() {
        return ScreenElement.of("bouton", ElementKind.BUTTON, 10, 10, 60, 20);
    }

    private static Blueprint with(Screen... screens) {
        Blueprint bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "tip"));
        for (Screen screen : screens) {
            new ScreenOps.AddScreen(screen).apply(bp, LOADED.nodes());
        }
        return bp;
    }

    // ------------------------------------------------------------------ infobulle

    @Test
    void unElementNeufNaPasDInfobulle() {
        assertFalse(bouton().hasTooltip());
        assertEquals(ScreenText.EMPTY, bouton().tooltip());
    }

    @Test
    void uneInfobullePeutEtreUneCleDeTraduction() {
        ScreenElement traduit = bouton().withTooltip(ScreenText.key("menu.acheter.aide"));

        assertTrue(traduit.hasTooltip());
        assertTrue(traduit.tooltip().translate(),
                "un menu qu'on traduit se traduit en entier, infobulles comprises");
    }

    /**
     * <b>Le test qui compte.</b> Chaque méthode de copie préserve l'infobulle.
     *
     * <p>Un élément a dix-huit composants et treize méthodes {@code withX}, chacune
     * réénumérant les dix-sept autres à la main. C'est exactement ainsi que la 10.10 a
     * perdu la table de styles d'un écran : une copie oubliait un champ, rien ne
     * compilait de travers, et le défaut ne se voyait qu'après avoir renommé un élément.
     * Ce test exerce les treize.
     */
    @Test
    void toutesLesCopiesEmportentLInfobulle() {
        ScreenElement source = bouton().withTooltip(ScreenText.literal("Coûte 12 pièces"));

        List<ScreenElement> copies = List.of(
                source.withParent("cadre"),
                source.withAnchor(Anchor.BOTTOM_RIGHT),
                source.movedTo(1, 2),
                source.resized(Extent.of(30), Extent.of(12)),
                source.renamed("autre"),
                source.styled(ElementStyle.DEFAULT.withWrap(true)),
                source.withText(ScreenText.literal("Acheter")),
                source.withTexture(Identifier.withDefaultNamespace("textures/gui/x.png")),
                source.withStyleName("bouton_actif"),
                source.withBinding(new ElementBinding("or",
                        ElementBinding.Target.TEXT, "%s", 0, 0, 0)),
                source.withOptions(ElementOptions.NONE),
                source.withLayout(LayoutSpec.ABSOLUTE),
                source.withVisible(false),
                source.withEnabled(false));

        for (ScreenElement copy : copies) {
            assertEquals("Coûte 12 pièces", copy.tooltip().value(),
                    () -> "une copie a perdu l'infobulle : " + copy);
        }
    }

    @Test
    void lInfobulleTraverseLaSauvegarde() {
        Blueprint bp = with(new Screen("menu", false, List.of(
                bouton().withTooltip(ScreenText.key("menu.aide")))));

        Blueprint relu = GraphNbt.decode(GraphNbt.encode(bp),
                id -> fr.blueprint.api.pin.PinTypes.builtin().stream()
                        .filter(type -> type.id().equals(id)).findFirst().orElse(null));

        ScreenElement copy = relu.screen("menu").element("bouton");
        assertEquals("menu.aide", copy.tooltip().value());
        assertTrue(copy.tooltip().translate(), "la nature de clé doit survivre aussi");
    }

    // ---------------------------------------------------------- retour à la ligne

    @Test
    void leRetourALaLigneEstFauxParDefaut() {
        assertFalse(ElementStyle.DEFAULT.wrap(),
                "un libellé de bouton tient sur une ligne, et le tronquer avertit "
                        + "l'auteur que son texte est trop long");
    }

    @Test
    void leRetourALaLigneSuitLeStyleNomme() {
        ElementStyle paragraphe = ElementStyle.DEFAULT.withWrap(true);
        Screen screen = new Screen("menu", false, List.of(
                        ScreenElement.of("a", ElementKind.LABEL, 0, 0, 100, 40)
                                .withStyleName("paragraphe"),
                        ScreenElement.of("b", ElementKind.LABEL, 0, 50, 100, 40)
                                .withStyleName("paragraphe")))
                .withStyle("paragraphe", paragraphe);

        // Décrit UNE fois, il vaut pour tous ceux qui le suivent : c'est ce qui distingue
        // une propriété d'apparence d'un réglage recopié élément par élément.
        assertTrue(screen.styleOf(screen.element("a")).wrap());
        assertTrue(screen.styleOf(screen.element("b")).wrap());
    }

    // ----------------------------------------------------- aller-retour BScript

    /**
     * L'aller-retour compare le <b>contenu</b> et non des comptes : le projet a déjà perdu
     * un filtre d'événement à l'export sans que rien ne le voie, parce qu'un test se
     * contentait de compter.
     */
    @Test
    void infobulleEtRetourALaLigneReviennentIdentiquesParLeTexte() {
        Blueprint bp = with(new Screen("menu", false, List.of(
                        ScreenElement.of("titre", ElementKind.LABEL, 0, 0, 200, 60)
                                .styled(ElementStyle.DEFAULT.withWrap(true))
                                .withText(ScreenText.literal("Une longue description")),
                        bouton().withTooltip(ScreenText.literal("Coûte 12 pièces")),
                        ScreenElement.of("aide", ElementKind.LABEL, 0, 80, 60, 10)
                                .withTooltip(ScreenText.key("menu.aide"))))
                .withStyle("paragraphe", ElementStyle.DEFAULT.withWrap(true)));

        var generated = ScriptGenerator.generate(bp, LOADED.nodes());
        var parsed = ScriptParser.parse(generated.text(), LOADED);
        assertTrue(parsed.success(), () -> "parse échoué : " + parsed.error());
        Screen screen = parsed.blueprint().screen("menu");

        assertTrue(screen.element("titre").style().wrap(),
                "le retour à la ligne a disparu à l'aller-retour");
        assertEquals("Coûte 12 pièces", screen.element("bouton").tooltip().value());
        assertFalse(screen.element("bouton").tooltip().translate());
        assertEquals("menu.aide", screen.element("aide").tooltip().value());
        assertTrue(screen.element("aide").tooltip().translate(),
                "une infobulle traduite ne doit pas revenir littérale");
        assertTrue(screen.styles().get("paragraphe").wrap(),
                "un style NOMMÉ porte aussi le retour à la ligne");
    }

    /**
     * Un `.bp` écrit AVANT l'existence du retour à la ligne se relit sans changement.
     *
     * <p>Le champ est un dixième élément <b>facultatif</b> du bloc de style pour cette
     * raison : l'imposer aurait rendu illisible tout fichier déjà exporté, alors que rien
     * ne l'exige.
     */
    @Test
    void unStyleSansLeDixiemeChampSeRelitEncore() {
        String script = """
                blueprint test:ancien {
                  screen "menu" {
                    label "a" @at(top_left, 0, 0) @size(100, 20) @text("Bonjour") \
                @style("#C0141519", "#FF6B7280", 1, "#FFE6E6E6", "#00000000", \
                "#00000000", "#00000000", 2, left)
                  }
                }
                """;
        var parsed = ScriptParser.parse(script, LOADED);
        assertTrue(parsed.success(), () -> "parse échoué : " + parsed.error());

        assertFalse(parsed.blueprint().screen("menu").element("a").style().wrap(),
                "sans le champ, pas de retour à la ligne — et surtout, pas d'erreur");
    }
}
