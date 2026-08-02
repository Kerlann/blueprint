package fr.blueprint.core.storage;

import fr.blueprint.api.event.EventType;
import fr.blueprint.core.BlueprintManager;
import fr.blueprint.core.BlueprintMod;
import fr.blueprint.core.compile.Compiler;
import fr.blueprint.core.event.BlueprintEventBridge;
import fr.blueprint.core.event.TriggerContextImpl;
import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.GraphNbt;
import fr.blueprint.core.registry.PluginLoader;
import fr.blueprint.core.vm.BlueprintScheduler;
import fr.blueprint.core.vm.ExecutionNbt;
import fr.blueprint.core.vm.RefResolver;
import fr.blueprint.core.vm.SuspendedExecution;
import net.minecraft.nbt.CompoundTag;

/**
 * La restauration au chargement du monde (story 6.1) : décoder les blueprints (les
 * indécodables restent préservés bruts), reprendre les exécutions suspendues (les
 * irrésolubles sont annulées proprement), et produire un rapport — jamais un crash.
 * Logique pure et testable ; l'attache serveur vit dans {@code BlueprintMod}.
 */
public final class PersistenceHooks {

    /** Le rapport de chargement (AC2) — journalisé au démarrage. */
    public record RestoreReport(int blueprintsLoaded, int blueprintsCorrupt,
                                int executionsResumed, int executionsCancelled) {
    }

    private PersistenceHooks() {
    }

    public static RestoreReport restore(BlueprintStorage storage, BlueprintManager manager,
                                        BlueprintScheduler scheduler,
                                        PluginLoader.LoadedRegistries registries,
                                        RefResolver resolver,
                                        BlueprintEventBridge.EnvFactory envFactory) {
        int loaded = 0;
        int corrupt = 0;
        for (CompoundTag tag : storage.blueprintTags()) {
            Blueprint bp = null;
            try {
                bp = GraphNbt.decode(tag, typeId -> registries.pinTypes().get(typeId).orElse(null));
            } catch (RuntimeException e) {
                BlueprintMod.LOGGER.warn("Blueprint indécodable — préservé brut", e);
            }
            if (bp == null || !manager.adopt(bp)) {
                storage.corruptTags().add(tag.copy());
                corrupt++;
            } else {
                loaded++;
            }
        }

        int resumed = 0;
        int cancelled = 0;
        for (CompoundTag tag : storage.suspendedTags()) {
            if (resumeOne(tag, manager, scheduler, registries, resolver, envFactory)) {
                resumed++;
            } else {
                cancelled++;
            }
        }
        return new RestoreReport(loaded, corrupt, resumed, cancelled);
    }

    private static boolean resumeOne(CompoundTag tag, BlueprintManager manager,
                                     BlueprintScheduler scheduler,
                                     PluginLoader.LoadedRegistries registries,
                                     RefResolver resolver,
                                     BlueprintEventBridge.EnvFactory envFactory) {
        SuspendedExecution suspended = ExecutionNbt.decode(tag, resolver);
        if (suspended == null || suspended.entryNode() == null) {
            return false;                                    // réf morte ou IR non persistable
        }
        Blueprint bp = manager.get(suspended.blueprintId()).orElse(null);
        if (bp == null || !bp.enabled()) {
            return false;                                    // blueprint disparu ou désactivé
        }
        if (bp.revision() != suspended.revision()) {
            BlueprintMod.LOGGER.info(
                    "Exécution suspendue de « {} » annulée : le blueprint a changé (révision {} → {})",
                    bp.id(), suspended.revision(), bp.revision());
            return false;                                    // l'IR ne correspondrait plus
        }
        EventType event = registries.events().get(suspended.eventId()).orElse(null);
        if (event == null) {
            return false;                                    // événement disparu (mod retiré)
        }
        Compiler.CompileResult result = Compiler.compile(bp, registries.nodes(), suspended.entryNode());
        if (!result.success()) {
            return false;                                    // fantômes apparus entre-temps
        }
        var trigger = new TriggerContextImpl(event, suspended.triggerValues());
        scheduler.resume(suspended, result.ir(), envFactory.create(bp, trigger));
        return true;
    }
}
