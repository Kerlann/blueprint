package fr.blueprint.core;

import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.Diagnostic;
import fr.blueprint.core.graph.GraphLimits;
import fr.blueprint.core.graph.GraphNbt;
import fr.blueprint.core.graph.GraphValidator;
import fr.blueprint.core.graph.screen.Screen;
import fr.blueprint.core.graph.screen.ScreenElement;
import fr.blueprint.core.graph.screen.ScreenLayout;
import fr.blueprint.core.net.GraphSync;
import fr.blueprint.core.net.ScreenSync;
import fr.blueprint.core.registry.PluginLoader;
import fr.blueprint.core.script.ScriptGenerator;
import fr.blueprint.core.script.ScriptParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ce que les petits tests ne pouvaient pas dire.
 *
 * <p>Tous les tests d'écran du projet travaillent sur cinq à soixante-quatre éléments,
 * alors que le modèle en autorise <b>cent vingt-huit par écran</b> et <b>mille nœuds</b>.
 * Entre ce qu'on mesure et ce qu'on autorise, personne ne regardait — et c'est précisément
 * là que vivent les pannes qu'un joueur rencontre : le paquet trop gros, la passe trop
 * lente, l'aller-retour qui perd quelque chose au milieu de cent dix éléments.
 *
 * <p>Chaque test ci-dessous porte donc sur une <b>limite réelle du produit</b>, pas sur un
 * cas de figure inventé.
 */
class StressBlueprintTest {

    static {
        // La démonstration « banque » donne des items : la validation de ses liens résout
        // le défaut du pin ITEMSTACK. Voir MinecraftBootstrap.
        MinecraftBootstrap.ensure();
    }

    private static final PluginLoader.LoadedRegistries LOADED = PluginLoader.load(List.of(), true);
    private static final String REGEN = "blueprint.regenDocs";

    /** Où la session en jeu va les chercher : ce sont des fixtures, pas des exemples. */
    private static final Path OUTPUT_DIR = Path.of("run", "blueprint", "exports");

    private static Blueprint screenBench() {
        return StressBlueprints.bigScreen(LOADED.nodes());
    }

    private static Blueprint graphBench() {
        return StressBlueprints.longGraph(LOADED.nodes());
    }

    // --------------------------------------------------------------- ils sont valides

    /**
     * Un banc qui ne serait pas valide ne prouverait rien : il mesurerait un écran que
     * le produit refuse d'ouvrir.
     */
    @Test
    void lesDeuxBancsPassentLeValidateurSansUneSeuleErreur() {
        for (Blueprint bp : List.of(screenBench(), graphBench())) {
            List<Diagnostic> erreurs = GraphValidator.validate(bp, LOADED.nodes())
                    .diagnostics().stream()
                    .filter(d -> d.severity() == Diagnostic.Severity.ERROR)
                    .toList();
            assertTrue(erreurs.isEmpty(),
                    () -> bp.id() + " : " + erreurs.stream().map(Diagnostic::code).toList());
        }
    }

    @Test
    void ilsRestentSousLesPlafondsQuIlsServentAEprouver() {
        Screen banc = screenBench().screen("banc");
        assertNotNull(banc);
        assertEquals(StressBlueprints.ELEMENTS, banc.size());
        assertTrue(banc.size() <= GraphLimits.DEFAULT.maxElementsPerScreen(),
                "un banc au-dessus du plafond ne mesurerait que le refus");
        assertTrue(graphBench().nodes().size() <= GraphLimits.DEFAULT.maxNodes());
        // Assez près du plafond pour que le plafond compte : à dix éléments, la mesure
        // n'apprendrait rien de ce qui se passe à cent.
        assertTrue(banc.size() > GraphLimits.DEFAULT.maxElementsPerScreen() * 0.8,
                "trop loin du plafond pour l'éprouver");
    }

    /** L'écran chargé montre bien les ONZE types : c'est ce qui le rend complet. */
    @Test
    void lEcranChargeMontreTousLesTypesDElement() {
        Screen banc = screenBench().screen("banc");
        var vus = banc.elements().values().stream().map(ScreenElement::kind)
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(fr.blueprint.core.graph.screen.ElementKind.values().length, vus.size(),
                () -> "types absents du banc : "
                        + List.of(fr.blueprint.core.graph.screen.ElementKind.values()).stream()
                                .filter(k -> !vus.contains(k)).toList());
    }

