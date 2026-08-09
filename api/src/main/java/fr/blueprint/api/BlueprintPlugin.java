package fr.blueprint.api;

import fr.blueprint.api.event.EventRegistry;
import fr.blueprint.api.registry.NodeRegistry;
import fr.blueprint.api.registry.PinTypeRegistry;

/**
 * Point d'entrée d'un mod tiers dans Blueprint.
 *
 * <p>Deux façons de se déclarer, et les deux marchent :
 *
 * <pre>{@code
 * // 1. RECOMMANDÉE — un fichier de service, valable sur tous les chargeurs :
 * //    META-INF/services/fr.blueprint.api.BlueprintPlugin
 * com.example.MyPlugin
 *
 * // 2. HISTORIQUE — l'entrypoint Fabric, toujours supporté :
 * "entrypoints": { "blueprint": ["com.example.MyPlugin"] }
 * }</pre>
 *
 * <p>Déclarer les deux est sans risque : un plugin trouvé deux fois n'est chargé qu'une.
 * La voie par service exige en revanche {@link #modId()}, que l'entrypoint fournissait
 * gratuitement.
 *
 * <p>Ordre d'appel, tous plugins confondus par phase :
 * {@link #registerTypes} → {@link #registerNodes} → {@link #registerEvents}.
 * Une exception levée dans l'une des phases isole le plugin : elle est journalisée
 * avec le nom du mod, ses enregistrements partiels sont retirés (ses nœuds deviennent
 * fantômes dans les graphes qui les utilisent), et les autres plugins chargent
 * normalement — jamais de crash au démarrage.
 */
public interface BlueprintPlugin {

    /** Enregistre les nœuds du mod. Obligatoire. */
    void registerNodes(NodeRegistry registry);

    /**
     * L'identifiant du mod qui fournit ce plugin — <b>obligatoire pour la voie par
     * service</b>, ignoré pour l'entrypoint Fabric (qui le connaît déjà).
     *
     * <p>Ce n'est pas une formalité : cet identifiant est <b>montré au joueur</b>. Il
     * apparaît dans l'infobulle d'un nœud et dans le panneau de détails, sous « fourni
     * par ». C'est aussi lui qui nomme le fautif quand un plugin lève, et qui permet de
     * retirer ses seuls enregistrements sans toucher aux autres.
     *
     * <p>Un plugin déclaré par service qui ne le redéfinit pas est <b>refusé</b>, et dit
     * pourquoi dans le journal. Le charger sous un nom de classe aurait mis
     * « com.example.MyPlugin » dans la palette d'un joueur, sans que personne ne le voie
     * venir — le refus, lui, se remarque et se corrige en une ligne.
     */
    default String modId() {
        return "";
    }

    /** Types de pins personnalisés. Appelé AVANT {@link #registerNodes}. */
    default void registerTypes(PinTypeRegistry registry) {
    }

    /** Événements déclencheurs personnalisés. Appelé APRÈS {@link #registerNodes}. */
    default void registerEvents(EventRegistry registry) {
    }
}
