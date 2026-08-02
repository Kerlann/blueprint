package fr.blueprint.core.registry;

import fr.blueprint.api.node.NodeCategories;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.PinKind;
import fr.blueprint.api.pin.PinTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Builder de NodeType (story 2.1) — chaque incohérence lève au build, jamais en jeu. */
class NodeTypeBuilderTest {

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("mymod", path);
    }

    @Test
    void nominalExecNodeDescribesItselfCompletely() {
        NodeType heal = NodeType.builder(id("heal_player"))
                .category(NodeCategories.ENTITY)
                .exec()
                .in("player", PinTypes.PLAYER)
                .in("amount", PinTypes.DOUBLE, 1.0)
                .out("healed", PinTypes.BOOL)
                .permission(Permission.GAMEPLAY)
                .action(ctx -> ctx.out("healed", true))
                .build();

        assertEquals(3, heal.inputs().size());       // exec_in + player + amount
        assertEquals(2, heal.outputs().size());      // exec_out + healed
        assertEquals(PinKind.EXEC, heal.inputs().get(0).kind());
        assertEquals("blueprint.node.mymod.heal_player.name", heal.titleKey());
        assertEquals(Permission.GAMEPLAY, heal.permission());
        assertEquals(1, heal.fuelCost());
        assertTrue(heal.deterministic());
        // Défaut typé exposé sur le pin (AC3).
        NodeType.PinSpec amount = heal.inputs().get(2);
        assertNotNull(amount.defaultValue());
        assertEquals(1.0, amount.defaultValue().value());
        // Immuabilité (AC2).
        assertThrows(UnsupportedOperationException.class,
                () -> heal.inputs().add(heal.inputs().get(0)));
    }

    @Test
    void pureNodeHasNoExecPins() {
        NodeType pure = NodeType.builder(id("mana_of"))
                .pure()
                .in("player", PinTypes.PLAYER)
                .out("mana", PinTypes.DOUBLE)
                .action(ctx -> ctx.out("mana", 0.0))
                .build();
        assertTrue(pure.pure());
        assertTrue(pure.inputs().stream().noneMatch(p -> p.kind() == PinKind.EXEC));
    }

    @Test
    void branchingNodeDeclaresNamedExecOutputs() {
        NodeType branch = NodeType.builder(id("check"))
                .execIn("exec_in")
                .execOut("completed")
                .execOut("failed")
                .in("quest", PinTypes.RESOURCE_LOCATION)
                .action(ctx -> ctx.exec("completed"))
                .build();
        assertEquals(2, branch.outputs().size());
        assertEquals("completed", branch.outputs().get(0).name());
    }

    @Test
    void wrongTypedDefaultFailsAtBuild() {
        var builder = NodeType.builder(id("bad_default"));
        var ex = assertThrows(IllegalStateException.class,
                () -> builder.in("amount", PinTypes.INT, "pas un entier"));
        assertTrue(ex.getMessage().contains("bad_default"), ex.getMessage());
    }

    @Test
    void pureWithExecPinsFailsAtBuild() {
        assertThrows(IllegalStateException.class, () -> NodeType.builder(id("both"))
                .pure().exec().action(ctx -> {
                }).build());
    }

    @Test
    void neitherPureNorExecFailsAtBuild() {
        assertThrows(IllegalStateException.class, () -> NodeType.builder(id("neither"))
                .in("a", PinTypes.INT).action(ctx -> {
                }).build());
    }

    @Test
    void duplicatePinNameOnSameSideFailsAtBuild() {
        assertThrows(IllegalStateException.class, () -> NodeType.builder(id("dup"))
                .exec()
                .in("x", PinTypes.INT)
                .in("x", PinTypes.STRING)
                .action(ctx -> {
                }).build());
        // Une entrée et une sortie peuvent partager un nom (liens orientés).
        NodeType ok = NodeType.builder(id("mirror"))
                .pure().in("value", PinTypes.INT).out("value", PinTypes.INT)
                .action(ctx -> {
                }).build();
        assertEquals("value", ok.outputs().get(0).name());
    }

    @Test
    void missingActionFailsAtBuild() {
        assertThrows(IllegalStateException.class,
                () -> NodeType.builder(id("no_action")).exec().build());
    }

    @Test
    void undeclaredGenericSlotFailsAtBuild() {
        var ex = assertThrows(IllegalStateException.class, () -> NodeType.builder(id("first"))
                .pure()
                .in("list", PinTypes.listOf(PinTypes.generic("T")))
                .out("elem", PinTypes.generic("T"))
                .action(ctx -> {
                }).build());
        assertTrue(ex.getMessage().contains("generic"), ex.getMessage());

        // Déclaré via generic("T") : accepté ; « any » est toujours implicite.
        NodeType ok = NodeType.builder(id("first_ok"))
                .pure().generic("T")
                .in("list", PinTypes.listOf(PinTypes.generic("T")))
                .out("elem", PinTypes.generic("T"))
                .action(ctx -> {
                }).build();
        assertEquals(1, ok.inputs().size());
        NodeType any = NodeType.builder(id("any_ok"))
                .pure().in("value", PinTypes.ANY).out("copy", PinTypes.ANY)
                .action(ctx -> {
                }).build();
        assertNotNull(any);
    }

    @Test
    void invalidFuelCostFailsAtBuild() {
        assertThrows(IllegalStateException.class, () -> NodeType.builder(id("free"))
                .exec().fuelCost(0).action(ctx -> {
                }).build());
    }
}
