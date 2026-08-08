package fr.blueprint.core.graph.screen;

import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.Diagnostic;
import fr.blueprint.core.graph.DiagnosticCode;
import fr.blueprint.core.graph.GraphNbt;
import fr.blueprint.core.graph.GraphValidator;
import fr.blueprint.core.graph.ScreenOps;
import fr.blueprint.core.registry.PluginLoader;
import fr.blueprint.core.script.ScriptGenerator;
import fr.blueprint.core.script.ScriptParser;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La taille du texte d'un élément (story 10.17).
 *
 * <p>La police de Minecraft n'existe qu'à <b>une</b> taille : huit pixels de haut. On
 * l'agrandit par un facteur, d'où un facteur dans le style plutôt qu'une taille en points,
 * qui aurait fini arrondie au facteur le plus proche de toute façon.
 *
 * <p>Elle vit dans le style et non dans les options : c'est une propriété d'apparence, au
 * même titre que l'alignement, et un style nommé « titre » doit pouvoir la porter pour tous
 * les textes qui le suivent.
 */
class ElementTextScaleTest {

    private static final PluginLoader.LoadedRegistries LOADED = PluginLoader.load(List.of(), true);

    private static ScreenElement libelle() {
        return ScreenElement.of("titre", ElementKind.LABEL, 10, 10, 120, 30)
                .withText(ScreenText.literal("Boutique"));
    }

