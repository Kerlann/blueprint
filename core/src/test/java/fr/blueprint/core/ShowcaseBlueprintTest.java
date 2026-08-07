package fr.blueprint.core;

import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.GraphValidator;
import fr.blueprint.core.graph.screen.ElementKind;
import fr.blueprint.core.registry.PluginLoader;
import fr.blueprint.core.script.ScriptGenerator;
import fr.blueprint.core.script.ScriptParser;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La vitrine <b>compile, se valide et survit au texte</b>.
 *
 * <p>Un exemple qui ne compile pas est pire que pas d'exemple : il apprend une erreur, et
 * il l'apprend avec autorité. Celui-ci prétend en plus montrer <b>tous</b> les types
 * d'éléments — une promesse qui se vérifie plutôt qu'elle ne se relit.
 */
class ShowcaseBlueprintTest {

    private static final PluginLoader.LoadedRegistries LOADED = PluginLoader.load(List.of(), true);

    private static Blueprint built() {
        return ShowcaseBlueprint.build(LOADED.nodes());
    }

    /** <b>La promesse du nom.</b> Aucun type d'élément ne manque à la vitrine. */
    @Test
    void lesOnzeTypesDElementsSontTousPresents() {
        var screen = built().screen(ShowcaseBlueprint.SCREEN);
        assertTrue(screen != null, "l'écran « vitrine » doit exister");

        var present = EnumSet.noneOf(ElementKind.class);
        screen.elements().values().forEach(element -> present.add(element.kind()));

        var missing = EnumSet.allOf(ElementKind.class);
        missing.removeAll(present);
        assertTrue(missing.isEmpty(),
                "la vitrine promet TOUS les types d'éléments, il en manque : " + missing);
    }

    /** Elle se valide sans un seul diagnostic — pas même un avertissement. */
    @Test
    void elleNeLaissePasUnSeulDiagnostic() {
        var diagnostics = GraphValidator.validate(built(), LOADED.nodes()).diagnostics();
        assertTrue(diagnostics.isEmpty(),
                "un exemple livré ne doit rien laisser à corriger : " + diagnostics);
    }

    /**
     * Et elle revient identique par le texte.
     *
     * <p>C'est la garantie centrale du produit, et c'est aussi là que ce projet a déjà
     * perdu un filtre d'événement sans que rien ne le voie. La vitrine porte huit nœuds
     * d'événement dont sept filtrés par un littéral : elle est le pire cas de ce défaut.
     */
    @Test
    void elleRevientIdentiqueParLeTexte() {
        Blueprint original = built();
        String text = ScriptGenerator.generate(original, LOADED.nodes()).text();

        ScriptParser.ParseResult parsed = ScriptParser.parse(text, LOADED);
        assertTrue(parsed.success(), () -> "le texte de la vitrine ne se relit pas : "
                + parsed.error());
        assertEquals(text, ScriptGenerator.generate(parsed.blueprint(), LOADED.nodes()).text(),
                "aller-retour non identique — quelque chose se perd à l'écriture ou à la lecture");
    }

    /** Les liaisons sont bien là : sans elles, le titre et la barre resteraient morts. */
    @Test
    void leTitreEtLaBarreSuiventLaVariable() {
        var screen = built().screen(ShowcaseBlueprint.SCREEN);
        var titre = screen.element("titre");
        var barre = screen.element("barre");

        assertEquals("score", titre.binding().variable(),
                "le titre doit suivre « score » plutôt que d'être écrit par un nœud");
        assertEquals("score", barre.binding().variable(),
                "la barre doit suivre la MÊME variable que le titre");
    }
}
