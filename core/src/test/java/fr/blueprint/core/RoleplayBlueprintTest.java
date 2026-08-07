package fr.blueprint.core;

import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.GraphValidator;
import fr.blueprint.core.graph.VarScope;
import fr.blueprint.core.graph.screen.ElementBinding;
import fr.blueprint.core.registry.PluginLoader;
import fr.blueprint.core.script.ScriptGenerator;
import fr.blueprint.core.script.ScriptParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le blueprint de serveur RP <b>compile, se valide et survit au texte</b>.
 *
 * <p>Un exemple qui ne compile pas est pire que pas d'exemple : il apprend une erreur, et
 * il l'apprend avec autorité.
 */
class RoleplayBlueprintTest {

    private static final PluginLoader.LoadedRegistries LOADED = PluginLoader.load(List.of(), true);

    private static Blueprint built() {
        return RoleplayBlueprint.build(LOADED.nodes());
    }

    /** Elle se valide sans un seul diagnostic — pas même un avertissement. */
    @Test
    void elleNeLaissePasUnSeulDiagnostic() {
        var diagnostics = GraphValidator.validate(built(), LOADED.nodes()).diagnostics();
        assertTrue(diagnostics.isEmpty(),
                "un exemple livré ne doit rien laisser à corriger : " + diagnostics);
    }

    /** Et elle revient identique par le texte — la garantie centrale du produit. */
    @Test
    void elleRevientIdentiqueParLeTexte() {
        Blueprint original = built();
        String text = ScriptGenerator.generate(original, LOADED.nodes()).text();

        ScriptParser.ParseResult parsed = ScriptParser.parse(text, LOADED);
        assertTrue(parsed.success(), () -> "le texte du blueprint RP ne se relit pas : "
                + parsed.error());
        assertEquals(text, ScriptGenerator.generate(parsed.blueprint(), LOADED.nodes()).text(),
                "aller-retour non identique — quelque chose se perd à l'écriture ou à la lecture");
    }

    /**
     * <b>Toute l'identité est de portée joueur.</b>
     *
     * <p>C'est la propriété qui décide si ce blueprint est utilisable sur un serveur. En
     * portée {@code GRAPH}, le deuxième joueur à créer son personnage effacerait le prénom
     * du premier, et chacun verrait dans sa fiche l'identité du dernier arrivé.
     */
    @Test
    void toutesLesVariablesSontDePorteeJoueur() {
        var partagees = built().variables().values().stream()
                .filter(v -> v.scope() != VarScope.PLAYER)
                .map(v -> v.name() + " (" + v.scope() + ')')
                .toList();

        assertTrue(partagees.isEmpty(),
                "une identité de personnage appartient à un joueur : " + partagees);
    }

    /**
     * <b>La vie ne passe pas par le serveur.</b>
     *
     * <p>La version naïve de cette fiche lierait la vie à une variable, ce qui obligerait
     * un {@code server_tick} à parcourir les joueurs connectés vingt fois par seconde pour
     * redire à chacun ce qu'il voit déjà dans ses propres cœurs. Ce test échoue si
     * quelqu'un refait ce chemin.
     */
    @Test
    void laVieDeLaFicheEstUneSourceClient() {
        var fiche = built().screen(RoleplayBlueprint.FICHE);
        assertTrue(fiche != null, "l'écran « fiche » doit exister");
        assertTrue(fiche.hud(), "la fiche est un HUD : un modal figerait le joueur en jeu");

        for (String nom : List.of("vie", "vie_texte")) {
            assertEquals(ElementBinding.Source.CLIENT, fiche.element(nom).binding().source(),
                    "« " + nom + " » doit se lire chez le client, sans variable ni paquet");
        }
        // Et l'inverse : le nom et le métier, eux, ne peuvent venir que du serveur.
        for (String nom : List.of("prenom", "nom", "metier")) {
            assertEquals(ElementBinding.Source.VARIABLE, fiche.element(nom).binding().source(),
                    "« " + nom + " » n'existe que dans une variable du graphe");
        }
    }

    /**
     * Aucune variable n'est écrite sans qu'un rafraîchissement suive.
     *
     * <p>C'est la panne la plus déroutante des interfaces : la variable change, l'écran ne
     * bouge pas. Ici le rafraîchissement suit la validation, seul moment où la fiche a
     * quelque chose de neuf à montrer — les frappes de clavier, elles, n'affichent rien.
     */
    @Test
    void laValidationRafraichitLaFiche() {
        String text = ScriptGenerator.generate(built(), LOADED.nodes()).text();
        assertTrue(text.contains("blueprint:gui/refresh"),
                "sans gui/refresh, la fiche resterait vide après la création");
        assertTrue(text.contains("blueprint:hud/show"),
                "la fiche doit s'afficher : c'est un HUD, pas un écran qu'on ouvre");
    }
}
