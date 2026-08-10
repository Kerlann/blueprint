package fr.blueprint.core.net;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ce qu'un joueur parti ne laisse pas derrière lui (story 10.3, AC5).
 *
 * <p>{@link RateLimiter} promet dans son javadoc que sa table est « bornée par le nombre de
 * joueurs connectés ». La promesse tenait pour deux seaux sur quatre : {@code CLICKS} et
 * {@code OPENS} n'étaient jamais vidés, donc chaque joueur ayant jamais cliqué — ou ayant
 * jamais reçu un écran — laissait une entrée gardée <b>jusqu'au redémarrage du serveur</b>.
 *
 * <p>Ce n'est pas une fuite qui se remarque : quelques dizaines d'octets par joueur, sur un
 * serveur public, pendant des mois. C'est précisément pourquoi elle se teste ici plutôt
 * qu'elle ne s'observe en jeu.
 *
 * <p>Les seaux sont des statiques partagés par toute la JVM de test : les comptes sont donc
 * lus <b>avant et après</b> plutôt que comparés à zéro, sans quoi ce fichier dépendrait de
 * l'ordre d'exécution des autres.
 */
class QuotaForgetTest {

    private static List<Integer> counts() {
        List<Integer> out = new ArrayList<>();
        for (RateLimiter bucket : ServerBlueprintNet.quotaBuckets()) {
            out.add(bucket.tracked());
        }
        return out;
    }

    @Test
    void unJoueurPartiNeLaisseAucunSeauDerriereLui() {
        List<Integer> before = counts();
        UUID alice = UUID.randomUUID();

        // Chaque seau porte maintenant une entrée pour Alice : c'est ce que fait la
        // première requête, le premier clic, le premier écran reçu.
        for (RateLimiter bucket : ServerBlueprintNet.quotaBuckets()) {
            bucket.allow(alice);
        }
        List<Integer> during = counts();
        for (int i = 0; i < before.size(); i++) {
            assertEquals(before.get(i) + 1, during.get(i),
                    "le seau " + i + " suit Alice avant sa déconnexion");
        }

        ServerBlueprintNet.forget(alice);

        assertEquals(before, counts(),
                "aucun seau ne garde un joueur parti — les quatre, pas deux");
    }

    /**
     * Les quatre seaux du modèle de menaces : enregistrements, requêtes, clics, ouvertures.
     * Le compte est ce qui rend le test précédent load-bearing — en ajouter un cinquième
     * sans l'énumérer le ferait passer au vert sans rien vérifier de lui.
     */
    @Test
    void lesQuatreSeauxSontEnumeres() {
        assertEquals(4, ServerBlueprintNet.quotaBuckets().size());
        assertTrue(ServerBlueprintNet.quotaBuckets().stream().distinct().count() == 4,
                "quatre seaux distincts, pas le même compté quatre fois");
    }

    /** Oublier un joueur qui n'a rien fait n'est pas une erreur : la déconnexion est aveugle. */
    @Test
    void oublierUnInconnuNeChangeRien() {
        List<Integer> before = counts();

        ServerBlueprintNet.forget(UUID.randomUUID());

        assertEquals(before, counts());
    }
}
