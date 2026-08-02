package fr.blueprint.core.graph;

import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.PinKind;
import fr.blueprint.api.pin.PinTypes;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Types de nœuds de test : le raccord {@link NodeTypeLookup} en attendant la story 2.2. */
final class TestNodes {

    static final Identifier START = id("start");
    static final Identifier PRINT = id("print");
    static final Identifier ADD = id("add");
    static final Identifier HALF = id("half");
    static final Identifier MAKE_LIST = id("make_list");
    static final Identifier FIRST = id("first");
    static final Identifier BOOM = id("boom");
    static final Identifier NEEDS_ENTITY = id("needs_entity");
    static final Identifier MISSING = Identifier.fromNamespaceAndPath("absentmod", "gone");

    private static final NodeShape.PinDef EXEC_IN =
            new NodeShape.PinDef("exec_in", PinKind.EXEC, PinTypes.EXEC, false);
    private static final NodeShape.PinDef EXEC_OUT =
            new NodeShape.PinDef("exec_out", PinKind.EXEC, PinTypes.EXEC, false);

    private static final Map<Identifier, NodeShape> SHAPES = Map.of(
            START, new NodeShape(List.of(), List.of(EXEC_OUT), true, Permission.SAFE),
            PRINT, new NodeShape(
                    List.of(EXEC_IN, new NodeShape.PinDef("text", PinKind.DATA, PinTypes.STRING, true)),
                    List.of(EXEC_OUT), false, Permission.GAMEPLAY),
            ADD, new NodeShape(
                    List.of(data("a", PinTypes.INT), data("b", PinTypes.INT)),
                    List.of(data("sum", PinTypes.INT)), false, Permission.SAFE),
            HALF, new NodeShape(
                    List.of(data("value", PinTypes.DOUBLE)),
                    List.of(data("result", PinTypes.DOUBLE)), false, Permission.SAFE),
            MAKE_LIST, new NodeShape(
                    List.of(),
                    List.of(data("list", PinTypes.listOf(PinTypes.INT))), false, Permission.SAFE),
            FIRST, new NodeShape(
                    List.of(data("list", PinTypes.listOf(PinTypes.generic("T")))),
                    List.of(data("elem", PinTypes.generic("T"))), false, Permission.SAFE),
            BOOM, new NodeShape(List.of(EXEC_IN), List.of(EXEC_OUT), false, Permission.ADMIN),
            NEEDS_ENTITY, new NodeShape(
                    List.of(new NodeShape.PinDef("target", PinKind.DATA, PinTypes.ENTITY, true)),
                    List.of(), false, Permission.SAFE));

    static final NodeTypeLookup LOOKUP = SHAPES::get;

    private TestNodes() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("test", path);
    }

    private static NodeShape.PinDef data(String name, fr.blueprint.api.pin.PinType type) {
        return new NodeShape.PinDef(name, PinKind.DATA, type, false);
    }

    static Blueprint newGraph() {
        return new Blueprint(Identifier.fromNamespaceAndPath("test", "graph"));
    }

    static UUID uuid(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Applique une opération qui doit réussir. */
    static EditOperation apply(Blueprint bp, EditOperation op) {
        EditOperation.Result result = op.apply(bp, LOOKUP);
        assertNull(result.refusal(), () -> "opération refusée : " + result.refusal());
        return result.inverse();
    }

    /** Applique une opération qui doit être refusée, et retourne le diagnostic. */
    static Diagnostic refuse(Blueprint bp, EditOperation op) {
        EditOperation.Result result = op.apply(bp, LOOKUP);
        assertTrue(result.refusal() != null, "l'opération aurait dû être refusée");
        return result.refusal();
    }

    static UUID node(Blueprint bp, String seed, Identifier type) {
        UUID id = uuid(seed);
        apply(bp, new EditOperation.AddNode(id, type, Vec2d.ZERO));
        return id;
    }
}
