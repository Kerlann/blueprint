package fr.blueprint.client.editor.screen;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ce qu'on voit est ce qu'on clique.
 *
 * <p>C'est le point 2.8 de la feuille de vérification, qui demandait de cliquer les cinq
 * cibles de liaison à la main — <b>y compris celles de la seconde ligne</b>. Ce geste ne
 * vérifie qu'une chose, et c'est une chose qui se prouve : que le rectangle peint et la
 * zone qui répond au clic désignent la même pastille.
 *
 * <p>Ce panneau s'est fait prendre trois fois par la faute inverse — une arithmétique pour
 * peindre, une autre pour cliquer. Le jour où l'une change sans l'autre, on appuie sur une
 * valeur pour en obtenir une autre, et rien ne le dit. Une pastille qui ment sur ce qu'elle
 * fait est pire qu'une pastille absente.
 */
class ChipHitTest {

    private static final int ROOM = 136;
    private static final int LEFT = 4;
    private static final int ROW_Y = 40;

    /**
     * <b>Le cas de la feuille : cinq cibles sur trois lignes, chacune répond pour elle.</b>
     *
     * <p>Le centre du rectangle peint de chaque pastille lui est rendu — pas à sa voisine
     * de gauche, pas à celle du dessus. C'est précisément la seconde ligne qui était en
     * cause : elle se pose douze pixels plus bas, et un routage qui ignore l'ordonnée y
     * déclenche la valeur d'au-dessus.
     */
    @Test
    void chaqueCibleDeLiaisonRepondPourElleMeme() {
        List<ElementPropertiesState.ChipSlot> slots =
                ElementPropertiesState.chipSlots(LEFT, ROOM, "Visible".length(), 5);
        assertEquals(3, slots.stream().mapToInt(ElementPropertiesState.ChipSlot::line).max()
                .orElseThrow() + 1, "cinq cibles de sept caractères tiennent sur trois lignes");

        for (int i = 0; i < slots.size(); i++) {
            var slot = slots.get(i);
            double cx = slot.x() + slot.width() / 2.0;
            double cy = ElementPropertiesState.chipTop(ROW_Y, slot.line())
                    + ElementPropertiesState.CHIP_HEIGHT / 2.0;
            assertEquals(i, ElementPropertiesState.chipAt(slots, ROW_Y, cx, cy),
                    "la pastille " + i + " (ligne " + slot.line() + ") ne se rend pas elle-même");
        }
    }

    /**
     * <b>Le test qui compte, sur toutes les formes à la fois.</b>
     *
     * <p>Quel que soit le nombre de pastilles et la longueur des mots, le centre de chaque
     * rectangle peint route vers sa propre pastille. Un cas particulier vérifié à la main
     * ne dit rien des autres ; celui-ci en couvre quatre-vingt-seize.
     */
    @Test
    void leCentreDeCeQuiEstPeintRouteToujoursVersLuiMeme() {
        for (int chars = 1; chars <= 12; chars++) {
            for (int count = 1; count <= 8; count++) {
                List<ElementPropertiesState.ChipSlot> slots =
                        ElementPropertiesState.chipSlots(LEFT, ROOM, chars, count);
                for (int i = 0; i < count; i++) {
                    var slot = slots.get(i);
                    double cx = slot.x() + slot.width() / 2.0;
                    double cy = ElementPropertiesState.chipTop(ROW_Y, slot.line())
                            + ElementPropertiesState.CHIP_HEIGHT / 2.0;
                    assertEquals(i, ElementPropertiesState.chipAt(slots, ROW_Y, cx, cy),
                            chars + " caractères × " + count + " : la pastille " + i
                                    + " est peinte en " + cx + "," + cy + " et rend autre chose");
                }
            }
        }
    }

    /**
     * <b>Tout ce qui est peint est cliquable, et rien ne déborde sur la ligne suivante.</b>
     *
     * <p>Deux pixels séparent deux pastilles empilées : la zone cliquable les avale pour
     * qu'aucun clic ne tombe dans une bande morte — un raté qu'on mettrait sur le compte
     * de sa souris. Mais elle ne doit pas mordre sur la ligne d'en dessous, sinon le haut
     * d'une pastille déclencherait celle du dessus.
     */
    @Test
    void leRectanglePeintTientDansLaZoneCliquableSansMordreLaSuivante() {
        for (int line = 0; line < 4; line++) {
            int top = ElementPropertiesState.chipTop(ROW_Y, line);
            int painted = top + ElementPropertiesState.CHIP_HEIGHT;
            int clickable = top + ElementPropertiesState.ROW;
            assertTrue(painted <= clickable,
                    "ligne " + line + " : on peint plus bas qu'on ne clique");
            assertEquals(ElementPropertiesState.chipTop(ROW_Y, line + 1), clickable,
                    "ligne " + line + " : la zone cliquable et la ligne suivante se recouvrent");
        }
    }

    /**
     * Hors de toute pastille, le routage rend {@code -1} — il ne devine pas la plus proche.
     *
     * <p>Un panneau qui rattrape les clics manqués agirait sur un réglage qu'on n'a pas
     * visé. Mieux vaut qu'il ne se passe rien.
     */
    @Test
    void horsDeTouteZoneRienNeSeDeclenche() {
        List<ElementPropertiesState.ChipSlot> slots =
                ElementPropertiesState.chipSlots(LEFT, ROOM, "Visible".length(), 5);
        assertEquals(-1, ElementPropertiesState.chipAt(slots, ROW_Y, LEFT - 2, ROW_Y),
                "à gauche de la première colonne");
        assertEquals(-1, ElementPropertiesState.chipAt(slots, ROW_Y, LEFT + ROOM + 4, ROW_Y),
                "à droite de la dernière");
        assertEquals(-1, ElementPropertiesState.chipAt(slots, ROW_Y, LEFT + 2, ROW_Y - 5),
                "au-dessus de la rangée");
        // Cinq pastilles sur trois lignes : la sixième place n'existe pas.
        assertEquals(-1, ElementPropertiesState.chipAt(slots, ROW_Y,
                        LEFT + ROOM / 2 + 4, ElementPropertiesState.chipTop(ROW_Y, 2) + 5),
                "la seconde colonne de la dernière ligne est vide");
    }

    /**
     * <b>2.12 — un style ne se détache que s'il est appliqué.</b>
     *
     * <p>Chaque ligne portait son bouton : quatre boutons pour une action qui n'en concerne
     * qu'une. Détacher un style qu'on n'a pas mis ne veut rien dire, et sur trente pixels
     * chacun affichait « Detac ».
     */
    @Test
    void unStyleNeSeDetacheQueSilEstApplique() {
        assertTrue(ElementPropertiesState.detachable("sombre", "sombre"));
        assertTrue(!ElementPropertiesState.detachable("clair", "sombre"),
                "détacher un style qu'on n'a pas mis ne veut rien dire");
        assertTrue(!ElementPropertiesState.detachable("sombre", null),
                "un élément sans style n'a rien à détacher");
        assertTrue(!ElementPropertiesState.detachable("sombre", ""),
                "le vide n'est pas un nom de style");
    }
}
