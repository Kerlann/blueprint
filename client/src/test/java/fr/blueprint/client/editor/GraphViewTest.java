package fr.blueprint.client.editor;

import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.PinKind;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.BlueprintFunction;
import fr.blueprint.core.graph.CommentBox;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.FunctionOps;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.NodeShape;
import fr.blueprint.core.graph.NodeTypeLookup;
import fr.blueprint.core.graph.Vec2d;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le canevas édite <b>le graphe qu'on regarde</b> (story 20.2, AC2, AC3, AC11).
 *
 * <p>Un corps de fonction est un graphe. Plutôt que d'en écrire un second canevas, celui
 * qui existe reçoit une {@link GraphView} : elle décide quels nœuds il lit, et redirige vers
 * le corps ouvert les opérations qu'il fabrique sans rien savoir des fonctions.
 *
 * <p>Ce qui se vérifie ici est ce qui se voit le plus mal en jouant — un geste qui tombe
 * dans le graphe qu'on ne regarde pas.
 */
class GraphViewTest {

    private static final Identifier TYPE = Identifier.fromNamespaceAndPath("test", "node");

    private static final NodeShape SHAPE = new NodeShape(
            List.of(new NodeShape.PinDef("exec_in", PinKind.EXEC, PinTypes.EXEC, false),
                    new NodeShape.PinDef("a", PinKind.DATA, PinTypes.DOUBLE, false)),
            List.of(new NodeShape.PinDef("exec_out", PinKind.EXEC, PinTypes.EXEC, false),
                    new NodeShape.PinDef("r", PinKind.DATA, PinTypes.DOUBLE, false)),
            false, Permission.SAFE);

    private static final NodeTypeLookup LOOKUP = typeId -> SHAPE;

    private Blueprint bp;
    private CanvasController controller;
    private UUID dansLeGraphe;

