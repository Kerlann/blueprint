package fr.blueprint.core.net;

import fr.blueprint.core.graph.screen.ElementBinding;
import fr.blueprint.core.graph.screen.ElementKind;
import fr.blueprint.core.graph.screen.Screen;
import fr.blueprint.core.graph.screen.ScreenElement;
import fr.blueprint.core.graph.screen.ScreenUpdate;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La liaison de données (story 10.7) : ce qu'un rafraîchissement produit, et surtout ce
 * qu'il <b>ne produit pas</b>.
 *
 * <p>La promesse la plus difficile à observer en jeu — « rien ne part quand rien ne
 * change » — se vérifie ici en une assertion. En partie, il faudrait un analyseur de
 * trames pour dire la différence entre « aucun paquet » et « des paquets qu'on ne voit
 * pas ».
 */
class ScreenBindingsTest {

    private static ScreenElement bound(String name, ElementKind kind, ElementBinding binding) {
        return ScreenElement.of(name, kind, 0, 0, 60, 20).withBinding(binding);
    }

    private static Screen screen(ScreenElement... elements) {
        return new Screen("menu", false, List.of(elements));
    }

    private static List<ScreenUpdate> updates(Screen screen, Map<String, Object> values) {
        return ScreenBindings.updates(screen, values::get);
    }

    // ------------------------------------------------------------- le format

    /**
     * Sans format, lier une étiquette à {@code argent} afficherait « 1234.0 ». L'auteur
     * mettrait alors un {@code convert/to_string}, un {@code string/concat}, et finirait
     * par appeler {@code gui/set_text} — c'est-à-dire par revenir au cas qu'on voulait
     * éviter. Le format n'est pas un raffinement : c'est ce qui rend la liaison utile.
     */
    @Test
    void leFormatEstCeQuiRendLaLiaisonUtile() {
        var binding = ElementBinding.text("argent", "Or : %s");

        assertEquals("Or : 1234", binding.renderText(1234.0),
                "un entier déguisé en double ne traîne pas son « .0 »");
        assertEquals("Or : 12,50".replace(',', '.'),
                binding.withDecimals(2).renderText(12.5));
        assertEquals("Or : bonjour", binding.renderText("bonjour"));
        assertEquals("Or : ", binding.renderText(null),
                "une variable jamais écrite donne du vide, jamais « null »");
    }

    @Test
    void uneBarreSeRamenneDansSaPlage() {
        var binding = ElementBinding.progress("vie", 0, 20);

        assertEquals(0.5, binding.renderProgress(10.0), 1e-9);
        assertEquals(0, binding.renderProgress(-5.0), 1e-9, "une vie négative ne dessine pas à gauche");
        assertEquals(1, binding.renderProgress(999.0), 1e-9);

        // Une plage nulle diviserait par zéro soixante fois par seconde, dans un rendu
        // qui n'a aucun moyen de signaler quoi que ce soit.
        assertEquals(1, new ElementBinding("x", ElementBinding.Target.PROGRESS, "%s", 5, 5, 0)
                .renderProgress(9.0), 1e-9);
    }

    @Test
    void unBooleenSeLitCommeUnAuteurLAttend() {
        var binding = ElementBinding.NONE.withVariable("actif")
                .withTarget(ElementBinding.Target.ENABLED);

        assertTrue(binding.renderFlag(true));
        assertTrue(binding.renderFlag(3));
        assertFalse(binding.renderFlag(0));
        assertFalse(binding.renderFlag(""));
        assertFalse(binding.renderFlag("false"));
        assertTrue(binding.renderFlag("oui"));
        assertFalse(binding.renderFlag(null), "jamais écrite : pas actif");
    }

    // -------------------------------------------------------------- le rendu

    @Test
    void chaqueCibleProduitLaBonneModification() {
        Screen screen = screen(
                bound("or", ElementKind.LABEL, ElementBinding.text("argent", "Or : %s")),
                bound("vie", ElementKind.PROGRESS, ElementBinding.progress("pv", 0, 20)),
                bound("bouton", ElementKind.BUTTON, ElementBinding.NONE.withVariable("riche")
                        .withTarget(ElementBinding.Target.ENABLED)));

        var out = updates(screen, Map.of("argent", 50, "pv", 15, "riche", true));

        assertEquals(3, out.size());
        assertEquals("Or : 50", out.stream()
                .filter(u -> u.element().equals("or")).findFirst().orElseThrow().text());
        assertEquals(0.75, out.stream()
                .filter(u -> u.element().equals("vie")).findFirst().orElseThrow().number(), 1e-9);
        assertTrue(out.stream()
                .filter(u -> u.element().equals("bouton")).findFirst().orElseThrow().flag());
        assertEquals("menu", out.getFirst().screen(), "chaque modification nomme son écran");
    }

    @Test
    void unElementNonLieNeProduitRien() {
        Screen screen = screen(
                ScreenElement.of("statique", ElementKind.LABEL, 0, 0, 60, 20),
                bound("lie", ElementKind.LABEL, ElementBinding.text("v", "%s")));

        var out = updates(screen, Map.of("v", 1));
        assertEquals(1, out.size());
        assertEquals("lie", out.getFirst().element());
    }

