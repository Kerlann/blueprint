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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Chaque nœud pur est exécuté au moins une fois.</b>
 *
 * <p>La bibliothèque compte cent quatre-vingt-quatre nœuds et
 * {@link StandardLibraryTest} en vérifiait les <i>métadonnées</i> — permissions, pureté,
 * enregistrement, symétrie des langues — sans jamais en <b>exécuter</b> un seul. Les tests
 * ciblés couvrent ceux qu'une story a touchés ; les autres n'ont jamais tourné.
 *
 * <p>Or les pins sont désignés par des <b>chaînes</b>. Un {@code ctx.out("resultat")} là où
 * la déclaration dit {@code "result"} compile parfaitement, passe toutes les vérifications
 * de forme, et ne se voit qu'au moment où quelqu'un pose le nœud dans une partie. Il en va
 * de même d'un {@code ctx.in()} dont le type ne correspond pas à celui déclaré : le cast
 * échoue à l'exécution, jamais avant.
 *
 * <p>Ce projet a déjà payé deux fois cette famille de défaut : {@code event/signal}, point
 * d'entrée mort pendant plusieurs stories, et le nœud de touche de la 11.4, muet parce
 * qu'il n'écoutait aucun emplacement. Les deux se posaient, se câblaient, et ne faisaient
 * rien.
 *
 * <p>Ce test n'a pas d'assertion sur ce que les nœuds <i>calculent</i> — c'est le travail
 * des tests ciblés. Il vérifie qu'aucun ne <b>lève</b> quand on l'exécute avec ses propres
 * valeurs par défaut, ce qui est le minimum qu'un nœud livré doive tenir.
 */
class PureNodeSmokeTest {

    private static final Logger LOGGER = LoggerFactory.getLogger("blueprint-test");

    private static final PluginLoader.LoadedRegistries LOADED =
            PluginLoader.load(List.of(), true);

    /**
     * Les nœuds <b>abaissés par le compilateur</b> : leur action est une garde qui doit
     * lever si elle est jamais atteinte.
     *
     * <p>Ce ne sont pas des exceptions à la règle mais son cas limite, et ils sont donc
     * <b>vérifiés à part</b> plutôt qu'écartés — voir
     * {@link #lesNoeudsAbaissesLeventBienSiLaVmLesAtteint()}. Une liste d'exclusions
     * n'aurait rien prouvé ; ici, un nœud qui cesserait de lever serait un nœud dont le
     * compilateur a oublié l'abaissement, et le graphe l'exécuterait pour de bon.
     */
    private static final List<String> LOWERED = List.of(
            "var/get", "var/set", "flow/sequence", "flow/while", "flow/for",
            "flow/wait_until", "flow/for_each", "flow/gate", "flow/do_once",
            // Épic 20 : ajoutés par les fonctions, et oubliés des deux listes jusqu'au
            // jour où le gametest les a signalés comme « nœuds non purs qui lèvent ».
            "func/call", "func/param", "func/result");

    /**
     * Les nœuds purs qui touchent malgré tout un registre du jeu, et ne peuvent donc pas
     * s'exécuter sans que Minecraft soit amorcé.
     *
     * <p>La liste doit rester <b>courte</b> : chaque entrée est un nœud que ce test ne
     * couvre pas. {@code item/create} y est parce qu'il résout un identifiant d'objet
     * dans {@code BuiltInRegistries}, ce qu'aucun test headless de ce projet n'amorce.
     */
    private static final List<String> NEEDS_GAME = List.of("item/create");

    private static boolean listed(NodeType type, List<String> paths) {
        return paths.contains(type.id().getPath());
    }

