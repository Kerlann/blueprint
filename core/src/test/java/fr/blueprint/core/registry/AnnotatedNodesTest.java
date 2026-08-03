package fr.blueprint.core.registry;

import fr.blueprint.api.annotation.AnnotatedNodes;
import fr.blueprint.api.annotation.BlueprintNode;
import fr.blueprint.api.annotation.In;
import fr.blueprint.api.annotation.Out;
import fr.blueprint.api.node.NodeContext;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.node.Permission;
import fr.blueprint.api.pin.PinKind;
import fr.blueprint.api.pin.PinType;
import fr.blueprint.api.pin.PinTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Dérivation de nœuds depuis des méthodes annotées (story 8.1). */
class AnnotatedNodesTest {

    /** Un mod tiers plausible : quelques méthodes statiques, rien d'autre. */
    public static final class ManaNodes {

        static final StringBuilder EFFECTS = new StringBuilder();

        @BlueprintNode(value = "mymod:mana/drain", category = "player",
                permission = Permission.GAMEPLAY, fuelCost = 3)
        public static void drain(@In("cible") String target, @In(def = "10") int amount) {
            EFFECTS.append(target).append('-').append(amount).append(';');
        }

        @BlueprintNode(value = "mymod:mana/of", pure = true)
        @Out("mana")
        public static int manaOf(String player) {
            return player.length() * 7;
        }

        @BlueprintNode("mymod:mana/log")
        public static void log(NodeContext ctx, @In("message") String message) {
            EFFECTS.append(ctx == null ? "?" : "ctx").append(':').append(message).append(';');
        }

        /** Sans annotation : ignorée. */
        public static void helper() {
        }
    }

    private static NodeType derived(String path) {
        for (NodeType type : AnnotatedNodes.derive(ManaNodes.class)) {
            if (type.id().getPath().equals(path)) {
                return type;
            }
        }
        throw new AssertionError("nœud « " + path + " » non dérivé");
    }

    @Test
    void aVoidMethodBecomesAnExecNodeWithItsParametersAsPins() {
        NodeType drain = derived("mana/drain");
        assertEquals("player", drain.category().id());
        assertEquals(Permission.GAMEPLAY, drain.permission());
        assertEquals(3, drain.fuelCost());
        assertFalse(drain.pure());

        assertEquals(List.of("exec_in", "cible", "amount"),
                drain.inputs().stream().map(NodeType.PinSpec::name).toList());
        assertEquals(PinKind.EXEC, drain.inputs().get(0).kind());
        assertEquals(PinTypes.STRING, drain.inputs().get(1).type());
        assertEquals(PinTypes.INT, drain.inputs().get(2).type());
        assertEquals(10, drain.inputs().get(2).defaultValue().value(),
                "la valeur par défaut de @In devient un littéral de pin");
        assertEquals(List.of("exec_out"),
                drain.outputs().stream().map(NodeType.PinSpec::name).toList());
    }

    @Test
    void aReturningMethodBecomesAPureNodeWithANamedOutput() {
        NodeType manaOf = derived("mana/of");
        assertTrue(manaOf.pure());
        assertEquals(List.of("player"),
                manaOf.inputs().stream().map(NodeType.PinSpec::name).toList());
        assertEquals(List.of("mana"),
                manaOf.outputs().stream().map(NodeType.PinSpec::name).toList());
        assertEquals(PinTypes.INT, manaOf.outputs().get(0).type());
    }

    /** Le nom du pin vient du paramètre — le projet compile avec {@code -parameters}. */
    @Test
    void parameterNamesBecomePinNames() {
        assertEquals("player", derived("mana/of").inputs().get(0).name());
    }

    @Test
    void theContextParameterIsInjectedNotExposedAsAPin() {
        NodeType log = derived("mana/log");
        assertEquals(List.of("exec_in", "message"),
                log.inputs().stream().map(NodeType.PinSpec::name).toList());
    }

    @Test
    void theBodyRunsWithThePinValues() throws Exception {
        ManaNodes.EFFECTS.setLength(0);
        NodeType drain = derived("mana/drain");
        drain.action().run(new FakeContext(Map.of("cible", "steve", "amount", 4)));
        assertEquals("steve-4;", ManaNodes.EFFECTS.toString());

        FakeContext ctx = new FakeContext(Map.of("player", "steve"));
        derived("mana/of").action().run(ctx);
        assertEquals(35, ctx.written.get("mana"), "la valeur de retour part sur le pin de sortie");
    }

    @Test
    void nodesAreDerivedInADeterministicOrder() {
        assertEquals(AnnotatedNodes.derive(ManaNodes.class).stream().map(NodeType::id).toList(),
                AnnotatedNodes.derive(ManaNodes.class).stream().map(NodeType::id).toList());
        assertEquals(3, AnnotatedNodes.derive(ManaNodes.class).size(),
                "les méthodes sans annotation sont ignorées");
    }

    /** AC2 : un mod peut n'avoir QUE des classes déclarées — ni plugin, ni processeur. */
    @Test
    void declaredHolderClassesAreScannedWithoutAnyPlugin() {
        var loaded = PluginLoader.load(List.of(), false,
                List.of(new PluginLoader.NodeHolders("mymod", List.of(ManaNodes.class.getName()))));
        assertEquals(3, loaded.nodes().all().size());
        assertEquals("mymod", loaded.nodes().providerOf(
                Identifier.fromNamespaceAndPath("mymod", "mana/of")).orElseThrow());
        assertTrue(loaded.failedMods().isEmpty());
    }

