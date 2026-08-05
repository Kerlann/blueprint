package fr.blueprint.core.nodes;

import fr.blueprint.api.node.NodeType;
import fr.blueprint.core.registry.PluginLoader;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Un nœud qui touche le monde ne coûte pas le prix d'une addition.</b>
 *
 * <p>C'est le garde-fou de l'épic 13b. La calibration a corrigé les tarifs une fois ; ce
 * test empêche qu'ils reviennent au défaut sans qu'on s'en aperçoive.
 *
 * <h2>Pourquoi un plancher plutôt qu'une valeur exacte</h2>
 *
 * <p>Vérifier chaque tarif au chiffre près demanderait de mesurer chaque nœud, et la moitié
 * d'entre eux exigent un serveur vivant. Ce que ce test défend est plus modeste et plus
 * durable : <b>le défaut n'est jamais un choix</b>. {@code NodeType.Builder} initialise
 * {@code fuelCost} à 1 ; un nœud de monde resté à 1 n'a donc pas été tarifé, il a été
 * oublié — c'est exactement ce qui était arrivé aux cent quatre-vingt-quatre nœuds de la
 * bibliothèque.
 *
 * <p>Le plancher de 2 n'est pas arbitraire : atteindre le monde, une entité, un inventaire
 * ou le réseau demande au minimum de déréférencer le niveau et de résoudre un chunk ou une
 * entité. Aucun de ces nœuds ne coûte réellement ce que coûte {@code math/add}.
 *
 * <p>Les nœuds <b>purs</b> en sont exclus : ils calculent, ils ne touchent rien, et la
 * mesure de {@code FuelCalibrationTest} a confirmé que leur tarif de 1 est juste.
 */
class FuelFloorTest {

    private static final Logger LOGGER = LoggerFactory.getLogger("blueprint-test");

    private static final PluginLoader.LoadedRegistries LOADED = PluginLoader.load(List.of(), true);

    /**
     * Les catégories qui atteignent quelque chose hors du graphe.
     *
     * <p>Par catégorie et non par préfixe d'identifiant : un nœud déclare sa catégorie
     * délibérément, alors qu'un identifiant se renomme. Un nouveau nœud rangé dans l'une
     * d'elles est soumis à la règle sans que personne ait à y penser — c'est le but.
     */
    private static final List<String> REACHING_OUT = List.of(
            "world/block", "world/state", "world/effect",
            "entity/query", "entity/read", "entity/act",
            "player", "player/act", "player/feedback", "player/inventory",
            "world", "entity", "gui", "gui/look", "gui/rich", "gui/update", "scoreboard");

    /**
     * Les catégories qui atteignent le monde et ne sont <b>pas encore</b> calibrées.
     *
     * <p><b>Elle est vide, et l'épic 13b se termine là.</b> Elle ne l'a pas toujours été :
     * au premier passage, ce test a compté <b>97 nœuds atteignant le monde, dont 56 encore
     * au tarif par défaut</b>. Les tarifer tous dans la même séance aurait été cinquante-six
     * jugements bâclés, alors la calibration s'est faite par lots — {@code world/*}, puis
     * {@code entity/*}, puis {@code player/*}, puis {@code gui/*} et {@code scoreboard} —
     * chacun déplaçant une ligne d'ici vers {@link #REACHING_OUT}.
     *
     * <p>La constante reste, vide, plutôt que d'être supprimée : elle est le seul endroit
     * où l'on puisse <b>différer délibérément</b> une catégorie, et l'assertion ci-dessous
     * exige qu'un tel report soit temporaire. Y ajouter une ligne fait échouer le test, ce
     * qui est le but — un report doit se discuter, pas se glisser.
     */
    private static final List<String> NOT_YET_CALIBRATED = List.of();

    /** Le défaut de {@code NodeType.Builder} — la valeur qui trahit un oubli. */
    private static final int UNTARIFFED = 1;

    @Test
    void aucunNoeudTouchantLeMondeNeResteAuTarifParDefaut() {
        List<String> forgotten = new ArrayList<>();
        int checked = 0;

        for (NodeType type : LOADED.nodes().all()) {
            // Les purs calculent sans rien atteindre ; leur tarif de 1 est mesuré juste.
            // Les points d'entrée ne s'exécutent pas : ils sont le début d'une exécution.
            if (type.pure() || type.entryPoint()) {
                continue;
            }
            String category = type.category() == null ? "" : type.category().id();
            if (!REACHING_OUT.contains(category)) {
                continue;
            }
            checked++;
            if (type.fuelCost() <= UNTARIFFED) {
                forgotten.add(type.id() + "  (catégorie " + category + ")");
            }
        }

        LOGGER.info("Nœuds gardés : {} ; sans tarif : {} ; catégories encore à calibrer : {}",
                checked, forgotten.size(), NOT_YET_CALIBRATED.size());

        // §7.1 : un test qui ne regarde rien passerait à vide. Les quatre catégories
        // gardées comptent une vingtaine de nœuds — en trouver beaucoup moins signalerait
        // que le filtre ne correspond plus au registre, pas que tout va bien.
        assertTrue(checked >= 15,
                "seulement " + checked + " nœuds examinés : le filtre de catégorie ne"
                        + " correspond plus au registre, ce test ne garde plus rien");

        assertTrue(forgotten.isEmpty(),
                "ces nœuds atteignent le monde et coûtent encore le prix d'une addition —"
                        + " leur fuelCost est resté au défaut du builder, donc jamais choisi."
                        + " Les tarifer par mesure (FuelCalibrationTest) ou par analyse"
                        + " documentée :\n  " + String.join("\n  ", forgotten));

        // L'épic 13b s'est terminé quand cette liste s'est vidée. La rouvrir doit être un
        // choix assumé, pas un contournement discret de l'assertion précédente.
        assertTrue(NOT_YET_CALIBRATED.isEmpty(),
                "des catégories sont à nouveau exemptées de tarif : " + NOT_YET_CALIBRATED
                        + " — un report se discute, il ne se glisse pas");
    }
}
