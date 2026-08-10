package fr.blueprint.core.vm;

import fr.blueprint.core.graph.VarScope;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le plafond de 64 Ko par joueur et l'effacement de ses données (NFR14).
 *
 * <p>Aucun des deux n'existait. Tout vivait dans le {@code SavedData} du monde sans borne,
 * et rien ne permettait d'effacer un joueur : un graphe qui ajoute une ligne d'historique à
 * chaque mort — cas parfaitement ordinaire — faisait grossir la sauvegarde du monde sans
 * fin, et aucun symptôme ne le disait avant que le fichier ne devienne pénible à écrire.
 *
 * <p>La borne se teste sur {@link VarBuckets} plutôt que sur la VM : c'est là que vit la
 * règle, et un test qui passerait par un graphe compilé mesurerait surtout le compilateur.
 */
class VarQuotaTest {

    private static final Identifier BOUTIQUE =
            Identifier.fromNamespaceAndPath("test", "boutique");
    private static final Identifier BANQUE = Identifier.fromNamespaceAndPath("test", "banque");

    private static VarOwner owner(UUID player, Identifier blueprint) {
        return new VarOwner(blueprint, player);
    }

    /** Une chaîne dont le poids estimé approche le plafond : ~32 Ko de caractères. */
    private static String heavy(int bytes) {
        return "x".repeat(bytes / 2);
    }

    // --------------------------------------------------------------- le poids estimé

