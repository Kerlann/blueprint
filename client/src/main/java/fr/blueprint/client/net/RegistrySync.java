package fr.blueprint.client.net;

import fr.blueprint.api.pin.PinType;
import fr.blueprint.client.registry.ClientNodeRegistry;
import fr.blueprint.core.BlueprintMod;
import fr.blueprint.core.graph.NodeTypeLookup;
import fr.blueprint.core.net.BlueprintPayloads;
import fr.blueprint.core.net.DescriptorSync;
import fr.blueprint.core.registry.NodeDescriptor;
import fr.blueprint.core.registry.RegistryHash;
import fr.blueprint.platform.Platform;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Réception du registre serveur (story 6.2). Au join, le serveur annonce son hash :
 * s'il correspond au registre local, rien ne circule (FR35) ; sinon le client demande
 * les descripteurs, reçus en fragments compressés puis assemblés.
 *
 * <p>L'éditeur consulte {@link #descriptors()} et {@link #lookup()} : en solo ou quand
 * les registres concordent, ce sont les registres locaux ; sur un serveur qui a des
 * mods absents du client, ce sont les descripteurs reçus — les nœuds de ces mods
 * s'affichent et se câblent comme les autres (AC3).
 */
public final class RegistrySync {

    private RegistrySync() {
    }

    private static final Map<Integer, byte[]> CHUNKS = new ConcurrentHashMap<>();
    private static volatile int expected = -1;
    private static volatile @Nullable ClientNodeRegistry synced;
    private static volatile @Nullable NodeTypeLookup syncedLookup;

    /** Types de pins inconnus du client, gardés opaques et partagés (identité valide). */
    private static final Map<Identifier, PinType> OPAQUE = new ConcurrentHashMap<>();

    public static void register() {
        var network = Platform.clientNetwork();
        network.receive(BlueprintPayloads.RegistryHash.TYPE,
                (payload, context) -> {
                    String local = RegistryHash.of(BlueprintMod.registries().nodes());
                    if (local.equals(payload.hash())) {
                        BlueprintMod.LOGGER.info(
                                "Registre serveur identique au registre local — pas de synchro");
                        return;
                    }
                    BlueprintMod.LOGGER.info(
                            "Registre serveur divergent ({}≠{}) — demande des descripteurs",
                            payload.hash().substring(0, 8), local.substring(0, 8));
                    reset();
                    context.reply(new BlueprintPayloads.RegistryRequest(0));
                });

        network.receive(BlueprintPayloads.DescriptorChunk.TYPE,
                (payload, context) -> {
                    expected = payload.total();
                    CHUNKS.put(payload.index(), payload.data());
                    if (CHUNKS.size() < expected) {
                        return;
                    }
                    List<byte[]> ordered = new ArrayList<>(expected);
                    for (int i = 0; i < expected; i++) {
                        byte[] chunk = CHUNKS.get(i);
                        if (chunk == null) {
                            // Fragment manquant : on attend, un renvoi complétera.
                            return;
                        }
                        ordered.add(chunk);
                    }
                    adopt(DescriptorSync.fromBytes(DescriptorSync.reassemble(ordered),
                            RegistrySync::resolvePinType));
                    CHUNKS.clear();
                });

    }

    /** Déconnexion : le registre reçu du serveur quitté n'a plus cours. */
    public static void onDisconnect() {
        reset();
    }

    /** Construit le registre synchronisé — pur, testable sans réseau. */
    public static void adopt(List<NodeDescriptor> received) {
        ClientNodeRegistry registry = ClientNodeRegistry.of(received);
        synced = registry;
        syncedLookup = registry.lookup();
        BlueprintMod.LOGGER.info("Registre serveur synchronisé : {} nœud(s)", received.size());
    }

    public static void reset() {
        CHUNKS.clear();
        expected = -1;
        synced = null;
        syncedLookup = null;
    }

    /** Descripteurs à afficher : ceux du serveur s'ils ont été reçus, sinon les locaux. */
    public static ClientNodeRegistry descriptors() {
        ClientNodeRegistry current = synced;
        return current != null ? current
                : ClientNodeRegistry.fromLocal(BlueprintMod.registries());
    }

    /** Vue validateur correspondante — jamais un mélange des deux sources. */
    public static NodeTypeLookup lookup() {
        NodeTypeLookup current = syncedLookup;
        return current != null ? current : BlueprintMod.registries().nodes();
    }

    /**
     * Résolution d'un type de pin reçu : le type local d'abord ; à défaut (mod absent
     * du client) un type <i>opaque</i> — même identifiant, sans littéral, compatible
     * avec lui-même seulement. Le nœud reste affichable et câblable au lieu d'être
     * rejeté en bloc.
     */
    private static @Nullable PinType resolvePinType(Identifier id) {
        PinType local = BlueprintMod.registries().pinTypes().get(id).orElse(null);
        if (local != null) {
            return local;
        }
        return OPAQUE.computeIfAbsent(id, key -> PinType.builder(key)
                .javaType(Object.class)
                .noLiteral()
                .build());
    }

    /** Vue de test : les types opaques fabriqués faute de mod côté client. */
    public static Map<Identifier, PinType> opaqueTypes() {
        return new HashMap<>(OPAQUE);
    }
}
