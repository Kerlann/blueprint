package fr.blueprint.core.vm;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.GraphLimits;
import fr.blueprint.core.graph.NodeTypeLookup;
import fr.blueprint.core.graph.VarScope;
import fr.blueprint.core.graph.VarValueNbt;
import fr.blueprint.core.graph.Variable;
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
 * Les quatre défauts que la relecture de l'épic 21 a trouvés, et que rien ne gardait.
 *
 * <p>Ils partagent une forme : chacun demande une <b>combinaison</b> que mes tests d'origine
 * n'avaient pas assemblée — deux portées partagées portant le même nom, une purge suivie d'un
 * regard sur l'écran, une imbrication plus profonde que huit. Écrits l'un après l'autre, ils
 * sont tous évidents ; c'est de ne pas les avoir croisés qui les a laissés passer.
 */
class ReplicationReviewFixesTest {

    private static final Identifier BOUTIQUE =
            Identifier.fromNamespaceAndPath("test", "boutique");
    private static final Identifier BANQUE = Identifier.fromNamespaceAndPath("test", "banque");
    private static final NodeTypeLookup LOOKUP = typeId -> null;

    private static Blueprint declaring(Identifier id, String name, VarScope scope) {
        Blueprint bp = new Blueprint(id);
        assertTrue(new EditOperation.AddVariable(new Variable(name, PinTypes.DOUBLE,
                LiteralValue.of(PinTypes.DOUBLE, 0.0), scope, false))
                .apply(bp, LOOKUP, GraphLimits.DEFAULT).applied());
        assertTrue(new EditOperation.SetReplicated(name, true)
                .apply(bp, LOOKUP, GraphLimits.DEFAULT).applied());
        return bp;
    }

    // ------------------------------------- 2. les deux portées partagées sont distinctes

    /**
     * {@code WORLD} et {@code PLAYER_SHARED} sont deux casiers : le même nom dans les deux est
     * <b>deux valeurs</b>. Elles vivaient dans une table indexée par le seul nom, si bien
     * qu'une écriture du monde était envoyée aux clients sous la clé du graphe qui déclarait la
     * variable <i>partagée par joueur</i> — le solde du monde écrasant chez le client celui du
     * joueur, sous la même clé.
     */
    @Test
    void lesDeuxPorteesPartageesNeSeConfondentPas() {
        var names = ReplicatedNames.of(List.of(
                declaring(BOUTIQUE, "solde", VarScope.PLAYER_SHARED),
                declaring(BANQUE, "solde", VarScope.WORLD)));

        assertTrue(names.covers(VarScope.PLAYER_SHARED, BOUTIQUE, "solde"));
        assertTrue(names.covers(VarScope.WORLD, BANQUE, "solde"));

        assertEquals(java.util.Set.of(BOUTIQUE),
                names.declaringBlueprints(VarScope.PLAYER_SHARED, null, "solde"),
                "la valeur partagée par joueur ne va QUE chez qui la déclare ainsi");
        assertEquals(java.util.Set.of(BANQUE),
                names.declaringBlueprints(VarScope.WORLD, null, "solde"),
                "et la valeur du monde de même — sinon l'une écrase l'autre chez le client");
    }

    /** Une portée partagée non déclarée reste non couverte, même si l'autre l'est. */
    @Test
    void unePorteePartageeNonDeclareeResteNonCouverte() {
        var names = ReplicatedNames.of(List.of(
                declaring(BOUTIQUE, "solde", VarScope.WORLD)));

        assertTrue(names.covers(VarScope.WORLD, BOUTIQUE, "solde"));
        assertFalse(names.covers(VarScope.PLAYER_SHARED, BOUTIQUE, "solde"),
                "personne n'a déclaré « solde » en portée joueur partagée");
    }

    // ------------------------------------------ 3. la purge prévient les clients

