package fr.blueprint.core.graph;

import com.mojang.serialization.Codec;
import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinType;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.registry.PinTypeRegistryImpl;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.function.Function;

import static fr.blueprint.core.graph.TestNodes.node;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Préservation (story 1.4, AC4 — principe P4) : mod retiré → rien ne se perd,
 * re-round-trip stable → mod réinstallé → contenu identique à l'original.
 */
class PreservationTest {

    /** Type de pin d'un mod tiers fictif. */
    private static final PinType MANA = PinType.builder(
                    Identifier.fromNamespaceAndPath("manamod", "mana"))
            .javaType(Integer.class)
            .codec(Codec.INT)
            .streamCodec(ByteBufCodecs.VAR_INT)
            .build();

    private static Function<Identifier, PinType> resolverWithMana() {
        var registry = new PinTypeRegistryImpl();
        registry.registerBuiltins();
        registry.currentProvider("manamod");
        registry.register(MANA);
        return id -> registry.get(id).orElse(null);
    }

    private static Function<Identifier, PinType> resolverWithoutMana() {
        var registry = new PinTypeRegistryImpl();
        registry.registerBuiltins();
        return id -> registry.get(id).orElse(null);
    }

    private static Blueprint graphUsingMana() {
        var bp = TestNodes.newGraph();
        UUID print = node(bp, "p", TestNodes.PRINT);
        // Littéral d'un type tiers, posé hors validation (comme un chargement le ferait).
        bp.node(print).setLiteral("mana_cost", LiteralValue.of(MANA, 42));
        bp.putVariable(new Variable("reserve", MANA, LiteralValue.of(MANA, 100), VarScope.PLAYER, false));
        return bp;
    }

    @Test
    void unknownNodeTypeSurvivesRoundTrip() {
        var bp = TestNodes.newGraph();
        UUID ghost = TestNodes.uuid("g");
        bp.putNode(new Node(ghost, TestNodes.MISSING, new Vec2d(3, 4)));
        Blueprint decoded = GraphNbt.decode(GraphNbt.encode(bp), resolverWithoutMana());
        assertEquals(TestNodes.MISSING, decoded.node(ghost).typeId(),
                "l'identifiant d'un nœud inconnu est conservé tel quel (FR40)");
        assertTrue(bp.contentEquals(decoded));
    }

    @Test
    void removedModThenReinstalledLosesNothing() {
        Blueprint original = graphUsingMana();
        CompoundTag saved = GraphNbt.encode(original);

        // 1. Mod retiré : les données mana sont préservées en brut, pas résolues.
        Blueprint withoutMod = GraphNbt.decode(saved, resolverWithoutMana());
        UUID print = TestNodes.uuid("p");
        assertNull(withoutMod.node(print).literal("mana_cost"), "littéral non résolu = non exposé");
        assertFalse(withoutMod.variables().containsKey("reserve"), "variable non résolue = non exposée");
        assertEquals(1, withoutMod.node(print).preservedLiterals().size());
        assertEquals(1, withoutMod.preservedVariables().size());

        // 2. Re-sauvegarde SANS le mod : le NBT ré-émis est identique à l'original.
        CompoundTag resaved = GraphNbt.encode(withoutMod);
        assertEquals(saved, resaved, "un cycle sans le mod ne doit pas dégrader la sauvegarde");

        // 3. Mod réinstallé : tout revient, à l'identique.
        Blueprint restored = GraphNbt.decode(resaved, resolverWithMana());
        assertEquals(42, restored.node(print).literal("mana_cost").value());
        assertEquals(100, restored.variables().get("reserve").defaultValue().value());
        assertTrue(original.contentEquals(restored), "restauration non identique à l'original");
    }

    @Test
    void ghostExecLoopNoLongerReportsFalseDataCycle() {
        // Reprise QA GHOST-001 : boucle exec p1↔p2, puis les deux types disparaissent.
        var bp = TestNodes.newGraph();
        UUID p1 = node(bp, "p1", TestNodes.PRINT);
        UUID p2 = node(bp, "p2", TestNodes.PRINT);
        TestNodes.apply(bp, new EditOperation.SetLiteral(p1, "text", LiteralValue.of(PinTypes.STRING, "a")));
        TestNodes.apply(bp, new EditOperation.SetLiteral(p2, "text", LiteralValue.of(PinTypes.STRING, "b")));
        TestNodes.apply(bp, new EditOperation.AddLink(new Link(p1, "exec_out", p2, "exec_in")));
        TestNodes.apply(bp, new EditOperation.AddLink(new Link(p2, "exec_out", p1, "exec_in")));

        NodeTypeLookup withoutPrint = typeId ->
                typeId.equals(TestNodes.PRINT) ? null : TestNodes.LOOKUP.shape(typeId);
        var result = GraphValidator.validate(bp, withoutPrint);

        assertFalse(result.diagnostics().stream().anyMatch(d -> d.code() == DiagnosticCode.DATA_CYCLE),
                "pas de faux DATA_CYCLE entre fantômes");
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code() == DiagnosticCode.UNKNOWN_NODE_TYPE));
        assertFalse(result.executable());
    }
}
