package fr.blueprint.core.registry;

import fr.blueprint.api.BlueprintPlugin;
import fr.blueprint.api.registry.NodeRegistry;
import fr.blueprint.platform.PlatformMods;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La fusion des deux voies de découverte (lot C du plan multiloader).
 *
 * <p>Un mod qui veut marcher sur tous les chargeurs se déclarera <b>des deux côtés</b> :
 * par l'entrypoint, que la story 8.1 a publié, et par un fichier de service, seul
 * mécanisme portable. C'est le cas normal, pas le cas tordu — et sans dédoublonnage, ses
 * nœuds seraient enregistrés deux fois, refusés la seconde, et son plugin isolé pour un
 * conflit avec lui-même. Il verrait ses nœuds devenir fantômes en <i>ajoutant</i> du
 * support.
 */
class PluginMergeTest {

    /** Un plugin qui n'enregistre rien : seule son identité compte ici. */
    private static class Muet implements BlueprintPlugin {
        private final String modId;

        Muet(String modId) {
            this.modId = modId;
        }

        @Override
        public String modId() {
            return modId;
        }

        @Override
        public void registerNodes(NodeRegistry registry) {
        }
    }

    /** Une classe distincte : le dédoublonnage travaille sur la CLASSE, pas l'instance. */
    private static final class Autre extends Muet {
        Autre(String modId) {
            super(modId);
        }
    }

    @Test
    void unPluginDeclareDesDeuxCotesNestChargeQuUneFois() {
        var partage = new Muet("exemple");
        List<String> refus = new ArrayList<>();

        var entries = PluginLoader.merge(
                List.of(new PlatformMods.ModPlugin("exemple", partage)),
                // Le service construit SA propre instance de la même classe : c'est ce
                // que fait ServiceLoader, et c'est pourquoi comparer les instances ne
                // marcherait pas.
                List.of(new Muet("exemple")),
                refus::add);

        assertEquals(1, entries.size(), "le même plugin, deux voies, une seule entrée");
        assertTrue(refus.isEmpty(), "se déclarer deux fois n'est pas une faute");
    }

    @Test
    void leModidDuChargeurFaitAutorite() {
        // Le plugin se trompe sur son propre nom ; le chargeur, lui, le tient de ses
        // métadonnées. C'est le chargeur qui gagne.
        var entries = PluginLoader.merge(
                List.of(new PlatformMods.ModPlugin("le_vrai_nom", new Muet("un_nom_invente"))),
                List.of(new Muet("un_nom_invente")),
                r -> { });

        assertEquals("le_vrai_nom", entries.get(0).modId());
    }

    @Test
    void unServiceSansModidEstRefuseEtNomme() {
        List<String> refus = new ArrayList<>();

        var entries = PluginLoader.merge(List.of(),
                List.of(new BlueprintPlugin() {
                    @Override
                    public void registerNodes(NodeRegistry registry) {
                    }
                }),
                refus::add);

        assertTrue(entries.isEmpty(), "sans modId, le plugin ne peut pas être attribué");
        assertEquals(1, refus.size());
        assertTrue(refus.get(0).contains("modId"),
                "le refus doit dire quoi corriger, pas seulement qu'il refuse : " + refus.get(0));
    }

    @Test
    void deuxPluginsDifferentsPassentTousLesDeux() {
        var entries = PluginLoader.merge(
                List.of(new PlatformMods.ModPlugin("mod_a", new Muet("mod_a"))),
                List.of(new Autre("mod_b")),
                r -> { });

        assertEquals(List.of("mod_a", "mod_b"),
                entries.stream().map(PluginLoader.PluginEntry::modId).toList(),
                "le chargeur d'abord, les services ensuite — un ordre stable, "
                        + "sinon deux démarrages n'enregistreraient pas pareil");
    }

    @Test
    void unServiceSeulSuffit() {
        var entries = PluginLoader.merge(List.of(), List.of(new Muet("tout_seul")), r -> { });

        assertEquals(1, entries.size());
        assertEquals("tout_seul", entries.get(0).modId(),
                "c'est la voie que docs/extension-api.md recommande : elle doit marcher "
                        + "sans le moindre entrypoint");
    }
}
