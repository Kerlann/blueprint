package fr.blueprint.client.editor.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;

/**
 * Les onglets « Graphe » / « Écrans » (story 10.2, AC1).
 *
 * <p>Un onglet dans le MÊME écran, pas une seconde fenêtre : on passe du graphe à
 * l'écran et retour sans fermer ni réenregistrer. Deux écrans distincts obligeraient à
 * choisir lequel détient la session, laquelle des deux piles d'annulation fait foi, et
 * ce qu'il advient d'un {@code Ctrl+S} lancé depuis l'autre.
 *
 * <p>Ils vivent <b>dans la barre d'outils</b>, entre le titre et les boutons. Ils
 * occupaient auparavant une seconde bande sous elle, alors que celle-ci est vide en son
 * milieu : treize pixels de canevas perdus sur toute la hauteur de l'éditeur, pour ce
 * qui tient dans une barre déjà présente.
 *
 * <p>Géométrie et rendu purs, comme {@code ToolbarWidget} : le hit-test et le dessin
 * lisent la même arithmétique, donc l'onglet se clique là où il se voit.
 */
public final class ModeTabs {

    /** Le mode d'édition courant de l'éditeur. */
    /**
     * Le mode d'édition courant.
     *
     * <p>{@code FUNCTIONS} s'ajoute sans rien changer d'autre : le rendu et le hit-test
     * bouclent tous deux sur { values()}. C'est ce que vaut une géométrie écrite une
     * fois plutôt que deux « if » — et c'est la 10.2 qui l'a payé.
     */
    public enum Mode {
        GRAPH, SCREENS, FUNCTIONS;

        /**
         * Cet onglet montre-t-il le <b>concepteur d'écrans</b> ? Sinon, c'est le canevas.
         *
         * <p>La question vit ici parce qu'elle se posait ailleurs <b>deux fois sous deux
         * formes</b> : le rendu demandait « est-ce le graphe ? » et tout le routage des
         * entrées demandait « est-ce les écrans ? ». Tant qu'il n'y avait que deux modes les
         * deux formulations coïncidaient. L'arrivée d'un troisième onglet a suffi à les
         * séparer : « Fonctions » dessinait le concepteur pendant que les clics partaient au
         * canevas — un écran qui ne réagit pas à ce qu'il montre.
         *
         * <p>Un {@code switch} exhaustif : le prochain mode ne compilera pas sans qu'on ait
         * dit sur quelle surface il vit.
         */
        public boolean showsDesigner() {
            return switch (this) {
                case SCREENS -> true;
                case GRAPH, FUNCTIONS -> false;
            };
        }

        /**
         * Passer à cet onglet doit-il <b>refermer</b> le corps de fonction ouvert ?
         *
         * <p>L'onglet Graphe montre le graphe : y revenir en laissant un corps ouvert
         * afficherait le corps sous la mauvaise étiquette, et le geste suivant tomberait
         * dans un graphe que rien n'annonce.
         *
         * <p>{@code SCREENS} ne le referme pas : le concepteur d'écrans ne montre aucun des
         * deux graphes, donc rien n'y ment, et revenir aux fonctions retrouve l'endroit où
         * l'on travaillait.
         */
        public boolean closesFunctionBody() {
            return switch (this) {
                case GRAPH -> true;
                case SCREENS, FUNCTIONS -> false;
            };
        }
    }


    private static final int ACTIVE_BACKGROUND = 0xFF2B2D31;
    private static final int ACTIVE_TEXT = 0xFFE6E6E6;
    private static final int IDLE_TEXT = 0xFF8A909A;
    private static final int PADDING = 8;

    private ModeTabs() {
    }

    private static String label(Mode mode) {
        return I18n.get(switch (mode) {
            case GRAPH -> "blueprint.editor.tab.graph";
            case SCREENS -> "blueprint.editor.tab.screens";
            case FUNCTIONS -> "blueprint.editor.tab.functions";
        });
    }

    /** Largeur d'un onglet — la même formule au rendu et au clic. */
    private static int widthOf(Font font, Mode mode) {
        return font.width(label(mode)) + PADDING * 2;
    }

    /** Largeur totale du bandeau — il s'arrête après le dernier onglet. */
    public static int totalWidth(Font font) {
        int total = 0;
        for (Mode mode : Mode.values()) {
            total += widthOf(font, mode);
        }
        return total;
    }

    /**
     * Dessine les onglets dans une barre existante, à partir de {@code left} et sur
     * {@code barHeight} de haut.
     *
     * <p>L'onglet actif est marqué par un filet EN BAS et non par un fond plein : dans
     * une barre partagée, un pavé de couleur derrière le mot se disputerait l'attention
     * avec les boutons d'action, qui sont à trois centimètres de là.
     */
    public static void render(GuiGraphics g, Font font, Mode active, int left, int barHeight) {
        int x = left;
        for (Mode mode : Mode.values()) {
            int w = widthOf(font, mode);
            if (mode == active) {
                g.fill(x, 1, x + w, barHeight - 1, ACTIVE_BACKGROUND);
                g.fill(x, barHeight - 2, x + w, barHeight - 1,
                        fr.blueprint.client.theme.Theme.current().nodeSelected());
            }
            g.drawString(font, label(mode), x + PADDING, (barHeight - font.lineHeight) / 2 + 1,
                    mode == active ? ACTIVE_TEXT : IDLE_TEXT, false);
            x += w;
        }
    }

    /** L'onglet sous la souris dans la barre, ou {@code null}. */
    public static @Nullable Mode modeAt(Font font, double mx, double my,
                                        int left, int barHeight) {
        if (my < 0 || my >= barHeight) {
            return null;
        }
        int x = left;
        for (Mode mode : Mode.values()) {
            int w = widthOf(font, mode);
            if (mx >= x && mx < x + w) {
                return mode;
            }
            x += w;
        }
        return null;
    }
}
