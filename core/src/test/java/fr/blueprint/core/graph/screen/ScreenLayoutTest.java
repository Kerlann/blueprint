package fr.blueprint.core.graph.screen;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La résolution géométrique (story 10.1). Elle est partagée par le concepteur, le rendu
 * et le contrôle de placement : une erreur ici se voit trois fois, ou pire, deux fois
 * sur trois seulement.
 */
class ScreenLayoutTest {

    private static Screen screen(ScreenElement... elements) {
        return new Screen("menu", false, List.of(elements));
    }

    private static void assertRect(ScreenLayout.Rect rect,
                                   double x, double y, double w, double h) {
        assertEquals(x, rect.x(), 1e-9, "x");
        assertEquals(y, rect.y(), 1e-9, "y");
        assertEquals(w, rect.width(), 1e-9, "largeur");
        assertEquals(h, rect.height(), 1e-9, "hauteur");
    }

    @Test
    void unElementAncreEnHautGaucheEstALaPositionEcrite() {
        ScreenElement e = ScreenElement.of("a", ElementKind.LABEL, 10, 20, 40, 12);
        assertRect(ScreenLayout.resolve(screen(e), e, 320, 180), 10, 20, 40, 12);
    }

    /**
     * <b>Ce que « centré » veut dire.</b> Un élément centré doit reculer d'une
     * demi-largeur : sans ce recul, son coin gauche se pose au centre et le menu part
     * vers la droite. L'erreur reste invisible tant qu'on ne teste qu'en haut-gauche.
     */
    @Test
    void unElementCentreEstVraimentCentre() {
        ScreenElement e = new ScreenElement("a", ElementKind.PANEL, null, Anchor.CENTER,
                0, 0, Extent.of(100), Extent.of(50), ScreenText.EMPTY, null,
                ElementStyle.DEFAULT, "", LayoutSpec.ABSOLUTE, ElementBinding.NONE, true, true);
        assertRect(ScreenLayout.resolve(screen(e), e, 320, 180), 110, 65, 100, 50);
    }

    @Test
    void uneAncreDeCoinColleAuCoin() {
        ScreenElement e = new ScreenElement("a", ElementKind.LABEL, null, Anchor.BOTTOM_RIGHT,
                -4, -4, Extent.of(60), Extent.of(20), ScreenText.EMPTY, null,
                ElementStyle.DEFAULT, "", LayoutSpec.ABSOLUTE, ElementBinding.NONE, true, true);
        // Bord droit à 320 - 4, bord bas à 180 - 4 : l'écart demandé, pas la position.
        assertRect(ScreenLayout.resolve(screen(e), e, 320, 180), 256, 156, 60, 20);
    }

    /** Un pourcentage se compte dans le parent, pas dans la fenêtre. */
    @Test
    void unPourcentageSuitLeParentEtNonLEcran() {
        ScreenElement panel = ScreenElement.of("cadre", ElementKind.PANEL, 0, 0, 40, 40);
        ScreenElement child = new ScreenElement("x", ElementKind.LABEL, "cadre",
                Anchor.TOP_LEFT, 0, 0, Extent.percent(0.5, 0, 0), Extent.percent(0.5, 0, 0),
                ScreenText.EMPTY, null, ElementStyle.DEFAULT, "", LayoutSpec.ABSOLUTE, ElementBinding.NONE, true, true);
        assertRect(ScreenLayout.resolve(screen(panel, child), child, 320, 180),
                0, 0, 20, 20);
    }

    /** La position d'un enfant est relative à son parent, et s'additionne en cascade. */
    @Test
    void lesPositionsSEmpilentDeParentEnEnfant() {
        ScreenElement panel = ScreenElement.of("cadre", ElementKind.PANEL, 30, 40, 100, 60);
        ScreenElement child = ScreenElement.of("x", ElementKind.LABEL, 5, 6, 20, 10)
                .withParent("cadre");
        assertRect(ScreenLayout.resolve(screen(panel, child), child, 320, 180),
                35, 46, 20, 10);
    }

