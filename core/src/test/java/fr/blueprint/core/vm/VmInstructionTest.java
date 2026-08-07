package fr.blueprint.core.vm;

import fr.blueprint.api.event.TriggerContext;
import fr.blueprint.api.node.BlueprintHandle;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.compile.ir.Instruction;
import fr.blueprint.core.compile.ir.Ir;
import fr.blueprint.core.graph.VarScope;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** VM sur IR manuelle (story 3.3) : bornes, statuts, fautes — sans compilateur. */
class VmInstructionTest {

    /**
     * Le propriétaire des variables de ce test.
     *
     * <p>Depuis que la portée PLAYER est réellement clé par joueur, un accès sans
     * propriétaire faute. Ces tests n’exercent qu’un blueprint et aucun joueur : leur
     * propriétaire nomme donc le graphe et laisse le joueur nul, ce qui est exactement
     * ce qu’est une exécution déclenchée par le tick serveur.
     */
    private static final fr.blueprint.core.vm.VarOwner OWNER =
            new fr.blueprint.core.vm.VarOwner(net.minecraft.resources.Identifier.fromNamespaceAndPath("test", "vm"), null);

    private static final BlueprintHandle HANDLE = new BlueprintHandle() {
        @Override
        public Identifier id() {
            return Identifier.fromNamespaceAndPath("test", "vm");
        }

        @Override
        public boolean enabled() {
            return true;
        }
    };
    private static final TriggerContext TRIGGER = new TriggerContext() {
        @Override
        public Identifier eventId() {
            return Identifier.fromNamespaceAndPath("test", "manual");
        }

        @Override
        public Object output(String name) {
            return null;
        }
    };

    private static ExecutionEnvironment env(Function<Identifier, NodeType> resolver) {
        return new ExecutionEnvironment(resolver, HANDLE, TRIGGER, VarStore.inMemory(),
                null, null, LoggerFactory.getLogger("blueprint-test"));
    }

    private static Ir ir(int slots, Instruction... instructions) {
        return new Ir(Identifier.fromNamespaceAndPath("test", "ir"), 0, null,
                List.of(instructions), slots);
    }

    @Test
    void constStoreLoadRoundTripThroughVariables() {
        var env = env(id -> null);
        Ir ir = ir(2,
                new Instruction.Const(0, LiteralValue.of(PinTypes.INT, 42), null),
                new Instruction.StoreVar(VarScope.WORLD, "x", 0, null),
                new Instruction.LoadVar(VarScope.WORLD, "x", 1, null),
                new Instruction.Return(null));
        var state = ExecutionState.fresh(ir);
        assertInstanceOf(ExecResult.Done.class, BlueprintVm.run(ir, state, env, 100));
        assertEquals(42, env.vars().get(VarScope.WORLD, OWNER, "x"));
    }

    @Test
    void localScopeLivesInTheExecutionNotTheStore() {
        var env = env(id -> null);
        Ir ir = ir(1,
                new Instruction.Const(0, LiteralValue.of(PinTypes.STRING, "privé"), null),
                new Instruction.StoreVar(VarScope.LOCAL, "tmp", 0, null),
                new Instruction.Return(null));
        BlueprintVm.run(ir, ExecutionState.fresh(ir), env, 100);
        assertNull(env.vars().get(VarScope.LOCAL, OWNER, "tmp"), "LOCAL ne touche jamais le VarStore");
    }

    @Test
    void jmpIfTakesBothPaths() {
        var env = env(id -> null);
        // slot0 = condition ; vrai → continue (store "then") ; faux → saute au store "else".
        Function<Boolean, Object> run = condition -> {
            Ir ir = ir(2,
                    new Instruction.Const(0, LiteralValue.of(PinTypes.BOOL, condition), null),
                    new Instruction.JmpIf(0, 4, null),
                    new Instruction.Const(1, LiteralValue.of(PinTypes.STRING, "then"), null),
                    new Instruction.Jmp(5, null),
                    new Instruction.Const(1, LiteralValue.of(PinTypes.STRING, "else"), null),
                    new Instruction.StoreVar(VarScope.WORLD, "chemin", 1, null),
                    new Instruction.Return(null));
            BlueprintVm.run(ir, ExecutionState.fresh(ir), env, 100);
            return env.vars().get(VarScope.WORLD, OWNER, "chemin");
        };
        assertEquals("then", run.apply(true));
        assertEquals("else", run.apply(false));
    }

