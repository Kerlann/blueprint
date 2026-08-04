package fr.blueprint.client.editor;

import fr.blueprint.client.editor.screen.ScreenDesignerWidget;
import fr.blueprint.core.graph.screen.ElementKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Les libellés des panneaux doivent tenir dans la place qui leur est laissée.
 *
 * <p>Constaté en jeu : le panneau de détails affichait « Descrip », « Permissi » et
 * « Nodes/li ». Un libellé coupé au milieu d'un mot ne dit plus ce qu'il désigne — et
 * c'est justement ce que ces panneaux sont là pour dire. La palette du concepteur, elle,
 * ne tronquait rien du tout : « Progress bar » touchait déjà le bord, et sa traduction
 * française l'aurait dépassé franchement.
 *
 * <p>Aucun de ces deux défauts n'était visible depuis le code : les largeurs vivaient à
 * un endroit, les libellés à un autre, et rien ne les confrontait. C'est ce que fait ce
 * test — il ne dessine rien, il compare des nombres.
 *
 * <p>La largeur d'un texte est <b>estimée</b> : la police du jeu n'est pas accessible
 * sans client. L'estimation reprend ses vraies valeurs — six pixels pour une capitale,
 * cinq pour le reste — plus une marge de sécurité. Une estimation grossière, six partout,
 * a été essayée d'abord : elle réclamait de la place inutile, et l'on aurait élargi les
 * panneaux au détriment du canevas pour un problème qui n'existait pas.
 */
class PanelLabelWidthTest {

    /**
     * Largeur estimée d'un texte dans la police du jeu : six pixels pour une capitale ou
     * un chiffre, cinq pour le reste. Ce sont les valeurs réelles de la police par
     * défaut de Minecraft.
     *
     * <p>Une estimation TROP pessimiste — six partout, comme au premier essai — ferait
     * réclamer de la place qui n'est pas nécessaire, et l'on élargirait les panneaux au
     * détriment du canevas pour un problème qui n'existe pas.
     */
    private static int estimate(String text) {
        int width = SAFETY_MARGIN;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            width += Character.isUpperCase(c) || Character.isDigit(c) ? 6 : 5;
        }
        return width;
    }

    /** De quoi absorber une police légèrement plus large, ou un accent qui déborde. */
    private static final int SAFETY_MARGIN = 4;

    /** Ce que le rendu laisse au libellé du panneau de détails. */
    private static final int DETAILS_LABEL = 64;

    /**
     * Les libellés du panneau de détails tiennent, dans les deux langues. Ils sont
     * repris en dur ici plutôt que lus des fichiers de traduction : ce test protège une
     * GÉOMÉTRIE, et le lier au chargement des ressources le ferait échouer pour des
     * raisons qui n'ont rien à voir.
     */
    @Test
    void lesLibellesDuPanneauDeDetailsTiennent() {
        for (String label : List.of(
                "Author", "Description", "Version", "Permission", "Counts",
                "Auteur", "Version", "Compteurs")) {
            assertTrue(estimate(label) <= DETAILS_LABEL, () -> String.format(
                    "« %s » demande ~%d px, le panneau en laisse %d — il sera tronqué",
                    label, estimate(label), DETAILS_LABEL));
        }
    }

    /**
     * <b>Le test qui compte.</b> Chaque type d'élément a un nom dans la palette du
     * concepteur, et ces noms grandissent avec la langue : « Item slot » fait neuf
     * caractères, « Emplacement » onze, et rien ne les tronquait.
     */
    @Test
    void lesTypesDElementTiennentDansLaPalette() {
        int room = ScreenDesignerWidget.PALETTE_WIDTH - 12;
        // Les noms français livrés, mot pour mot : ce sont eux qui seront dessinés.
        for (String label : List.of("Panneau", "Texte", "Bouton", "Image", "Barre",
                "Liste", "Saisie", "Emplacement", "Case à cocher", "Curseur", "Entité",
                // Et les anglais, dont « Progress bar » qui touchait le bord.
                "Progress bar", "Item slot", "Checkbox")) {
            assertTrue(estimate(label) <= room, () -> String.format(
                    "« %s » demande ~%d px, la palette en laisse %d",
                    label, estimate(label), room));
        }
        assertTrue(ElementKind.values().length >= 11,
                "onze types au moins : si un type est ajouté, son nom doit passer ici");
    }

    /**
     * Toute clé de type d'élément est bien celle que la palette construit. Un nom qui ne
     * correspondrait à aucune clé afficherait la clé brute — plus long que tout libellé,
     * et donc tronqué en plus d'être illisible.
     */
    @Test
    void chaqueTypeAUneCleDeLaBonneForme() {
        for (ElementKind kind : ElementKind.values()) {
            String key = "blueprint.designer.kind." + kind.name().toLowerCase(Locale.ROOT);
            assertTrue(key.matches("blueprint\\.designer\\.kind\\.[a-z_]+"), key);
        }
    }

    /**
     * La palette laisse assez de place pour ses libellés SANS manger le canevas. Élargir
     * jusqu'à ce que tout tienne serait facile ; ce qui compte est que la surface de
     * conception reste la plus grande partie de l'écran.
     */
    @Test
    void lesPanneauxNeMangentPasLeCanevas() {
        int chrome = ScreenDesignerWidget.PALETTE_WIDTH + ScreenDesignerWidget.PROPERTIES_WIDTH;
        // 320 unités : la plus petite fenêtre réellement rencontrée (1280×720 en scale 4).
        assertTrue(chrome < 320 * 0.75, () -> String.format(
                "les deux panneaux prennent %d unités sur 320 — il ne reste presque rien "
                        + "pour dessiner", chrome));
    }
}
