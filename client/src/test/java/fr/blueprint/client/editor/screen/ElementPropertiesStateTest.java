package fr.blueprint.client.editor.screen;

import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.graph.screen.Anchor;
import fr.blueprint.core.graph.screen.ElementKind;
import fr.blueprint.core.graph.screen.Extent;
import fr.blueprint.core.graph.screen.ScreenElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le panneau de propriétés (story 10.2, AC7). Deux exigences : rien d'invalide n'entre
 * dans le modèle, et l'auteur le sait <b>pendant</b> qu'il tape.
 */
class ElementPropertiesStateTest {

    private static final Predicate<String> LIBRE = name -> !name.isBlank() && !name.equals("pris");

    private ElementPropertiesState state;

    @BeforeEach
    void setUp() {
        state = new ElementPropertiesState();
        state.select(ScreenElement.of("bouton", ElementKind.BUTTON, 12, 34, 60, 20));
    }

    private void typeAll(String text) {
        for (char c : text.toCharArray()) {
            state.type(c);
        }
    }

    private static java.util.List<ElementPropertiesState.Field> shown(ScreenElement element) {
        java.util.List<ElementPropertiesState.Field> out = new java.util.ArrayList<>();
        for (var field : ElementPropertiesState.Field.values()) {
            if (ElementPropertiesState.applies(element, field, false)) {
                out.add(field);
            }
        }
        return out;
    }

    /**
     * <b>Un type ne montre que ce qui le concerne.</b>
     *
     * <p>La règle retombait sur {@code default -> true} : onze champs sans objet
     * s'affichaient sur chaque élément. Un simple libellé proposait « Indication »,
     * « Longueur max », « Pas », « Hauteur de ligne » et « Type d'entité » — exactement ce
     * que le commentaire voisin disait vouloir éviter : « un champ rempli sans effet est
     * ce qui fait douter d'un outil ».
     */
    @Test
    void unTypeNeMontreQueCeQuiLeConcerne() {
        var libelle = shown(ScreenElement.of("t", ElementKind.LABEL, 0, 0, 40, 20));

        for (var absent : java.util.List.of(
                ElementPropertiesState.Field.PLACEHOLDER,
                ElementPropertiesState.Field.MAX_LENGTH,
                ElementPropertiesState.Field.STEP,
                ElementPropertiesState.Field.ROW_HEIGHT,
                ElementPropertiesState.Field.ENTITY,
                ElementPropertiesState.Field.OPT_MIN,
                ElementPropertiesState.Field.OPT_MAX)) {
            assertFalse(libelle.contains(absent),
                    "un libellé n'a que faire de « " + absent + " »");
        }
        assertTrue(libelle.contains(ElementPropertiesState.Field.TEXT),
                "en revanche il porte du texte");
    }

    /**
     * <b>Aucun champ n'est proposé deux fois.</b>
     *
     * <p>Six l'étaient : les quatre réglages de liaison et les deux de saisie
     * apparaissaient dans la boucle générale <i>et</i> dans leur section dédiée. Deux
     * lignes distinctes éditaient la même valeur, et corriger l'une ne montrait rien sur
     * l'autre.
     */
    @Test
    void aucunChampNestProposeDeuxFois() {
        var saisie = shown(ScreenElement.of("s", ElementKind.INPUT, 0, 0, 80, 20));

        for (var double_ : java.util.List.of(
                ElementPropertiesState.Field.PLACEHOLDER,
                ElementPropertiesState.Field.MAX_LENGTH,
                ElementPropertiesState.Field.BIND_FORMAT,
                ElementPropertiesState.Field.BIND_DECIMALS,
                ElementPropertiesState.Field.BIND_MIN,
                ElementPropertiesState.Field.BIND_MAX)) {
            assertFalse(saisie.contains(double_),
                    "« " + double_ + " » a sa section : la boucle générale ne doit pas "
                            + "l'afficher aussi");
        }
    }

