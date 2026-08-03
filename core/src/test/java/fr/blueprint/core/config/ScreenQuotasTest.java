package fr.blueprint.core.config;

import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.Diagnostic;
import fr.blueprint.core.graph.DiagnosticCode;
import fr.blueprint.core.graph.GraphLoader;
import fr.blueprint.core.graph.GraphValidator;
import fr.blueprint.core.graph.NodeTypeLookup;
import fr.blueprint.core.graph.screen.ElementKind;
import fr.blueprint.core.graph.screen.Screen;
import fr.blueprint.core.graph.screen.ScreenElement;
import fr.blueprint.core.net.GraphGuard;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Les quotas d'écrans, de bout en bout (story 10.6, AC1 et AC2).
 *
 * <p>Ce qui est vérifié n'est pas qu'un champ existe dans un fichier, mais que ce qu'on y
 * écrit <b>arrive</b> jusqu'aux deux endroits qui décident : le validateur, qui prévient
 * l'auteur, et le garde réseau, qui refuse ce qui vient de l'extérieur. Un quota
 * configurable qui n'atteindrait ni l'un ni l'autre serait un réglage décoratif — et rien
 * ne le dirait, puisque le défaut, lui, fonctionne.
 *
 * <p>Les écrans bornent trois surfaces neuves : de la mémoire par joueur <b>connecté</b>,
 * des paquets à chaque clic, et un rendu dans la boucle d'affichage du client. C'est le
 * premier état du produit qui grandit avec le nombre de joueurs plutôt qu'avec celui des
 * blueprints.
 */
class ScreenQuotasTest {

    private static final NodeTypeLookup LOOKUP = typeId -> null;
    private static final Identifier QUOTAS_ID =
            Identifier.fromNamespaceAndPath("test", "quotas");

    private static Blueprint withScreens(int screens, int elementsEach) {
        Blueprint bp = new Blueprint(QUOTAS_ID);
        for (int s = 0; s < screens; s++) {
            List<ScreenElement> elements = new ArrayList<>();
            for (int e = 0; e < elementsEach; e++) {
                elements.add(ScreenElement.of("e" + e, ElementKind.LABEL, 0, 0, 20, 10));
            }
            GraphLoader.addScreen(bp, new Screen("ecran" + s, false, elements));
        }
        return bp;
    }

    // ------------------------------------------------------------ configuration

    /**
     * Ce qu'on écrit dans le fichier arrive dans les deux jeux de bornes. Les laisser
     * diverger rendrait le garde réseau plus permissif que l'éditeur — c'est le sens à ne
     * pas prendre : le serveur accepterait ce que l'auteur ne peut pas construire.
     */
    @Test
    void lesQuotasEcritsArriventDansLesDeuxJeuxDeBornes(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("config.json"), """
                {
                  "maxScreens": 3,
                  "maxElementsPerScreen": 7,
                  "clicksPerWindow": 5,
                  "opensPerWindow": 2
                }
                """, StandardCharsets.UTF_8);

        BlueprintConfig config = BlueprintConfig.load(root);

