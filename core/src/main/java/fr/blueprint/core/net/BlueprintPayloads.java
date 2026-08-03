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