    private static Blueprint with(Screen... screens) {
        Blueprint bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "taille"));
        for (Screen screen : screens) {
            new ScreenOps.AddScreen(screen).apply(bp, LOADED.nodes());
        }
        return bp;
    }

    private static List<DiagnosticCode> codes(Blueprint bp) {
        return GraphValidator.validate(bp, LOADED.nodes()).diagnostics().stream()
                .map(Diagnostic::code).toList();
    }

    @Test
    void leFacteurVautUnParDefaut() {
        assertEquals(1, ElementStyle.DEFAULT.textScale(),
                "un élément qu'on vient de poser garde la taille de la police");
    }

    /**
     * <b>Une valeur absurde est ramenée, pas refusée.</b>
     *
     * <p>Elle arrive d'un fichier écrit à la main ou d'un pack tiers. Refuser l'écran
     * entier pour un nombre coûterait bien plus que de le border — et un écran qui ne
     * s'ouvre pas est autrement plus difficile à diagnostiquer qu'un texte trop grand.
     */
    @Test
    void uneValeurAbsurdeEstRamenee() {
        assertEquals(ElementStyle.MAX_SCALE, ElementStyle.DEFAULT.withTextScale(99).textScale());
        assertEquals(ElementStyle.MIN_SCALE, ElementStyle.DEFAULT.withTextScale(0.01).textScale());
        assertEquals(1, ElementStyle.DEFAULT.withTextScale(0).textScale(),
                "zéro rendrait le texte invisible sans rien dire");
        assertEquals(1, ElementStyle.DEFAULT.withTextScale(Double.NaN).textScale());
    }

    /**
     * <b>Chaque copie emporte le facteur.</b>
     *
     * <p>{@code ElementStyle} a onze composants et deux méthodes de copie qui réénumèrent
     * les autres à la main. C'est ainsi que la 10.10 a perdu la table de styles d'un
     * écran : une copie oubliait un champ, rien ne compilait de travers, et le défaut ne se
     * voyait qu'après coup.
     */
    @Test
    void chaqueCopieEmporteLeFacteur() {
        ElementStyle titre = ElementStyle.DEFAULT.withTextScale(2);

        assertEquals(2, titre.withWrap(true).textScale(),
                "withWrap réénumère les dix autres composants : il peut perdre celui-ci");
        assertEquals(true, titre.withWrap(true).wrap());
        assertEquals(2, titre.withTextScale(2).textScale());
    }

    /** L'ancienne forme du constructeur reste lisible, et vaut ×1. */
    @Test
    void lAncienneFormeVautUn() {
        ElementStyle ancien = new ElementStyle(0, 0, 1, 0xFFFFFFFF, 0, 0, 0, 2,
                ElementStyle.TextAlign.LEFT, false);

        assertEquals(1, ancien.textScale(),
                "un style écrit avant l'existence du facteur ne doit pas changer d'aspect");
    }

    // ------------------------------------------------------------------ persistance

    @Test
    void leFacteurTraverseLaSauvegarde() {
        Blueprint bp = with(new Screen("menu", false, List.of(
                libelle().styled(ElementStyle.DEFAULT.withTextScale(1.5)))));

        Blueprint relu = GraphNbt.decode(GraphNbt.encode(bp),
                id -> fr.blueprint.api.pin.PinTypes.builtin().stream()
                        .filter(type -> type.id().equals(id)).findFirst().orElse(null));

        assertEquals(1.5, relu.screen("menu").element("titre").style().textScale());
    }

    /**
     * <b>Le facteur traverse le texte, dans les deux sens.</b>
     *
     * <p>C'est la garantie centrale du produit : tout graphe s'écrit en BScript et se relit
     * identique. Un champ ajouté à l'écriture sans l'être à la lecture casse l'aller-retour
     * pour tous les écrans, pas seulement pour ceux qui s'en servent.
     */
    @Test
    void leFacteurTraverseLeTexte() {
        Blueprint bp = with(new Screen("menu", false, List.of(
                libelle().styled(ElementStyle.DEFAULT.withTextScale(2)))));

        String script = ScriptGenerator.generate(bp, LOADED.nodes()).text();
        var relu = ScriptParser.parse(script, LOADED);

        assertNull(relu.error(), () -> "relecture refusée : " + relu.error());
        assertEquals(2, relu.blueprint().screen("menu").element("titre").style().textScale());
    }

    /**
     * <b>Et il s'écrit sans forcer le retour à la ligne.</b>
     *
     * <p>Les deux champs facultatifs se suivent après l'alignement. S'ils se lisaient par
     * leur POSITION, écrire une échelle sans retour à la ligne obligerait à écrire « wrap »
     * quand même pour atteindre le champ suivant. Ils se reconnaissent donc à leur forme :
     * un mot pour l'un, un nombre pour l'autre.
     */
    @Test
    void leFacteurSEcritSansForcerLeRetourALaLigne() {
        Blueprint bp = with(new Screen("menu", false, List.of(
                libelle().styled(ElementStyle.DEFAULT.withTextScale(1.5)))));

        String script = ScriptGenerator.generate(bp, LOADED.nodes()).text();
        assertFalse(script.contains("wrap"),
                "rien n'oblige à écrire « wrap » pour écrire une échelle : " + script);

        var relu = ScriptParser.parse(script, LOADED);
        assertNull(relu.error(), () -> "relecture refusée : " + relu.error());
        ElementStyle style = relu.blueprint().screen("menu").element("titre").style();
        assertEquals(1.5, style.textScale());
        assertFalse(style.wrap());
    }

    /** Un style par défaut n'écrit aucun des deux : un `.bp` déjà exporté ne bouge pas. */
    @Test
    void unStyleOrdinaireNecritRien() {
        Blueprint bp = with(new Screen("menu", false, List.of(libelle())));

        String script = ScriptGenerator.generate(bp, LOADED.nodes()).text();

        assertFalse(script.contains("wrap"));
        assertNull(ScriptParser.parse(script, LOADED).error());
    }

    // ------------------------------------------------------------------ diagnostic

    /**
     * <b>Le texte qui ne tient plus se dit.</b>
     *
     * <p>Agrandir le texte n'agrandit pas l'élément — délibérément : la taille d'un élément
     * est ce que l'auteur a posé, et la faire bouger sous sa main déplacerait tout ce qui
     * l'entoure. Le validateur prévient donc, au lieu de laisser découvrir en jeu un titre
     * coupé en deux.
     */
    @Test
    void leTexteQuiNeTientPlusSeDit() {
        Blueprint sain = with(new Screen("menu", false, List.of(
                libelle().styled(ElementStyle.DEFAULT.withTextScale(2)))));
        assertFalse(codes(sain).contains(DiagnosticCode.ELEMENT_TEXT_TOO_TALL),
                "trente unités de haut accueillent sans peine deux fois neuf pixels");

        Blueprint serre = with(new Screen("menu", false, List.of(
                ScreenElement.of("titre", ElementKind.LABEL, 10, 10, 120, 12)
                        .withText(ScreenText.literal("Boutique"))
                        .styled(ElementStyle.DEFAULT.withTextScale(3)))));

        assertTrue(codes(serre).contains(DiagnosticCode.ELEMENT_TEXT_TOO_TALL),
                "vingt-sept pixels de texte dans douze unités : le texte sera coupé — "
                        + codes(serre));
    }

    /**
     * <b>C'est un avertissement, pas une erreur.</b>
     *
     * <p>Un texte coupé est laid ; il n'empêche pas le menu de fonctionner. Refuser de
     * l'exécuter pour cela serait disproportionné — et l'auteur qui teste un réglage
     * verrait son écran cesser de s'ouvrir.
     */
    @Test
    void cestUnAvertissementPasUneErreur() {
        Blueprint serre = with(new Screen("menu", false, List.of(
                ScreenElement.of("titre", ElementKind.LABEL, 10, 10, 120, 12)
                        .withText(ScreenText.literal("Boutique"))
                        .styled(ElementStyle.DEFAULT.withTextScale(3)))));

        assertTrue(GraphValidator.validate(serre, LOADED.nodes()).errors().isEmpty(),
                "l'écran doit rester exécutable");
    }

    /**
     * <b>C'est le texte qui décide, pas le type.</b>
     *
     * <p>Correction d'une croyance fausse : j'avais d'abord écarté l'image, la barre,
     * l'emplacement et l'aperçu d'entité, en les tenant pour muets. Le peintre appelle en
     * réalité {@code paintText} pour <b>tous</b> les types sauf la liste déroulante, et
     * dessine leur étiquette dès qu'elle n'est pas vide. Trier par type aurait laissé
     * passer une barre de progression dont le nom se coupe en deux.
     */
    @Test
    void cestLeTexteQuiDecidePasLeType() {
        for (ElementKind kind : List.of(ElementKind.IMAGE, ElementKind.PROGRESS,
                ElementKind.SLOT, ElementKind.LABEL)) {
            Blueprint vide = with(new Screen("menu", false, List.of(
                    ScreenElement.of("e", kind, 0, 0, 40, 8)
                            .styled(ElementStyle.DEFAULT.withTextScale(3)))));
            assertFalse(codes(vide).contains(DiagnosticCode.ELEMENT_TEXT_TOO_TALL),
                    kind + " sans étiquette n'a rien à couper — l'avertir serait du bruit "
                            + "qu'on ne peut pas corriger");

            Blueprint etiquete = with(new Screen("menu", false, List.of(
                    ScreenElement.of("e", kind, 0, 0, 40, 8)
                            .withText(ScreenText.literal("Vie"))
                            .styled(ElementStyle.DEFAULT.withTextScale(3)))));
            assertTrue(codes(etiquete).contains(DiagnosticCode.ELEMENT_TEXT_TOO_TALL),
                    kind + " porte une étiquette de vingt-sept pixels dans huit unités : "
                            + "elle sera coupée, quel que soit le type");
        }
    }
}
