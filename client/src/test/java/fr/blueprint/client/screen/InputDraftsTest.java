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
class InputDraftsTest {

    /** <b>Le défaut d'origine.</b> Treize frappes, zéro paquet. */
    @Test
    void unChampOrdinaireRetientCeQuOnTape() {
        InputDrafts drafts = new InputDrafts();
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
        InputDrafts drafts = new InputDrafts();
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
        InputDrafts drafts = new InputDrafts();
        drafts.typed("prenom", false);
        drafts.flush("prenom");

        assertFalse(drafts.flush("prenom"), "rien de neuf, rien à envoyer");
        assertFalse(drafts.flush("prenom"));
    }

    /** {@code Entrée} envoie par un autre chemin : le brouillon ne doit pas repartir. */
    @Test
    void unePartieDejaEnvoyeeNeRepartPasAuRelachement() {
        InputDrafts drafts = new InputDrafts();
        drafts.typed("prenom", false);
        drafts.sent("prenom");

        assertFalse(drafts.flush("prenom"),
                "Entrée a déjà tout envoyé, relâcher ensuite doublerait le paquet");
    }

    /** Une recherche qui filtre, elle, n'a pas le choix : chaque frappe compte. */
    @Test
    void unChampLiveEnvoieAChaqueFrappe() {
        InputDrafts drafts = new InputDrafts();

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
        InputDrafts drafts = new InputDrafts();
        drafts.typed("prenom", false);
        drafts.typed("nom", false);

        assertTrue(drafts.flush("prenom"));
        assertTrue(drafts.waiting("nom"), "envoyer le prénom ne doit pas jeter le nom");
        assertTrue(drafts.flush("nom"));
    }

    /** Relâcher « rien » — aucun champ n'a le focus — ne fait rien. */
    @Test
    void relacherAucunChampNeFaitRien() {
        assertFalse(new InputDrafts().flush(null));
    }
}
