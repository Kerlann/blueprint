package fr.blueprint.client.screen;

import fr.blueprint.core.graph.screen.ElementKind;
import fr.blueprint.core.graph.screen.Screen;
import fr.blueprint.core.graph.screen.ScreenElement;
import fr.blueprint.core.graph.screen.ScreenText;
import fr.blueprint.core.graph.screen.ScreenUpdate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Les HUD affichés chez un client (story 10.9).
 *
 * <p>Le HUD s'affichait comme un écran modal : le joueur se retrouvait figé sur place
 * dès qu'un graphe voulait lui montrer son or. Ce n'était pas un défaut de rendu mais
 * une confusion de nature — un {@code Screen} de Minecraft capte la souris par
 * construction.
 */
class HudViewTest {

    private static Screen hud(String name, String element, String text) {
        return new Screen(name, true, List.of(
                ScreenElement.of(element, ElementKind.LABEL, 0, 0, 80, 10)
                        .withText(ScreenText.literal(text))));
    }

    private HudView view;

    @BeforeEach
    void setUp() {
        view = new HudView();
    }

    /**
     * <b>Le test qui compte.</b> Deux HUD coexistent, contrairement aux écrans modaux.
     * En interdire plusieurs obligerait à réunir la mana et le suivi de quête dans un
     * même document, alors qu'ils n'ont rien à voir.
     */
    @Test
    void plusieursHudCoexistent() {
        view.show(hud("mana", "valeur", "40"));
        view.show(hud("quete", "titre", "Trouver la clé"));

        assertEquals(2, view.size());
        assertEquals(List.of("mana", "quete"),
                view.visible().stream().map(Screen::name).toList(),
                "dans l'ordre d'apparition — c'est l'ordre de dessin");
    }

    @Test
    void reafficherUnHudRemplaceSaDescription() {
        view.show(hud("mana", "valeur", "40"));
        view.show(hud("mana", "valeur", "80"));

        assertEquals(1, view.size(), "pas un doublon");
        assertEquals("80", view.get("mana").element("valeur").text().value());
    }

    @Test
    void masquerUnHudNeTouchePasLesAutres() {
        view.show(hud("mana", "valeur", "40"));
        view.show(hud("quete", "titre", "Trouver la clé"));

        view.hide("mana");
        assertNull(view.get("mana"));
        assertNotNull(view.get("quete"));
    }

    /**
     * La bascule est une <b>garde de sécurité</b>. Un écran modal a toujours Échap ;
     * un HUD n'a rien. Un graphe fautif affichant un panneau opaque plein écran
     * laisserait le joueur sans aucun recours.
     */
    @Test
    void laBasculeMasqueToutSansRienPerdre() {
        view.show(hud("mana", "valeur", "40"));
        view.show(hud("quete", "titre", "Trouver la clé"));

        view.toggleHidden();
        assertTrue(view.hidden());
        assertTrue(view.visible().isEmpty(), "plus rien ne se dessine");
        assertEquals(2, view.size(), "mais rien n'est perdu");

        view.toggleHidden();
        assertEquals(2, view.visible().size(), "tout revient");
    }

    @Test
    void uneModificationVaAuHudQuElleNomme() {
        view.show(hud("mana", "valeur", "40"));
        view.show(hud("quete", "titre", "Trouver la clé"));

        assertEquals(1, view.apply(List.of(
                ScreenUpdate.text("mana", "valeur", ScreenText.literal("10")))));
        assertEquals("10", view.get("mana").element("valeur").text().value());
        assertEquals("Trouver la clé", view.get("quete").element("titre").text().value(),
                "l'autre HUD n'a pas bougé");
    }

    /** Ce qui vise un écran non affiché est ignoré : le client n'invente pas de cible. */
    @Test
    void uneModificationSansDestinataireEstIgnoree() {
        view.show(hud("mana", "valeur", "40"));

        assertEquals(0, view.apply(List.of(
                ScreenUpdate.text("boutique", "or", ScreenText.literal("100")),
                ScreenUpdate.text("mana", "inexistant", ScreenText.literal("x")))));
        assertEquals("40", view.get("mana").element("valeur").text().value());
    }

    @Test
    void laValeurDUneBarreVitParEcran() {
        view.show(new Screen("mana", true, List.of(
                ScreenElement.of("barre", ElementKind.PROGRESS, 0, 0, 80, 6))));
        view.show(new Screen("vie", true, List.of(
                ScreenElement.of("barre", ElementKind.PROGRESS, 0, 0, 80, 6))));

        view.apply(List.of(ScreenUpdate.progress("mana", "barre", 0.25)));
        assertEquals(0.25, view.progressOf("mana", "barre"), 1e-9);
        assertEquals(0.0, view.progressOf("vie", "barre"), 1e-9,
                "deux barres du même nom ne se confondent pas");
    }

    @Test
    void masquerUnHudOublieSesBarres() {
        view.show(new Screen("mana", true, List.of(
                ScreenElement.of("barre", ElementKind.PROGRESS, 0, 0, 80, 6))));
        view.apply(List.of(ScreenUpdate.progress("mana", "barre", 0.5)));

        view.hide("mana");
        assertEquals(0.0, view.progressOf("mana", "barre"), 1e-9);
    }

    /** Une déconnexion ne laisse pas les HUD du serveur précédent à l'écran. */
    @Test
    void seDeconnecterRemetToutAZero() {
        view.show(hud("mana", "valeur", "40"));
        view.toggleHidden();

        view.clear();
        assertEquals(0, view.size());
        assertFalse(view.hidden(), "et la bascule repart affichée");
    }
}
