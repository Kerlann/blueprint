package fr.blueprint.compat;

import fr.blueprint.core.BlueprintMod;
import fr.blueprint.core.registry.NodeRegistryImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Intégrations conditionnelles avec les mods tiers (story 8.4). Chacune vit dans
 * {@code fr.blueprint.compat.<modid>} et n'est chargée <b>que si son mod est présent</b> :
 * Blueprint démarre exactement pareil qu'il y en ait zéro ou dix.
 *
 * <p>La présence d'un mod est un <b>paramètre</b>, pas un appel direct à
 * {@code FabricLoader} : c'est ce qui rend le chargement testable sans jeu, y compris le
 * cas « aucun de ces mods n'est là » (AC3), qui est celui de la plupart des installations.
 *
 * <p>Une intégration qui lève est <b>isolée</b> comme un plugin tiers : journalisée avec
 * le mod concerné, ses enregistrements retirés. Un mod compagnon cassé ne doit jamais
 * emporter Blueprint.
 */
public final class CompatLoader {

    /** Ce qu'une intégration déclare : le mod qu'elle vise, et ce qu'elle ajoute. */
    public interface Integration {

        /** Modid attendu — l'intégration ne s'exécute que s'il est chargé. */
        String modId();

        /** Enregistre ses nœuds ; le registre est déjà positionné sur le bon fournisseur. */
        void register(NodeRegistryImpl nodes);
    }

    /** Les intégrations livrées. Une seule pour l'instant : elle sert de modèle (AC2). */
    static final List<Integration> INTEGRATIONS = List.of(new TestmodCompat());

    /** Fournisseur déclaré au registre pour l'intégration d'un mod donné. */
    public static String providerFor(String modId) {
        return "blueprint_compat_" + modId;
    }

    private CompatLoader() {
    }

    /**
     * Charge les intégrations dont le mod est présent.
     *
     * @param modPresent test de présence (en jeu : {@code FabricLoader::isModLoaded})
     * @return les modids effectivement intégrés, dans l'ordre de déclaration
     */
    public static List<String> load(NodeRegistryImpl nodes, Predicate<String> modPresent) {
        List<String> loaded = new ArrayList<>();
        for (Integration integration : INTEGRATIONS) {
            String modId = integration.modId();
            if (!modPresent.test(modId)) {
                continue;
            }
            String provider = providerFor(modId);
            nodes.currentProvider(provider);
            try {
                integration.register(nodes);
                loaded.add(modId);
                BlueprintMod.LOGGER.info("Intégration « {} » chargée", modId);
            } catch (Exception e) {
                nodes.removeAllFrom(provider);
                BlueprintMod.LOGGER.error(
                        "Intégration « {} » en échec — ses nœuds sont retirés, Blueprint continue",
                        modId, e);
            }
        }
        return List.copyOf(loaded);
    }
}
