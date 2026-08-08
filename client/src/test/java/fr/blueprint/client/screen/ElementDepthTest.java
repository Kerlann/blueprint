package fr.blueprint.client.screen;

import fr.blueprint.core.graph.screen.ElementKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Le relief d'un élément : ce qui dit, sans un mot, ce qu'on peut en faire.
 *
 * <p>Tous les éléments recevaient le <b>même</b> châssis — un fond, un cadre d'un pixel.
 * Un champ de saisie était donc un libellé avec une bordure, et rien n'y invitait à taper.
 * C'est exactement le retour que le canevas de nœuds a déjà reçu une fois, et sa réponse
 * est écrite dans son code : « le littéral doit se VOIR comme un champ, même vide ».
 */
class ElementDepthTest {

    /**
     * <b>On appuie sur ce qui saille, on remplit ce qui est creux.</b>
     *
     * <p>La convention est celle de toutes les interfaces depuis toujours, Minecraft
     * compris. L'inverser — un bouton creusé, un champ saillant — serait pire que pas de
     * relief du tout : l'auteur croirait pouvoir taper dans un bouton.
     */
    @Test
    void onAppuieSurCeQuiSailleEtOnRemplitCeQuiEstCreux() {
        assertEquals(ScreenPainter.Depth.RAISED, ScreenPainter.depthOf(ElementKind.BUTTON));
        assertEquals(ScreenPainter.Depth.RAISED, ScreenPainter.depthOf(ElementKind.TOGGLE));

        for (ElementKind creux : java.util.List.of(ElementKind.INPUT, ElementKind.SLOT,
                ElementKind.PROGRESS, ElementKind.SLIDER, ElementKind.LIST,
                ElementKind.DROPDOWN)) {
            assertEquals(ScreenPainter.Depth.SUNKEN, ScreenPainter.depthOf(creux),
                    creux + " reçoit quelque chose : il doit se lire comme un creux");
        }
    }

    /**
     * <b>Ce qui n'est pas un contrôle reste plat.</b>
     *
     * <p>Un libellé, une image, un panneau et un aperçu d'entité sont du <i>contenu</i>.
     * Leur donner du relief promettrait une interaction qui n'existe pas — la même faute
     * que les poignées inertes que la 10.10 a retirées.
     */
    @Test
    void ceQuiNestPasUnControleResteplat() {
        for (ElementKind contenu : java.util.List.of(ElementKind.PANEL, ElementKind.LABEL,
                ElementKind.IMAGE, ElementKind.ENTITY_PREVIEW)) {
            assertEquals(ScreenPainter.Depth.FLAT, ScreenPainter.depthOf(contenu),
                    contenu + " ne se presse ni ne se remplit");
        }
    }

    /**
     * Chaque type a un relief, et le {@code switch} est exhaustif.
     *
     * <p>Sans {@code default} : un treizième type ne compilera pas tant qu'on n'aura pas
     * dit s'il se presse, se remplit, ou ne fait ni l'un ni l'autre.
     */
    @Test
    void chaqueTypeAUnRelief() {
        for (ElementKind kind : ElementKind.values()) {
            assertNotEquals(null, ScreenPainter.depthOf(kind), kind.toString());
        }
    }

    /**
     * <b>Les interactifs se distinguent des contenus.</b>
     *
     * <p>Le test qui compte : ce n'est pas la valeur exacte d'un relief qui importe, c'est
     * qu'un élément avec lequel on interagit ne se dessine <i>jamais</i> comme un élément
     * qu'on lit. Sans cette séparation, le relief serait décoratif.
     */
    @Test
    void unInteractifNeSeDessineJamaisCommeUnContenu() {
        for (ElementKind kind : ElementKind.values()) {
            if (kind.interactive()) {
                assertNotEquals(ScreenPainter.Depth.FLAT, ScreenPainter.depthOf(kind),
                        kind + " est interactif : le dessiner plat n'annoncerait rien");
            }
        }
    }
}
