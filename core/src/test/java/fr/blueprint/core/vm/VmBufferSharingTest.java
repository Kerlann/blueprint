package fr.blueprint.core.vm;

import fr.blueprint.api.BlueprintPlugin;
import fr.blueprint.api.event.TriggerContext;
import fr.blueprint.api.node.BlueprintHandle;
import fr.blueprint.api.node.NodeCategories;
import fr.blueprint.api.node.NodeContext;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.compile.Compiler;
import fr.blueprint.core.compile.ir.Ir;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.GraphLimits;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.Vec2d;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>La garde anti-fuite survit au partage des tampons.</b>
 *
 * <p>Depuis l'épic 16, les tables d'entrées et de sorties ne sont plus construites à chaque
 * appel de nœud : l'exécution les prête et les réemploie. Cela crée un risque qui n'existait
 * pas — un mod qui conserverait le contexte d'un nœud pourrait, au nœud suivant, lire des
 * tampons désormais remplis par <b>quelqu'un d'autre</b>.
 *
 * <p>{@link NodeContextImplTest} vérifie déjà qu'un contexte conservé est mort après son
 * appel. Il ne vérifiait pas ce cas-ci, parce qu'il n'existait pas : le contexte fuité et le
 * suivant ne partageaient rien. C'est exactement le genre de trou qu'une optimisation ouvre
 * en silence, et la raison pour laquelle le contexte, lui, <b>reste neuf à chaque appel</b>
 * — seuls les tampons sont mutualisés.
 */
class VmBufferSharingTest {

    /** Le contexte que le premier nœud conserve, comme le ferait un mod mal écrit. */
    private static final NodeContext[] LEAKED = new NodeContext[1];
    /** Ce que le second nœud a observé en tentant d'utiliser le contexte du premier. */
    private static final List<String> OBSERVED = new ArrayList<>();

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("fuite", path);
    }

    private static final BlueprintPlugin PLUGIN = registry -> {
        registry.register(NodeType.builder(id("start"))
                .category(NodeCategories.EVENT).execOut("exec_out")
                .action(ctx -> {
                }).build());

        // Le nœud fautif : il garde son contexte dans un champ. C'est précisément ce que
        // la story 2.3 (AC5) interdit, et ce que la garde doit rendre inoffensif.
        registry.register(NodeType.builder(id("keeper"))
                .exec().in("value", PinTypes.INT, 111).out("result", PinTypes.INT)
                .action(ctx -> {
                    LEAKED[0] = ctx;
                    ctx.out("result", ctx.<Integer>in("value"));
                })
                .build());

        // Le nœud suivant. Ses entrées remplissent LES MÊMES tampons ; si la garde
        // manquait, le contexte fuité les lirait comme s'ils étaient les siens.
        registry.register(NodeType.builder(id("second"))
                .exec().in("value", PinTypes.INT, 222).out("result", PinTypes.INT)
                .action(ctx -> {
                    try {
                        Object seen = LEAKED[0].in("value");
                        OBSERVED.add("LU:" + seen);
                    } catch (IllegalStateException expected) {
                        OBSERVED.add("REFUSE");
                    }
                    ctx.out("result", ctx.<Integer>in("value"));
                })
                .build());
    };

    private static final PluginLoader.LoadedRegistries LOADED =
            PluginLoader.load(List.of(new PluginLoader.PluginEntry("fuite", PLUGIN)));

    private static Ir chain() {
        Blueprint bp = new Blueprint(Identifier.fromNamespaceAndPath("fuite", "graph"));
        GraphLimits limits = new GraphLimits(16);
        UUID start = UUID.nameUUIDFromBytes("s".getBytes());
        UUID keeper = UUID.nameUUIDFromBytes("k".getBytes());
        UUID second = UUID.nameUUIDFromBytes("d".getBytes());
        assertTrue(new EditOperation.AddNode(start, id("start"), Vec2d.ZERO)
                .apply(bp, LOADED.nodes(), limits).applied());
        assertTrue(new EditOperation.AddNode(keeper, id("keeper"), new Vec2d(10, 0))
                .apply(bp, LOADED.nodes(), limits).applied());
        assertTrue(new EditOperation.AddNode(second, id("second"), new Vec2d(20, 0))
                .apply(bp, LOADED.nodes(), limits).applied());
        assertTrue(new EditOperation.AddLink(new Link(start, "exec_out", keeper, "exec_in"))
                .apply(bp, LOADED.nodes(), limits).applied());
        assertTrue(new EditOperation.AddLink(new Link(keeper, "exec_out", second, "exec_in"))
                .apply(bp, LOADED.nodes(), limits).applied());
        Compiler.CompileResult result = Compiler.compile(bp, LOADED.nodes(), start);
        assertTrue(result.success(), "le graphe du test doit compiler");
        return result.ir();
    }

    private static ExecutionEnvironment env() {
        return new ExecutionEnvironment(
                typeId -> LOADED.nodes().get(typeId).orElse(null),
                new BlueprintHandle() {
                    @Override
                    public Identifier id() {
                        return Identifier.fromNamespaceAndPath("fuite", "graph");
                    }

                    @Override
                    public boolean enabled() {
                        return true;
                    }
                },
                new TriggerContext() {
                    @Override
                    public Identifier eventId() {
                        return Identifier.fromNamespaceAndPath("fuite", "manual");
                    }

                    @Override
                    public Object output(String name) {
                        return null;
                    }
                },
                VarStore.inMemory(), null, null, LoggerFactory.getLogger("blueprint-test"));
    }

    /**
     * <b>Le test qui compte.</b> Le contexte du premier nœud, réutilisé pendant le second,
     * <b>lève</b> — il ne rend jamais la valeur du second.
     *
     * <p>Sans la garde, {@code LEAKED[0].in("value")} rendrait <b>222</b> : la valeur du
     * nœud en cours, lue dans un tampon partagé, présentée comme celle du nœud fuité. Aucune
     * exception, aucun symptôme — le pire résultat possible.
     */
    @Test
    void unContexteConserveNeVoitJamaisLesEntreesDuNoeudSuivant() {
        LEAKED[0] = null;
        OBSERVED.clear();

        Ir ir = chain();
        ExecutionState state = ExecutionState.fresh(ir);
        assertEquals(ExecResult.DONE, BlueprintVm.run(ir, state, env(), Integer.MAX_VALUE));

        assertEquals(List.of("REFUSE"), OBSERVED,
                "le contexte fuité a été LU pendant le nœud suivant — les tampons partagés"
                        + " sont visibles à travers la garde anti-fuite (AC5 rompue)");
    }

    /** Et il reste mort après l'exécution, comme avant l'épic 16. */
    @Test
    void unContexteConserveResteMortApresLExecution() {
        LEAKED[0] = null;
        OBSERVED.clear();

        Ir ir = chain();
        BlueprintVm.run(ir, ExecutionState.fresh(ir), env(), Integer.MAX_VALUE);

        assertInstanceOf(NodeContext.class, LEAKED[0]);
        assertThrows(IllegalStateException.class, () -> LEAKED[0].in("value"));
        assertThrows(IllegalStateException.class, () -> LEAKED[0].out("result", 1));
        assertThrows(IllegalStateException.class, LEAKED[0]::logger);
    }
}
