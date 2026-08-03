package fr.blueprint.core.net;

import fr.blueprint.core.graph.screen.ScreenText;
import fr.blueprint.core.graph.screen.ScreenUpdate;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Les trois gardes qui rendent tenable un graphe écrit naïvement (story 10.4) : le
 * regroupement par tick, la comparaison avec ce que le client affiche, et le numéro
 * d'instance. Sans elles, « à chaque tick, affiche l'or » coûterait vingt paquets par
 * seconde et par joueur, dont dix-neuf identiques.
 */
class ScreenUpdateQueueTest {

    private static final Identifier BOUTIQUE =
            Identifier.fromNamespaceAndPath("test", "boutique");

    private ScreenSessions sessions;
    private UUID alice;

    @BeforeEach
    void setUp() {
        sessions = new ScreenSessions();
        alice = UUID.randomUUID();
        sessions.opened(alice, BOUTIQUE, "achat");
    }

    private static ScreenUpdate or(String value) {
        return ScreenUpdate.text("achat", "or", ScreenText.literal(value));
    }

    @Test
    void lesModificationsDUnTickPartentEnsemble() {
        sessions.queue(alice, or("100"));
        sessions.queue(alice, ScreenUpdate.text("achat", "niveau", ScreenText.literal("3")));
        sessions.queue(alice, ScreenUpdate.progress("achat", "xp", 0.5));

        assertEquals(3, sessions.drain(alice).size(), "un envoi, pas trois");
        assertTrue(sessions.drain(alice).isEmpty(), "et la file est vidée");
    }

    /** Deux écritures de même nature sur le même élément n'en font qu'une : la dernière. */
    @Test
    void deuxEcrituresDuMemeChampNEnFontQuUne() {
        sessions.queue(alice, or("100"));
        sessions.queue(alice, or("150"));

        List<ScreenUpdate> sent = sessions.drain(alice);
        assertEquals(1, sent.size());
        assertEquals("150", sent.getFirst().text());
    }

    /**
     * <b>Le test qui compte.</b> Un blueprint sur {@code server_tick} qui réécrit l'or
     * à chaque passage enverrait vingt paquets par seconde et par joueur, dont
     * dix-neuf strictement identiques.
     */
    @Test
    void reecrireLaMemeValeurNEnvoieRien() {
        sessions.queue(alice, or("100"));
        assertEquals(1, sessions.drain(alice).size());

        for (int tick = 0; tick < 20; tick++) {
            assertFalse(sessions.queue(alice, or("100")), "tick " + tick);
        }
        assertTrue(sessions.drain(alice).isEmpty(), "vingt ticks, zéro paquet");

        assertTrue(sessions.queue(alice, or("101")), "et la vraie différence part");
        assertEquals(1, sessions.drain(alice).size());
    }

    /**
     * Le cas subtil : écrire A puis B dans le même tick alors que le client affiche
     * déjà B doit <b>retirer</b> A de la file. S'abstenir seulement d'ajouter B
     * laisserait partir A, et l'écran clignoterait vers une valeur périmée.
     */
    @Test
    void revenirALaValeurAffichEeAnnuleLaModificationEnAttente() {
        sessions.queue(alice, or("100"));
        sessions.drain(alice);

        assertTrue(sessions.queue(alice, or("999")));
        assertFalse(sessions.queue(alice, or("100")), "retour à la valeur affichée");
        assertTrue(sessions.drain(alice).isEmpty(), "rien ne part : « 999 » a été retiré");
    }

    /** Un écran neuf n'affiche rien : le premier rafraîchissement doit partir. */
    @Test
    void rouvrirUnEcranOublieCeQueLAncienAffichait() {
        sessions.queue(alice, or("100"));
        sessions.drain(alice);
        assertFalse(sessions.queue(alice, or("100")));

        sessions.opened(alice, BOUTIQUE, "achat");
        assertTrue(sessions.queue(alice, or("100")),
                "le nouvel écran n'a jamais reçu cette valeur");
    }

    @Test
    void leNumeroDInstanceMonteAChaqueOuverture() {
        int first = sessions.of(alice).instance();
        int second = sessions.opened(alice, BOUTIQUE, "admin");

        assertTrue(second > first, first + " → " + second);
        assertEquals(second, sessions.of(alice).instance());
    }

    @Test
    void sansEcranOuvertRienNeSEmpile() {
        sessions.closed(alice);
        assertFalse(sessions.queue(alice, or("100")));
        assertTrue(sessions.drain(alice).isEmpty());
        assertTrue(sessions.pendingPlayers().isEmpty());
    }

    @Test
    void fermerJetteCeQuiEtaitEnAttente() {
        sessions.queue(alice, or("100"));
        sessions.closed(alice);
        assertTrue(sessions.drain(alice).isEmpty());
    }

    /** La variante « à tous les spectateurs » vise ceux qui regardent le MÊME écran. */
    @Test
    void lesSpectateursDUnEcranSeTrouventEnUnParcours() {
        UUID bob = UUID.randomUUID();
        UUID carol = UUID.randomUUID();
        sessions.opened(bob, BOUTIQUE, "achat");
        sessions.opened(carol, BOUTIQUE, "admin");

        List<UUID> viewers = sessions.viewersOf(BOUTIQUE, "achat");
        assertEquals(2, viewers.size());
        assertTrue(viewers.containsAll(List.of(alice, bob)));
        assertTrue(sessions.viewersOf(
                Identifier.fromNamespaceAndPath("test", "autre"), "achat").isEmpty());
    }

    @Test
    void seulsLesJoueursAvecDuRetardSontParcourus() {
        UUID bob = UUID.randomUUID();
        sessions.opened(bob, BOUTIQUE, "achat");
        sessions.queue(alice, or("100"));

        assertEquals(List.of(alice), sessions.pendingPlayers());
    }

    /** Une barre hors de [0, 1] se dessinerait au-delà de son cadre : elle est bornée. */
    @Test
    void uneBarreEstBorneeALaSource() {
        assertEquals(1.0, ScreenUpdate.progress("achat", "xp", 5).number(), 1e-9);
        assertEquals(0.0, ScreenUpdate.progress("achat", "xp", -2).number(), 1e-9);
        assertEquals(0.0, ScreenUpdate.progress("achat", "xp", Double.NaN).number(), 1e-9);
        assertEquals(0.5, ScreenUpdate.progress("achat", "xp", 0.5).number(), 1e-9);
    }

    /** Deux natures sur le même élément ne se marchent pas dessus. */
    @Test
    void deuxNaturesSurLeMemeElementCoexistent() {
        sessions.queue(alice, or("100"));
        sessions.queue(alice, ScreenUpdate.visible("achat", "or", false));

        assertEquals(2, sessions.drain(alice).size());
    }
}
