package fr.blueprint.client.editor.screen;

import fr.blueprint.core.graph.screen.ElementKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La colonne de gauche du concepteur : <b>on clique la rangée qu'on voit</b>.
 *
 * <p>L'ordonnée de chaque rangée était calculée deux fois — au dessin et au clic — et les
 * deux avaient divergé sans que personne ne s'en aperçoive, parce qu'elles tombaient
 * d'accord partout sauf à un endroit. Ces tests tiennent la seule arithmétique qui reste.
 */
class DesignerPaletteTest {

    private static final int TOP = 16;
    private static final int HEIGHT = 360;

    private static DesignerPalette.Model model(List<String> screens,
                                               List<DesignerPalette.Layer> layers) {
        return new DesignerPalette.Model(screens, screens.isEmpty() ? null : screens.get(0),
                null, "", false, layers, List.of(), List.of());
    }

    private static List<DesignerPalette.Row> of(List<DesignerPalette.Row> rows,
                                                DesignerPalette.Kind kind) {
        return rows.stream().filter(r -> r.kind() == kind).toList();
    }

    /**
     * <b>Chaque rangée se clique là où elle se dessine</b>, sur toute la colonne.
     *
     * <p>C'est le test que l'ancienne arithmétique n'avait pas et qui aurait attrapé le
     * filet tracé dans le texte de « supprimer l'écran ».
     */
    @Test
    void chaqueRangeeSeCliqueLaOuElleSeDessine() {
        var m = model(List.of("guichet", "boutique"),
                List.of(new DesignerPalette.Layer("cadre", null, true),
                        new DesignerPalette.Layer("titre", "cadre", true)));

        for (DesignerPalette.Row row : DesignerPalette.rows(m, TOP, HEIGHT, 0)) {
            if (row.kind() == DesignerPalette.Kind.SECTION
                    || row.kind() == DesignerPalette.Kind.GROUP
                    || row.kind() == DesignerPalette.Kind.EMPTY) {
                continue;
            }
            var click = DesignerPalette.clickAt(m, TOP, HEIGHT, 0, 40, row.y() + 1);
            assertNotNull(click);
            assertFalse(click.hit() == DesignerPalette.Hit.NONE,
                    "la rangée « " + row.label() + " » dessinée en y=" + row.y()
                            + " ne répond pas au clic");
        }
    }

    /**
     * <b>Les calques forment un arbre : chaque enfant sous son parent.</b>
     *
     * <p>La liste précédente inversait l'ordre d'insertion et indentait selon la
     * profondeur. Le commentaire promettait « chaque enfant sous son parent », ce qui
     * n'était vrai que si l'ordre d'insertion s'y prêtait — poser un enfant après avoir posé
     * autre chose suffisait à le séparer de son parent, avec une indentation qui ne
     * désignait plus rien.
     */
    @Test
    void lesCalquesFormentUnArbre() {
        // « solde » est posé APRÈS « fermer », qui n'est pas dans le cadre. Avec un simple
        // ordre d'insertion inversé, il se retrouverait séparé de son parent.
        var m = model(List.of("g"), List.of(
                new DesignerPalette.Layer("cadre", null, true),
                new DesignerPalette.Layer("titre", "cadre", true),
                new DesignerPalette.Layer("fermer", null, true),
                new DesignerPalette.Layer("solde", "cadre", true)));

        var layers = of(DesignerPalette.rows(m, TOP, HEIGHT, 0), DesignerPalette.Kind.LAYER);
        List<String> ordre = layers.stream().map(DesignerPalette.Row::name).toList();

        assertEquals(List.of("fermer", "cadre", "solde", "titre"), ordre,
                "les enfants de « cadre » doivent le suivre immédiatement, quel que soit "
                        + "l'ordre dans lequel on les a posés");
        assertEquals(0, layers.get(1).depth());
        assertEquals(1, layers.get(2).depth(), "un enfant est indenté d'un cran");
        assertTrue(layers.get(1).expandable(), "« cadre » a des enfants : il porte un chevron");
        assertFalse(layers.get(0).expandable());
    }

