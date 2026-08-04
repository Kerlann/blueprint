package fr.blueprint.core.content;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Rarity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La lecture du contenu déclaré (épic 11, story 11.1).
 *
 * <p>Ce chargeur tourne pendant l'initialisation du mod, <b>avant le gel des registres</b>
 * et donc avant qu'un serveur, un monde ou un joueur n'existe. Il n'a personne à qui se
 * plaindre : tout ce qu'il peut faire d'un fichier fautif est l'écarter et le nommer.
 *
 * <p>D'où le poids de ces tests. Une erreur ici ne donne pas un message en jeu — elle
 * donne un <b>jeu qui ne démarre pas</b>, parce que Minecraft lève à l'enregistrement d'un
 * identifiant invalide. C'est le seul code du projet dont l'échec se paie avant l'écran
 * titre.
 */
class ContentLoaderTest {

    private static void write(Path dir, String file, String json) throws IOException {
        Files.createDirectories(dir.resolve("items"));
        Files.writeString(dir.resolve("items").resolve(file), json);
    }

    @Test
    void sansDossierIlNyARien(@TempDir Path dir) {
        ContentLoader.Report report = ContentLoader.load(dir.resolve("content"));

        assertTrue(report.items().isEmpty());
        assertTrue(report.rejected().isEmpty(),
                "l'absence de contenu déclaré n'est pas une faute à signaler");
    }

    @Test
    void unFichierMinimalDonneUnItemUtilisable(@TempDir Path dir) throws IOException {
        write(dir, "rubis.json", "{}");

        ContentLoader.Report report = ContentLoader.load(dir);
        ItemDefinition rubis = report.items()
                .get(Identifier.fromNamespaceAndPath("blueprint", "rubis"));

        assertNotNull(rubis, "un fichier vide de champs reste un item valide");
        assertEquals(64, rubis.stackSize());
        assertEquals(Rarity.COMMON, rubis.rarity());
    }

    @Test
    void lesChampsSontLus(@TempDir Path dir) throws IOException {
        write(dir, "epee_legendaire.json", """
                {"name": "Épée légendaire", "stackSize": 1, "rarity": "epic"}
                """);

        ItemDefinition epee = ContentLoader.load(dir).items()
                .get(Identifier.fromNamespaceAndPath("blueprint", "epee_legendaire"));

        assertNotNull(epee);
        assertEquals("Épée légendaire", epee.name());
        assertFalse(epee.translate());
        assertEquals(1, epee.stackSize());
        assertEquals(Rarity.EPIC, epee.rarity());
    }

    /**
     * <b>Le test qui compte.</b> Un fichier fautif n'emporte pas les autres.
     *
     * <p>C'est la leçon de la 8.2, où un JSON au mauvais type faisait échouer tout le
     * rechargement des nœuds de datapack. Ici l'enjeu est plus grand encore : sur vingt
     * items déclarés, une virgule oubliée dans le dix-septième ne doit pas priver le
     * serveur des dix-neuf autres — et surtout pas empêcher le jeu de démarrer.
     */
    @Test
    void unFichierFautifNEmportePasLesAutres(@TempDir Path dir) throws IOException {
        write(dir, "bon.json", "{\"name\": \"Bon\"}");
        write(dir, "casse.json", "{ ceci n'est pas du json");
        write(dir, "vide.json", "");
        write(dir, "autre.json", "{\"name\": \"Autre\"}");

        ContentLoader.Report report = ContentLoader.load(dir);

        assertEquals(2, report.items().size(), "les deux bons fichiers passent");
        assertEquals(2, report.rejected().size());
        assertTrue(report.rejected().stream().anyMatch(r -> r.startsWith("casse.json")),
                () -> "le refus doit NOMMER le fichier : " + report.rejected());
        assertTrue(report.rejected().stream().anyMatch(r -> r.contains("vide")));
    }

