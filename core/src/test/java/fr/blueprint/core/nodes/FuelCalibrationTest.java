package fr.blueprint.core.nodes;

import fr.blueprint.api.node.NodeType;
import fr.blueprint.api.pin.PinKind;
import fr.blueprint.api.pin.PinTypes;
import fr.blueprint.core.registry.PluginLoader;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Le {@code fuelCost} déclaré d'un nœud doit refléter ce qu'il coûte vraiment.</b>
 *
 * <p>Le mécanisme de fuel est complet de bout en bout — {@code NodeType.Builder.fuelCost},
 * l'annotation {@code @BlueprintNode}, le compilateur, la sérialisation de l'IR, jusqu'à
 * {@code spent += call.fuelCost()} dans la VM. Il était simplement <b>jamais renseigné</b> :
 * les cent quatre-vingt-quatre nœuds de la bibliothèque coûtaient tous 1, du {@code math/add}
 * au raycast de cent vingt-huit blocs.
 *
 * <p>Le budget d'un tick en autorise dix mille. Il bornait donc le <b>nombre</b> d'appels et
 * pas leur <b>poids</b> — un graphe pouvait consommer un tick entier en travail lourd sans
 * jamais déclencher la police de dépassement, qui ne réagit qu'à {@code OUT_OF_FUEL}.
 *
 * <h2>Ce que ce test fait, et ce qu'il ne fait pas</h2>
 *
 * <p>Il mesure le coût réel de chaque nœud pur exécutable sans serveur, <b>relativement à
 * {@code math/add}</b> — le nœud le plus simple de la bibliothèque, et donc l'unité naturelle
 * de fuel. La mesure passe par le même harnais pour tous ({@link FakeNodeRun}), ce qui est
 * exact plutôt que commode : dans la VM aussi, exécuter un nœud coûte l'enveloppe d'appel
 * <i>plus</i> son travail propre, et c'est bien cette somme que le fuel doit payer.
 *
 * <p>Il ne cherche pas à départager 20 de 23. Il attrape les écarts d'<b>ordre de
 * grandeur</b> — un nœud cinquante fois plus cher que l'unité et tarifé à un. C'est le
 * défaut réellement présent, et le seul qui compte pour la sûreté d'un serveur.
 *
 * <p>Les nœuds qui touchent au monde ({@code ctx.level()} nul hors serveur) ne sont pas
 * mesurables ici : ils sont tarifés par analyse documentée, et couverts par le garde-fou
 * de {@code StandardLibraryTest}.
 */
class FuelCalibrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger("blueprint-test");

    private static final PluginLoader.LoadedRegistries LOADED =
            PluginLoader.load(List.of(), true);

    /** L'unité : le nœud le plus simple de la bibliothèque, tarifé 1 par définition. */
    private static final String UNIT = "math/add";

    /**
     * Tolérance sur l'écart entre coût mesuré et coût déclaré.
     *
     * <p>Quatre, et non deux : la mesure d'une opération de quelques dizaines de
     * nanosecondes reste bruitée même agrégée, et surtarifer un nœud est sans danger alors
     * que le rougir à tort apprend à relancer plutôt qu'à chercher (§7.1). Ce facteur laisse
     * passer l'imprécision et arrête les ordres de grandeur, ce qui est exactement le
     * partage voulu.
     */
    private static final double TOLERANCE = 4.0;

    /**
     * Plancher d'une mesure, en nanosecondes.
     *
     * <h2>Pourquoi une mesure adaptative et non un nombre d'appels fixe</h2>
     *
     * <p>L'horloge processeur du fil est grossière — environ 15 ms sous Windows — et une
     * mesure plus courte rend <b>zéro</b>. Un nombre d'appels fixe ne peut donc convenir à
     * la fois à {@code list/size} (quelques centaines de nanosecondes) et à
     * {@code string/replace} sur trente-deux mille caractères : trop peu pour l'un, ce sont
     * des minutes pour l'autre.
     *
     * <p>La première version de ce banc fixait deux cents appels pour le pire cas. Tous les
     * nœuds ont mesuré <b>×0,0</b>, et le test <b>passait</b> — un zéro comparé à un tarif
     * de 1 satisfait n'importe quelle tolérance. C'est le piège nommé en §7.1 : une mesure
     * nulle ne rougit pas, elle rend le banc muet, et personne ne s'en aperçoit.
     *
     * <p>Le nombre d'appels double donc jusqu'à ce que la mesure dépasse ce plancher, fixé
     * à plus de trois fois la granularité. Et le résultat est vérifié non nul, faute de quoi
     * le test échoue au lieu de mentir.
     */
    private static final long FLOOR_NANOS = 50_000_000L;
    /** Garde-fou contre un nœud si rapide que le doublement ne s'arrêterait jamais. */
    private static final int MAX_CALLS = 1 << 21;
    private static final int ROUNDS = 3;

    /** Mêmes exclusions que {@link PureNodeSmokeTest} : abaissés par le compilateur. */
    private static final List<String> LOWERED = List.of(
            "var/get", "var/set", "flow/sequence", "flow/while", "flow/for",
            "flow/wait_until", "flow/for_each", "flow/gate", "flow/do_once");

    /** Purs mais dépendants d'un registre du jeu : non amorçables headless. */
    private static final List<String> NEEDS_GAME = List.of("item/create");

    private static Object fallback(fr.blueprint.api.pin.PinType type) {
        if (type.equals(PinTypes.STRING)) {
            return "x";
        }
        if (type.equals(PinTypes.DOUBLE)) {
            return 1.0;
        }
        if (type.equals(PinTypes.INT)) {
            return 1;
        }
        if (type.equals(PinTypes.LONG)) {
            return 1L;
        }
        if (type.equals(PinTypes.BOOL)) {
            return true;
        }
        if (type.equals(PinTypes.RESOURCE_LOCATION)) {
            return Identifier.withDefaultNamespace("stone");
        }
        if (type.equals(PinTypes.TEXT)) {
            return net.minecraft.network.chat.Component.literal("x");
        }
        if (type.equals(PinTypes.BLOCKPOS)) {
            return net.minecraft.core.BlockPos.ZERO;
        }
        if (type.equals(PinTypes.VEC3)) {
            return net.minecraft.world.phys.Vec3.ZERO;
        }
        if (type.equals(PinTypes.ANY)) {
            return "x";
        }
        if (type.id().getPath().startsWith("list")) {
            return List.of();
        }
        if (type.id().getPath().startsWith("map")) {
            return Map.of();
        }
        return null;
    }

    private static Map<String, Object> inputsFor(NodeType type) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        for (NodeType.PinSpec pin : type.inputs()) {
            if (pin.kind() == PinKind.EXEC) {
                continue;
            }
            Object value = pin.defaultValue() != null
                    ? pin.defaultValue().value() : fallback(pin.type());
            if (value == null) {
                return null;
            }
            inputs.put(pin.name(), value);
        }
        return inputs;
    }

    /** Le harnais de mesure, partagé — {@code cpuTime} dit si l'horloge CPU est disponible. */
    private record Clock(java.lang.management.ThreadMXBean threads, boolean cpuTime) {
        long now() {
            return cpuTime ? threads.getCurrentThreadCpuTime() : System.nanoTime();
        }
    }

    /**
     * Coût d'<b>un</b> appel, en nanosecondes. Le nombre d'appels agrégés double jusqu'à
     * dépasser {@link #FLOOR_NANOS}, ce qui rend la mesure valable aussi bien pour un nœud
     * à trois cents nanosecondes que pour un nœud à cinquante microsecondes.
     */
    private static double nanosPerCall(NodeType type, Map<String, Object> inputs, Clock clock) {
        for (int i = 0; i < 256; i++) {
            FakeNodeRun.invoke(type, inputs);
        }
        int calls = 256;
        long elapsed;
        while (true) {
            long begin = clock.now();
            for (int i = 0; i < calls; i++) {
                FakeNodeRun.invoke(type, inputs);
            }
            elapsed = clock.now() - begin;
            if (elapsed >= FLOOR_NANOS || calls >= MAX_CALLS) {
                break;
            }
            calls *= 2;
        }
        long best = elapsed;
        for (int r = 1; r < ROUNDS; r++) {
            long begin = clock.now();
            for (int i = 0; i < calls; i++) {
                FakeNodeRun.invoke(type, inputs);
            }
            best = Math.min(best, clock.now() - begin);
        }
        assertTrue(best > 0, "mesure nulle sur " + type.id()
                + " après " + calls + " appels agrégés — le banc serait muet");
        return (double) best / calls;
    }

    private record Measured(NodeType type, double ratio) {
    }

    @Test
    void leFuelDeclareRefleteLeCoutMesure() {
        var bean = java.lang.management.ManagementFactory.getThreadMXBean();
        Clock clock = new Clock(bean, bean.isCurrentThreadCpuTimeSupported());

        NodeType unit = LOADED.nodes()
                .get(Identifier.fromNamespaceAndPath("blueprint", UNIT)).orElseThrow();
        double unitNanos = nanosPerCall(unit, inputsFor(unit), clock);

        List<Measured> measured = new ArrayList<>();
        for (NodeType type : LOADED.nodes().all()) {
            if (!type.pure() || type.entryPoint()
                    || LOWERED.contains(type.id().getPath())
                    || NEEDS_GAME.contains(type.id().getPath())) {
                continue;
            }
            Map<String, Object> inputs = inputsFor(type);
            if (inputs == null) {
                continue;
            }
            measured.add(new Measured(type, nanosPerCall(type, inputs, clock) / unitNanos));
        }
        measured.sort((a, b) -> Double.compare(b.ratio(), a.ratio()));

        List<String> underpriced = new ArrayList<>();
        StringBuilder table = new StringBuilder("\nCoût mesuré, en unités de « " + UNIT + " » :\n");
        for (Measured m : measured) {
            int expected = (int) Math.max(1, Math.round(m.ratio()));
            int declared = m.type().fuelCost();
            boolean ok = declared * TOLERANCE >= expected;
            table.append(String.format(Locale.ROOT, "  %-28s mesuré ×%-7.1f déclaré %-4d %s%n",
                    m.type().id().getPath(), m.ratio(), declared, ok ? "" : "  ← SOUS-TARIFÉ"));
            if (!ok) {
                underpriced.add(String.format(Locale.ROOT,
                        "%s : mesuré ×%.1f, déclaré %d", m.type().id(), m.ratio(), declared));
            }
        }
        LOGGER.info(table.toString());

        assertTrue(underpriced.isEmpty(),
                "des nœuds coûtent bien plus que leur fuel déclaré — un graphe peut donc"
                        + " épuiser un tick en restant dans le budget :\n  "
                        + String.join("\n  ", underpriced));
    }

    // ------------------------------------------------------------------------------
    // Le pire cas borné
    // ------------------------------------------------------------------------------

    /**
     * Le test ci-dessus mesure les nœuds avec <b>leurs entrées par défaut</b>, et les
     * trouve tous équivalents — leur travail propre est noyé dans l'enveloppe d'appel.
     * C'est vrai, et c'est ce qui justifie leur tarif de 1.
     *
     * <p>Mais c'est une moyenne, et le fuel doit payer un <b>pire cas</b>. Le coût de
     * {@code string/replace} sur trois caractères ne dit rien de son coût sur trente-deux
     * mille ; celui de {@code list/add} sur une liste vide ne dit rien de son coût sur mille
     * vingt-quatre éléments, où il recopie tout.
     *
     * <p>Ce pire cas <b>existe et est fini</b> depuis que les bornes sont posées (épic 13a) :
     * {@code MAX_LENGTH} pour les chaînes, {@code MAX_ELEMENTS} pour les collections. C'est
     * ce qui rend la question décidable — sans bornes, aucun {@code fuelCost} constant
     * n'aurait pu être juste, puisque le coût n'aurait eu aucun majorant.
     */

    /** Une entrée au plafond pour le type donné, ou {@code null} si la taille n'y change rien. */
    private static Object atBound(fr.blueprint.api.pin.PinType type) {
        if (type.equals(PinTypes.STRING)) {
            return "a".repeat(TextMathNodes.MAX_LENGTH);
        }
        if (type.id().getPath().startsWith("list")) {
            List<Object> full = new ArrayList<>(ListNodes.MAX_ELEMENTS);
            for (int i = 0; i < ListNodes.MAX_ELEMENTS; i++) {
                full.add("e" + i);
            }
            return List.copyOf(full);
        }
        if (type.id().getPath().startsWith("map")) {
            Map<Object, Object> full = new LinkedHashMap<>();
            for (int i = 0; i < ListNodes.MAX_ELEMENTS; i++) {
                full.put("k" + i, i);
            }
            return Map.copyOf(full);
        }
        return null;
    }

    /**
     * Entrées au plafond, ou {@code null} si aucune entrée de ce nœud n'est dimensionnable
     * — auquel cas son pire cas est son cas ordinaire, déjà couvert plus haut.
     */
    private static Map<String, Object> heavyInputsFor(NodeType type) {
        Map<String, Object> inputs = inputsFor(type);
        if (inputs == null) {
            return null;
        }
        boolean sized = false;
        for (NodeType.PinSpec pin : type.inputs()) {
            if (pin.kind() == PinKind.EXEC) {
                continue;
            }
            Object heavy = atBound(pin.type());
            if (heavy != null) {
                inputs.put(pin.name(), heavy);
                sized = true;
            }
        }
        return sized ? inputs : null;
    }

    /** <b>Le test qui compte.</b> Le fuel déclaré couvre le pire cas borné du nœud. */
    @Test
    void leFuelCouvreLePireCasBorne() {
        var bean = java.lang.management.ManagementFactory.getThreadMXBean();
        Clock clock = new Clock(bean, bean.isCurrentThreadCpuTimeSupported());

        NodeType unit = LOADED.nodes()
                .get(Identifier.fromNamespaceAndPath("blueprint", UNIT)).orElseThrow();
        double unitNanos = nanosPerCall(unit, inputsFor(unit), clock);

        List<Measured> measured = new ArrayList<>();
        for (NodeType type : LOADED.nodes().all()) {
            if (!type.pure() || type.entryPoint()
                    || LOWERED.contains(type.id().getPath())
                    || NEEDS_GAME.contains(type.id().getPath())) {
                continue;
            }
            Map<String, Object> heavy = heavyInputsFor(type);
            if (heavy == null) {
                continue;
            }
            measured.add(new Measured(type, nanosPerCall(type, heavy, clock) / unitNanos));
        }
        measured.sort((a, b) -> Double.compare(b.ratio(), a.ratio()));

        List<String> underpriced = new ArrayList<>();
        StringBuilder table = new StringBuilder(
                "\nPire cas borné, en unités de « " + UNIT + " » :\n");
        for (Measured m : measured) {
            int expected = (int) Math.max(1, Math.round(m.ratio()));
            int declared = m.type().fuelCost();
            boolean ok = declared * TOLERANCE >= expected;
            table.append(String.format(Locale.ROOT, "  %-28s pire ×%-9.1f déclaré %-4d %s%n",
                    m.type().id().getPath(), m.ratio(), declared, ok ? "" : "  ← SOUS-TARIFÉ"));
            if (!ok) {
                underpriced.add(String.format(Locale.ROOT,
                        "%s : pire cas ×%.1f, déclaré %d — tarif suggéré %d",
                        m.type().id(), m.ratio(), declared, expected));
            }
        }
        LOGGER.info(table.toString());

        assertTrue(underpriced.isEmpty(),
                "au plafond de leurs entrées, ces nœuds coûtent bien plus que leur fuel :\n  "
                        + String.join("\n  ", underpriced));
    }
}
