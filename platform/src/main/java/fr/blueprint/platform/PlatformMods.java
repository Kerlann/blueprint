package fr.blueprint.platform;

import fr.blueprint.api.BlueprintPlugin;

import java.util.List;

/**
 * Ce que le chargeur sait des <b>autres mods</b> : qui est là, et qui étend Blueprint.
 *
 * <p>Les trois questions posées ici ont la même forme et des réponses très différentes
 * selon le chargeur. Fabric répond par le {@code fabric.mod.json} — entrypoints et
 * valeurs personnalisées ; NeoForge n'a d'équivalent pour ni l'un ni l'autre et devra
 * passer par {@link java.util.ServiceLoader}. C'est précisément pourquoi la question est
 * posée ici plutôt que résolue dans {@code core}.
 */
public interface PlatformMods {

    /** Ce mod est-il chargé ? Sert aux intégrations conditionnelles (story 8.4). */
    boolean isLoaded(String modId);

    /**
     * Les plugins déclarés par les mods présents, chacun avec le modid qui le fournit.
     *
     * <p>Le modid n'est pas décoratif : c'est lui qui nomme le fautif quand un plugin
     * lève, et qui permet de retirer ses seuls enregistrements sans toucher aux autres
     * (story 2.2).
     */
    List<ModPlugin> plugins();

    /**
     * Les classes porteuses de {@code @BlueprintNode} déclarées par un mod (story 8.1) :
     * la voie sans plugin ni processeur d'annotations.
     */
    List<ModNodeHolders> nodeHolders();

    /** Un plugin et le mod qui le fournit. */
    record ModPlugin(String modId, BlueprintPlugin plugin) {
    }

    /** Des classes annotées et le mod qui les déclare. */
    record ModNodeHolders(String modId, List<String> classNames) {
    }
}