    @Test
    void lePoidsEstimeEstMajorant() {
        // Deux octets par caractère : un texte accentué pèse plus en UTF-8 qu'un ASCII, et
        // l'estimation doit se tromper vers le haut, jamais vers le bas.
        assertTrue(VarQuota.sizeOf("héllo") >= "héllo".getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        assertEquals(8, VarQuota.sizeOf(3.5));
        assertEquals(4, VarQuota.sizeOf(7));
        assertEquals(1, VarQuota.sizeOf(true));
        assertEquals(0, VarQuota.sizeOf(null), "une valeur absente ne pèse rien");
    }

    @Test
    void unNomCompteDansLePoidsDeSonEntree() {
        assertTrue(VarQuota.entrySize("progression_du_joueur", 1.0)
                > VarQuota.entrySize("x", 1.0));
    }

    /**
     * Une liste qui se contient elle-même n'est pas une hypothèse : {@code list/add} peut
     * l'ajouter à elle-même. Sans borne de profondeur, la mesure finirait en
     * {@code StackOverflowError} dans le chemin d'écriture de la VM — exactement ce que
     * NFR4 interdit d'atteindre.
     */
    @Test
    void uneListeQuiSeContientNeFaitPasDeborderLaPile() {
        List<Object> boucle = new ArrayList<>();
        boucle.add(boucle);
        boucle.add("texte");

        int size = VarQuota.sizeOf(boucle);

        assertTrue(size > 0, "elle pèse quelque chose");
        assertTrue(size < Integer.MAX_VALUE);
    }

    @Test
    void unTypeInconnuPeseSansLever() {
        assertTrue(VarQuota.sizeOf(new Object()) > 0,
                "compter zéro laisserait passer sans limite ce qu'on ne sait pas mesurer");
    }

    // ------------------------------------------------------------------- le plafond

    @Test
    void uneEcritureOrdinaireEstAcceptee() {
        VarBuckets buckets = new VarBuckets();
        UUID alice = UUID.randomUUID();

        assertTrue(buckets.put(VarScope.PLAYER, owner(alice, BOUTIQUE), "or", 100.0));
        assertEquals(100.0, buckets.of(VarScope.PLAYER, owner(alice, BOUTIQUE), false).get("or"));
        assertTrue(buckets.playerBytesOf(alice) > 0);
    }

    @Test
    void auDelaDuPlafondLEcritureEstRefuseeEtRienNestModifie() {
        VarBuckets buckets = new VarBuckets();
        UUID alice = UUID.randomUUID();
        var chez = owner(alice, BOUTIQUE);

        assertTrue(buckets.put(VarScope.PLAYER, chez, "journal",
                heavy(VarQuota.MAX_PLAYER_BYTES - 1_000)));
        int before = buckets.playerBytesOf(alice);

        assertFalse(buckets.put(VarScope.PLAYER, chez, "second", heavy(4_000)),
                "la seconde écriture ferait dépasser");
        assertEquals(before, buckets.playerBytesOf(alice), "et le total n'a pas bougé");
        assertNull(buckets.of(VarScope.PLAYER, chez, false).get("second"),
                "ni le casier : un refus ne laisse pas la moitié de la valeur");
    }

    /**
     * Les deux portées joueur partagent le budget. Compter {@code PLAYER} sans
     * {@code PLAYER_SHARED} donnerait un plafond qu'il suffit de contourner en changeant un
     * mot-clé dans la déclaration.
     */
    @Test
    void lesDeuxPorteesJoueurPartagentLeBudget() {
        VarBuckets buckets = new VarBuckets();
        UUID alice = UUID.randomUUID();

        assertTrue(buckets.put(VarScope.PLAYER, owner(alice, BOUTIQUE), "journal",
                heavy(VarQuota.MAX_PLAYER_BYTES - 1_000)));

        assertFalse(buckets.put(VarScope.PLAYER_SHARED, owner(alice, null), "prenom",
                heavy(4_000)), "le budget est celui du JOUEUR, pas celui d'une portée");
    }

    /** Et les deux blueprints d'un même joueur aussi : l'isolation n'est pas un budget. */
    @Test
    void lesBlueprintsDUnMemeJoueurPartagentLeBudget() {
        VarBuckets buckets = new VarBuckets();
        UUID alice = UUID.randomUUID();

        assertTrue(buckets.put(VarScope.PLAYER, owner(alice, BOUTIQUE), "journal",
                heavy(VarQuota.MAX_PLAYER_BYTES - 1_000)));

        assertFalse(buckets.put(VarScope.PLAYER, owner(alice, BANQUE), "journal",
                heavy(4_000)));
    }

    @Test
    void deuxJoueursNePartagentRien() {
        VarBuckets buckets = new VarBuckets();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        assertTrue(buckets.put(VarScope.PLAYER, owner(alice, BOUTIQUE), "journal",
                heavy(VarQuota.MAX_PLAYER_BYTES - 1_000)));

        assertTrue(buckets.put(VarScope.PLAYER, owner(bob, BOUTIQUE), "journal",
                heavy(4_000)), "le plafond d'Alice ne borne pas Bob");
    }

    /**
     * Les portées non-joueur ne sont pas concernées : NFR14 parle des données d'un joueur,
     * et un score de monde plafonné à 64 Ko serait une borne que rien ne demande.
     */
    @Test
    void lesPorteesMondeEtGrapheNeSontPasPlafonnees() {
        VarBuckets buckets = new VarBuckets();
        var monde = new VarOwner(BOUTIQUE, null);

        assertTrue(buckets.put(VarScope.WORLD, monde, "gros",
                heavy(VarQuota.MAX_PLAYER_BYTES * 2)));
        assertTrue(buckets.put(VarScope.GRAPH, monde, "gros",
                heavy(VarQuota.MAX_PLAYER_BYTES * 2)));
    }

    /**
     * Le cas qui rendrait le plafond piégeux : un joueur déjà au-delà — parce qu'un monde a
     * été écrit avant que la borne n'existe — doit pouvoir écrire ce qui le RÉDUIT, sinon il
     * ne peut plus rien faire du tout.
     */
    @Test
    void unJoueurAuPlafondPeutEncoreReduire() {
        VarBuckets buckets = new VarBuckets();
        UUID alice = UUID.randomUUID();
        var chez = owner(alice, BOUTIQUE);

        assertTrue(buckets.put(VarScope.PLAYER, chez, "journal",
                heavy(VarQuota.MAX_PLAYER_BYTES - 500)));

        assertTrue(buckets.put(VarScope.PLAYER, chez, "journal", "court"),
                "remplacer par plus petit est toujours permis");
        assertTrue(buckets.playerBytesOf(alice) < 1_000, "et le total suit à la baisse");
        assertTrue(buckets.put(VarScope.PLAYER, chez, "autre", heavy(4_000)),
                "la place libérée est réellement réutilisable");
    }

    @Test
    void effacerUneValeurLibereSaPlace() {
        VarBuckets buckets = new VarBuckets();
        UUID alice = UUID.randomUUID();
        var chez = owner(alice, BOUTIQUE);

        buckets.put(VarScope.PLAYER, chez, "journal", heavy(10_000));
        int avecJournal = buckets.playerBytesOf(alice);
        buckets.put(VarScope.PLAYER, chez, "journal", null);

        assertTrue(buckets.playerBytesOf(alice) < avecJournal);
    }

    // ---------------------------------------------------------------- la suppression

    @Test
    void effacerUnJoueurEmporteSesDeuxPorteesEtRienDAutre() {
        VarBuckets buckets = new VarBuckets();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        var chezAlice = owner(alice, BOUTIQUE);
        var monde = new VarOwner(BOUTIQUE, null);

        buckets.put(VarScope.PLAYER, chezAlice, "or", 100.0);
        buckets.put(VarScope.PLAYER_SHARED, owner(alice, null), "prenom", "Aliénor");
        buckets.put(VarScope.PLAYER, owner(bob, BOUTIQUE), "or", 50.0);
        buckets.put(VarScope.WORLD, monde, "score", 7.0);
        buckets.put(VarScope.GRAPH, monde, "compteur", 3.0);

        int freed = buckets.forget(alice);

        assertTrue(freed > 0, "l'effacement rend ce qu'il a libéré");
        assertEquals(0, buckets.playerBytesOf(alice));
        assertNull(buckets.of(VarScope.PLAYER, chezAlice, false));
        assertNull(buckets.of(VarScope.PLAYER_SHARED, owner(alice, null), false));

        assertNotNull(buckets.of(VarScope.PLAYER, owner(bob, BOUTIQUE), false),
                "Bob n'a rien demandé");
        assertEquals(7.0, buckets.of(VarScope.WORLD, monde, false).get("score"),
                "et le monde non plus : effacer un joueur n'efface pas la partie");
        assertEquals(3.0, buckets.of(VarScope.GRAPH, monde, false).get("compteur"));
    }

    @Test
    void effacerUnJoueurInconnuNeLeveRien() {
        assertEquals(0, new VarBuckets().forget(UUID.randomUUID()));
    }

    /**
     * Après effacement, le joueur repart d'un budget entier — sinon la suppression ne
     * servirait à rien pour celui qu'elle vise d'abord : le joueur bloqué au plafond.
     */
    @Test
    void apresEffacementLeBudgetEstEntierANouveau() {
        VarBuckets buckets = new VarBuckets();
        UUID alice = UUID.randomUUID();
        var chez = owner(alice, BOUTIQUE);

        buckets.put(VarScope.PLAYER, chez, "journal", heavy(VarQuota.MAX_PLAYER_BYTES - 500));
        buckets.forget(alice);

        assertTrue(buckets.put(VarScope.PLAYER, chez, "journal",
                heavy(VarQuota.MAX_PLAYER_BYTES - 500)));
    }

    // ------------------------------------------------------------------- le recompte

    /**
     * La désérialisation remplit les casiers directement. Sans recompte, tous les joueurs
     * repartiraient à zéro octet au démarrage et le plafond ne s'appliquerait qu'aux données
     * écrites depuis — c'est-à-dire à presque rien.
     */
    @Test
    void leRecompteRetrouveLePoidsDesDonneesChargees() {
        VarBuckets buckets = new VarBuckets();
        UUID alice = UUID.randomUUID();
        var chez = owner(alice, BOUTIQUE);

        buckets.put(VarScope.PLAYER, chez, "or", 100.0);
        buckets.put(VarScope.PLAYER_SHARED, owner(alice, null), "prenom", "Aliénor");
        int attendu = buckets.playerBytesOf(alice);

        // Ce que fait un chargement : les tables sont remplies, les totaux ne le sont pas.
        VarBuckets recharge = new VarBuckets();
        recharge.player().put(alice, new java.util.LinkedHashMap<>(buckets.player().get(alice)));
        recharge.sharedPlayer().put(alice, new java.util.LinkedHashMap<>(
                buckets.sharedPlayer().get(alice)));
        assertEquals(0, recharge.playerBytesOf(alice), "avant le recompte, il ne sait rien");

        recharge.recount();

        assertEquals(attendu, recharge.playerBytesOf(alice));
    }
}
