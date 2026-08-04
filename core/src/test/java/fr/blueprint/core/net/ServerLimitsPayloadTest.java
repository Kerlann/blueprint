package fr.blueprint.core.net;

import fr.blueprint.core.config.BlueprintConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Les bornes que le serveur annonce au join (story 10.6, AC2).
 *
 * <p>Sans elles, l'éditeur validait avec les défauts du modèle. Sur un serveur aux quotas
 * resserrés, l'auteur dessinait donc un écran que rien ne signalait, et découvrait le
 * refus à l'enregistrement — après le travail plutôt que pendant, ce qui est exactement
 * ce que « diagnostic à l'édition » veut éviter.
 *
 * <p>Ce qui se vérifie ici est la <b>traversée</b> : ce qu'un administrateur écrit dans
 * son fichier doit arriver intact jusqu'aux bornes que l'éditeur applique. Le paquet
 * lui-même ne se teste pas sans réseau ; sa conversion, si — et c'est là que se perdrait
 * une valeur.
 */
class ServerLimitsPayloadTest {

    @Test
    void lesBornesConfigureesTraversentSansSeDeformer() {
        var config = new BlueprintConfig(2, 10_000, 100, 700, 256, 10, 60, true,
                5, 40, 20, 4);

        var announced = new BlueprintPayloads.ServerLimits(
                config.graphLimits().maxNodes(),
                config.graphLimits().maxScreens(),
                config.graphLimits().maxElementsPerScreen());
        var applied = announced.toGraphLimits();

        assertEquals(700, applied.maxNodes());
        assertEquals(5, applied.maxScreens());
        assertEquals(40, applied.maxElementsPerScreen());
        assertEquals(config.graphLimits(), applied,
                "ce que le serveur applique et ce que l'éditeur appliquera sont les mêmes");
    }

    /**
     * Un paquet abîmé ou venu d'une version antérieure ne doit pas désarmer les bornes.
     * Zéro écran autorisé rendrait tout écran invalide ; un nombre négatif, pire, ferait
     * passer les comparaisons dans le mauvais sens.
     */
    @Test
    void unPaquetAbimeNeDesarmePasLesBornes() {
        var applied = new BlueprintPayloads.ServerLimits(0, -3, Integer.MIN_VALUE)
                .toGraphLimits();

        assertTrue(applied.maxNodes() >= 1);
        assertTrue(applied.maxScreens() >= 1);
        assertTrue(applied.maxElementsPerScreen() >= 1);
    }

    /**
     * Un serveur qui n'annonce rien — solo, ou version antérieure du mod — laisse
     * l'éditeur sur les défauts du modèle : exactement ce qu'il utilisait avant que ces
     * bornes ne voyagent. Une mise à jour ne doit rien resserrer sans qu'on l'ait
     * demandé.
     */
    @Test
    void sansAnnonceLesDefautsDuModeleSAppliquent() {
        var defaults = fr.blueprint.core.graph.GraphLimits.DEFAULT;

        assertEquals(defaults.maxScreens(), BlueprintConfig.DEFAULT.graphLimits().maxScreens());
        assertEquals(defaults.maxElementsPerScreen(),
                BlueprintConfig.DEFAULT.graphLimits().maxElementsPerScreen());
    }
}
