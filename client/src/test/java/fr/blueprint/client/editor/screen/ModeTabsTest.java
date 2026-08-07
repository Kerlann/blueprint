package fr.blueprint.client.editor.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Les onglets de l'éditeur : <b>on clique là où on voit</b> (story 20.2).
 *
 * <p>Le rendu et le hit-test lisent la même arithmétique — c'est ce qui permet d'ajouter un
 * troisième onglet sans toucher à l'un ni à l'autre. Encore faut-il que ce soit vrai, et
 * rien ne le vérifiait : ces tests couvrent la géométrie, pas le dessin.
 *
 * <p>Ils n'utilisent pas de {@code Font}, qui demande un client démarré. La largeur d'un
 * onglet en dépend, donc ce qui se teste ici est ce qui n'en dépend pas : l'ordre des
 * modes, et le refus de tout ce qui tombe hors de la barre.
 */
class ModeTabsTest {

    /**
     * <b>Trois modes, dans un ordre qui est celui de l'affichage.</b>
     *
     * <p>{@code values()} sert au rendu comme au hit-test. Insérer un mode au milieu
     * déplacerait donc les onglets existants — ce qui est visible, mais qu'il vaut mieux
     * décider que subir.
     */
    @Test
    void troisModesDansLOrdreDAffichage() {
        assertEquals(3, ModeTabs.Mode.values().length);
        assertEquals(ModeTabs.Mode.GRAPH, ModeTabs.Mode.values()[0]);
        assertEquals(ModeTabs.Mode.SCREENS, ModeTabs.Mode.values()[1]);
        assertEquals(ModeTabs.Mode.FUNCTIONS, ModeTabs.Mode.values()[2],
                "le nouvel onglet s'ajoute à la FIN : l'insérer au milieu déplacerait ceux "
                        + "que l'auteur a appris à viser");
    }

    /**
     * <b>Un seul onglet montre le concepteur d'écrans.</b>
     *
     * <p>Le défaut qui a rendu ce test nécessaire : le rendu de l'éditeur demandait « est-ce
     * le graphe ? » pendant que tout le routage des entrées demandait « est-ce les
     * écrans ? ». Avec deux modes, les deux formulations coïncident. Le troisième onglet les
     * a séparées, et « Fonctions » dessinait le concepteur d'écrans pendant que les clics
     * partaient au canevas — un écran qui ne réagit pas à ce qu'il montre.
     *
     * <p>Ce que ce test protège n'est donc pas la valeur d'un booléen : c'est le fait que la
     * question ne se pose qu'à un seul endroit. Un quatrième mode ne compilera pas sans
     * qu'on ait dit sur quelle surface il vit.
     */
    @Test
    void unSeulOngletMontreLeConcepteurDEcrans() {
        assertTrue(ModeTabs.Mode.SCREENS.showsDesigner());
        assertFalse(ModeTabs.Mode.GRAPH.showsDesigner());
        assertFalse(ModeTabs.Mode.FUNCTIONS.showsDesigner(),
                "l'onglet Fonctions montre le CANEVAS : c'est là qu'on édite un corps, et "
                        + "c'est là que ses clics vont déjà");
    }

    /**
     * <b>Revenir au graphe referme le corps ; passer aux écrans ne le referme pas.</b>
     *
     * <p>L'onglet Graphe montre le graphe : y revenir en laissant un corps ouvert
     * afficherait le corps sous la mauvaise étiquette. Le concepteur d'écrans, lui, ne
     * montre aucun des deux graphes — rien n'y ment, et revenir aux fonctions doit retrouver
     * l'endroit où l'on travaillait.
     */
    @Test
    void revenirAuGrapheRefermeLeCorpsMaisPasLesEcrans() {
        assertTrue(ModeTabs.Mode.GRAPH.closesFunctionBody());
        assertFalse(ModeTabs.Mode.FUNCTIONS.closesFunctionBody(),
                "c'est l'onglet où l'on édite un corps : le refermer en y arrivant serait "
                        + "absurde");
        assertFalse(ModeTabs.Mode.SCREENS.closesFunctionBody(),
                "un aller-retour par les écrans ne doit pas faire perdre le corps ouvert");
    }

    /**
     * Au-dessus ou en dessous de la barre, aucun onglet.
     *
     * <p>Le seul test de {@code modeAt} qui ne demande pas de police : il protège le cas
     * où un clic dans le canevas, juste sous la barre, changerait d'onglet.
     */
    @Test
    void unClicHorsDeLaBarreNeViseAucunOnglet() {
        assertNull(ModeTabs.modeAt(null, 10, -1, 0, 13),
                "au-dessus de la barre, rien");
        assertNull(ModeTabs.modeAt(null, 10, 13, 0, 13),
                "à la première ligne SOUS la barre, rien — sinon un clic dans le canevas "
                        + "changerait d'onglet");
        assertNull(ModeTabs.modeAt(null, 10, 40, 0, 13));
    }
}
