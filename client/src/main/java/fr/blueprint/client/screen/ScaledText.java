package fr.blueprint.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Le texte d'un élément, à l'échelle que son style demande (story 10.17).
 *
 * <p>La police de Minecraft n'existe qu'à <b>une</b> taille : huit pixels de haut. On
 * l'agrandit par une transformation, pas en choisissant une autre fonte — d'où un
 * <i>facteur</i> dans le style plutôt qu'une taille en points, qui aurait fini arrondie au
 * facteur le plus proche de toute façon.
 *
 * <p><b>Mesurer et tracer passent par ici tous les deux.</b> C'est la seule chose qui
 * compte : une largeur mesurée sans le facteur tronquerait un texte qui tient, ou en
 * laisserait déborder un qui ne tient pas. Ce projet a déjà payé deux fois pour avoir
 * séparé la mesure du tracé — le concepteur dessinait à 320×180 pendant que le clic
 * résolvait à la taille simulée, et « dès qu'on quittait le préréglage le plus petit, on
 * cliquait à côté de ce qu'on voyait ».
 */
public final class ScaledText {

    private ScaledText() {
    }

    /** La hauteur d'une ligne à cette échelle. */
    public static int lineHeight(Font font, double scale) {
        return (int) Math.round(font.lineHeight * scale);
    }

    /** La largeur d'un texte à cette échelle. */
    public static int width(Font font, String text, double scale) {
        return (int) Math.round(font.width(text) * scale);
    }

    /**
     * Le texte tronqué pour tenir dans {@code room} pixels <b>à cette échelle</b>.
     *
     * <p>La place disponible est ramenée à l'échelle 1 avant d'interroger la police :
     * c'est dans ce repère qu'elle sait couper.
     */
    public static String fit(Font font, String text, int room, double scale) {
        return font.plainSubstrByWidth(text, (int) Math.floor(room / Math.max(0.01, scale)));
    }

    /** Trace {@code text} en {@code (x, y)}, agrandi depuis ce coin. */
    public static void draw(GuiGraphics g, Font font, String text, int x, int y,
                            int color, double scale) {
        if (scale == 1) {
            g.drawString(font, text, x, y, color, false);
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        g.pose().scale((float) scale, (float) scale);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popMatrix();
    }
}
