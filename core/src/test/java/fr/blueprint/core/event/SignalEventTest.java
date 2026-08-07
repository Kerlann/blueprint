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
 * Signaux (batch 1). L'événement {@code signal} existait depuis la story 7.6 et
 * <b>rien au monde ne le déclenchait</b> : un point d'entrée mort, qu'on pouvait
 * poser et câbler sans qu'il ne s'exécute jamais. C'est la primitive « un blueprint
 * en appelle un autre ».
 */
class SignalEventTest {

    /** GRAPH est clé par graphe : le propriétaire nomme le blueprint écouteur. */
    private static fr.blueprint.core.vm.VarOwner ownerOf(String path) {
        return new fr.blueprint.core.vm.VarOwner(
                net.minecraft.resources.Identifier.fromNamespaceAndPath("test", path), null);
    }

    private static final PluginLoader.LoadedRegistries LOADED =
            PluginLoader.load(List.of(), true);

    private BlueprintScheduler scheduler;
    private BlueprintManager manager;
    private BlueprintEventBridge bridge;
    private final VarStore vars = VarStore.inMemory();

    @BeforeEach
    void setup() {
        scheduler = new BlueprintScheduler(1000, new BlueprintScheduler.Listener() {
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

    /** Écoute {@code signalName} et recopie la charge utile dans la variable « recu ». */
    private Blueprint listener(String id, String signalName) {
        Blueprint bp = manager.create(Identifier.fromNamespaceAndPath("test", id)).orElseThrow();
        apply(bp, new EditOperation.AddVariable(
                new Variable("recu", PinTypes.STRING, null, VarScope.GRAPH, false)));
        UUID event = UUID.randomUUID();
        apply(bp, new EditOperation.AddNode(event, StandardEvents.SIGNAL.id(), new Vec2d(0, 0)));
        apply(bp, new EditOperation.SetLiteral(event, "name",
                LiteralValue.of(PinTypes.STRING, signalName)));
        UUID set = UUID.randomUUID();
        apply(bp, new EditOperation.AddNode(set, fr.blueprint.core.graph.VarNodes.SET,
                new Vec2d(200, 0)));
        apply(bp, new EditOperation.SetLiteral(set, "var",
                LiteralValue.of(PinTypes.STRING, "recu")));
        apply(bp, new EditOperation.AddLink(new Link(event, "exec_out", set, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(event, "payload", set, "value")));
        bp.setEnabled(true);
        return bp;
    }

    private TriggerContextImpl trigger(String payload) {
        return new TriggerContextImpl(StandardEvents.SIGNAL, Map.of("payload", payload));
    }

    @Test
    void unSignalNommeAtteintSonEcouteurEtLuiPasseSaCharge() {
        listener("recepteur", "ouvrir_porte");

        assertEquals(1, bridge.launchSignal("ouvrir_porte", trigger("nord")));
        scheduler.tick(1000);
        assertEquals("nord", vars.get(VarScope.GRAPH, ownerOf("recepteur"), "recu"));
    }

    /**
     * Le nom filtre, comme pour les commandes : sans cela, tout signal réveillerait
     * tous les nœuds signal et l'auteur devrait trier à la main à chaque fois.
     */
    @Test
    void unSignalNAtteintQueLesNoeudsQuiPortentSonNom() {
        listener("a", "alpha");
        listener("b", "beta");

        assertEquals(1, bridge.launchSignal("alpha", trigger("x")));
        assertEquals(0, bridge.launchSignal("gamma", trigger("x")),
                "aucun écouteur : rien ne part");
    }

    /** Plusieurs blueprints peuvent écouter le même signal — c'est le but. */
    @Test
    void plusieursEcouteursDuMemeSignalPartentTous() {
        listener("un", "tic");
        listener("deux", "tic");
        assertEquals(2, bridge.launchSignal("tic", trigger("")));
        assertEquals(2, bridge.signalListeners("tic"));
    }

    @Test
    void unBlueprintDesactiveNEcoutePlus() {
        Blueprint bp = listener("dormeur", "reveil");
        assertEquals(1, bridge.signalListeners("reveil"));

        bp.setEnabled(false);
        assertEquals(0, bridge.signalListeners("reveil"));
        assertEquals(0, bridge.launchSignal("reveil", trigger("")));
    }

    /**
     * <b>Le test qui compte.</b> Un signal peut en émettre un autre. Sans borne, un
     * blueprint qui s'auto-signale remplit la file d'exécution sans jamais la vider :
     * le carburant se partage entre exécutions, donc chacune avance moins vite à
     * mesure qu'il y en a plus, et la mémoire monte jusqu'au crash du serveur.
     */
    @Test
    void leBudgetDeSignauxParTickCoupeLaRecursion() {
        listener("boucle", "encore");

        int accepted = 0;
        for (int i = 0; i < BlueprintEventBridge.MAX_SIGNALS_PER_TICK * 3; i++) {
            if (bridge.launchSignal("encore", trigger("")) >= 0) {
                accepted++;
            }
        }
        assertEquals(BlueprintEventBridge.MAX_SIGNALS_PER_TICK, accepted,
                "au-delà du budget, l'émission est REFUSÉE, pas silencieusement acceptée");

        // Et le tick suivant repart à neuf : la borne protège, elle ne condamne pas.
        bridge.endTick();
        assertTrue(bridge.launchSignal("encore", trigger("")) >= 0);
    }

    /** Le refus se distingue de « personne n'écoute » : −1 contre 0. */
    @Test
    void leRefusDeBudgetSeDistingueDeLAbsenceDEcouteur() {
        assertEquals(0, bridge.launchSignal("personne", trigger("")),
                "aucun écouteur, mais l'émission a bien eu lieu");

        listener("plein", "sature");
        for (int i = 0; i < BlueprintEventBridge.MAX_SIGNALS_PER_TICK; i++) {
            bridge.launchSignal("sature", trigger(""));
        }
        assertEquals(-1, bridge.launchSignal("sature", trigger("")),
                "budget épuisé : un code distinct, que l'appelant doit rapporter");
    }

    /**
     * Le nœud d'événement signal n'est PAS synthétisé : il est enregistré à la main
     * avec son entrée « name », comme command. Si la synthèse le reprenait, le nœud
     * perdrait son filtre et tous les signaux réveilleraient tout le monde.
     */
    @Test
    void leNoeudSignalPorteBienSonEntreeDeFiltre() {
        var type = LOADED.nodes().get(StandardEvents.SIGNAL.id()).orElseThrow();
        assertTrue(type.entryPoint());
        assertTrue(type.inputs().stream().anyMatch(pin -> pin.name().equals("name")),
                "sans l'entrée « name », le filtrage par nom est impossible");
        assertTrue(type.outputs().stream().anyMatch(pin -> pin.name().equals("payload")));
    }
}
