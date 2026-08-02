package fr.blueprint.core;

import fr.blueprint.core.graph.Blueprint;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Les blueprints d'un serveur. Cycle de vie uniquement — la persistance
 * ({@code SavedData}, rapport de chargement) arrive avec la story 6.1.
 *
 * <p>MODEL-001 : {@code enabled} est un état de cycle de vie serveur (FR20), muté ici
 * et seulement ici — jamais par une {@code EditOperation} ; l'annuler/rétablir de
 * l'éditeur ne le touche pas.
 */
public final class BlueprintManager {

    private static final Map<MinecraftServer, BlueprintManager> BY_SERVER =
            Collections.synchronizedMap(new WeakHashMap<>());

    public static BlueprintManager of(MinecraftServer server) {
        return BY_SERVER.computeIfAbsent(server, s -> new BlueprintManager());
    }

    private final Map<Identifier, Blueprint> blueprints = new LinkedHashMap<>();

    /** Vide si l'identifiant est déjà pris. */
    public Optional<Blueprint> create(Identifier id) {
        if (blueprints.containsKey(id)) {
            return Optional.empty();
        }
        Blueprint bp = new Blueprint(id);
        blueprints.put(id, bp);
        return Optional.of(bp);
    }

    /** Vrai si un blueprint a été supprimé. */
    public boolean delete(Identifier id) {
        return blueprints.remove(id) != null;
    }

    public Optional<Blueprint> get(Identifier id) {
        return Optional.ofNullable(blueprints.get(id));
    }

    /** Vrai si le blueprint existe (l'état est appliqué), faux sinon. */
    public boolean setEnabled(Identifier id, boolean enabled) {
        Blueprint bp = blueprints.get(id);
        if (bp == null) {
            return false;
        }
        bp.setEnabled(enabled);
        return true;
    }

    public Collection<Blueprint> all() {
        return Collections.unmodifiableCollection(blueprints.values());
    }
}