    /**
     * <b>Tout élément peut porter une étiquette.</b>
     *
     * <p>Correction d'une croyance fausse : j'avais réservé « Texte » aux types « qui
     * parlent » et l'avais retiré de l'image, de la barre, de l'emplacement et de l'aperçu
     * d'entité. Le peintre, lui, appelle {@code paintText} pour <b>tous</b> les types sauf
     * la liste déroulante et dessine leur texte dès qu'il n'est pas vide. Masquer le champ
     * retirait donc une capacité qui existe — une barre de progression peut porter son nom.
     */
    @Test
    void toutElementPeutPorterUneEtiquette() {
        for (ElementKind kind : ElementKind.values()) {
            var champs = shown(ScreenElement.of("e", kind, 0, 0, 40, 20));
            assertTrue(champs.contains(ElementPropertiesState.Field.TEXT),
                    kind + " : le peintre dessine son texte, le panneau doit le proposer");
            assertTrue(champs.contains(ElementPropertiesState.Field.TEXT_COLOR),
                    kind + " : et sa couleur");
        }
    }

    /**
     * <b>Les sections se suivent sans jamais revenir en arrière.</b>
     *
     * <p>Le panneau émet ses champs dans l'ordre de l'énumération et pose un en-tête au
     * premier champ de chaque section. Si l'ordre des champs ne suivait pas celui des
     * sections, un titre réapparaîtrait plus bas — « Apparence » deux fois, avec deux
     * moitiés de la même chose de part et d'autre d'un autre sujet.
     */
    @Test
    void lesSectionsSeSuiventSansJamaisRevenirEnArriere() {
        // Seules les quatre premières sections sortent de la boucle générale. Disposition,
        // réglages, liaison et styles ont chacun leur bloc, posé après elle et dans l'ordre
        // voulu — c'est pour cela que leurs champs sont exclus de la boucle.
        var parLaBoucle = java.util.EnumSet.of(
                ElementPropertiesState.Section.IDENTITY,
                ElementPropertiesState.Section.POSITION,
                ElementPropertiesState.Section.SIZE,
                ElementPropertiesState.Section.APPEARANCE);
        int precedent = -1;
        for (ElementPropertiesState.Field field : ElementPropertiesState.Field.values()) {
            var section = ElementPropertiesState.sectionOf(field);
            if (!parLaBoucle.contains(section)) {
                continue;
            }
            assertTrue(section.ordinal() >= precedent,
                    "« " + field + " » appartient à " + section + ", qui vient avant la "
                            + "section du champ précédent : son en-tête serait posé une "
                            + "seconde fois, avec deux moitiés du même sujet de part et "
                            + "d'autre d'un autre");
            precedent = section.ordinal();
        }
    }

    /**
     * <b>Ce qui se tape sur une ligne, et ce qui mérite la sienne.</b>
     *
     * <p>Sur une seule ligne la valeur commence après le libellé et il lui reste environ
     * soixante-douze pixels — une douzaine de caractères. Un nom, une texture ou un format
     * n'y tiennent pas ; un nombre, toujours. Le partage se fait par nature et non sur la
     * valeur du moment, qui ferait sauter la disposition d'une frappe à l'autre.
     */
    @Test
    void ceQuiMeriteSaPropreLigne() {
        for (var texte : java.util.List.of(ElementPropertiesState.Field.NAME,
                ElementPropertiesState.Field.TEXT,
                ElementPropertiesState.Field.TEXTURE,
                ElementPropertiesState.Field.BIND_FORMAT)) {
            assertTrue(ElementPropertiesState.needsOwnLine(texte),
                    texte + " porte du texte : douze caractères ne suffisent pas");
        }
        for (var nombre : java.util.List.of(ElementPropertiesState.Field.X,
                ElementPropertiesState.Field.PADDING,
                ElementPropertiesState.Field.COLUMNS,
                ElementPropertiesState.Field.BIND_DECIMALS)) {
            assertFalse(ElementPropertiesState.needsOwnLine(nombre),
                    nombre + " tient toujours : lui donner une ligne doublerait la hauteur "
                            + "du panneau pour rien");
        }
    }

