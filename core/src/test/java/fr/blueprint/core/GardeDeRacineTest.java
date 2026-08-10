package fr.blueprint.core;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.RootCommandNode;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La garde qui empêche un blueprint de voler une commande.
 *
 * <p>{@code RootCommandNode.addChild} <b>remplace</b> un enfant du même nom, sans un mot.
 * Sans cette garde, un blueprint dont l'événement {@code command} s'appelle « kill »
 * écraserait le {@code /kill} de vanilla — et l'administrateur chercherait longtemps
 * pourquoi sa commande ne tue plus rien.
 *
 * <p>C'est la seule partie de la pose à chaud qui se vérifie sans serveur, et c'est aussi
 * la seule dont une erreur serait grave : le reste échoue bruyamment, celui-ci
 * réussirait silencieusement.
 */
class GardeDeRacineTest {

    private static RootCommandNode<Object> racineAvec(String... noms) {
        RootCommandNode<Object> racine = new RootCommandNode<>();
        for (String nom : noms) {
            racine.addChild(LiteralArgumentBuilder.literal(nom).build());
        }
        return racine;
    }

    @Test
    void unNomLibrePasse() {
        assertNull(BlueprintMod.refusDeRacine(racineAvec("kill", "give"), "home", Set.of()));
    }

    @Test
    void unNomDejaPrisEstRefuseEtLeDit() {
        String refus = BlueprintMod.refusDeRacine(racineAvec("kill"), "kill", Set.of());

        assertNotNull(refus, "écraser /kill doit être refusé, pas accepté en silence");
        assertTrue(refus.contains("/blueprint run kill"),
                "le refus doit indiquer la voie qui marche encore, pas seulement refuser : " + refus);
    }

    @Test
    void uneRacineDejaNotreNestPasUneCollision() {
        // Le cas du /reload : le dispatcher est reconstruit, notre racine a disparu, mais
        // nous savons qu'elle est à nous. La reposer doit être permis — sinon les
        // commandes des blueprints disparaîtraient au premier rechargement de datapacks.
        assertNull(BlueprintMod.refusDeRacine(racineAvec(), "home", Set.of("home")));
    }

    @Test
    void unNomVideEstRefuse() {
        assertNotNull(BlueprintMod.refusDeRacine(racineAvec(), "", Set.of()));
        assertNotNull(BlueprintMod.refusDeRacine(racineAvec(), "   ", Set.of()));
    }
}
