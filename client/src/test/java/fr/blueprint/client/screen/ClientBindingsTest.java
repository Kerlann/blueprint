package fr.blueprint.client.screen;

import fr.blueprint.core.graph.screen.ClientValue;
import fr.blueprint.core.graph.screen.ElementBinding;
import fr.blueprint.core.graph.screen.ElementKind;
import fr.blueprint.core.graph.screen.Screen;
import fr.blueprint.core.graph.screen.ScreenElement;
import fr.blueprint.core.net.ScreenBindings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Le partage du travail entre le client et le serveur.</b>
 *
 * <p>Une barre de vie liée à une variable oblige le serveur à parcourir les joueurs
 * connectés à chaque tick, lire la vie de chacun, l'écrire et envoyer un paquet — pour
 * une valeur que chaque client affiche déjà dans ses propres cœurs. À cinquante joueurs
 * c'est mille lectures et jusqu'à mille paquets par seconde, gratuitement.
 *
 * <p>Ces tests vérifient la ligne de partage elle-même : le serveur n'émet rien pour une
 * source client, le client ne calcule rien pour une variable, et le client ne réapplique
 * pas ce qui n'a pas bougé.
 */
class ClientBindingsTest {

    private static Screen hud() {
        return new Screen("fiche", true, List.of(
                ScreenElement.of("cadre", ElementKind.PANEL, 0, 0, 120, 40),
                // Ce que le serveur seul sait : un nom de personnage vient d'un graphe.
                ScreenElement.of("identite", ElementKind.LABEL, 0, 0, 120, 10)
                        .withBinding(ElementBinding.text("identite", "%s")),
                // Ce que le client sait déjà.
                ScreenElement.of("vie", ElementKind.PROGRESS, 0, 0, 120, 6)
                        .withBinding(ElementBinding.clientProgress(ClientValue.HEALTH, 0, 20)),
                ScreenElement.of("chiffres", ElementKind.LABEL, 0, 0, 120, 10)
                        .withBinding(ElementBinding.client(ClientValue.HEALTH,
                                ElementBinding.Target.TEXT, "%s / 20"))),
                Map.of());
    }

    /**
     * <b>Le serveur n'envoie rien pour une source client.</b> C'est tout l'objet de la
     * distinction : sans ce filtre, la barre de vie repartirait sur le réseau à chaque
     * rafraîchissement, et la source client ne serait qu'une étiquette décorative.
     */
    @Test
    void leServeurNEmetQuePourLesVariables() {
        AtomicInteger demandes = new AtomicInteger();
        var updates = ScreenBindings.updates(hud(), name -> {
            demandes.incrementAndGet();
            return "Jean Valjean";
        });

        assertEquals(1, updates.size(), "seule la liaison de variable doit produire un envoi");
        assertEquals("identite", updates.get(0).element());
        assertEquals(1, demandes.get(),
                "le serveur ne doit même pas CHERCHER la valeur d'une source client");
    }

    /** Et symétriquement : le client ne calcule pas les variables, qu'il ne connaît pas. */
    @Test
    void leClientNeCalculeQueLesSourcesClient() {
        var updates = ScreenBindings.updates(hud(), name -> 15.0,
                ElementBinding.Source.CLIENT);

        assertEquals(2, updates.size());
        assertTrue(updates.stream().noneMatch(u -> u.element().equals("identite")),
                "le client n'a aucune variable de blueprint à sa disposition");
        assertEquals(0.75, updates.stream()
                .filter(u -> u.element().equals("vie")).findFirst().orElseThrow().number(),
                "15 sur une plage 0..20 remplit la barre aux trois quarts");
        assertEquals("15 / 20", updates.stream()
                .filter(u -> u.element().equals("chiffres")).findFirst().orElseThrow().text());
    }

    /**
     * <b>Ce qui n'a pas bougé ne se réapplique pas.</b>
     *
     * <p>Le chemin d'application recrée un {@code Screen} à chaque texte changé. L'appeler
     * pour une vie qui n'a pas varié allouerait un écran complet par tick et par HUD, pour
     * repeindre exactement les mêmes pixels.
     */
    @Test
    void uneValeurInchangeeNeReappliqueRien() {
        HudView view = new HudView();
        view.show(hud());

        assertEquals(2, view.refreshClientBindings(name -> 20.0),
                "le premier tour applique les deux liaisons client");
        assertEquals(0, view.refreshClientBindings(name -> 20.0),
                "le deuxième tour, à valeur égale, ne doit rien appliquer");
        assertEquals(2, view.refreshClientBindings(name -> 19.0),
                "une vie qui baisse doit repasser");
    }

    /** Un HUD retiré n'emporte pas son souvenir : le rouvrir doit tout repeindre. */
    @Test
    void retirerUnHudOublieCeQuIlAffichait() {
        HudView view = new HudView();
        view.show(hud());
        view.refreshClientBindings(name -> 20.0);

        view.hide("fiche");
        view.show(hud());

        assertEquals(2, view.refreshClientBindings(name -> 20.0),
                "après un retrait, la même valeur doit se réappliquer sur l'écran neuf");
    }

    /** Le catalogue se lit avec ou sans préfixe, et refuse ce qu'il ne connaît pas. */
    @Test
    void leCatalogueRefuseCeQuIlNeConnaitPas() {
        assertEquals(ClientValue.HEALTH, ClientValue.byKey("health"));
        assertEquals(ClientValue.HEALTH, ClientValue.byKey("@health"));
        assertNull(ClientValue.byKey("@argent"),
                "un nom inconnu doit rester une variable, pas devenir une source client");
        assertNull(ClientValue.byKey(null));
        assertEquals("@health", ClientValue.HEALTH.prefixed());
    }

    /** Sans joueur, du vide — et non un zéro qui annoncerait un joueur à l'agonie. */
    @Test
    void sansJoueurLaValeurEstNulle() {
        assertNull(ClientValues.of(null, "health"));
        assertNull(ClientValues.of(null, "inconnue"));
    }
}
