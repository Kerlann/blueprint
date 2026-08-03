package fr.blueprint.client.pack;

import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.GraphLoader;
import fr.blueprint.core.graph.screen.Anchor;
import fr.blueprint.core.graph.screen.ElementKind;
import fr.blueprint.core.graph.screen.ElementStyle;
import fr.blueprint.core.graph.screen.Extent;
import fr.blueprint.core.graph.screen.LayoutSpec;
import fr.blueprint.core.graph.screen.PackRef;
import fr.blueprint.core.graph.screen.Screen;
import fr.blueprint.core.graph.screen.ScreenElement;
import fr.blueprint.core.graph.screen.ScreenLayout;
import fr.blueprint.core.graph.screen.ScreenText;
import fr.blueprint.core.registry.PluginLoader;
import fr.blueprint.core.script.ScriptGenerator;
import fr.blueprint.core.script.ScriptParser;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le pack d'exemple livré dans {@code docs/examples/packs/} (story 10.5, AC7).
 *
 * <p>Un exemple « prêt à copier » que personne ne vérifie finit par ne plus l'être : les
 * exemples BScript du dépôt ont déjà perdu un filtre d'événement en silence, faute d'un
 * test qui comparait autre chose que des comptes. Son {@code .bp} est donc <b>généré</b>
 * depuis le modèle, comme les autres exemples et la référence des nœuds — écrit à la
 * main, il dériverait à la première évolution de la grammaire.
 *
 * <pre>./gradlew :client:test --tests "*ExamplePackTest" -Dblueprint.regenDocs=true</pre>
 */
class ExamplePackTest {

    private static final String REGEN = "blueprint.regenDocs";
    private static final PluginLoader.LoadedRegistries LOADED = PluginLoader.load(List.of(), true);
    private static final String PACK = "ma_boutique";

