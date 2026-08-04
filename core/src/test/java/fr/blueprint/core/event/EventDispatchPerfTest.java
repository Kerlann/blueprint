package fr.blueprint.core.event;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.BlueprintManager;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.GraphLimits;
import fr.blueprint.core.graph.Vec2d;
import fr.blueprint.core.registry.PluginLoader;
import fr.blueprint.core.vm.BlueprintScheduler;
import fr.blueprint.core.vm.ExecutionEnvironment;
import fr.blueprint.core.vm.VarStore;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le coût d'une émission <b>filtrée</b> — signal, commande, touche, clic.
 *
 * <p>Ces quatre-là ne visent pas un nœud précis : elles doivent trouver, parmi les graphes
 * actifs du serveur, ceux qui portent le bon littéral. La question est donc « combien de
 * nœuds faut-il regarder pour émettre un signal auquel personne n'écoute ? », et la bonne
 * réponse est <b>aucun</b>.
 *
 * <p>Ce n'était pas le cas. Le pont tient un index des points d'entrée par événement,
 * construit pour cette raison exacte (QA BRIDGE-001) et reconstruit seulement quand un
 * blueprint change de révision — et les chemins filtrés l'ignoraient, parcourant chaque
 * nœud de chaque graphe à chaque émission.
 *
 * <h2>Pourquoi ce test mesure une PENTE et non une durée</h2>
 * <p>Un budget en millisecondes mesure la machine qui fait tourner la suite autant que le
 * code : {@code CompilerPerfTest} a rougi une fois pour cette raison, et un test qui
 * rougit sans qu'aucun code n'ait changé apprend à relancer plutôt qu'à chercher.
 *
 * <p>Ce qui est vérifié ici est donc la propriété qu'on a réellement changée :
 * <b>quadrupler le nombre de nœuds qui n'écoutent pas ne doit pas quadrupler le coût</b>.
 * C'est vrai sur n'importe quelle machine, à n'importe quelle charge, et c'est exactement
 * ce qui casserait si quelqu'un remettait un parcours par nœud.
 */
class EventDispatchPerfTest {

    private static final Logger LOGGER = LoggerFactory.getLogger("blueprint-test");

    private static final int BLUEPRINTS = 20;
    private static final int SMALL = 150;
    /** Quatre fois plus de nœuds — la seule variable qui change entre les deux mesures. */
    private static final int LARGE = SMALL * 4;
    /** Émissions mesurées : le budget d'un tick, c'est-à-dire le pire cas réel. */
    private static final int EMISSIONS = BlueprintEventBridge.MAX_SIGNALS_PER_TICK;

    private static final PluginLoader.LoadedRegistries LOADED =
            PluginLoader.load(List.of(), true);

    /** Un pont sur un serveur peuplé de {@code nodesEach} nœuds par graphe. */
    private record Fixture(BlueprintManager manager, BlueprintEventBridge bridge) {
    }

    private static Fixture build(int nodesEach) {
        var scheduler = new BlueprintScheduler(1_000_000, new BlueprintScheduler.Listener() {
            @Override
            public void disabled(Identifier blueprintId, int streakTicks) {
            }

            @Override
            public void faulted(Identifier blueprintId, UUID node, String message) {
                throw new AssertionError(message);
            }
        });
        BlueprintManager manager = new BlueprintManager();
        VarStore vars = VarStore.inMemory();
        var bridge = new BlueprintEventBridge(manager, LOADED.nodes(), scheduler,
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
                        trigger, vars, null, null, LOGGER));

        GraphLimits limits = new GraphLimits(nodesEach + 10);
        Identifier add = Identifier.fromNamespaceAndPath("blueprint", "math/add");
        for (int b = 0; b < BLUEPRINTS; b++) {
            Blueprint bp = manager.create(Identifier.fromNamespaceAndPath("test",
                    "graphe_" + nodesEach + "_" + b)).orElseThrow();
            for (int n = 0; n < nodesEach; n++) {
                UUID uuid = UUID.nameUUIDFromBytes(("n" + nodesEach + "_" + b + "_" + n)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                assertTrue(new EditOperation.AddNode(uuid, add, new Vec2d(n * 10, b * 10))
                        .apply(bp, LOADED.nodes(), limits).applied());
            }
            bp.setEnabled(true);
        }
        // Un unique écouteur, dans le dernier graphe : il doit rester trouvable.
        Blueprint last = manager.get(Identifier.fromNamespaceAndPath("test",
                "graphe_" + nodesEach + "_" + (BLUEPRINTS - 1))).orElseThrow();
        UUID listener = UUID.randomUUID();
        assertTrue(new EditOperation.AddNode(listener, StandardEvents.SIGNAL.id(),
                new Vec2d(0, 0)).apply(last, LOADED.nodes(), limits).applied());
        assertTrue(new EditOperation.SetLiteral(listener, "name",
                LiteralValue.of(PinTypes.STRING, "ouvrir"))
                .apply(last, LOADED.nodes()).applied());
        return new Fixture(manager, bridge);
    }

    private static TriggerContextImpl trigger() {
        return new TriggerContextImpl(StandardEvents.SIGNAL, Map.of("payload", ""));
    }

    /** Séries mesurées, alternées entre les deux tailles. */
    private static final int ROUNDS = 40;
    /** Ticks par série : {@link #EMISSIONS} émissions chacun. Assez pour sortir du bruit. */
    private static final int TICKS_PER_ROUND = 50;

