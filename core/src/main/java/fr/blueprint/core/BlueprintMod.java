package fr.blueprint.core;

import fr.blueprint.api.BlueprintPlugin;
import fr.blueprint.core.command.BlueprintCommand;
import fr.blueprint.core.config.BlueprintConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlueprintMod implements ModInitializer {
    public static final String MOD_ID = "blueprint";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static BlueprintConfig config = BlueprintConfig.DEFAULT;
    private static fr.blueprint.core.registry.PluginLoader.LoadedRegistries registries;

    public static BlueprintConfig config() {
        return config;
    }

    /** Registres gelés du serveur (types de pins + nœuds), disponibles après l'init. */
    public static fr.blueprint.core.registry.PluginLoader.LoadedRegistries registries() {
        return registries;
    }

    @Override
    public void onInitialize() {
        LOGGER.info("Blueprint initialisé");

        config = BlueprintConfig.load(FabricLoader.getInstance().getConfigDir());
        BlueprintCommand.register(config);

        int declared = FabricLoader.getInstance()
                .getEntrypointContainers("blueprint", BlueprintPlugin.class)
                .size();
        registries = fr.blueprint.core.registry.PluginLoader.loadFromFabric();
        LOGGER.info("{} plugin(s) Blueprint détecté(s) — {} type(s) de pins, {} nœud(s), {} événement(s), {} en échec",
                declared, registries.pinTypes().all().size(), registries.nodes().all().size(),
                registries.events().all().size(), registries.failedMods().size());

        // Le dispatcher d'événements vit avec le serveur : installé au démarrage,
        // retiré à l'arrêt — avant/après, BlueprintEvents.fire est un no-op sûr.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(server ->
                fr.blueprint.api.event.BlueprintEvents.install(
                        new fr.blueprint.core.event.EventDispatcher(new fr.blueprint.core.event.EventDispatcher.ThreadGate() {
                            @Override
                            public boolean isOnThread() {
                                return server.isSameThread();
                            }

                            @Override
                            public void submit(Runnable task) {
                                server.execute(task);
                            }
                        })));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server ->
                fr.blueprint.api.event.BlueprintEvents.uninstall());

        // L'ordonnanceur tourne en fin de tick serveur ; un blueprint glouton ou en
        // faute est désactivé via le manager (l'annuler/rétablir ne le réactivera pas).
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server ->
                schedulerOf(server).tick(config.fuelPerTick()));
    }

    private static final java.util.Map<net.minecraft.server.MinecraftServer,
            fr.blueprint.core.vm.BlueprintScheduler> SCHEDULERS =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    public static fr.blueprint.core.vm.BlueprintScheduler schedulerOf(net.minecraft.server.MinecraftServer server) {
        return SCHEDULERS.computeIfAbsent(server, s ->
                new fr.blueprint.core.vm.BlueprintScheduler(config.maxOverBudgetTicks(),
                        new fr.blueprint.core.vm.BlueprintScheduler.Listener() {
                            @Override
                            public void disabled(net.minecraft.resources.Identifier blueprintId, int streakTicks) {
                                LOGGER.warn("Blueprint « {} » désactivé : budget dépassé {} ticks d'affilée",
                                        blueprintId, streakTicks);
                                BlueprintManager.of(s).setEnabled(blueprintId, false);
                            }

                            @Override
                            public void faulted(net.minecraft.resources.Identifier blueprintId,
                                                java.util.UUID node, String message) {
                                LOGGER.error("Blueprint « {} » en faute (nœud {}) : {}",
                                        blueprintId, node, message);
                                BlueprintManager.of(s).setEnabled(blueprintId, false);
                            }
                        }));
    }
}