    /**
     * Une valeur de secours pour un pin sans défaut déclaré, quand elle se fabrique sans
     * serveur. Les types qui exigent un monde vivant — joueur, entité, pile d'objets —
     * n'y sont volontairement pas : ces nœuds-là ne sont pas purs et se vérifient en
     * gametest.
     */
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
        return null;   // type qui demande un monde : le nœud est écarté
    }

    /** Les entrées à donner au nœud, ou {@code null} si l'une d'elles est hors de portée. */
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

    /**
     * <b>Le test qui compte.</b> Aucun nœud pur ne lève quand on l'exécute avec ses
     * propres défauts.
     *
     * <p>Une <i>faute</i> déclarée par le nœud ({@code ctx.fail}) est un résultat
     * légitime — une division par zéro, un identifiant inconnu. Ce qui n'en est pas un,
     * c'est une exception : elle traverse la VM et devient une panne d'exécution que
     * l'auteur du graphe ne peut ni prévoir ni corriger.
     */
    @Test
    void aucunNoeudPurNeLeveAvecSesPropresDefauts() {
        List<String> exploded = new ArrayList<>();
        int run = 0;
        int skipped = 0;

        for (NodeType type : LOADED.nodes().all()) {
            if (!type.pure() || type.entryPoint()
                    || listed(type, LOWERED) || listed(type, NEEDS_GAME)) {
                continue;
            }
            Map<String, Object> inputs = inputsFor(type);
            if (inputs == null) {
                skipped++;
                continue;
            }
            try {
                FakeNodeRun.invoke(type, inputs);
                run++;
            } catch (RuntimeException | Error e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                exploded.add(type.id() + " → " + cause.getClass().getSimpleName()
                        + " : " + cause.getMessage());
            }
        }

        LOGGER.info("Nœuds purs exécutés : {} ; écartés (type exigeant un monde) : {}",
                run, skipped);
        assertTrue(exploded.isEmpty(),
                "nœud(s) qui lèvent avec leurs propres valeurs par défaut :\n"
                        + String.join("\n", exploded));
        // Sans plancher, un changement de forme des pins pourrait tout faire écarter et
        // ce test passerait à vide — la panne qu'on vient de corriger sur PaletteTest.
        assertTrue(run >= 60, "seulement " + run + " nœuds purs exécutés : le test ne "
                + "couvre presque plus rien, vérifier fallback()");
    }

    /**
     * <b>Le test qui compte.</b> Les neuf nœuds abaissés par le compilateur lèvent bien
     * si la VM les atteint.
     *
     * <p>Ils n'ont pas d'implémentation : le compilateur les remplace par des
     * instructions dédiées ({@code LoadVar}, les boucles, les portes). Leur action est
     * donc une <b>garde</b>, et un nœud abaissé qui cesserait de lever serait un nœud dont
     * le compilateur a oublié l'abaissement — la VM l'exécuterait alors pour de bon, et
     * une boucle deviendrait silencieusement un nœud qui ne fait rien.
     *
     * <p>Les vérifier vaut mieux que les écarter : une liste d'exclusions n'aurait rien
     * prouvé, et se serait périmée au premier nœud abaissé de plus.
     */
    @Test
    void lesNoeudsAbaissesLeventBienSiLaVmLesAtteint() {
        List<String> silentlyRan = new ArrayList<>();
        for (String path : LOWERED) {
            NodeType type = LOADED.nodes()
                    .get(Identifier.fromNamespaceAndPath("blueprint", path)).orElse(null);
            assertTrue(type != null, "nœud abaissé « " + path + " » absent du registre : "
                    + "la liste de ce test est périmée");
            Map<String, Object> inputs = inputsFor(type);
            try {
                FakeNodeRun.invoke(type, inputs == null ? Map.of() : inputs);
                silentlyRan.add(path);
            } catch (RuntimeException | Error expected) {
                // C'est le comportement attendu : la garde a fait son office.
            }
        }
        assertTrue(silentlyRan.isEmpty(), "nœud(s) abaissé(s) dont la garde ne lève plus — "
                + "le compilateur a-t-il cessé de les abaisser ? " + silentlyRan);
    }

    /**
     * Tout nœud pur déclare au moins une sortie, et la <b>renseigne</b>.
     *
     * <p>Un nœud pur qui ne produit rien est un nœud qu'on câble et qui donne
     * silencieusement {@code null} au suivant — la faute se manifeste alors chez le
     * voisin, à un endroit qui n'a rien fait de mal. C'est la forme la plus coûteuse à
     * diagnostiquer, parce qu'elle accuse le mauvais nœud.
     */
    @Test
    void toutNoeudPurRemplitCeQuIlDeclare() {
        List<String> silent = new ArrayList<>();

        for (NodeType type : LOADED.nodes().all()) {
            if (!type.pure() || type.entryPoint()
                    || listed(type, LOWERED) || listed(type, NEEDS_GAME)) {
                continue;
            }
            Map<String, Object> inputs = inputsFor(type);
            if (inputs == null) {
                continue;
            }
            var ctx = FakeNodeRun.invoke(type, inputs);
            if (ctx.failReason() != null) {
                continue;   // une faute déclarée est un résultat légitime
            }
            for (NodeType.PinSpec out : type.outputs()) {
                if (out.kind() != PinKind.EXEC && !ctx.outputs().containsKey(out.name())) {
                    silent.add(type.id() + " ne renseigne pas « " + out.name() + " »");
                }
            }
        }

        assertTrue(silent.isEmpty(), "sortie(s) déclarée(s) et jamais remplie(s) — "
                + "le nœud suivant recevra null sans savoir pourquoi :\n"
                + String.join("\n", silent));
    }
}
