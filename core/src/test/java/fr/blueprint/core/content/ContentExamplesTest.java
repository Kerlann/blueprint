package fr.blueprint.core.content;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le <b>contenu d'exemple</b> de l'épic 11, prêt à déposer.
 *
 * <p>La 10.5 livrait son pack d'images tout fait, et c'est ce qui rend sa vérification en
 * jeu tenable : on copie un dossier, on recharge, on regarde. L'épic 11 n'avait rien
 * d'équivalent — ses quatre points de vérification commençaient tous par « écrire un JSON
 * à la main », y compris le PNG, qu'on ne fabrique pas dans un bloc-notes.
 *
 * <p>Ce test tient donc deux dossiers : {@code docs/examples/content/}, qu'on lit et qu'on
 * copie, et {@code run/blueprint/content/}, où la session en jeu va le chercher. Les deux
 * sont <b>générés depuis ce fichier</b> plutôt que déposés à la main — c'est la leçon de
 * la 10.15, où un {@code run/} rempli par copie avait dérivé de trois stories sans que
 * personne ne le voie.
 *
 * <pre>./gradlew :core:test --tests "*ContentExamplesTest" -Dblueprint.regenDocs=true</pre>
 */
class ContentExamplesTest {

    private static final String REGEN = "blueprint.regenDocs";

    /** Ce qu'on lit, copie et commite. */
    private static final Path DOCS = Path.of("..", "docs", "examples", "content");
    /** Ce que la session en jeu importe — {@code run/} n'est pas suivi par git. */
    private static final Path RUN = Path.of("..", "run", "blueprint", "content");

    /**
     * Les définitions. Volontairement <b>minimales</b> : chaque champ présent est un
     * champ qu'on veut montrer, et un exemple qui déclare tout n'apprend pas lequel est
     * obligatoire — aucun ne l'est.
     */
    private static final Map<String, String> ITEMS = Map.of(
            "rubis", """
                    {
                      "name": "Rubis",
                      "stackSize": 16,
                      "rarity": "rare"
                    }
                    """,
            // La monnaie de l'exemple « banque ». Deux coupures, parce qu'une seule ne
            // demande aucun calcul : c'est en rendant 250 en deux lingots et cinquante
            // pièces qu'un distributeur devient autre chose qu'un compteur.
            "piece", """
                    {
                      "name": "Pièce",
                      "stackSize": 64
                    }
                    """,
            "lingot", """
                    {
                      "name": "Lingot (100 pièces)",
                      "stackSize": 64,
                      "rarity": "uncommon"
                    }
                    """);

    private static final Map<String, String> BLOCKS = Map.of(
            "granit_bleu", """
                    {
                      "name": "Granit bleu",
                      "hardness": 3.0,
                      "resistance": 6.0,
                      "tool": "pickaxe",
                      "requiresTool": true,
                      "light": 7,
                      "sound": "stone"
                    }
                    """,
            // Le bloc qu'on pose et sur lequel on clique droit pour ouvrir la banque.
            // C'est lui qui relie les trois moitiés de l'épic 11 : un bloc déclaré, des
            // items déclarés, et un graphe qui réagit aux deux.
            "distributeur", """
                    {
                      "name": "Distributeur de billets",
                      "hardness": 2.0,
                      "resistance": 12.0,
                      "tool": "pickaxe",
                      "light": 10,
                      "sound": "metal"
                    }
                    """);

    /** Une teinte par déclaration, reconnaissable d'un coup d'œil dans l'inventaire. */
    private static final Map<String, int[]> COLOURS = Map.of(
            "rubis", new int[]{0xC0_1A2B, 0xFF_5566},
            "granit_bleu", new int[]{0x3A_4A6B, 0x55_6F99},
            "piece", new int[]{0xB8_860B, 0xFF_D700},
            "lingot", new int[]{0x8B_6914, 0xFF_C125},
            "distributeur", new int[]{0x2F_4F4F, 0x4A_C0C0});

