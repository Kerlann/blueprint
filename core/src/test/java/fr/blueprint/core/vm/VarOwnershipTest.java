package fr.blueprint.core.vm;

import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.VarScope;
import fr.blueprint.core.graph.Variable;
import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Une variable appartient à quelqu'un.</b>
 *
 * <p>Le magasin rangeait par {@code (portée, nom)} seul. {@code VarScope.PLAYER} —
 * documenté « persistante par joueur » — mettait donc tous les joueurs dans le même
 * panier : sur un serveur de jeu de rôle, le deuxième à créer son personnage effaçait le
 * prénom du premier, et chacun voyait dans son interface l'identité du dernier arrivé.
 * {@code GRAPH} avait le même trou entre deux blueprints portant chacun un {@code score}.
 *
 * <p>Ces quatre tests échouent tous sur le magasin d'avant. Ils sont écrits contre le
 * magasin plutôt que contre la VM à dessein : c'est là qu'était le défaut, et un test qui
 * passerait par un graphe compilé pourrait rester vert pour une autre raison.
 */
class VarOwnershipTest {

    private static final Identifier RP = Identifier.fromNamespaceAndPath("test", "rp");
    private static final Identifier AUTRE = Identifier.fromNamespaceAndPath("test", "autre");

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    /** <b>Le défaut d'origine.</b> Deux joueurs, deux prénoms, aucun mélange. */
    @Test
    void deuxJoueursNePartagentPasUneVariableDePorteeJoueur() {
        VarStore store = VarStore.inMemory();
        store.set(VarScope.PLAYER, new VarOwner(RP, ALICE), "prenom", "Alice");
        store.set(VarScope.PLAYER, new VarOwner(RP, BOB), "prenom", "Bob");

        assertEquals("Alice", store.get(VarScope.PLAYER, new VarOwner(RP, ALICE), "prenom"),
                "le deuxième joueur a écrasé le prénom du premier");
        assertEquals("Bob", store.get(VarScope.PLAYER, new VarOwner(RP, BOB), "prenom"));
    }

    /** Le même trou, entre deux graphes : deux {@code score} qui n'en font qu'un. */
    @Test
    void deuxBlueprintsNePartagentPasUneVariableDeGraphe() {
        VarStore store = VarStore.inMemory();
        store.set(VarScope.GRAPH, new VarOwner(RP, null), "score", 1.0);
        store.set(VarScope.GRAPH, new VarOwner(AUTRE, null), "score", 2.0);

        assertEquals(1.0, store.get(VarScope.GRAPH, new VarOwner(RP, null), "score"),
                "le second blueprint a écrasé la variable du premier");
        assertEquals(2.0, store.get(VarScope.GRAPH, new VarOwner(AUTRE, null), "score"));
    }

    /**
     * <b>Le défaut signalé.</b> Deux blueprints, un même nom de variable joueur, aucun
     * mélange.
     *
     * <p>Le rangement se faisait par joueur seul : un script de création et un script de
     * métiers déclarant chacun un {@code prenom} écrivaient au même endroit, avec deux
     * types possiblement différents et rien pour le signaler.
     */
    @Test
    void deuxBlueprintsNePartagentPasUneVariableDeJoueur() {
        VarStore store = VarStore.inMemory();
        store.set(VarScope.PLAYER, new VarOwner(RP, ALICE), "prenom", "Alice");
        store.set(VarScope.PLAYER, new VarOwner(AUTRE, ALICE), "prenom", 42);

        assertEquals("Alice", store.get(VarScope.PLAYER, new VarOwner(RP, ALICE), "prenom"),
                "le second blueprint a écrasé la variable du premier, et avec un autre type");
        assertEquals(42, store.get(VarScope.PLAYER, new VarOwner(AUTRE, ALICE), "prenom"));
    }

    /**
     * <b>Mais le partage reste possible — déclaré.</b>
     *
     * <p>Une identité de jeu de rôle écrite par le script de création et lue par celui des
     * métiers est un besoin réel. Il se déclare des deux côtés : deux graphes qui se
     * rencontrent l'ont tous les deux voulu.
     */
    @Test
    void lePartageEntreBlueprintsSeDeclare() {
        VarStore store = VarStore.inMemory();
        store.set(VarScope.PLAYER_SHARED, new VarOwner(RP, ALICE), "prenom", "Alice");

        assertEquals("Alice",
                store.get(VarScope.PLAYER_SHARED, new VarOwner(AUTRE, ALICE), "prenom"),
                "un autre blueprint doit lire ce qui se déclare partagé");
        assertNull(store.get(VarScope.PLAYER_SHARED, new VarOwner(RP, BOB), "prenom"),
                "partagé entre GRAPHES, jamais entre joueurs");
        assertNull(store.get(VarScope.PLAYER, new VarOwner(RP, ALICE), "prenom"),
                "les deux portées sont deux casiers : partager n'est pas fuir");
    }

    /** {@code WORLD} n'appartient à personne, et c'est bien le but. */
    @Test
    void lePorteeMondeResteCommuneAuxDeuxGraphes() {
        VarStore store = VarStore.inMemory();
        store.set(VarScope.WORLD, new VarOwner(RP, ALICE), "heure", 6.0);

        assertEquals(6.0, store.get(VarScope.WORLD, new VarOwner(AUTRE, BOB), "heure"),
                "une variable de monde doit se lire depuis n'importe quel graphe");
    }

    /**
     * <b>Sans joueur, rien.</b> Un tick serveur n'a pas d'acteur : ranger sa valeur dans
     * un panier commun rendrait plus tard l'identité d'un joueur au hasard.
     */
    @Test
    void unAccesJoueurSansJoueurNeRangeNulPart() {
        VarStore store = VarStore.inMemory();
        VarOwner sansJoueur = new VarOwner(RP, null);

        assertTrue(!VarStore.owns(VarScope.PLAYER, sansJoueur),
                "un propriétaire sans joueur ne possède rien de la portée joueur");
        store.set(VarScope.PLAYER, sansJoueur, "prenom", "Personne");
        assertNull(store.get(VarScope.PLAYER, new VarOwner(RP, ALICE), "prenom"),
                "l'écriture sans joueur ne doit atteindre aucun joueur");
    }

    /** Et l'amorçage des défauts suit la même règle, joueur par joueur. */
    @Test
    void lesDefautsSAmorcentChezChaqueJoueur() {
        Blueprint bp = new Blueprint(RP);
        fr.blueprint.core.graph.GraphLoader.addVariable(bp, new Variable("metier", PinTypes.STRING,
                LiteralValue.of(PinTypes.STRING, "Sans-emploi"), VarScope.PLAYER, false));

        VarStore store = VarStore.inMemory();
        store.seedDefaults(bp, new VarOwner(RP, ALICE));

        assertEquals("Sans-emploi", store.get(VarScope.PLAYER, new VarOwner(RP, ALICE), "metier"));
        assertNull(store.get(VarScope.PLAYER, new VarOwner(RP, BOB), "metier"),
                "amorcer chez Alice ne doit rien poser chez Bob");
    }
}
