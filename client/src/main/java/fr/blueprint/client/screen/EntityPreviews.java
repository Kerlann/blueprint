package fr.blueprint.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Les modèles d'entités affichés par un {@code ENTITY_PREVIEW} (story 10.8, AC5c).
 *
 * <p><b>Rien ne vit ici.</b> L'entité est construite côté client, contre le monde du
 * client, et n'est <b>jamais ajoutée</b> à ce monde : elle sert de modèle à dessiner,
 * exactement comme le joueur qu'affiche l'écran d'inventaire. En faire apparaître une
 * vraie la ferait exister — elle prendrait des dégâts, déclencherait des événements,
 * compterait dans les quotas du monde, et resterait là si l'écran se fermait mal.
 *
 * <p>Avec un cache, et pas par confort : sans lui, un menu ouvert construirait une entité
 * <b>par image</b>, soixante fois par seconde. Chaque construction alloue un modèle
 * complet et son inventaire ; le coût ne se verrait pas tout de suite, mais le ramasse-
 * miettes finirait par le montrer sous forme de saccades qu'on attribuerait au rendu.
 *
 * <p>Le cache est vidé au changement de monde : une entité porte une référence à son
 * niveau, et la garder à travers un rechargement retiendrait tout l'ancien monde en
 * mémoire.
 */
public final class EntityPreviews {

    /**
     * Nombre maximal de modèles gardés. Un menu de création de personnage en montre un ;
     * un sélecteur de monture, une dizaine. Au-delà, on ne cache plus — mieux vaut
     * reconstruire que retenir sans fin ce qu'un écran a cessé d'afficher.
     */
    private static final int MAX_CACHED = 16;

    private static final Map<Identifier, LivingEntity> CACHE = new HashMap<>();
    private static @Nullable net.minecraft.world.level.Level cachedLevel;

    private EntityPreviews() {
    }

    /**
     * Le modèle à dessiner pour ce type, ou {@code null} si le type est inconnu ou n'est
     * pas une créature.
     *
     * <p>{@code null} plutôt qu'une entité de remplacement : un cochon à la place du
     * dragon demandé ferait croire que le graphe dit « cochon ». Un cadre vide se
     * comprend ; une mauvaise entité, non.
     */
    public static @Nullable LivingEntity of(@Nullable Identifier type) {
        Minecraft client = Minecraft.getInstance();
        if (type == null || client == null || client.level == null) {
            return null;
        }
        if (cachedLevel != client.level) {
            // Changement de monde : une entité garde une référence à son niveau, et la
            // retenir garderait tout l'ancien monde avec elle.
            clear();
            cachedLevel = client.level;
        }
        LivingEntity cached = CACHE.get(type);
        if (cached != null) {
            return cached;
        }
        LivingEntity built = build(type, client.level);
        if (built != null && CACHE.size() < MAX_CACHED) {
            CACHE.put(type, built);
        }
        return built;
    }

    private static @Nullable LivingEntity build(Identifier type,
                                                net.minecraft.world.level.Level level) {
        var entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(type).orElse(null);
        if (entityType == null) {
            return null;
        }
        try {
            // EVENT et non NATURAL : la raison de création est lue par certaines entités
            // pour décider de leur équipement et de leurs variantes. « Naturelle » ferait
            // apparaître un squelette armé au hasard, différent à chaque ouverture du
            // menu — un aperçu doit être stable.
            var entity = entityType.create(level, EntitySpawnReason.EVENT);
            return entity instanceof LivingEntity living ? living : null;
        } catch (RuntimeException e) {
            // Une entité peut refuser d'être construite hors du monde. C'est son droit :
            // le cadre reste vide et le reste du menu s'affiche, plutôt qu'un écran qui
            // tombe pour un aperçu décoratif.
            return null;
        }
    }

    /** Vide le cache — au changement de monde, et à la déconnexion. */
    public static void clear() {
        CACHE.clear();
        cachedLevel = null;
    }
}
