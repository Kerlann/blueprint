package fr.blueprint.api.pin;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Valeur littérale typée portée par un pin d'entrée non connecté.
 * Immuable ; la valeur est toujours une instance de {@code type().javaType()}.
 */
public final class LiteralValue {

    private final PinType type;
    private final Object value;

    private LiteralValue(PinType type, Object value) {
        this.type = type;
        this.value = value;
    }

    /**
     * @throws IllegalArgumentException si le type ne supporte pas les littéraux ou si
     *         la valeur n'est pas du bon type Java — erreur de développeur, jamais d'utilisateur.
     */
    public static LiteralValue of(PinType type, Object value) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(value, "value");
        if (!type.supportsLiteral()) {
            throw new IllegalArgumentException("Le type « " + type.id() + " » ne porte pas de littéral");
        }
        if (!type.javaType().isInstance(value)) {
            throw new IllegalArgumentException("Littéral invalide pour « " + type.id() + " » : attendu "
                    + type.javaType().getSimpleName() + ", reçu " + value.getClass().getSimpleName());
        }
        return new LiteralValue(type, value);
    }

    public PinType type() {
        return type;
    }

    public Object value() {
        return value;
    }

    /** Sérialise via le codec du type (NBT, JSON… selon {@code ops}). */
    public <T> DataResult<T> encode(DynamicOps<T> ops) {
        var codec = type.codec();
        if (codec == null) {
            return DataResult.error(() -> "Pas de codec pour le type « " + type.id() + " »");
        }
        return codec.encodeStart(ops, value);
    }

    public static <T> DataResult<LiteralValue> decode(PinType type, DynamicOps<T> ops, T input) {
        var codec = type.codec();
        if (codec == null) {
            return DataResult.error(() -> "Pas de codec pour le type « " + type.id() + " »");
        }
        return codec.parse(ops, input).map(v -> LiteralValue.of(type, v));
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        return obj instanceof LiteralValue other && type == other.type && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return 31 * System.identityHashCode(type) + value.hashCode();
    }

    @Override
    public String toString() {
        return type + "=" + value;
    }
}
