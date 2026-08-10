package fr.blueprint.core.net;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.GraphLimits;
import fr.blueprint.core.graph.NodeTypeLookup;
import fr.blueprint.core.graph.VarScope;
import fr.blueprint.core.graph.Variable;
import fr.blueprint.core.storage.VarStorage;
import fr.blueprint.core.vm.ReplicatedNames;
import fr.blueprint.core.vm.VarDirty;
import fr.blueprint.core.vm.VarOwner;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Qui reçoit quoi, et ce qu'un joueur trouve en arrivant (épic 21, story 21.4).
 *
 * <p>L'envoi lui-même demande un serveur et ne se teste pas sans lui. Ce qui se teste ici est
 * ce qui décide : le <b>choix des destinataires</b>, qui est la frontière de sécurité de tout
 * l'épic, et la <b>composition de l'instantané d'arrivée</b>, sans laquelle un écran lié à une
 * valeur stable n'afficherait jamais rien.
 */
class VarReplicationTest {

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

    // -------------------------------------------- la frontière : qui reçoit une valeur

    /**
     * <b>Le test le plus important de l'épic.</b> Une valeur qui appartient à un joueur ne part
     * que chez lui. Envoyer à tous la réputation, le solde ou le prénom de chacun serait une
     * divulgation que rien dans le modèle actuel ne produit et qu'une réplication naïve
     * introduirait d'un trait.
     */
    @Test
    void uneValeurDeJoueurNeVaQuAuSien() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        List<UUID> connectes = List.of(alice, bob);

        var marque = new VarDirty.Mark(VarScope.PLAYER, alice, BOUTIQUE, "or");

