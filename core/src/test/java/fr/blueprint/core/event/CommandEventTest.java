package fr.blueprint.core.event;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.BlueprintManager;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.VarScope;
import fr.blueprint.core.graph.Variable;
import fr.blueprint.core.graph.Vec2d;
import fr.blueprint.core.registry.PluginLoader;
import fr.blueprint.core.vm.BlueprintScheduler;
import fr.blueprint.core.vm.ExecutionEnvironment;
import fr.blueprint.core.vm.VarStore;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story 7.7 : un blueprint déclare une commande par le littéral « name » de son
 * nœud event/command ; /bpc la déclenche via {@code launchCommand}, la
 * désactivation la retire des noms vivants, et l'argument texte traverse la charge
 * utile jusqu'au graphe.
 */
class CommandEventTest {

    private static final PluginLoader.LoadedRegistries LOADED =
            PluginLoader.load(List.of(), true);

    private BlueprintScheduler scheduler;
    private BlueprintManager manager;
    private BlueprintEventBridge bridge;
    private final VarStore vars = VarStore.inMemory();

    @BeforeEach
    void setup() {
        scheduler = new BlueprintScheduler(100, new BlueprintScheduler.Listener() {
            @Override
            public void disabled(Identifier blueprintId, int streakTicks) {
            }

            @Override
            public void faulted(Identifier blueprintId, UUID node, String message) {
                throw new AssertionError("faute inattendue : " + message);
            }
        });
        manager = new BlueprintManager();
        bridge = new BlueprintEventBridge(manager, LOADED.nodes(), scheduler,
                (bp, trigger) -> new ExecutionEnvironment(
                        typeId -> LOADED.nodes().get(typeId).orElse(null),
                        new fr.blueprint.api.node.BlueprintHandle() {
                            @Override
                            public Identifier id() {
                                return bp.id();
                            }

                            @Override
                            public boolean enabled() {
                                return bp.enabled();
                            }
                        },
                        trigger, vars, null, null,
                        LoggerFactory.getLogger("blueprint-test")));
    }

    private static void apply(Blueprint bp, EditOperation op) {
        assertTrue(op.apply(bp, LOADED.nodes()).applied(), op::toString);
    }

    /** hello → écrit l'argument reçu dans la variable « dernier ». */
    private Blueprint declare(String commandName) {
        Blueprint bp = manager.create(Identifier.fromNamespaceAndPath("test",
                commandName + "_bp")).orElseThrow();
        apply(bp, new EditOperation.AddVariable(new Variable("dernier", PinTypes.STRING,
                null, VarScope.GRAPH, false)));
        UUID event = UUID.randomUUID();
        apply(bp, new EditOperation.AddNode(event, StandardEvents.COMMAND.id(), new Vec2d(0, 0)));
        apply(bp, new EditOperation.SetLiteral(event, "name",
                LiteralValue.of(PinTypes.STRING, commandName)));
        UUID set = UUID.randomUUID();
        apply(bp, new EditOperation.AddNode(set, fr.blueprint.core.graph.VarNodes.SET,
                new Vec2d(200, 0)));
        apply(bp, new EditOperation.SetLiteral(set, "var",
                LiteralValue.of(PinTypes.STRING, "dernier")));
        apply(bp, new EditOperation.AddLink(new Link(event, "exec_out", set, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(event, "arg", set, "value")));
        bp.setEnabled(true);
        return bp;
    }

    /** Le propriétaire des variables du blueprint déclaré : GRAPH est clé par graphe. */
    private static fr.blueprint.core.vm.VarOwner ownerOf(String path) {
        return new fr.blueprint.core.vm.VarOwner(
                net.minecraft.resources.Identifier.fromNamespaceAndPath("test", path), null);
    }

    private TriggerContextImpl trigger(String name, String arg) {
        return new TriggerContextImpl(StandardEvents.COMMAND, Map.of("name", name, "arg", arg));
    }

    @Test
    void laCommandeDeclareeSeDeclencheAvecSonArgument() {
        declare("hello");
        declare("autre");
        assertEquals(java.util.Set.of("hello", "autre"), bridge.commandNames());

        // Seul le blueprint « hello » se lance — pas « autre ».
        assertEquals(1, bridge.launchCommand("hello", trigger("hello", "salut toi")));
        scheduler.tick(1_000);
        assertEquals("salut toi", vars.get(VarScope.GRAPH, ownerOf("hello_bp"), "dernier"));

        assertEquals(0, bridge.launchCommand("inconnue", trigger("inconnue", "")));
    }

    @Test
    void laDesactivationRetireLaCommande() {
        Blueprint bp = declare("ping");
        assertTrue(bridge.commandNames().contains("ping"));

        bp.setEnabled(false);
        assertTrue(bridge.commandNames().isEmpty());
        assertEquals(0, bridge.launchCommand("ping", trigger("ping", "x")));
    }
}
