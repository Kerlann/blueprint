package fr.blueprint.client.browser;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le navigateur de blueprints (F6). Il n'y avait rien : F6 ouvrait une démo, et
 * ouvrir un vrai graphe demandait de taper son identifiant complet de mémoire.
 */
class BrowserStateTest {

    private static Identifier id(String text) {
        return Identifier.tryParse(text);
    }

    private BrowserState state;

    @BeforeEach
    void setUp() {
        state = new BrowserState();
        state.setBlueprints(List.of(
                id("demo:boutique"), id("demo:banque"), id("blueprint:exemple/porte")));
        state.setFiles(List.of("demo_boutique", "sauvegarde"));
    }

    /** Une ligne par son libellé — l'ordre est alphabétique, pas celui d'insertion. */
    private BrowserState.Row row(String label) {
        return state.rows().stream().filter(r -> r.label().equals(label)).findFirst()
                .orElseThrow(() -> new AssertionError("ligne « " + label + " » absente"));
    }

    private List<String> labels() {
        return state.rows().stream().map(BrowserState.Row::label).toList();
    }

    @Test
    void lesBlueprintsSontGroupesParEspaceDeNoms() {
        assertEquals(BrowserState.Kind.FOLDER, row("demo").kind());
        assertEquals(0, row("demo").depth());
        assertEquals(BrowserState.Kind.BLUEPRINT, row("boutique").kind());
        assertEquals(1, row("boutique").depth(), "indenté sous son dossier");
    }

    /** Le « / » d'un identifiant est un SOUS-dossier, pas un nom à rallonge. */
    @Test
    void unCheminAvecUnSlashDonneUnSousDossier() {
        assertEquals(BrowserState.Kind.FOLDER, row("exemple").kind());
        assertEquals(1, row("exemple").depth());
        assertEquals(BrowserState.Kind.BLUEPRINT, row("porte").kind());
        assertEquals(2, row("porte").depth(), "deux niveaux sous la racine");
    }

    @Test
    void lesFichiersImportablesFormentLeurPropreDossier() {
        assertTrue(labels().contains(BrowserState.FILES_FOLDER));
        assertTrue(labels().contains("demo_boutique"));

        state.toggleFiles();
        assertFalse(labels().contains(BrowserState.FILES_FOLDER),
                "on peut les cacher : ce n'est pas ce qu'on ouvre le plus souvent");
    }

    @Test
    void replierUnDossierCacheSonContenu() {
        BrowserState.Row folder = row("demo");
        assertFalse(state.click(folder), "un dossier ne s'ouvre pas, il se replie");

        assertTrue(state.isCollapsed("demo"));
        assertFalse(labels().contains("boutique"));
        assertTrue(labels().contains("demo"), "le dossier reste visible");

        state.click(folder);
        assertTrue(labels().contains("boutique"));
    }

    /**
     * <b>Le test qui compte.</b> Chercher puis ne rien voir parce que le dossier était
     * replié serait exactement le contraire du service rendu.
     */
    @Test
    void chercherDeplieTout() {
        state.click(row("demo"));
        assertFalse(labels().contains("boutique"));

        state.setFilter("bout");
        assertTrue(labels().contains("boutique"), "le filtre passe outre le repli");
    }

    @Test
    void leFiltrePorteSurLIdentifiantEntier() {
        state.setFilter("demo:");
        assertTrue(labels().contains("boutique"));
        assertTrue(labels().contains("banque"));
        assertFalse(labels().contains("porte"), "d'un autre espace de noms");

        state.setFilter("porte");
        assertTrue(labels().contains("porte"));
        assertFalse(labels().contains("boutique"));
    }

    @Test
    void unFiltreSansResultatNAfficheAucunDossierVide() {
        state.setFilter("zzz");
        assertTrue(state.rows().isEmpty(), "pas de dossier sans contenu");
    }

    @Test
    void selectionnerUnBlueprintLeRendOuvrable() {
        BrowserState.Row row = state.rows().stream()
                .filter(r -> r.kind() == BrowserState.Kind.BLUEPRINT).findFirst().orElseThrow();

        assertTrue(state.click(row), "ouvrable");
        assertEquals(row.path(), state.selected());
        assertNotNull(state.selectedRow());
        assertNotNull(state.selectedRow().blueprint());
    }

    @Test
    void selectionnerUnFichierGardeSonNom() {
        BrowserState.Row row = state.rows().stream()
                .filter(r -> r.kind() == BrowserState.Kind.FILE).findFirst().orElseThrow();

        assertTrue(state.click(row));
        assertEquals("demo_boutique", state.selectedRow().file());
    }

    /** Replier le dossier de ce qui était sélectionné ne laisse pas une sélection fantôme. */
    @Test
    void uneSelectionCacheeNEstPlusRendue() {
        BrowserState.Row blueprint = state.rows().stream()
                .filter(r -> r.kind() == BrowserState.Kind.BLUEPRINT).findFirst().orElseThrow();
        state.click(blueprint);

        state.click(row("demo"));   // replie « demo »
        assertNull(state.selectedRow(), "elle n'est plus affichée");
    }

    /**
     * Exiger l'espace de noms pour créer son premier graphe serait une leçon avant le
     * premier geste.
     */
    @Test
    void unNomSimpleSuffitPourCreer() {
        assertEquals(id("blueprint:menu"), BrowserState.parseId("menu"));
        assertEquals(id("demo:menu"), BrowserState.parseId("demo:menu"));
        assertEquals(id("blueprint:menu"), BrowserState.parseId("  menu  "));
        assertNull(BrowserState.parseId(""));
        assertNull(BrowserState.parseId("PAS UN ID"));
        assertNull(BrowserState.parseId(null));
    }

    @Test
    void unNavigateurVideNAfficheRien() {
        BrowserState empty = new BrowserState();
        assertTrue(empty.rows().isEmpty());
        assertNull(empty.selectedRow());
    }
}
