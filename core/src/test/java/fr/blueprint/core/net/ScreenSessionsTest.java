package fr.blueprint.core.net;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Les écrans ouverts, côté serveur (story 10.3, AC1 et AC5). C'est cette table qui
 * décidera si un clic reçu est recevable (FR52) : le serveur ne croit jamais le client
 * sur l'écran qu'il prétend avoir ouvert.
 */
class ScreenSessionsTest {

    private static final Identifier BOUTIQUE =
            Identifier.fromNamespaceAndPath("test", "boutique");
    private static final Identifier BANQUE = Identifier.fromNamespaceAndPath("test", "banque");

    private ScreenSessions sessions;
    private UUID alice;
    private UUID bob;

    @BeforeEach
    void setUp() {
        sessions = new ScreenSessions();
        alice = UUID.randomUUID();
        bob = UUID.randomUUID();
    }

    @Test
    void unSeulEcranALaFoisParJoueur() {
        sessions.opened(alice, BOUTIQUE, "achat");
        sessions.opened(alice, BANQUE, "depot");

        assertEquals(1, sessions.size(), "le second remplace, il n'empile pas");
        assertEquals(BANQUE, sessions.of(alice).blueprint());
        assertEquals("depot", sessions.of(alice).screen());
    }

    @Test
    void lesJoueursSontIndependants() {
        sessions.opened(alice, BOUTIQUE, "achat");
        sessions.opened(bob, BANQUE, "depot");

        assertEquals(2, sessions.size());
        sessions.closed(alice);
        assertNull(sessions.of(alice));
        assertEquals("depot", sessions.of(bob).screen());
    }

    /**
     * <b>Le test qui compte.</b> Un client modifié annonce ce qu'il veut. Sans cette
     * vérification, il déclencherait le bouton d'un menu qu'il n'a jamais vu.
     */
    @Test
    void seulLEcranREELLEMENTouvertEstReconnu() {
        sessions.opened(alice, BOUTIQUE, "achat");

        assertTrue(sessions.hasOpen(alice, BOUTIQUE, "achat"));
        assertFalse(sessions.hasOpen(alice, BOUTIQUE, "admin"),
                "un autre écran du même blueprint");
        assertFalse(sessions.hasOpen(alice, BANQUE, "achat"), "un autre blueprint");
        assertFalse(sessions.hasOpen(bob, BOUTIQUE, "achat"), "un autre joueur");
        assertFalse(sessions.hasOpen(alice, BOUTIQUE, "achat".toUpperCase(java.util.Locale.ROOT)),
                "le nom est comparé exactement");
    }

    @Test
    void fermerDitSiQuelqueChoseEtaitOuvert() {
        assertFalse(sessions.closed(alice), "rien à fermer");
        sessions.opened(alice, BOUTIQUE, "achat");
        assertTrue(sessions.closed(alice));
        assertFalse(sessions.closed(alice), "et pas deux fois");
    }

    /**
     * Un blueprint désactivé referme ses menus. Les laisser ouverts donnerait un écran
     * qui refuse chaque clic sans que le joueur comprenne pourquoi.
     */
    @Test
    void desactiverUnBlueprintRefermeSesEcransPartout() {
        sessions.opened(alice, BOUTIQUE, "achat");
        sessions.opened(bob, BOUTIQUE, "admin");
        UUID carol = UUID.randomUUID();
        sessions.opened(carol, BANQUE, "depot");

        List<UUID> affected = sessions.closeAllOf(BOUTIQUE);

        assertEquals(2, affected.size());
        assertTrue(affected.containsAll(List.of(alice, bob)));
        assertNull(sessions.of(alice));
        assertNull(sessions.of(bob));
        assertEquals("depot", sessions.of(carol).screen(),
                "les autres blueprints ne sont pas touchés");
    }

    @Test
    void unBlueprintSansEcranOuvertNeDerangePersonne() {
        sessions.opened(alice, BOUTIQUE, "achat");
        assertTrue(sessions.closeAllOf(BANQUE).isEmpty());
        assertEquals(1, sessions.size());
    }

    /** Un joueur parti ne laisse pas d'écran fantôme (AC5). */
    @Test
    void unJoueurPartiNeLaissePasDeTrace() {
        sessions.opened(alice, BOUTIQUE, "achat");
        sessions.forget(alice);

        assertEquals(0, sessions.size());
        assertFalse(sessions.hasOpen(alice, BOUTIQUE, "achat"));
    }
}