    /** Une classe introuvable ou fautive isole SON mod, sans casser les autres. */
    @Test
    void abrokenHolderIsolatesItsOwnModOnly() {
        var loaded = PluginLoader.load(List.of(), false, List.of(
                new PluginLoader.NodeHolders("badmod", List.of("com.example.Absente")),
                new PluginLoader.NodeHolders("mymod", List.of(ManaNodes.class.getName()))));
        assertEquals(List.of("badmod"), loaded.failedMods());
        assertEquals(3, loaded.nodes().all().size(), "les nœuds du mod sain sont là");

        var brokenDeclaration = PluginLoader.load(List.of(), false, List.of(
                new PluginLoader.NodeHolders("badmod", List.of(BadDefault.class.getName())),
                new PluginLoader.NodeHolders("mymod", List.of(ManaNodes.class.getName()))));
        assertEquals(List.of("badmod"), brokenDeclaration.failedMods());
        assertEquals(3, brokenDeclaration.nodes().all().size());
    }

    @Test
    void registeringGoesThroughTheNormalRegistry() {
        NodeRegistryImpl registry = new NodeRegistryImpl();
        registry.currentProvider("mymod");
        AnnotatedNodes.register(registry, ManaNodes.class);
        assertEquals(3, registry.all().size());
        assertTrue(registry.get(Identifier.fromNamespaceAndPath("mymod", "mana/of")).isPresent());
        assertEquals("mymod", registry.providerOf(
                Identifier.fromNamespaceAndPath("mymod", "mana/of")).orElseThrow());
    }

    // ---------------------------------------------------------- refus explicites

    /**
     * Chaque déclaration fautive a sa propre classe porteuse : la dérivation lève à la
     * première méthode fautive, une classe fourre-tout n'en prouverait qu'une (AC3).
     */
    @Test
    void everyBrokenDeclarationIsRefusedWithANamedMessage() {
        assertMessage(BadId.class, "identifiant");
        assertMessage(NotPublicStatic.class, "publique et statique");
        assertMessage(PureWithoutReturn.class, "pur");
        assertMessage(UnknownType.class, "aucun type de pin");
        assertMessage(BadDefault.class, "illisible");
        assertMessage(UnsupportedDefault.class, "non prise en charge");
        assertMessage(OutOnVoid.class, "@Out");
    }

    private static void assertMessage(Class<?> holder, String expected) {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> AnnotatedNodes.derive(holder), holder.getSimpleName() + " devrait être refusée");
        assertTrue(error.getMessage().contains(expected),
                "message attendu autour de « " + expected + " » : " + error.getMessage());
        assertTrue(error.getMessage().contains(holder.getSimpleName()),
                "le message doit nommer la classe et la méthode : " + error.getMessage());
    }

    public static final class BadId {
        @BlueprintNode("pas un identifiant")
        public static void badId() {
        }
    }

    public static final class NotPublicStatic {
        @BlueprintNode("mymod:not/static")
        static void notStatic() {
        }
    }

    public static final class PureWithoutReturn {
        @BlueprintNode(value = "mymod:no/return", pure = true)
        public static void pureWithoutReturn() {
        }
    }

    public static final class UnknownType {
        @BlueprintNode("mymod:unknown/type")
        public static void unknownType(java.util.regex.Pattern pattern) {
        }
    }

    public static final class BadDefault {
        @BlueprintNode("mymod:bad/default")
        public static void badDefault(@In(def = "beaucoup") int amount) {
        }
    }

    public static final class UnsupportedDefault {
        @BlueprintNode("mymod:unsupported/default")
        public static void unsupportedDefault(@In(def = "0 0 0") net.minecraft.core.BlockPos pos) {
        }
    }

    public static final class OutOnVoid {
        @BlueprintNode("mymod:out/on_void")
        @Out("rien")
        public static void outOnVoid() {
        }
    }

    /** Un type de pin d'un mod tiers se déclare par la table {@code extra}. */
    @Test
    void aModsOwnPinTypeIsFoundThroughTheExtraTable() {
        PinType mana = PinType.builder(Identifier.fromNamespaceAndPath("mymod", "mana"))
                .javaType(ManaPool.class).noLiteral().build();
        NodeType type = AnnotatedNodes.derive(CustomTyped.class,
                Map.of(ManaPool.class, mana)).get(0);
        assertEquals(mana, type.inputs().get(1).type());
    }

    public record ManaPool(int points) {
    }

    public static final class CustomTyped {
        @BlueprintNode("mymod:mana/show")
        public static void show(@In("pool") ManaPool pool) {
        }
    }

    // ------------------------------------------------------------------ contexte

    /** Contexte minimal : seules les lectures d'entrées et l'écriture de sortie servent ici. */
    private static final class FakeContext implements NodeContext {
        private final Map<String, Object> values;
        private final java.util.Map<String, Object> written = new java.util.HashMap<>();

        FakeContext(Map<String, Object> values) {
            this.values = values;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T in(String pin) {
            if (!values.containsKey(pin)) {
                throw new IllegalArgumentException("pin inconnu : " + pin);
            }
            return (T) values.get(pin);
        }

        @Override
        public void out(String pin, Object value) {
            written.put(pin, value);
        }

        @Override
        public void exec(String pin) {
        }

        @Override
        public void suspend(int ticks) {
        }

        @Override
        public void fail(net.minecraft.network.chat.Component reason) {
        }

        @Override
        public net.minecraft.server.MinecraftServer server() {
            return null;
        }

        @Override
        public net.minecraft.server.level.ServerLevel level() {
            return null;
        }

        @Override
        public fr.blueprint.api.node.BlueprintHandle blueprint() {
            return null;
        }

        @Override
        public fr.blueprint.api.event.TriggerContext trigger() {
            return null;
        }

        @Override
        public org.slf4j.Logger logger() {
            return org.slf4j.LoggerFactory.getLogger("test");
        }
    }
}