    @Test
    void infiniteLoopExhaustsFuelAndResumes() {
        var env = env(id -> null);
        Ir ir = ir(0, new Instruction.Jmp(0, null));
        var state = ExecutionState.fresh(ir);
        // NFR4 : la boucle infinie ne fige pas — elle épuise son budget, état préservé.
        assertInstanceOf(ExecResult.OutOfFuel.class, BlueprintVm.run(ir, state, env, 50));
        assertInstanceOf(ExecResult.OutOfFuel.class, BlueprintVm.run(ir, state, env, 50));
    }

    @Test
    void yieldSuspendsAndResumesAtNextInstruction() {
        var env = env(id -> null);
        Ir ir = ir(1,
                new Instruction.Yield(40, null),
                new Instruction.Const(0, LiteralValue.of(PinTypes.INT, 7), null),
                new Instruction.StoreVar(VarScope.WORLD, "après", 0, null),
                new Instruction.Return(null));
        var state = ExecutionState.fresh(ir);
        ExecResult first = BlueprintVm.run(ir, state, env, 100);
        assertEquals(new ExecResult.Suspended(40), first);
        assertNull(env.vars().get(VarScope.WORLD, OWNER, "après"), "rien après le yield au premier run");
        assertInstanceOf(ExecResult.Done.class, BlueprintVm.run(ir, state, env, 100));
        assertEquals(7, env.vars().get(VarScope.WORLD, OWNER, "après"));
    }

    @Test
    void unresolvableNodeTypeFaultsWithSource() {
        var env = env(id -> null);
        UUID ghost = UUID.randomUUID();
        Ir ir = ir(0, new Instruction.Call(Identifier.fromNamespaceAndPath("gone", "node"),
                List.of(), List.of(), Map.of(), 1, false, ghost));
        ExecResult result = BlueprintVm.run(ir, ExecutionState.fresh(ir), env, 100);
        var fault = assertInstanceOf(ExecResult.Faulted.class, result);
        assertEquals(ghost, fault.node());
        assertTrue(fault.message().contains("gone:node"));
    }

    @Test
    void throwingActionAndFailBothFaultCleanly() {
        NodeType thrower = NodeType.builder(Identifier.fromNamespaceAndPath("t", "boom"))
                .exec().action(ctx -> {
                    throw new IllegalStateException("cassé exprès");
                }).build();
        NodeType failer = NodeType.builder(Identifier.fromNamespaceAndPath("t", "failer"))
                .exec().action(ctx -> ctx.fail(Component.literal("raison propre"))).build();
        Function<Identifier, NodeType> resolver = id -> switch (id.getPath()) {
            case "boom" -> thrower;
            case "failer" -> failer;
            default -> null;
        };
        UUID node = UUID.randomUUID();

        Ir boom = ir(0, new Instruction.Call(thrower.id(), List.of(), List.of(),
                Map.of(), 1, false, node));
        var faultBoom = assertInstanceOf(ExecResult.Faulted.class,
                BlueprintVm.run(boom, ExecutionState.fresh(boom), env(resolver), 100));
        assertTrue(faultBoom.message().contains("cassé exprès"));

        Ir fail = ir(0, new Instruction.Call(failer.id(), List.of(), List.of(),
                Map.of(), 1, false, node));
        var faultFail = assertInstanceOf(ExecResult.Faulted.class,
                BlueprintVm.run(fail, ExecutionState.fresh(fail), env(resolver), 100));
        assertEquals("raison propre", faultFail.message());
    }

    @Test
    void missingInputWithoutDefaultFaultsNamingThePin() {
        // CTX-001 tranché : ni valeur ni défaut → faute nommée, jamais un null silencieux.
        NodeType needy = NodeType.builder(Identifier.fromNamespaceAndPath("t", "needy"))
                .exec().in("target", PinTypes.ENTITY)
                .action(ctx -> ctx.in("target")).build();
        Ir ir = ir(0, new Instruction.Call(needy.id(), List.of(), List.of(),
                Map.of(), 1, false, UUID.randomUUID()));
        var fault = assertInstanceOf(ExecResult.Faulted.class,
                BlueprintVm.run(ir, ExecutionState.fresh(ir), env(id -> needy), 100));
        assertTrue(fault.message().contains("target"), fault.message());
    }
}