    private static Path repoRoot() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.isDirectory(path.resolve("docs"))) {
            path = path.getParent();
        }
        assertNotNull(path, "racine du dépôt introuvable");
        return path;
    }

    private static Path packsDir() {
        return repoRoot().resolve("docs/examples/packs");
    }

    private static Path blueprintFile() {
        return packsDir().resolve(PACK).resolve("boutique.bp");
    }

    // ------------------------------------------------------------- le modèle

    private static final ElementStyle INVISIBLE = new ElementStyle(
            0x00000000, 0x00000000, 0, 0xFFE6E6E6,
            0x00000000, 0x00000000, 0x00000000, 0, ElementStyle.TextAlign.LEFT);
    private static final ElementStyle BOUTON = new ElementStyle(
            0xFF262E42, 0xFF7AA2F7, 1, 0xFFE6E6E6,
            0xFF3A4868, 0xFF141922, 0x40303030, 3, ElementStyle.TextAlign.CENTER);

    /**
     * L'écran de l'exemple : un fond en image, une colonne qui range trois boutons, un
     * style nommé partagé. Ce qu'un pack sert à montrer — et rien de plus, pour qu'on
     * puisse le lire d'un coup d'œil.
     */
    private static Blueprint example() {
        Identifier fond = PackRef.texture(PACK + "/fond");
        List<ScreenElement> elements = new ArrayList<>();
        elements.add(new ScreenElement("fond", ElementKind.IMAGE, null, Anchor.CENTER,
                0, 0, Extent.of(160), Extent.of(96), ScreenText.EMPTY, fond,
                INVISIBLE, "", LayoutSpec.ABSOLUTE, true, true));
        elements.add(ScreenElement.of("colonne", ElementKind.PANEL, 8, 8, 144, 80)
                .withParent("fond")
                .withLayout(LayoutSpec.column(4).withCross(LayoutSpec.Cross.STRETCH))
                .styled(INVISIBLE));
        elements.add(ScreenElement.of("titre", ElementKind.LABEL, 0, 0, 144, 12)
                .withParent("colonne")
                .resized(Extent.fill(), Extent.of(12))
                .withText(ScreenText.literal("Boutique du village"))
                .styled(INVISIBLE));
        for (String[] button : new String[][]{
                {"acheter", "Acheter une pomme"},
                {"vendre", "Vendre du ble"},
                {"fermer", "Fermer"}}) {
            elements.add(ScreenElement.of(button[0], ElementKind.BUTTON, 0, 0, 144, 20)
                    .withParent("colonne")
                    .resized(Extent.fill(), Extent.of(20))
                    .withText(ScreenText.literal(button[1]))
                    .withStyleName("bouton")
                    .styled(BOUTON));
        }

        Blueprint bp = new Blueprint(Identifier.fromNamespaceAndPath("blueprint", "boutique"),
                new fr.blueprint.core.graph.BlueprintMeta("Kerlann",
                        "Exemple de pack : un menu qui utilise les images de " + PACK,
                        "1.0", fr.blueprint.api.node.Permission.SAFE));
        GraphLoader.addScreen(bp, new Screen("boutique", false, elements,
                Map.of("bouton", BOUTON)));
        return bp;
    }

    // -------------------------------------------------------------- les tests

    /**
     * Le {@code .bp} commité est ce que le modèle produit. Écrit à la main, il aurait
     * dérivé à la première évolution de la grammaire — et l'exemple aurait enseigné une
     * syntaxe que le parseur ne lit plus.
     */
    @Test
    void leFichierCommiteCorrespondAuModele() {
        var generated = ScriptGenerator.generate(example(), LOADED.nodes());
        assertTrue(generated.issues().isEmpty(),
                "l'exemple ne se génère pas : " + generated.issues());

        if (Boolean.getBoolean(REGEN)) {
            write(blueprintFile(), generated.text());
            return;
        }
        assertTrue(Files.isRegularFile(blueprintFile()), "boutique.bp absent");
        assertEquals(generated.text(), read(blueprintFile()), """
                boutique.bp diverge du modèle. Régénérer :
                ./gradlew :client:test --tests "*ExamplePackTest" -Dblueprint.regenDocs=true\
                """);
    }

    @Test
    void lePackDExempleChargeSansRienEcarter() {
        var result = ScriptPackLoader.load(packsDir());

        assertEquals(List.of(), result.rejections(),
                "un exemple qui produit un avertissement enseigne l'avertissement");
        ScriptPack pack = result.pack(PACK);
        assertNotNull(pack, "le pack d'exemple doit s'appeler " + PACK);
        assertEquals("1.0", pack.version());
        assertEquals(List.of("bouton", "bouton_survol", "fond"),
                List.copyOf(pack.textures().keySet()));
        assertNotNull(pack.blueprintFile(), "le .bp voyage avec ses images");
    }

    /**
     * <b>Le test qui compte.</b> Chaque texture nommée par l'écran existe dans le
     * dossier. C'est exactement le lien qu'un pack sert à démontrer, et exactement celui
     * qu'une faute de frappe casse sans rien dire — le menu s'ouvre, et l'image est un
     * damier.
     */
    @Test
    void chaqueTextureNommeeParLExempleExisteDansLePack() {
        ScriptPack pack = ScriptPackLoader.load(packsDir()).pack(PACK);
        assertNotNull(pack);
        Screen screen = parsed().screen("boutique");
        assertNotNull(screen, "l'exemple définit bien un écran « boutique »");

        List<String> missing = new ArrayList<>();
        int images = 0;
        for (var element : screen.elements().values()) {
            if (element.texture() == null) {
                continue;
            }
            images++;
            assertEquals(PACK, PackRef.packOf(element.texture()),
                    element.name() + " doit viser le pack d'exemple");
            if (!pack.has(PackRef.fileOf(element.texture()))) {
                missing.add(element.name() + " → " + PackRef.reference(element.texture()));
            }
        }

        assertTrue(images > 0, "un pack d'exemple sans aucune image ne démontre rien");
        assertEquals(List.of(), missing, "textures nommées mais absentes du dossier");
        assertEquals(java.util.Set.of(PACK), screen.requiredPacks(),
                "et l'écran sait de quel pack il dépend");
    }

    /**
     * L'exemple reste lisible <b>sans</b> le pack (AC6) : c'est la contrepartie assumée
     * du fait qu'un serveur ne peut pas pousser de fichiers. Un joueur sans le dossier
     * voit la mise en page, les couleurs et les textes — pas un écran vide.
     */
    @Test
    void lExempleResteLisibleSansAucunPackInstalle() {
        Screen screen = parsed().screen("boutique");
        var rects = ScreenLayout.solve(screen, 320, 180);

        long withText = screen.elements().values().stream()
                .filter(e -> !e.text().isEmpty()).count();
        assertTrue(withText >= 3, "les libellés portent le sens quand les images manquent");

        for (var element : screen.elements().values()) {
            var rect = rects.get(element.name());
            assertNotNull(rect, element.name() + " n'est pas placé");
            assertTrue(rect.width() > 0 && rect.height() > 0,
                    element.name() + " est réduit à rien : " + rect);
        }
        assertFalse(screen.styles().isEmpty(),
                "le style vit dans le blueprint, pas dans les images");
    }

    /** Le fichier commité se relit vraiment : le générateur et le parseur s'accordent. */
    private static Blueprint parsed() {
        var result = ScriptParser.parse(read(blueprintFile()), LOADED);
        assertNotNull(result.blueprint(), "boutique.bp ne se parse pas : " + result.error());
        return result.blueprint();
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
