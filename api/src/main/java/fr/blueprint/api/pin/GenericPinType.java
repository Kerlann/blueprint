package fr.blueprint.api.pin;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.Function;

/**
 * Type joker : {@code any}, ou un emplacement générique nommé ({@code T}) d'un nœud.
 * Deux pins portant le même nom d'emplacement forment un groupe : résoudre l'un
 * résout l'autre (via {@link GenericResolution}). Instances uniques par nom,
 * obtenues via {@link PinTypes#generic(String)}.
 */
final class GenericPinType implements PinType {

    private final String name;
    private final Identifier id;

    GenericPinType(String name) {
        this.name = name;
        // Le chemin d'un Identifier est en minuscules ; le nom du slot ("T") reste
        // porté par name() — l'id ne sert qu'à l'affichage et au diagnostic.
        this.id = Identifier.fromNamespaceAndPath("blueprint",
                "any".equals(name) ? "any" : "generic_" + name.toLowerCase(Locale.ROOT));
    }

    /** Nom de l'emplacement générique ({@code "any"}, {@code "T"}…). */
    String name() {
        return name;
    }

    @Override
    public Identifier id() {
        return id;
    }

    @Override
    public PinKind kind() {
        return PinKind.DATA;
    }

    @Override
    public Class<?> javaType() {
        return Object.class;
    }

    @Override
    public int color() {
        return 0xFFABB2BF;
    }

    @Override
    public PinShape shape() {
        // Anneau : le joker doit rester distinguable des types concrets même en
        // deutéranopie, où son gris se rapproche du vert et du violet (NFR11, 9.4).
        return PinShape.RING;
    }

    @Override
    public String translationKey() {
        return "blueprint.pin.blueprint.any.name";
    }

    @Override
    public boolean isGeneric() {
        return true;
    }

    @Override
    public String genericName() {
        return name;
    }

    @Override
    public boolean supportsLiteral() {
        return false;
    }

    @Override
    public boolean hasCodec() {
        return false;
    }

    @Override
    public boolean hasStreamCodec() {
        return false;
    }

    @Override
    public @Nullable Codec<Object> codec() {
        return null;
    }

    @Override
    public @Nullable StreamCodec<? super RegistryFriendlyByteBuf, Object> streamCodec() {
        return null;
    }

    @Override
    public @Nullable LiteralValue defaultValue() {
        return null;
    }

    @Override
    public boolean isAssignableFrom(PinType other) {
        return other.kind() == PinKind.DATA;
    }

    @Override
    public @Nullable Function<Object, Object> coercionFor(PinType source) {
        return null;
    }

    @Override
    public String toString() {
        return name;
    }
}