    /**
     * <b>Une barre nourrie d'une chaîne reste vide pour toujours.</b>
     *
     * <p>{@code renderProgress} lit {@code value instanceof Number} et rend zéro sinon.
     * Proposer une variable texte pour une barre revient donc à proposer une panne — que
     * l'auteur découvrirait en jeu, devant une barre qui ne bouge jamais.
     */
    @Test
    void uneBarreNAccepteQueDesNombres() {
        var progress = fr.blueprint.core.graph.screen.ElementBinding.Target.PROGRESS;

        assertTrue(ElementPropertiesState.acceptsVariable(progress, PinTypes.DOUBLE));
        assertTrue(ElementPropertiesState.acceptsVariable(progress, PinTypes.INT));
        assertFalse(ElementPropertiesState.acceptsVariable(progress, PinTypes.STRING),
                "une chaîne donnerait zéro à chaque image");
        assertFalse(ElementPropertiesState.acceptsVariable(progress, PinTypes.ENTITY));
    }

    /**
     * <b>Le texte et les drapeaux acceptent tout, et c'est le moteur qui le dit.</b>
     *
     * <p>{@code renderText} formate n'importe quoi, {@code renderFlag} a un cas par défaut
     * qui rend vrai. Restreindre ces trois cibles serait inventer une règle que le moteur
     * n'applique pas — et refuser un choix qui marcherait.
     */
    @Test
    void leTexteEtLesDrapeauxAcceptentTout() {
        for (var cible : java.util.List.of(
                fr.blueprint.core.graph.screen.ElementBinding.Target.TEXT,
                fr.blueprint.core.graph.screen.ElementBinding.Target.ENABLED,
                fr.blueprint.core.graph.screen.ElementBinding.Target.VISIBLE)) {
            for (var type : java.util.List.of(PinTypes.STRING, PinTypes.DOUBLE,
                    PinTypes.BOOL, PinTypes.ENTITY, PinTypes.ITEMSTACK)) {
                assertTrue(ElementPropertiesState.acceptsVariable(cible, type),
                        cible + " refuse " + type + " alors que le moteur l'accepte");
            }
        }
    }

    /** Une texture se lit d'une référence écrite ; un nombre n'en produit jamais. */
    @Test
    void uneTextureSeLitDunTexte() {
        var texture = fr.blueprint.core.graph.screen.ElementBinding.Target.TEXTURE;

        assertTrue(ElementPropertiesState.acceptsVariable(texture, PinTypes.STRING));
        assertFalse(ElementPropertiesState.acceptsVariable(texture, PinTypes.DOUBLE));
    }

    /** Chaque section a sa clé, écrite en toutes lettres pour le contrôle des clés mortes. */
    @Test
    void chaqueSectionAsaCle() {
        for (var section : ElementPropertiesState.Section.values()) {
            assertTrue(section.key().startsWith("blueprint.designer.section."),
                    section + " : " + section.key());
        }
        assertEquals(ElementPropertiesState.Section.values().length,
                java.util.Arrays.stream(ElementPropertiesState.Section.values())
                        .map(ElementPropertiesState.Section::key).distinct().count(),
                "deux sections qui partagent une clé porteraient le même titre");
    }

    /** Une texture ne se règle que sur une image, et le survol que sur ce qui réagit. */
    @Test
    void latextureEtLeSurvolSontReserves() {
        assertTrue(shown(ScreenElement.of("i", ElementKind.IMAGE, 0, 0, 40, 20))
                .contains(ElementPropertiesState.Field.TEXTURE));
        assertFalse(shown(ScreenElement.of("t", ElementKind.LABEL, 0, 0, 40, 20))
                .contains(ElementPropertiesState.Field.TEXTURE),
                "le modèle réserve la texture aux images ; le panneau la proposait partout");
        assertTrue(shown(ScreenElement.of("b", ElementKind.BUTTON, 0, 0, 40, 20))
                .contains(ElementPropertiesState.Field.HOVER));
        assertFalse(shown(ScreenElement.of("t", ElementKind.LABEL, 0, 0, 40, 20))
                .contains(ElementPropertiesState.Field.HOVER),
                "un libellé ne réagit pas au survol");
    }

