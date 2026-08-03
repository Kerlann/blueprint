package fr.blueprint.client.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La lecture d'un pack de script (story 10.5).
 *
 * <p>Deux promesses à tenir : un pack cassé n'emporte jamais les autres, et une image
 * démesurée est refusée <b>avant</b> d'être décodée. La seconde ne se vérifie qu'ici :
 * en jeu, on ne saurait pas dire si l'image a été refusée avant ou après avoir alloué
 * quatre cents mégaoctets.
 */
class ScriptPackLoaderTest {

    /**
     * Un PNG minimal mais <b>véritable</b> : signature, IHDR complet avec CRC, IEND. Un
     * en-tête bricolé passerait le lecteur de dimensions et échouerait au décodage réel,
     * ce qui ferait passer le test sur un fichier que le jeu refuserait.
     */
    private static byte[] png(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
        ihdr.write(new byte[]{'I', 'H', 'D', 'R'});
        writeInt(ihdr, width);
        writeInt(ihdr, height);
        ihdr.write(new byte[]{8, 6, 0, 0, 0});   // 8 bits, RVBA, sans entrelacement
        chunk(out, ihdr.toByteArray());
        chunk(out, new byte[]{'I', 'E', 'N', 'D'});
        return out.toByteArray();
    }

    private static void chunk(ByteArrayOutputStream out, byte[] typeAndData) throws IOException {
        writeInt(out, typeAndData.length - 4);
        out.write(typeAndData);
        CRC32 crc = new CRC32();
        crc.update(typeAndData);
        writeInt(out, (int) crc.getValue());
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write(value >>> 24);
        out.write(value >>> 16 & 0xFF);
        out.write(value >>> 8 & 0xFF);
        out.write(value & 0xFF);
    }

    private static Path pack(Path root, String name) throws IOException {
        Path directory = root.resolve(name);
        Files.createDirectories(directory.resolve("textures"));
        return directory;
    }

    private static void texture(Path pack, String fileName, int w, int h) throws IOException {
        Files.write(pack.resolve("textures").resolve(fileName), png(w, h));
    }

    // --------------------------------------------------------------- lecture

    @Test
    void unPackCompletEstLuAvecSesImages(@TempDir Path root) throws IOException {
        Path boutique = pack(root, "ma_boutique");
        Files.writeString(boutique.resolve(ScriptPack.MANIFEST), """
                { "version": "1.2", "author": "Kerlann", "description": "Une boutique" }
                """, StandardCharsets.UTF_8);
        texture(boutique, "fond.png", 256, 128);
        texture(boutique, "bouton.png", 64, 16);
        Files.writeString(boutique.resolve("boutique.bp"), "blueprint \"test:x\" {}");

        var result = ScriptPackLoader.load(root);

        assertEquals(List.of(), result.rejections());
        ScriptPack packed = result.pack("ma_boutique");
        assertNotNull(packed);
        assertEquals("1.2", packed.version());
        assertEquals("Kerlann", packed.author());
        assertEquals(List.of("bouton", "fond"), List.copyOf(packed.textures().keySet()),
                "sans extension, et dans un ordre stable");
        assertEquals("ma_boutique/fond", packed.reference("fond"));
        assertNotNull(packed.blueprintFile(), "le .bp du dossier est repéré");
    }

    /**
     * Le {@code pack.json} est facultatif. L'exiger transformerait « je te donne mon
     * dossier » en une formalité, que le joueur découvrirait par un pack qui ne charge
     * pas — au moment où il essaie de montrer son menu à quelqu'un.
     */
    @Test
    void unDossierDeDeuxImagesSansManifesteChargeQuandMeme(@TempDir Path root) throws IOException {
        texture(pack(root, "simple"), "fond.png", 32, 32);

        var result = ScriptPackLoader.load(root);

        assertEquals(1, result.packs().size());
        assertTrue(result.pack("simple").has("fond"));
        assertEquals("", result.pack("simple").version());
    }