    /** Une série de {@link #TICKS_PER_ROUND} ticks pleins d'émissions vers personne, en ns. */
    private static long round(Fixture fixture) {
        long begin = System.nanoTime();
        for (int tick = 0; tick < TICKS_PER_ROUND; tick++) {
            for (int i = 0; i < EMISSIONS; i++) {
                assertEquals(0, fixture.bridge().launchSignal("inconnu", trigger()));
            }
            fixture.bridge().endTick();
        }
        return System.nanoTime() - begin;
    }

    private static void warmUp(Fixture fixture) {
        for (int i = 0; i < 5; i++) {
            round(fixture);
        }
    }

    /**
     * <b>Le test qui compte.</b> Quadrupler les nœuds qui n'écoutent pas ne doit pas
     * quadrupler le coût d'une émission.
     *
     * <p>Sans l'index, le rapport valait celui des tailles — quatre. Avec lui, le coût ne
     * dépend plus que du nombre de graphes et du nombre d'écouteurs, tous deux inchangés
     * entre les deux mesures ; le rapport tombe donc vers un.
     *
     * <p>La borne est fixée à <b>deux</b>, à mi-chemin : assez lâche pour absorber le
     * bruit d'une machine chargée, assez serrée pour qu'un retour au parcours par nœud la
     * franchisse sans ambiguïté.
     *
     * <h2>La marge, mesurée</h2>
     * <p>Les standards de code (§7.1) demandent qu'un banc ait été <b>vu échouer</b> et que
     * sa marge ait été <b>mesurée</b>. Les deux l'ont été, en contournant l'index dans
     * {@code entriesFor} :
     *
     * <ul>
     *   <li>index en place : rapport <b>0,95 à 1,01</b> sur six exécutions ;</li>
     *   <li>index contourné : rapport <b>4,30</b> — soit le rapport des tailles, comme
     *       attendu d'un parcours par nœud.</li>
     * </ul>
     *
     * <p>Le seuil de 2,0 laisse donc un facteur deux de chaque côté.
     *
     * <p>La première version de ce banc mesurait les deux tailles <b>l'une après l'autre</b>
     * et n'observait qu'un tick. Elle rendait 0,6 — le grand paraissait plus rapide que le
     * petit, ce qui aurait dû alerter : le second bénéficiait du JIT chauffé par le premier.
     * Cinquante microsecondes de mesure suffisaient à ce qu'un ramasse-miettes tombant dans
     * l'une des deux fenêtres la fasse bondir à 3,2 et rougir la CI sans qu'aucun code n'ait
     * changé. Les séries sont désormais <b>alternées</b> et chacune dure cinquante ticks.
     */
    @Test
    void leCoutNeSuitPasLeNombreDeNoeudsQuiNEcoutentPas() {
        Fixture small = build(SMALL);
        Fixture large = build(LARGE);
        warmUp(small);
        warmUp(large);

        // Séries ALTERNÉES, et non l'une après l'autre. Mesurer petit puis grand donnait
        // au second le bénéfice du JIT chauffé par le premier : le rapport tombait à 0,6
        // — le GRAND paraissait plus rapide que le petit, ce qui aurait dû alerter — et
        // un ramasse-miettes tombant dans l'une des deux fenêtres suffisait à le faire
        // bondir à 3,2, au-dessus du seuil, sans qu'aucun code n'ait changé.
        long bestSmall = Long.MAX_VALUE;
        long bestLarge = Long.MAX_VALUE;
        for (int i = 0; i < ROUNDS; i++) {
            bestSmall = Math.min(bestSmall, round(small));
            bestLarge = Math.min(bestLarge, round(large));
        }

        double ratio = (double) bestLarge / Math.max(1, bestSmall);
        LOGGER.info("{} ticks de {} émissions filtrées : {} µs à {} nœuds, {} µs à {} nœuds"
                        + " — rapport {}",
                TICKS_PER_ROUND, EMISSIONS, bestSmall / 1000, BLUEPRINTS * SMALL,
                bestLarge / 1000, BLUEPRINTS * LARGE,
                String.format(java.util.Locale.ROOT, "%.2f", ratio));

        assertTrue(ratio < 2.0, String.format(java.util.Locale.ROOT,
                "quadrupler les nœuds a multiplié le coût par %.2f — le parcours par nœud "
                        + "est probablement revenu (index ignoré ?)", ratio));
    }

    /** Et l'écouteur unique, perdu dans douze mille nœuds, est toujours trouvé. */
    @Test
    void leSeulEcouteurResteTrouve() {
        var fixture = build(LARGE);
        assertEquals(1, fixture.bridge().launchSignal("ouvrir", trigger()));
        assertEquals(1, fixture.bridge().signalListeners("ouvrir"));
        assertEquals(0, fixture.bridge().launchSignal("fermer", trigger()));
    }

    /**
     * Un blueprint <b>désactivé</b> n'écoute plus, index ou pas.
     *
     * <p>La règle vivait dans chacun des cinq chemins filtrés ; elle vit maintenant dans
     * un seul endroit. Une règle déplacée est une règle à re-vérifier, et celle-ci décide
     * si couper un blueprint le coupe vraiment.
     */
    @Test
    void unBlueprintDesactiveNEcoutePlusMalgreLIndex() {
        var fixture = build(SMALL);
        assertEquals(1, fixture.bridge().launchSignal("ouvrir", trigger()));

        fixture.manager().all().forEach(bp -> bp.setEnabled(false));
        assertEquals(0, fixture.bridge().launchSignal("ouvrir", trigger()));
        assertEquals(0, fixture.bridge().signalListeners("ouvrir"));
    }
}