    /**
     * Effacer les données d'un joueur doit <b>marquer</b> ce qui était répliqué, sinon son
     * client garde sa dernière valeur pour toujours : elle ne sera jamais réécrite — elle
     * n'existe plus — donc rien ne viendra la corriger. « Les données d'un joueur sont
     * supprimables » (NFR14) n'était tenu qu'à moitié : effacé au serveur, toujours à l'écran.
     */
    @Test
    void laPurgePrevientLesClients() {
        var storage = new fr.blueprint.core.storage.VarStorage();
        storage.replicating(ReplicatedNames.of(List.of(
                declaring(BOUTIQUE, "or", VarScope.PLAYER))));
        UUID alice = UUID.randomUUID();
        storage.set(VarScope.PLAYER, new VarOwner(BOUTIQUE, alice), "or", 100.0);
        storage.dirty().drain();

        storage.forget(alice);

        var marks = storage.dirty().drain();
        assertEquals(1, marks.size(),
                "la purge doit produire une marque, sans quoi l'écran garde l'ancienne valeur");
        assertEquals("or", marks.get(0).name());
        assertEquals(alice, marks.get(0).player());
        assertNull(storage.get(VarScope.PLAYER, new VarOwner(BOUTIQUE, alice), "or"),
                "et la valeur est bien partie du serveur : la marque partira donc vide");
    }

    @Test
    void laPurgeDUnJoueurSansValeurRepliqueeNeMarqueRien() {
        var storage = new fr.blueprint.core.storage.VarStorage();
        storage.set(VarScope.PLAYER, new VarOwner(BOUTIQUE, UUID.randomUUID()), "or", 1.0);

        storage.forget(UUID.randomUUID());

        assertTrue(storage.dirty().isEmpty());
    }

    // --------------------------- 4. l'encodeur borne sa récursion, le quota la suit

    /** Une imbrication que {@code list/add} peut construire, un cran par appel. */
    private static Object nested(int depth) {
        Object value = "feuille";
        for (int i = 0; i < depth; i++) {
            value = List.of(value);
        }
        return value;
    }

    /**
     * L'encodeur tournait sans borne de profondeur. Sur le disque, cela ne s'exécutait qu'à la
     * sauvegarde du monde ; depuis l'épic 21 il tourne <b>dans le tick</b>, et un
     * {@code StackOverflowError} y emporterait le tick et la sauvegarde — ce que NFR4 interdit.
     */
    @Test
    void lEncodeurRefuseUneImbricationDeraisonnable() {
        assertNotNull(VarValueNbt.encode(nested(4)), "une imbrication ordinaire passe");
        assertNull(VarValueNbt.encode(nested(64)),
                "au-delà de sa borne, l'encodeur refuse au lieu de creuser");
    }

    /** Une profondeur énorme ne fait ni lever ni déborder la pile — elle rend null. */
    @Test
    void uneImbricationEnormeNeFaitPasDeborderLaPile() {
        assertNull(VarValueNbt.encode(nested(50_000)));
    }

    /**
     * Le quota bornait sa mesure à huit niveaux quand l'encodeur n'en avait aucune : tout ce
     * qui était plus profond était facturé un forfait alors qu'il se persistait <b>en
     * entier</b>. Les deux bornes doivent coïncider, sinon le plafond de 64 Ko se contourne en
     * imbriquant.
     */
    @Test
    void leQuotaEtLEncodeurSAccordentSurLaProfondeur() {
        // À une profondeur que l'encodeur accepte, la mesure doit encore compter le contenu
        // et non un forfait : une longue chaîne au fond doit peser.
        List<Object> profond = new ArrayList<>();
        profond.add("x".repeat(4_000));
        Object value = profond;
        for (int i = 0; i < 12; i++) {
            value = List.of(value);
        }

        assertNotNull(VarValueNbt.encode(value), "l'encodeur l'accepte à cette profondeur");
        assertTrue(VarQuota.sizeOf(value) > 4_000,
                "donc le quota doit le compter, pas le forfaitiser : "
                        + VarQuota.sizeOf(value) + " octets pour 4 000 caractères");
    }
}