    /** Un parent replié cache sa descendance entière, pas seulement ses enfants directs. */
    @Test
    void unParentReplieCacheToutSaDescendance() {
        var m = new DesignerPalette.Model(List.of("g"), "g", null, "", false,
                List.of(new DesignerPalette.Layer("cadre", null, true),
                        new DesignerPalette.Layer("titre", "cadre", true),
                        new DesignerPalette.Layer("valeur", "titre", true)),
                List.of(), List.of("cadre"));

        var noms = of(DesignerPalette.rows(m, TOP, HEIGHT, 0), DesignerPalette.Kind.LAYER)
                .stream().map(DesignerPalette.Row::name).toList();

        assertEquals(List.of("cadre"), noms,
                "replier « cadre » doit cacher « valeur » aussi, qui est son petit-enfant");
    }

    /**
     * <b>L'œil bascule la visibilité ; il ne sélectionne pas.</b>
     *
     * <p>Il était dessiné et inerte : le commentaire du code annonçait la bascule, et le
     * clic ne recevait même pas l'abscisse. Masquer un élément n'existait qu'en
     * {@code Ctrl+H}, un raccourci que rien n'annonce.
     */
    @Test
    void lOeilBasculeLaVisibiliteIlNeSelectionnePas() {
        var m = model(List.of("g"), List.of(new DesignerPalette.Layer("cadre", null, true)));
        var row = of(DesignerPalette.rows(m, TOP, HEIGHT, 0), DesignerPalette.Kind.LAYER).get(0);

        assertEquals(DesignerPalette.Hit.LAYER_VISIBILITY,
                DesignerPalette.clickAt(m, TOP, HEIGHT, 0,
                        DesignerPalette.eyeX(row) + 2, row.y() + 1).hit());
        assertEquals(DesignerPalette.Hit.LAYER_SELECT,
                DesignerPalette.clickAt(m, TOP, HEIGHT, 0,
                        DesignerPalette.nameX(row) + 4, row.y() + 1).hit(),
                "le nom sélectionne, lui");
    }

    /**
     * <b>Les actions d'un écran n'existent que sur la ligne active.</b>
     *
     * <p>« supprimer l'écran » était une ligne de texte au milieu de la liste, de la même
     * couleur et de la même taille qu'un nom d'écran. La confondre avec une donnée coûtait
     * un écran.
     */
    @Test
    void lesActionsDUnEcranNexistentQueSurLaLigneActive() {
        var m = model(List.of("guichet", "boutique"), List.of());
        var lignes = of(DesignerPalette.rows(m, TOP, HEIGHT, 0), DesignerPalette.Kind.SCREEN);
        int w = DesignerPalette.WIDTH;

        assertTrue(lignes.get(0).selected());
        assertEquals(DesignerPalette.Hit.SCREEN_DELETE,
                DesignerPalette.clickAt(m, TOP, HEIGHT, 0, w - 8, lignes.get(0).y() + 1).hit());
        assertEquals(DesignerPalette.Hit.SCREEN_SELECT,
                DesignerPalette.clickAt(m, TOP, HEIGHT, 0, w - 8, lignes.get(1).y() + 1).hit(),
                "au même endroit sur la ligne inactive : sélectionner, pas supprimer");
    }

    /** Le « + » de créer un écran vit dans l'en-tête, pas dans la liste. */
    @Test
    void lePlusVitDansLEnTete() {
        var m = model(List.of("guichet"), List.of());
        var entete = of(DesignerPalette.rows(m, TOP, HEIGHT, 0),
                DesignerPalette.Kind.SECTION).get(0);

        assertEquals(DesignerPalette.Hit.SCREEN_ADD,
                DesignerPalette.clickAt(m, TOP, HEIGHT, 0,
                        DesignerPalette.WIDTH - 8, entete.y() + 2).hit());
        assertEquals(DesignerPalette.Hit.NONE,
                DesignerPalette.clickAt(m, TOP, HEIGHT, 0, 6, entete.y() + 2).hit(),
                "le titre n'est pas un bouton");
    }