    // --------------------------------------------------------------- ils passent le fil

    /**
     * <b>Le test qui compte.</b> Un écran réellement chargé tient-il dans son paquet ?
     *
     * <p>{@link ScreenSync#MAX_BYTES} vaut 64 Kio, et rien ne le vérifiait au-delà d'une
     * poignée d'éléments. Un écran qui dépasserait ne s'ouvrirait tout simplement pas chez
     * le joueur — après avoir été dessiné, enregistré et validé sans un mot. La panne
     * arriverait donc au pire moment : à la première ouverture, chez quelqu'un d'autre.
     */
    @Test
    void unEcranChargeTientDansSonPaquetReseau() {
        Blueprint bp = screenBench();
        for (String name : bp.screens().keySet()) {
            byte[] bytes = ScreenSync.toBytes(bp.screen(name));
            assertTrue(bytes.length <= ScreenSync.MAX_BYTES, () -> String.format(
                    "l'écran « %s » pèse %d octets pour un plafond de %d : il ne s'ouvrirait pas",
                    name, bytes.length, ScreenSync.MAX_BYTES));
            assertNotNull(ScreenSync.fromBytes(bytes),
                    "encodé mais illisible : pire qu'un refus");
        }
    }

    @Test
    void unLongGrapheTientDansSonPaquetReseau() {
        byte[] bytes = GraphSync.toBytes(graphBench());
        assertTrue(bytes.length <= GraphSync.MAX_BYTES, () -> String.format(
                "%d octets pour un plafond de %d", bytes.length, GraphSync.MAX_BYTES));
    }

    // ------------------------------------------------------- ils reviennent identiques

    /**
     * L'aller-retour NBT à l'échelle. Comparé sur le <b>contenu</b> : c'est en comptant
     * plutôt qu'en comparant que ce projet a déjà laissé passer la perte d'un filtre
     * d'événement.
     */
    @Test
    void lAllerRetourNbtEstExactSurLesDeuxBancs() {
        for (Blueprint bp : List.of(screenBench(), graphBench())) {
            Blueprint relu = GraphNbt.decode(GraphNbt.encode(bp),
                    id -> fr.blueprint.api.pin.PinTypes.builtin().stream()
                            .filter(type -> type.id().equals(id)).findFirst().orElse(null));
            assertTrue(bp.contentEquals(relu), () -> bp.id() + " a changé en passant par NBT");
        }
    }

    /**
     * L'aller-retour BScript à l'échelle — celui qui a le plus de raisons de perdre
     * quelque chose, puisqu'il traverse une grammaire écrite à la main.
     */
    @Test
    void lAllerRetourBscriptEstExactSurLesDeuxBancs() {
        for (Blueprint bp : List.of(screenBench(), graphBench())) {
            var generated = ScriptGenerator.generate(bp, LOADED.nodes());
            assertTrue(generated.issues().isEmpty(),
                    () -> bp.id() + " : points non émis " + generated.issues());
            var parsed = ScriptParser.parse(generated.text(), LOADED);
            assertTrue(parsed.success(),
                    () -> bp.id() + " : parse échoué — " + parsed.error());
            assertTrue(bp.contentEquals(parsed.blueprint()),
                    () -> bp.id() + " a changé en passant par le texte");
        }
    }

    // ------------------------------------------------------------------ ils tiennent

