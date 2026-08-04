package fr.blueprint.core;

import fr.blueprint.core.config.BlueprintConfig;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le reflet sur disque : {@code blueprint/exports/} suit ce que le monde contient.
 *
 * <p>Le dossier ne s'écrivait qu'à la demande, par {@code /blueprint export}. Il ne
 * reflétait donc que le jour où l'on y avait pensé, et dérivait dès l'enregistrement
 * suivant — on relisait une version d'avant en croyant relire son travail. C'est
 * exactement ce qui a rendu tout {@code run/exports/} périmé de trois stories.
 *
 * <p>Un <b>reflet</b>, et non une seconde source de vérité : rien ne relit ce dossier au
 * démarrage, et la sauvegarde du monde reste seule autorité. C'est ce qui permet au reflet
 * d'échouer sans conséquence — ce que le test le plus important ci-dessous vérifie.
 */
class AutoExportTest {

    private static final PluginLoader.LoadedRegistries LOADED = PluginLoader.load(List.of(), true);
    private static final Identifier ID = Identifier.fromNamespaceAndPath("test", "reflet");

    private static Blueprint blueprintIn(BlueprintManager manager) {
        return manager.create(ID).orElseThrow();
    }

    @Test
    void unEnregistrementEcritLeFichier(@TempDir Path dir) {
        BlueprintManager manager = new BlueprintManager();
        Blueprint bp = blueprintIn(manager);
        manager.mirrorWith(saved -> BlueprintFiles.export(saved, dir, LOADED));

        assertEquals(BlueprintManager.SaveOutcome.SAVED,
                manager.save(new Blueprint(ID), bp.revision()).outcome());

        Path file = dir.resolve(BlueprintFiles.fileName(ID));
        assertTrue(Files.exists(file), "l'enregistrement doit laisser son reflet");
    }

    /**
     * Le reflet suit <b>chaque</b> enregistrement. C'est tout l'objet : un fichier écrit
     * une seule fois vaudrait à peine mieux que pas de fichier du tout, puisqu'on ne
     * saurait pas de quand il date.
     */
    @Test
    void chaqueEnregistrementRafraichitLeFichier(@TempDir Path dir) {
        BlueprintManager manager = new BlueprintManager();
        Blueprint bp = blueprintIn(manager);
        List<Identifier> reflets = new ArrayList<>();
        manager.mirrorWith(saved -> reflets.add(saved.id()));

        int revision = bp.revision();
        for (int i = 0; i < 3; i++) {
            var result = manager.save(new Blueprint(ID), revision);
            assertEquals(BlueprintManager.SaveOutcome.SAVED, result.outcome());
            revision = result.revision();
        }
        assertEquals(3, reflets.size());
    }

    /**
     * <b>Le test qui compte.</b> Un reflet qui échoue ne coûte pas l'enregistrement.
     *
     * <p>La vérité est dans la sauvegarde du monde ; le fichier n'en est qu'une copie. Un
     * disque plein, un dossier en lecture seule ou un antivirus qui verrouille le fichier
     * doivent faire perdre la copie — jamais le travail. Sans cette garantie, la
     * commodité qu'on vient d'ajouter deviendrait une nouvelle façon de perdre une heure
     * d'édition.
     */
    @Test
    void unRefletQuiEchoueNeCoutePasLEnregistrement() {
        BlueprintManager manager = new BlueprintManager();
        Blueprint bp = blueprintIn(manager);
        manager.mirrorWith(saved -> {
            throw new IllegalStateException("disque plein");
        });

        var result = manager.save(new Blueprint(ID), bp.revision());

        assertEquals(BlueprintManager.SaveOutcome.SAVED, result.outcome(),
                "le monde a bien été écrit ; seul le reflet a échoué");
        assertEquals(bp.revision() + 1, result.revision());
    }

    /** Un enregistrement REFUSÉ n'écrit rien : le fichier ne doit pas devancer le monde. */
    @Test
    void unEnregistrementRefuseNeLaissePasDeReflet() {
        BlueprintManager manager = new BlueprintManager();
        Blueprint bp = blueprintIn(manager);
        List<Identifier> reflets = new ArrayList<>();
        manager.mirrorWith(saved -> reflets.add(saved.id()));

        // Révision périmée : le verrou optimiste refuse, rien n'est écrasé (6.3).
        assertEquals(BlueprintManager.SaveOutcome.CONFLICT,
                manager.save(new Blueprint(ID), bp.revision() + 99).outcome());
        assertTrue(reflets.isEmpty(),
                "un fichier écrit pour un enregistrement refusé mentirait sur l'état du monde");
    }

    /**
     * Supprimer un blueprint <b>n'efface pas</b> son fichier.
     *
     * <p>Asymétrie délibérée. Le reflet suit les écritures, pas les suppressions : effacer
     * le fichier détruirait la dernière copie de quelque chose que le joueur vient de
     * retirer du monde, et le dossier d'exports est justement ce qu'on ouvre pour
     * récupérer. Un fichier orphelin, lui, ne coûte rien : il reste réimportable.
     */
    @Test
    void supprimerUnBlueprintNEffacePasSonFichier(@TempDir Path dir) throws Exception {
        BlueprintManager manager = new BlueprintManager();
        Blueprint bp = blueprintIn(manager);
        manager.mirrorWith(saved -> BlueprintFiles.export(saved, dir, LOADED));
        manager.save(new Blueprint(ID), bp.revision());
        Path file = dir.resolve(BlueprintFiles.fileName(ID));
        assertTrue(Files.exists(file));

        assertTrue(manager.delete(ID));

        assertTrue(Files.exists(file), "le fichier survit : c'est la dernière copie");
        assertTrue(Files.readString(file).contains("test:reflet"));
    }

    @Test
    void sansMiroirLeGestionnaireEcritQuandMemeDansLeMonde() {
        BlueprintManager manager = new BlueprintManager();
        Blueprint bp = blueprintIn(manager);

        assertEquals(BlueprintManager.SaveOutcome.SAVED,
                manager.save(new Blueprint(ID), bp.revision()).outcome(),
                "un gestionnaire de test n'a pas de disque, et cela ne le regarde pas");
    }

    // ------------------------------------------------------------------- réglage

    @Test
    void leRefletEstActifParDefaut() {
        assertTrue(BlueprintConfig.DEFAULT.autoExport(),
                "sans lui le dossier dérive dès l'enregistrement suivant, ce qui est le "
                        + "défaut qu'on vient de corriger");
    }

    @Test
    void ilSeCoupeParLaConfiguration(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("config.json"), "{\"autoExport\": false}");

        assertFalse(BlueprintConfig.load(dir).autoExport());
    }

    /**
     * Une configuration écrite <b>avant</b> ce réglage l'active. Un fichier antérieur ne
     * doit pas priver son serveur d'une commodité qu'il n'a pas eu l'occasion de refuser.
     */
    @Test
    void uneConfigurationAnterieureLeRecoitActif(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("config.json"),
                "{\"commandPermissionLevel\": 2, \"maxNodes\": 500}");

        BlueprintConfig config = BlueprintConfig.load(dir);
        assertTrue(config.autoExport());
        assertEquals(500, config.maxNodes(), "et le reste du fichier est bien lu");
    }
}
