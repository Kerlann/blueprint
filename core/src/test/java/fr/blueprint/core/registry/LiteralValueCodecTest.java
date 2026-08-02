package fr.blueprint.core.registry;

import fr.blueprint.api.pin.LiteralValue;
import fr.blueprint.api.pin.PinType;
import fr.blueprint.api.pin.PinTypes;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip des littéraux (story 1.2, AC4). Les types adossés aux registres du jeu
 * ({@code itemstack}, {@code blockstate}, {@code text}) sont paresseux et ne peuvent
 * pas se round-tripper sans bootstrap Minecraft : leur codec est exercé par les
 * gametests (épic 7) ; ici on vérifie seulement qu'ils sont déclarés.
 */
class LiteralValueCodecTest {

    static Stream<Arguments> nbtRoundTrips() {
        return Stream.of(
                Arguments.of(PinTypes.BOOL, true),
                Arguments.of(PinTypes.INT, 42),
                Arguments.of(PinTypes.LONG, 1234567890123L),
                Arguments.of(PinTypes.DOUBLE, 3.5),
                Arguments.of(PinTypes.STRING, "château"),
                Arguments.of(PinTypes.VEC3, new Vec3(1.5, -2.0, 64.25)),
                Arguments.of(PinTypes.BLOCKPOS, new BlockPos(10, -60, 300)),
                Arguments.of(PinTypes.DIRECTION, Direction.UP),
                Arguments.of(PinTypes.RESOURCE_LOCATION, Identifier.fromNamespaceAndPath("mymod", "clef")),
                Arguments.of(PinTypes.listOf(PinTypes.INT), List.of(1, 2, 3)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nbtRoundTrips")
    void nbtRoundTrip(PinType type, Object value) {
        LiteralValue literal = LiteralValue.of(type, value);
        Tag encoded = literal.encode(NbtOps.INSTANCE).getOrThrow();
        LiteralValue decoded = LiteralValue.decode(type, NbtOps.INSTANCE, encoded).getOrThrow();
        assertEquals(literal, decoded);
    }

    static Stream<Arguments> streamRoundTrips() {
        return Stream.of(
                Arguments.of(PinTypes.BOOL, false),
                Arguments.of(PinTypes.INT, -7),
                Arguments.of(PinTypes.LONG, Long.MAX_VALUE),
                Arguments.of(PinTypes.DOUBLE, 0.5),
                Arguments.of(PinTypes.STRING, "épée"),
                Arguments.of(PinTypes.VEC3, new Vec3(0.0, 80.0, -12.5)),
                Arguments.of(PinTypes.BLOCKPOS, new BlockPos(-1, 0, 1)),
                Arguments.of(PinTypes.DIRECTION, Direction.EAST),
                Arguments.of(PinTypes.RESOURCE_LOCATION, Identifier.fromNamespaceAndPath("blueprint", "test")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("streamRoundTrips")
    @SuppressWarnings("unchecked")
    void streamRoundTrip(PinType type, Object value) {
        // Les stream codecs des types simples opèrent sur (Friendly)ByteBuf ; le cast
        // est sûr pour ceux de cette liste (aucun n'exige un contexte de registre).
        var codec = (StreamCodec<FriendlyByteBuf, Object>) (StreamCodec<?, ?>) type.streamCodec();
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        codec.encode(buf, value);
        assertEquals(value, codec.decode(buf));
        assertEquals(0, buf.readableBytes(), "octets restants après décodage");
    }

    @Test
    void literalRejectsWrongJavaType() {
        assertThrows(IllegalArgumentException.class, () -> LiteralValue.of(PinTypes.INT, "pas un entier"));
        assertThrows(IllegalArgumentException.class, () -> LiteralValue.of(PinTypes.BOOL, 1));
    }

    @Test
    void literalRejectsNoLiteralTypes() {
        assertThrows(IllegalArgumentException.class, () -> LiteralValue.of(PinTypes.EXEC, new Object()));
        assertThrows(IllegalArgumentException.class, () -> LiteralValue.of(PinTypes.ANY, 1));
        assertThrows(IllegalArgumentException.class, () -> LiteralValue.of(PinTypes.PLAYER, new Object()));
    }

    @Test
    void defaultsMatchTheirTypes() {
        assertEquals(0, PinTypes.INT.defaultValue().value());
        assertEquals(false, PinTypes.BOOL.defaultValue().value());
        assertEquals("", PinTypes.STRING.defaultValue().value());
        assertEquals(Vec3.ZERO, PinTypes.VEC3.defaultValue().value());
        assertEquals(BlockPos.ZERO, PinTypes.BLOCKPOS.defaultValue().value());
        assertNull(PinTypes.EXEC.defaultValue());
        assertNull(PinTypes.PLAYER.defaultValue());
    }

    @Test
    void lazyTypesDeclareTheirCodecsWithoutResolvingThem() {
        // hasCodec() ne force pas la résolution : aucun registre du jeu n'est touché.
        assertTrue(PinTypes.ITEMSTACK.hasCodec());
        assertTrue(PinTypes.ITEMSTACK.hasStreamCodec());
        assertTrue(PinTypes.BLOCKSTATE.hasCodec());
        assertTrue(PinTypes.TEXT.hasCodec());
        assertTrue(PinTypes.TEXT.hasStreamCodec());
    }

    @Test
    void listCodecComposesFromElementCodec() {
        assertTrue(PinTypes.listOf(PinTypes.INT).hasCodec());
        // list<entity> : pas de littéral, pas de codec — cohérent.
        assertEquals(false, PinTypes.listOf(PinTypes.ENTITY).supportsLiteral());
        assertEquals(false, PinTypes.listOf(PinTypes.ENTITY).hasCodec());
    }
}
