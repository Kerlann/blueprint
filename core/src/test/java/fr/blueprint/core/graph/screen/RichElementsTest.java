package fr.blueprint.core.graph.screen;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Les éléments riches (story 10.8) : ce que leur état calcule, hors de tout dessin.
 *
 * <p>Trois mécaniques que les cinq types d'origine n'avaient pas — le défilement d'une
 * liste, le filtre d'une saisie, l'alignement d'un curseur. Chacune est de
 * l'arithmétique, et chacune casse en silence : un décalage d'une ligne, une valeur
 * arrondie autrement au dessin qu'à l'envoi, un filtre plus permissif côté serveur que
 * côté client. Rien de tout cela ne se voit en regardant l'écran.
 */
class RichElementsTest {

    // -------------------------------------------------------- défilement d'une liste

    @Test
    void uneListeNeMontreQueLesLignesQuiTiennent() {
        // 100 unités de haut, lignes de 12 : huit lignes entières, pas huit et demie.
        ListView view = ListView.of(50, 100, 12);

        assertEquals(8, view.visibleRows(), "une ligne coupée en deux ne se compte pas");
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7), view.visibleIndices());
        assertTrue(view.scrollable());
    }

    @Test
    void leDefilementResteDansLesBornes() {
        ListView view = ListView.of(10, 60, 12);   // 5 lignes visibles sur 10

        assertEquals(0, view.scrolledBy(-3).offset(), "on ne défile pas au-dessus du haut");
        assertEquals(5, view.scrolledBy(99).offset(), "ni en dessous de la dernière page");
        assertEquals(List.of(5, 6, 7, 8, 9), view.scrolledBy(99).visibleIndices());
    }

    /**
     * <b>Le test qui compte.</b> L'indice rendu est celui de l'ENTRÉE, pas le rang à
     * l'écran. Les confondre donne un clic juste tant qu'on n'a pas défilé, et faux
     * ensuite — le pire des défauts, puisqu'il passe tous les essais rapides.
     */
    @Test
    void lIndiceCliqueEstCeluiDeLEntreeEtNonDuRangAffiche() {
        ListView view = ListView.of(20, 60, 12).scrolledBy(7);

        assertEquals(7, view.indexAt(0, 12), "première ligne visible = huitième entrée");
        assertEquals(9, view.indexAt(25, 12), "troisième ligne visible");
        assertEquals(11, view.indexAt(59, 12), "dernière ligne visible");
    }

    @Test
    void cliquerHorsDesLignesNeRendRien() {
        ListView view = ListView.of(3, 60, 12);   // 3 entrées, 5 lignes de place

        assertEquals(-1, view.indexAt(-1, 12), "au-dessus");
        assertEquals(2, view.indexAt(30, 12), "la dernière entrée");
        assertEquals(-1, view.indexAt(42, 12), "sous la dernière ENTRÉE, dans le vide");
        assertEquals(-1, view.indexAt(200, 12), "hors du cadre");
    }

    @Test
    void uneListeVideOuMinusculeNeCasseRien() {
        assertEquals(List.of(), ListView.of(0, 100, 12).visibleIndices());
        assertFalse(ListView.of(0, 100, 12).scrollable());
        assertEquals(0, ListView.rowsThatFit(100, 1), "une ligne d'un pixel est refusée");
        assertEquals(0, ListView.rowsThatFit(0, 12));
        assertEquals(-1, ListView.of(5, 0, 12).indexAt(0, 12), "aucune place : aucun clic");
    }

    /**
     * Le curseur de défilement garde une taille saisissable. Strictement proportionnel,
     * il ferait un pixel pour mille entrées — ni visible ni attrapable.
     */
    @Test
    void leCurseurDeDefilementResteSaisissable() {
        ListView many = ListView.of(1000, 60, 12);
        assertTrue(many.thumbFraction() >= 0.1, "taille plancher : " + many.thumbFraction());
        assertEquals(0, many.thumbPosition(), 1e-9, "en haut au départ");
        assertEquals(1, many.scrolledBy(9999).thumbPosition(), 1e-9, "en bas à la fin");
        assertEquals(1, ListView.of(3, 100, 12).thumbFraction(), 1e-9,
                "rien à défiler : le curseur remplit la piste");
    }

    // ------------------------------------------------------------ filtre de saisie

    /**
     * <b>Le second test qui compte.</b> Le filtre est la MÊME fonction des deux côtés :
     * le client refuse une frappe, le serveur refuse un paquet (FR52). Deux
     * implémentations divergeraient, et c'est la plus permissive qui déciderait.
     */
    @Test
    void leFiltreAccepteEtRefuseLaMemeChoseDesDeuxCotes() {
        var integer = ElementOptions.input("", 8, ElementOptions.InputFilter.INTEGER);
        assertTrue(integer.accepts("42"));
        assertTrue(integer.accepts("-7"));
        assertTrue(integer.accepts(""), "un champ vide est un état de saisie normal");
        assertFalse(integer.accepts("4.2"));
        assertFalse(integer.accepts("douze"));

        var decimal = ElementOptions.input("", 8, ElementOptions.InputFilter.DECIMAL);
        assertTrue(decimal.accepts("4.2"));
        assertTrue(decimal.accepts("4,2"), "le joueur tape le séparateur de sa langue");
        assertFalse(decimal.accepts("4.2.3"));

        var id = ElementOptions.input("", 32, ElementOptions.InputFilter.IDENTIFIER);
        assertTrue(id.accepts("mon_pack-2"));
        assertFalse(id.accepts("mon pack"));
        assertFalse(id.accepts("café"));
    }

    /**
     * Un client modifié qui envoie dix mille caractères dans un champ limité à seize est
     * <b>ignoré</b>, jamais tronqué : tronquer laisserait croire à une saisie que le
     * joueur n'a pas faite, et l'écrirait dans une variable.
     */
    @Test
    void unTexteTropLongEstRefuseEtNonTronque() {
        var options = ElementOptions.input("Nom", 16, ElementOptions.InputFilter.TEXT);

        assertTrue(options.accepts("seize caracter"));
        assertFalse(options.accepts("x".repeat(17)));
        assertFalse(options.accepts("x".repeat(10_000)));
        assertFalse(options.accepts(null), "un texte absent n'est pas un texte vide");
    }

    /** La longueur demandée est elle-même bornée : un champ de 100 000 n'existe pas. */
    @Test
    void laLongueurDemandeeEstBornee() {
        var absurd = ElementOptions.input("", 100_000, ElementOptions.InputFilter.TEXT);
        assertEquals(ElementOptions.MAX_INPUT_LENGTH, absurd.maxLength());
        assertFalse(absurd.accepts("x".repeat(ElementOptions.MAX_INPUT_LENGTH + 1)));

        assertEquals(1, ElementOptions.input("", 0, ElementOptions.InputFilter.TEXT).maxLength(),
                "zéro caractère rendrait le champ inutilisable");
    }

    // ------------------------------------------------------------------- curseur

    /**
     * L'alignement sur le pas se fait <b>une fois</b>, et pas au dessin : sinon le joueur
     * relâche sur 7 et le graphe reçoit 6,83.
     */
    @Test
    void unCurseurSAligneSurSonPas() {
        var options = ElementOptions.slider(0, 10, 2);

        assertEquals(4, options.snap(4.4), 1e-9);
        assertEquals(6, options.snap(5.2), 1e-9);
        assertEquals(0, options.snap(-99), 1e-9, "borné en bas");
        assertEquals(10, options.snap(99), 1e-9, "et en haut");
    }

    @Test
    void sansPasLeCurseurEstContinu() {
        var options = ElementOptions.slider(0, 1, 0);
        assertEquals(0.37, options.snap(0.37), 1e-9);
    }

    /** Position et valeur sont réciproques : le curseur revient là où on l'a lâché. */
    @Test
    void positionEtValeurSontReciproques() {
        var options = ElementOptions.slider(10, 20, 0);

        assertEquals(0.5, options.fractionOf(15), 1e-9);
        assertEquals(15, options.valueAt(0.5), 1e-9);
        assertEquals(0, options.fractionOf(-5), 1e-9, "hors plage : collé au bord");
        assertEquals(20, options.valueAt(2), 1e-9);
    }

    /**
     * Une plage nulle diviserait par zéro à chaque image. La refuser à la construction
     * plutôt qu'au rendu : le rendu tourne soixante fois par seconde et n'a aucun moyen
     * de signaler quoi que ce soit.
     */
    @Test
    void unePlageNulleNeDivisePasParZero() {
        var options = ElementOptions.slider(5, 5, 0);
        assertTrue(Double.isFinite(options.fractionOf(5)));
        assertTrue(Double.isFinite(options.valueAt(0.5)));
        assertTrue(options.max() > options.min());
    }

    // ------------------------------------------------------------ les modifications

    /** Les lignes voyagent jointes, et reviennent découpées à l'identique. */
    @Test
    void lesLignesFontLAllerRetourParLeChampTexte() {
        var update = ScreenUpdate.lines("menu", "liste",
                List.of("Pomme", "Épée en fer", "Potion"));

        assertEquals(ScreenUpdate.Kind.LINES, update.kind());
        assertEquals(List.of("Pomme", "Épée en fer", "Potion"), update.linesValue());
        assertEquals(3, update.number(), 1e-9, "le compte voyage aussi");
        assertEquals(List.of(), ScreenUpdate.lines("menu", "liste", List.of()).linesValue());
    }

    /** Une modification qui n'est pas des lignes n'en rend pas. */
    @Test
    void seulesLesModificationsDeListeRendentDesLignes() {
        assertEquals(List.of(),
                ScreenUpdate.text("menu", "titre", ScreenText.literal("a\nb")).linesValue());
    }
}
