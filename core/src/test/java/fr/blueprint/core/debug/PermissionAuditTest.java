package fr.blueprint.core.debug;

import fr.blueprint.api.event.TriggerContext;
import fr.blueprint.api.node.BlueprintHandle;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.node.Permission;
import fr.blueprint.core.compile.Compiler;
import fr.blueprint.core.compile.ir.Instruction;
import fr.blueprint.core.compile.ir.Ir;
import fr.blueprint.core.config.BlueprintConfig;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.BlueprintMeta;
import fr.blueprint.core.graph.DiagnosticCode;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.GraphValidator;
import fr.blueprint.core.graph.Vec2d;
import fr.blueprint.core.net.GraphGuard;
import fr.blueprint.core.registry.PluginLoader;
import fr.blueprint.core.vm.BlueprintVm;
import fr.blueprint.core.vm.ExecutionEnvironment;
import fr.blueprint.core.vm.ExecutionState;
import fr.blueprint.core.vm.VarStore;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Permissions, quotas configurables et audit des nœuds ADMIN (story 9.3, NFR15). */
class PermissionAuditTest {

    private static final Identifier BLUEPRINT = Identifier.fromNamespaceAndPath("test", "audit");
    private static final Identifier OP_NODE = Identifier.fromNamespaceAndPath("t", "run_command");
    private static final UUID NODE = UUID.nameUUIDFromBytes("admin".getBytes());

    /** Un nœud de niveau ADMIN, celui qu'il faut savoir tracer. */
    private static final PluginLoader.LoadedRegistries LOADED = PluginLoader.load(List.of(
            new PluginLoader.PluginEntry("t", registry -> registry.register(
                    NodeType.builder(OP_NODE).exec().permission(Permission.ADMIN)
                            .action(ctx -> {
                            }).build()))), true);

    private static ExecutionEnvironment env(String playerName) {
        return new ExecutionEnvironment(id -> LOADED.nodes().get(id).orElse(null),
                new BlueprintHandle() {
                    @Override
                    public Identifier id() {
                        return BLUEPRINT;
                    }

                    @Override
                    public boolean enabled() {
                        return true;
                    }
                },
                new TriggerContext() {
                    @Override
                    public Identifier eventId() {
                        return Identifier.fromNamespaceAndPath("blueprint", "player_chat");
                    }

                    @Override
                    public Object output(String name) {
                        if ("player".equals(name)) {
                            return playerName;
                        }
                        throw new IllegalArgumentException("sortie inconnue : " + name);
                    }
                },
                VarStore.inMemory(), null, null, LoggerFactory.getLogger("blueprint-test"));
    }

    private static Ir program() {
        return new Ir(BLUEPRINT, 0, NODE, List.of(
                new Instruction.Call(OP_NODE, List.of(), List.of(), Map.of(), 1, false, NODE),
                new Instruction.Return(null)), 0);
    }

    @AfterEach
    void clearAudit() {
        AdminAudit.clear();
        AdminAudit.enabled(true);
    }

    // ------------------------------------------------------------------ NFR15

    @Test
    void everyAdminNodeIsLoggedWithBlueprintNodeActorAndTime() {
        long before = System.currentTimeMillis();
        Ir ir = program();
        BlueprintVm.run(ir, ExecutionState.fresh(ir), env("Steve"), 100);

        List<AdminAudit.Entry> entries = AdminAudit.recent();
        assertEquals(1, entries.size());
        AdminAudit.Entry entry = entries.get(0);
        assertEquals(BLUEPRINT, entry.blueprint());
        assertEquals(NODE, entry.node());
        assertEquals(OP_NODE, entry.type());
        assertEquals("Steve", entry.actor(), "l'acteur vient de la charge utile du déclencheur");
        assertTrue(entry.epochMillis() >= before, "horodaté");
    }

    @Test
    void anEventWithoutPlayerFallsBackToTheEventItself() {
        AdminAudit.record(Permission.ADMIN, BLUEPRINT, NODE, OP_NODE,
                "blueprint:server_tick", 1L);
        assertEquals("blueprint:server_tick", AdminAudit.recent().get(0).actor());
    }

