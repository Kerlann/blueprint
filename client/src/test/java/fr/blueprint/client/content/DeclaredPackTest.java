package fr.blueprint.client.content;

import fr.blueprint.core.content.ContentPack;
import fr.blueprint.core.content.ContentPackWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L'installation du pack du contenu déclaré, côté client (story 11.2).
 *
 * <p>Elle n'avait <b>aucun test</b>. C'est pourtant elle qui décide si un item déclaré
 * s'affiche ou reste en damier magenta, et elle est parfaitement vérifiable sans jeu :
 * lire un dossier, construire un plan, écrire des fichiers. Seule l'activation du pack
 * exige un client vivant, et elle est ailleurs.
 *
 * <p>Ce trou est de la même famille que ceux des stories 11.7 et 11.8 : du code livré que
 * rien n'exécutait. Il a été trouvé en cherchant où la couverture du client avait baissé
 * après la suppression des favoris — pas en le cherchant lui.
 */
class DeclaredPackTest {

    /** Un PNG minimal valide : signature puis IHDR, ce que le lecteur d'en-tête lit. */
    private static void png(Path file, int width, int height) throws IOException {
        byte[] bytes = new byte[32];
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        System.arraycopy(signature, 0, bytes, 0, 8);
        bytes[12] = 'I';
        bytes[13] = 'H';
        bytes[14] = 'D';
        bytes[15] = 'R';
        for (int i = 0; i < 4; i++) {
            bytes[16 + i] = (byte) (width >>> (24 - 8 * i));
            bytes[20 + i] = (byte) (height >>> (24 - 8 * i));
        }
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
    }

    private static Path contentWith(Path root, String name, boolean withTexture)
            throws IOException {
        Path items = Files.createDirectories(root.resolve("items"));
        Files.writeString(items.resolve(name + ".json"), "{\"name\": \"" + name + "\"}",
                StandardCharsets.UTF_8);
        if (withTexture) {
            png(items.resolve(name + ".png"), 16, 16);
        }
        return root;
    }

    @Test
    void unItemAvecSonImageEstHabilleEtLePackEcrit(@TempDir Path dir) throws IOException {
        Path content = contentWith(Files.createDirectories(dir.resolve("content")),
                "rubis", true);
        Path packs = dir.resolve("resourcepacks");

        DeclaredPack.install(content, packs);

        assertEquals(1, DeclaredPack.dressed());
        Path pack = packs.resolve(ContentPackWriter.DIRECTORY);
        assertTrue(Files.isRegularFile(pack.resolve("pack.mcmeta")));
        assertTrue(Files.isRegularFile(pack.resolve("assets/blueprint/items/rubis.json")));
        assertTrue(Files.isRegularFile(
                pack.resolve("assets/blueprint/textures/item/rubis.png")));
        assertTrue(Files.isRegularFile(pack.resolve(ContentPack.STAMP)));
        assertEquals(java.util.List.of(), DeclaredPack.notices());
    }

    /**
     * <b>Le test qui compte.</b> Un item sans image n'est pas habillé, et la raison est
     * dite au joueur.
     *
     * <p>C'est le cas le plus déroutant du contenu déclaré : l'item existe, se donne, se
     * range — et s'affiche en damier. Sans le message, rien ne distingue « j'ai oublié le
     * PNG » de « le mod est cassé ».
     */
    @Test
    void unItemSansImageEstSignaleAuJoueur(@TempDir Path dir) throws IOException {
        Path content = contentWith(Files.createDirectories(dir.resolve("content")),
                "rubis", false);

        DeclaredPack.install(content, dir.resolve("resourcepacks"));

        assertEquals(0, DeclaredPack.dressed());
        assertEquals(1, DeclaredPack.notices().size());
        assertTrue(DeclaredPack.notices().getFirst().contains("rubis.png"),
                "la raison doit nommer le fichier attendu : " + DeclaredPack.notices());
    }

    /** Un dossier de contenu absent n'est pas une faute : c'est l'état par défaut. */
    @Test
    void unDossierAbsentNeProduitRien(@TempDir Path dir) {
        DeclaredPack.install(dir.resolve("jamais-creee"), dir.resolve("resourcepacks"));

        assertEquals(0, DeclaredPack.dressed());
        assertEquals(java.util.List.of(), DeclaredPack.notices());
        assertFalse(Files.exists(dir.resolve("resourcepacks")
                .resolve(ContentPackWriter.DIRECTORY)));
    }

    /**
     * <b>Le test qui compte.</b> Un dossier qu'on n'a pas créé est <b>épargné</b>, et le
     * refus est dit.
     *
     * <p>Ce dossier vit dans {@code resourcepacks/}, au milieu des packs du joueur. Un
     * pack téléchargé portant ce nom serait détruit sans que rien ne le dise — et sans
     * qu'aucun historique ne puisse le rendre.
     */
    @Test
    void unDossierEtrangerEstEpargneEtLeRefusEstDit(@TempDir Path dir) throws IOException {
        Path content = contentWith(Files.createDirectories(dir.resolve("content")),
                "rubis", true);
        Path packs = dir.resolve("resourcepacks");
        Path intrus = Files.createDirectories(packs.resolve(ContentPackWriter.DIRECTORY));
        Files.writeString(intrus.resolve("pack.mcmeta"), "à quelqu'un d'autre",
                StandardCharsets.UTF_8);

        DeclaredPack.install(content, packs);

        assertEquals("à quelqu'un d'autre", Files.readString(intrus.resolve("pack.mcmeta")));
        assertTrue(DeclaredPack.notices().stream()
                        .anyMatch(n -> n.contains(ContentPackWriter.DIRECTORY)),
                "le refus doit être dit au joueur : " + DeclaredPack.notices());
    }

    /** Deux installations identiques : la seconde ne réécrit rien. */
    @Test
    void uneSecondeInstallationIdentiqueNeReecritRien(@TempDir Path dir) throws IOException {
        Path content = contentWith(Files.createDirectories(dir.resolve("content")),
                "rubis", true);
        Path packs = dir.resolve("resourcepacks");

        DeclaredPack.install(content, packs);
        Path stamp = packs.resolve(ContentPackWriter.DIRECTORY).resolve(ContentPack.STAMP);
        String first = Files.readString(stamp);

        DeclaredPack.install(content, packs);

        assertEquals(first, Files.readString(stamp), "l'empreinte ne doit pas bouger");
        assertEquals(1, DeclaredPack.dressed());
    }
}