        assertEquals(List.of(alice), VarReplication.recipientsOf(marque, connectes));
        assertFalse(VarReplication.recipientsOf(marque, connectes).contains(bob),
                "Bob n'a rien à savoir de l'or d'Alice");
    }

    @Test
    void unePorteeJoueurPartageeSuitLaMemeRegle() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        var marque = new VarDirty.Mark(VarScope.PLAYER_SHARED, alice, null, "prenom");

        assertEquals(List.of(alice),
                VarReplication.recipientsOf(marque, List.of(alice, bob)));
    }

    @Test
    void uneValeurDuMondeVaATousLesConnectes() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        List<UUID> connectes = List.of(alice, bob);

        var marque = new VarDirty.Mark(VarScope.WORLD, null, null, "or");

        assertEquals(connectes, VarReplication.recipientsOf(marque, connectes));
    }

    @Test
    void uneValeurDeGrapheVaATousLesConnectes() {
        UUID alice = UUID.randomUUID();
        var marque = new VarDirty.Mark(VarScope.GRAPH, null, BOUTIQUE, "score");

        assertEquals(List.of(alice), VarReplication.recipientsOf(marque, List.of(alice)));
    }

    @Test
    void sansPersonneDeConnecteRienNePart() {
        var marque = new VarDirty.Mark(VarScope.WORLD, null, null, "or");

        assertTrue(VarReplication.recipientsOf(marque, List.of()).isEmpty());
    }

    // ------------------------------------------------- l'instantané d'arrivée

    /**
     * Le carnet des marques répond à « qu'est-ce qui a changé » et jamais à « qu'est-ce qui
     * existe ». Sans instantané, un écran lié à un prénom choisi la semaine dernière
     * n'afficherait rien jusqu'à ce que quelqu'un le change.
     */
    @Test
    void unJoueurQuiArriveTrouveLesValeursDejaEcrites() {
        VarStorage storage = new VarStorage();
        storage.replicating(ReplicatedNames.of(List.of(
                declaring(BOUTIQUE, "prenom", VarScope.PLAYER_SHARED))));
        UUID alice = UUID.randomUUID();
        storage.set(VarScope.PLAYER_SHARED, new VarOwner(BOUTIQUE, alice), "prenom", "Aliénor");
        storage.dirty().drain();   // le tick a passé, le carnet est vide

        var marks = storage.replicatedMarks(alice);

        assertEquals(1, marks.size());
        assertEquals("prenom", marks.get(0).name());
        assertEquals(VarScope.PLAYER_SHARED, marks.get(0).scope());
    }

    /** L'instantané d'un joueur ne contient <b>jamais</b> les valeurs d'un autre. */
    @Test
    void lInstantaneDUnJoueurIgnoreCeluiDesAutres() {
        VarStorage storage = new VarStorage();
        storage.replicating(ReplicatedNames.of(List.of(
                declaring(BOUTIQUE, "or", VarScope.PLAYER))));
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        storage.set(VarScope.PLAYER, new VarOwner(BOUTIQUE, alice), "or", 100.0);
        storage.set(VarScope.PLAYER, new VarOwner(BOUTIQUE, bob), "or", 999.0);

        var pourAlice = storage.replicatedMarks(alice);

        assertEquals(1, pourAlice.size());
        assertEquals(alice, pourAlice.get(0).player(), "sa valeur, et celle de personne d'autre");
    }

    @Test
    void lInstantaneContientLesPorteesPartageesEtLesSiennes() {
        VarStorage storage = new VarStorage();
        storage.replicating(ReplicatedNames.of(List.of(
                declaring(BOUTIQUE, "or", VarScope.PLAYER),
                declaring(BANQUE, "taux", VarScope.WORLD),
                declaring(BOUTIQUE, "score", VarScope.GRAPH))));
        UUID alice = UUID.randomUUID();
        storage.set(VarScope.PLAYER, new VarOwner(BOUTIQUE, alice), "or", 100.0);
        storage.set(VarScope.WORLD, new VarOwner(BANQUE, null), "taux", 1.5);
        storage.set(VarScope.GRAPH, new VarOwner(BOUTIQUE, null), "score", 7.0);

        var marks = storage.replicatedMarks(alice);

        assertEquals(3, marks.size());
        assertTrue(marks.stream().anyMatch(m -> m.scope() == VarScope.WORLD));
        assertTrue(marks.stream().anyMatch(m -> m.scope() == VarScope.GRAPH));
        assertTrue(marks.stream().anyMatch(m -> m.scope() == VarScope.PLAYER));
    }

    @Test
    void lInstantaneIgnoreLesVariablesNonRepliquees() {
        VarStorage storage = new VarStorage();
        storage.replicating(ReplicatedNames.of(List.of(
                declaring(BOUTIQUE, "or", VarScope.WORLD))));
        storage.set(VarScope.WORLD, new VarOwner(BOUTIQUE, null), "or", 1.0);
        storage.set(VarScope.WORLD, new VarOwner(BOUTIQUE, null), "argent", 2.0);

        var marks = storage.replicatedMarks(UUID.randomUUID());

        assertEquals(1, marks.size());
        assertEquals("or", marks.get(0).name());
    }

    @Test
    void sansReplicationLInstantaneEstVide() {
        VarStorage storage = new VarStorage();
        storage.set(VarScope.WORLD, new VarOwner(BOUTIQUE, null), "or", 1.0);

        assertTrue(storage.replicatedMarks(UUID.randomUUID()).isEmpty());
    }

    // -------------------------------------- ne marquer que ce qui change vraiment

    /**
     * « À chaque tick, écris l'or » ne doit rien envoyer si l'or ne bouge pas. C'est la leçon de
     * {@code ScreenSessions}, appliquée à la source plutôt qu'en aval : comparer ici est exact,
     * alors qu'une empreinte gardée par spectateur serait approchée — et serait la seconde table
     * que la story 10.7 a écrite puis retirée.
     */
    @Test
    void reecrireLaMemeValeurNeMarqueRien() {
        VarStorage storage = new VarStorage();
        storage.replicating(ReplicatedNames.of(List.of(
                declaring(BOUTIQUE, "or", VarScope.WORLD))));
        var monde = new VarOwner(BOUTIQUE, null);

        storage.set(VarScope.WORLD, monde, "or", 100.0);
        assertEquals(1, storage.dirty().size());
        storage.dirty().drain();

        for (int i = 0; i < 20; i++) {
            storage.set(VarScope.WORLD, monde, "or", 100.0);
        }

        assertEquals(0, storage.dirty().size(),
                "vingt écritures de la même valeur : aucun changement, aucun envoi");
    }

    @Test
    void changerLaValeurMarqueANouveau() {
        VarStorage storage = new VarStorage();
        storage.replicating(ReplicatedNames.of(List.of(
                declaring(BOUTIQUE, "or", VarScope.WORLD))));
        var monde = new VarOwner(BOUTIQUE, null);

        storage.set(VarScope.WORLD, monde, "or", 100.0);
        storage.dirty().drain();
        storage.set(VarScope.WORLD, monde, "or", 101.0);

        assertEquals(1, storage.dirty().size());
    }

    /** Effacer une valeur est un changement : le client doit vider sa case. */
    @Test
    void effacerUneValeurEstUnChangement() {
        VarStorage storage = new VarStorage();
        storage.replicating(ReplicatedNames.of(List.of(
                declaring(BOUTIQUE, "or", VarScope.WORLD))));
        var monde = new VarOwner(BOUTIQUE, null);

        storage.set(VarScope.WORLD, monde, "or", 100.0);
        storage.dirty().drain();
        storage.set(VarScope.WORLD, monde, "or", null);

        assertEquals(1, storage.dirty().size());
    }
}
