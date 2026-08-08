package fr.blueprint.client.editor.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Un libellé de pastille se lit, il ne se devine pas.
 *
 * <p>Le panneau distribuait ses pastilles à parts égales sur la largeur, quel qu'en soit
 * le nombre. Cinq cibles de liaison sur 136 pixels donnaient 27 pixels chacune, et la
 * capture d'écran montrait « Detac », « Activ », « Visib », « Imag » — quatre mots coupés
 * au milieu. C'est la même faute que le panneau des propriétés vient de corriger sur ses
 * étiquettes : on peint dans une place qu'on n'a pas.
 */
class ChipWrapTest {

    /** La largeur réelle de la colonne des pastilles dans le panneau. */
    private static final int ROOM = 136;

    /**
     * <b>Le cas de la capture : cinq cibles ne tiennent pas sur une ligne.</b>
     *
     * <p>« Visible » fait sept caractères, soit 46 pixels d'estimation. Cinq en demandent
     * 230 ; il y en a 136. La rangée doit donc passer à la ligne — deux par ligne, trois
     * lignes — plutôt que d'en écraser cinq côte à côte.
     */
    @Test
    void cinqCiblesDeLiaisonNeTiennentPasSurUneLigne() {
        int perRow = ElementPropertiesState.chipsPerRow(ROOM, "Visible".length(), 5);
        assertTrue(perRow < 5,
                "cinq pastilles de sept caractères sur 136 px, c'est un mot coupé sur deux");
        assertEquals(2, perRow);
        assertEquals(3, ElementPropertiesState.chipLines(ROOM, "Visible".length(), 5));
    }

    /**
     * <b>Ce qui tient sur une ligne y reste.</b>
     *
     * <p>Le repli n'est pas gratuit : il coûte une rangée de hauteur au panneau, déjà long.
     * Quatre modes de taille courts — « Fixe », « % », « Rest », « Ajust » — tiennent, et
     * les mettre sur deux lignes serait de la place perdue.
     */
    @Test
    void ceQuiTientSurUneLigneYReste() {
        assertEquals(4, ElementPropertiesState.chipsPerRow(ROOM, "Ajust".length(), 4));
        assertEquals(1, ElementPropertiesState.chipLines(ROOM, "Ajust".length(), 4));
    }

    /**
     * <b>Aucune pastille n'est peinte plus étroite que son libellé.</b>
     *
     * <p>Le test qui compte, et qui vaut pour toutes les tailles à la fois : quel que soit
     * le nombre de pastilles et la longueur des mots, la place accordée à chacune couvre
     * son texte — sauf quand une seule ne tient déjà pas, cas où il n'y a rien à faire de
     * mieux que tronquer.
     */
    @Test
    void chaquePastilleARsaPlaceOuAlorsAucuneNeLAurait() {
        for (int chars = 1; chars <= 12; chars++) {
            for (int count = 1; count <= 8; count++) {
                int perRow = ElementPropertiesState.chipsPerRow(ROOM, chars, count);
                int step = ROOM / perRow;
                int needed = chars * ElementPropertiesState.CHAR_WIDTH;
                boolean seule = perRow == 1;
                assertTrue(step >= needed || seule,
                        chars + " caractères × " + count + " : " + step
                                + " px accordés pour " + needed + " nécessaires");
            }
        }
    }

    /**
     * <b>Toutes les pastilles sont placées, aucune n'est perdue.</b>
     *
     * <p>Le repli répartit sur plusieurs lignes ; il ne doit pas en oublier. Le nombre de
     * lignes multiplié par les pastilles par ligne couvre bien le compte — un cran de plus
     * qu'un simple {@code >= 1}, qui passerait même si la dernière ligne n'existait pas.
     */
    @Test
    void toutesLesPastillesSontPlacees() {
        for (int chars = 1; chars <= 12; chars++) {
            for (int count = 1; count <= 8; count++) {
                int perRow = ElementPropertiesState.chipsPerRow(ROOM, chars, count);
                int lines = ElementPropertiesState.chipLines(ROOM, chars, count);
                assertTrue(perRow * lines >= count,
                        chars + " caractères × " + count + " : " + lines + " lignes de "
                                + perRow + " n'en contiennent pas " + count);
                assertTrue((lines - 1) * perRow < count,
                        "une ligne de trop pour " + count + " pastilles");
            }
        }
    }

    /**
     * Une largeur nulle ne divise pas par zéro, et rend au moins une pastille par ligne.
     *
     * <p>Le panneau peut être réduit ; personne ne doit voir une trace de pile pour ça.
     */
    @Test
    void unPanneauEcraseNeDivisePasParZero() {
        assertEquals(1, ElementPropertiesState.chipsPerRow(0, 8, 5));
        assertEquals(5, ElementPropertiesState.chipLines(0, 8, 5));
        assertEquals(3, ElementPropertiesState.chipsPerRow(136, 0, 3));
    }
}