    /**
     * La valeur d'un axe en mode « Ajuster » ne veut rien dire.
     *
     * <p>{@code sizeValueMatters} existait et n'était appelé nulle part : on pouvait taper
     * un nombre sans le moindre effet, la taille venant des enfants.
     */
    @Test
    void laValeurDunAxeEnAjusterNeVeutRienDire() {
        var ajuste = ScreenElement.of("c", ElementKind.PANEL, 0, 0, 40, 20)
                .resized(fr.blueprint.core.graph.screen.Extent.hug(),
                        fr.blueprint.core.graph.screen.Extent.of(20));
        state.select(ajuste);

        assertFalse(state.sizeValueMatters(true), "la largeur vient des enfants");
        assertTrue(state.sizeValueMatters(false), "la hauteur, elle, reste écrite");
    }

    private void edit(ElementPropertiesState.Field field, String text) {
        state.beginEdit(field);
        for (int i = state.buffer().length(); i > 0; i--) {
            state.backspace();
        }
        typeAll(text);
    }

    @Test
    void lesChampsAffichentLaValeurCourante() {
        assertEquals("bouton", state.valueOf(ElementPropertiesState.Field.NAME));
        assertEquals("12", state.valueOf(ElementPropertiesState.Field.X));
        assertEquals("60", state.valueOf(ElementPropertiesState.Field.WIDTH));
        assertEquals("", state.valueOf(ElementPropertiesState.Field.TEXTURE));
    }

    @Test
    void unPourcentageSAfficheCommeEnBScript() {
        state.select(ScreenElement.of("a", ElementKind.PANEL, 0, 0, 10, 10)
                .resized(Extent.percent(0.5, 0, 0), Extent.of(20)));
        assertEquals("50%", state.valueOf(ElementPropertiesState.Field.WIDTH));
    }

    /**
     * <b>Le test qui compte.</b> Sans tampon de frappe, taper « -1 » serait impossible :
     * le « - » seul ne se convertit pas, la conversion échouerait, et le champ
     * reviendrait à sa valeur d'avant à chaque caractère.
     */
    @Test
    void uneFrappeIntermediaireInvalideNEcrasePasLeChamp() {
        state.beginEdit(ElementPropertiesState.Field.X);
        for (int i = state.buffer().length(); i > 0; i--) {
            state.backspace();
        }
        state.type('-');
        assertFalse(state.valid(LIBRE), "« - » seul n'est pas un nombre");
        assertNull(state.commit(LIBRE), "et rien n'est écrit");

        state.type('1');
        assertTrue(state.valid(LIBRE));
        assertEquals(-1, state.commit(LIBRE).x(), 1e-9);
    }

    @Test
    void unNomDejaPrisSeVoitPendantLaFrappe() {
        edit(ElementPropertiesState.Field.NAME, "pris");
        assertFalse(state.valid(LIBRE));

        edit(ElementPropertiesState.Field.NAME, "libre");
        assertTrue(state.valid(LIBRE));
        assertEquals("libre", state.pendingName());
    }

    @Test
    void unNomVideNEstPasUnNom() {
        edit(ElementPropertiesState.Field.NAME, "   ");
        assertFalse(state.valid(LIBRE));
    }

    @Test
    void unePositionSecritDansLeModele() {
        edit(ElementPropertiesState.Field.Y, "80");
        ScreenElement out = state.commit(LIBRE);
        assertEquals(80, out.y(), 1e-9);
        assertEquals(12, out.x(), 1e-9, "l'autre axe ne bouge pas");
        assertNull(state.editing(), "et le champ se referme");
    }

    /** Une taille tapée en pourcentage le reste : la nature du champ suit la frappe. */
    @Test
    void unePourcentageTapeResteRelatif() {
        edit(ElementPropertiesState.Field.WIDTH, "40%");
        ScreenElement out = state.commit(LIBRE);
        assertTrue(out.width().relative());
        assertEquals(0.4, out.width().value(), 1e-9);

        state.select(out);
        edit(ElementPropertiesState.Field.WIDTH, "90");
        assertFalse(state.commit(LIBRE).width().relative(), "et repasse en unités si on l'écrit");
    }

    @Test
    void unDieseFaitDuTexteUneCleDeTraduction() {
        edit(ElementPropertiesState.Field.TEXT, "#menu.acheter");
        var text = state.commit(LIBRE).text();
        assertTrue(text.translate(), "NFR10 : traduisible sans quitter le panneau");
        assertEquals("menu.acheter", text.value());

        state.select(ScreenElement.of("b", ElementKind.LABEL, 0, 0, 10, 10));
        edit(ElementPropertiesState.Field.TEXT, "Acheter");
        assertFalse(state.commit(LIBRE).text().translate());
    }

