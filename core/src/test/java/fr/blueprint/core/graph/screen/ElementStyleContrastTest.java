package fr.blueprint.core.graph.screen;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le contraste des couleurs d'élément par défaut (story 10.6, AC4 ; NFR11).
 *
 * <p>Un écran qui n'utilise que les couleurs par défaut doit être <b>lisible pour
 * tous</b>. C'est une garantie qu'on ne peut pas laisser au jugement : elle se mesure,
 * par le rapport de contraste défini par les recommandations d'accessibilité du web —
 * la seule référence chiffrée qui existe pour cette question.
 *
 * <p>Le seuil retenu est 4,5:1, celui du texte courant. Pas 3:1 (grands textes) : la
 * police du jeu fait neuf pixels de haut, ce qui n'est un grand texte pour personne.
 *
 * <p>Ce test vaut aussi pour ce qu'il empêche : ajuster une couleur par défaut « pour
 * que ce soit plus joli » est exactement le geste qui casse la lisibilité sans que
 * personne ne s'en aperçoive avant qu'un joueur ne le signale.
 */
class ElementStyleContrastTest {

    /** Seuil du texte courant selon WCAG 2.1, critère 1.4.3. */
    private static final double MIN_RATIO = 4.5;

    /**
     * Luminance relative d'une couleur opaque, formule WCAG. Les composantes sont
     * linéarisées avant pondération : moyenner les valeurs brutes donnerait un résultat
     * franchement faux dans les tons sombres, qui sont justement ceux de l'interface.
     */
    private static double luminance(int rgb) {
        double[] channels = {
                ((rgb >> 16) & 0xFF) / 255.0,
                ((rgb >> 8) & 0xFF) / 255.0,
                (rgb & 0xFF) / 255.0};
        for (int i = 0; i < 3; i++) {
            channels[i] = channels[i] <= 0.03928
                    ? channels[i] / 12.92
                    : Math.pow((channels[i] + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
    }

    /**
     * Rapport de contraste entre deux couleurs, le fond étant <b>composé sur du noir</b>
     * quand il est translucide.
     *
     * <p>Le noir est le pire cas honnête : un menu s'ouvre sur le monde du jeu, qui peut
     * être une caverne comme un désert de midi. Mesurer sur un fond clair flatterait le
     * résultat pour un cas qu'on ne contrôle pas.
     */
    private static double ratio(int foregroundArgb, int backgroundArgb) {
        double lf = luminance(compositeOnBlack(foregroundArgb));
        double lb = luminance(compositeOnBlack(backgroundArgb));
        double lighter = Math.max(lf, lb);
        double darker = Math.min(lf, lb);
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static int compositeOnBlack(int argb) {
        double alpha = ((argb >>> 24) & 0xFF) / 255.0;
        int r = (int) Math.round(((argb >> 16) & 0xFF) * alpha);
        int g = (int) Math.round(((argb >> 8) & 0xFF) * alpha);
        int b = (int) Math.round((argb & 0xFF) * alpha);
        return (r << 16) | (g << 8) | b;
    }

    /**
     * <b>Le test qui compte.</b> Le texte par défaut se lit sur chacun des quatre fonds
     * d'état. Le fond au repos ne suffit pas : un bouton passe la moitié de sa vie
     * survolé ou enfoncé, et c'est au moment où on le vise qu'il faut pouvoir le lire.
     */
    @Test
    void leTexteParDefautSeLitSurTousLesEtats() {
        ElementStyle style = ElementStyle.DEFAULT;
        record Etat(String nom, int fond) {
        }
        for (Etat etat : List.of(
                new Etat("repos", style.background()),
                new Etat("survol", style.hoverBackground()),
                new Etat("enfoncé", style.pressedBackground()),
                new Etat("désactivé", style.disabledBackground()))) {
            double ratio = ratio(style.textColor(), etat.fond());
            assertTrue(ratio >= MIN_RATIO, () -> String.format(
                    "texte sur fond « %s » : %.2f:1, il faut %.1f:1",
                    etat.nom(), ratio, MIN_RATIO));
        }
    }

    /**
     * La bordure se distingue du fond. Elle est ce qui dit où finit un élément quand
     * son fond est translucide — et un menu dont on ne voit pas les bords est un menu
     * dont on ne sait pas où cliquer.
     *
     * <p>Seuil 3:1, celui des éléments d'interface non textuels (WCAG 1.4.11) : une
     * bordure n'a pas à se lire, seulement à se voir.
     */
    @Test
    void laBordureSeDistingueDuFond() {
        ElementStyle style = ElementStyle.DEFAULT;
        double ratio = ratio(style.border(), style.background());
        assertTrue(ratio >= 3.0, () -> String.format(
                "bordure sur fond : %.2f:1, il faut 3,0:1", ratio));
    }

    /**
     * Les états ne se distinguent pas QUE par la teinte : leurs luminances diffèrent
     * assez pour qu'un joueur daltonien voie la différence entre un bouton au repos et
     * un bouton survolé. C'est le NFR11 appliqué aux écrans — le même principe que la
     * forme des pins, qui double leur couleur dans l'éditeur.
     */
    @Test
    void lesEtatsSeDistinguentSansLaCouleur() {
        ElementStyle style = ElementStyle.DEFAULT;
        double repos = luminance(compositeOnBlack(style.background()));
        double survol = luminance(compositeOnBlack(style.hoverBackground()));
        double enfonce = luminance(compositeOnBlack(style.pressedBackground()));

        assertTrue(Math.abs(survol - repos) > 0.008, () -> String.format(
                "repos %.4f et survol %.4f : trop proches en luminance", repos, survol));
        assertTrue(Math.abs(survol - enfonce) > 0.008, () -> String.format(
                "survol %.4f et enfoncé %.4f : trop proches en luminance", survol, enfonce));
    }

    /** La formule elle-même, sur deux cas connus : sans quoi le test pourrait tout valider. */
    @Test
    void laMesureEstJusteSurDesCasConnus() {
        assertTrue(ratio(0xFFFFFFFF, 0xFF000000) > 20.9,
                "blanc sur noir vaut 21:1, le maximum");
        assertTrue(ratio(0xFF808080, 0xFF808080) < 1.01,
                "une couleur sur elle-même vaut 1:1");
    }
}
