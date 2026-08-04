package fr.blueprint.core.content;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Story 11.3 : les blocs déclarés — le modèle, la règle d'outil, la lecture, le pack. */
class BlockDefinitionTest {

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("blueprint", path);
    }

    private static BlockDefinition block(BlockDefinition.Tool tool, boolean requiresTool) {
        return new BlockDefinition(id("granit"), "", false, 3f, 6f, tool, requiresTool, 0,
                BlockDefinition.Sound.STONE, null);
    }

    @Test
    void lesValeursImpossiblesSontBorneesEtNonRefusees() {
        var absurd = new BlockDefinition(id("granit"), null, false, -5f, -1f, null, false,
                99, null, null);

        assertEquals(0f, absurd.hardness());
        assertEquals(0f, absurd.resistance());
        assertEquals(15, absurd.light(), "la lumière ne dépasse pas 15");
        assertEquals(BlockDefinition.Tool.NONE, absurd.tool());
        assertEquals(BlockDefinition.Sound.STONE, absurd.sound());
        assertEquals("", absurd.name());

        assertEquals(BlockDefinition.MAX_HARDNESS,
                new BlockDefinition(id("x"), "", false, 1e9f, 0f, null, false, 0, null, null)
                        .hardness());
    }

    @Test
    void uneFamilleDOutilInconnueNeCoutePasLeBloc() {
        assertEquals(BlockDefinition.Tool.NONE, BlockDefinition.toolOf("marteau"));
        assertEquals(BlockDefinition.Tool.PICKAXE, BlockDefinition.toolOf("PicKaxe"));
        assertEquals(BlockDefinition.Sound.STONE, BlockDefinition.soundOf("cristal"));
        assertEquals(BlockDefinition.Sound.WOOL, BlockDefinition.soundOf("wool"));
    }

    /**
     * Le test qui compte : <b>la vitesse décide, pas le nom de l'outil</b>.
     *
     * <p>C'est ce qui fait fonctionner la règle avec les outils des autres mods, qu'aucune
     * liste écrite d'avance n'aurait pu connaître — et c'est ce qui remplace les tags
     * {@code mineable/*}, inaccessibles à l'initialisation du mod.
     */
    @Test
    void laVitesseDeMinageVientDeLOutilTenu() {
        var pickaxe = block(BlockDefinition.Tool.PICKAXE, false);

        assertEquals(1f, pickaxe.miningSpeed(1f), "à la main, la vitesse de base");
        assertEquals(6f, pickaxe.miningSpeed(6f), "une pioche de fer accélère d'autant");
        assertEquals(1f, pickaxe.miningSpeed(0.5f),
                "rien ne mine PLUS LENTEMENT qu'une main : ce serait une double peine");

        var soft = block(BlockDefinition.Tool.NONE, false);
        assertEquals(1f, soft.miningSpeed(9f),
                "sans famille d'outil, aucun outil n'aide — l'annoncer ferait chercher "
                        + "un outil qui n'existe pas");
    }

    /**
     * Le test qui compte : <b>un bloc qui exige un outil ne lâche rien sans lui</b>, et un
     * bloc qui n'en exige pas lâche toujours.
     */
    @Test
    void leButinSuitLExigenceDOutil() {
        var strict = block(BlockDefinition.Tool.PICKAXE, true);
        assertFalse(strict.dropsFor(1f), "à la main, rien");
        assertTrue(strict.dropsFor(6f), "avec une pioche, le bloc");

        var lenient = block(BlockDefinition.Tool.PICKAXE, false);
        assertTrue(lenient.dropsFor(1f), "sans exigence, la main suffit");

        // Le piège : exiger un outil sur un bloc dont la famille est NONE rendrait le
        // bloc IMPOSSIBLE à récupérer, puisqu'aucun outil ne peut dépasser la main.
        var trap = block(BlockDefinition.Tool.NONE, true);
        assertTrue(trap.dropsFor(1f), "un bloc irrécupérable serait un piège, pas une règle");
    }

    @Test
    void unBlocParDefautEstJouable() {
        var granite = BlockDefinition.of(id("granit"));
        assertTrue(granite.hardness() > 0f && granite.hardness() < 10f);
        assertEquals(BlockDefinition.Tool.PICKAXE, granite.tool());
        assertFalse(granite.requiresTool());
        assertFalse(granite.hasTexture());
    }

    @Test
    void leDossierDesBlocsEstLuAPartDeCeluiDesItems(@TempDir Path dir) throws IOException {
        Path items = Files.createDirectories(dir.resolve("items"));
        Path blocks = Files.createDirectories(dir.resolve("blocks"));
        Files.writeString(items.resolve("rubis.json"), "{\"name\": \"Rubis\"}",
                StandardCharsets.UTF_8);
        Files.writeString(blocks.resolve("granit.json"),
                "{\"name\": \"Granit\", \"hardness\": 3.5, \"tool\": \"pickaxe\","
                        + " \"requiresTool\": true, \"light\": 7, \"sound\": \"stone\"}",
                StandardCharsets.UTF_8);

        var report = ContentLoader.load(dir);

        assertEquals(1, report.items().size());
        assertEquals(1, report.blocks().size());
        var granite = report.blocks().get(id("granit"));
        assertEquals("Granit", granite.name());
        assertEquals(3.5f, granite.hardness());
        assertEquals(BlockDefinition.Tool.PICKAXE, granite.tool());
        assertTrue(granite.requiresTool());
        assertEquals(7, granite.light());
        assertTrue(report.rejected().isEmpty());
    }

    @Test
    void unBlocFautifNEmportePasLesAutresNiLesItems(@TempDir Path dir) throws IOException {
        Path items = Files.createDirectories(dir.resolve("items"));
        Path blocks = Files.createDirectories(dir.resolve("blocks"));
        Files.writeString(items.resolve("rubis.json"), "{}", StandardCharsets.UTF_8);
        Files.writeString(blocks.resolve("bon.json"), "{}", StandardCharsets.UTF_8);
        Files.writeString(blocks.resolve("casse.json"), "{\"hardness\": ,}",
                StandardCharsets.UTF_8);
        Files.writeString(blocks.resolve("Mon Bloc.json"), "{}", StandardCharsets.UTF_8);

        var report = ContentLoader.load(dir);

        assertEquals(1, report.items().size(), "un bloc fautif n'emporte pas les items");
        assertEquals(1, report.blocks().size());
        assertTrue(report.blocks().containsKey(id("bon")));
        assertEquals(2, report.rejected().size());
        assertTrue(report.rejected().stream().anyMatch(r -> r.startsWith("Mon Bloc.json")),
                "le refus doit nommer son fichier : " + report.rejected());
    }

    @Test
    void unDossierDeBlocsSeulSuffit(@TempDir Path dir) throws IOException {
        Path blocks = Files.createDirectories(dir.resolve("blocks"));
        Files.writeString(blocks.resolve("granit.json"), "{}", StandardCharsets.UTF_8);

        var report = ContentLoader.load(dir);
        assertTrue(report.items().isEmpty(), "déclarer des blocs sans items est légitime");
        assertEquals(1, report.blocks().size());
    }

    /**
     * Le test qui compte : <b>un même nom des deux côtés est tranché avant le registre</b>.
     *
     * <p>Un bloc pose aussi un item, du même identifiant. Sans ce refus, le bloc
     * s'enregistrerait puis son item échouerait : il resterait un bloc réel qu'aucun objet
     * ne permet de poser, et un pack décrivant l'un pendant que le registre contient
     * l'autre.
     */
    @Test
    void unNomDeclareDesDeuxCotesEstTranche(@TempDir Path dir) throws IOException {
        Path items = Files.createDirectories(dir.resolve("items"));
        Path blocks = Files.createDirectories(dir.resolve("blocks"));
        Files.writeString(items.resolve("granit.json"), "{}", StandardCharsets.UTF_8);
        Files.writeString(blocks.resolve("granit.json"), "{}", StandardCharsets.UTF_8);
        Files.writeString(blocks.resolve("marbre.json"), "{}", StandardCharsets.UTF_8);

        var report = ContentLoader.load(dir);

        assertTrue(report.items().containsKey(id("granit")), "l'item garde le nom");
        assertFalse(report.blocks().containsKey(id("granit")), "le bloc cède");
        assertTrue(report.blocks().containsKey(id("marbre")), "l'autre bloc est intact");
        assertEquals(1, report.rejected().size());
        assertTrue(report.rejected().getFirst().contains("granit"), report.rejected().toString());
    }

    @Test
    void unBlocHabilleRecoitEtatModeleEtModeleDItem(@TempDir Path dir) throws IOException {
        Path texture = ContentPackTestSupport.png(dir, "granit.png", 16, 16);
        var granite = new BlockDefinition(id("granit"), "", false, 3f, 6f,
                BlockDefinition.Tool.PICKAXE, false, 0, BlockDefinition.Sound.STONE,
                texture.toString());
        var pack = ContentPack.of(List.of(), List.of(granite));

        assertTrue(pack.files().containsKey("assets/blueprint/blockstates/granit.json"));
        assertTrue(pack.files().containsKey("assets/blueprint/models/block/granit.json"));
        assertTrue(pack.files().containsKey("assets/blueprint/items/granit.json"));
        assertTrue(pack.textures().containsKey("assets/blueprint/textures/block/granit.png"));

        // L'item du bloc doit hériter du MODÈLE DE BLOC, sinon il s'affiche en vignette
        // plate dans la main — visible immédiatement, et sans message d'erreur.
        String item = pack.files().get("assets/blueprint/items/granit.json");
        assertTrue(item.contains("blueprint:block/granit"), item);
        assertTrue(pack.files().get("assets/blueprint/models/block/granit.json")
                .contains("minecraft:block/cube_all"));
    }

    @Test
    void unBlocSansImageNEstPasHabilleEtLeDit() {
        var granite = new BlockDefinition(id("granit"), "", false, 3f, 6f,
                BlockDefinition.Tool.PICKAXE, false, 0, BlockDefinition.Sound.STONE, null);
        var pack = ContentPack.of(List.of(), List.of(granite));

        assertFalse(pack.files().containsKey("assets/blueprint/blockstates/granit.json"));
        assertEquals(1, pack.rejected().size());
        assertTrue(pack.rejected().getFirst().contains("granit.png"));
    }
}
