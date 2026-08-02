package fr.blueprint.api.pin;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Les 16 types de pins de base, plus les fabriques de types paramétrés et de jokers.
 * Toutes les instances retournées sont uniques : comparaison par identité valide.
 *
 * <p>Palette : les six couleurs de {@code ux-ui-spec.md} §12, complétées pour les
 * autres types dans la même gamme (One Dark / Tokyo Night). La forme double toujours
 * la couleur (NFR11).
 *
 * <p>Aucun registre du jeu n'est touché au chargement de cette classe : les codecs
 * adossés aux registres ({@code itemstack}, {@code blockstate}, {@code text}) sont
 * paresseux — condition pour que les tests de {@code core} tournent sans Minecraft.
 */
public final class PinTypes {

    private PinTypes() {
    }

    // Déclaré avant les constantes : ANY est construit via generic() pendant l'init statique.
    private static final Map<Object, PinType> CACHE = new ConcurrentHashMap<>();

    private static final StreamCodec<FriendlyByteBuf, Vec3> VEC3_STREAM = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, Vec3::x,
            ByteBufCodecs.DOUBLE, Vec3::y,
            ByteBufCodecs.DOUBLE, Vec3::z,
            Vec3::new);

    private static final StreamCodec<FriendlyByteBuf, Direction> DIRECTION_STREAM = StreamCodec.of(
            (buf, dir) -> buf.writeEnum(dir),
            buf -> buf.readEnum(Direction.class));

    public static final PinType EXEC = PinType.builder(id("exec"))
            .kind(PinKind.EXEC).javaType(Void.class).noLiteral()
            .color(0xFFE6E6E6).shape(PinShape.EXEC)
            .build();

    public static final PinType BOOL = PinType.builder(id("bool"))
            .javaType(Boolean.class).color(0xFFE06C75)
            .codec(Codec.BOOL).streamCodec(ByteBufCodecs.BOOL)
            .defaultValue(() -> Boolean.FALSE)
            .build();

    public static final PinType INT = PinType.builder(id("int"))
            .javaType(Integer.class).color(0xFF56B6C2)
            .codec(Codec.INT).streamCodec(ByteBufCodecs.VAR_INT)
            .defaultValue(() -> 0)
            .build();

    public static final PinType LONG = PinType.builder(id("long"))
            .javaType(Long.class).color(0xFF4FA3AE)
            .codec(Codec.LONG).streamCodec(ByteBufCodecs.VAR_LONG)
            .defaultValue(() -> 0L)
            .coerceFrom(INT, v -> ((Number) v).longValue())
            .build();

    public static final PinType DOUBLE = PinType.builder(id("double"))
            .javaType(Double.class).color(0xFF98C379)
            .codec(Codec.DOUBLE).streamCodec(ByteBufCodecs.DOUBLE)
            .defaultValue(() -> 0.0)
            .coerceFrom(LONG, v -> ((Number) v).doubleValue())
            .build();

    public static final PinType STRING = PinType.builder(id("string"))
            .javaType(String.class).color(0xFFE5C07B)
            .codec(Codec.STRING).streamCodec(ByteBufCodecs.STRING_UTF8)
            .defaultValue(() -> "")
            .build();

    public static final PinType VEC3 = PinType.builder(id("vec3"))
            .javaType(Vec3.class).color(0xFFC678DD).shape(PinShape.DIAMOND)
            .codec(Vec3.CODEC).streamCodec(VEC3_STREAM)
            .defaultValue(() -> Vec3.ZERO)
            .build();

    public static final PinType BLOCKPOS = PinType.builder(id("blockpos"))
            .javaType(BlockPos.class).color(0xFF61AFEF).shape(PinShape.DIAMOND)
            .codec(BlockPos.CODEC).streamCodec(BlockPos.STREAM_CODEC)
            .defaultValue(() -> BlockPos.ZERO)
            .build();

    public static final PinType DIRECTION = PinType.builder(id("direction"))
            .javaType(Direction.class).color(0xFF7AA2F7).shape(PinShape.DIAMOND)
            .codec(Direction.CODEC).streamCodec(DIRECTION_STREAM)
            .defaultValue(() -> Direction.NORTH)
            .build();

    public static final PinType ITEMSTACK = PinType.builder(id("itemstack"))
            .javaType(ItemStack.class).color(0xFFD19A66).shape(PinShape.DIAMOND)
            .lazyCodec(() -> ItemStack.OPTIONAL_CODEC)
            .lazyStreamCodec(() -> ItemStack.OPTIONAL_STREAM_CODEC)
            .defaultValue(() -> ItemStack.EMPTY)
            .build();

    /** Référence vivante : pas de littéral (une entité ne se sérialise pas dans un graphe). */
    public static final PinType PLAYER = PinType.builder(id("player"))
            .javaType(net.minecraft.server.level.ServerPlayer.class)
            .color(0xFFBB9AF7).shape(PinShape.DIAMOND).noLiteral()
            .build();

    public static final PinType ENTITY = PinType.builder(id("entity"))
            .javaType(Entity.class).color(0xFFE0AF68).shape(PinShape.DIAMOND).noLiteral()
            .coerceFrom(PLAYER, v -> v)
            .build();

    public static final PinType BLOCKSTATE = PinType.builder(id("blockstate"))
            .javaType(BlockState.class).color(0xFF9ECE6A).shape(PinShape.DIAMOND)
            .lazyCodec(() -> BlockState.CODEC)
            .lazyStreamCodec(() -> ByteBufCodecs.fromCodec(BlockState.CODEC))
            .build();

    public static final PinType RESOURCE_LOCATION = PinType.builder(id("resourcelocation"))
            .javaType(Identifier.class).color(0xFF8A8F98).shape(PinShape.DIAMOND)
            .codec(Identifier.CODEC).streamCodec(Identifier.STREAM_CODEC)
            .build();

    public static final PinType TEXT = PinType.builder(id("text"))
            .javaType(Component.class).color(0xFFF7CE68).shape(PinShape.DIAMOND)
            .lazyCodec(() -> net.minecraft.network.chat.ComponentSerialization.CODEC)
            .lazyStreamCodec(() -> net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC)
            .defaultValue(Component::empty)
            .build();

    /** Le joker anonyme : tous les pins {@code any} d'un nœud forment un seul groupe. */
    public static final PinType ANY = generic("any");

    /** UUID : pas un type de pin de base, mais le codec de référence pour les entités persistées. */
    public static final Codec<java.util.UUID> UUID_CODEC = UUIDUtil.CODEC;

    private static final List<PinType> BUILTIN = List.of(
            EXEC, BOOL, INT, LONG, DOUBLE, STRING, VEC3, BLOCKPOS, DIRECTION,
            ITEMSTACK, PLAYER, ENTITY, BLOCKSTATE, RESOURCE_LOCATION, TEXT, ANY);

    /** Les 16 types de base, dans un ordre stable. */
    public static List<PinType> builtin() {
        return BUILTIN;
    }

    /** {@code list<element>} — instance unique par élément. */
    public static PinType listOf(PinType element) {
        return CACHE.computeIfAbsent(List.of("list", element),
                k -> new ParameterizedPinType(ParameterizedPinType.Container.LIST, List.of(element)));
    }

    /** {@code map<key, value>} — instance unique par couple. */
    public static PinType mapOf(PinType key, PinType value) {
        return CACHE.computeIfAbsent(List.of("map", key, value),
                k -> new ParameterizedPinType(ParameterizedPinType.Container.MAP, List.of(key, value)));
    }

    /** L'emplacement générique nommé {@code name} — instance unique par nom. */
    public static PinType generic(String name) {
        return CACHE.computeIfAbsent(List.of("generic", name), k -> new GenericPinType(name));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("blueprint", path);
    }
}
