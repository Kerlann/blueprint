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
        refreshFromLive();
        CompoundTag root = new CompoundTag();
        root.put("blueprints", writeList(blueprints));
        root.put("suspended", writeList(suspended));
        root.put("corrupt", writeList(corrupt));
        return root;
    }

    private void refreshFromLive() {
        if (liveManager == null || liveScheduler == null) {
            return;
        }
        blueprints.clear();
        for (Blueprint bp : liveManager.all()) {
            blueprints.add(GraphNbt.encode(bp));
        }
        suspended.clear();
        for (SuspendedExecution execution : liveScheduler.captureForSave()) {
            CompoundTag tag = ExecutionNbt.encode(execution);
            if (tag != null) {
                suspended.add(tag);
            }
        }
        // corrupt : jamais régénéré — préservé tel qu'arrivé.
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

    private static ListTag writeList(List<CompoundTag> tags) {
        ListTag list = new ListTag();
        for (CompoundTag tag : tags) {
            list.add(tag.copy());
        }
        return list;
    }
}
