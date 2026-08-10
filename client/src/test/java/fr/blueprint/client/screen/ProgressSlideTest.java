package fr.blueprint.client.screen;

import fr.blueprint.core.graph.screen.ElementKind;
import fr.blueprint.core.graph.screen.Screen;
import fr.blueprint.core.graph.screen.ScreenElement;
import fr.blueprint.core.graph.screen.ScreenUpdate;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Une barre glisse au lieu de sauter (épic 21, story 21.6).
 *
 * <p>C'est le gain visible de tout l'épic. Avant, une barre sautait d'une valeur à l'autre au
 * rythme des paquets : sur une valeur répliquée qui change à chaque tick, vingt sauts par seconde
 * sur un écran dessiné soixante fois. La recherche de {@code partialTick|lerp|interpolat} dans
 * {@code ScreenPainter} ne rendait rien — il n'y avait aucun lissage nulle part.
 *
 * <p>L'horloge est <b>injectée</b>, comme celle de {@code RateLimiter} et pour la même raison :
 * un test qui dépend du temps réel est un test qui rougit un jour sur une machine chargée.
 */
class ProgressSlideTest {

    private static final Identifier BP = Identifier.fromNamespaceAndPath("test", "hud");

    private HudView view;
    private long now;

    @BeforeEach
    void setUp() {
        view = new HudView();
        now = 1_000_000L;
        view.clock(() -> now);
        view.show(BP, new Screen("fiche", true, List.of(
                ScreenElement.of("mana", ElementKind.PROGRESS, 0, 0, 100, 6)), Map.of()));
    }

    private void set(double value) {
        view.apply(List.of(ScreenUpdate.progress("fiche", "mana", value)));
    }

    /**
     * La <b>première</b> valeur ne glisse pas : elle s'affiche. Partir de zéro aurait fait
     * remplir la barre à l'ouverture de l'écran — une animation que personne n'a demandée, et qui
     * ment sur l'état pendant qu'elle dure.
     */
    @Test
    void laPremiereValeurNeGlissePas() {
        set(0.8);

        assertEquals(0.8, view.progressAt("fiche", "mana", now));
    }

    @Test
    void unChangementGlisseAuLieuDeSauter() {
        set(0.0);
        set(1.0);

        assertEquals(0.0, view.progressAt("fiche", "mana", now), "au départ, rien n'a bougé");
        assertEquals(0.5, view.progressAt("fiche", "mana", now + 50), 1e-9,
                "à mi-parcours, à mi-chemin");
        assertEquals(1.0, view.progressAt("fiche", "mana", now + 100), "et arrivée");
    }

    @Test
    void auDelaDeLaDureeLaBarreResteALaCible() {
        set(0.0);
        set(1.0);

        assertEquals(1.0, view.progressAt("fiche", "mana", now + 10_000));
    }

    /**
     * Une valeur qui arrive <b>pendant</b> un glissement repart de la position réelle et non de
     * l'ancienne cible. Sinon la barre reculerait à chaque nouvelle valeur, et un flux à 20 Hz —
     * plus rapide que le glissement de 100 ms — saccaderait au lieu de couler.
     */
    @Test
    void uneValeurEnPleinGlissementRepartDeLaPositionReelle() {
        set(0.0);
        set(1.0);
        now += 50;                       // à mi-chemin, donc à 0.5
        set(0.0);                        // demi-tour

        assertEquals(0.5, view.progressAt("fiche", "mana", now), 1e-9,
                "elle repart d'où elle est, pas de 1.0");
        assertEquals(0.25, view.progressAt("fiche", "mana", now + 50), 1e-9);
    }

    /** Vingt valeurs à la cadence d'un tick donnent un mouvement monotone, sans retour. */
    @Test
    void unFluxAuRythmeDuTickNeReculeJamais() {
        set(0.0);
        double previous = 0.0;
        for (int i = 1; i <= 20; i++) {
            now += 50;                   // un tick
            set(i / 20.0);
            double shown = view.progressAt("fiche", "mana", now);
            assertTrue(shown >= previous,
                    "la barre a reculé au pas " + i + " : " + shown + " < " + previous);
            previous = shown;
        }
        assertEquals(1.0, view.progressAt("fiche", "mana", now + 100));
    }

    /** La cible reste lisible telle quelle : c'est ce que le serveur a dit, sans lissage. */
    @Test
    void laCibleResteLisibleSansLissage() {
        set(0.0);
        set(1.0);

        assertEquals(1.0, view.progressOf("fiche", "mana"),
                "progressOf rend la cible, progressAt rend la position");
    }

    @Test
    void uneBarreInconnueVautZero() {
        assertEquals(0.0, view.progressAt("fiche", "inexistante", now));
        assertEquals(0.0, view.progressAt("autre", "mana", now));
    }

    /**
     * Une horloge qui recule — changement d'heure système — ne doit pas figer la barre en arrière.
     * Le temps écoulé négatif vaut « terminé », donc la barre est à sa cible.
     */
    @Test
    void uneHorlogeQuiReculeNeFigePasLaBarre() {
        set(0.0);
        set(1.0);

        assertEquals(1.0, view.progressAt("fiche", "mana", now - 10_000));
    }

    @Test
    void masquerUnHudOublieSesBarres() {
        set(0.8);
        view.hide("fiche");

        assertEquals(0.0, view.progressAt("fiche", "mana", now));
    }
}
