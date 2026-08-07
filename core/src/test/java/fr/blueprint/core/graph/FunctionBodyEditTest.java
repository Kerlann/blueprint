package fr.blueprint.core.graph;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Éditer <b>dans</b> un corps de fonction, geste par geste (story 20.2, tâche 1).
 *
 * <p>La story dit de faire cette tâche <b>avant tout dessin</b>, et de s'arrêter si elle
 * résiste. Ce qu'elle protège : {@code SetBody} remplace un corps en bloc, ce qui convient
 * à un collage et donnerait un pas d'annulation par pixel sur un glissement de nœud — et
 * dont l'inverse porterait tout le corps, donc annulerait aussi ce qu'on ne voulait pas.
 */
class FunctionBodyEditTest {

    private static final PluginLoader.LoadedRegistries LOADED = PluginLoader.load(List.of(), true);
    private static final Identifier BP = Identifier.fromNamespaceAndPath("test", "edit");

    private static Identifier type(String path) {
        return Identifier.fromNamespaceAndPath("blueprint", path);
    }

    /** Un blueprint portant une fonction vide nommée « f ». */
    private static Blueprint withEmptyFunction() {
        Blueprint bp = new Blueprint(BP);
        GraphLoader.addFunction(bp, BlueprintFunction.of("f",
                List.of(new BlueprintFunction.Param("n", PinTypes.DOUBLE)),
                List.of(new BlueprintFunction.Param("r", PinTypes.DOUBLE))));
        return bp;
    }

    private static EditOperation.Result apply(Blueprint bp, EditOperation op) {
        return op.apply(bp, LOADED.nodes());
    }

    private static void applyOk(Blueprint bp, EditOperation op) {
        var result = apply(bp, op);
        assertTrue(result.applied(), () -> "opération refusée : " + result.refusal());
    }

    /** Poser un nœud dans un corps, et l'annuler. */
    @Test
    void poserUnNoeudDansUnCorpsEtLAnnuler() {
        Blueprint bp = withEmptyFunction();
        UUID node = UUID.randomUUID();

        var added = apply(bp, new FunctionOps.AddNodeIn("f", node, type("math/add"),
                new Vec2d(10, 20)));
        assertTrue(added.applied());
        assertEquals(1, bp.function("f").nodes().size());

        applyOk(bp, added.inverse());
        assertTrue(bp.function("f").nodes().isEmpty(),
                "l'inverse d'un ajout doit retirer exactement ce qu'il a posé");
    }

    /**
     * <b>Le test qui décide.</b> Déplacer un nœud d'un corps rend un {@code MoveNodeIn},
     * et non un {@code SetBody}.
     *
     * <p>Un glissement de souris produit une opération par image. Si chacune remplaçait le
     * corps entier, l'historique porterait des dizaines de copies complètes, et annuler
     * remonterait un pixel à la fois en restaurant à chaque pas tout ce que le corps
     * contient — y compris ce qu'on aurait modifié entre-temps.
     */
    @Test
    void deplacerUnNoeudRendUnPasDAnnulationPrecis() {
        Blueprint bp = withEmptyFunction();
        UUID node = UUID.randomUUID();
        applyOk(bp, new FunctionOps.AddNodeIn("f", node, type("math/add"), new Vec2d(0, 0)));

        var moved = apply(bp, new FunctionOps.MoveNodeIn("f", node, new Vec2d(100, 50)));

        assertTrue(moved.applied());
        assertInstanceOf(FunctionOps.MoveNodeIn.class, moved.inverse(),
                "l'inverse d'un déplacement doit être un déplacement, pas un remplacement "
                        + "de corps : sinon annuler un glissement restaurerait tout le corps");
        assertEquals(new Vec2d(100, 50), bp.function("f").nodes().get(node).position());

        applyOk(bp, moved.inverse());
        assertEquals(new Vec2d(0, 0), bp.function("f").nodes().get(node).position());
    }

    /** Un littéral se pose et se retire, et l'inverse rend l'ancienne valeur. */
    @Test
    void unLitteralSePoseEtSAnnule() {
        Blueprint bp = withEmptyFunction();
        UUID node = UUID.randomUUID();
        applyOk(bp, new FunctionOps.AddNodeIn("f", node, type("math/add"), new Vec2d(0, 0)));

        var set = apply(bp, new FunctionOps.SetLiteralIn("f", node, "b",
                LiteralValue.of(PinTypes.DOUBLE, 7.0)));
        assertTrue(set.applied());
        assertEquals(7.0, bp.function("f").nodes().get(node).literal("b").value());

        applyOk(bp, set.inverse());
        assertNull(bp.function("f").nodes().get(node).literal("b"),
                "l'inverse doit rendre l'absence de littéral, pas un zéro");
    }