    /** Une texture illisible n'efface pas celle en place : l'écran resterait sans image. */
    @Test
    void uneTextureIllisibleNeffacePasCelleEnPlace() {
        Screen screen = screen(bound("image", ElementKind.IMAGE,
                ElementBinding.NONE.withVariable("icone")
                        .withTarget(ElementBinding.Target.TEXTURE)));

        assertTrue(updates(screen, Map.of("icone", "PAS UN IDENTIFIANT")).isEmpty());
        assertEquals("blueprint:pack/ma_boutique/piece",
                updates(screen, Map.of("icone", "ma_boutique/piece")).getFirst().text());
    }

    // ------------------------------------------------- ce qui ne part PAS (AC2)

    /**
     * <b>Le test qui compte.</b> Un écran ouvert et immobile ne coûte rien : deux
     * rafraîchissements de suite sur des valeurs inchangées n'envoient qu'une trame,
     * puis plus rien.
     *
     * <p>Le diff n'est pas refait ici — {@link ScreenSessions#queue} compare déjà chaque
     * modification à ce que le client affiche. Ce test vérifie que les deux morceaux
     * s'emboîtent : c'est la jonction, et non chaque moitié, qui décide si un écran
     * immobile coûte quelque chose.
     */
    @Test
    void deuxRafraichissementsIdentiquesNenvoientRienLaSecondeFois() {
        Screen screen = screen(
                bound("or", ElementKind.LABEL, ElementBinding.text("argent", "Or : %s")),
                bound("vie", ElementKind.PROGRESS, ElementBinding.progress("pv", 0, 20)));

        ScreenSessions sessions = new ScreenSessions();
        UUID player = UUID.randomUUID();
        sessions.opened(player, net.minecraft.resources.Identifier
                .fromNamespaceAndPath("test", "bp"), "menu");
        Map<String, Object> values = new HashMap<>(Map.of("argent", 50, "pv", 15));

        int first = queueAll(sessions, player, updates(screen, values));
        assertEquals(2, first, "le premier rafraîchissement envoie tout");
        assertEquals(2, sessions.drain(player).size());

        int second = queueAll(sessions, player, updates(screen, values));
        assertEquals(0, second, "rien n'a changé : RIEN ne part");
        assertTrue(sessions.drain(player).isEmpty());

        values.put("argent", 51);
        int third = queueAll(sessions, player, updates(screen, values));
        assertEquals(1, third, "une seule valeur a bougé : une seule modification");
        assertEquals("or", sessions.drain(player).getFirst().element());
    }

    /**
     * Deux valeurs distinctes qui s'<b>affichent</b> pareil ne méritent pas de paquet.
     * « 3,0001 » et « 3,0002 » à zéro décimale donnent tous deux « 3 » : l'écran ne
     * bougerait pas, et l'envoi n'aurait servi qu'à occuper le réseau.
     */
    @Test
    void deuxValeursQuiSAffichentPareilNenvoientQuUneFois() {
        Screen screen = screen(bound("or", ElementKind.LABEL,
                ElementBinding.text("argent", "%s")));

        ScreenSessions sessions = new ScreenSessions();
        UUID player = UUID.randomUUID();
        sessions.opened(player, net.minecraft.resources.Identifier
                .fromNamespaceAndPath("test", "bp"), "menu");

        assertEquals(1, queueAll(sessions, player, updates(screen, Map.of("argent", 3.0001))));
        sessions.drain(player);
        assertEquals(0, queueAll(sessions, player, updates(screen, Map.of("argent", 3.0002))),
                "arrondis à la même chose : rien à envoyer");
    }

    /**
     * Une liaison n'exclut pas les modificateurs (AC5) : forcer un texte à la main tient
     * jusqu'au prochain rafraîchissement, qui reprend la main. La surprise inverse — un
     * {@code gui/set_text} sans effet sur un élément lié — serait pénible.
     */
    @Test
    void unModificateurExpliciteTientJusquAuProchainRafraichissement() {
        Screen screen = screen(bound("or", ElementKind.LABEL,
                ElementBinding.text("argent", "Or : %s")));
        ScreenSessions sessions = new ScreenSessions();
        UUID player = UUID.randomUUID();
        sessions.opened(player, net.minecraft.resources.Identifier
                .fromNamespaceAndPath("test", "bp"), "menu");

        queueAll(sessions, player, updates(screen, Map.of("argent", 50)));
        sessions.drain(player);

        assertTrue(sessions.queue(player, ScreenUpdate.text("menu", "or",
                        fr.blueprint.core.graph.screen.ScreenText.literal("Chargement…"))),
                "le modificateur passe : il diffère de ce qui est affiché");
        sessions.drain(player);

        assertEquals(1, queueAll(sessions, player, updates(screen, Map.of("argent", 50))),
                "et la liaison reprend la main au rafraîchissement suivant, même valeur");
    }

    private static int queueAll(ScreenSessions sessions, UUID player, List<ScreenUpdate> updates) {
        int sent = 0;
        for (ScreenUpdate update : updates) {
            if (sessions.queue(player, update)) {
                sent++;
            }
        }
        return sent;
    }
}
