package fr.blueprint.api.pin;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/** Implémentation standard produite par {@link PinType.Builder}. */
final class BasePinType implements PinType {

    private final Identifier id;
    private final PinKind kind;
    private final Class<?> javaType;
    private final int color;
    private final PinShape shape;
    private final String translationKey;
    private final boolean supportsLiteral;
    private final @Nullable Supplier<Codec<Object>> codec;
    private final @Nullable Supplier<StreamCodec<? super RegistryFriendlyByteBuf, Object>> streamCodec;
    private final @Nullable Supplier<Object> defaultValue;
    private final Map<PinType, Function<Object, Object>> coercions;

    BasePinType(PinType.Builder b) {
        this.id = b.id;
        this.kind = b.kind;
        this.javaType = b.javaType;
        this.color = b.color;
        this.shape = b.shape;
        this.translationKey = b.translationKey;
        this.supportsLiteral = b.supportsLiteral;
        this.codec = b.codec;
        this.streamCodec = b.streamCodec;
        this.defaultValue = b.defaultValue;
        this.coercions = new LinkedHashMap<>(b.coercions);
    }

    @Override
    public Identifier id() {
        return id;
    }

    @Override
    public PinKind kind() {
        return kind;
    }

    @Override
    public Class<?> javaType() {
        return javaType;
    }

    @Override
    public int color() {
        return color;
    }

    @Override
    public PinShape shape() {
        return shape;
    }

    @Override
    public String translationKey() {
        return translationKey;
    }

    @Override
    public boolean supportsLiteral() {
        return supportsLiteral;
    }

    @Override
    public boolean hasCodec() {
        return codec != null;
    }

    @Override
    public boolean hasStreamCodec() {
        return streamCodec != null;
    }

    @Override
    public @Nullable Codec<Object> codec() {
        return codec == null ? null : codec.get();
    }

    @Override
    public @Nullable StreamCodec<? super RegistryFriendlyByteBuf, Object> streamCodec() {
        return streamCodec == null ? null : streamCodec.get();
    }

    @Override
    public @Nullable LiteralValue defaultValue() {
        return defaultValue == null ? null : LiteralValue.of(this, defaultValue.get());
    }

    @Override
    public boolean isAssignableFrom(PinType other) {
        if (this == other) {
            return true;
        }
        // Joker non résolu : le lien est permis, GenericResolution tranchera au câblage.
        if (other instanceof GenericPinType) {
            return kind == PinKind.DATA;
        }
        if (kind != other.kind()) {
            return false;
        }
        return coercionFor(other) != null;
    }

    @Override
    public @Nullable Function<Object, Object> coercionFor(PinType source) {
        return find(source, new HashSet<>());
    }

    // Parcours du graphe de coercitions déclarées (cibles → sources), en composant
    // les fonctions le long du chemin. Les chaînes sont courtes : la récursion suffit.
    private @Nullable Function<Object, Object> find(PinType source, Set<PinType> visited) {
        if (!visited.add(this)) {
            return null;
        }
        Function<Object, Object> direct = coercions.get(source);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<PinType, Function<Object, Object>> step : coercions.entrySet()) {
            if (step.getKey() instanceof BasePinType intermediate) {
                Function<Object, Object> tail = intermediate.find(source, visited);
                if (tail != null) {
                    return step.getValue().compose(tail);
                }
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