    /**
     * <b>Un type incompatible est refusé dans un corps comme ailleurs.</b>
     *
     * <p>C'est ce que garantit le passage par {@code canLinkIn}, qui pose la question à la
     * MÊME règle que le graphe principal. Une seconde implémentation aurait fini par
     * accepter dans une fonction ce qu'elle refuse dehors.
     */
    @Test
    void laRegleDeCablageEstLaMemeDansUnCorps() {
        Blueprint bp = withEmptyFunction();
        UUID texte = UUID.randomUUID();
        UUID somme = UUID.randomUUID();
        applyOk(bp, new FunctionOps.AddNodeIn("f", texte, type("string/upper"), new Vec2d(0, 0)));
        applyOk(bp, new FunctionOps.AddNodeIn("f", somme, type("math/add"), new Vec2d(0, 0)));

        var refused = apply(bp, new FunctionOps.AddLinkIn("f",
                new Link(texte, "result", somme, "a")));

        assertFalse(refused.applied(), "une chaîne ne se câble pas sur un nombre");
        assertEquals(DiagnosticCode.TYPE_MISMATCH, refused.refusal().code());
    }

    /** Et un câblage juste passe, puis se défait. */
    @Test
    void unCablageJustePasseEtSeDefait() {
        Blueprint bp = withEmptyFunction();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        applyOk(bp, new FunctionOps.AddNodeIn("f", a, type("math/add"), new Vec2d(0, 0)));
        applyOk(bp, new FunctionOps.AddNodeIn("f", b, type("math/mul"), new Vec2d(0, 0)));

        var linked = apply(bp, new FunctionOps.AddLinkIn("f", new Link(a, "result", b, "a")));
        assertTrue(linked.applied(), () -> String.valueOf(linked.refusal()));
        assertEquals(1, bp.function("f").links().size());

        applyOk(bp, linked.inverse());
        assertTrue(bp.function("f").links().isEmpty());
    }

    /**
     * Retirer un nœud emporte ses liens — et l'annulation les remet.
     *
     * <p>Les liens coupés voyagent DANS l'inverse. Les recalculer au moment de l'annulation
     * regarderait un corps qui a pu changer entre-temps, et remettrait un fil vers un nœud
     * qui n'existe plus.
     */
    @Test
    void retirerUnNoeudEmporteSesLiensEtLAnnulationLesRemet() {
        Blueprint bp = withEmptyFunction();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        applyOk(bp, new FunctionOps.AddNodeIn("f", a, type("math/add"), new Vec2d(0, 0)));
        applyOk(bp, new FunctionOps.AddNodeIn("f", b, type("math/mul"), new Vec2d(0, 0)));
        applyOk(bp, new FunctionOps.AddLinkIn("f", new Link(a, "result", b, "a")));

        var removed = apply(bp, new FunctionOps.RemoveNodeIn("f", a));
        assertTrue(removed.applied());
        assertTrue(bp.function("f").links().isEmpty(), "un fil pendant n'existe pas");

        applyOk(bp, removed.inverse());
        assertEquals(2, bp.function("f").nodes().size());
        assertEquals(1, bp.function("f").links().size(),
                "l'annulation doit remettre le câblage, pas seulement le nœud");
    }

    /** Chaque geste incrémente la révision : l'IR est mise en cache par elle. */
    @Test
    void chaqueGesteInvalideLIr() {
        Blueprint bp = withEmptyFunction();
        UUID node = UUID.randomUUID();
        int before = bp.revision();

        applyOk(bp, new FunctionOps.AddNodeIn("f", node, type("math/add"), new Vec2d(0, 0)));
        applyOk(bp, new FunctionOps.MoveNodeIn("f", node, new Vec2d(5, 5)));
        applyOk(bp, new FunctionOps.SetLiteralIn("f", node, "a",
                LiteralValue.of(PinTypes.DOUBLE, 1.0)));

        assertEquals(before + 3, bp.revision(),
                "un corps corrigé qui garderait sa révision continuerait de tourner dans "
                        + "son ancienne version jusqu'au redémarrage du serveur");
    }

    /** Une fonction absente refuse proprement, elle ne tombe pas. */
    @Test
    void uneFonctionAbsenteRefuseProprement() {
        Blueprint bp = new Blueprint(BP);
        var result = apply(bp, new FunctionOps.AddNodeIn("fantome", UUID.randomUUID(),
                type("math/add"), new Vec2d(0, 0)));

        assertFalse(result.applied());
        assertNotNull(result.refusal());
        assertEquals(DiagnosticCode.FUNCTION_NOT_FOUND, result.refusal().code());
    }
}
