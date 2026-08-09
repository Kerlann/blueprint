package fr.blueprint.core.content;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L'ordre d'entrée dans les registres — ce qui décide des identifiants réseau.
 *
 * <p>Un item enregistré reçoit un <b>rang</b>, et ce rang voyage sur le réseau à la place
 * du nom. Client et serveur le calculent chacun de leur côté, sans se concerter : s'ils ne
 * produisent pas la même suite, un client affiche « pièce » là où le serveur a écrit
 * « rubis », ou se déconnecte sur un paquet illisible. Rien ne le rattrape à l'exécution.
 *
 * <p>Ce test existe parce que le plan multiloader a rendu le risque réel. Tant qu'un seul
 * chargeur existait, l'ordre était un effet de bord de l'écriture du code — la boucle des
 * blocs venait après celle des items, donc les blocs passaient après. NeoForge ouvre ses
 * registres quand il veut : l'ordre entre eux n'est plus le nôtre. Il fallait donc que la
 * suite des <b>items</b> cesse d'en dépendre, et c'est cela qui se vérifie ici.
 *
 * <p>Écrit <b>avant</b> qu'un second chargeur existe, volontairement : à ce moment-là on
 * peut encore comparer à un comportement de référence.
 */
class ContentOrderTest {

    private static void item(Path dir, String name) throws IOException {
        Files.createDirectories(dir.resolve("items"));
        Files.writeString(dir.resolve("items").resolve(name + ".json"), "{}");
    }

    private static void block(Path dir, String name) throws IOException {
        Files.createDirectories(dir.resolve("blocks"));
        Files.writeString(dir.resolve("blocks").resolve(name + ".json"), "{}");
    }

    private static Identifier id(String name) {
        return Identifier.fromNamespaceAndPath("blueprint", name);
    }

    @Test
    void lesItemsDabordPuisLesBlocs(@TempDir Path dir) throws IOException {
        item(dir, "rubis");
        item(dir, "lingot");
        block(dir, "granit");
        block(dir, "distributeur");

        ContentLoader.Report report = ContentLoader.load(dir);

        // Dans chaque dossier, l'ordre des fichiers triés — pas celui du système de
        // fichiers, qui ne promet rien.
        assertEquals(List.of(id("lingot"), id("rubis"), id("distributeur"), id("granit")),
                ContentRegistrar.itemOrder(report),
                "les items du dossier items/ passent AVANT l'item de chaque bloc");
        assertEquals(List.of(id("distributeur"), id("granit")),
                ContentRegistrar.blockOrder(report));
    }

    /**
     * Le cœur du test : la suite des items ne dépend que du contenu des dossiers.
     *
     * <p>Elle ne dépend ni du moment où la passe des blocs s'exécute, ni de l'ordre dans
     * lequel le chargeur ouvre ses registres — deux choses que Fabric et NeoForge ne
     * décident pas pareil.
     */
    @Test
    void laSuiteDesItemsNeDependPasDeLaPasseDesBlocs(@TempDir Path dir) throws IOException {
        item(dir, "rubis");
        block(dir, "granit");

        ContentLoader.Report report = ContentLoader.load(dir);
        List<Identifier> reference = ContentRegistrar.itemOrder(report);

        // Appelée dix fois, dans n'importe quel entrelacement avec blockOrder : c'est une
        // fonction pure du rapport, elle n'a aucun état à retenir.
        for (int i = 0; i < 10; i++) {
            ContentRegistrar.blockOrder(report);
            assertEquals(reference, ContentRegistrar.itemOrder(report));
        }
    }

    @Test
    void unBlocHomonymeDunItemNapparaitPasDeuxFois(@TempDir Path dir) throws IOException {
        item(dir, "rubis");
        block(dir, "rubis");

        ContentLoader.Report report = ContentLoader.load(dir);
        List<Identifier> order = ContentRegistrar.itemOrder(report);

        // ContentLoader a déjà écarté le bloc : un bloc pose son propre item, du même
        // nom. Sans cela, le même identifiant apparaîtrait deux fois dans la suite, et
        // le second enregistrement lèverait au milieu de la boucle.
        assertEquals(List.of(id("rubis")), order);
        assertEquals(List.of(), ContentRegistrar.blockOrder(report));
        assertEquals(order.size(), order.stream().distinct().count(),
                "aucun identifiant ne peut occuper deux rangs");
    }

    @Test
    void deuxLecturesDuMemeDossierDonnentLaMemeSuite(@TempDir Path dir) throws IOException {
        item(dir, "zebre");
        item(dir, "abeille");
        block(dir, "zinc");
        block(dir, "argile");

        assertEquals(ContentRegistrar.itemOrder(ContentLoader.load(dir)),
                ContentRegistrar.itemOrder(ContentLoader.load(dir)),
                "deux démarrages doivent numéroter pareil, sinon un monde sauvegardé "
                        + "relit ses coffres de travers");
        assertTrue(ContentRegistrar.itemOrder(ContentLoader.load(dir))
                        .indexOf(id("abeille")) < ContentRegistrar.itemOrder(ContentLoader.load(dir))
                        .indexOf(id("zinc")),
                "un item passe toujours avant l'item d'un bloc, quel que soit son nom");
    }
}
