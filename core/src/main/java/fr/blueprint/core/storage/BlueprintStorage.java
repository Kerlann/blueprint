package fr.blueprint.core.storage;

import com.mojang.serialization.Codec;
import fr.blueprint.core.BlueprintManager;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.GraphNbt;
import fr.blueprint.core.vm.BlueprintScheduler;
import fr.blueprint.core.vm.ExecutionNbt;
import fr.blueprint.core.vm.SuspendedExecution;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * L'état persistant de Blueprint dans la sauvegarde du monde (story 6.1) : blueprints,
 * exécutions suspendues, et entrées corrompues <b>préservées brutes</b> (P4 — un
 * blueprint indécodable est ré-émis tel quel, jamais perdu).
 *
 * <p>Conteneur de NBT brut, sans dépendance aux registres : le décodage typé est le
 * travail de {@link PersistenceHooks#restore}. À l'encodage, si l'état vivant est lié,
 * les listes sont régénérées depuis le manager et l'ordonnanceur — la capture est
 * non destructive, une sauvegarde périodique n'arrête aucune exécution.
 */
public final class BlueprintStorage extends SavedData {

    /**
     * DataFixTypes null assumé : nos données portent leur propre schéma
     * ({@code SchemaMigrations}) — à confirmer en jeu (session/1.6).
     */
    public static final SavedDataType<BlueprintStorage> TYPE = new SavedDataType<>(
            "blueprint_state", BlueprintStorage::new, codec(), null);

    private final List<CompoundTag> blueprints = new ArrayList<>();
    private final List<CompoundTag> suspended = new ArrayList<>();
    private final List<CompoundTag> corrupt = new ArrayList<>();

    private @Nullable BlueprintManager liveManager;
    private @Nullable BlueprintScheduler liveScheduler;

    /** Lie l'état vivant : dès lors, chaque encodage capture le présent. */
    public void bindLive(BlueprintManager manager, BlueprintScheduler scheduler) {
        this.liveManager = manager;
        this.liveScheduler = scheduler;
        setDirty();
    }

    List<CompoundTag> blueprintTags() {
        return blueprints;
    }

    List<CompoundTag> suspendedTags() {
        return suspended;
    }

    List<CompoundTag> corruptTags() {
        return corrupt;
    }

    @Override
    public boolean isDirty() {
        // Données vivantes : on laisse Minecraft écrire à chaque sauvegarde du monde.
        return true;
    }

    private static Codec<BlueprintStorage> codec() {
        return CompoundTag.CODEC.xmap(BlueprintStorage::fromTag, BlueprintStorage::toTag);
    }

    private static BlueprintStorage fromTag(CompoundTag root) {
        BlueprintStorage storage = new BlueprintStorage();
        readList(root, "blueprints", storage.blueprints);
        readList(root, "suspended", storage.suspended);
        readList(root, "corrupt", storage.corrupt);
        return storage;
    }

    private CompoundTag toTag() {
        // Qui possède quoi, à l'écriture :
        //
        // - « suspended » est reconstruit à chaque passage et n'appartient qu'à ce
        //   stockage : le recopier serait du travail et un pic mémoire pour rien (19a) ;
        // - « blueprints » vient désormais du CACHE (19c) et survit d'une sauvegarde à
        //   l'autre : celui-là se copie, sinon le tag remis à Minecraft et celui gardé en
        //   cache seraient le même objet ;
        // - « corrupt » est conservé tel qu'arrivé du disque : il se copie aussi.
        //
        // Le compromis est franchement gagnant : on paie une copie pour économiser un
        // ENCODAGE complet — nœuds, littéraux passés par leurs codecs, liens, variables,
        // écrans, commentaires — et cela pour chaque graphe inchangé, toutes les cinq
        // minutes.
        boolean fresh = refreshFromLive();
        CompoundTag root = new CompoundTag();
        root.put("blueprints", writeList(blueprints, true));
        root.put("suspended", writeList(suspended, !fresh));
        root.put("corrupt", writeList(corrupt, true));
        return root;
    }

    /**
     * Un graphe déjà encodé, et l'état qui l'a produit.
     *
     * <p>{@code enabled} EN PLUS de la révision, et ce n'est pas une précaution :
     * {@code setEnabled} est un état de cycle de vie serveur (MODEL-001) muté par le
     * gestionnaire <b>sans</b> incrémenter la révision — celle-ci compte les opérations
     * d'édition. Se fier à la révision seule ferait donc écrire l'ancien état d'activation
     * jusqu'à la prochaine édition, et un blueprint désactivé par un administrateur
     * ressusciterait au redémarrage.
     */
    private record Encoded(int revision, boolean enabled, CompoundTag tag) {
    }

    private final java.util.Map<net.minecraft.resources.Identifier, Encoded> encoded =
            new java.util.HashMap<>();

    /** Vrai si les listes vivantes ont été reconstruites — donc exclusives à ce stockage. */
    private boolean refreshFromLive() {
        if (liveManager == null || liveScheduler == null) {
            return false;
        }
        blueprints.clear();
        for (Blueprint bp : liveManager.all()) {
            Encoded hit = encoded.get(bp.id());
            if (hit == null || hit.revision() != bp.revision() || hit.enabled() != bp.enabled()) {
                hit = new Encoded(bp.revision(), bp.enabled(), GraphNbt.encode(bp));
                encoded.put(bp.id(), hit);
            }
            blueprints.add(hit.tag());
        }
        // Un blueprint supprimé ne doit pas garder son encodage en mémoire jusqu'à
        // l'arrêt du serveur. Le balayage est borné par le nombre de graphes et n'a lieu
        // qu'à la sauvegarde du monde, pas dans un chemin chaud.
        encoded.keySet().removeIf(id -> !liveManager.contains(id));
        suspended.clear();
        for (SuspendedExecution execution : liveScheduler.captureForSave()) {
            CompoundTag tag = ExecutionNbt.encode(execution);
            if (tag != null) {
                suspended.add(tag);
            }
        }
        // corrupt : jamais régénéré — préservé tel qu'arrivé.
        return true;
    }

    private static void readList(CompoundTag root, String key, List<CompoundTag> into) {
        if (root.get(key) instanceof ListTag list) {
            for (Tag tag : list) {
                if (tag instanceof CompoundTag compound) {
                    into.add(compound);
                }
            }
        }
    }

    private static ListTag writeList(List<CompoundTag> tags, boolean copy) {
        ListTag list = new ListTag();
        for (CompoundTag tag : tags) {
            list.add(copy ? tag.copy() : tag);
        }
        return list;
    }
}
