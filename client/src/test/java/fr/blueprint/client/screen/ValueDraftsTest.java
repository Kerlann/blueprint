package fr.blueprint.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Un champ de saisie ne coûte plus un paquet par frappe.</b>
 *
 * <p>Il en coûtait un. Taper « Jean-Baptiste » valait treize allers vers le serveur,
 * treize exécutions du graphe et treize écritures de variable, pour un nom qui n'intéresse
 * personne avant d'être complet.
 *
 * <p>Ces tests portent sur la règle, pas sur l'écran : un brouillon perdu ou envoyé deux
 * fois se remarque très mal en jouant, et pas du tout en relisant.
 */
class ValueDraftsTest {

    /** <b>Le défaut d'origine.</b> Treize frappes, zéro paquet. */
    @Test
    void unChampOrdinaireRetientCeQuOnTape() {
        ValueDrafts drafts = new ValueDrafts();
        int envois = 0;
        for (int i = 0; i < "Jean-Baptiste".length(); i++) {
            if (drafts.typed("prenom", false)) {
                envois++;
            }
        }
        assertEquals(0, envois, "aucune frappe ne doit partir seule");
        assertTrue(drafts.waiting("prenom"), "le brouillon attend");
    }

    /**
     * <b>Et il part au bon moment.</b> C'est ce qui rend le report sûr : cliquer sur
     * « Valider » relâche le champ, donc le texte part avant le clic.
     */
    @Test
    void leBrouillonPartALaPerteDuFocus() {
        ValueDrafts drafts = new ValueDrafts();
        drafts.typed("prenom", false);

        assertTrue(drafts.flush("prenom"), "relâcher le champ doit faire partir le texte");
        assertFalse(drafts.waiting("prenom"));
    }

    /**
     * Relâcher deux fois n'envoie qu'une fois.
     *
     * <p>Sans cela, chaque clic dans le vide referait partir le même texte : on aurait
     * remplacé un paquet par frappe par un paquet par clic, ce qui n'est pas un progrès.
     */
    @Test
    void relacherSansAvoirTapeNEnvoieRien() {
        ValueDrafts drafts = new ValueDrafts();
        drafts.typed("prenom", false);
        drafts.flush("prenom");

        assertFalse(drafts.flush("prenom"), "rien de neuf, rien à envoyer");
        assertFalse(drafts.flush("prenom"));
    }

    /** {@code Entrée} envoie par un autre chemin : le brouillon ne doit pas repartir. */
    @Test
    void unePartieDejaEnvoyeeNeRepartPasAuRelachement() {
        ValueDrafts drafts = new ValueDrafts();
        drafts.typed("prenom", false);
        drafts.sent("prenom");

        assertFalse(drafts.flush("prenom"),
                "Entrée a déjà tout envoyé, relâcher ensuite doublerait le paquet");
    }

    /** Une recherche qui filtre, elle, n'a pas le choix : chaque frappe compte. */
    @Test
    void unChampLiveEnvoieAChaqueFrappe() {
        ValueDrafts drafts = new ValueDrafts();

        assertTrue(drafts.typed("recherche", true));
        assertTrue(drafts.typed("recherche", true));
        assertFalse(drafts.waiting("recherche"),
                "rien n'attend : tout est déjà parti");
        assertFalse(drafts.flush("recherche"),
                "relâcher un champ live ne doit rien renvoyer");
    }

    /** Deux champs remplis à la suite ne se marchent pas dessus. */
    @Test
    void deuxChampsGardentChacunSonBrouillon() {
        ValueDrafts drafts = new ValueDrafts();
        drafts.typed("prenom", false);
        drafts.typed("nom", false);

        assertTrue(drafts.flush("prenom"));
        assertTrue(drafts.waiting("nom"), "envoyer le prénom ne doit pas jeter le nom");
        assertTrue(drafts.flush("nom"));
    }

    /** Relâcher « rien » — aucun champ n'a le focus — ne fait rien. */
    @Test
    void relacherAucunChampNeFaitRien() {
        assertFalse(new ValueDrafts().flush(null));
    }

    /**
     * <b>Le cas vu dans le journal du serveur.</b>
     *
     * <p>Régler l'âge du formulaire de jeu de rôle — de 16 à 90, par pas de 1 — traverse
     * soixante-quatorze crans en une seconde. Le serveur accepte quarante interactions
     * d'écran par dix secondes : le joueur crevait le quota et se faisait ignorer, avec
     * pour toute explication un avertissement dans un journal qu'il ne lit pas.
     */
    @Test
    void unGlissementDeCurseurNEnvoieQuUnPaquet() {
        ValueDrafts drafts = new ValueDrafts();
        int envois = 0;
        for (int cran = 16; cran <= 90; cran++) {
            if (drafts.typed("age", false)) {
                envois++;
            }
        }
        if (drafts.flush("age")) {
            envois++;
        }
        assertEquals(1, envois,
                "soixante-quatorze crans doivent tenir en un seul paquet, au relâchement");
    }

    /** Une jauge que le graphe doit suivre en continu reste possible, en le déclarant. */
    @Test
    void unCurseurLiveEnvoieAChaqueCran() {
        ValueDrafts drafts = new ValueDrafts();
        int envois = 0;
        for (int cran = 0; cran < 10; cran++) {
            if (drafts.typed("jauge", true)) {
                envois++;
            }
        }
        assertEquals(10, envois);
        assertFalse(drafts.flush("jauge"), "tout est déjà parti : le relâchement n'ajoute rien");
    }
}
