package fr.blueprint.core.content;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Rarity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Story 11.2 : le pack de ressources du contenu déclaré. */
class ContentPackTest {

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("blueprint", path);
    }

    /** Un PNG minimal valide — voir {@link ContentPackTestSupport}, partagé avec la 11.3. */
    private static Path png(Path directory, String name, int width, int height)
            throws IOException {
        return ContentPackTestSupport.png(directory, name, width, height);
    }

    private static ItemDefinition item(String name, Path texture) {
        return new ItemDefinition(id(name), "", false, 64, Rarity.COMMON,
                texture == null ? null : texture.toString());
    }

    @Test
    void unItemHabilleRecoitSesTroisFichiers(@TempDir Path dir) throws IOException {
        var pack = ContentPack.of(List.of(item("rubis", png(dir, "rubis.png", 16, 16))));

        assertTrue(pack.files().containsKey("assets/blueprint/items/rubis.json"),
                "la définition de modèle : c'est son absence qui donne le damier");
        assertTrue(pack.files().containsKey("assets/blueprint/models/item/rubis.json"));
        assertTrue(pack.textures().containsKey("assets/blueprint/textures/item/rubis.png"));
        assertTrue(pack.covers(id("rubis")));
        assertEquals(1, pack.dressed());
        assertTrue(pack.rejected().isEmpty());

        // Le modèle doit pointer la texture du pack, pas une texture du jeu : une faute
        // ici donnerait un item parfaitement valide portant l'image de quelqu'un d'autre.
        String model = pack.files().get("assets/blueprint/models/item/rubis.json");
        assertTrue(model.contains("\"layer0\": \"blueprint:item/rubis\""), model);
        assertTrue(model.contains("minecraft:item/generated"), model);
    }

    /**
     * Le mcmeta doit annoncer un <b>intervalle</b>, pas une version.
     *
     * <p>L'ancienne version de ce test vérifiait que le fichier contenait le champ qu'on
     * venait d'y écrire : il ne pouvait donc rien attraper. Le jeu, lui, a une règle —
     * au-delà du format 64, un pack qui n'annonce que {@code pack_format} est
     * <b>rejeté en entier</b>, et {@code supported_formats} est refusé. C'est cette règle
     * qui est écrite ici, et c'est elle qui manquait : le pack du contenu déclaré a été
     * refusé en silence, si bien que chaque item déclaré s'affichait en damier.
     */
    @Test
    void leMcmetaDeclareLIntervalleQueLeJeuExige(@TempDir Path dir) throws IOException {
        var pack = ContentPack.of(List.of(item("rubis", png(dir, "rubis.png", 16, 16))));
        String meta = pack.files().get("pack.mcmeta");
        assertNotNull(meta);

        assertTrue(meta.contains("\"min_format\": " + ContentPack.PACK_FORMAT), meta);
        assertTrue(meta.contains("\"max_format\": " + ContentPack.PACK_FORMAT), meta);
        assertFalse(meta.contains("pack_format"),
                "pack_format seul fait rejeter le pack au-delà du format 64 : " + meta);
        assertFalse(meta.contains("supported_formats"),
                "supported_formats est refusé au-delà du format 64 : " + meta);
    }

    /**
     * Le test qui compte : <b>sans image, rien n'est écrit</b>.
     *
     * <p>Écrire un modèle pointant vers une texture absente donnerait le damier quand
     * même, mais en le faisant passer pour un défaut du pack. Ne rien écrire laisse le
     * jeu produire son message habituel, et la raison est dite au joueur.
     */
    @Test
    void unItemSansImageNEstPasHabilleEtLeDit() {
        var pack = ContentPack.of(List.of(item("rubis", null)));

        assertFalse(pack.covers(id("rubis")));
        assertEquals(0, pack.dressed());
        assertEquals(1, pack.rejected().size());
        assertTrue(pack.rejected().getFirst().contains("rubis.png"),
                "la raison doit nommer le fichier attendu : " + pack.rejected());
    }

    @Test
    void uneImageQuiNEnEstPasUneEstEcarteeSansEmporterLesAutres(@TempDir Path dir)
            throws IOException {
        Path fake = dir.resolve("faux.png");
        Files.writeString(fake, "ceci n'est pas une image", StandardCharsets.UTF_8);
        var pack = ContentPack.of(List.of(
                item("bon", png(dir, "bon.png", 16, 16)),
                item("faux", fake),
                item("enorme", png(dir, "enorme.png", 4096, 4096))));

        assertTrue(pack.covers(id("bon")), "un voisin fautif n'emporte pas les bons");
        assertFalse(pack.covers(id("faux")));
        assertFalse(pack.covers(id("enorme")));
        assertEquals(2, pack.rejected().size());
        assertTrue(pack.rejected().stream().anyMatch(r -> r.contains("4096")),
                "la borne dépassée doit être dite : " + pack.rejected());
    }

    @Test
    void lEmpreinteSuitLeContenuEtPasLesDates(@TempDir Path dir) throws IOException {
        Path texture = png(dir, "rubis.png", 16, 16);
        String before = ContentPack.of(List.of(item("rubis", texture))).stamp();

        // Même contenu, une seconde plus tard : l'empreinte ne doit pas bouger, sans quoi
        // chaque démarrage réécrirait le pack et rechargerait les ressources pour rien.
        Files.setLastModifiedTime(texture,
                java.nio.file.attribute.FileTime.fromMillis(
                        Files.getLastModifiedTime(texture).toMillis() + 60_000));
        assertEquals(before, ContentPack.of(List.of(item("rubis", texture))).stamp());

        // Image modifiée sans changer de taille : l'empreinte DOIT bouger, sinon le
        // joueur redessinerait son icône et ne la verrait jamais.
        byte[] bytes = Files.readAllBytes(texture);
        bytes[bytes.length - 1] = 42;
        Files.write(texture, bytes);
        org.junit.jupiter.api.Assertions.assertNotEquals(before,
                ContentPack.of(List.of(item("rubis", texture))).stamp());
    }

    @Test
    void lOrdreNeChangePasLEmpreinteDUnDemarrageALAutre(@TempDir Path dir) throws IOException {
        var items = List.of(item("a", png(dir, "a.png", 16, 16)),
                item("b", png(dir, "b.png", 16, 16)));
        assertEquals(ContentPack.of(items).stamp(), ContentPack.of(items).stamp());
    }

    @Test
    void unPackEcritEstRelu(@TempDir Path dir) throws IOException {
        Path source = Files.createDirectories(dir.resolve("source"));
        Path target = dir.resolve("resourcepacks").resolve("blueprint_content");
        var pack = ContentPack.of(List.of(item("rubis", png(source, "rubis.png", 16, 16))));

        var first = ContentPackWriter.writeIfChanged(pack, target);
        assertTrue(first.ok());
        assertTrue(first.changed());
        assertTrue(Files.isRegularFile(target.resolve("pack.mcmeta")));
        assertTrue(Files.isRegularFile(
                target.resolve("assets/blueprint/textures/item/rubis.png")));

        // Le test qui compte : rien n'a changé, donc RIEN n'est fait — pas de
        // réécriture, et surtout pas de rechargement des ressources au démarrage.
        var second = ContentPackWriter.writeIfChanged(pack, target);
        assertTrue(second.ok());
        assertFalse(second.changed(), "un second passage identique ne doit rien faire");
    }

    /**
     * Le test qui compte : <b>un item retiré ne laisse pas de fantôme</b>.
     *
     * <p>Sans élagage, son modèle et sa texture resteraient sur le disque ; le jeu
     * continuerait de les charger et l'item paraîtrait toujours là, alors qu'il n'est
     * plus enregistré. C'est la version « pack » du fichier orphelin.
     */
    @Test
    void unItemRetireNeLaissePasSesFichiers(@TempDir Path dir) throws IOException {
        Path source = Files.createDirectories(dir.resolve("source"));
        Path target = dir.resolve("pack");
        var two = ContentPack.of(List.of(item("rubis", png(source, "rubis.png", 16, 16)),
                item("saphir", png(source, "saphir.png", 16, 16))));
        assertTrue(ContentPackWriter.writeIfChanged(two, target).changed());
        assertTrue(Files.isRegularFile(target.resolve("assets/blueprint/items/saphir.json")));

        var one = ContentPack.of(List.of(item("rubis", source.resolve("rubis.png"))));
        assertTrue(ContentPackWriter.writeIfChanged(one, target).changed());

        assertTrue(Files.isRegularFile(target.resolve("assets/blueprint/items/rubis.json")));
        assertFalse(Files.exists(target.resolve("assets/blueprint/items/saphir.json")),
                "le modèle du disparu doit disparaître");
        assertFalse(Files.exists(target.resolve("assets/blueprint/textures/item/saphir.png")),
                "sa texture aussi");
    }

    /**
     * Le test qui compte : <b>on n'écrase pas un dossier qu'on n'a pas créé</b>.
     *
     * <p>Ce dossier vit dans {@code resourcepacks/}, au milieu des packs du joueur. Un
     * pack téléchargé qui porterait ce nom serait détruit sans que rien ne le dise, et
     * sans qu'aucun historique ne puisse le rendre. La leçon a été apprise dans cette
     * même session, en écrasant un export que personne ne pouvait récupérer.
     */
    @Test
    void unDossierEtrangerNEstPasEcrase(@TempDir Path dir) throws IOException {
        Path source = Files.createDirectories(dir.resolve("source"));
        Path target = Files.createDirectories(dir.resolve("pack"));
        Path precious = target.resolve("pack.mcmeta");
        Files.writeString(precious, "le pack de quelqu'un d'autre", StandardCharsets.UTF_8);

        var pack = ContentPack.of(List.of(item("rubis", png(source, "rubis.png", 16, 16))));
        var outcome = ContentPackWriter.writeIfChanged(pack, target);

        assertFalse(outcome.ok(), "il faut refuser, pas écraser");
        assertFalse(outcome.changed());
        assertNotNull(outcome.refusal());
        assertEquals("le pack de quelqu'un d'autre", Files.readString(precious));
    }

    @Test
    void unDossierVideEstAPrendre(@TempDir Path dir) throws IOException {
        Path source = Files.createDirectories(dir.resolve("source"));
        Path target = Files.createDirectories(dir.resolve("pack"));
        var pack = ContentPack.of(List.of(item("rubis", png(source, "rubis.png", 16, 16))));
        assertTrue(ContentPackWriter.writeIfChanged(pack, target).ok());
        assertTrue(Files.isRegularFile(target.resolve(ContentPack.STAMP)));
    }

    /**
     * Le test qui compte : <b>la création se distingue de la réécriture</b>.
     *
     * <p>C'est cette distinction qui autorise l'activation automatique une seule fois. Sans
     * elle, un pack décoché par le joueur serait recoché à chaque démarrage : la case du
     * menu ne ferait plus rien, et il n'y aurait aucune façon d'en sortir.
     */
    @Test
    void laCreationSeDistingueDeLaReecriture(@TempDir Path dir) throws IOException {
        Path source = Files.createDirectories(dir.resolve("source"));
        Path target = dir.resolve("pack");
        var one = ContentPack.of(List.of(item("rubis", png(source, "rubis.png", 16, 16))));

        var creation = ContentPackWriter.writeIfChanged(one, target);
        assertTrue(creation.created(), "le premier passage crée le pack");

        var two = ContentPack.of(List.of(item("rubis", source.resolve("rubis.png")),
                item("saphir", png(source, "saphir.png", 16, 16))));
        var rewrite = ContentPackWriter.writeIfChanged(two, target);
        assertTrue(rewrite.changed(), "un item de plus change le pack");
        assertFalse(rewrite.created(), "mais ce n'est plus une création");

        assertFalse(ContentPackWriter.writeIfChanged(two, target).created());
    }

    @Test
    void unPackSansAucunItemResteValide(@TempDir Path dir) {
        var pack = ContentPack.of(List.of());
        assertEquals(0, pack.dressed());
        assertTrue(pack.rejected().isEmpty());
        assertTrue(ContentPackWriter.writeIfChanged(pack, dir.resolve("pack")).ok());
    }

    @Test
    void lEnTetePngRefuseCeQuiNEnEstPasUn(@TempDir Path dir) throws IOException {
        Path text = dir.resolve("faux.png");
        Files.writeString(text, "pas une image du tout, mais assez longue", StandardCharsets.UTF_8);
        assertNull(PngHeader.size(text));
        assertNull(PngHeader.size(dir.resolve("absent.png")));

        int[] size = PngHeader.size(png(dir, "vrai.png", 32, 64));
        assertNotNull(size);
        assertEquals(32, size[0]);
        assertEquals(64, size[1]);
    }
}
