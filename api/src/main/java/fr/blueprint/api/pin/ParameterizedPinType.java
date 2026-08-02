package fr.blueprint.api.pin;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Type paramétré : {@code list<T>} ou {@code map<K,V>}. Les paramètres sont
 * <b>invariants</b> : {@code list<int>} n'est pas assignable à {@code list<double>},
 * même si {@code int → double} l'est — la covariance créerait des pièges à
 * l'écriture. Instances uniques par (conteneur, arguments), obtenues via
 * {@link PinTypes#listOf} et {@link PinTypes#mapOf} : la comparaison par identité est valide.
 */
public final class ParameterizedPinType implements PinType {

    public enum Container {
        LIST, MAP
    }

    private static final Identifier LIST_ID = Identifier.fromNamespaceAndPath("blueprint", "list");
    private static final Identifier MAP_ID = Identifier.fromNamespaceAndPath("blueprint", "map");

    private final Container container;
    private final List<PinType> args;

    ParameterizedPinType(Container container, List<PinType> args) {
        this.container = container;
        this.args = List.copyOf(args);
    }

    public Container container() {
        return container;
    }

    public List<PinType> args() {
        return args;
    }

    @Override
    public Identifier id() {
        return container == Container.LIST ? LIST_ID : MAP_ID;
    }

    @Override
    public PinKind kind() {
        return PinKind.DATA;
    }

    @Override
    public Class<?> javaType() {
        return container == Container.LIST ? List.class : Map.class;
    }

    @Override
    public int color() {
        // La couleur du premier paramètre, la forme signale le conteneur.
        return args.get(0).color();
    }

    @Override
    public PinShape shape() {
        return container == Container.LIST ? PinShape.ARRAY : PinShape.MAP;
    }

    @Override
    public String translationKey() {
        return "blueprint.pin.blueprint." + (container == Container.LIST ? "list" : "map") + ".name";
    }

    @Override
    public boolean supportsLiteral() {
        for (PinType arg : args) {
            if (!arg.supportsLiteral()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean hasCodec() {
        for (PinType arg : args) {
            if (!arg.hasCodec()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean hasStreamCodec() {
        for (PinType arg : args) {
            if (!arg.hasStreamCodec()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public @Nullable Codec<Object> codec() {
        if (!hasCodec()) {
            return null;
        }
        return cast(container == Container.LIST
                ? args.get(0).codec().listOf()
                : Codec.unboundedMap(args.get(0).codec(), args.get(1).codec()));
    }

    @Override
    @SuppressWarnings("unchecked")
    public @Nullable StreamCodec<? super RegistryFriendlyByteBuf, Object> streamCodec() {
        if (!hasStreamCodec()) {
            return null;
        }
        if (container == Container.LIST) {
            StreamCodec<RegistryFriendlyByteBuf, Object> element =
                    (StreamCodec<RegistryFriendlyByteBuf, Object>) args.get(0).streamCodec();
            return (StreamCodec<? super RegistryFriendlyByteBuf, Object>) (StreamCodec<?, ?>)
                    element.apply(net.minecraft.network.codec.ByteBufCodecs.list());
        }
        StreamCodec<RegistryFriendlyByteBuf, Object> key =
                (StreamCodec<RegistryFriendlyByteBuf, Object>) args.get(0).streamCodec();
        StreamCodec<RegistryFriendlyByteBuf, Object> value =
                (StreamCodec<RegistryFriendlyByteBuf, Object>) args.get(1).streamCodec();
        return (StreamCodec<? super RegistryFriendlyByteBuf, Object>) (StreamCodec<?, ?>)
                net.minecraft.network.codec.ByteBufCodecs.map(java.util.HashMap::new, key, value);
    }

    @SuppressWarnings("unchecked")
    private static Codec<Object> cast(Codec<?> codec) {
        return (Codec<Object>) codec;
    }

    @Override
    public @Nullable LiteralValue defaultValue() {
        if (!supportsLiteral()) {
            return null;
        }
        return LiteralValue.of(this, container == Container.LIST ? List.of() : Map.of());
    }

    @Override
    public boolean isAssignableFrom(PinType other) {
        if (this == other) {
            return true;
        }
        if (other instanceof GenericPinType) {
            return true;
        }
        if (!(other instanceof ParameterizedPinType p) || p.container != container
                || p.args.size() != args.size()) {
            return false;
        }
        // Invariance : identité exigée, sauf joker (résolu par GenericResolution).
        for (int i = 0; i < args.size(); i++) {
            PinType a = args.get(i);
            PinType b = p.args.get(i);
            if (a != b && !(a instanceof GenericPinType) && !(b instanceof GenericPinType)
                    && !(a instanceof ParameterizedPinType && a.isAssignableFrom(b) && b.isAssignableFrom(a))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public @Nullable Function<Object, Object> coercionFor(PinType source) {
        return null;
    }

    @Override
    public String toString() {
        return (container == Container.LIST ? "list" : "map")
                + args.stream().map(Object::toString).collect(Collectors.joining(", ", "<", ">"));
    }
}