    @Test
    void ordinaryNodesAreNotLogged() {
        AdminAudit.record(Permission.SAFE, BLUEPRINT, NODE, OP_NODE, "Steve", 1L);
        AdminAudit.record(Permission.GAMEPLAY, BLUEPRINT, NODE, OP_NODE, "Steve", 1L);
        AdminAudit.record(Permission.WORLD, BLUEPRINT, NODE, OP_NODE, "Steve", 1L);
        assertTrue(AdminAudit.recent().isEmpty(), "seul ADMIN se journalise");
    }

    @Test
    void theAuditCanBeTurnedOffAndStaysBounded() {
        AdminAudit.enabled(false);
        AdminAudit.record(Permission.ADMIN, BLUEPRINT, NODE, OP_NODE, "Steve", 1L);
        assertTrue(AdminAudit.recent().isEmpty());

        AdminAudit.enabled(true);
        for (int i = 0; i < AdminAudit.MEMORY * 2; i++) {
            AdminAudit.record(Permission.ADMIN, BLUEPRINT, NODE, OP_NODE, "Steve", i);
        }
        assertEquals(AdminAudit.MEMORY, AdminAudit.recent().size());
    }

    // ------------------------------------------------- escalades refusées

    /** Le plafond du blueprint prime : un nœud ADMIN sous plafond GAMEPLAY ne tourne pas. */
    @Test
    void anAdminNodeUnderAGameplayCapIsRefused() {
        Blueprint bp = new Blueprint(BLUEPRINT, new BlueprintMeta("", "", "1.0.0",
                Permission.GAMEPLAY));
        new EditOperation.AddNode(NODE, OP_NODE, Vec2d.ZERO).apply(bp, LOADED.nodes());

        var validation = GraphValidator.validate(bp, LOADED.nodes());
        assertFalse(validation.executable());
        assertTrue(validation.errors().stream()
                        .anyMatch(d -> d.code() == DiagnosticCode.PERMISSION_EXCEEDED),
                "le diagnostic dit ce qui dépasse : " + validation.errors());
        assertFalse(Compiler.compile(bp, LOADED.nodes(), NODE).success(),
                "et la compilation refuse : rien ne s'exécute");
    }

    /** Un client ne s'accorde pas ADMIN en changeant le plafond de son graphe (SEC-001). */
    @Test
    void raisingTheCapNeedsAnOperator() {
        Blueprint admin = new Blueprint(BLUEPRINT,
                new BlueprintMeta("", "", "1.0.0", Permission.ADMIN));
        assertFalse(GraphGuard.capAllowed(admin, Permission.WORLD));
        assertTrue(GraphGuard.capAllowed(admin, Permission.ADMIN));
    }

    // ------------------------------------------------- quotas configurables

    @Test
    void quotasComeFromTheServerConfiguration() {
        BlueprintConfig config = new BlueprintConfig(2, 5_000, 50, 42, 64, 3, 7, false);
        assertEquals(42, config.graphLimits().maxNodes());
        assertEquals(42, config.netLimits().maxNodes());
        assertEquals(64 * 1024, config.netLimits().maxGraphBytes());
        assertEquals(3, config.netLimits().savesPerWindow());
        assertEquals(7, config.netLimits().requestsPerWindow());
        assertFalse(config.auditAdminNodes());
    }

    @Test
    void absurdQuotasAreClampedNotObeyed() {
        BlueprintConfig config = new BlueprintConfig(2, 10_000, 100, 0, 0, 0, 0, true);
        assertEquals(1, config.graphLimits().maxNodes(), "zéro nœud rendrait le mod inutilisable");
        assertTrue(config.netLimits().maxGraphBytes() >= 16 * 1024);
        assertTrue(config.netLimits().savesPerWindow() >= 1);
    }

    @Test
    void theNodeLimitIsEnforcedWithTheConfiguredValue() {
        Blueprint bp = new Blueprint(BLUEPRINT);
        var limits = new BlueprintConfig(2, 10_000, 100, 2, 256, 10, 60, true).graphLimits();
        for (int i = 0; i < 3; i++) {
            new EditOperation.AddNode(UUID.randomUUID(), OP_NODE, new Vec2d(i, 0))
                    .apply(bp, LOADED.nodes(), limits);
        }
        assertEquals(2, bp.nodes().size(), "la troisième addition est refusée par la limite");
    }
}