    /**
     * <b>Les calques restent atteignables quel que soit le nombre d'écrans.</b>
     *
     * <p>Les trois sections s'empilaient sans défiler : à huit écrans, les calques passaient
     * sous le bord de la fenêtre et il n'existait plus aucun moyen de les atteindre — alors
     * qu'ils sont la seule façon de sélectionner un conteneur recouvert par ses enfants.
     */
    @Test
    void lesCalquesRestentAtteignablesAvecBeaucoupDEcrans() {
        var beaucoup = new java.util.ArrayList<String>();
        for (int i = 0; i < 12; i++) {
            beaucoup.add("ecran" + i);
        }
        var m = model(beaucoup, List.of(new DesignerPalette.Layer("cadre", null, true)));

        assertTrue(of(DesignerPalette.rows(m, TOP, HEIGHT, 0),
                        DesignerPalette.Kind.LAYER).isEmpty(),
                "sans défiler, le calque est bien hors de vue — c'est le défaut");

        int bas = DesignerPalette.contentRows(m);
        var enBas = of(DesignerPalette.rows(m, TOP, HEIGHT, bas), DesignerPalette.Kind.LAYER);

        assertFalse(enBas.isEmpty(), "en défilant jusqu'en bas, on doit l'atteindre");
        assertEquals("cadre", enBas.get(0).name());
    }

    /**
     * Les douze types sont groupés, et chacun n'apparaît qu'une fois.
     *
     * <p>Le groupe suit ce que le modèle sait dire d'un type — conteneur, interactif — et
     * non un classement inventé qui se démentirait au premier type ajouté.
     */
    @Test
    void lesTypesSontGroupesEtChacunApparaitUneFois() {
        var m = model(List.of("g"), List.of());
        var types = of(DesignerPalette.rows(m, TOP, HEIGHT, 0), DesignerPalette.Kind.ELEMENT)
                .stream().map(DesignerPalette.Row::element).toList();

        // La colonne ne montre pas tout d'un coup ; on lit le contenu complet.
        var tous = DesignerPalette.content(m).stream()
                .filter(r -> r.kind() == DesignerPalette.Kind.ELEMENT)
                .map(DesignerPalette.Row::element).toList();

        assertEquals(ElementKind.values().length, tous.size(),
                "chaque type doit être proposé, et une seule fois");
        assertEquals(tous.size(), java.util.Set.copyOf(tous).size());
        assertEquals(DesignerPalette.Group.CONTAINER, DesignerPalette.groupOf(ElementKind.PANEL));
        assertEquals(DesignerPalette.Group.INTERACTIVE, DesignerPalette.groupOf(ElementKind.BUTTON));
        assertEquals(DesignerPalette.Group.DISPLAY, DesignerPalette.groupOf(ElementKind.LABEL));
        assertFalse(types.isEmpty());
    }

    /** Sans écran ni calque, la colonne dit quoi faire au lieu de rester vide. */
    @Test
    void uneListeVideDitQuoiFaire() {
        var vides = of(DesignerPalette.rows(DesignerPalette.Model.EMPTY, TOP, HEIGHT, 0),
                DesignerPalette.Kind.EMPTY);

        assertEquals(2, vides.size(),
                "un panneau vide sans un mot ne se distingue pas d'un panneau cassé");
    }

    /** Celui qu'on renomme montre la frappe, pas son ancien nom. */
    @Test
    void celuiQuOnRenommeMontreLaFrappe() {
        var m = new DesignerPalette.Model(List.of("guichet"), "guichet", "guichet", "bouti",
                false, List.of(), List.of(), List.of());

        assertEquals("bouti_", of(DesignerPalette.rows(m, TOP, HEIGHT, 0),
                DesignerPalette.Kind.SCREEN).get(0).label());
    }
}
