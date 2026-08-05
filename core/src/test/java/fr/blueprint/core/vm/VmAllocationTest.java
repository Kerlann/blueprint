package fr.blueprint.core.vm;

import fr.blueprint.api.BlueprintPlugin;
import fr.blueprint.api.event.TriggerContext;
import fr.blueprint.api.node.BlueprintHandle;
import fr.blueprint.api.node.NodeCategories;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Ce que coûte <b>un appel de nœud</b> en mémoire allouée.
 *
 * <p>Les standards de code (§5) demandent « aucune allocation dans {@code BlueprintVm#step}
 * hors des valeurs produites par les nœuds » et « réutiliser les tampons ». Rien ne le
 * vérifiait. Ce banc pose le chiffre, pour que la règle cesse d'être une intention.
 *
 * <h2>Pourquoi des octets et non des millisecondes</h2>
 *
 * <p>Les standards (§7.1) classent les formes de banc par ordre de préférence, et les trois
 * reposent sur une horloge : rapport de deux mesures, temps processeur du fil, temps mural.
 * Toutes mesurent la machine autant que le code — c'est pour cette raison que trois bancs de
 * ce projet ont rougi sans qu'aucune ligne n'ait changé.
 *
 * <p>Le nombre d'octets alloués, lui, <b>ne dépend d'aucune horloge</b>. Une machine chargée
 * alloue exactement autant qu'une machine au repos. C'est donc une quatrième forme, et là où
 * elle s'applique elle est plus forte que les trois autres : elle ne peut pas rougir pour une
 * raison étrangère au code, donc elle n'apprend jamais à relancer plutôt qu'à chercher.
 *
 * <h2>Ce dont elle dépend quand même — mesuré, pas supposé</h2>
 *
 * <p>Elle n'est pas pour autant absolue. Ce banc mesure <b>616</b> octets lancé seul et
 * <b>744</b> lancé dans la suite complète — chacun reproductible à l'octet près, d'une
 * exécution à l'autre. L'écart vient de ce que le compilateur JIT parvient à éliminer :
 * moins de sites d'appel pollués, plus d'objets scalarisés, donc moins d'octets réellement
 * alloués. Déclarer douze types de nœuds distincts pour rendre le site d'action
 * mégamorphique <b>n'a pas suffi</b> à faire converger les deux chiffres — la cause exacte
 * est ailleurs et n'a pas été isolée.
 *
 * <p>La différence avec un banc à horloge reste entière et décisive : cette variance est
 * <b>reproductible et bornée</b>, pas aléatoire. Elle ne dépend pas de la charge de la
 * machine, seulement du contexte d'exécution, et ce contexte est le même à chaque
 * construction. Le budget est donc calé sur le <b>pire des deux</b>, celui de la suite
 * complète — c'est ainsi que la construction lance ce test, et c'est le chiffre le plus
 * proche de la production, où quatre-vingts types de nœuds circulent par les mêmes sites.
 *
 * <h2>Pourquoi une différence et non une mesure directe</h2>
 *
 * <p>Une exécution alloue deux choses : un coût fixe par lancement ({@link ExecutionState},
 * ses slots, sa table de locales) et un coût par nœud traversé. Mesurer un seul graphe les
 * mélange, et le chiffre obtenu dépendrait de la longueur choisie.
 *
 * <p>On mesure donc <b>deux chaînes</b>, l'une deux fois plus longue que l'autre, et on prend
 * la différence : tout ce qui est fixe s'annule, il ne reste que le marginal — le coût d'un
 * appel de nœud, et rien d'autre. C'est la discipline du §7.1 (« les deux mesures ne doivent
 * rien partager ») appliquée à l'allocation plutôt qu'au temps.
 */
class VmAllocationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger("blueprint-test");

    /** Longueur de la chaîne courte ; la longue en fait le double. */
    private static final int SHORT_CHAIN = 100;
    private static final int LONG_CHAIN = SHORT_CHAIN * 2;
    /** Exécutions mesurées par série — assez pour noyer le bruit d'un éventuel arrondi TLAB. */
    private static final int RUNS = 200;

    /**
     * Plafond d'octets par appel de nœud.
     *
     * <h2>La marge, mesurée</h2>
     *
     * <p>L'implémentation alloue <b>288 octets par appel</b>. Elle en allouait <b>744</b>
     * avant l'épic 16 — cinq objets par nœud traversé : la table des entrées, sa copie
     * défensive, la table des sorties, le contexte lui-même, et l'{@code Optional} de la
     * résolution de type. En sont partis la copie défensive, l'{@code Optional} (résolu une
     * fois par exécution) et les deux tables (prêtées par l'exécution). Reste le contexte,
     * délibérément neuf à chaque appel : c'est lui qui porte la garde anti-fuite d'AC5, que
     * {@code VmBufferSharingTest} vérifie.
     *
     * <p>Relevé au même octet près d'une exécution à l'autre : c'est la propriété qui
     * justifiait de mesurer des octets plutôt que du temps.
     *
     * <p>Le budget est fixé à <b>312</b>, soit 24 octets au-dessus — la taille du plus petit
     * objet qu'on puisse ajouter. Toute allocation nouvelle dans la boucle chaude le franchit.
     *
     * <p>Les standards (§7.1) demandent « au moins un ordre de grandeur » de marge. Cette
     * règle-là vise les bancs à horloge, dont la mesure varie avec la charge de la machine ;
     * ici la mesure ne varie pas d'un octet. Et l'appliquer serait contre-productif :
     * <b>essayé, mesuré</b> — avec un budget à 700, l'ajout d'une copie défensive dans
     * {@code BlueprintVm.call} portait la mesure à 688 et le banc <b>passait quand même</b>.
     * Un banc qui laisse passer le défaut qu'il surveille ne prouve rien (§7.1) : c'est
     * précisément pourquoi la marge se mesure au lieu de se supposer.
     *
     * <h2>Vu rougir — et ce que la première tentative a appris</h2>
     *
     * <p>Défaut réintroduit : {@code new LinkedHashMap&lt;&gt;(inputs)} passé au contexte à la
     * place de {@code inputs}, c'est-à-dire exactement le genre de copie défensive qu'on
     * ajoute sans y penser. Mesure : <b>768 octets</b>, banc rouge. Retiré : 616, banc vert.
     *
     * <p>La <b>première</b> tentative de défaut n'avait rien donné, et c'est instructif : une
     * {@code ArrayList} locale ajoutée dans la même méthode laissait la mesure à 616. Elle ne
     * quitte pas la méthode, donc C2 la scalarise — elle n'est jamais allouée. C'est la raison
     * de fond pour laquelle ce banc a un sens : ce qu'il mesure, ce sont les objets qui
     * <b>s'échappent</b>, et {@link NodeContextImpl} s'échappe vers un appel virtuel
     * mégamorphique que le compilateur ne peut ni inliner ni confiner. Un banc d'allocation
     * qui ne surveillerait que des objets locaux ne mesurerait que le talent du JIT.
     *
     * <p><b>Si ce banc rougit sur une JVM différente sans qu'aucun code n'ait changé</b>, la
     * cause probable est la taille d'en-tête d'objet (oops compressés désactivés au-delà de
     * 32 Go de tas). Relever la valeur journalisée et rebaser cette constante est alors la
     * bonne réponse — mais vérifier d'abord qu'aucune allocation n'a été ajoutée.
     *
     * <p><b>Ce plafond a vocation à baisser.</b> C'est l'épic 16 (contexte de nœud réutilisé)
     * qui le fera, et le diff de cette constante sera son rapport de gain.
     */
    private static final long MAX_BYTES_PER_CALL = 312;

    /**
     * Nombre de types de nœuds distincts dans la chaîne mesurée.
     *
     * <h2>Pourquoi plusieurs types et non un seul</h2>
     *
     * <p>La première version de ce banc n'en déclarait qu'un. Elle mesurait <b>616</b>
     * octets lancée seule, et <b>744</b> lancée au sein de la suite complète — 20 % d'écart,
     * sans qu'une ligne de code n'ait changé. La cause n'est pas le bruit : avec un seul
     * type, {@code type.action().run(ctx)} est un site d'appel <b>monomorphe</b>, que C2
     * inline et dont il scalarise une partie des allocations. Le reste de la suite, en
     * faisant passer d'autres types par le même site, le rendait mégamorphique et rendait
     * ces allocations bien réelles.
     *
     * <p>Le chiffre honnête est donc 744, pas 616 : en production le mod déclare quatre-vingts
     * types, et ce site d'appel <b>est</b> mégamorphique. Un banc qui mesurait 616 mesurait
     * une condition qui n'existe nulle part.
     *
     * <p>Douze types suffisent : le seuil de C2 est à deux receveurs pour l'inlining bimorphe
     * et le site devient mégamorphique bien avant douze. Le banc crée ainsi lui-même la
     * condition qu'il doit mesurer, au lieu de dépendre de ce qui tourne à côté de lui.
     */
    private static final int NODE_TYPES = 12;

    /** Des nœuds d'action triviaux : ce qu'on mesure est la VM, pas le travail du nœud. */
    private static final BlueprintPlugin PLUGIN = registry -> {
        registry.register(NodeType.builder(id("start"))
                .category(NodeCategories.EVENT).execOut("exec_out")
                .action(ctx -> {
                }).build());
        // Une entrée et une sortie de données : le cas courant, celui qui fait travailler
        // les deux tables de pins de NodeContextImpl. Un nœud sans pin ne mesurerait rien.
        for (int i = 0; i < NODE_TYPES; i++) {
            final int marker = i;
            registry.register(NodeType.builder(id("step" + i))
                    .exec().in("value", PinTypes.INT, 1).out("result", PinTypes.INT)
                    // Corps distincts : des lambdas identiques pourraient partager un profil.
                    .action(ctx -> ctx.out("result", ctx.<Integer>in("value") + marker))
                    .build());
        }
    };

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("alloc", path);
    }

    private static final PluginLoader.LoadedRegistries LOADED =
            PluginLoader.load(List.of(new PluginLoader.PluginEntry("alloc", PLUGIN)));

    private static final BlueprintHandle HANDLE = new BlueprintHandle() {
        @Override
        public Identifier id() {
            return Identifier.fromNamespaceAndPath("alloc", "graph");
        }

        @Override
        public boolean enabled() {
            return true;
        }
    };

    private static final TriggerContext TRIGGER = new TriggerContext() {
        @Override
        public Identifier eventId() {
            return Identifier.fromNamespaceAndPath("alloc", "manual");
        }

        @Override
        public Object output(String name) {
            return null;
        }
    };

    /** Une chaîne exec de {@code length} nœuds, compilée. */
    private static Ir chain(int length) {
        Blueprint bp = new Blueprint(Identifier.fromNamespaceAndPath("alloc", "c" + length));
        GraphLimits limits = new GraphLimits(length + 10);
        UUID start = UUID.nameUUIDFromBytes(("start" + length).getBytes());
        assertTrue(new EditOperation.AddNode(start, id("start"), Vec2d.ZERO)
                .apply(bp, LOADED.nodes(), limits).applied());
        UUID previous = start;
        String previousPin = "exec_out";
        for (int i = 0; i < length; i++) {
            UUID uuid = UUID.nameUUIDFromBytes(("step" + length + "_" + i).getBytes());
            // Types alternés : le site d'appel de la VM doit être mégamorphique, comme en
            // production. Voir NODE_TYPES pour ce que coûtait de l'oublier.
            assertTrue(new EditOperation.AddNode(uuid, id("step" + (i % NODE_TYPES)),
                    new Vec2d(i * 10, 0))
                    .apply(bp, LOADED.nodes(), limits).applied());
            assertTrue(new EditOperation.AddLink(new Link(previous, previousPin, uuid, "exec_in"))
                    .apply(bp, LOADED.nodes(), limits).applied());
            previous = uuid;
            previousPin = "exec_out";
        }
        Compiler.CompileResult result = Compiler.compile(bp, LOADED.nodes(), start);
        assertTrue(result.success(), "le graphe du banc doit compiler");
        return result.ir();
    }

    private static ExecutionEnvironment env() {
        return new ExecutionEnvironment(
                typeId -> LOADED.nodes().get(typeId).orElse(null),
                HANDLE, TRIGGER, VarStore.inMemory(), null, null, LOGGER);
    }

    /** {@link #RUNS} exécutions complètes de l'IR donnée, jusqu'à {@code DONE}. */
    private static void series(Ir ir) {
        for (int i = 0; i < RUNS; i++) {
            ExecutionState state = ExecutionState.fresh(ir);
            ExecResult result = BlueprintVm.run(ir, state, env(), Integer.MAX_VALUE);
            assertEquals(ExecResult.DONE, result, "l'exécution du banc doit aller au bout");
        }
    }

    private static long allocatedBy(Runnable work, com.sun.management.ThreadMXBean threads) {
        long id = Thread.currentThread().threadId();
        long before = threads.getThreadAllocatedBytes(id);
        work.run();
        return threads.getThreadAllocatedBytes(id) - before;
    }

    @Test
    void unAppelDeNoeudResteSousSonBudgetDOctets() {
        var bean = java.lang.management.ManagementFactory.getThreadMXBean();
        assumeTrue(bean instanceof com.sun.management.ThreadMXBean,
                "mesure d'allocation par fil indisponible sur cette JVM");
        var threads = (com.sun.management.ThreadMXBean) bean;
        assumeTrue(threads.isThreadAllocatedMemorySupported(),
                "mesure d'allocation par fil non supportée");
        threads.setThreadAllocatedMemoryEnabled(true);

        Ir shortIr = chain(SHORT_CHAIN);
        Ir longIr = chain(LONG_CHAIN);

        // Échauffement : on veut le régime établi, celui où le JIT a déjà scalarisé ce qu'il
        // pouvait. Mesurer à froid surestimerait, et surtout mesurerait l'interpréteur.
        for (int i = 0; i < 5; i++) {
            series(shortIr);
            series(longIr);
        }

        // Séries ALTERNÉES et meilleur de chaque, comme EventDispatchPerfTest : une
        // compilation JIT qui tomberait au milieu d'une série ne fausse alors qu'elle.
        long bestShort = Long.MAX_VALUE;
        long bestLong = Long.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            bestShort = Math.min(bestShort, allocatedBy(() -> series(shortIr), threads));
            bestLong = Math.min(bestLong, allocatedBy(() -> series(longIr), threads));
        }

        // La différence isole le marginal : le coût fixe par exécution s'annule.
        long extraCalls = (long) RUNS * (LONG_CHAIN - SHORT_CHAIN);
        long perCall = (bestLong - bestShort) / extraCalls;

        LOGGER.info("Allocation de la VM : {} octets par appel de nœud"
                        + " ({} nœuds → {} Ko, {} nœuds → {} Ko, sur {} exécutions ;"
                        + " budget {} octets)",
                perCall, SHORT_CHAIN, bestShort / 1024, LONG_CHAIN, bestLong / 1024,
                RUNS, MAX_BYTES_PER_CALL);

        // §7.1 : un banc qui mesure zéro passe À VIDE, ce qui est pire que rouge — il ne
        // vérifie plus rien et personne ne s'en aperçoit. Un appel de nœud alloue forcément
        // quelque chose aujourd'hui ; le jour où ce sera vraiment zéro, ce test devra être
        // relu, pas contourné.
        assertTrue(perCall > 0,
                "mesure nulle : le banc ne mesure rien (allocation désactivée ? série trop courte ?)");
        assertTrue(perCall <= MAX_BYTES_PER_CALL,
                "un appel de nœud alloue " + perCall + " octets, budget " + MAX_BYTES_PER_CALL
                        + " — une allocation a été ajoutée dans la boucle chaude de la VM"
                        + " (coding-standards §5)");
    }
}
