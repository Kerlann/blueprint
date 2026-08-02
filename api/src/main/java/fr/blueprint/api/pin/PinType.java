package fr.blueprint.api.pin;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Type d'un pin de données (ou d'exécution). Les instances sont immuables et,
 * pour les types paramétrés et génériques obtenus via {@link PinTypes}, uniques :
 * la comparaison par identité est valide.
 *
 * <p>Un mod tiers déclare un type via {@link #builder(Identifier)} :
 * <pre>{@code
 * PinType MANA = PinType.builder(Identifier.fromNamespaceAndPath("mymod", "mana"))
 *     .javaType(ManaPool.class)
 *     .color(0xFF4FC3F7)
 *     .shape(PinShape.DIAMOND)
 *     .codec(ManaPool.CODEC)
 *     .streamCodec(ManaPool.STREAM_CODEC)
 *     .coerceFrom(PinTypes.INT, v -> new ManaPool(((Number) v).intValue()))
 *     .build();
 * }</pre>
 */
public interface PinType {

    Identifier id();

    PinKind kind();

    /** Type Java des valeurs transportées (forme boxée pour les primitifs). */
    Class<?> javaType();

    /** Couleur ARGB du pin et de ses liens. */
    int color();

    PinShape shape();

    String translationKey();

    /**
     * Faux pour les types qui ne peuvent pas porter de valeur littérale sur un pin
     * non connecté : {@code exec}, les jokers, et les références vivantes
     * ({@code entity}, {@code player}).
     */
    boolean supportsLiteral();

    /** Vrai si un codec de littéral est déclaré — sans forcer sa résolution (types paresseux). */
    boolean hasCodec();

    boolean hasStreamCodec();

    /**
     * Codec des littéraux. Résolu paresseusement : les types adossés aux registres du
     * jeu ({@code itemstack}, {@code blockstate}…) ne touchent aucun registre avant le
     * premier appel. Null si {@link #supportsLiteral()} est faux.
     */
    @Nullable Codec<Object> codec();

    @Nullable StreamCodec<? super RegistryFriendlyByteBuf, Object> streamCodec();

    /** Valeur par défaut d'un pin d'entrée non connecté, ou null s'il n'y en a pas. */
    @Nullable LiteralValue defaultValue();

    /**
     * Vrai si une valeur de {@code other} peut alimenter un pin de ce type :
     * identité, ou coercition implicite déclarée (transitive). Les jokers non
     * résolus sont acceptés — la résolution est l'affaire de {@link GenericResolution}.
     */
    /** Vrai pour un joker ({@code any}) ou un emplacement générique nommé ({@code T}). */
    default boolean isGeneric() {
        return false;
    }

    boolean isAssignableFrom(PinType other);

    /**
     * Fonction de conversion depuis {@code source} (composée le long de la chaîne de
     * coercitions déclarées), ou null si aucune n'y mène. Identité exclue : pour
     * {@code source == this}, la réponse est null.
     */
    @Nullable Function<Object, Object> coercionFor(PinType source);

    static Builder builder(Identifier id) {
        return new Builder(id);
    }

    /** Construit un {@link PinType} immuable. Toute incohérence lève à {@code build()}. */
    final class Builder {
        final Identifier id;
        PinKind kind = PinKind.DATA;
        Class<?> javaType;
        int color = 0xFFABB2BF;
        PinShape shape = PinShape.CIRCLE;
        String translationKey;
        boolean supportsLiteral = true;
        @Nullable Supplier<Codec<Object>> codec;
        @Nullable Supplier<StreamCodec<? super RegistryFriendlyByteBuf, Object>> streamCodec;
        @Nullable Supplier<Object> defaultValue;
        final Map<PinType, Function<Object, Object>> coercions = new LinkedHashMap<>();

        Builder(Identifier id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public Builder kind(PinKind kind) {
            this.kind = kind;
            return this;
        }

        public Builder javaType(Class<?> javaType) {
            this.javaType = javaType;
            return this;
        }

        public Builder color(int argb) {
            this.color = argb;
            return this;
        }

        public Builder shape(PinShape shape) {
            this.shape = shape;
            return this;
        }

        public Builder translationKey(String key) {
            this.translationKey = key;
            return this;
        }

        /** Le type ne porte jamais de littéral : codec facultatif. */
        public Builder noLiteral() {
            this.supportsLiteral = false;
            return this;
        }

        @SuppressWarnings("unchecked")
        public Builder codec(Codec<?> codec) {
            Objects.requireNonNull(codec, "codec");
            this.codec = () -> (Codec<Object>) codec;
            return this;
        }

        /** Variante paresseuse pour les codecs adossés aux registres du jeu. */
        @SuppressWarnings("unchecked")
        public Builder lazyCodec(Supplier<? extends Codec<?>> codec) {
            Objects.requireNonNull(codec, "codec");
            this.codec = memoize(() -> (Codec<Object>) codec.get());
            return this;
        }

        @SuppressWarnings("unchecked")
        public Builder streamCodec(StreamCodec<? super RegistryFriendlyByteBuf, ?> codec) {
            Objects.requireNonNull(codec, "streamCodec");
            this.streamCodec = () -> (StreamCodec<? super RegistryFriendlyByteBuf, Object>) codec;
            return this;
        }

        @SuppressWarnings("unchecked")
        public Builder lazyStreamCodec(Supplier<? extends StreamCodec<? super RegistryFriendlyByteBuf, ?>> codec) {
            Objects.requireNonNull(codec, "streamCodec");
            this.streamCodec = memoize(() -> (StreamCodec<? super RegistryFriendlyByteBuf, Object>) codec.get());
            return this;
        }

        /** Valeur par défaut, fournie paresseusement (ne jamais toucher un registre ici). */
        public Builder defaultValue(Supplier<Object> value) {
            this.defaultValue = value;
            return this;
        }

        /** Déclare une coercition implicite {@code source → this}. */
        public Builder coerceFrom(PinType source, Function<Object, Object> fn) {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(fn, "fn");
            if (coercions.put(source, fn) != null) {
                throw new IllegalStateException("Coercition déclarée deux fois depuis " + source.id() + " vers " + id);
            }
            return this;
        }

        public PinType build() {
            if (javaType == null) {
                throw new IllegalStateException("Type de pin « " + id + " » incomplet : javaType manquant");
            }
            if (kind == PinKind.EXEC && (supportsLiteral || !coercions.isEmpty())) {
                throw new IllegalStateException("Type de pin « " + id + " » : un type exec ne porte ni littéral ni coercition");
            }
            if (translationKey == null) {
                translationKey = "blueprint.pin." + id.getNamespace() + "." + id.getPath() + ".name";
            }
            return new BasePinType(this);
        }

        private static <T> Supplier<T> memoize(Supplier<T> source) {
            return new Supplier<>() {
                private volatile T value;

                @Override
                public T get() {
                    T local = value;
                    if (local == null) {
                        synchronized (this) {
                            local = value;
                            if (local == null) {
                                local = Objects.requireNonNull(source.get());
                                value = local;
                            }
                        }
                    }
                    return local;
                }
            };
        }
    }
}