    /**
     * Un nom de fichier hors de l'alphabet des identifiants est écarté <b>ici</b>.
     *
     * <p>Sans ce contrôle, Minecraft lèverait à l'enregistrement — c'est-à-dire pendant
     * l'initialisation du mod, avant l'écran titre. Un fichier mal nommé empêcherait donc
     * le jeu de se lancer, et rien à l'écran ne dirait lequel.
     */
    @Test
    void unNomDeFichierInutilisableEstEcarteAvantDeFaireLeverLeJeu(@TempDir Path dir)
            throws IOException {
        write(dir, "Rubis Éclatant.json", "{}");
        write(dir, "correct.json", "{}");

        ContentLoader.Report report = ContentLoader.load(dir);

        assertEquals(1, report.items().size());
        assertTrue(report.rejected().stream()
                        .anyMatch(r -> r.startsWith("Rubis Éclatant.json")),
                () -> report.rejected().toString());
    }

    @Test
    void unePileImpossibleEstBorneePlutotQueRefusee(@TempDir Path dir) throws IOException {
        write(dir, "zero.json", "{\"stackSize\": 0}");
        write(dir, "enorme.json", "{\"stackSize\": 5000}");

        var items = ContentLoader.load(dir).items();

        assertEquals(1, items.get(Identifier.fromNamespaceAndPath("blueprint", "zero"))
                .stackSize(), "une pile de zéro est une faute de frappe, pas un motif de refus");
        assertEquals(ItemDefinition.MAX_STACK,
                items.get(Identifier.fromNamespaceAndPath("blueprint", "enorme")).stackSize());
    }

    @Test
    void uneRareteInconnueVautCommune(@TempDir Path dir) throws IOException {
        write(dir, "bizarre.json", "{\"rarity\": \"flamboyant\"}");

        assertEquals(Rarity.COMMON, ContentLoader.load(dir).items()
                .get(Identifier.fromNamespaceAndPath("blueprint", "bizarre")).rarity(),
                "la couleur d'un nom ne vaut pas de perdre l'item");
    }

    /**
     * La texture est le PNG <b>du même nom</b>, à côté. Aucun chemin à écrire, donc aucun
     * chemin à écrire faux — un champ « texture » aurait demandé de connaître la syntaxe
     * des identifiants de ressource, et se serait trompé une fois sur deux.
     */
    @Test
    void laTextureEstLePngDuMemeNom(@TempDir Path dir) throws IOException {
        write(dir, "avec.json", "{}");
        write(dir, "sans.json", "{}");
        Files.write(dir.resolve("items").resolve("avec.png"), new byte[]{1, 2, 3});

        var items = ContentLoader.load(dir).items();

        assertTrue(items.get(Identifier.fromNamespaceAndPath("blueprint", "avec")).hasTexture());
        assertFalse(items.get(Identifier.fromNamespaceAndPath("blueprint", "sans")).hasTexture(),
                "sans PNG, l'item existe quand même — il s'affichera en damier");
    }

    /**
     * L'ordre de lecture est <b>stable</b> entre deux démarrages.
     *
     * <p>Les identifiants numériques du réseau sont attribués dans l'ordre
     * d'enregistrement. S'il changeait d'un lancement à l'autre, un monde enregistré
     * rouvrirait avec les items permutés — un rubis deviendrait une émeraude, en silence.
     */
    @Test
    void lOrdreDeLectureEstStable(@TempDir Path dir) throws IOException {
        write(dir, "c.json", "{}");
        write(dir, "a.json", "{}");
        write(dir, "b.json", "{}");

        List<String> premier = List.copyOf(ContentLoader.load(dir).items().keySet())
                .stream().map(Identifier::getPath).toList();
        List<String> second = List.copyOf(ContentLoader.load(dir).items().keySet())
                .stream().map(Identifier::getPath).toList();

        assertEquals(List.of("a", "b", "c"), premier, "trié, donc reproductible");
        assertEquals(premier, second);
    }
}