        assertEquals(3, config.graphLimits().maxScreens());
        assertEquals(7, config.graphLimits().maxElementsPerScreen());
        assertEquals(3, config.netLimits().maxScreens(), "le garde réseau suit le modèle");
        assertEquals(7, config.netLimits().maxElementsPerScreen());
        assertEquals(5, config.netLimits().clicksPerWindow());
        assertEquals(2, config.netLimits().opensPerWindow());
    }

    /**
     * Un fichier écrit par une version antérieure n'a aucun de ces quatre champs. Il doit
     * reprendre les défauts, exactement comme avant qu'ils ne soient réglables — sans
     * quoi une mise à jour du mod ramènerait tous les quotas à zéro.
     */
    @Test
    void uneConfigurationDAvantLesEcransGardeLesDefauts(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("config.json"),
                "{ \"commandPermissionLevel\": 2, \"maxNodes\": 500 }", StandardCharsets.UTF_8);

        BlueprintConfig config = BlueprintConfig.load(root);

        assertEquals(500, config.graphLimits().maxNodes(), "ce qui est écrit est lu");
        assertEquals(BlueprintConfig.DEFAULT.maxScreens(), config.graphLimits().maxScreens());
        assertEquals(BlueprintConfig.DEFAULT.maxElementsPerScreen(),
                config.graphLimits().maxElementsPerScreen());
    }

    /** Un fichier absent est créé avec les quatre champs : ils se découvrent en le lisant. */
    @Test
    void leFichierCreeMontreLesQuotasDEcrans(@TempDir Path root) throws IOException {
        BlueprintConfig.load(root);

        String written = Files.readString(root.resolve("config.json"), StandardCharsets.UTF_8);
        for (String key : List.of("maxScreens", "maxElementsPerScreen",
                "clicksPerWindow", "opensPerWindow")) {
            assertTrue(written.contains(key), key + " absent du fichier créé");
        }
    }

    /** Une valeur absurde ne désarme pas la borne : elle est ramenée au minimum. */
    @Test
    void unQuotaAbsurdeNeDesarmePasLaBorne(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("config.json"),
                "{ \"maxScreens\": 0, \"maxElementsPerScreen\": -50, \"clicksPerWindow\": 0 }",
                StandardCharsets.UTF_8);

        BlueprintConfig config = BlueprintConfig.load(root);

        assertTrue(config.graphLimits().maxScreens() >= 1);
        assertTrue(config.graphLimits().maxElementsPerScreen() >= 1);
        assertTrue(config.netLimits().clicksPerWindow() >= 1);
    }

    // ------------------------------------------------------ diagnostic à l'édition

    /**
     * <b>AC2.</b> Le dépassement est un diagnostic, pas une coupure à l'exécution :
     * l'auteur l'apprend dans l'éditeur, pas le joueur devant un écran vide.
     */
    @Test
    void leDepassementEstUnDiagnosticEtPasUneCoupure() {
        var limits = new fr.blueprint.core.graph.GraphLimits(1000, 2, 4);

        var codes = GraphValidator.validate(withScreens(3, 2), LOOKUP, limits)
                .diagnostics().stream().map(Diagnostic::code).toList();
        assertTrue(codes.contains(DiagnosticCode.SCREEN_LIMIT_EXCEEDED),
                "trop d'écrans : " + codes);

        var perScreen = GraphValidator.validate(withScreens(1, 9), LOOKUP, limits)
                .diagnostics().stream().map(Diagnostic::code).toList();
        assertTrue(perScreen.contains(DiagnosticCode.ELEMENT_LIMIT_EXCEEDED),
                "trop d'éléments : " + perScreen);

        assertTrue(GraphValidator.validate(withScreens(2, 4), LOOKUP, limits).errors().isEmpty(),
                "pile sur la borne : rien à signaler");
    }

    // ---------------------------------------------------------- refus côté réseau

    /**
     * Le garde applique les quotas <b>configurés</b>, pas les défauts. C'est lui qui
     * protège de ce qui vient de l'extérieur : un client peut envoyer n'importe quoi, et
     * un fichier {@code .bp} déposé à la main est du contenu extérieur au même titre.
     */
    @Test
    void leGardeRefuseSelonLesQuotasConfigures(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("config.json"),
                "{ \"maxScreens\": 2, \"maxElementsPerScreen\": 4 }", StandardCharsets.UTF_8);
        var limits = BlueprintConfig.load(root).netLimits();

        var tooMany = GraphGuard.inspect(QUOTAS_ID,
                withScreens(5, 1), LOOKUP, limits);
        assertFalse(tooMany.accepted(), "cinq écrans pour une borne de deux");
        assertNotNull(tooMany.reason(), "un refus doit dire POURQUOI");
        assertTrue(tooMany.reason().contains("2"), "et nommer la borne : " + tooMany.reason());

        var tooBig = GraphGuard.inspect(QUOTAS_ID,
                withScreens(1, 9), LOOKUP, limits);
        assertFalse(tooBig.accepted());
        assertTrue(tooBig.reason().contains("ecran0"),
                "et nommer l'écran fautif : " + tooBig.reason());

        assertTrue(GraphGuard.inspect(QUOTAS_ID,
                        withScreens(2, 4), LOOKUP, limits).accepted(),
                "pile sur la borne : accepté");
    }
}
