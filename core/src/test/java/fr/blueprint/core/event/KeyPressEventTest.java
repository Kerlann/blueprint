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
 * Les touches de Blueprint (story 11.4).
 *
 * <p>Cinquième cas de la règle du littéral filtrant — après {@code command},
 * {@code signal}, {@code gui_clicked} et les éléments riches — et le premier où le
 * littéral est un <b>entier</b>. C'est là que se trouve tout le risque de la story : un
 * filtre qui ne filtre pas ferait exécuter les huit graphes à chaque pression.
 */
class KeyPressEventTest {

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

    /** Écoute l'emplacement demandé et recopie son numéro dans la variable « recu ». */
    private Blueprint listener(String id, Object slotLiteral) {
        Blueprint bp = manager.create(Identifier.fromNamespaceAndPath("test", id)).orElseThrow();
        apply(bp, new EditOperation.AddVariable(
                new Variable("recu", PinTypes.INT, null, VarScope.GRAPH, false)));
        UUID event = UUID.randomUUID();
        apply(bp, new EditOperation.AddNode(event, StandardEvents.KEY_PRESSED.id(),
                new Vec2d(0, 0)));
        apply(bp, new EditOperation.SetLiteral(event, "key",
                LiteralValue.of(PinTypes.INT, slotLiteral)));
        UUID set = UUID.randomUUID();
        apply(bp, new EditOperation.AddNode(set, fr.blueprint.core.graph.VarNodes.SET,
                new Vec2d(200, 0)));
        apply(bp, new EditOperation.SetLiteral(set, "var",
                LiteralValue.of(PinTypes.STRING, "recu")));
        apply(bp, new EditOperation.AddLink(new Link(event, "exec_out", set, "exec_in")));
        apply(bp, new EditOperation.AddLink(new Link(event, "key", set, "value")));
        bp.setEnabled(true);
        return bp;
    }

    private TriggerContextImpl trigger(int slot) {
        return new TriggerContextImpl(StandardEvents.KEY_PRESSED, Map.of("key", slot));
    }

    @Test
    void unePressionAtteintSonEcouteurEtLuiDonneSonNumero() {
        listener("raccourci", 3);

        assertEquals(1, bridge.launchKeyPress(3, trigger(3)));
        scheduler.tick(1000);
        assertEquals(3, vars.get(VarScope.GRAPH, ownerOf("raccourci"), "recu"));
    }

    /**
     * <b>Le test qui compte.</b> Sans le filtre, chaque pression réveillerait les huit
     * écouteurs, et l'auteur devrait comparer le numéro à la main dans chaque graphe.
     */
    @Test
    void unePressionNAtteintQueLesNoeudsQuiPortentSonNumero() {
        listener("un", 1);
        listener("deux", 2);
        listener("trois", 3);

        assertEquals(1, bridge.launchKeyPress(2, trigger(2)));
        assertEquals(0, bridge.launchKeyPress(7, trigger(7)),
                "aucun écouteur sur cet emplacement : rien ne part");
    }

    /**
     * <b>Le test qui compte.</b> Un nœud posé et jamais touché doit écouter
     * l'emplacement 1, sa valeur par défaut.
     *
     * <p>Sinon il n'écouterait <b>rien</b> : l'auteur poserait le nœud, le câblerait,
     * presserait sa touche, et rien ne se produirait — sans erreur, sans diagnostic, sans
     * rien à corriger de visible. C'est exactement la forme du point d'entrée mort que ce
     * projet a déjà payée avec {@code signal}.
     */
    @Test
    void unNoeudPoseSansRienToucherEcouteLEmplacementUn() {
        Blueprint bp = manager.create(Identifier.fromNamespaceAndPath("test", "brut"))
                .orElseThrow();
        UUID event = UUID.randomUUID();
        apply(bp, new EditOperation.AddNode(event, StandardEvents.KEY_PRESSED.id(),
                new Vec2d(0, 0)));
        bp.setEnabled(true);

        assertEquals(1, bridge.launchKeyPress(1, trigger(1)),
                "un nœud jamais édité doit écouter son emplacement par défaut, "
                        + "sinon il est muet sans que rien ne le dise");
        assertEquals(0, bridge.launchKeyPress(2, trigger(2)));
    }

    /** Deux blueprints peuvent écouter la même touche — un HUD et un menu, par exemple. */
    @Test
    void plusieursBlueprintsPeuventEcouterLaMemeTouche() {
        listener("hud", 1);
        listener("menu", 1);
        assertEquals(2, bridge.launchKeyPress(1, trigger(1)));
    }

    @Test
    void unBlueprintDesactiveNEcoutePlus() {
        Blueprint bp = listener("dormeur", 6);
        assertEquals(1, bridge.launchKeyPress(6, trigger(6)));

        bp.setEnabled(false);
        assertEquals(0, bridge.launchKeyPress(6, trigger(6)));
    }

    /**
     * Le nœud n'est PAS synthétisé : il porte son entrée de filtre, comme command et
     * signal. Si la synthèse le reprenait, il perdrait ce filtre en silence.
     */
    @Test
    void leNoeudPorteBienSonEntreeDeFiltre() {
        var type = LOADED.nodes().get(StandardEvents.KEY_PRESSED.id()).orElseThrow();
        assertTrue(type.entryPoint());
        assertTrue(type.inputs().stream().anyMatch(pin -> pin.name().equals("key")),
                "sans l'entrée « key », le filtrage par emplacement est impossible");
        assertTrue(type.outputs().stream().anyMatch(pin -> pin.name().equals("player")));
        assertTrue(type.outputs().stream().anyMatch(pin -> pin.name().equals("key")));
    }

    /**
     * Le nœud vit dans sa propre catégorie. « Événements du joueur » était à douze —
     * la borne au-delà de laquelle un repli de palette cesse de se lire d'un coup d'œil —
     * et une touche ne vient de toute façon pas du monde mais du clavier.
     */
    @Test
    void leNoeudEstRangeAvecLesCommandesDuJoueur() {
        var type = LOADED.nodes().get(StandardEvents.KEY_PRESSED.id()).orElseThrow();
        assertEquals(fr.blueprint.api.node.NodeCategories.EVENT_INPUT.id(),
                type.category().id());
    }
}