    /**
     * Une texture 16×16 lisible, faite de deux teintes en damier grossier.
     *
     * <p>Pas une image d'artiste : une image qui prouve que la chaîne va du PNG déposé
     * jusqu'à l'objet en main. Elle doit surtout se distinguer du damier magenta de
     * « texture absente », faute de quoi le point de vérification ne prouverait rien.
     */
    private static byte[] texture(String name) {
        int[] tones = COLOURS.get(name);
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                boolean edge = x == 0 || y == 0 || x == 15 || y == 15;
                int rgb = edge || ((x / 4 + y / 4) % 2 == 0) ? tones[0] : tones[1];
                image.setRGB(x, y, 0xFF000000 | rgb);
            }
        }
        var out = new java.io.ByteArrayOutputStream();
        try {
            javax.imageio.ImageIO.write(image, "PNG", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /** Tout ce que les deux dossiers doivent contenir : chemin relatif → octets. */
    private static Map<String, byte[]> expected() {
        Map<String, byte[]> files = new java.util.LinkedHashMap<>();
        ITEMS.forEach((name, json) -> {
            files.put("items/" + name + ".json", json.getBytes(StandardCharsets.UTF_8));
            files.put("items/" + name + ".png", texture(name));
        });
        BLOCKS.forEach((name, json) -> {
            files.put("blocks/" + name + ".json", json.getBytes(StandardCharsets.UTF_8));
            files.put("blocks/" + name + ".png", texture(name));
        });
        return files;
    }

    private static void write(Path root, Map<String, byte[]> files) throws IOException {
        for (var entry : files.entrySet()) {
            Path file = root.resolve(entry.getKey());
            Files.createDirectories(file.getParent());
            Files.write(file, entry.getValue());
        }
    }

    /**
     * <b>Le test qui compte.</b> Les fichiers commités sont ceux que ce fichier décrit.
     *
     * <p>Sans lui, le dossier d'exemple dériverait exactement comme {@code run/} l'a fait
     * en 10.15 : il resterait plausible, il serait faux, et on ne s'en apercevrait qu'en
     * jeu — au moment le plus coûteux.
     */
    @Test
    void lesFichiersCommitesCorrespondentAuModele() throws IOException {
        Map<String, byte[]> files = expected();
        if (Boolean.getBoolean(REGEN)) {
            write(DOCS, files);
            write(RUN, files);   // run/ est ignoré par git : régénéré, jamais commité
            return;
        }
        List<String> diverging = new java.util.ArrayList<>();
        for (var entry : files.entrySet()) {
            Path file = DOCS.resolve(entry.getKey());
            if (!Files.isRegularFile(file)) {
                diverging.add(entry.getKey() + " (absent)");
            } else if (!java.util.Arrays.equals(Files.readAllBytes(file), entry.getValue())) {
                diverging.add(entry.getKey() + " (divergent)");
            }
        }
        assertTrue(diverging.isEmpty(), "Fichier(s) de contenu à régénérer : " + diverging
                + "\n./gradlew :core:test --tests \"*ContentExamplesTest\" -Dblueprint.regenDocs=true");
    }

    /**
     * <b>Le test qui compte.</b> Le contenu d'exemple passe le vrai chargeur, sans refus.
     *
     * <p>Un exemple qui ne se charge pas est pire qu'aucun exemple : la première chose
     * qu'on fait avec, c'est le copier, et la deuxième c'est en conclure que la
     * fonctionnalité est cassée. Le {@code .bp} rédigé à la main de la 10.5 avait
     * exactement ce défaut, et c'est ce qui a donné la règle « généré, jamais recopié ».
     */
    @Test
    void leContenuDExempleSeChargeSansUnSeulRefus() throws IOException {
        Path temp = Files.createTempDirectory("blueprint-content-exemple");
        write(temp, expected());

        var report = ContentLoader.load(temp);

        assertEquals(List.of(), report.rejected(), "un exemple doit se charger tel quel");
        assertEquals(ITEMS.size(), report.items().size());
        assertEquals(BLOCKS.size(), report.blocks().size());

        var rubis = report.items().get(Identifier.fromNamespaceAndPath("blueprint", "rubis"));
        assertEquals("Rubis", rubis.name());
        assertEquals(16, rubis.stackSize());
        assertTrue(rubis.hasTexture(), "le PNG voisin doit être trouvé");

        var granite = report.blocks()
                .get(Identifier.fromNamespaceAndPath("blueprint", "granit_bleu"));
        assertEquals(BlockDefinition.Tool.PICKAXE, granite.tool());
        assertTrue(granite.requiresTool());
        assertEquals(7, granite.light());
        assertTrue(granite.hasTexture());
    }

    /**
     * Et le pack de ressources se construit pour eux <b>sans écarter personne</b> : c'est
     * la moitié qui décide si l'objet s'affiche ou reste en damier magenta.
     */
    @Test
    void lePackSeConstruitPourLeContenuDExemple() throws IOException {
        Path temp = Files.createTempDirectory("blueprint-content-pack");
        write(temp, expected());
        var report = ContentLoader.load(temp);

        var pack = ContentPack.of(report.items().values(), report.blocks().values());

        assertEquals(List.of(), pack.rejected(), "aucune texture d'exemple ne doit être écartée");
        assertTrue(pack.covers(Identifier.fromNamespaceAndPath("blueprint", "rubis")));
        assertTrue(pack.files().containsKey("assets/blueprint/blockstates/granit_bleu.json"));
        assertEquals(ITEMS.size() + BLOCKS.size(), pack.dressed());
    }
}