    @Test
    void uneTextureInvalideEstRefuseeEtLeVideLEfface() {
        edit(ElementPropertiesState.Field.TEXTURE, "PAS UN ID");
        assertFalse(state.valid(LIBRE));
        assertNull(state.commit(LIBRE));

        edit(ElementPropertiesState.Field.TEXTURE, "boutique:textures/gui/fond.png");
        assertNotNull(state.commit(LIBRE).texture());

        state.select(ScreenElement.of("c", ElementKind.IMAGE, 0, 0, 10, 10)
                .withTexture(net.minecraft.resources.Identifier
                        .fromNamespaceAndPath("pack", "a.png")));
        edit(ElementPropertiesState.Field.TEXTURE, "");
        assertNull(state.commit(LIBRE).texture(), "le champ vidé retire la texture");
    }

    @Test
    void uneCouleurSecritEnHexadecimal() {
        assertEquals("#C0141519", state.valueOf(ElementPropertiesState.Field.BACKGROUND));
        edit(ElementPropertiesState.Field.BACKGROUND, "#FF203040");
        assertEquals(0xFF203040, state.commit(LIBRE).style().background());

        edit(ElementPropertiesState.Field.BORDER, "zzz");
        assertFalse(state.valid(LIBRE));
    }

    @Test
    void uneMargeNegativeEstRamenneeAZero() {
        edit(ElementPropertiesState.Field.PADDING, "-5");
        assertEquals(0, state.commit(LIBRE).style().padding(),
                "le modèle refuse une marge négative : on la borne plutôt que de lever");
    }

    @Test
    void lAncreTourneDansLesDeuxSens() {
        assertEquals(Anchor.TOP_CENTER, state.cycleAnchor(1).anchor());
        assertEquals(Anchor.BOTTOM_RIGHT, state.cycleAnchor(-1).anchor(), "et boucle");
    }

    /**
     * Revalider le graphe est débouncé et retombe pendant la frappe. Effacer le tampon
     * à ce moment-là ferait perdre un caractère sur deux à l'auteur.
     */
    @Test
    void reselectionnerLeMemeElementNInterrompPasLaFrappe() {
        edit(ElementPropertiesState.Field.X, "99");
        state.select(ScreenElement.of("bouton", ElementKind.BUTTON, 12, 34, 60, 20));

        assertEquals(ElementPropertiesState.Field.X, state.editing());
        assertEquals("99", state.buffer());
    }

    @Test
    void changerDElementFermeLeChampOuvert() {
        edit(ElementPropertiesState.Field.X, "99");
        state.select(ScreenElement.of("autre", ElementKind.LABEL, 0, 0, 10, 10));

        assertNull(state.editing());
        assertEquals("", state.buffer());
    }

    // ------------------------------------------------- manipulation directe (10.10)

    /**
     * L'ancre se posait en faisant défiler neuf valeurs à l'aveugle : jusqu'à huit clics
     * pour atteindre celle qu'on voulait, sans jamais voir laquelle venait ensuite. La
     * grille 3×3 la donne d'un clic, et montre laquelle est active.
     */
    @Test
    void lAncreSePoseParSaCaseDansLaGrille() {
        assertArrayEquals(new int[]{0, 0}, state.anchorCell(), "TOP_LEFT en haut à gauche");

        ScreenElement centre = state.setAnchor(1, 1);
        assertNotNull(centre);
        assertEquals(Anchor.CENTER, centre.anchor());

        state.select(centre);
        assertArrayEquals(new int[]{1, 1}, state.anchorCell());
        assertEquals(Anchor.BOTTOM_RIGHT, state.setAnchor(2, 2).anchor());
        assertNull(state.setAnchor(3, 0), "hors de la grille : rien");
    }

