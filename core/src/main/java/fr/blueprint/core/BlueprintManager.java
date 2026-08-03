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

    /** Adopte un blueprint existant (import, démo) ; faux si l'identifiant est pris. */
    public boolean adopt(Blueprint blueprint) {
        return blueprints.putIfAbsent(blueprint.id(), blueprint) == null;
    }

    public Optional<Blueprint> get(Identifier id) {
        return Optional.ofNullable(blueprints.get(id));
    }

    /** Verdict d'un enregistrement sous verrou optimiste (story 6.3). */
    public enum SaveOutcome {
        /** Instantané adopté, révision incrémentée. */
        SAVED,
        /** Le serveur a bougé depuis l'ouverture : rien n'est écrasé. */
        CONFLICT,
        /** Plus aucun blueprint sous cet identifiant. */
        UNKNOWN
    }

    /** Verdict et révision COURANTE du serveur — le client se recale dessus. */
    public record SaveResult(SaveOutcome outcome, int revision) {
    }

    /**
     * Enregistre un instantané sous verrou optimiste : {@code baseRevision} est la
     * révision servie à l'ouverture. Si elle a bougé, rien n'est écrit — le travail
     * de l'autre éditeur survit, et le client reçoit de quoi se recaler (AC3/AC4).
     *
     * <p>MODEL-001 : {@code enabled} est un état de cycle de vie serveur — l'instantané
     * ne le transporte pas, il hérite de celui en place.
     */
    public SaveResult save(Blueprint snapshot, int baseRevision) {
        Blueprint current = blueprints.get(snapshot.id());
        if (current == null) {
            return new SaveResult(SaveOutcome.UNKNOWN, -1);
        }
        if (current.revision() != baseRevision) {
            return new SaveResult(SaveOutcome.CONFLICT, current.revision());
        }
        snapshot.setEnabled(current.enabled());
        snapshot.adoptRevision(baseRevision + 1);
        blueprints.put(snapshot.id(), snapshot);
        return new SaveResult(SaveOutcome.SAVED, snapshot.revision());
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
