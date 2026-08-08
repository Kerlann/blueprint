package fr.blueprint.core.compile;

import fr.blueprint.api.BlueprintPlugin;
import fr.blueprint.api.node.NodeType;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.EditOperation;
import fr.blueprint.core.graph.GraphLimits;
import fr.blueprint.core.graph.Link;
import fr.blueprint.core.graph.Vec2d;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** NFR2 : compilation d'un graphe de 1 000 nœuds < 50 ms, mesurée et consignée. */
@Tag("bench")
class CompilerPerfTest {

    private static final Logger LOGGER = LoggerFactory.getLogger("blueprint-test");

    @Test
    void thousandNodeChainCompilesUnderFiftyMillis() {
        BlueprintPlugin plugin = registry -> registry.register(
                NodeType.builder(Identifier.fromNamespaceAndPath("perf", "pass"))
                        .exec().action(ctx -> {
                        }).build());
        var loaded = PluginLoader.load(List.of(new PluginLoader.PluginEntry("perf", plugin)));

        var bp = new Blueprint(Identifier.fromNamespaceAndPath("perf", "chain"));
        Identifier pass = Identifier.fromNamespaceAndPath("perf", "pass");
        GraphLimits limits = new GraphLimits(1200);
        UUID first = null;
        UUID previous = null;
        for (int i = 0; i < 1000; i++) {
            UUID uuid = UUID.nameUUIDFromBytes(("n" + i).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var add = new EditOperation.AddNode(uuid, pass, new Vec2d(i * 10, 0))
                    .apply(bp, loaded.nodes(), limits);
            assertTrue(add.applied());
            if (previous != null) {
                assertTrue(new EditOperation.AddLink(new Link(previous, "exec_out", uuid, "exec_in"))
                        .apply(bp, loaded.nodes(), limits).applied());
            } else {
                first = uuid;
            }
            previous = uuid;
        }

        // Échauffement JIT, puis meilleur de cinq mesures — en temps <b>processeur du
        // fil</b>, pas en temps mural.
        //
        // Le meilleur de cinq mesures murales ne suffisait pas : mesuré seul, ce
        // compilateur prend 18 ms ; mesuré pendant que la suite complète tourne en
        // parallèle, il « prend » 54 ms et le test échoue. Il mesurait donc la charge de
        // la machine, et un test qui rougit sans qu'aucun code n'ait changé apprend
        // surtout à relancer plutôt qu'à chercher — c'est ainsi qu'une vraie régression
        // finit par passer inaperçue.
        //
        // Le temps processeur du fil ne compte que les instants où ce fil a réellement
        // tourné : une préemption ne s'y voit pas, une régression du compilateur si.
        // C'est ce que le NFR2 veut dire, et c'était ce qu'on cherchait depuis le début.
        //
        // AGRÉGÉ, et pas mesuré coup par coup. Ce banc mesurait UNE compilation, ce qui
        // suffisait tant qu'elle prenait 18 ms. Depuis que les liens sont indexés (épic 14)
        // elle en prend moins d'une, et la mesure rendait **0 ms** : le test passait à vide,
        // ce qui est pire que rouge — il ne vérifiait plus rien et personne ne s'en
        // apercevait. C'est exactement le piège décrit en §7.1, et il s'est refermé ici
        // dès que le code est devenu plus rapide. Un banc doit donc survivre au succès de
        // ce qu'il surveille.
        for (int i = 0; i < 3; i++) {
            Compiler.compile(bp, loaded.nodes(), first);
        }
        var threads = java.lang.management.ManagementFactory.getThreadMXBean();
        boolean cpuTime = threads.isCurrentThreadCpuTimeSupported();
        long bestNanos = Long.MAX_VALUE;
        Compiler.CompileResult result = null;
        for (int i = 0; i < 5; i++) {
            long begin = cpuTime ? threads.getCurrentThreadCpuTime() : System.nanoTime();
            for (int c = 0; c < COMPILES_PER_ROUND; c++) {
                result = Compiler.compile(bp, loaded.nodes(), first);
            }
            long end = cpuTime ? threads.getCurrentThreadCpuTime() : System.nanoTime();
            bestNanos = Math.min(bestNanos, end - begin);
        }

        assertTrue(result.success());
        double perCompileMs = bestNanos / (double) COMPILES_PER_ROUND / 1_000_000.0;
        LOGGER.info("Compilation de 1 000 nœuds : {} ms (meilleure série de {} sur 5 ;"
                        + " budget NFR2 : 50 ms)",
                String.format(java.util.Locale.ROOT, "%.2f", perCompileMs), COMPILES_PER_ROUND);

        assertTrue(bestNanos > 0,
                "mesure nulle : granularité d'horloge non couverte malgré l'agrégation");
        assertTrue(perCompileMs < 50, String.format(java.util.Locale.ROOT,
                "compilation en %.2f ms ≥ 50 ms", perCompileMs));
    }

    // ------------------------------------------------------------------------------
    // Second cas : des liens de DONNÉES, denses.
    // ------------------------------------------------------------------------------

    /**
     * Le banc ci-dessus mesure une chaîne de mille nœuds — mais ses nœuds
     * <b>n'ont aucun pin de données</b> : un {@code perf:pass} sans entrée ni sortie. Or
     * c'est le nombre de <b>liens</b> qui fait travailler {@link fr.blueprint.core.graph.GraphValidator},
     * que {@code Compiler.compile} appelle en entier avant d'émettre quoi que ce soit.
     *
     * <p>Un graphe sans liens de données neutralise donc la moitié des parcours du
     * validateur, et le budget de 50 ms passe confortablement sans rien dire de la
     * complexité réelle. Ce banc-ci ajoute le cas manquant.
     *
     * <h2>Pourquoi un rapport et non un budget</h2>
     *
     * <p>Les standards (§7.1) préfèrent « un rapport entre deux mesures prises au même
     * moment » : les deux subissent la même machine, donc leur rapport n'en dépend plus.
     * C'est la forme qui convient ici, parce que la question n'est pas « combien de
     * millisecondes » mais « <b>le coût suit-il la taille, ou son carré ?</b> ».
     *
     * <p>On mesure donc le coût <b>par lien</b> sur deux graphes dont l'un est quatre fois
     * l'autre. Si la validation est linéaire, le coût par lien est constant et le rapport
     * vaut 1. Si elle est quadratique, le coût par lien croît comme la taille et le rapport
     * vaut 4. Le seuil est posé à <b>2,0</b>, à mi-chemin en échelle logarithmique.
     */
    private static final int SMALL_NODES = 250;
    private static final int LARGE_NODES = SMALL_NODES * 4;
    /** Entrées de données par nœud : c'est ce qui rend les liens denses (≈ 4 liens/nœud). */
    private static final int DATA_INPUTS = 3;
    /**
     * Compilations par série : la granularité de l'horloge CPU (~15 ms) impose d'agréger.
     *
     * <p>Dix suffisaient quand la validation était quadratique — le petit cas prenait alors
     * 7,8 ms par compilation. Depuis que les liens sont indexés (épic 14) il en prend moins
     * de deux, et dix compilations sont retombées <b>sous la granularité</b> : la mesure a
     * rendu 0 et la garde anti-vacuité a fait échouer le banc. C'est le comportement voulu,
     * et c'est la deuxième fois dans ce fichier qu'un banc devient trop rapide pour son
     * propre agrégat.
     *
     * <p>Quarante ne suffisaient pas non plus : le petit cas retombait à 15 ms, soit <b>un
     * seul granule</b>, et le rapport oscillait jusqu'à 1,74 contre un seuil de 2,0 — la
     * marge d'un banc sur le point de rougir sans raison. C'est mot pour mot l'incident du
     * « 0,51 contre 0,50 » que §7.1 raconte. Deux cents portent le petit cas à plus de dix
     * granules, ce qui rend la quantification négligeable devant la mesure.
     */
    private static final int COMPILES_PER_ROUND = 200;

    /** Un nœud à {@link #DATA_INPUTS} entrées de données et une sortie. */
    private static final BlueprintPlugin DENSE = registry -> {
        var builder = NodeType.builder(Identifier.fromNamespaceAndPath("perf", "dense")).exec();
        for (int i = 0; i < DATA_INPUTS; i++) {
            builder = builder.in("in" + i, fr.blueprint.api.pin.PinTypes.INT, 0);
        }
        registry.register(builder.out("result", fr.blueprint.api.pin.PinTypes.INT)
                .action(ctx -> ctx.out("result", ctx.<Integer>in("in0")))
                .build());
    };

    private record Fixture(Blueprint blueprint, UUID entry, int links) {
    }

    /**
     * Une chaîne de {@code nodes} nœuds, chacun recevant ses {@link #DATA_INPUTS} entrées
     * de la sortie du précédent — une sortie de données peut alimenter plusieurs entrées,
     * une entrée n'en accepte qu'une.
     */
    private static Fixture denseGraph(PluginLoader.LoadedRegistries loaded, int nodes) {
        var bp = new Blueprint(Identifier.fromNamespaceAndPath("perf", "dense" + nodes));
        Identifier dense = Identifier.fromNamespaceAndPath("perf", "dense");
        GraphLimits limits = new GraphLimits(nodes + 10);
        UUID first = null;
        UUID previous = null;
        int links = 0;
        for (int i = 0; i < nodes; i++) {
            UUID uuid = UUID.nameUUIDFromBytes(("d" + nodes + "_" + i)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertTrue(new EditOperation.AddNode(uuid, dense, new Vec2d(i * 10, 0))
                    .apply(bp, loaded.nodes(), limits).applied());
            if (previous != null) {
                assertTrue(new EditOperation.AddLink(
                        new Link(previous, "exec_out", uuid, "exec_in"))
                        .apply(bp, loaded.nodes(), limits).applied());
                links++;
                for (int p = 0; p < DATA_INPUTS; p++) {
                    assertTrue(new EditOperation.AddLink(
                            new Link(previous, "result", uuid, "in" + p))
                            .apply(bp, loaded.nodes(), limits).applied());
                    links++;
                }
            } else {
                first = uuid;
            }
            previous = uuid;
        }
        return new Fixture(bp, first, links);
    }

    /** Temps processeur du fil, en ns, pour {@link #COMPILES_PER_ROUND} compilations. */
    private static long round(PluginLoader.LoadedRegistries loaded, Fixture fixture,
                             java.lang.management.ThreadMXBean threads, boolean cpuTime) {
        long begin = cpuTime ? threads.getCurrentThreadCpuTime() : System.nanoTime();
        for (int i = 0; i < COMPILES_PER_ROUND; i++) {
            assertTrue(Compiler.compile(fixture.blueprint(), loaded.nodes(),
                    fixture.entry()).success());
        }
        return (cpuTime ? threads.getCurrentThreadCpuTime() : System.nanoTime()) - begin;
    }

    /**
     * <b>Le test qui compte.</b> Quadrupler la taille du graphe ne doit pas quadrupler le
     * coût <i>par lien</i>.
     *
     * <h2>La marge, mesurée</h2>
     *
     * <p>Le seuil de 2,0 sépare le régime linéaire (rapport ≈ 1) du régime quadratique
     * (rapport ≈ 4) avec un facteur deux de chaque côté.
     *
     * <p>Relevés : <b>3,67 avant l'index des liens</b> (épic 14), puis <b>1,12 et 1,28 sur
     * deux exécutions</b> après. La marge au seuil est donc d'environ 1,6×, et l'écart au
     * régime quadratique d'environ 3×.
     */
    @Test
    void leCoutParLienNeSuitPasLaTailleDuGraphe() {
        var loaded = PluginLoader.load(List.of(new PluginLoader.PluginEntry("perf", DENSE)));
        Fixture small = denseGraph(loaded, SMALL_NODES);
        Fixture large = denseGraph(loaded, LARGE_NODES);

        var threads = java.lang.management.ManagementFactory.getThreadMXBean();
        boolean cpuTime = threads.isCurrentThreadCpuTimeSupported();

        round(loaded, small, threads, cpuTime);
        round(loaded, large, threads, cpuTime);

        // Séries alternées, meilleur de chaque : une préemption ou un ramasse-miettes
        // tombant dans une fenêtre ne fausse alors que celle-là.
        long bestSmall = Long.MAX_VALUE;
        long bestLarge = Long.MAX_VALUE;
        for (int i = 0; i < 3; i++) {
            bestSmall = Math.min(bestSmall, round(loaded, small, threads, cpuTime));
            bestLarge = Math.min(bestLarge, round(loaded, large, threads, cpuTime));
        }

        double perLinkSmall = (double) bestSmall / small.links();
        double perLinkLarge = (double) bestLarge / large.links();
        double ratio = perLinkLarge / perLinkSmall;

        LOGGER.info("Validation dense : {} nœuds / {} liens → {} ms, {} nœuds / {} liens →"
                        + " {} ms — coût par lien × {}",
                SMALL_NODES, small.links(), bestSmall / 1_000_000,
                LARGE_NODES, large.links(), bestLarge / 1_000_000,
                String.format(java.util.Locale.ROOT, "%.2f", ratio));

        // §7.1 : une mesure nulle ferait passer le test À VIDE.
        assertTrue(bestSmall > 0 && bestLarge > 0,
                "mesure nulle : granularité d'horloge non couverte, augmenter COMPILES_PER_ROUND");
        assertTrue(ratio < 2.0, String.format(java.util.Locale.ROOT,
                "quadrupler le graphe a multiplié le coût PAR LIEN par %.2f — la validation"
                        + " est redevenue quadratique (index des liens contourné ?)", ratio));
    }
}