    /**
     * Changer de mode garde les bornes mais <b>remplace la valeur</b> quand elle n'a plus
     * de sens : reprendre 60 comme fraction donnerait 6000 % du parent — un élément que
     * l'auteur ne retrouverait plus.
     */
    @Test
    void changerDeModeDeTailleNeGardeQueCeQuiAUnSens() {
        state.select(ScreenElement.of("b", ElementKind.BUTTON, 0, 0, 60, 20)
                .resized(Extent.percent(0.5, 20, 120), Extent.of(20)));

        ScreenElement fixe = state.setSizeMode(true, Extent.Mode.FIXED);
        assertEquals(Extent.Mode.FIXED, fixe.width().mode());
        assertEquals(20, fixe.width().min(), 1e-9, "les bornes survivent");
        assertEquals(120, fixe.width().max(), 1e-9);

        ScreenElement remplir = state.setSizeMode(true, Extent.Mode.FILL);
        assertEquals(Extent.Mode.FILL, remplir.width().mode());
        assertEquals(1, remplir.width().value(), 1e-9, "poids par défaut, pas 0,5");

        state.select(remplir);
        assertTrue(state.sizeValueMatters(true), "un poids se règle");
        state.select(state.setSizeMode(true, Extent.Mode.HUG));
        assertFalse(state.sizeValueMatters(true), "« ajuster » ne consomme aucune valeur");
    }

    /** La taille s'écrit aussi au clavier, dans la même syntaxe qu'en BScript. */
    @Test
    void laTailleSeTapeCommeEnBScript() {
        state.select(ScreenElement.of("b", ElementKind.BUTTON, 0, 0, 60, 20)
                .resized(new Extent(Extent.Mode.FIXED, 60, 10, 200), Extent.of(20)));

        edit(ElementPropertiesState.Field.WIDTH, "fill:2");
        ScreenElement out = state.commit(LIBRE);
        assertEquals(Extent.Mode.FILL, out.width().mode());
        assertEquals(2, out.width().value(), 1e-9);
        assertEquals(10, out.width().min(), 1e-9, "les bornes ne se perdent pas à la frappe");

        state.select(out);
        edit(ElementPropertiesState.Field.WIDTH, "hug");
        assertEquals(Extent.Mode.HUG, state.commit(LIBRE).width().mode());

        state.select(out);
        edit(ElementPropertiesState.Field.WIDTH, "n'importe quoi");
        assertFalse(state.valid(LIBRE));
        assertNull(state.commit(LIBRE), "rien d'invalide n'entre dans le modèle");
    }

    /** Les réglages de disposition passent par les mêmes champs que le reste. */
    @Test
    void laDispositionSeRegleDepuisLePanneau() {
        state.select(ScreenElement.of("cadre", ElementKind.PANEL, 0, 0, 100, 100));

        ScreenElement colonne = state.setLayoutMode(
                fr.blueprint.core.graph.screen.LayoutSpec.Mode.COLUMN);
        assertTrue(colonne.arranges());

        state.select(colonne);
        edit(ElementPropertiesState.Field.GAP, "6");
        ScreenElement espace = state.commit(LIBRE);
        assertEquals(6, espace.layout().gap(), 1e-9);

        state.select(espace);
        assertEquals(fr.blueprint.core.graph.screen.LayoutSpec.Cross.STRETCH,
                state.setLayoutCross(fr.blueprint.core.graph.screen.LayoutSpec.Cross.STRETCH)
                        .layout().cross());
        assertEquals("6", state.valueOf(ElementPropertiesState.Field.GAP));
    }

    /** Suivre un style nommé n'efface pas le style en ligne : détacher le rend intact. */
    @Test
    void suivreUnStyleNommePuisSEnDetacher() {
        ScreenElement suiveur = state.useStyle("principal");
        assertTrue(suiveur.followsNamedStyle());
        assertEquals("principal", suiveur.styleName());

        state.select(suiveur);
        ScreenElement detache = state.useStyle("");
        assertFalse(detache.followsNamedStyle());
        assertEquals(suiveur.style(), detache.style(), "le style en ligne n'a pas bougé");
    }

    @Test
    void sansSelectionLePanneauNeFaitRien() {
        state.select(null);
        state.beginEdit(ElementPropertiesState.Field.X);
        state.type('5');

        assertNull(state.editing());
        assertNull(state.commit(LIBRE));
        assertNull(state.cycleAnchor(1));
        assertEquals("", state.valueOf(ElementPropertiesState.Field.NAME));
    }
}
