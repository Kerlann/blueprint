package fr.blueprint.client.editor;

import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.PinKind;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.EditOperation;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanvasControllerTest {

    private static final Identifier TYPE = Identifier.fromNamespaceAndPath("test", "node");

    private static final NodeShape SHAPE = new NodeShape(
            List.of(new NodeShape.PinDef("exec_in", PinKind.EXEC, PinTypes.EXEC, false),
                    new NodeShape.PinDef("a", PinKind.DATA, PinTypes.DOUBLE, false)),
            List.of(new NodeShape.PinDef("exec_out", PinKind.EXEC, PinTypes.EXEC, false)),
            false, Permission.SAFE);

    /** Get/Set portent le pin « var » : sans lui, SetLiteral serait refusé (5.5). */
    private static final NodeShape VAR_SHAPE = new NodeShape(
            List.of(new NodeShape.PinDef(fr.blueprint.core.graph.VarNodes.VAR_PIN,
                    PinKind.DATA, PinTypes.STRING, false)),
            List.of(new NodeShape.PinDef("value", PinKind.DATA, PinTypes.DOUBLE, false)),
            true, Permission.SAFE);

    private static final NodeTypeLookup LOOKUP = typeId ->
            fr.blueprint.core.graph.VarNodes.GET.equals(typeId)
                    || fr.blueprint.core.graph.VarNodes.SET.equals(typeId) ? VAR_SHAPE : SHAPE;

    private Blueprint bp;
    private Camera camera;
    private CanvasController controller;
    private UUID n1;
    private UUID n2;

    @BeforeEach
    void setUp() {
        bp = new Blueprint(Identifier.fromNamespaceAndPath("test", "graph"));
        n1 = addNode(0, 0);
        n2 = addNode(300, 100);
        camera = new Camera();
        controller = new CanvasController(bp, LOOKUP, camera);
    }

    private UUID addNode(double x, double y) {
        UUID id = UUID.randomUUID();
        assertTrue(new EditOperation.AddNode(id, TYPE, new Vec2d(x, y)).apply(bp, LOOKUP).applied());
        return id;
    }

    @Test
    void presseSurUnNoeudLeSelectionneEtDemarreLeDeplacement() {
        controller.press(10, 10, false);
        assertTrue(controller.selection().isSelected(n1));
        assertEquals(CanvasController.Gesture.MOVE, controller.gesture());

        controller.drag(60, 35);
        controller.release(false);
        // Saisi en (10,10), nœud en (0,0) : offset (−10,−10) → cible (50,25).
        assertEquals(new Vec2d(50, 25), bp.node(n1).position());
        assertEquals(CanvasController.Gesture.NONE, controller.gesture());
    }

    @Test
    void deplacementMultiSelectionConserveLesEcarts() {
        controller.selection().selectAll(List.of(n1, n2), false);

        controller.press(10, 10, false);
        controller.drag(26, 42);
        controller.release(false);
        assertEquals(new Vec2d(16, 32), bp.node(n1).position());
        assertEquals(new Vec2d(316, 132), bp.node(n2).position());
    }

    @Test
    void deplacementAvecAccroche() {
        camera.toggleSnap();
        controller.press(5, 5, false);
        controller.drag(30, 30);
        controller.release(false);
        // Cible brute (25,25) → accrochée (32,32) : jamais de dérive incrémentale.
        assertEquals(new Vec2d(32, 32), bp.node(n1).position());
    }

    @Test
    void rectangleElastiqueSelectionne() {
        controller.press(-50, -50, false);
        assertEquals(CanvasController.Gesture.RUBBER, controller.gesture());
        controller.drag(320, 120);
        assertNotNull(controller.rubberRect());
        controller.release(false);
        assertTrue(controller.selection().isSelected(n1));
        assertTrue(controller.selection().isSelected(n2));
        assertNull(controller.rubberRect());
    }

    @Test
    void rectangleAdditifConserveLExistant() {
        controller.press(10, 10, false);
        controller.release(false);
        assertTrue(controller.selection().isSelected(n1));

        controller.press(290, 90, true);
        controller.drag(320, 120);
        controller.release(true);
        assertEquals(2, controller.selection().size());
    }

    @Test
    void rectangleNonAdditifVideALaPresse() {
        controller.selection().selectAll(List.of(n1, n2), false);
        controller.press(-500, -500, false);
        assertTrue(controller.selection().isEmpty());
        controller.release(false);
        assertTrue(controller.selection().isEmpty());
    }

    @Test
    void suppressionRetireNoeudsEtLiens() {
        assertTrue(new EditOperation.AddLink(new Link(n1, "exec_out", n2, "exec_in"))
                .apply(bp, LOOKUP).applied());
        assertEquals(1, bp.links().size());

        controller.press(10, 10, false);
        controller.release(false);
        controller.deleteSelection();

        assertNull(bp.node(n1));
        assertNotNull(bp.node(n2));
        assertTrue(bp.links().isEmpty());
        assertTrue(controller.selection().isEmpty());
    }

    @Test
    void unGesteDeDeplacementSAnnuleEnUneFois() {
        controller.press(10, 10, false);
        controller.drag(60, 35);
        controller.drag(80, 55);
        controller.release(false);
        assertEquals(1, controller.history().undoDepth());
        assertEquals(new Vec2d(70, 45), bp.node(n1).position());

        assertTrue(controller.undo());
        assertEquals(new Vec2d(0, 0), bp.node(n1).position());
        assertTrue(controller.redo());
        assertEquals(new Vec2d(70, 45), bp.node(n1).position());
    }

    @Test
    void laSuppressionSAnnule() {
        controller.press(10, 10, false);
        controller.release(false);
        controller.deleteSelection();
        assertNull(bp.node(n1));

        assertTrue(controller.undo());
        assertNotNull(bp.node(n1));
    }

    @Test
    void hitTestAttrapeLeNoeudDessineAuDessus() {
        // n3 chevauche n1 et est inséré après : il se dessine au-dessus.
        UUID n3 = addNode(20, 10);
        NodeGeometry.Box hit = controller.hitTest(25, 15);
        assertNotNull(hit);
        assertEquals(n3, hit.node().uuid());
        assertFalse(controller.selection().isSelected(n3));
    }

    @Test
    void shiftDeclicSurUnNoeudSelectionne() {
        controller.selection().selectAll(List.of(n1, n2), false);
        controller.press(10, 10, true);
        assertFalse(controller.selection().isSelected(n1));
        assertTrue(controller.selection().isSelected(n2));
    }

    // ------------------------------------------------------------- câblage (5.3)

    private Vec2d out1() {
        return NodeGeometry.outputPinCenter(controller.boxOf(n1), 0);
    }

    private Vec2d in2(int row) {
        return NodeGeometry.inputPinCenter(controller.boxOf(n2), row);
    }

    @Test
    void cablageParGlisserDePinAPin() {
        Vec2d from = out1();
        controller.press(from.x(), from.y(), false);
        assertEquals(CanvasController.Gesture.WIRE, controller.gesture());
        assertNotNull(controller.wireFrom());

        Vec2d to = in2(0); // exec_in
        controller.drag(to.x(), to.y());
        assertNull(controller.release(false));
        assertEquals(1, bp.links().size());
        Link link = bp.links().iterator().next();
        assertEquals(n1, link.fromNode());
        assertEquals("exec_out", link.fromPin());
        assertEquals("exec_in", link.toPin());
    }

    @Test
    void lienInvalideRefuseParCanLink() {
        Vec2d from = out1(); // exec_out
        controller.press(from.x(), from.y(), false);
        Vec2d to = in2(1); // « a » : pin data double — kind incompatible
        controller.drag(to.x(), to.y());
        controller.release(false);
        assertTrue(bp.links().isEmpty());
    }

    @Test
    void relacheDansLeVideSignaleLaPalette() {
        Vec2d from = out1();
        controller.press(from.x(), from.y(), false);
        controller.drag(700, 400);
        CanvasController.WireDrop drop = controller.release(false);
        assertNotNull(drop);
        assertEquals("exec_out", drop.from().pin());
        assertEquals(700, drop.worldX());
        assertTrue(bp.links().isEmpty());
    }

    @Test
    void altClicDetacheLesLiensDuPin() {
        assertTrue(new EditOperation.AddLink(new Link(n1, "exec_out", n2, "exec_in"))
                .apply(bp, LOOKUP).applied());
        Vec2d pin = in2(0);
        controller.press(pin.x(), pin.y(), false, true);
        assertTrue(bp.links().isEmpty());
        assertEquals(CanvasController.Gesture.NONE, controller.gesture());
    }

    @Test
    void leDetachementEstSaPropreEntreeDAnnulation() {
        // Régression QA 5.3 : le geste ouvert à la presse Alt doit se refermer —
        // sans ça, le détachement fusionnait avec le geste SUIVANT dans le undo.
        assertTrue(new EditOperation.AddLink(new Link(n1, "exec_out", n2, "exec_in"))
                .apply(bp, LOOKUP).applied());
        Vec2d pin = in2(0);
        controller.press(pin.x(), pin.y(), false, true); // détache (pas de release)
        assertEquals(1, controller.history().undoDepth());

        controller.press(10, 10, false); // geste suivant : déplacer n1
        controller.drag(60, 35);
        controller.release(false);
        assertEquals(2, controller.history().undoDepth());

        // Annuler le déplacement ne restaure PAS le lien ; l'annulation suivante si.
        assertTrue(controller.undo());
        assertTrue(bp.links().isEmpty());
        assertTrue(controller.undo());
        assertEquals(1, bp.links().size());
    }

    @Test
    void compatibiliteViaCanLink() {
        CanvasController.PinRef from = controller.pinAt(out1().x(), out1().y());
        assertNotNull(from);
        assertTrue(controller.canConnect(from, n2, "exec_in", false));
        assertFalse(controller.canConnect(from, n2, "a", false));
        // Même direction : jamais.
        assertFalse(controller.canConnect(from, n2, "exec_out", true));
        // Pin inexistant : refusé par le validateur.
        assertFalse(controller.canConnect(from, n2, "inconnu", false));
    }

    // ----------------------------------------------------------- littéraux (5.2b)

    @Test
    void zoneLitteraleDUnPinDataNonCable() {
        // Rangée 1 de n1 : pin data « a ». La zone est à droite du label.
        NodeGeometry.Box box = controller.boxOf(n1);
        Camera.Rect zone = NodeGeometry.literalZone(box, 1);
        double cx = (zone.left() + zone.right()) / 2;
        double cy = (zone.top() + zone.bottom()) / 2;

        CanvasController.LiteralRef ref = controller.literalAt(cx, cy);
        assertNotNull(ref);
        assertEquals(n1, ref.node());
        assertEquals("a", ref.pin());
        assertEquals(1, ref.row());

        // Rangée 0 : exec — jamais de littéral.
        Camera.Rect execZone = NodeGeometry.literalZone(box, 0);
        assertNull(controller.literalAt((execZone.left() + execZone.right()) / 2,
                (execZone.top() + execZone.bottom()) / 2));
    }

    @Test
    void unPinCableMasqueSonLitteral() {
        // Le lien prime : dès que « a » est câblé depuis une sortie data, sa zone
        // littérale disparaît (AC1 5.2b).
        NodeShape withDataOut = new NodeShape(
                List.of(),
                List.of(new NodeShape.PinDef("out", PinKind.DATA, PinTypes.DOUBLE, false)),
                false, Permission.SAFE);
        Identifier srcType = Identifier.fromNamespaceAndPath("test", "src");
        NodeTypeLookup lookup2 = typeId -> srcType.equals(typeId) ? withDataOut : SHAPE;
        Blueprint bp2 = new Blueprint(Identifier.fromNamespaceAndPath("test", "wired"));
        UUID src = UUID.randomUUID();
        UUID dst = UUID.randomUUID();
        assertTrue(new EditOperation.AddNode(src, srcType, new Vec2d(0, 0)).apply(bp2, lookup2).applied());
        assertTrue(new EditOperation.AddNode(dst, TYPE, new Vec2d(300, 0)).apply(bp2, lookup2).applied());
        CanvasController c2 = new CanvasController(bp2, lookup2, new Camera());

        Camera.Rect zone = NodeGeometry.literalZone(c2.boxOf(dst), 1);
        double cx = (zone.left() + zone.right()) / 2;
        double cy = (zone.top() + zone.bottom()) / 2;
        assertNotNull(c2.literalAt(cx, cy));

        assertTrue(new EditOperation.AddLink(new Link(src, "out", dst, "a")).apply(bp2, lookup2).applied());
        assertNull(c2.literalAt(cx, cy));
    }

    @Test
    void setLiteralPasseParLOperationEtSAnnule() {
        int avant = controller.history().undoDepth();
        assertTrue(controller.setLiteral(n1, "a",
                fr.blueprint.api.pin.LiteralValue.of(PinTypes.DOUBLE, 2.5)));
        assertEquals(2.5, bp.node(n1).literal("a").value());
        assertEquals(avant + 1, controller.history().undoDepth());
        assertTrue(controller.undo());
        assertNull(bp.node(n1).literal("a"));
        // Type incompatible : refusé par SetLiteral, rien de collecté.
        assertFalse(controller.setLiteral(n1, "a",
                fr.blueprint.api.pin.LiteralValue.of(PinTypes.STRING, "nope")));
    }

    // -------------------------------------------------- commentaires, alignement (5.7)

    @Test
    void commentaireAutourDeLaSelectionDeplaceSonContenu() {
        controller.selection().selectAll(List.of(n1), false);
        UUID comment = controller.createCommentAroundSelection("Note");
        assertNotNull(comment);
        var box = bp.comment(comment);
        assertNotNull(box);
        assertEquals(comment, controller.selectedComment());

        // Saisir la barre de titre et glisser : la boîte ET n1 (centre dedans) suivent.
        double grabX = box.position().x() + 5;
        double grabY = box.position().y() + 5;
        Vec2d n1Before = bp.node(n1).position();
        controller.press(grabX, grabY, false);
        assertEquals(CanvasController.Gesture.MOVE_COMMENT, controller.gesture());
        controller.drag(grabX + 100, grabY + 50);
        controller.release(false);
        assertEquals(n1Before.x() + 100, bp.node(n1).position().x());
        assertEquals(box.position().x() + 100, bp.comment(comment).position().x());

        // Suppr retire la boîte sélectionnée, pas les nœuds.
        controller.deleteSelection();
        assertNull(bp.comment(comment));
        assertNotNull(bp.node(n1));
        // Renommage (la presse sur la boîte avait vidé la sélection : resélectionner).
        controller.selection().selectAll(List.of(n1), false);
        UUID second = controller.createCommentAroundSelection("A");
        assertNotNull(second);
        assertTrue(controller.renameComment(second, "B"));
        assertEquals("B", bp.comment(second).text());
    }

    @Test
    void alignementSelonLAxeDominant() {
        // n1 (0,0) et n2 (300,100) + n3 : sélection plus large que haute → rangée.
        UUID n3 = addNode(600, 40);
        controller.selection().selectAll(List.of(n1, n2, n3), false);
        assertTrue(controller.alignSelection());
        assertEquals(1, controller.history().undoDepth() > 0 ? 1 : 0);
        assertEquals(bp.node(n1).position().y(), bp.node(n2).position().y());
        assertEquals(bp.node(n1).position().y(), bp.node(n3).position().y());
        // L'ordre horizontal est préservé et espacé d'au moins la largeur + l'écart.
        assertTrue(bp.node(n2).position().x() >= bp.node(n1).position().x()
                + NodeGeometry.WIDTH + AlignActions.GAP - 1e-9);
    }

    @Test
    void insertionDepuisLaPaletteAvecAutoConnexion() {
        camera.toggleSnap();
        CanvasController.PinRef from = controller.pinAt(out1().x(), out1().y());
        assertNotNull(from);
        UUID inserted = controller.insertNode(TYPE, 601, 299, from);
        assertNotNull(inserted);
        // Accroche au pas de 16 : (601, 299) → (608, 304).
        assertEquals(new Vec2d(608, 304), bp.node(inserted).position());
        assertEquals(1, bp.links().size());
        Link link = bp.links().iterator().next();
        assertEquals(inserted, link.toNode());
        assertEquals("exec_in", link.toPin());
    }

    // ------------------------------------------------------------- liens (5.12)

    /** Câble n1.exec_out → n2.exec_in et rend un point situé sur la courbe. */
    private Vec2d wireUpAndPickMiddle() {
        Vec2d from = out1();
        controller.press(from.x(), from.y(), false);
        Vec2d to = in2(0);
        controller.drag(to.x(), to.y());
        controller.release(false);
        assertEquals(1, bp.links().size());
        // Deux pins à la même hauteur : le milieu de la Bézier est sur la corde.
        return new Vec2d((from.x() + to.x()) / 2, (from.y() + to.y()) / 2);
    }

    @Test
    void cliquerUnFilLeSelectionne() {
        Vec2d middle = wireUpAndPickMiddle();
        assertNull(controller.selectedLink(), "rien de sélectionné au départ");

        controller.press(middle.x(), middle.y(), false);
        assertNotNull(controller.selectedLink(), "le fil sous le curseur");
        assertEquals(CanvasController.Gesture.NONE, controller.gesture(),
                "et surtout pas d'élastique : le clic a bien été consommé");
    }

    @Test
    void supprRetireLeFilSelectionne() {
        Vec2d middle = wireUpAndPickMiddle();
        controller.press(middle.x(), middle.y(), false);

        controller.deleteSelection();
        assertEquals(0, bp.links().size());
        assertNull(controller.selectedLink());

        assertTrue(controller.undo(), "et Ctrl+Z le remet");
        assertEquals(1, bp.links().size());
    }

    /**
     * Le geste NONE ne déclenche pas de {@code release()} côté widget : si la branche
     * « fil » laissait son geste d'annulation ouvert, TOUTES les modifications
     * suivantes fusionneraient en une seule entrée d'annulation.
     */
    @Test
    void selectionnerUnFilNeFusionnePasLesAnnulationsSuivantes() {
        Vec2d middle = wireUpAndPickMiddle();
        controller.press(middle.x(), middle.y(), false);

        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertTrue(controller.applyOp(new EditOperation.AddNode(a, TYPE, new Vec2d(500, 0))));
        assertTrue(controller.applyOp(new EditOperation.AddNode(b, TYPE, new Vec2d(600, 0))));

        assertTrue(controller.undo());
        assertNull(bp.node(b));
        assertNotNull(bp.node(a), "le premier ajout survit : ce sont deux annulations");
    }

    @Test
    void loinDeToutFilOnRetombeSurLElastique() {
        wireUpAndPickMiddle();
        controller.press(0, 900, false);
        assertNull(controller.selectedLink());
        assertEquals(CanvasController.Gesture.RUBBER, controller.gesture());
    }

    // -------------------------------------------- remplacement total et navigation

    /**
     * L'import de script (5.11) écrase TOUT le graphe. C'est l'opération la plus
     * destructrice de l'éditeur et elle n'était couverte par aucun test : sa promesse
     * — « Ctrl+Z annule » — n'était vérifiée nulle part.
     */
    @Test
    void remplacerToutLeGrapheTientEnUneSeuleAnnulation() {
        wireUpAndPickMiddle();
        assertTrue(controller.applyOp(new EditOperation.AddVariable(
                new fr.blueprint.core.graph.Variable("ancienne", PinTypes.DOUBLE, null,
                        fr.blueprint.core.graph.VarScope.GRAPH, false))));
        int avant = bp.nodes().size();
        assertEquals(2, avant);

        Blueprint fragment = new Blueprint(Identifier.fromNamespaceAndPath("test", "fragment"));
        UUID importe = UUID.randomUUID();
        assertTrue(new EditOperation.AddNode(importe, TYPE, new Vec2d(42, 42))
                .apply(fragment, LOOKUP).applied());
        assertTrue(new EditOperation.AddVariable(
                new fr.blueprint.core.graph.Variable("nouvelle", PinTypes.DOUBLE, null,
                        fr.blueprint.core.graph.VarScope.GRAPH, false))
                .apply(fragment, LOOKUP).applied());

        controller.replaceAll(fragment);

        assertEquals(1, bp.nodes().size(), "les anciens nœuds sont partis");
        assertNotNull(bp.node(importe), "et l'UUID importé est CONSERVÉ tel quel");
        assertEquals(new Vec2d(42, 42), bp.node(importe).position());
        assertEquals(0, bp.links().size(), "les liens des anciens nœuds avec eux");
        assertTrue(bp.variables().containsKey("nouvelle"));
        assertFalse(bp.variables().containsKey("ancienne"));

        assertTrue(controller.undo(), "un seul Ctrl+Z remet tout en place");
        assertEquals(avant, bp.nodes().size());
        assertEquals(1, bp.links().size());
        assertTrue(bp.variables().containsKey("ancienne"));
        assertNull(bp.node(importe));
    }

    /** Le nœud Get/Set d'une variable, déposé depuis le panneau (5.5). */
    @Test
    void deposerUnNoeudDeVariableLeRelieALaVariable() {
        UUID id = controller.insertVariableNode(false, "compteur", 100, 100);
        assertNotNull(id);
        assertEquals(fr.blueprint.core.graph.VarNodes.GET, bp.node(id).typeId());
        assertEquals("compteur", bp.node(id)
                .literals().get(fr.blueprint.core.graph.VarNodes.VAR_PIN).value());

        assertTrue(controller.undo(), "dépôt et littéral = un seul geste");
        assertNull(bp.node(id));
    }

    /** Clic sur un diagnostic : recentrer ET sélectionner, sinon on cherche encore. */
    @Test
    void focusNodeRecentreEtSelectionne() {
        assertFalse(controller.focusNode(UUID.randomUUID(), 800, 600),
                "un nœud disparu ne fait pas bouger la caméra");

        assertTrue(controller.focusNode(n2, 800, 600));
        assertEquals(java.util.Set.of(n2), controller.selection().ids());

        NodeGeometry.Box box = controller.boxOf(n2);
        assertEquals(400, camera.toScreenX(box.x() + box.width() / 2), 1e-6,
                "le centre du nœud atterrit au centre de l'écran");
        assertEquals(300, camera.toScreenY(box.y() + box.height() / 2), 1e-6);
    }

    /** {@code pinDef} sert au rendu des fils (couleur, coercition) : jamais testé. */
    @Test
    void pinDefTrouveEntreesEtSorties() {
        assertNotNull(controller.pinDef(n1, "exec_out"));
        assertNotNull(controller.pinDef(n1, "a"));
        assertNull(controller.pinDef(n1, "inconnu"));
        assertNull(controller.pinDef(UUID.randomUUID(), "exec_out"),
                "un nœud absent n'a pas de pins");
    }

    @Test
    void autoLayoutDeplaceLeGraphe() {
        wireUpAndPickMiddle();
        Vec2d avant = bp.node(n2).position();
        assertTrue(controller.autoLayout());
        assertFalse(avant.equals(bp.node(n2).position()));

        assertTrue(controller.undo(), "une seule entrée d'annulation pour tout le graphe");
        assertEquals(avant, bp.node(n2).position());
    }

    // ------------------------------------------ insertion sur un fil (5.13, UE5)

    /** Amène le nœud {@code id} au centre du fil et relâche. */
    private void dragOnto(UUID id, Link link) {
        Vec2d a = controller.pinCenter(link.fromNode(), link.fromPin());
        Vec2d b = controller.pinCenter(link.toNode(), link.toPin());
        NodeGeometry.Box box = controller.boxOf(id);
        double cx = (a.x() + b.x()) / 2;
        double cy = (a.y() + b.y()) / 2;
        // Saisir le coin haut-gauche, viser le centre du fil avec le CENTRE du nœud.
        controller.press(box.x() + 2, box.y() + 2, false);
        controller.drag(cx - box.width() / 2 + 2, cy - box.height() / 2 + 2);
    }

    @Test
    void lacherUnNoeudSurUnFilLInsereAuMilieu() {
        wireUpAndPickMiddle();
        Link original = bp.links().iterator().next();
        UUID n3 = addNode(1000, 1000);

        dragOnto(n3, original);
        assertEquals(original, controller.spliceCandidate(),
                "le fil visé est signalé PENDANT le glissement, sinon le geste ne se découvre pas");
        controller.release(false);

        assertNull(controller.spliceCandidate());
        assertEquals(2, bp.links().size(), "un fil coupé en deux");
        assertFalse(bp.links().contains(original));
        assertTrue(bp.links().stream().anyMatch(l ->
                l.fromNode().equals(n1) && l.toNode().equals(n3)), "amont");
        assertTrue(bp.links().stream().anyMatch(l ->
                l.fromNode().equals(n3) && l.toNode().equals(n2)), "aval");

        assertTrue(controller.undo(), "déplacement ET recâblage : un seul Ctrl+Z");
        assertEquals(1, bp.links().size());
        assertTrue(bp.links().contains(original));
    }

    /** Un nœud déjà sur le fil ne se réinsère pas sur lui-même. */
    @Test
    void unNoeudNeSInserePasSurSonPropreFil() {
        wireUpAndPickMiddle();
        Link original = bp.links().iterator().next();

        dragOnto(n1, original);
        assertNull(controller.spliceCandidate());
        controller.release(false);
        assertEquals(1, bp.links().size());
    }

    /** Déplacer plusieurs nœuds à la fois n'insère rien : quel nœud passerait ? */
    @Test
    void uneSelectionMultipleNeSInserePas() {
        wireUpAndPickMiddle();
        Link original = bp.links().iterator().next();
        UUID n3 = addNode(1000, 1000);
        UUID n4 = addNode(1100, 1000);
        controller.selection().selectAll(List.of(n3, n4), false);

        Vec2d a = controller.pinCenter(original.fromNode(), original.fromPin());
        Vec2d b = controller.pinCenter(original.toNode(), original.toPin());
        NodeGeometry.Box box = controller.boxOf(n3);
        controller.press(box.x() + 2, box.y() + 2, true);
        controller.drag((a.x() + b.x()) / 2 - box.width() / 2, (a.y() + b.y()) / 2);
        assertNull(controller.spliceCandidate());
        controller.release(true);
        assertEquals(1, bp.links().size());
    }

    // ---------------------------------------------- actions du menu contextuel (5.13)

    @Test
    void casserLesLiensDUnNoeudLesRetireTousEnUneAnnulation() {
        wireUpAndPickMiddle();
        assertEquals(1, bp.links().size());

        assertTrue(controller.breakNodeLinks(n1));
        assertEquals(0, bp.links().size());
        assertTrue(controller.undo(), "un seul Ctrl+Z, quel que soit le nombre de liens");
        assertEquals(1, bp.links().size());

        assertFalse(controller.breakNodeLinks(UUID.randomUUID()),
                "un nœud sans lien ne crée pas d'entrée d'annulation vide");
    }

    @Test
    void casserLesLiensDUnPinNeToucheQueLuiMeme() {
        wireUpAndPickMiddle();
        Vec2d out = out1();
        CanvasController.PinRef pin = controller.pinAt(out.x(), out.y());
        assertNotNull(pin);

        assertTrue(controller.breakPinLinks(pin));
        assertEquals(0, bp.links().size());
        assertFalse(controller.breakPinLinks(pin), "plus rien à casser");
    }

    @Test
    void valeurParDefautRetireLeLitteralExplicite() {
        assertTrue(controller.setLiteral(n1, "a",
                fr.blueprint.api.pin.LiteralValue.of(PinTypes.DOUBLE, 7.0)));
        assertNotNull(bp.node(n1).literal("a"));

        assertTrue(controller.resetLiteral(n1, "a"));
        assertNull(bp.node(n1).literal("a"));
        assertTrue(controller.undo(), "et la valeur saisie revient");
        assertEquals(7.0, bp.node(n1).literal("a").value());
    }

    /**
     * Promouvoir un pin en variable (geste d'Unreal) : quatre opérations — créer la
     * variable, poser le nœud, y écrire son nom, câbler — qui doivent former UNE
     * seule intention. Quatre Ctrl+Z pour défaire un clic serait absurde.
     */
    @Test
    void promouvoirUnPinCreeLaVariableEtLaCable() {
        assertTrue(controller.setLiteral(n1, "a",
                fr.blueprint.api.pin.LiteralValue.of(PinTypes.DOUBLE, 3.5)));
        Vec2d entree = NodeGeometry.inputPinCenter(controller.boxOf(n1), 1);
        CanvasController.PinRef pin = controller.pinAt(entree.x(), entree.y());
        assertNotNull(pin);
        assertEquals("a", pin.pin());

        String name = controller.promoteToVariable(pin, "a");
        assertEquals("a", name);

        var variable = bp.variables().get("a");
        assertNotNull(variable);
        assertEquals(PinTypes.DOUBLE, variable.type());
        assertEquals(3.5, variable.defaultValue().value(),
                "la valeur déjà saisie devient le défaut : promouvoir ne perd rien");

        assertEquals(3, bp.nodes().size(), "un nœud Get est apparu");
        assertEquals(1, bp.links().size(), "et il est câblé sur le pin");
        Link link = bp.links().iterator().next();
        assertEquals(n1, link.toNode());
        assertEquals("a", link.toPin());

        assertTrue(controller.undo(), "UNE annulation pour les quatre opérations");
        assertEquals(2, bp.nodes().size());
        assertTrue(bp.variables().isEmpty());
        assertEquals(0, bp.links().size());
    }

    @Test
    void promouvoirUnPinExecNaAucunSens() {
        Vec2d exec = NodeGeometry.inputPinCenter(controller.boxOf(n1), 0);
        CanvasController.PinRef pin = controller.pinAt(exec.x(), exec.y());
        assertNotNull(pin);
        assertNull(controller.promoteToVariable(pin, "exec_in"));
        assertTrue(bp.variables().isEmpty(), "et rien n'est créé au passage");
    }

    /** Deux promotions du même nom ne doivent pas se marcher dessus. */
    @Test
    void deuxPromotionsDuMemeNomSeDistinguent() {
        Vec2d a1 = NodeGeometry.inputPinCenter(controller.boxOf(n1), 1);
        assertEquals("a", controller.promoteToVariable(controller.pinAt(a1.x(), a1.y()), "a"));

        Vec2d a2 = NodeGeometry.inputPinCenter(controller.boxOf(n2), 1);
        assertEquals("a2", controller.promoteToVariable(controller.pinAt(a2.x(), a2.y()), "a"));
        assertEquals(2, bp.variables().size());
    }

    // ------------------------------------------------------- passe de dette (low)

    /**
     * Un nœud qui en recouvre un autre doit MASQUER ses pins : sinon on câble un pin
     * qu'on ne voit pas, et le lien apparaît en sortant d'un nœud au hasard.
     */
    @Test
    void unNoeudRecouvertNeLaissePasSaisirSesPins() {
        Vec2d pin = out1();
        assertNotNull(controller.pinAt(pin.x(), pin.y()));

        // n3, ajouté après n1, se dessine par-dessus et couvre le pin.
        UUID n3 = addNode(pin.x() - NodeGeometry.WIDTH / 2, pin.y() - 20);
        assertNotNull(controller.hitTest(pin.x(), pin.y()));
        assertEquals(n3, controller.hitTest(pin.x(), pin.y()).node().uuid());
        assertNull(controller.pinAt(pin.x(), pin.y()),
                "le pin de n1 est sous n3 : il ne se saisit plus");
    }

    /**
     * Un câblage refusé était un no-op SILENCIEUX. Le validateur produit pourtant la
     * phrase exacte à chaque fois — elle était simplement jetée.
     */
    @Test
    void unCablageRefuseExpliquePourquoi() {
        assertNull(controller.takeRefusal(), "rien à raconter au départ");

        // Deuxième lien sur le même exec_out : refusé par cardinalité.
        Vec2d from = out1();
        controller.press(from.x(), from.y(), false);
        Vec2d to = in2(0);
        controller.drag(to.x(), to.y());
        controller.release(false);
        assertEquals(1, bp.links().size());

        UUID n3 = addNode(600, 300);
        controller.press(from.x(), from.y(), false);
        Vec2d autre = NodeGeometry.inputPinCenter(controller.boxOf(n3), 0);
        controller.drag(autre.x(), autre.y());
        controller.release(false);

        assertEquals(1, bp.links().size(), "le lien est bien refusé");
        var refus = controller.takeRefusal();
        assertNotNull(refus, "et le joueur peut enfin savoir pourquoi");
        assertEquals(fr.blueprint.core.graph.DiagnosticCode.EXEC_OUT_ALREADY_LINKED,
                refus.code());
        assertNull(controller.takeRefusal(), "consommé une fois, pas répété à l'infini");
    }

    @Test
    void cliquerUnNoeudDefaitLaSelectionDeFil() {
        Vec2d middle = wireUpAndPickMiddle();
        controller.press(middle.x(), middle.y(), false);
        assertNotNull(controller.selectedLink());

        controller.press(10, 10, false);
        assertNull(controller.selectedLink(), "sinon Suppr effacerait le nœud ET le fil");
    }
}