    /**
     * L'élément n'a pas à figurer dans l'écran : c'est ce qui permet de contrôler un
     * placement <i>avant</i> de le poser (AddElement).
     */
    @Test
    void unElementPasEncorePoseSeResoutQuandMeme() {
        ScreenElement panel = ScreenElement.of("cadre", ElementKind.PANEL, 10, 10, 100, 60);
        ScreenElement future = ScreenElement.of("neuf", ElementKind.LABEL, 2, 3, 20, 10)
                .withParent("cadre");
        assertRect(ScreenLayout.resolve(screen(panel), future, 320, 180), 12, 13, 20, 10);
    }

    /**
     * Un parent introuvable ne fait pas tomber le rendu : l'élément retombe à la
     * racine. Le validateur signale la référence morte, le dessin continue.
     */
    @Test
    void unParentIntrouvableRetombeSurLEcran() {
        ScreenElement orphan = ScreenElement.of("x", ElementKind.LABEL, 7, 8, 20, 10)
                .withParent("disparu");
        assertRect(ScreenLayout.resolve(screen(orphan), orphan, 320, 180), 7, 8, 20, 10);
    }

    /**
     * Un cycle de parenté est refusé à l'édition, mais une sauvegarde réparée peut en
     * contenir. Le rendu doit s'en sortir en temps fini — une boucle infinie au dessin
     * fige le client, là où un rectangle approximatif ne coûte rien.
     */
    @Test
    void unCycleDeParenteNeBouclePas() {
        ScreenElement a = ScreenElement.of("a", ElementKind.PANEL, 1, 1, 40, 40)
                .withParent("b");
        ScreenElement b = ScreenElement.of("b", ElementKind.PANEL, 2, 2, 40, 40)
                .withParent("a");
        ScreenLayout.Rect rect = ScreenLayout.resolve(screen(a, b), a, 320, 180);
        assertTrue(Double.isFinite(rect.x()) && Double.isFinite(rect.width()));
    }

    /**
     * Les packs requis se déduisent des textures. Une liste déclarée à la main
     * dériverait : l'éditeur avertirait pour un pack devenu inutile et se tairait sur
     * celui qui manque.
     */
    @Test
    void lesPacksRequisSeDeduisentDesTextures() {
        Screen screen = screen(
                ScreenElement.of("fond", ElementKind.IMAGE, 0, 0, 100, 100)
                        .withTexture(net.minecraft.resources.Identifier
                                .fromNamespaceAndPath("boutique", "textures/gui/fond.png")),
                ScreenElement.of("icone", ElementKind.IMAGE, 0, 0, 16, 16)
                        .withTexture(net.minecraft.resources.Identifier
                                .fromNamespaceAndPath("boutique", "textures/gui/piece.png")),
                ScreenElement.of("vanille", ElementKind.IMAGE, 0, 0, 16, 16)
                        .withTexture(net.minecraft.resources.Identifier
                                .withDefaultNamespace("textures/gui/widgets.png")),
                ScreenElement.of("texte", ElementKind.LABEL, 0, 0, 40, 10));

        assertEquals(java.util.Set.of("boutique"), screen.requiredPacks(),
                "dédoublonné, et minecraft exclu : il est toujours là");
        assertTrue(screen().requiredPacks().isEmpty(), "un écran sans image n'exige rien");
    }

    @Test
    void leRectangleSaitCeQuIlContient() {
        ScreenLayout.Rect rect = new ScreenLayout.Rect(10, 20, 30, 40);
        assertTrue(rect.contains(10, 20), "le coin haut-gauche est dedans");
        assertFalse(rect.contains(40, 20), "le bord droit est dehors");
        assertFalse(rect.contains(10, 60), "le bord bas est dehors");
        assertTrue(rect.contains(39.9, 59.9));
        assertFalse(rect.contains(9.9, 20));
    }
}