    /**
     * La passe de disposition sur un écran <b>profond</b> : cent dix éléments, quatre
     * niveaux d'imbrication, trois panneaux défilants.
     *
     * <p>Le banc de rendu existant travaille sur soixante-quatre éléments sur quatre pages
     * plates. Celui-ci va plus loin sur les deux axes qui coûtent — le nombre et la
     * profondeur — et à la taille de fenêtre où l'écran est le plus grand.
     */
    @Test
    void laPasseTientSonBudgetSurUnEcranCharge() {
        Screen banc = screenBench().screen("banc");
        double[][] fenetres = {{320, 180}, {1920, 1080}};

        for (int i = 0; i < 200; i++) {
            ScreenLayout.solve(banc, fenetres[i % 2][0], fenetres[i % 2][1]);
        }
        long start = System.nanoTime();
        for (int i = 0; i < 1_000; i++) {
            ScreenLayout.solve(banc, fenetres[i % 2][0], fenetres[i % 2][1]);
        }
        double perFrame = (System.nanoTime() - start) / 1_000.0;

        assertTrue(perFrame < BUDGET_NANOS, () -> String.format(
                "%.0f ns pour %d éléments (budget %.0f) — à 60 images par seconde, "
                        + "c'est ce qui décide si le menu rame",
                perFrame, banc.size(), BUDGET_NANOS));
    }

    /** Large, parce qu'une machine d'intégration est inconnue. Il attrape un facteur, pas un pourcent. */
    private static final double BUDGET_NANOS = 1_500_000;

    /**
     * Un long graphe se <b>compile</b>. C'est ce qu'aucun test à l'échelle ne faisait :
     * la chaîne de cent vingt maillons est là pour ça, un tas de nœuds indépendants se
     * compilant en un clin d'œil sans rien prouver.
     */
    @Test
    void unLongGrapheSeCompile() {
        Blueprint bp = graphBench();
        var entry = bp.nodes().values().stream()
                .filter(n -> LOADED.nodes().get(n.typeId()).map(t -> t.entryPoint()).orElse(false))
                .findFirst().orElseThrow();

        var compiled = fr.blueprint.core.compile.Compiler.compile(bp, LOADED.nodes(), entry.uuid());
        assertTrue(compiled.diagnostics().stream()
                        .noneMatch(d -> d.severity() == Diagnostic.Severity.ERROR),
                () -> "compilation en erreur : " + compiled.diagnostics());
        assertTrue(compiled.ir().instructions().size() >= StressBlueprints.CHAIN_LENGTH,
                "la chaîne entière doit se retrouver dans le programme");
    }

    // ------------------------------------------------------------------- génération

    /**
     * Régénère <b>tout</b> {@code run/blueprint/exports/} depuis le modèle : les huit
     * exemples, la démo, et les deux bancs.
     *
     * <p>Ce dossier est ce que la session en jeu importe. Il était rempli à la main, par
     * copie, et avait donc dérivé : il lui manquait {@code reglement}, et ses fichiers
     * dataient d'avant l'infobulle, le retour à la ligne et le panneau défilant. Une
     * session de vérification menée dessus aurait donc validé une version qui n'existe
     * plus — le pire résultat possible pour une session, puisqu'elle aurait l'air d'avoir
     * réussi.
     *
     * <p>Écrits <b>depuis le modèle</b>, jamais à la main : c'est la leçon du pack
     * {@code ma_boutique} de la 10.5, dont le {@code .bp} rédigé à la main ne se parsait
     * pas.
     *
     * <pre>{@code
     * ./gradlew :core:test --tests "*StressBlueprintTest" -Dblueprint.regenDocs=true
     * }</pre>
     */
    @Test
    void toutLeDossierDExportSeRegenereDepuisLeModele() {
        if (!Boolean.getBoolean(REGEN)) {
            return;
        }
        Path dir = repoRoot().resolve(OUTPUT_DIR);
        try {
            Files.createDirectories(dir);
            write(dir.resolve("bench.bp"), BenchBlueprint.build(LOADED.nodes()));
            write(dir.resolve("banc_ecran.bp"), screenBench());
            write(dir.resolve("banc_graphe.bp"), graphBench());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void write(Path file, Blueprint bp) throws IOException {
        var generated = ScriptGenerator.generate(bp, LOADED.nodes());
        assertTrue(generated.issues().isEmpty(), () -> "points non émis : " + generated.issues());
        Files.writeString(file, generated.text(), StandardCharsets.UTF_8);
    }

    private static Path repoRoot() {
        Path here = Path.of("").toAbsolutePath();
        return Files.isDirectory(here.resolve("docs")) ? here : here.getParent();
    }
}
