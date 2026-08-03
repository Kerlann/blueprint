package fr.blueprint.core.nodes;

import net.minecraft.world.BossEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Barres de boss (batch 7) — la <b>gestion d'état</b>, la seule chose ici qui puisse
 * fuir. C'est le seul affichage persistant du jeu : contrairement à un titre, une
 * barre reste tant qu'on ne la retire pas.
 */
class BossBarNodesTest {

    @BeforeEach
    @AfterEach
    void reset() {
        BossBarNodes.clear();
    }

    /**
     * <b>Le test qui compte.</b> Un nœud posé dans une boucle passe soixante fois par
     * seconde : si le nom ne réutilisait pas la barre, il en créerait soixante.
     */
    @Test
    void leMemeNomReutiliseLaMemeBarre() {
        var first = BossBarNodes.obtain("partie", BossEvent.BossBarColor.RED);
        var second = BossBarNodes.obtain("partie", BossEvent.BossBarColor.BLUE);

        assertSame(first, second, "deux appels, une seule barre");
        assertEquals(1, BossBarNodes.liveBars());
    }

    @Test
    void desNomsDifferentsDonnentDesBarresDifferentes() {
        BossBarNodes.obtain("a", BossEvent.BossBarColor.WHITE);
        BossBarNodes.obtain("b", BossEvent.BossBarColor.WHITE);
        assertEquals(2, BossBarNodes.liveBars());
    }

    /**
     * Au-delà du plafond, la création rend null — le nœud en fait une faute nommée
     * plutôt que d'emplir l'écran et la mémoire.
     */
    @Test
    void lePlafondRefuseLaBarreDeTrop() {
        for (int i = 0; i < BossBarNodes.MAX_BARS; i++) {
            assertNotNull(BossBarNodes.obtain("barre" + i, BossEvent.BossBarColor.WHITE),
                    "la barre " + i + " tient dans le plafond");
        }
        assertEquals(BossBarNodes.MAX_BARS, BossBarNodes.liveBars());
        assertNull(BossBarNodes.obtain("celle_de_trop", BossEvent.BossBarColor.WHITE));

        // Mais une barre EXISTANTE reste accessible : le plafond borne la création,
        // il ne condamne pas ce qui est déjà là.
        assertNotNull(BossBarNodes.obtain("barre0", BossEvent.BossBarColor.WHITE));
    }

    @Test
    void retirerLibereUnePlaceDuPlafond() {
        for (int i = 0; i < BossBarNodes.MAX_BARS; i++) {
            BossBarNodes.obtain("barre" + i, BossEvent.BossBarColor.WHITE);
        }
        assertNull(BossBarNodes.obtain("nouvelle", BossEvent.BossBarColor.WHITE));

        BossBarNodes.remove("barre0");
        assertEquals(BossBarNodes.MAX_BARS - 1, BossBarNodes.liveBars());
        assertNotNull(BossBarNodes.obtain("nouvelle", BossEvent.BossBarColor.WHITE),
                "la place libérée doit être reprise");
    }

    /**
     * Le nettoyage d'arrêt de serveur : une barre qui survit à un rechargement
     * n'appartient plus à personne — ni au graphe qui l'a posée, ni à un joueur qui
     * pourrait la retirer.
     */
    @Test
    void leNettoyageVideTout() {
        BossBarNodes.obtain("a", BossEvent.BossBarColor.WHITE);
        BossBarNodes.obtain("b", BossEvent.BossBarColor.WHITE);
        BossBarNodes.clear();
        assertEquals(0, BossBarNodes.liveBars());

        BossBarNodes.clear();
        assertEquals(0, BossBarNodes.liveBars(), "et clear est idempotent");
    }

    @Test
    void retirerUneBarreInexistanteNeCassePas() {
        BossBarNodes.remove("jamais_creee");
        assertEquals(0, BossBarNodes.liveBars());
    }

    @Test
    void uneCouleurInconnueVautBlanc() {
        assertEquals(BossEvent.BossBarColor.RED, BossBarNodes.colorOf("red"));
        assertEquals(BossEvent.BossBarColor.RED, BossBarNodes.colorOf(" RED "),
                "casse et espaces tolérés : c'est une saisie humaine");
        assertEquals(BossEvent.BossBarColor.WHITE, BossBarNodes.colorOf("mauve"));
        assertEquals(BossEvent.BossBarColor.WHITE, BossBarNodes.colorOf(null));
    }

    @Test
    void unNomVideRetombeSurLeNomParDefaut() {
        assertEquals("principale", BossBarNodes.name(""));
        assertEquals("principale", BossBarNodes.name("   "));
        assertEquals("principale", BossBarNodes.name(null));
        assertEquals("partie", BossBarNodes.name("  partie  "));
    }
}
