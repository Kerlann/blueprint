package fr.blueprint.core.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Paquets de la synchro registre (story 6.2). Enregistrés dans {@code BlueprintMod}
 * (types) et gérés côté client dans {@code client/net} — toute entrée est bornée.
 */
public final class BlueprintPayloads {

    private BlueprintPayloads() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("blueprint", path);
    }

    /** S2C au join : le hash du registre serveur — le client demande s'il diverge (FR35). */
    public record RegistryHash(String hash) implements CustomPacketPayload {
        public static final Type<RegistryHash> TYPE = new Type<>(id("registry_hash"));
        public static final StreamCodec<ByteBuf, RegistryHash> CODEC =
                ByteBufCodecs.STRING_UTF8.map(RegistryHash::new, RegistryHash::hash);

        @Override
        public Type<RegistryHash> type() {
            return TYPE;
        }
    }

    /** C2S : le client demande les descripteurs complets. */
    public record RegistryRequest(int nonce) implements CustomPacketPayload {
        public static final Type<RegistryRequest> TYPE = new Type<>(id("registry_request"));
        public static final StreamCodec<ByteBuf, RegistryRequest> CODEC =
                ByteBufCodecs.VAR_INT.map(RegistryRequest::new, RegistryRequest::nonce);

        @Override
        public Type<RegistryRequest> type() {
            return TYPE;
        }
    }

    /** C2S : la liste des blueprints du serveur (alimente /blueprint-edit en multi). */
    public record ListRequest(int nonce) implements CustomPacketPayload {
        public static final Type<ListRequest> TYPE = new Type<>(id("bp_list_request"));
        public static final StreamCodec<ByteBuf, ListRequest> CODEC =
                ByteBufCodecs.VAR_INT.map(ListRequest::new, ListRequest::nonce);

        @Override
        public Type<ListRequest> type() {
            return TYPE;
        }
    }

    /** S2C : identifiants des blueprints visibles, et si le joueur peut les modifier. */
    public record ListData(java.util.List<Identifier> ids, boolean writable)
            implements CustomPacketPayload {
        public static final Type<ListData> TYPE = new Type<>(id("bp_list"));
        public static final StreamCodec<ByteBuf, ListData> CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LIST)), ListData::ids,
                ByteBufCodecs.BOOL, ListData::writable,
                ListData::new);

        @Override
        public Type<ListData> type() {
            return TYPE;
        }
    }

    /** C2S : demande d'ouverture d'un blueprint pour édition. */
    public record OpenRequest(Identifier blueprint) implements CustomPacketPayload {
        public static final Type<OpenRequest> TYPE = new Type<>(id("bp_open_request"));
        public static final StreamCodec<ByteBuf, OpenRequest> CODEC =
                Identifier.STREAM_CODEC.map(OpenRequest::new, OpenRequest::blueprint);

        @Override
        public Type<OpenRequest> type() {
            return TYPE;
        }
    }

    /** C2S : création puis ouverture immédiate (/blueprint-edit create). */
    public record CreateRequest(Identifier blueprint) implements CustomPacketPayload {
        public static final Type<CreateRequest> TYPE = new Type<>(id("bp_create_request"));
        public static final StreamCodec<ByteBuf, CreateRequest> CODEC =
                Identifier.STREAM_CODEC.map(CreateRequest::new, CreateRequest::blueprint);

        @Override
        public Type<CreateRequest> type() {
            return TYPE;
        }
    }

    /**
     * S2C : le graphe demandé (ou renvoyé après un refus — resynchro ciblée).
     * {@code revision} est le verrou optimiste : c'est lui que le client renverra.
     */
    public record GraphData(Identifier blueprint, int revision, boolean writable, byte[] data)
            implements CustomPacketPayload {
        public static final Type<GraphData> TYPE = new Type<>(id("bp_graph"));
        public static final StreamCodec<ByteBuf, GraphData> CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, GraphData::blueprint,
                ByteBufCodecs.VAR_INT, GraphData::revision,
                ByteBufCodecs.BOOL, GraphData::writable,
                ByteBufCodecs.byteArray(MAX_GRAPH_BYTES), GraphData::data,
                GraphData::new);

        @Override
        public Type<GraphData> type() {
            return TYPE;
        }
    }

    /** C2S : enregistrement sous verrou optimiste — {@code baseRevision} = révision ouverte. */
    public record SaveRequest(Identifier blueprint, int baseRevision, byte[] data)
            implements CustomPacketPayload {
        public static final Type<SaveRequest> TYPE = new Type<>(id("bp_save_request"));
        public static final StreamCodec<ByteBuf, SaveRequest> CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, SaveRequest::blueprint,
                ByteBufCodecs.VAR_INT, SaveRequest::baseRevision,
                ByteBufCodecs.byteArray(MAX_GRAPH_BYTES), SaveRequest::data,
                SaveRequest::new);

        @Override
        public Type<SaveRequest> type() {
            return TYPE;
        }
    }

    /** Verdict d'un enregistrement. Ordinal transmis : jamais de nom de classe sur le fil. */
    public enum SaveStatus {
        SAVED, CONFLICT, UNKNOWN, DENIED, INVALID
    }

    /** S2C : verdict + révision courante du serveur (le client se recale dessus). */
    public record SaveAck(Identifier blueprint, SaveStatus status, int revision)
            implements CustomPacketPayload {
        public static final Type<SaveAck> TYPE = new Type<>(id("bp_save_ack"));
        public static final StreamCodec<ByteBuf, SaveAck> CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, SaveAck::blueprint,
                ByteBufCodecs.VAR_INT.map(SaveAck::statusOf, s -> s.ordinal()), SaveAck::status,
                ByteBufCodecs.VAR_INT, SaveAck::revision,
                SaveAck::new);

        /** Un ordinal inconnu (client plus récent, paquet forgé) vaut INVALID. */
        private static SaveStatus statusOf(int ordinal) {
            SaveStatus[] all = SaveStatus.values();
            return ordinal >= 0 && ordinal < all.length ? all[ordinal] : SaveStatus.INVALID;
        }

        @Override
        public Type<SaveAck> type() {
            return TYPE;
        }
    }

    /** C2S : bouton Tester — active le blueprint côté serveur après enregistrement. */
    public record SetEnabled(Identifier blueprint, boolean enabled) implements CustomPacketPayload {
        public static final Type<SetEnabled> TYPE = new Type<>(id("bp_set_enabled"));
        public static final StreamCodec<ByteBuf, SetEnabled> CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, SetEnabled::blueprint,
                ByteBufCodecs.BOOL, SetEnabled::enabled,
                SetEnabled::new);

        @Override
        public Type<SetEnabled> type() {
            return TYPE;
        }
    }

    /** Bornes du fil : un paquet plus gros est rejeté par le décodeur, pas par nous. */
    public static final int MAX_GRAPH_BYTES = fr.blueprint.core.net.GraphSync.MAX_BYTES;
    public static final int MAX_LIST = 4_096;

    /** S2C : un fragment du flux de descripteurs compressé. */
    public record DescriptorChunk(int index, int total, byte[] data)
            implements CustomPacketPayload {
        public static final Type<DescriptorChunk> TYPE = new Type<>(id("descriptor_chunk"));
        public static final StreamCodec<ByteBuf, DescriptorChunk> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, DescriptorChunk::index,
                ByteBufCodecs.VAR_INT, DescriptorChunk::total,
                ByteBufCodecs.BYTE_ARRAY, DescriptorChunk::data,
                DescriptorChunk::new);

        @Override
        public Type<DescriptorChunk> type() {
            return TYPE;
        }
    }
}