    @Test
    void unDossierDePacksAbsentNestPasUneErreur(@TempDir Path root) {
        var result = ScriptPackLoader.load(root.resolve("jamais_cree"));
        assertTrue(result.packs().isEmpty());
        assertTrue(result.rejections().isEmpty(), "n'avoir rien reçu n'est pas un défaut");
    }

    // ------------------------------------------------------------ isolation

    /**
     * <b>Le test qui compte.</b> Un pack cassé est nommé et écarté ; les autres chargent.
     * La règle des plugins (2.2) et des datapacks (8.2), appliquée ici : un fichier reçu
     * de quelqu'un d'autre ne doit jamais pouvoir empêcher le jeu de démarrer.
     */
    @Test
    void unPackCasseNemporteJamaisLesAutres(@TempDir Path root) throws IOException {
        texture(pack(root, "bon"), "fond.png", 32, 32);

        Path casse = pack(root, "casse");
        Files.writeString(casse.resolve(ScriptPack.MANIFEST), "{ ceci n'est pas du json");
        texture(casse, "fond.png", 32, 32);

        Path malNomme = root.resolve("Mon Pack");
        Files.createDirectories(malNomme.resolve("textures"));
        texture(malNomme, "fond.png", 32, 32);

        var result = ScriptPackLoader.load(root);

        assertEquals(1, result.packs().size(), "seul le bon pack charge");
        assertEquals("bon", result.packs().getFirst().name());
        assertEquals(2, result.rejections().size());
        for (var rejection : result.rejections()) {
            assertFalse(rejection.detail().isBlank(),
                    rejection.pack() + " est écarté sans dire pourquoi");
        }
        assertTrue(result.rejections().stream().anyMatch(r -> r.pack().equals("Mon Pack")),
                "le pack écarté est NOMMÉ, pas juste compté");
    }

    // --------------------------------------------------------------- bornes

    /**
     * Une image démesurée est refusée sur son <b>en-tête</b>, sans être décodée. Refuser
     * après décodage serait refuser trop tard : les 20 000 × 20 000 pixels auraient déjà
     * été alloués, ce qui est exactement l'accident que la borne existe pour empêcher.
     */
    @Test
    void uneImageDemesureeEstRefuseeSansEtreDecodee(@TempDir Path root) throws IOException {
        Path enorme = pack(root, "enorme");
        texture(enorme, "geante.png", 20_000, 20_000);
        texture(enorme, "correcte.png", 64, 64);

        // Le fichier lui-même ne pèse que quelques dizaines d'octets : seul l'en-tête
        // annonce la taille. C'est bien lui, et non le poids, qui déclenche le refus.
        assertTrue(Files.size(enorme.resolve("textures/geante.png")) < 1024);

        var result = ScriptPackLoader.load(root);

        assertEquals(1, result.packs().size(), "le pack charge, amputé de l'image fautive");
        assertEquals(List.of("correcte"), List.copyOf(result.pack("enorme").textures().keySet()));
        assertEquals(1, result.rejections().size());
        assertTrue(result.rejections().getFirst().detail().contains("20000×20000"),
                "la raison nomme la taille : " + result.rejections().getFirst().detail());
    }

    @Test
    void seulLePngEstAccepte(@TempDir Path root) throws IOException {
        Path mixte = pack(root, "mixte");
        texture(mixte, "bon.png", 16, 16);
        Files.writeString(mixte.resolve("textures/photo.jpg"), "peu importe");
        Files.write(mixte.resolve("textures/menteur.png"), "je ne suis pas un png".getBytes());

        var result = ScriptPackLoader.load(root);

        assertEquals(List.of("bon"), List.copyOf(result.pack("mixte").textures().keySet()));
        assertEquals(2, result.rejections().size());
        assertTrue(result.rejections().stream()
                        .anyMatch(r -> r.detail().contains("PNG uniquement")),
                "l'extension est refusée pour ce qu'elle est");
        assertTrue(result.rejections().stream()
                        .anyMatch(r -> r.detail().contains("pas un PNG lisible")),
                "et un fichier qui ment sur son extension aussi");
    }

