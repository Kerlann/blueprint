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

    // ------------------------------------------------------------- HUD (10.9)

    // ------------------------------------ idempotence de l'affichage (épic 17b/19)

    private static fr.blueprint.core.graph.screen.Screen hudScreen(String name) {
        return new fr.blueprint.core.graph.screen.Screen(name, true, List.of(
                fr.blueprint.core.graph.screen.ScreenElement.of(
                        "titre", fr.blueprint.core.graph.screen.ElementKind.LABEL,
                        0, 0, 60, 10)));
    }

    /**
     * <b>Le test qui compte.</b> Réafficher le MÊME écran ne demande pas de le réenvoyer.
     *
     * <p>{@code gui/show_hud} réencodait et regzippait l'écran entier à chaque appel. Un
     * graphe branché sur le tick le fait vingt fois par seconde et par joueur, pour
     * produire des octets que le client possède déjà.
     */
    @Test
    void reafficherLeMemeEcranNeRedemandePasDEnvoi() {
        var screen = hudScreen("mana");
        assertTrue(sessions.showHud(alice, BOUTIQUE, "mana", screen),
                "premier affichage : la description doit partir");
        assertFalse(sessions.showHud(alice, BOUTIQUE, "mana", screen),
                "même écran, même version : rien à renvoyer");
        assertFalse(sessions.showHud(alice, BOUTIQUE, "mana", screen));
        assertEquals(1, sessions.hudsOf(alice).size());
    }

    /**
     * <b>Et le piège que l'idempotence naïve aurait ouvert.</b> Un enregistrement du
     * blueprint produit une NOUVELLE instance d'écran : la description doit repartir.
     *
     * <p>Se contenter de « ce HUD était-il déjà affiché ? » aurait figé le HUD à sa
     * version d'ouverture, sans aucun symptôme — {@code refreshScreensOf} ne parcourt que
     * les écrans <b>modaux</b> et ne rafraîchit aucun HUD après un enregistrement.
     */
    @Test
    void unEcranModifieRepartMemeSiLeHudEstDejaAffiche() {
        assertTrue(sessions.showHud(alice, BOUTIQUE, "mana", hudScreen("mana")));
        assertTrue(sessions.showHud(alice, BOUTIQUE, "mana", hudScreen("mana")),
                "nouvelle instance = écran réenregistré : la description doit repartir");
    }

    /** Masquer puis réafficher renvoie : le client a jeté ce qu'il avait. */
    @Test
    void masquerPuisReafficherRenvoieLaDescription() {
        var screen = hudScreen("mana");
        assertTrue(sessions.showHud(alice, BOUTIQUE, "mana", screen));
        assertTrue(sessions.hideHud(alice, "mana"));
        assertTrue(sessions.showHud(alice, BOUTIQUE, "mana", screen),
                "le HUD a été masqué : sa description doit repartir");
    }

    /** Un joueur oublié repart de zéro — sa reconnexion doit tout recevoir. */
    @Test
    void unJoueurOublieRecoitANouveau() {
        var screen = hudScreen("mana");
        assertTrue(sessions.showHud(alice, BOUTIQUE, "mana", screen));
        sessions.forget(alice);
        assertTrue(sessions.showHud(alice, BOUTIQUE, "mana", screen),
                "le joueur a été oublié : sa description doit repartir");
    }

    /** Le retrait par blueprint efface aussi la version mémorisée. */
    @Test
    void leRetraitParBlueprintEffaceLaVersionMemorisee() {
        var screen = hudScreen("mana");
        assertTrue(sessions.showHud(alice, BOUTIQUE, "mana", screen));
        assertEquals(List.of("mana"), sessions.takeHudsOf(BOUTIQUE).get(alice));
        assertTrue(sessions.showHud(alice, BOUTIQUE, "mana", screen),
                "le HUD a été retiré avec son blueprint : sa description doit repartir");
    }

    /**
     * PLUSIEURS HUD coexistent, contrairement au modal. Les interdire obligerait à
     * réunir la mana et le suivi de quête dans un même document.
     */
    @Test
    void plusieursHudCoexistentAvecUnModal() {
        sessions.opened(alice, BOUTIQUE, "achat");
        assertTrue(sessions.showHud(alice, BOUTIQUE, "mana"));
        assertTrue(sessions.showHud(alice, BANQUE, "quete"));

        assertEquals(2, sessions.hudsOf(alice).size());
        assertTrue(sessions.shows(alice, "achat"), "le modal est toujours là");
        assertTrue(sessions.shows(alice, "mana"));
        assertFalse(sessions.shows(alice, "inconnu"));
    }

    /**
     * <b>Le test qui compte.</b> Un HUD n'a pas d'Échap : en laisser un qui pointe un
     * blueprint désactivé le rendrait indélogeable autrement qu'à la touche de
     * masquage — et le joueur n'aurait aucune raison de deviner laquelle.
     */
    @Test
    void desactiverUnBlueprintRetireSesHudEtEuxSeuls() {
        sessions.showHud(alice, BOUTIQUE, "mana");
        sessions.showHud(alice, BANQUE, "quete");
        sessions.showHud(bob, BOUTIQUE, "mana");

        var retires = sessions.takeHudsOf(BOUTIQUE);
        assertEquals(2, retires.size(), "les deux joueurs sont concernés");
        assertEquals(List.of("mana"), retires.get(alice));
        assertEquals(List.of("quete"), List.copyOf(sessions.hudsOf(alice)),
                "le HUD de l'autre blueprint reste");
        assertTrue(sessions.hudsOf(bob).isEmpty());
    }

    @Test
    void masquerToutEstLaGardeDeSecurite() {
        sessions.showHud(alice, BOUTIQUE, "mana");
        sessions.showHud(alice, BOUTIQUE, "quete");

        assertTrue(sessions.hideAllHuds(alice));
        assertTrue(sessions.hudsOf(alice).isEmpty());
        assertFalse(sessions.hideAllHuds(alice), "et pas deux fois");
    }

    @Test
    void uneModificationVisantUnHudEstAcceptee() {
        sessions.showHud(alice, BOUTIQUE, "mana");
        assertTrue(sessions.queue(alice, fr.blueprint.core.graph.screen.ScreenUpdate.text(
                "mana", "valeur", fr.blueprint.core.graph.screen.ScreenText.literal("40"))),
                "sans modal ouvert, un HUD reçoit quand même ses mises à jour");
    }

    @Test
    void lesSpectateursIncluentLesHud() {
        sessions.showHud(alice, BOUTIQUE, "tableau");
        sessions.opened(bob, BOUTIQUE, "tableau");

        var viewers = sessions.viewersOf(BOUTIQUE, "tableau");
        assertEquals(2, viewers.size(), "un HUD compte comme un spectateur");
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