    @BeforeEach
    void setUp() {
        bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "vue"));
        assertTrue(new FunctionOps.AddFunction(
                BlueprintFunction.of("carre", List.of(), List.of()))
                .apply(bp, LOOKUP).applied());
        dansLeGraphe = UUID.randomUUID();
        assertTrue(new EditOperation.AddNode(dansLeGraphe, TYPE, new Vec2d(0, 0))
                .apply(bp, LOOKUP).applied());
        controller = new CanvasController(bp, LOOKUP, new Camera());
    }

    /**
     * <b>Un nœud posé dans un corps arrive dans le corps.</b>
     *
     * <p>Le canevas fabrique un {@code AddNode} qui ne sait rien des fonctions ; sans la
     * redirection il tomberait dans le graphe principal, et l'auteur verrait le corps rester
     * vide pendant que le graphe se remplit à son insu.
     */
    @Test
    void unNoeudPoseDansUnCorpsArriveDansLeCorps() {
        assertTrue(controller.view().open("carre"));
        int avant = bp.nodes().size();

        UUID pose = controller.insertNode(TYPE, 40, 40, null);

        assertNotNull(pose);
        assertEquals(avant, bp.nodes().size(),
                "le graphe principal ne doit pas avoir grossi");
        assertNotNull(bp.function("carre").nodes().get(pose),
                "le nœud devait atterrir dans le corps ouvert");
    }

    /**
     * <b>Une seule pile d'annulation, dans l'ordre des gestes</b> (AC3).
     *
     * <p>Deux piles seraient un piège : {@code Ctrl+Z} annulerait ce qu'on ne regarde pas.
     * Le geste fait dans le corps s'annule ici alors qu'un geste du graphe l'a précédé —
     * c'est le cas qui distingue une pile unique de deux piles qui se ressemblent tant qu'on
     * ne mélange pas les deux graphes.
     */
    @Test
    void unSeulCtrlZPourLesDeuxGraphes() {
        assertTrue(controller.applyOp(new EditOperation.MoveNode(dansLeGraphe, new Vec2d(500, 500))));

        assertTrue(controller.view().open("carre"));
        UUID posé = controller.insertNode(TYPE, 40, 40, null);
        assertNotNull(posé);
        // Vérifié AVANT l'annulation : sans cette ligne, un nœud qui n'aurait jamais
        // atteint le corps rendrait l'assertion d'après vraie sans rien prouver.
        assertNotNull(bp.function("carre").nodes().get(posé));

        assertTrue(controller.undo(), "le dernier geste est celui du corps");
        assertNull(bp.function("carre").nodes().get(posé),
                "Ctrl+Z devait défaire le geste du CORPS, pas celui du graphe");
        assertEquals(new Vec2d(500, 500), bp.node(dansLeGraphe).position(),
                "le geste précédent, lui, tient toujours");

        assertTrue(controller.undo(), "le geste d'avant est celui du graphe");
        assertEquals(new Vec2d(0, 0), bp.node(dansLeGraphe).position());
    }

    /**
     * <b>Un rétablissement retrouve le corps</b> — même quand on n'y est plus.
     *
     * <p>L'inverse rendu par une opération redirigée porte déjà le nom du corps. Si la pile
     * rejouait l'opération nue, un {@code Ctrl+Y} fait depuis le graphe principal reposerait
     * dans le graphe le nœud qu'on avait posé dans le corps.
     */
    @Test
    void leRetablissementRetrouveLeCorpsMemeDepuisLeGraphe() {
        assertTrue(controller.view().open("carre"));
        UUID posé = controller.insertNode(TYPE, 40, 40, null);
        assertNotNull(posé);
        assertTrue(controller.undo());

        controller.view().open(null);
        assertTrue(controller.redo());

        assertNull(bp.nodes().get(posé),
                "le nœud ne doit pas réapparaître dans le graphe principal");
        assertNotNull(bp.function("carre").nodes().get(posé),
                "il appartenait au corps, il y revient");
    }

    /**
     * <b>Un corps effacé sous les pieds ne devient pas le graphe principal.</b>
     *
     * <p>Un {@code Ctrl+Z} peut annuler la création de la fonction qu'on est en train
     * d'éditer. Retomber alors sur le graphe principal serait le pire des cas : le canevas
     * montrerait d'autres nœuds sans rien dire, et le geste suivant tomberait dedans.
     */
    @Test
    void unCorpsEffaceNeRetombePasSurLeGraphePrincipal() {
        assertTrue(controller.view().open("carre"));
        assertTrue(new FunctionOps.RemoveFunction("carre").apply(bp, LOOKUP).applied());

        assertTrue(controller.view().nodes().isEmpty(),
                "la vue d'un corps disparu est VIDE, pas celle du graphe");
        assertTrue(controller.boxes().isEmpty());
    }

    /**
     * <b>Changer de corps invalide la géométrie.</b>
     *
     * <p>Les caches du canevas — boîtes, index de survol, pins câblés — sont invalidés par
     * la révision. Passer d'un graphe à l'autre ne modifie rien, donc ne la fait pas bouger :
     * sans le nom dans la clé, on cliquerait sur les boîtes du graphe précédent.
     */
    @Test
    void changerDeCorpsInvalideLaGeometrie() {
        int auGraphe = controller.view().revision();
        assertEquals(1, controller.boxes().size());

        assertTrue(controller.view().open("carre"));

        assertNotEquals(auGraphe, controller.view().revision(),
                "la révision doit distinguer les deux graphes");
        assertEquals(0, controller.boxes().size(),
                "le cache de boîtes a resservi celles du graphe principal");
    }

    /**
     * <b>Un commentaire est refusé dans un corps, et le dit.</b>
     *
     * <p>Un corps ne stocke que des nœuds et des liens. Laisser passer le geste poserait le
     * commentaire dans le graphe principal, où l'auteur ne le chercherait jamais ; un clic
     * sans effet ni explication serait à peine mieux.
     */
    @Test
    void unCommentaireEstRefuseDansUnCorpsEtLeDit() {
        assertTrue(controller.view().open("carre"));

        assertFalse(controller.applyOp(new EditOperation.AddComment(new CommentBox(
                UUID.randomUUID(), "note", new Vec2d(0, 0), new Vec2d(100, 60), 0xFF808080))));
        assertTrue(bp.comments().isEmpty(),
                "le commentaire ne doit pas s'être posé dans le graphe principal");
        assertNotNull(controller.takeRefusal(),
                "un geste refusé sans diagnostic est un clic sans effet");
    }

    /**
     * <b>Le câblage se valide dans le graphe qu'on regarde.</b>
     *
     * <p>Le validateur a deux portes, une par graphe. Poser la question au graphe principal
     * pendant qu'on câble un corps répondrait sur des nœuds qui ne sont pas ceux qu'on
     * regarde : « nœud introuvable » pour un câblage parfaitement valide.
     */
    @Test
    void leCablageSeValideDansLeGrapheQuOnRegarde() {
        assertTrue(controller.view().open("carre"));
        UUID a = controller.insertNode(TYPE, 0, 0, null);
        UUID b = controller.insertNode(TYPE, 200, 0, null);
        assertNotNull(a);
        assertNotNull(b);

        assertNull(controller.view().canLink(LOOKUP, new Link(a, "r", b, "a")),
                "deux nœuds du même corps doivent pouvoir se câbler");
        assertTrue(controller.applyOp(new EditOperation.AddLink(new Link(a, "r", b, "a"))));
        assertEquals(1, bp.function("carre").links().size());
        assertTrue(bp.links().isEmpty(), "le lien n'a rien à faire dans le graphe principal");
    }

    /**
     * <b>Tous les gestes du canevas visent le corps, pas seulement le premier.</b>
     *
     * <p>Chaque branche oubliée de la redirection est un geste qui tombe dans le graphe
     * qu'on ne regarde pas — et qui n'y fait rien de visible, puisque les identifiants n'y
     * existent pas. L'auteur voit alors un canevas qui ignore un geste sur six, sans motif.
     *
     * <p>Le tour complet plutôt qu'un échantillon : c'est le genre de liste où l'on ajoute
     * une opération sans penser à la rediriger.
     */
    @Test
    void tousLesGestesDuCanevasVisentLeCorps() {
        assertTrue(controller.view().open("carre"));
        UUID a = controller.insertNode(TYPE, 0, 0, null);
        UUID b = controller.insertNode(TYPE, 200, 0, null);
        assertNotNull(a);
        assertNotNull(b);
        Link lien = new Link(a, "r", b, "a");

        assertTrue(controller.applyOp(new EditOperation.AddLink(lien)));
        assertEquals(1, bp.function("carre").links().size());
        assertTrue(controller.applyOp(new EditOperation.RemoveLink(lien)));
        assertTrue(bp.function("carre").links().isEmpty());

        assertTrue(controller.applyOp(new EditOperation.MoveNode(a, new Vec2d(9, 9))));
        assertEquals(new Vec2d(9, 9), bp.function("carre").nodes().get(a).position());

        assertTrue(controller.applyOp(new EditOperation.SetLiteral(a, "a",
                fr.blueprint.api.pin.LiteralValue.of(PinTypes.DOUBLE, 3.0))));
        assertNotNull(bp.function("carre").nodes().get(a).literals().get("a"));

        assertTrue(controller.applyOp(new EditOperation.RemoveNode(b)));
        assertNull(bp.function("carre").nodes().get(b));
        assertTrue(controller.undo(), "l'inverse d'un retrait est une restauration");
        assertNotNull(bp.function("carre").nodes().get(b),
                "restaurer dans le graphe principal aurait rendu le nœud invisible ici");

        // Les variables appartiennent au blueprint, pas au graphe : elles traversent la
        // redirection sans être touchées, et restent les mêmes vues d'un corps.
        assertTrue(controller.applyOp(new EditOperation.AddVariable(
                new fr.blueprint.core.graph.Variable("v", PinTypes.DOUBLE, null,
                        fr.blueprint.core.graph.VarScope.GRAPH, false))));
        assertTrue(bp.variables().containsKey("v"));
    }

    /**
     * <b>On sait retrouver le graphe d'un nœud à partir du seul identifiant</b> (AC9).
     *
     * <p>Un diagnostic ne nomme que le nœud fautif, jamais le corps où il vit. Sans cette
     * question, un clic sur une erreur d'un corps cherchait la boîte dans le graphe
     * principal, ne la trouvait pas, et ne faisait rien — une erreur qu'on ne peut pas
     * atteindre vaut à peine mieux qu'un silence.
     */
    @Test
    void onRetrouveLeGrapheDUnNoeudParSonIdentifiant() {
        assertTrue(controller.view().open("carre"));
        UUID dansLeCorps = controller.insertNode(TYPE, 0, 0, null);
        assertNotNull(dansLeCorps);
        controller.view().open(null);

        assertEquals("carre", controller.view().owner(dansLeCorps),
                "le nœud vit dans le corps, et on doit pouvoir le dire depuis le graphe");
        assertNull(controller.view().owner(dansLeGraphe),
                "un nœud du graphe principal n'a pas de corps propriétaire");
        assertTrue(controller.view().exists(dansLeCorps));
        assertFalse(controller.view().exists(UUID.randomUUID()),
                "un identifiant inconnu n'appartient à aucun graphe — et ne doit pas se "
                        + "confondre avec « il est dans le graphe principal »");
    }

    /** Le panneau s'arrête où il se dessine — sous lui, le clic va au canevas. */
    @Test
    void souscLePanneauLeClicVaAuCanevas() {
        FunctionPanelState panneau = new FunctionPanelState(bp, controller::applyOp);
        int bas = FunctionPanelLayout.bottom(panneau, 300);

        assertTrue(FunctionPanelLayout.contains(10, bas - 1, panneau, 300));
        assertFalse(FunctionPanelLayout.contains(10, bas, panneau, 300),
                "une bande invisible sous le panneau avalerait les clics du canevas");
        assertFalse(FunctionPanelLayout.contains(FunctionPanelLayout.WIDTH + 1, bas - 1,
                panneau, 300));
        assertFalse(FunctionPanelLayout.contains(10, 0, panneau, 300),
                "la barre d'outils est au-dessus du panneau");
    }

    /**
     * <b>Les liens se lisent dans le bon graphe, dans les deux sens.</b>
     *
     * <p>Le canevas interroge les liens d'un pin trois fois par rangée et par image : au
     * survol, au câblage, au dessin. Une seule de ces lectures restée sur le graphe
     * principal ferait clignoter un pin comme libre alors qu'il est câblé — ou l'inverse.
     */
    @Test
    void lesLiensSeLisentDansLeBonGraphe() {
        assertTrue(controller.view().open("carre"));
        UUID a = controller.insertNode(TYPE, 0, 0, null);
        UUID b = controller.insertNode(TYPE, 200, 0, null);
        assertNotNull(a);
        assertNotNull(b);
        assertTrue(controller.applyOp(new EditOperation.AddLink(new Link(a, "r", b, "a"))));

        assertEquals(1, controller.view().linksFrom(a, "r").size());
        assertEquals(1, controller.view().linksInto(b, "a").size());
        assertEquals(1, controller.view().linksTouching(a).size());
        assertTrue(controller.isWired(b, "a"), "le pin d'entrée du corps est câblé");

        controller.view().open(null);
        assertTrue(controller.view().linksFrom(a, "r").isEmpty(),
                "vu du graphe principal, ce lien n'existe pas");
        assertFalse(controller.isWired(b, "a"));
    }

    /**
     * <b>Un corps ne montre aucun commentaire.</b>
     *
     * <p>{@code BlueprintFunction} n'en stocke pas. Laisser passer ceux du graphe principal
     * les dessinerait par-dessus le corps, à des coordonnées qui n'ont plus de sens, et un
     * clic dessus tenterait de déplacer une boîte qui appartient à un autre graphe.
     */
    @Test
    void unCorpsNeMontreAucunCommentaire() {
        UUID note = UUID.randomUUID();
        assertTrue(controller.applyOp(new EditOperation.AddComment(new CommentBox(
                note, "note", new Vec2d(0, 0), new Vec2d(100, 60), 0xFF808080))));
        assertEquals(1, controller.view().comments().size());

        assertTrue(controller.view().open("carre"));

        assertTrue(controller.view().comments().isEmpty());
        assertNull(controller.view().comment(note),
                "un commentaire atteignable par identifiant depuis un corps se laisserait "
                        + "déplacer et redimensionner sans jamais se dessiner");
    }

    /**
     * Ouvrir une fonction qui n'existe pas ne bouge pas la vue.
     *
     * <p>Le panneau et la navigation par diagnostic nomment tous deux la fonction à ouvrir.
     * Accepter un nom mort viderait le canevas sans rien dire.
     */
    @Test
    void ouvrirUneFonctionInconnueNeBougePasLaVue() {
        assertFalse(controller.view().open("inexistante"));
        assertFalse(controller.view().inBody());
        assertEquals(1, controller.view().nodes().size(), "on est resté sur le graphe");
    }

    /**
     * <b>Enregistrer pendant l'édition d'un corps enregistre tout</b> (AC11).
     *
     * <p>La vue ne détient aucun état du graphe : elle en détient le nom. Le corps est donc
     * déjà dans la session à chaque instant, et il n'y a rien à reposer avant un
     * {@code Ctrl+S} — c'est précisément ce que la piste du blueprint jetable aurait perdu.
     */
    @Test
    void leCorpsOuvertEstDejaDansLaSession() {
        assertTrue(controller.view().open("carre"));
        UUID posé = controller.insertNode(TYPE, 40, 40, null);

        assertNotNull(posé);
        assertNotNull(bp.function("carre").nodes().get(posé),
                "rien à reposer : le geste a déjà touché la session");
        assertTrue(controller.view().inBody(), "et l'on reste où l'on était");
    }
}