    @Test
    void unFichierTropLourdEstRefuse(@TempDir Path root) throws IOException {
        Path lourd = pack(root, "lourd");
        byte[] valid = png(64, 64);
        byte[] padded = new byte[(int) ScriptPackLoader.MAX_TEXTURE_BYTES + 1];
        System.arraycopy(valid, 0, padded, 0, valid.length);
        Files.write(lourd.resolve("textures/gros.png"), padded);

        var result = ScriptPackLoader.load(root);

        assertTrue(result.pack("lourd").textures().isEmpty());
        assertTrue(result.rejections().getFirst().detail().contains("Ko"),
                "la raison donne le poids : " + result.rejections().getFirst().detail());
    }

    // -------------------------------------------------- le style sans les images

    /**
     * Le style ne dépend d'<b>aucune</b> image (AC6). C'est ce qui rend la contrepartie
     * du multijoueur supportable : le joueur qui n'a pas le pack voit un menu complet,
     * pas un écran vide. Un écran entièrement stylé n'exige donc aucun pack.
     */
    @Test
    void unEcranEntierementStyleNexigeAucunPack() {
        var style = new fr.blueprint.core.graph.screen.ElementStyle(
                0xFF1E2430, 0xFF3A4453, 2, 0xFFE6E6E6, 0xFF2A3242, 0xFF141922, 0x40303030, 4,
                fr.blueprint.core.graph.screen.ElementStyle.TextAlign.CENTER);
        var screen = new fr.blueprint.core.graph.screen.Screen("menu", false, List.of(
                fr.blueprint.core.graph.screen.ScreenElement.of("cadre",
                                fr.blueprint.core.graph.screen.ElementKind.PANEL, 0, 0, 160, 90)
                        .withLayout(fr.blueprint.core.graph.screen.LayoutSpec.column(4)),
                fr.blueprint.core.graph.screen.ScreenElement.of("ok",
                                fr.blueprint.core.graph.screen.ElementKind.BUTTON, 0, 0, 80, 20)
                        .withParent("cadre").withStyleName("bouton")),
                java.util.Map.of("bouton", style));

        assertTrue(screen.requiredPacks().isEmpty(),
                "couleurs, bordures, marges et alignement ne viennent d'aucun fichier");
        assertEquals(style, screen.styleOf(screen.element("ok")));
        assertEquals(2, screen.styleOf(screen.element("ok")).borderWidth(),
                "la bordure — le « fond en neuf tranches » du style — est bien là");
    }

    // ------------------------------------------------------------ le nommage

    @Test
    void unNomDePackDoitTenirDansUnIdentifiant() {
        assertTrue(ScriptPack.validName("ma_boutique"));
        assertTrue(ScriptPack.validName("pack-2"));
        assertFalse(ScriptPack.validName("Ma Boutique"), "ni majuscule ni espace");
        assertFalse(ScriptPack.validName("café"), "ni accent");
        assertFalse(ScriptPack.validName(""), "ni vide");
        assertFalse(ScriptPack.validName(null));
    }

    @Test
    void lEnTetePngEstLuOuRefuse(@TempDir Path root) throws IOException {
        Path file = root.resolve("image.png");
        Files.write(file, png(320, 180));
        assertArrayEqualsInt(new int[]{320, 180}, ScriptPackLoader.pngSize(file));

        Files.write(file, new byte[]{1, 2, 3});
        assertNull(ScriptPackLoader.pngSize(file), "trop court pour porter un en-tête");
    }

    private static void assertArrayEqualsInt(int[] expected, int[] actual) {
        assertNotNull(actual);
        assertEquals(expected[0], actual[0], "largeur");
        assertEquals(expected[1], actual[1], "hauteur");
    }
}
